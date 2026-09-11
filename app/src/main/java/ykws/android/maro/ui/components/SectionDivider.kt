package ykws.android.maro.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import ykws.android.maro.config.AppConfig

/** Section divider between settings sections. */
@Composable
internal fun SectionDivider() {
    Spacer(Modifier.height(AppConfig.uiDividerGap.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(AppConfig.uiDividerHeight.dp)
            .background(ComposeColor(AppConfig.uiDividerColor))
    )
    Spacer(Modifier.height(AppConfig.uiDividerGap.dp))
}
