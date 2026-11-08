package dev.vetro.modules.demo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import dev.vetro.modules.demo.glass.FrostedMaterial
import dev.vetro.modules.demo.glass.frostedGlass

class Palette(val isDark: Boolean) {
    val page = if (isDark) Color(0xFF0A0A0B) else Color(0xFFEDEDF0)
    val ink = if (isDark) Color.White.copy(alpha = 0.92f) else Color(0xFF1C1C1E)
    val ink2 = if (isDark) Color.White.copy(alpha = 0.62f) else Color(0xFF1C1C1E).copy(alpha = 0.58f)
    val surface = if (isDark) Color(0xFF141416) else Color(0xFFF4F4F7)
    val row = if (isDark) Color(0xFF1F1F22) else Color.White
    val separator = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.07f)
    val brand = Color(0xFFE85002)
    val brandBright = Color(0xFFFF6A1F)
}

@Composable
fun Label(
    text: String,
    size: TextUnit,
    weight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier,
    spacing: TextUnit = TextUnit.Unspecified,
) = BasicText(
    text,
    modifier,
    style = TextStyle(color = color, fontSize = size, fontWeight = weight, letterSpacing = spacing),
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
)

/** Height of the camera cut-out; the header sits under it. */
@Composable
fun topInset(): Dp = with(LocalDensity.current) { WindowInsets.displayCutout.getTop(this).toDp() }

@Composable
fun GlassCircle(
    backdrop: Backdrop,
    material: FrostedMaterial,
    size: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) = Box(
    modifier.size(size).frostedGlass(backdrop, CircleShape, material),
    contentAlignment = Alignment.Center,
    content = content,
)

/** Back bubble, title capsule and "more" bubble: the floating glass header. */
@Composable
fun BoxScope.HeaderBar(backdrop: Backdrop, material: FrostedMaterial, p: Palette, topInset: Dp, moreScale: () -> Float) {
    Box(
        Modifier
            .align(Alignment.TopStart)
            .fillMaxWidth()
            .padding(top = topInset + 12.dp, start = 16.dp, end = 16.dp)
            .height(48.dp),
    ) {
        GlassCircle(backdrop, material, 48.dp, Modifier.align(Alignment.CenterStart)) {
            Icon24(Icons24.back, p.ink)
        }
        Box(
            Modifier
                .align(Alignment.Center)
                .width(240.dp)
                .height(48.dp)
                .frostedGlass(backdrop, RoundedCornerShape(24.dp), material),
            contentAlignment = Alignment.Center,
        ) { Label("Collection", 18.sp, FontWeight.ExtraBold, p.ink) }
        GlassCircle(
            backdrop, material, 48.dp,
            Modifier.align(Alignment.CenterEnd).graphicsLayer { moreScale().let { scaleX = it; scaleY = it } },
        ) { Icon24(Icons24.dots, p.ink) }
    }
}

@Composable
private fun DockItem(icon: Ic, label: String, tint: Color, iconBox: Modifier, scale: () -> Float) {
    Column(Modifier.width(60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(36.dp)
                .then(iconBox)
                .graphicsLayer { scale().let { scaleX = it; scaleY = it } },
            contentAlignment = Alignment.Center,
        ) { Icon24(icon, tint) }
        Label(label, 10.sp, FontWeight.SemiBold, tint, spacing = 0.2.sp)
    }
}

/**
 * The glass dock capsule. [settingsBox] lets the caller turn the Settings icon into a shared-transition source.
 */
@Composable
fun BoxScope.DockBar(
    backdrop: Backdrop,
    material: FrostedMaterial,
    p: Palette,
    settingsBox: Modifier = Modifier,
    settingsScale: () -> Float = { 1f },
    plusScale: () -> Float = { 1f },
) {
    Row(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 22.dp)
            .size(300.dp, 66.dp)
            .frostedGlass(backdrop, RoundedCornerShape(33.dp), material),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockItem(Icons24.home, "Home", p.brand, Modifier) { 1f }
        DockItem(Icons24.stats, "Stats", p.ink, Modifier) { 1f }
        DockItem(Icons24.plus, "Add", p.ink, Modifier, plusScale)
        DockItem(Icons24.tune, "Settings", p.ink, settingsBox, settingsScale)
    }
}

