package ykws.android.maro.ui.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

// ═══════════════════════════════════════════════════════════════════════════════
// ArcAnchorButton — reports root position for arc overlay
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun ArcAnchorButton(
    activeLayerCount: Int,
    onClick: () -> Unit,
    onPositionChanged: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = ComposeColor(ZoneConfig.uiArcAnchorColor)
    // Outer Box(64dp) holds layout; the circle clip is on an inner layer so the badge
    // (sibling at this level) renders outside the clip and is never cropped.
    Box(modifier = modifier.size(64.dp).onGloballyPositioned { co ->
        val p = co.positionInRoot(); val s = co.size
        onPositionChanged(Offset(p.x + s.width/2f, p.y + s.height/2f))
    }) {
        // Clipped circle button area
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(ComposeColor(ZoneConfig.uiArcAnchorBackground))
                .clickable(onClick = onClick)
        ) {
            Box(Modifier.size(32.dp).align(Alignment.Center)) {
                Canvas(Modifier.fillMaxSize()) {
                    val w=size.width;val h=size.height;val i=w*0.12f;val lh=h*0.22f
                    // Flipped vertically: narrow stripe at top, full-width at bottom
                    drawRoundRect(c,Offset(i*0.5f,h*0.02f),Size(w-i,lh),CornerRadius(3f,3f),alpha=0.5f)
                    drawRoundRect(c,Offset(i*0.25f,h*0.48f-lh/2),Size(w-i*0.5f,lh),CornerRadius(3f,3f),alpha=0.7f)
                    drawRoundRect(c,Offset(0f,h-lh-i*0.5f),Size(w,lh),CornerRadius(3f,3f),alpha=1f)
                }
            }
        }
        // Badge OUTSIDE the circle clip — renders fully visible at top-right
        if (activeLayerCount>0) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(ComposeColor(ZoneConfig.uiArcAnchorColor)).align(Alignment.TopEnd)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("$activeLayerCount", color = ComposeColor(ZoneConfig.uiArcAnchorBackground), fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)))
                }
            }
        }
    }
}

