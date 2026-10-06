/**
 * Application-wide singleton that owns the notification queue, the forwarding policy, and the
 * background worker that actually talks Bluetooth. It is the seam between
 * [app.notificationbridge.notification.NotificationBridgeService] (the producer) and
 * [app.notificationbridge.bluetooth.ObexObjectPushClient] (the transport).
 *
 * Responsibilities:
 * - [enqueue]: decides, per notification, whether it should be forwarded at all. Calls
 *   (`Notification.CATEGORY_CALL`) follow a separate gate from regular app notifications,
 *   because the component that posts a call notification (system dialer, WhatsApp's VoIP
 *   integration, etc.) usually has no launcher icon and could never be ticked in the
 *   "allowed apps" list even if the user wanted to. Regular notifications are checked against
 *   the allowed-app list and the ongoing/silent filters; calls bypass both since call
 *   notifications are almost always posted as `ongoing`.
 * - Duplicate suppression ([DuplicateDetector]): Android (and some apps) re-post the *same*
 *   logical notification within milliseconds of the original. The window is intentionally short
 *   (a few seconds for messages, wider for calls, which can legitimately keep re-posting while
 *   ringing) - wide enough to absorb that repost, narrow enough that a genuinely repeated
 *   message (e.g. the same contact texting "ok" twice) is not silently dropped.
 * - Burst protection ([RateLimiter]): one app cannot queue more than a fixed number of
 *   transfers per minute, so a chatty app cannot starve the strictly sequential Bluetooth link.
 * - Batching/cooldown ([MessageBatch], `addToBatch`/`flushBatch`): when enabled, messages from
 *   the same app+title ("the same conversation") arriving within a user-configured window
 *   (5-60s) are combined into a single file instead of one transfer per message. The window is
 *   fixed, starting at the first message of the batch - see the comment on [PendingBatch] for
 *   why it doesn't reset per message. Calls are never batched.
 * - The worker loop: consumes the queue strictly in order, generates the file via
 *   [app.notificationbridge.format.NotificationFormatter], and pushes it via
 *   [app.notificationbridge.bluetooth.ObexObjectPushClient.push]. On failure it retries with a
 *   short backoff before giving up on that one item and moving to the next. Two consecutive
 *   failed notifications open a five-minute circuit breaker, avoiding repeated RFCOMM attempts
 *   while the receiver is unavailable. The loop survives unexpected exceptions on a single item
 *   (a corrupt settings read, say) instead of dying and silently stopping all forwarding.
 * - History ([track]): each notification that reaches a decision gets one record that is
 *   updated in place as it moves through [TransferStatus] (queued, connecting, transferred or
 *   failed). Notifications excluded by a per-notification filter or the rate limiter are recorded
 *   too, so "why didn't it arrive?" has an answer. Global off-states (bridge disabled, app not
 *   allowed) and re-posted duplicates are not recorded: they would flood the list and, for apps
 *   the user never allowed, keep titles the user never opted in.
 * - Failures: every failed record carries a [FailureReason] (never parsed from message text). Setup
 *   problems stop after the first attempt; link problems use the retry loop, shown as "Attempt N
 *   of M". "Retry now" ([retry]) resends a recently failed notification from a small, short-lived,
 *   memory-only [RetryBuffer].
 * - [state]: a [StateFlow] the UI observes for connection status, queue depth and transfer
 *   history, so the UI never needs to poll.
 * - Auto-clear ([RetentionPolicy], `autoClearLoop`): periodically prunes transfer history by
 *   age when enabled, so unattended/dumbphone-mode use doesn't accumulate in-memory state
 *   forever.
 *
 * Assumptions: notifications are processed strictly one at a time (no parallel transfers),
 * which keeps the Bluetooth link and OBEX session simple at the cost of throughput under a
 * burst of notifications - acceptable for this app's use case.
 *
 * Limitations: a transfer that exhausts all retries is dropped (recorded in the history, and
 * surfaced as an alert in dumbphone mode); there is no persistent backlog across app restarts,
 * and no cross-process locking, since this is a single-process app. Transfer history is held in
 * memory only.
 */
package app.notificationbridge.queue

