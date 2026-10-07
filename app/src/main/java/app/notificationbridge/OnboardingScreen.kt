package app.notificationbridge

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.notificationbridge.ui.InsetSurface
import app.notificationbridge.ui.PageHeader

private data class OnboardingStep(val titleRes: Int, val descRes: Int, val imageRes: Int? = null)

// Light, fixed backdrop for the illustrations: they have transparent backgrounds and dark phone
// bodies, which would disappear against the dark theme's background.
private val ILLUSTRATION_BACKDROP = Color(0xFFE7EEEA)
private val STAGE_MIN_HEIGHT = 96.dp
private val STAGE_MAX_HEIGHT = 220.dp
private const val STAGE_PORTRAIT_FRACTION = 0.30f
private const val STAGE_LANDSCAPE_WEIGHT = 0.4f

private val ONBOARDING_STEPS = listOf(
    OnboardingStep(R.string.onboarding_step1_title, R.string.onboarding_step1_desc, R.drawable.onboarding_1),
    OnboardingStep(R.string.onboarding_step2_title, R.string.onboarding_step2_desc, R.drawable.onboarding_2),
    OnboardingStep(R.string.onboarding_step3_title, R.string.onboarding_step3_desc, R.drawable.onboarding_3),
    OnboardingStep(R.string.onboarding_step4_title, R.string.onboarding_step4_desc, R.drawable.onboarding_4),
    OnboardingStep(R.string.onboarding_step5_title, R.string.onboarding_step5_desc, R.drawable.onboarding_5),
    OnboardingStep(R.string.onboarding_step6_title, R.string.onboarding_step6_desc, R.drawable.onboarding_1)
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val step = ONBOARDING_STEPS[stepIndex]
    val isLast = stepIndex == ONBOARDING_STEPS.lastIndex
    val scrollState = rememberScrollState()

    // The activity is edge-to-edge and this screen is drawn outside a Scaffold, so nothing else
    // reserves room for the status bar, the on-screen navigation buttons or a display cutout.
    // Without this, the bottom row (Back / Next) sits underneath the system buttons.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.onboarding_step_indicator, stepIndex + 1, ONBOARDING_STEPS.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onFinished) { Text(stringResource(R.string.onboarding_skip)) }
        }
        LinearProgressIndicator(
            progress = { (stepIndex + 1).toFloat() / ONBOARDING_STEPS.size },
            modifier = Modifier.fillMaxWidth()
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val image = step.imageRes
            val landscape = maxWidth > maxHeight
            val textCard: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier.verticalScroll(scrollState)) {
                    InsetSurface {
                        Column(Modifier.fillMaxWidth().padding(22.dp)) {
                            PageHeader(stringResource(step.titleRes))
                            Text(
                                stringResource(step.descRes),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (landscape) {
                // Wide/short window: illustration on the left, text on the right. Both panes use
                // the full available height, so the illustration never competes with the text
                // for vertical space.
                Row(
                    Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    if (image != null) {
                        IllustrationStage(image, Modifier.weight(STAGE_LANDSCAPE_WEIGHT).fillMaxHeight())
                    }
                    textCard(Modifier.weight(1f - STAGE_LANDSCAPE_WEIGHT).fillMaxHeight())
                }
            } else {
                // Same slot size on every page, so the illustration stays put while paging.
                val stageHeight: Dp = (maxHeight * STAGE_PORTRAIT_FRACTION)
                    .coerceIn(STAGE_MIN_HEIGHT, STAGE_MAX_HEIGHT)
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (image != null) {
                        IllustrationStage(image, Modifier.fillMaxWidth().height(stageHeight))
                    }
                    textCard(Modifier.weight(1f))
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (stepIndex > 0) {
                TextButton(onClick = { stepIndex-- }) { Text(stringResource(R.string.onboarding_back)) }
            } else {
                Spacer(Modifier.width(64.dp))
            }
            Button(onClick = { if (isLast) onFinished() else stepIndex++ }) {
                Text(stringResource(if (isLast) R.string.onboarding_finish else R.string.onboarding_next))
            }
        }
    }
}

/**
 * A fixed-size slot for an onboarding illustration. The image is scaled to fit (never cropped or
 * stretched) and centred, so illustrations with different aspect ratios occupy the same place.
 * It is decorative: the step's title and description carry the meaning.
 */
@Composable
private fun IllustrationStage(imageRes: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(ILLUSTRATION_BACKDROP),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().padding(16.dp)
        )
    }
}
