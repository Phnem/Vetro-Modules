package dev.vetro.modules.demo

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.vetro.modules.demo.glass.FrostedMaterials
import dev.vetro.modules.demo.motion.MotionTokens
import dev.vetro.modules.demo.ui.DockBar
import dev.vetro.modules.demo.ui.GlassCircle
import dev.vetro.modules.demo.ui.HeaderBar
import dev.vetro.modules.demo.ui.Ic
import dev.vetro.modules.demo.ui.Icon24
import dev.vetro.modules.demo.ui.Icons24
import dev.vetro.modules.demo.ui.LoopClock
import dev.vetro.modules.demo.ui.NotificationPlate
import dev.vetro.modules.demo.ui.Palette
import dev.vetro.modules.demo.ui.PopoverMenu
import dev.vetro.modules.demo.ui.PosterBackground
import dev.vetro.modules.demo.ui.SearchBubble
import dev.vetro.modules.demo.ui.topInset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Loop length of the glass scene, seconds. */
const val GlassLoopSeconds = 8f

/** A button squeezes with springPressIn while pressed and comes back with springSnappy (see the motion guide). */
@Composable
fun pressScale(pressed: Boolean): State<Float> = animateFloatAsState(
    targetValue = if (pressed) 0.84f else 1f,
    animationSpec = if (pressed) MotionTokens.springPressIn() else MotionTokens.springSnappy(),
    label = "press",
)

private class Bubble(val icon: Ic, val size: Dp, val player: Boolean, val x: Dp, val y: Dp, val ax: Dp, val ay: Dp, val m: Int, val ph: Float)

private val Bubbles = listOf(
    Bubble(Icons24.play, 58.dp, true, 40.dp, 380.dp, 34.dp, 60.dp, 1, 0.00f),
    Bubble(Icons24.heart, 46.dp, false, 250.dp, 440.dp, 40.dp, 50.dp, 2, 0.25f),
    Bubble(Icons24.mark, 40.dp, true, 140.dp, 540.dp, 60.dp, 36.dp, 1, 0.55f),
)

/**
 * Scene 1: the frosted glass over busy moving content. Dock capsule, round buttons, floating bubbles,
 * a "..." dropdown and a notification plate. Every glass surface is a sibling of the posters, which carry the backdrop.
 */
@Composable
fun GlassScene(clock: LoopClock, isDark: Boolean) {
    val p = remember(isDark) { Palette(isDark) }
    val backdrop = rememberLayerBackdrop()
    val dock = FrostedMaterials.dock(isDark)
    val player = FrostedMaterials.playerControl()
    val plate = FrostedMaterials.notification(isDark)
    val inset = topInset()

    val menuOpen by remember { derivedStateOf { clock.t in 1.0f..3.7f } }
    val menuProgress = animateFloatAsState(
        targetValue = if (menuOpen) 1f else 0f,
        animationSpec = if (menuOpen) MotionTokens.menuPop() else MotionTokens.sheetDismissForced(),
        label = "menu",
    )
    val morePressed by remember { derivedStateOf { clock.t in 0.88f..0.97f } }
    val plusPressed by remember { derivedStateOf { clock.t in 5.3f..5.39f } }
    val searchPressed by remember { derivedStateOf { clock.t in 6.2f..6.29f } }
    val moreScale = pressScale(morePressed)
    val plusScale = pressScale(plusPressed)
    val searchScale = pressScale(searchPressed)

    Box(Modifier.fillMaxSize()) {
        PosterBackground(clock, p.page, Modifier.fillMaxSize().layerBackdrop(backdrop))

        Bubbles.forEach { b ->
            GlassCircle(
                backdrop, if (b.player) player else dock, b.size,
                Modifier.offset {
                    val a = 2 * PI * (clock.t / clock.period * b.m + b.ph)
                    IntOffset(
                        (b.x.toPx() + sin(a) * b.ax.toPx()).roundToInt(),
                        (b.y.toPx() + (sin(2 * a + 1) * b.ay.toPx() * 0.5 + cos(a) * b.ay.toPx() * 0.5)).roundToInt(),
                    )
                },
            ) { Icon24(b.icon, if (b.player) Color.White else p.ink, size = if (b.size > 50.dp) 28.dp else 24.dp) }
        }

        HeaderBar(backdrop, dock, p, inset) { moreScale.value }
        DockBar(backdrop, dock, p, plusScale = { plusScale.value })
        SearchBubble(backdrop, dock, p) { searchScale.value }

        // notification plate: arrives from the top, is flung away; state is rebuilt on every loop
        key(clock.loop) {
            val arrived by remember { derivedStateOf { clock.t >= 4.4f } }
            val gone by remember { derivedStateOf { clock.t >= 7.0f } }
            val y = animateFloatAsState(if (arrived) 0f else -170f, MotionTokens.notificationArrive(), label = "ntY")
            val x = animateFloatAsState(if (gone) 560f else 0f, MotionTokens.notificationFling(), label = "ntX")
            if (y.value > -169f && x.value < 540f) {
                NotificationPlate(
                    backdrop, plate, p, Color(0xFFFF7A2E), "Frieren · Season 2, Episode 9", "New episode just released", { 1f },
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = inset + 12.dp + 48.dp + 16.dp)
                        .graphicsLayer { translationX = x.value.dp.toPx(); translationY = y.value.dp.toPx() },
                )
            }
        }

        if (menuProgress.value > 0.001f) PopoverMenu(backdrop, dock, p, inset) { menuProgress.value }
    }
}