import android.content.ComponentName
import android.content.Context
import android.annotation.SuppressLint
import android.os.SystemClock
import android.provider.Settings
import app.notificationbridge.BuildConfig
import app.notificationbridge.bluetooth.ObexObjectPushClient
import app.notificationbridge.data.SettingsRepository
import app.notificationbridge.format.NotificationFormatter
import app.notificationbridge.model.BridgeUiState
import app.notificationbridge.model.ConnectionState
import app.notificationbridge.model.DropReason
import app.notificationbridge.model.FailureReason
import app.notificationbridge.model.NotificationData
import app.notificationbridge.model.TestSampleKind
import app.notificationbridge.model.TransferRecord
import app.notificationbridge.model.TransferStatus
import app.notificationbridge.notification.ErrorNotifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// This process-wide runtime intentionally holds only application-scoped dependencies.
@SuppressLint("StaticFieldLeak")
object BridgeRuntime {

    private const val LOG_TAG = "BridgeRuntime"
    private const val CATEGORY_CALL = "call"

    // Absorbs the OS/app re-firing onNotificationPosted for the exact same event (milliseconds to
    // a couple of seconds). It must stay short: a wide window (it used to be 2 minutes) treats a
    // genuinely repeated message as a duplicate and silently drops the second, real one.
    private const val DUPLICATE_WINDOW_MS = 5_000L

    // Calls get a wider window: some dialers/OEMs repost the same ringing notification several
    // times while it rings, and only one file per call is wanted.
    private const val CALL_DUPLICATE_WINDOW_MS = 20_000L

    private const val RATE_LIMIT_MAX_EVENTS = 10
    private const val RATE_LIMIT_WINDOW_MS = 60_000L

    // Safety cap so a pathological flood within one cooldown window can't grow a batch
    // unbounded in memory; ordinary conversations never get close to this.
    private const val MAX_BATCH_MESSAGES = 50

    private const val MAX_ATTEMPTS = 3

    // "Retry now" needs the notification itself, which History never keeps. It is held briefly
    // and in small numbers; see RetryBuffer for the privacy reasoning.
    private const val RETRY_WINDOW_MS = 10 * 60_000L
    private const val MAX_RETRY_ITEMS = 5
    private const val CIRCUIT_BREAKER_FAILURE_THRESHOLD = 2
    private const val CIRCUIT_BREAKER_COOLDOWN_MS = 5 * 60_000L
    private const val MAX_LOG_LINE_CHARS = 240
    private val LOG_LINE_BREAKS = Regex("[\\r\\n]+")

    // How often the auto-clear loop re-checks settings and prunes - not how precisely entries
    // age out. A 15-minute granularity is more than enough for a feature whose shortest setting
    // is measured in hours.
    private const val CLEANUP_INTERVAL_MS = 15 * 60_000L

    private lateinit var app: Context
    private lateinit var repo: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<QueuedItem>(Channel.UNLIMITED)
    private val recordIds = java.util.concurrent.atomic.AtomicLong(0)
    private val retryBuffer = RetryBuffer<QueuedItem>(MAX_RETRY_ITEMS, RETRY_WINDOW_MS)
    private val mutable = MutableStateFlow(BridgeUiState())
    val state: StateFlow<BridgeUiState> = mutable.asStateFlow()

    private val duplicates = DuplicateDetector(retentionMs = CALL_DUPLICATE_WINDOW_MS)
    private val rateLimiter = RateLimiter(RATE_LIMIT_MAX_EVENTS, RATE_LIMIT_WINDOW_MS)

    // The single queue worker owns these values, so breaker transitions stay ordered with
    // transfers and no lock is needed.
    private var consecutiveTransferFailures = 0
    private var circuitBreakerUntilElapsedMs = 0L

    @Volatile
    private var initialized = false

    fun initialize(context: Context, repository: SettingsRepository) {
        if (initialized) return
        initialized = true
        app = context.applicationContext
        repo = repository
        scope.launch { worker() }
        scope.launch { autoClearLoop() }
    }

