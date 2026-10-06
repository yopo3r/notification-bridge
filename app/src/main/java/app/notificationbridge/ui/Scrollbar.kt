/**
 * A thin, auto-sized vertical scrollbar drawn over scrollable content.
 *
 * Compose/Material 3 has no built-in visible scrollbar for `Modifier.verticalScroll` or
 * `LazyColumn`, so this draws one directly: a translucent rounded bar on the trailing edge whose
 * height reflects how much of the content is visible and whose position reflects how far the
 * user has scrolled. It fades in quickly, and after 1.2 seconds without scrolling it fades out slowly while sliding
 * back into the screen edge. It is purely decorative
 * (no drag-to-scroll) - the goal is just to give the user a visual sense of "there is more below"
 * on the longer screens (History, Diagnostics log, Settings, About).
 *
 * Two overloads are needed because [ScrollState] (used by `verticalScroll`) and [LazyListState]
 * (used by `LazyColumn`) share no common interface for scroll progress.
 */
package app.notificationbridge.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first

private const val MIN_THUMB_FRACTION = 0.1f
private const val HIDE_DELAY_MS = 1_200L
private const val FADE_IN_MS = 120
private const val FADE_OUT_MS = 650
private typealias ThumbGeometry = Pair<Float, Float>

/** 0f = hidden, 1f = fully shown. Quick to appear, slow and smooth to leave. */
@Composable
private fun animatedScrollbarVisibility(visible: Boolean): State<Float> =
    animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (visible) tween(FADE_IN_MS, easing = LinearOutSlowInEasing)
        else tween(FADE_OUT_MS, easing = FastOutSlowInEasing),
        label = "scrollbarAlpha"
    )

@Composable
private fun rememberScrollbarAlpha(state: ScrollState): State<Float> {
    var visible by remember(state) { mutableStateOf(false) }
    LaunchedEffect(state) {
        snapshotFlow { state.value to state.isScrollInProgress }
            .drop(1)
            .collectLatest { (_, scrolling) ->
                visible = true
                if (scrolling) snapshotFlow { state.isScrollInProgress }.first { !it }
                delay(HIDE_DELAY_MS)
                visible = false
            }
    }
    return animatedScrollbarVisibility(visible)
}

@Composable
private fun rememberScrollbarAlpha(state: LazyListState): State<Float> {
    var visible by remember(state) { mutableStateOf(false) }
    LaunchedEffect(state) {
        snapshotFlow {
            Triple(
                state.firstVisibleItemIndex,
                state.firstVisibleItemScrollOffset,
                state.isScrollInProgress
            )
        }
            .drop(1)
            .collectLatest { (_, _, scrolling) ->
                visible = true
                if (scrolling) snapshotFlow { state.isScrollInProgress }.first { !it }
                delay(HIDE_DELAY_MS)
                visible = false
            }
    }
    return animatedScrollbarVisibility(visible)
}

/**
 * Estimates the total height of a lazy list. Items that have been on screen have a known height;
 * the rest are assumed to be as tall as the average known one. As the user scrolls, more heights
 * become known and the thumb size/position converge on the truth. Averaging only the *visible*
 * items (what this used to do) is badly wrong on screens with a few very different items such as
 * About and Diagnostics, where the thumb jumped around.
 */
private class LazyExtentEstimator {
    private val sizes = HashMap<Int, Int>()
    private var spacingSum = 0f
    private var spacingCount = 0
    private var lastTotal = -1

    fun geometry(state: LazyListState): ThumbGeometry? {
        val info = state.layoutInfo
        val total = info.totalItemsCount
        val visible = info.visibleItemsInfo
        if (total == 0 || visible.isEmpty()) return null
        if (total != lastTotal) {
            sizes.clear(); spacingSum = 0f; spacingCount = 0; lastTotal = total
        }
        for (i in visible.indices) {
            val item = visible[i]
            sizes[item.index] = item.size
            val next = visible.getOrNull(i + 1)
            if (next != null && next.index == item.index + 1) {
                val gap = (next.offset - item.offset - item.size).toFloat()
                if (gap >= 0f) { spacingSum += gap; spacingCount++ }
            }
        }
        val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
        if (viewport <= 0f) return null
        val knownSum = sizes.values.sumOf { it.toLong() }.toFloat()
        val average = knownSum / sizes.size
        val spacing = if (spacingCount > 0) spacingSum / spacingCount else 0f
        val content = knownSum + (total - sizes.size) * average + spacing * (total - 1)
        val maxScroll = content - viewport
        if (maxScroll <= 0.5f) return null

        val first = visible.first()
        var before = 0f
        for (i in 0 until first.index) before += (sizes[i]?.toFloat() ?: average) + spacing
        val scroll = before + (info.viewportStartOffset - first.offset)

        val thumbHeight = viewport * (viewport / content).coerceIn(MIN_THUMB_FRACTION, 1f)
        val progress = (scroll / maxScroll).coerceIn(0f, 1f)
        return thumbHeight to progress * (viewport - thumbHeight)
    }
}

private fun DrawScope.drawThumb(
    thumb: ThumbGeometry,
    widthPx: Float,
    color: Color,
    visibility: Float
) {
    if (visibility <= 0f) return
    // Fade, and slide a little towards the edge as it goes away.
    val slide = (1f - visibility) * widthPx * 1.5f
    drawRoundRect(
        color = color.copy(alpha = color.alpha * visibility),
        topLeft = Offset(size.width - widthPx + slide, thumb.second),
        size = Size(widthPx, thumb.first),
        cornerRadius = CornerRadius(widthPx / 2)
    )
}

/**
 * For [ScrollState]-based scrolling. Place this *before* `verticalScroll` in the modifier chain
 * so it draws over the viewport; placed after, it would be drawn inside the scrolling content and
 * scroll away with it.
 */
@Composable
fun Modifier.verticalScrollbar(
    state: ScrollState,
    width: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
): Modifier {
    val widthPx = with(LocalDensity.current) { width.toPx() }
    val scrollbarAlpha = rememberScrollbarAlpha(state)
    val thumb = remember(state) {
        derivedStateOf<ThumbGeometry?> {
            val viewport = state.viewportSize.toFloat()
            val content = viewport + state.maxValue
            if (state.maxValue <= 0 || content <= 0f) {
                null
            } else {
                val heightFraction = (viewport / content).coerceIn(MIN_THUMB_FRACTION, 1f)
                val thumbHeight = viewport * heightFraction
                val progress = (state.value.toFloat() / state.maxValue).coerceIn(0f, 1f)
                thumbHeight to progress * (viewport - thumbHeight)
            }
        }
    }
    return drawWithContent {
        drawContent()
        thumb.value?.let { drawThumb(it, widthPx, color, scrollbarAlpha.value) }
    }
}

@Composable
fun Modifier.verticalScrollbar(
    state: LazyListState,
    width: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
): Modifier {
    val widthPx = with(LocalDensity.current) { width.toPx() }
    val scrollbarAlpha = rememberScrollbarAlpha(state)
    val estimator = remember(state) { LazyExtentEstimator() }
    val thumb = remember(state) { derivedStateOf<ThumbGeometry?> { estimator.geometry(state) } }
    return drawWithContent {
        drawContent()
        thumb.value?.let { drawThumb(it, widthPx, color, scrollbarAlpha.value) }
    }
}
