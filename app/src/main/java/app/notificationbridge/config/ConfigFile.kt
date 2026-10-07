/**
 * Small, versioned text format for exporting the app's configuration and restoring it on another
 * build or phone. It follows the same conventions as the custom theme file
 * ([app.notificationbridge.ui.theme.CustomThemeParser]): `key: value` lines, `#` comments, and
 * all-or-nothing parsing - an unknown key, a duplicate, a missing required value or anything out
 * of range rejects the whole file, so a damaged or future-version file can never apply half a
 * configuration.
 *
 * What is included: allowed apps, the four forwarding filters, batching, maximum text length,
 * theme (mode and any custom palette), language, dumbphone mode, and - at the user's choice - the
 * preferred receiver ([ReceiverExport]). What is deliberately left out: the bridge on/off switch
 * (an import must never start forwarding by itself), onboarding state, history, auto-clear, the
 * Test sample choice and auto-reconnect.
 *
 * The receiver's Bluetooth address is identifying and the Contributing guide asks users never to
 * post one, so the default export leaves it out. A receiver *name* alone is still useful: on
 * import the app can pick the paired device with that name.
 *
 * Pure JVM like [app.notificationbridge.diagnostics.DiagnosticsReport]: no Android types, so the
 * whole format is unit tested. Whatever [export] writes, [parse] accepts.
 */
package app.notificationbridge.config

import app.notificationbridge.data.SettingsRepository
import app.notificationbridge.model.BridgeSettings
import app.notificationbridge.model.ThemeMode
import app.notificationbridge.ui.theme.CustomThemeParser

/** How much of the preferred receiver goes into an exported file. */
enum class ReceiverExport { NONE, NAME_ONLY, NAME_AND_ADDRESS }

/**
 * A validated configuration, ready to apply.
 *
 * @property receiverName `null` when the file has no receiver section.
 * @property receiverAddress Only ever set together with [receiverName]; `null` for the default
 *   (address-free) export.
 * @property language `"system"` or one of [ConfigFile.LANGUAGES].
 * @property customThemeSource Theme text in the format [CustomThemeParser] reads, already
 *   validated, or `null` to use the built-in schemes.
 */
data class ConfigSnapshot(
    val allowedPackages: Set<String>,
    val ignoreSilent: Boolean,
    val ignoreOngoing: Boolean,
    val ignoreUpdates: Boolean,
    val notifyOnCalls: Boolean,
    val batchingEnabled: Boolean,
    val batchingCooldownSeconds: Int,
    val maxTextChars: Int,
    val receiverName: String?,
    val receiverAddress: String?,
    val themeMode: ThemeMode,
    val customThemeSource: String?,
    val language: String,
    val dumbphoneMode: Boolean
)

object ConfigFile {

    const val FORMAT_VERSION = 1
    const val MAX_FILE_CHARS = 64 * 1024
    const val MAX_APPS = 5000
    const val MAX_NAME_CHARS = 100
    const val LANGUAGE_SYSTEM = "system"

    /** The languages the app ships, matching the Settings language picker. */
    val LANGUAGES = listOf("de", "en", "es", "fr", "it", "nl", "pt", "zh", "ja", "ko", "ru")

    private const val THEME_PREFIX = "custom-theme."

    private object Key {
        const val VERSION = "version"
        const val APP = "app"
        const val IGNORE_SILENT = "filter.ignore-silent"
        const val IGNORE_ONGOING = "filter.ignore-ongoing"
        const val IGNORE_DUPLICATES = "filter.ignore-duplicates"
        const val NOTIFY_CALLS = "filter.notify-calls"
        const val BATCHING_ENABLED = "batching.enabled"
        const val BATCHING_SECONDS = "batching.interval-seconds"
        const val MAX_TEXT = "max-text-chars"
        const val RECEIVER_NAME = "receiver.name"
        const val RECEIVER_ADDRESS = "receiver.address"
        const val THEME_MODE = "theme.mode"
        const val LANGUAGE = "language"
        const val DUMBPHONE = "dumbphone-mode"
    }

    private val SINGLE_KEYS = setOf(
        Key.VERSION, Key.IGNORE_SILENT, Key.IGNORE_ONGOING, Key.IGNORE_DUPLICATES, Key.NOTIFY_CALLS,
        Key.BATCHING_ENABLED, Key.BATCHING_SECONDS, Key.MAX_TEXT, Key.RECEIVER_NAME,
        Key.RECEIVER_ADDRESS, Key.THEME_MODE, Key.LANGUAGE, Key.DUMBPHONE
    )