    /** Periodically prunes transfer history by age, per the auto-clear setting. */
    private suspend fun autoClearLoop() {
        while (true) {
            delay(CLEANUP_INTERVAL_MS)
            val s = runCatching { repo.settings.first() }.getOrNull() ?: continue
            if (!s.autoClearEnabled) continue
            val cutoff = RetentionPolicy.cutoffFor(s.autoClearHours, System.currentTimeMillis())
            mutable.update {
                it.copy(
                    history = RetentionPolicy.pruneOlderThan(it.history, cutoff) { r -> r.timestamp }
                )
            }
        }
    }

    fun refreshAccess() {
        mutable.update { it.copy(notificationAccess = hasNotificationAccess(app)) }
    }

    /** Called by the listener service when Android actually binds/unbinds it. */
    fun setListenerConnected(connected: Boolean) {
        mutable.update { it.copy(listenerConnected = connected) }
    }

    /** Emits troubleshooting lines only in debug builds and retains protocol status in state. */
    fun log(message: String) {
        val line = message.replace(LOG_LINE_BREAKS, " ").take(MAX_LOG_LINE_CHARS)
        if (BuildConfig.DEBUG) android.util.Log.d(LOG_TAG, line)
        if (isObexResponseLine(line)) {
            mutable.update { it.copy(lastObexResponse = line) }
        }
    }

    fun clearHistory() {
        retryBuffer.clear()
        mutable.update { it.copy(history = emptyList(), lastTransfer = null) }
    }

    /**
     * Applies the forwarding policy and, if the notification passes, queues it.
     * @param manual bypasses every filter (nothing currently sets this; kept for callers that
     *   want to force a send).
     * @return whether the notification was queued.
     */
    suspend fun enqueue(n: NotificationData, manual: Boolean = false): Boolean {
        val s = repo.settings.first()
        val isCall = n.category == CATEGORY_CALL

        if (!manual) {
            if (!s.bridgeEnabled) return skip(n, "bridge disabled")
            if (isCall) {
                if (!s.notifyOnCalls) return drop(n, "call notifications disabled", DropReason.CALLS_DISABLED)
            } else {
                if (n.packageName !in s.allowedPackages) return skip(n, "not in allowed list")
                if (s.ignoreOngoing && n.isOngoing) return drop(n, "ongoing", DropReason.ONGOING)
                if (s.ignoreSilent && n.isSilent) return drop(n, "silent", DropReason.SILENT)
            }
            val window = if (isCall) CALL_DUPLICATE_WINDOW_MS else DUPLICATE_WINDOW_MS
            if (s.ignoreUpdates && duplicates.isDuplicate(n, window)) return skip(n, "duplicate")
            // Calls are rare and important; only regular notifications count against the budget.
            if (!isCall && !rateLimiter.tryAcquire(n.packageName)) {
                track(newRecordId(), n.appName, n.title, TransferStatus.RATE_LIMITED)
                return skip(n, "rate limited")
            }

            // Batching replaces the rate limiter as the anti-burst mechanism for whatever app
            // it applies to (several messages become one file instead of competing for the
            // per-minute budget), so it only makes sense - and only runs - for messages that
            // already passed every other filter above. Calls are never batched: they're rare,
            // time-sensitive, and there's nothing meaningful to combine them with.
            if (!isCall && s.batchingEnabled) {
                addToBatch(n, s.batchingCooldownSeconds)
                return true
            }
        }

        val item = QueuedItem(n, newRecordId())
        track(item, TransferStatus.QUEUED)
        sendToQueue(item)
        return true
    }

    private fun skip(n: NotificationData, reason: String): Boolean {
        log("Skipped ${n.packageName}: $reason")
        return false
    }

    /** A [skip] that also leaves a [TransferStatus.DROPPED] row in the history. */
    private fun drop(n: NotificationData, reason: String, why: DropReason): Boolean {
        track(newRecordId(), n.appName, n.title, TransferStatus.DROPPED, dropReason = why)
        return skip(n, reason)
    }

    /** One notification, or one combined batch, on its way through the queue. */
    private class QueuedItem(
        val n: NotificationData,
        val recordId: Long,
        val messageCount: Int = 1,
        /** A user-requested retry: also clears a paused circuit breaker. */
        val retry: Boolean = false
    )

    private fun newRecordId() = recordIds.incrementAndGet()

