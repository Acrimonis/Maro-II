package ykws.android.maro.ui.markers.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.config.AppConfig

// ─────────────────────────────────────────────────────────────────────────────
// WizardButtonRow — Previous / Next / Finish (text pills)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun WizardButtonRow(
    isFirstStep: Boolean,
    isLastStep: Boolean,
    canFinish: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit
) {
    val accentBg = ComposeColor(AppConfig.uiSettingsAccent)
    val accentFg = ComposeColor(AppConfig.uiSettingsTextPrimary)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous (hidden on first step)
        if (!isFirstStep) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentBg)
                    .clickable { onPrevious() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Previous",
                    color = accentFg,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        // Next (hidden on last step)
        if (!isLastStep) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentBg)
                    .clickable { onNext() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Next",
                    color = accentFg,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        // Finish (always present, dimmed when invalid)
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(accentBg)
                .then(if (!canFinish) Modifier.alpha(0.4f) else Modifier)
                .then(if (canFinish) Modifier.clickable { onFinish() } else Modifier)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Finish",
                color = accentFg,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
