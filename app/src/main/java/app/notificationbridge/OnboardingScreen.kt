package app.notificationbridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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

private data class OnboardingStep(val titleRes: Int, val descRes: Int)

private val ONBOARDING_STEPS = listOf(
    OnboardingStep(R.string.onboarding_step1_title, R.string.onboarding_step1_desc),
    OnboardingStep(R.string.onboarding_step2_title, R.string.onboarding_step2_desc),
    OnboardingStep(R.string.onboarding_step3_title, R.string.onboarding_step3_desc),
    OnboardingStep(R.string.onboarding_step4_title, R.string.onboarding_step4_desc),
    OnboardingStep(R.string.onboarding_step5_title, R.string.onboarding_step5_desc),
    OnboardingStep(R.string.onboarding_step6_title, R.string.onboarding_step6_desc)
)

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val step = ONBOARDING_STEPS[stepIndex]
    val isLast = stepIndex == ONBOARDING_STEPS.lastIndex
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
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
        Column(Modifier.weight(1f).verticalScroll(scrollState)) {
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