    // A batch's cooldown is a fixed window starting at its first message, not a countdown that
    // resets on every new arrival. A resetting countdown could in principle never fire during an
    // active conversation, delaying delivery unpredictably; a fixed window guarantees the wait
    // is never longer than the configured cooldown, at the cost of occasionally splitting a
    // fast-moving conversation into two consecutive files instead of one.
    private class PendingBatch(
        val packageName: String,
        val appName: String,
        val title: String?,
        val recordId: Long
    ) {
        val messages = java.util.Collections.synchronizedList(mutableListOf<NotificationData>())
    }

    private val pendingBatches = java.util.concurrent.ConcurrentHashMap<String, PendingBatch>()

    private fun addToBatch(n: NotificationData, cooldownSeconds: Int) {
        val key = MessageBatch.groupKey(n)
        var isNewBatch = false
        val batch = pendingBatches.computeIfAbsent(key) {
            isNewBatch = true
            PendingBatch(n.packageName, n.appName, n.title, newRecordId())
        }
        if (batch.messages.size < MAX_BATCH_MESSAGES) {
            batch.messages.add(n)
        } else {
            log("Batch for ${n.packageName} is full ($MAX_BATCH_MESSAGES), dropping one message")
        }
        publishBatchedCount()
        // One row per batch: it shows "combined into batch" while the cooldown runs, then the
        // same row moves on to queued / transferred with the final message count.
        track(batch.recordId, batch.appName, batch.title, TransferStatus.BATCHED, messageCount = batch.messages.size)

        if (isNewBatch) {
            log("Batching started for ${n.packageName}: waiting ${cooldownSeconds}s")
            scope.launch {
                delay(cooldownSeconds * 1000L)
                flushBatch(key)
            }
        }
    }

    private suspend fun flushBatch(key: String) {
        val batch = pendingBatches.remove(key) ?: return
        publishBatchedCount()
        val messages = batch.messages.toList()
        if (messages.isEmpty()) return

        val combined = if (messages.size == 1) {
            messages.first()
        } else {
            messages.last().copy(
                text = MessageBatch.combinedText(messages),
                notificationKey = "batch-${System.nanoTime()}"
            )
        }
        log("Batch flushed for ${batch.packageName}: ${messages.size} message(s) combined")
        val item = QueuedItem(combined, batch.recordId, messages.size)
        track(item, TransferStatus.QUEUED)
        sendToQueue(item)
    }

    private fun publishBatchedCount() {
        val total = pendingBatches.values.sumOf { it.messages.size }
        mutable.update { it.copy(batchedMessageCount = total) }
    }

    // Count first: the worker can finish an item instantly (e.g. no receiver selected) and must
    // never decrement before this increment has happened.
    private suspend fun sendToQueue(item: QueuedItem) {
        mutable.update { it.copy(queueCount = it.queueCount + 1) }
        queue.send(item)
    }

    private suspend fun worker() {
        val client = ObexObjectPushClient(app, ::log)
        for (item in queue) {
            try {
                process(client, item)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("Unexpected error while processing a notification: ${e.message}")
                track(item, TransferStatus.FAILED, describe(e), failure = FailureClassifier.classify(e))
            } finally {
                mutable.update { it.copy(queueCount = (it.queueCount - 1).coerceAtLeast(0)) }
            }
        }
    }

