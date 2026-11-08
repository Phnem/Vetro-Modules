@file:OptIn(ExperimentalSharedTransitionApi::class)

package dev.vetro.modules.demo

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.vetro.modules.demo.glass.FrostedMaterials
import dev.vetro.modules.demo.motion.MotionTokens
import dev.vetro.modules.demo.ui.BlobBackground
import dev.vetro.modules.demo.ui.DockBar
import dev.vetro.modules.demo.ui.HeaderBar
import dev.vetro.modules.demo.ui.Ic
import dev.vetro.modules.demo.ui.Icon24
import dev.vetro.modules.demo.ui.Icons24
import dev.vetro.modules.demo.ui.Label
import dev.vetro.modules.demo.ui.LoopClock
import dev.vetro.modules.demo.ui.NotificationPlate
import dev.vetro.modules.demo.ui.Palette
import dev.vetro.modules.demo.ui.PopoverMenu
import dev.vetro.modules.demo.ui.topInset
import kotlin.math.roundToInt

/** Loop length of the motion scene, seconds. */
const val MotionLoopSeconds = 12f

private const val SettingsKey = "settings_container"

/**
 * Scene 2: the motion system. (1) a window grows out of a dock icon (sharedBounds + scaleToBounds, separate
 * enter/exit springs), (2) the "..." popover, (3) a notification stack with arrive / fling / reflow.
 * The scene script only flips booleans at fixed moments; every movement is a real spring from MotionTokens.
 */
@Composable
fun MotionScene(clock: LoopClock, isDark: Boolean) {
    val p = remember(isDark) { Palette(isDark) }
    val open by remember { derivedStateOf { clock.t in 0.55f..3.5f } }

    SharedTransitionLayout(Modifier.fillMaxSize()) {
        val scope = this
        AnimatedContent(
            targetState = open,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                if (targetState) {
                    // opening: the settings content reveals itself (per-row), Home stays until the window almost covers it
                    ContentTransform(EnterTransition.None, fadeOut(tween(120, delayMillis = 260)), 1f, null)
                } else {
                    ContentTransform(fadeIn(tween(200, delayMillis = 100)), fadeOut(MotionTokens.easeExit()), 0f, null)
                }
            },
            label = "window",
        ) { isOpen ->
            if (isOpen) SettingsWindow(p, scope, this) else HomeLayer(clock, p, scope, this)
        }
    }
}

/** Opening grows the bounds (enter spring), closing shrinks them (exit spring). */
private fun boundsTransformByDirection() = androidx.compose.animation.BoundsTransform { initial, target ->
    if (target.width > initial.width) MotionTokens.largeSurfaceEnterBounds else MotionTokens.largeSurfaceExitBounds
}

@Composable
private fun HomeLayer(clock: LoopClock, p: Palette, sts: SharedTransitionScope, avs: AnimatedVisibilityScope) {
    val backdrop = rememberLayerBackdrop()
    val dock = FrostedMaterials.dock(p.isDark)
    val inset = topInset()

    val settingsPressed by remember { derivedStateOf { clock.t in 0.45f..0.53f } }
    val morePressed by remember { derivedStateOf { clock.t in 4.7f..4.79f } }
    val settingsScale = pressScale(settingsPressed)
    val moreScale = pressScale(morePressed)
    val menuOpen by remember { derivedStateOf { clock.t in 4.8f..7.0f } }
    val menuProgress = animateFloatAsState(
        targetValue = if (menuOpen) 1f else 0f,
        animationSpec = if (menuOpen) MotionTokens.menuPop() else MotionTokens.sheetDismissForced(),
        label = "menu",
    )

    val bounds = boundsTransformByDirection()
    val settingsBox = with(sts) {
        Modifier.sharedBounds(
            rememberSharedContentState(key = SettingsKey),
            animatedVisibilityScope = avs,
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
            boundsTransform = bounds,
            // sharedBounds cross-fades by default (fadeIn/fadeOut): the growing window would look half transparent
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            clipInOverlayDuringTransition = OverlayClip(CircleShape),
        )
    }

    Box(Modifier.fillMaxSize()) {
        BlobBackground(clock, p.page, Modifier.fillMaxSize().layerBackdrop(backdrop))
        HeaderBar(backdrop, dock, p, inset) { moreScale.value }
        DockBar(backdrop, dock, p, settingsBox = settingsBox, settingsScale = { settingsScale.value })
        NotificationStack(clock, backdrop, p, inset)
        if (menuProgress.value > 0.001f) PopoverMenu(backdrop, dock, p, inset) { menuProgress.value }
    }
}

private class Note(val accent: Color, val title: String, val sub: String)

private val Notes = listOf(
    Note(Color(0xFFFF7A2E), "Frieren · Season 2, Episode 9", "New episode just released"),
    Note(Color(0xFF5B5BF0), "Dungeon Meshi · Episode 14", "Subtitles are ready"),
    Note(Color(0xFF00B87A), "Solo Leveling · Episode 7", "Added to your collection"),
)
// card 0 is the newest (top of the stack): the oldest arrives first, every newer plate pushes the others back
private val Arrive = floatArrayOf(8.4f, 8.0f, 7.6f)
private val Fling = floatArrayOf(9.9f, 10.6f, 11.2f)

/**
 * Plates arrive one by one (each new one lands on top and pushes the rest back one slot), then are flung from the top;
 * the ones behind slide forward on the reflow spring (period +8 % per slot). The material is the same glass for every
 * plate (switching glass -> opaque while a plate moves back is visible as the glass popping out); the text of the plates
 * lying behind is faded instead, so nothing shows through the top plate.
 */
