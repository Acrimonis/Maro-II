package ykws.android.maro.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ykws.android.maro.config.AppConfig

/**
 * Section heading for the Settings render model: accent-coloured, 18sp Bold, sentence case,
 * no letter spacing.
 *
 * The title takes the remaining width (`weight(1f)`) and sits vertically centred against the
 * [trailing] slot, which hosts right-aligned controls (e.g. filter icons). An empty [trailing]
 * renders exactly like a title-only header.
 */
@Composable
internal fun SectionHeader(
    title: String,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = ComposeColor(AppConfig.uiAccent),
                fontSize = AppConfig.uiFontSectionSize.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp
            )
        }
        trailing()
    }
}