    private suspend fun process(client: ObexObjectPushClient, item: QueuedItem) {
        val n = item.n
        val s = repo.settings.first()
        val address = s.selectedAddress
        val file = NotificationFormatter.generateFileName(n)
        val bytes = NotificationFormatter.notificationToFile(n, s.maxTextChars)
        log("File name: $file")

        // A user-requested retry means "try again now", so it clears a pause.
        if (item.retry) {
            circuitBreakerUntilElapsedMs = 0L
            consecutiveTransferFailures = 0
        }
        val nowElapsedMs = SystemClock.elapsedRealtime()
        if (address != null && nowElapsedMs < circuitBreakerUntilElapsedMs) {
            val remainingMs = circuitBreakerUntilElapsedMs - nowElapsedMs
            val detail = "Bluetooth circuit breaker open; retry paused for " +
                "${(remainingMs + 999) / 1000}s after repeated failures"
            log("Skipping $file: $detail")
            failTransfer(item, FailureReason.PAUSED, detail, attempt = 0, maxAttempts = 0)
            return
        }
        if (circuitBreakerUntilElapsedMs != 0L && nowElapsedMs >= circuitBreakerUntilElapsedMs) {
            log("Bluetooth circuit breaker cooldown ended; resuming transfers")
            circuitBreakerUntilElapsedMs = 0L
            consecutiveTransferFailures = 0
        }

        var error: Exception? = null
        var failure: FailureReason? = null
        var success = false
        var attemptsMade = 0
        // Dumbphone mode implies automatic reconnection: nobody is watching the screen to retry.
        val attempts = if (s.autoReconnect || s.dumbphoneMode) MAX_ATTEMPTS else 1

        if (address == null) {
            failure = FailureReason.NO_RECEIVER
        } else {
            for (attempt in 1..attempts) {
                attemptsMade = attempt
                try {
                    mutable.update { it.copy(connection = ConnectionState.CONNECTING) }
                    if (attempt == 1) {
                        track(item, TransferStatus.CONNECTING, attempt = 1, maxAttempts = attempts)
                    }
                    log("Transfer attempt $attempt/$attempts: $file")
                    client.push(address, file, "text/plain", bytes)
                    success = true
                    error = null
                    failure = null
                    consecutiveTransferFailures = 0
                    mutable.update { it.copy(connection = ConnectionState.DISCONNECTED) }
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = e
                    val reason = FailureClassifier.classify(e)
                    failure = reason
                    log("Attempt $attempt/$attempts failed for $file: ${e.message}")
                    mutable.update { it.copy(connection = ConnectionState.ERROR) }
                    // Nothing the next attempt does can fix a setup problem; stop waiting.
                    if (reason.needsUserAction) break
                    if (attempt < attempts) {
                        // Show "Attempt 2 of 3 · <why the last one failed>" during the backoff.
                        track(
                            item, TransferStatus.CONNECTING,
                            attempt = attempt + 1, maxAttempts = attempts, failure = reason
                        )
                        delay(1000L * attempt)
                    }
                }
            }
        }

        if (success) {
            track(item, TransferStatus.TRANSFERRED, attempt = attemptsMade, maxAttempts = attempts)
            return
        }
        val reason = failure ?: FailureReason.UNKNOWN
        failTransfer(item, reason, describe(error ?: IllegalStateException(reason.name)), attemptsMade, attempts)
        if (address != null && reason.countsTowardCircuitBreaker) {
            consecutiveTransferFailures++
            if (consecutiveTransferFailures >= CIRCUIT_BREAKER_FAILURE_THRESHOLD) {
                circuitBreakerUntilElapsedMs =
                    SystemClock.elapsedRealtime() + CIRCUIT_BREAKER_COOLDOWN_MS
                log("Bluetooth circuit breaker opened for ${CIRCUIT_BREAKER_COOLDOWN_MS / 1000}s " +
                    "after $consecutiveTransferFailures consecutive failed notifications")
            }
        }
        if (s.dumbphoneMode) ErrorNotifier.notifyTransferFailed(app, reason)
    }

    /**
     * Records the final failure. If "Retry now" is worth offering, the notification is parked in
     * the [retryBuffer] and the row says until when.
     */
    private fun failTransfer(
        item: QueuedItem,
        reason: FailureReason,
        detail: String,
        attempt: Int,
        maxAttempts: Int
    ) {
        var retryUntil: Long? = null
        if (reason.retryable) {
            val stored = retryBuffer.put(item.recordId, item, System.currentTimeMillis())
            retryUntil = stored.expiresAtMs
            stored.dropped.forEach(::stopOfferingRetry)
        }
        track(
            item, TransferStatus.FAILED, detail,
            failure = reason, attempt = attempt, maxAttempts = maxAttempts, retryUntil = retryUntil
        )
    }

    /** The row for [id] stays, but its "Retry now" goes away: the notification is no longer held. */
    private fun stopOfferingRetry(id: Long) {
        mutable.update { state ->
            state.copy(history = state.history.map { if (it.id == id && it.retryUntil != null) it.copy(retryUntil = null) else it })
        }
    }

