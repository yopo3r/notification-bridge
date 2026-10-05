/**
 * A thin, auto-sized vertical scrollbar drawn over scrollable content.
 *
 * Compose/Material 3 has no built-in visible scrollbar for `Modifier.verticalScroll` or
 * `LazyColumn`, so this draws one directly: a translucent rounded bar on the trailing edge whose
 * height reflects how much of the content is visible and whose position reflects how far the
 * user has scrolled. It is purely decorative (no drag-to-scroll) - the goal is just to give the
 * user a visual sense of "there is more below" on the longer screens (History, Diagnostics log,
 * Settings, About).
 *
 * Two overloads are needed because [ScrollState] (used by `verticalScroll`) and [LazyListState]
 * (used by `LazyColumn`) share no common interface for scroll progress.
 */
package app.notificationbridge.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val MIN_THUMB_FRACTION = 0.1f
private typealias ThumbGeometry = Pair<Float, Float>

@Composable
fun Modifier.verticalScrollbar(
    state: ScrollState,
    width: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
): Modifier {
    val widthPx = with(LocalDensity.current) { width.toPx() }
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
        thumb.value?.let { (thumbHeight, thumbY) ->
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width - widthPx, thumbY),
                size = Size(widthPx, thumbHeight),
                cornerRadius = CornerRadius(widthPx / 2)
            )
        }
    }
}

@Composable
fun Modifier.verticalScrollbar(
    state: LazyListState,
    width: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
): Modifier {
    val widthPx = with(LocalDensity.current) { width.toPx() }
    val thumb = remember(state) {
        derivedStateOf<ThumbGeometry?> {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            val visible = info.visibleItemsInfo
            if (total == 0 || visible.isEmpty()) {
                null
            } else {
                val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
                // Item heights vary a lot between screens (for example the settings header and
                // individual app rows). Estimate the item stride from the measured offsets rather
                // than treating every visible item as the same height or positioning by index
                // alone. The first item's partial offset keeps the thumb moving while scrolling
                // through a tall item.
                var strideTotal = 0f
                var strideCount = 0
                for (index in 1 until visible.size) {
                    val stride = (visible[index].offset - visible[index - 1].offset).toFloat()
                    if (stride > 0f) {
                        strideTotal += stride
                        strideCount++
                    }
                }
                val averageStride = if (strideCount > 0) {
                    strideTotal / strideCount
                } else {
                    // When a single tall item fills the viewport (such as the diagnostic
                    // report), using its full height as the average row stride and multiplying
                    // by every list item makes the scrollbar several times too small. Estimate
                    // the unseen rows conservatively while preserving this item's measured size.
                    minOf(visible.first().size.toFloat(), viewport / 3f)
                }
                // Correct the average-based estimate for unusually tall or short items that are
                // currently measured. This matters when a long report is one LazyColumn item:
                // all items can be visible by index even though that report extends past the
                // viewport, and its full measured size must still contribute to the scroll range.
                var measuredAdjustment = 0f
                for (item in visible) {
                    measuredAdjustment += item.size.toFloat() - averageStride
                }
                val contentExtent = (averageStride * total + measuredAdjustment).coerceAtLeast(0f)
                if (viewport <= 0f || contentExtent <= 0f) {
                    null
                } else {
                    val heightFraction =
                        (viewport / contentExtent).coerceIn(MIN_THUMB_FRACTION, 1f)
                    val thumbHeight = viewport * heightFraction
                    val first = visible.first()
                    val estimatedScroll = first.index * averageStride +
                        (info.viewportStartOffset - first.offset)
                    val maxScroll = (contentExtent - viewport).coerceAtLeast(0f)
                    val progress = if (maxScroll == 0f) 0f
                    else (estimatedScroll / maxScroll).coerceIn(0f, 1f)
                    thumbHeight to progress * (viewport - thumbHeight)
                }
            }
        }
    }
    return drawWithContent {
        drawContent()
        thumb.value?.let { (thumbHeight, thumbY) ->
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width - widthPx, thumbY),
                size = Size(widthPx, thumbHeight),
                cornerRadius = CornerRadius(widthPx / 2)
            )
        }
    }
}
