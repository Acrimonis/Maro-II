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
    val c = ComposeColor(0xFF1565C0)
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
                .background(ComposeColor.White)
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
            Box(Modifier.size(18.dp).clip(CircleShape).background(ComposeColor(0xFF1565C0)).align(Alignment.TopEnd)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("$activeLayerCount", color = ComposeColor.White, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)))
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ArcButtonOverlay — arc buttons positioned absolutely from anchor root pos
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun ArcButtonOverlay(
    expanded: Boolean,
    onDismiss: () -> Unit,
    anchorCenter: Offset?,
    activeLayerCount: Int,
    depthLayerVisible: Boolean, onToggleDepthLayer: () -> Unit,
    regulatedZonesVisible: Boolean, onToggleRegulatedZones: () -> Unit,
    zone300Visible: Boolean, onToggleZone300: () -> Unit,
    lowDepthWarningVisible: Boolean, onToggleLowDepthWarning: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (anchorCenter == null) return

    // Keep the overlay alive during collapse animation after expanded→false
    var keepAlive by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded) {
            keepAlive = true
        } else if (keepAlive) {
            delay(400) // wait for collapse animation (200ms + buffer)
            keepAlive = false
        }
    }
    if (!expanded && !keepAlive) return

    val ax = anchorCenter.x.roundToInt()
    val ay = anchorCenter.y.roundToInt()

    // Scrim — clickable to dismiss
    if (expanded) {
        Box(Modifier.fillMaxSize().clickable(
            remember { MutableInteractionSource() }, null, onClick = onDismiss
        ))
    }

    // Arc buttons
    // R = 80dp, sweep = 180° (90°->270°), 4 buttons -> 60° spacing
    // chord = 2*80*sin(30°) = 80dp -> 16dp gap between 64dp buttons
    arcButton(ax, ay, 80,  90.0, 0, expanded, depthLayerVisible, onToggleDepthLayer) { a -> depthIcon(a) }
    arcButton(ax, ay, 80, 150.0, 1, expanded, regulatedZonesVisible, onToggleRegulatedZones) { a -> regIcon(a) }
    arcButton(ax, ay, 80, 210.0, 2, expanded, zone300Visible, onToggleZone300) { a -> zoneIcon(a) }
    arcButton(ax, ay, 80, 270.0, 3, expanded, lowDepthWarningVisible, onToggleLowDepthWarning) { a -> lowIcon(a) }

    // Dummy anchor — rendered ON TOP of arc buttons at anchor position, fully opaque
    // and visually matches the real anchor (white circle + 3-stripe blue icon + badge).
    // Arc buttons start at the same position, hidden behind this dummy, then
    // emerge from behind it as they fan outward.
    val density = LocalDensity.current
    val hp = with(density) { 32.dp.toPx() }
    val c = ComposeColor(0xFF1565C0)
    Box(
        modifier = Modifier
            .size(64.dp)
            .offset { IntOffset((ax - hp).roundToInt(), (ay - hp).roundToInt()) }
    ) {
        // Clipped circle area — matches real anchor layout exactly
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(ComposeColor.White)
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
        // Badge OUTSIDE the circle clip — matches real anchor exactly
        if (activeLayerCount>0) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(ComposeColor(0xFF1565C0)).align(Alignment.TopEnd)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("$activeLayerCount", color = ComposeColor.White, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)))
                }
            }
        }
    }
}

/** Emit one animated arc button at [angleDeg] from anchor [ax,ay] at radius [Rdp].
 *  Animates from behind the anchor (same center) outward to the arc position.
 *  Collapse reverses all buttons simultaneously (no stagger). */
@Composable
private fun arcButton(
    ax: Int, ay: Int, Rdp: Int, angleDeg: Double, idx: Int,
    expanded: Boolean,
    active: Boolean, onClick: () -> Unit,
    icon: @Composable (Boolean) -> Unit
) {
    val density = LocalDensity.current
    val Rpx = with(density) { Rdp.dp.toPx() }
    val halfBtnPx = with(density) { 32.dp.toPx() }

    val rad = Math.toRadians(angleDeg)
    val tx = ax + (Rpx * cos(rad)).roundToInt() - halfBtnPx.roundToInt()
    val ty = ay + (Rpx * sin(rad)).roundToInt() - halfBtnPx.roundToInt()

    // Start from behind the anchor (exact same position), fan outward
    val startX = ax - halfBtnPx
    val startY = ay - halfBtnPx

    val anim = remember { Animatable(0f) }
    LaunchedEffect(expanded) {
        if (expanded) {
            anim.snapTo(0f)                            // reset to anchor position
            delay(idx * 70L)                           // stagger on expand
            anim.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
        } else {
            anim.animateTo(0f, tween(200, easing = FastOutSlowInEasing)) // no stagger on collapse
        }
    }

    val t = anim.value
    val dx = (startX + (tx - startX) * t).roundToInt()
    val dy = (startY + (ty - startY) * t).roundToInt()

    Button(onClick = onClick,
        modifier = Modifier.size(64.dp).offset { IntOffset(dx, dy) },
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = ComposeColor.White),
        contentPadding = PaddingValues(0.dp)
    ) { icon(active) }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Icon composables
// ═══════════════════════════════════════════════════════════════════════════════

@Composable private fun depthIcon(active:Boolean) {
    val a=if(active)1f else .25f;val c=ComposeColor(0xFF1565C0)
    Canvas(Modifier.size(28.dp)){val w=size.width;val h=size.height;val i=w*.10f;val bh=(h-i*2)/3
        listOf(.6f,.8f,1f).forEachIndexed{n,aa->drawRoundRect(c,Offset(i,i+n*bh),Size(w-i*2,bh-1f),CornerRadius(2f,2f),alpha=a*aa)}}
}

@Composable private fun regIcon(active:Boolean) {
    val a=if(active)1f else .25f;val c=ComposeColor(0xFF1565C0)
    Canvas(Modifier.size(28.dp)){val w=size.width;val cx=w/2f;val cy=size.height/2f;val r=w*.40f
        drawCircle(c,r,Offset(cx,cy),a,style=Stroke(w*.12f))
        val d=r*.5f;drawLine(c,Offset(cx-d,cy+d),Offset(cx+d,cy-d),w*.12f,cap=androidx.compose.ui.graphics.StrokeCap.Round,alpha=a)}
}

@Composable private fun zoneIcon(active:Boolean) {
    val a=if(active)1f else .25f;val c=ComposeColor(0xFF1565C0)
    Canvas(Modifier.size(28.dp)){val w=size.width;val cx=w/2f;val cy=size.height/2f
        drawCircle(c,w*.40f,Offset(cx,cy),a,style=Stroke(w*.10f))
        drawCircle(c,w*.20f,Offset(cx,cy),a)}
}

@Composable private fun lowIcon(active:Boolean) {
    val a=if(active)1f else .25f;val c=ComposeColor(0xFF1565C0)
    Canvas(Modifier.size(28.dp)){val w=size.width;val h=size.height
        val tri=Path().apply{moveTo(w*.50f,h*.06f);lineTo(w*.96f,h*.88f);lineTo(w*.04f,h*.88f);close()}
        drawPath(tri,c,alpha=a)
        drawRoundRect(ComposeColor.White,Offset(w*.455f,h*.34f),Size(w*.09f,h*.28f),CornerRadius(3f,3f),alpha=a)
        drawCircle(ComposeColor.White,w*.055f,Offset(w*.50f,h*.74f),alpha=a)}
}