@Composable
private fun BoxScope.NotificationStack(clock: LoopClock, backdrop: Backdrop, p: Palette, inset: androidx.compose.ui.unit.Dp) {
    val material = FrostedMaterials.notification(p.isDark)
    key(clock.loop) {
        for (i in Notes.indices.reversed()) key(i) {
            val arrived by remember { derivedStateOf { clock.t >= Arrive[i] } }
            val flung by remember { derivedStateOf { clock.t >= Fling[i] } }
            // plates in front of this one: newer (lower index), already arrived and not yet flung
            val inFront by remember { derivedStateOf { (0 until i).count { clock.t >= Arrive[it] && clock.t < Fling[it] } } }
            val y = animateFloatAsState(if (arrived) 0f else -170f, MotionTokens.notificationArrive(), label = "y$i")
            val x = animateFloatAsState(if (flung) 560f else 0f, MotionTokens.notificationFling(), label = "x$i")
            val slot = animateFloatAsState(
                targetValue = inFront.toFloat(),
                animationSpec = spring(0.72f, MotionTokens.stiffnessForPeriod((340 * (1 + 0.08f * i)).roundToInt())),
                label = "slot$i",
            )
            if (y.value > -169f && x.value < 540f) {
                val note = Notes[i]
                NotificationPlate(
                    backdrop, material, p, note.accent, note.title, note.sub,
                    { (1f - slot.value * 1.4f).coerceIn(0f, 1f) },
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = inset + 12.dp + 48.dp + 16.dp)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            val s = 1f - 0.045f * slot.value
                            scaleX = s
                            scaleY = s
                            translationX = x.value.dp.toPx()
                            translationY = (12f * slot.value + y.value).dp.toPx()
                        },
                )
            }
        }
    }
}

@Composable
private fun reveal(avs: AnimatedVisibilityScope, index: Int): State<Float> = avs.transition.animateFloat(
    transitionSpec = {
        if (targetState == EnterExitState.Visible) {
            MotionTokens.easeEnter(
                delayMillis = MotionTokens.WindowContentRevealDelayMillis + MotionTokens.StaggerMillis * index.coerceAtMost(MotionTokens.StaggerMaxItems),
            )
        } else {
            MotionTokens.easeExit()
        }
    },
    label = "reveal$index",
) { if (it == EnterExitState.Visible) 1f else 0f }

private class SettingsRow(val icon: Ic, val color: Color, val label: String, val toggle: Boolean = false)

private val SettingsGroups = listOf(
    listOf(SettingsRow(Icons24.sun, Color(0xFFE85002), "Appearance"), SettingsRow(Icons24.globe, Color(0xFF0A84FF), "Language"), SettingsRow(Icons24.bell, Color(0xFFFF9F0A), "Notifications", true)),
    listOf(SettingsRow(Icons24.sync, Color(0xFF34C759), "Sync"), SettingsRow(Icons24.down, Color(0xFF5E5CE6), "Updates"), SettingsRow(Icons24.disk, Color(0xFF8E8E93), "Storage")),
    listOf(SettingsRow(Icons24.info, Color(0xFF64D2FF), "About Vetro")),
)

@Composable
private fun SettingsWindow(p: Palette, sts: SharedTransitionScope, avs: AnimatedVisibilityScope) {
    val shape = RoundedCornerShape(28.dp)
    val inset = topInset()
    val bounds = boundsTransformByDirection()
    val shared = with(sts) {
        Modifier.sharedBounds(
            rememberSharedContentState(key = SettingsKey),
            animatedVisibilityScope = avs,
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(),
            boundsTransform = bounds,
            // sharedBounds cross-fades by default (fadeIn/fadeOut): the growing window would look half transparent
            enter = EnterTransition.None,
            exit = ExitTransition.None,
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
    Box(Modifier.fillMaxSize().then(shared).clip(shape).background(p.surface)) {
        Column(Modifier.fillMaxSize().padding(top = inset + 24.dp, start = 16.dp, end = 16.dp)) {
            val titleAlpha = reveal(avs, 0)
            Label(
                "Settings", 34.sp, FontWeight.ExtraBold, p.ink,
                Modifier.padding(start = 8.dp, top = 40.dp, bottom = 24.dp).graphicsLayer { alpha = titleAlpha.value },
                spacing = (-0.8).sp,
            )
            var index = 1
            SettingsGroups.forEach { group ->
                Column(Modifier.fillMaxWidth().padding(bottom = 28.dp).clip(RoundedCornerShape(18.dp)).background(p.row)) {
                    group.forEachIndexed { r, row ->
                        val a = reveal(avs, index++)
                        Row(
                            Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 14.dp)
                                .graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 8.dp.toPx() },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(row.color), contentAlignment = Alignment.Center) {
                                Icon24(row.icon, Color.White, size = 18.dp, strokeWidth = 2f)
                            }
                            Spacer(Modifier.width(14.dp))
                            Label(row.label, 16.sp, FontWeight.Medium, p.ink)
                            Spacer(Modifier.weight(1f))
                            if (row.toggle) {
                                Box(Modifier.size(46.dp, 28.dp).clip(CircleShape).background(Color(0xFF34C759))) {
                                    Box(Modifier.align(Alignment.CenterEnd).padding(end = 3.dp).size(22.dp).clip(CircleShape).background(Color.White))
                                }
                            } else {
                                Icon24(Icons24.chev, p.ink2, size = 16.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}