@Composable
fun BoxScope.SearchBubble(backdrop: Backdrop, material: FrostedMaterial, p: Palette, scale: () -> Float) {
    GlassCircle(
        backdrop, material, 52.dp,
        Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 22.dp, bottom = 100.dp)
            .graphicsLayer { scale().let { scaleX = it; scaleY = it } },
    ) { Icon24(Icons24.search, p.ink) }
}

@Composable
private fun MenuRow(icon: Ic, text: String, tint: Color, alpha: () -> Float) {
    Row(
        Modifier.height(48.dp).padding(horizontal = 18.dp).graphicsLayer { this.alpha = alpha() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon24(icon, tint, size = 20.dp)
        Spacer(Modifier.width(12.dp))
        Label(text, 15.sp, FontWeight.Medium, tint)
    }
}

/**
 * The "..." popover: graphicsLayer scale 0.82 -> 1 about the corner, the glass inside the layer, panel alpha
 * saturating at progress 0.25, rows fading in with a stagger. [progress] is the spring output (menuPop on open,
 * forced dismiss on close).
 */
@Composable
fun BoxScope.PopoverMenu(backdrop: Backdrop, material: FrostedMaterial, p: Palette, topInset: Dp, progress: () -> Float) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .align(Alignment.TopEnd)
            .padding(top = topInset + 12.dp + 48.dp + 8.dp, end = 16.dp)
            .graphicsLayer {
                transformOrigin = TransformOrigin(1f, 0f)
                val pr = progress()
                val s = 0.82f + 0.18f * pr
                scaleX = s
                scaleY = s
                // The glass itself becomes opaque within the first quarter of the motion. Fading the whole panel
                // with alpha = progress shows the SHARP backdrop through a half-transparent glass for several frames,
                // which reads as the glass popping in. Only the rows fade slowly (see MenuRow).
                alpha = (pr / 0.25f).coerceIn(0f, 1f)
            }
            .width(232.dp)
            .clip(shape)
            .frostedGlass(backdrop, shape, material)
            .padding(vertical = 6.dp),
    ) {
        val rows = listOf(
            Triple(Icons24.check, "Mark as watched", p.ink),
            Triple(Icons24.image, "Change cover", p.ink),
            Triple(Icons24.unlink, "Wrong source", p.brandBright),
            Triple(Icons24.lock, "Move to secret folder", p.ink),
        )
        rows.forEachIndexed { i, (icon, text, tint) ->
            MenuRow(icon, text, tint) { ((progress() - 0.15f - 0.05f * i) / 0.4f).coerceIn(0f, 1f) }
        }
    }
}

/** One notification plate; [contentAlpha] hides the text while the plate lies under another one. */
@Composable
fun NotificationPlate(
    backdrop: Backdrop,
    material: FrostedMaterial,
    p: Palette,
    accent: Color,
    title: String,
    subtitle: String,
    contentAlpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(26.dp)
    Box(modifier.width(342.dp).height(72.dp).clip(shape).frostedGlass(backdrop, shape, material)) {
        Box(
            Modifier
                .padding(start = 14.dp, top = 13.dp)
                .size(46.dp)
                .graphicsLayer { alpha = contentAlpha() }
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.8f)))),
        )
        Label(title, 14.5.sp, FontWeight.Bold, p.ink, Modifier.padding(start = 72.dp, top = 14.dp, end = 56.dp).graphicsLayer { alpha = contentAlpha() })
        Label(subtitle, 13.sp, FontWeight.Normal, p.ink2, Modifier.padding(start = 72.dp, top = 38.dp, end = 14.dp).graphicsLayer { alpha = contentAlpha() })
        Label("now", 11.sp, FontWeight.Normal, p.ink2, Modifier.align(Alignment.TopEnd).padding(top = 15.dp, end = 16.dp).graphicsLayer { alpha = contentAlpha() })
    }
}