    /**
     * Resends a failed notification that is still held (see [RetryBuffer]), reusing its History
     * row. Returns `false` if it has expired or was never kept, so the UI can say so.
     */
    fun retry(recordId: Long): Boolean {
        val held = retryBuffer.take(recordId, System.currentTimeMillis()) ?: return false
        scope.launch {
            val again = QueuedItem(held.n, held.recordId, held.messageCount, retry = true)
            track(again, TransferStatus.QUEUED)
            sendToQueue(again)
        }
        return true
    }

    private fun describe(error: Throwable?): String =
        error?.message ?: error?.javaClass?.simpleName ?: "Unknown error"

    /**
     * Creates or updates the history row [id]. Only records with a result (transferred or
     * failed) become [BridgeUiState.lastTransfer]; queued/connecting/filtered ones never do.
     */
    private fun track(
        id: Long,
        appName: String,
        title: String?,
        status: TransferStatus,
        detail: String = "",
        dropReason: DropReason? = null,
        messageCount: Int = 1,
        failure: FailureReason? = null,
        attempt: Int = 0,
        maxAttempts: Int = 0,
        retryUntil: Long? = null
    ) {
        val record = TransferRecord(
            appName = appName,
            timestamp = System.currentTimeMillis(),
            status = status,
            detail = detail,
            title = TransferHistory.summarizeTitle(title),
            id = id,
            dropReason = dropReason,
            messageCount = messageCount,
            failure = failure,
            attempt = attempt,
            maxAttempts = maxAttempts,
            retryUntil = retryUntil
        )
        mutable.update {
            it.copy(
                lastTransfer = if (status.isOutcome) record else it.lastTransfer,
                history = TransferHistory.upsert(it.history, record)
            )
        }
    }

    private fun track(
        item: QueuedItem,
        status: TransferStatus,
        detail: String = "",
        failure: FailureReason? = null,
        attempt: Int = 0,
        maxAttempts: Int = 0,
        retryUntil: Long? = null
    ) = track(
        item.recordId, item.n.appName, item.n.title, status, detail,
        messageCount = item.messageCount,
        failure = failure, attempt = attempt, maxAttempts = maxAttempts, retryUntil = retryUntil
    )

    /** Sends a fixed sample file straight to [address], bypassing the queue and all filters. */
    fun testPush(context: Context, address: String, kind: TestSampleKind, onDone: (Result<Unit>) -> Unit) {
        // On Android 12 and earlier, AppCompat applies the selected app language to the
        // Activity context, while the process-wide Application context can keep the system
        // language. Resolve localized sample strings from the calling Activity before moving
        // the Bluetooth work to this application-scoped background coroutine.
        val sample = NotificationSamples.forKind(context, kind)
        scope.launch {
            val recordId = newRecordId()
            track(recordId, sample.appName, sample.title, TransferStatus.CONNECTING)
            val maxChars = repo.settings.first().maxTextChars
            val file = NotificationFormatter.generateFileName(sample)
            val result = runCatching {
                log("Manual test file name: $file (${kind.name})")
                ObexObjectPushClient(app, ::log)
                    .push(address, file, "text/plain", NotificationFormatter.notificationToFile(sample, maxChars))
                Unit
            }
            track(
                recordId, sample.appName, sample.title,
                if (result.isSuccess) TransferStatus.TRANSFERRED else TransferStatus.FAILED,
                if (result.isSuccess) "" else describe(result.exceptionOrNull()),
                failure = result.exceptionOrNull()?.let(FailureClassifier::classify)
            )
            withContext(Dispatchers.Main) { onDone(result) }
        }
    }

    fun pairedDevices() = ObexObjectPushClient(app, ::log).pairedDevices()

    fun hasNotificationAccess(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?: return false
        return flat.split(':')
            .mapNotNull(ComponentName::unflattenFromString)
            .any { it.packageName == context.packageName }
    }

    // Only protocol lines that report a response code qualify; the "OBEX object name: ..." line
    // (which contains a title-derived file name) must never be captured as the "last response".
    private fun isObexResponseLine(line: String) =
        line.startsWith("OBEX ") && line.contains(" response") && line.contains("0x")
}