    private val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)+")
    private val BLUETOOTH_ADDRESS = Regex("(?i)(?:[0-9a-f]{2}:){5}[0-9a-f]{2}")
    private val WHITESPACE = Regex("\\s+")

    /** Maps the platform's current language tag to a file value; anything unknown is "system". */
    fun languageValue(tag: String?): String = tag?.takeIf { it in LANGUAGES } ?: LANGUAGE_SYSTEM

    fun export(
        settings: BridgeSettings,
        language: String,
        receiver: ReceiverExport,
        appVersion: String
    ): String {
        val receiverName = settings.selectedName?.let(::oneLine)?.take(MAX_NAME_CHARS)?.ifBlank { null }
        val receiverAddress = settings.selectedAddress?.takeIf { BLUETOOTH_ADDRESS.matches(it) }
        val includeAddress = receiver == ReceiverExport.NAME_AND_ADDRESS && receiverAddress != null

        val lines = mutableListOf(
            "# Notification Bridge configuration, format version $FORMAT_VERSION",
            "# Exported by app version ${oneLine(appVersion)}. Review it before sharing:",
            "# it lists the apps you forward notifications from."
        )
        if (includeAddress) {
            lines += "# It also contains your receiver's Bluetooth address. Do not post it publicly."
        }
        lines += "${Key.VERSION}: $FORMAT_VERSION"
        // Only names the importer accepts, so a file this app writes is always importable.
        settings.allowedPackages.filter { PACKAGE_NAME.matches(it) }.sorted()
            .forEach { lines += "${Key.APP}: $it" }
        lines += "${Key.IGNORE_SILENT}: ${settings.ignoreSilent}"
        lines += "${Key.IGNORE_ONGOING}: ${settings.ignoreOngoing}"
        lines += "${Key.IGNORE_DUPLICATES}: ${settings.ignoreUpdates}"
        lines += "${Key.NOTIFY_CALLS}: ${settings.notifyOnCalls}"
        lines += "${Key.BATCHING_ENABLED}: ${settings.batchingEnabled}"
        lines += "${Key.BATCHING_SECONDS}: ${settings.batchingCooldownSeconds}"
        lines += "${Key.MAX_TEXT}: ${settings.maxTextChars}"
        if (receiver != ReceiverExport.NONE) {
            // The importer needs a name whenever there is an address.
            val name = receiverName ?: if (includeAddress) "Receiver" else null
            if (name != null) {
                lines += "${Key.RECEIVER_NAME}: $name"
                if (includeAddress) lines += "${Key.RECEIVER_ADDRESS}: ${receiverAddress!!.uppercase()}"
            } else {
                lines += "# No receiver selected."
            }
            if (!includeAddress) lines += "# Bluetooth address not included."
        }
        lines += "${Key.THEME_MODE}: ${settings.themeMode.name.lowercase()}"
        customThemeLines(settings.customThemeSource)?.let { lines += it }
        lines += "${Key.LANGUAGE}: ${if (language in LANGUAGES) language else LANGUAGE_SYSTEM}"
        lines += "${Key.DUMBPHONE}: ${settings.dumbphoneMode}"
        return lines.joinToString("\n", postfix = "\n")
    }

    /** Re-emits a valid custom theme as `custom-theme.<key>: <value>` lines; invalid ones are dropped. */
    private fun customThemeLines(source: String?): List<String>? {
        if (source == null) return null
        if (runCatching { CustomThemeParser.parse(source) }.isFailure) return null
        return source.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith('#') }
            .mapNotNull { line ->
                val separator = line.indexOf(':')
                if (separator <= 0) null
                else "$THEME_PREFIX${line.substring(0, separator).trim().lowercase()}: ${line.substring(separator + 1).trim()}"
            }
            .toList()
    }

    /** @throws IllegalArgumentException with a user-readable reason if the file is not acceptable. */
    fun parse(source: String): ConfigSnapshot {
        require(source.length <= MAX_FILE_CHARS) { "Configuration file is too large (maximum 64 KB)" }
        val values = linkedMapOf<String, String>()
        val apps = linkedSetOf<String>()
        val theme = linkedMapOf<String, String>()

        source.removePrefix("﻿").lineSequence().forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith('#')) return@forEachIndexed
            val where = "Line ${index + 1}"
            val separator = line.indexOf(':')
            require(separator > 0) { "$where: expected key: value" }
            val key = line.substring(0, separator).trim().lowercase()
            val value = line.substring(separator + 1).trim()
            when {
                key == Key.APP -> {
                    require(PACKAGE_NAME.matches(value) && value.length <= 255) { "$where: invalid app package name" }
                    require(apps.size < MAX_APPS) { "$where: too many apps (maximum $MAX_APPS)" }
                    require(apps.add(value)) { "$where: duplicate app '$value'" }
                }
                key.startsWith(THEME_PREFIX) -> {
                    val themeKey = key.removePrefix(THEME_PREFIX)
                    require(themeKey.isNotEmpty()) { "$where: empty theme key" }
                    require(theme.put(themeKey, value) == null) { "$where: duplicate key '$key'" }
                }
                key in SINGLE_KEYS -> require(values.put(key, value) == null) { "$where: duplicate key '$key'" }
                else -> throw IllegalArgumentException("$where: unknown key '$key'")
            }
        }

        require(values[Key.VERSION] == FORMAT_VERSION.toString()) {
            "Missing or unsupported configuration version"
        }

        fun bool(key: String): Boolean = when (values[key]) {
            "true" -> true
            "false" -> false
            null -> throw IllegalArgumentException("Missing $key")
            else -> throw IllegalArgumentException("Invalid value for $key: use true or false")
        }

        fun int(key: String, range: IntRange): Int {
            val raw = values[key] ?: throw IllegalArgumentException("Missing $key")
            val number = raw.toIntOrNull()
            require(number != null && number in range) {
                "Invalid value for $key: use a number from ${range.first} to ${range.last}"
            }
            return number
        }

        val themeMode = when (values[Key.THEME_MODE]) {
            "system" -> ThemeMode.SYSTEM
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            null -> throw IllegalArgumentException("Missing ${Key.THEME_MODE}")
            else -> throw IllegalArgumentException("Invalid value for ${Key.THEME_MODE}: use system, light or dark")
        }

        val language = values[Key.LANGUAGE] ?: throw IllegalArgumentException("Missing ${Key.LANGUAGE}")
        require(language == LANGUAGE_SYSTEM || language in LANGUAGES) {
            "Invalid value for ${Key.LANGUAGE}: use system or one of ${LANGUAGES.joinToString(", ")}"
        }

        val receiverName = values[Key.RECEIVER_NAME]
        require(receiverName == null || (receiverName.isNotBlank() && receiverName.length <= MAX_NAME_CHARS)) {
            "Invalid value for ${Key.RECEIVER_NAME}: use 1 to $MAX_NAME_CHARS characters"
        }
        val receiverAddress = values[Key.RECEIVER_ADDRESS]
        if (receiverAddress != null) {
            require(receiverName != null) { "${Key.RECEIVER_ADDRESS} needs ${Key.RECEIVER_NAME}" }
            require(BLUETOOTH_ADDRESS.matches(receiverAddress)) {
                "Invalid value for ${Key.RECEIVER_ADDRESS}: expected AA:BB:CC:DD:EE:FF"
            }
        }

        val customTheme = if (theme.isEmpty()) null else {
            val text = theme.entries.joinToString("\n") { "${it.key}: ${it.value}" }
            try {
                CustomThemeParser.parse(text)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Custom theme: ${e.message}")
            }
            text
        }

        return ConfigSnapshot(
            allowedPackages = apps,
            ignoreSilent = bool(Key.IGNORE_SILENT),
            ignoreOngoing = bool(Key.IGNORE_ONGOING),
            ignoreUpdates = bool(Key.IGNORE_DUPLICATES),
            notifyOnCalls = bool(Key.NOTIFY_CALLS),
            batchingEnabled = bool(Key.BATCHING_ENABLED),
            batchingCooldownSeconds = int(
                Key.BATCHING_SECONDS,
                SettingsRepository.MIN_BATCHING_COOLDOWN_SECONDS..SettingsRepository.MAX_BATCHING_COOLDOWN_SECONDS
            ),
            maxTextChars = int(
                Key.MAX_TEXT,
                SettingsRepository.MIN_TEXT_CHARS..SettingsRepository.MAX_TEXT_CHARS
            ),
            receiverName = receiverName,
            receiverAddress = receiverAddress?.uppercase(),
            themeMode = themeMode,
            customThemeSource = customTheme,
            language = language,
            dumbphoneMode = bool(Key.DUMBPHONE)
        )
    }

    private fun oneLine(text: String) = text.replace(WHITESPACE, " ").trim()
}
