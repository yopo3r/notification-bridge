package app.notificationbridge.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * A destructive-action confirmation dialog with motion: it pops in (fade + a slightly bouncy
 * scale), the warning triangle pulses gently to draw the eye, and it fades and shrinks out
 * before [onConfirm] / [onDismiss] run, so the caller removes it only after the exit finished.
 *
 * The pulse is an infinite animation, which Compose stops by itself when the user has turned
 * system animations off, so nothing needs to be special-cased for that setting.
 */
@Composable
fun WarningDialog(
    title: String,
    warning: String,
    body: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    // Starts hidden and flips to visible on the first frame, which plays the enter animation.
    val visibility = remember { MutableTransitionState(false).apply { targetState = true } }
    var afterExit by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun closeThen(action: () -> Unit) {
        if (afterExit != null) return // a close is already running (e.g. double tap)
        afterExit = action
        visibility.targetState = false
    }

    LaunchedEffect(visibility.currentState, visibility.isIdle) {
        if (!visibility.currentState && visibility.isIdle) afterExit?.invoke()
    }

    Dialog(onDismissRequest = { closeThen(onDismiss) }) {
        AnimatedVisibility(
            visibleState = visibility,
            enter = fadeIn(tween(160)) + scaleIn(
                initialScale = 0.8f,
                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)
            ),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.92f, animationSpec = tween(120))
        ) {
            Surface(
                shape = AlertDialogDefaults.shape,
                color = AlertDialogDefaults.containerColor,
                tonalElevation = AlertDialogDefaults.TonalElevation
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PulsingWarningSign(Modifier.align(Alignment.CenterHorizontally))
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = AlertDialogDefaults.titleContentColor
                    )
                    Text(
                        warning,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AlertDialogDefaults.textContentColor
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { closeThen(onDismiss) }) { Text(dismissText) }
                        TextButton(
                            onClick = { closeThen(onConfirm) },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text(confirmText) }
                    }
                }
            }
        }
    }
}

/** A decorative "⚠" that breathes (scale 1.0 to 1.2). Hidden from accessibility services. */
@Composable
private fun PulsingWarningSign(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "warningPulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "warningPulseScale"
    )
    Text(
        "⚠",
        style = MaterialTheme.typography.displaySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier.scale(pulse).clearAndSetSemantics { }
    )
}
