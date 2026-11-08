package dev.vetro.modules.demo.motion

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset

object MotionTokens {

    // --- period-defined springs: (ζ, T), mass 1 --------------------------------------------------

    /** Press squeeze: ζ 1.0, T 80 ms. */
    fun <T> springPressIn(): SpringSpec<T> = specSpring(1.00f, 80)
    /** Release, toggle, chevron: ζ 0.86, T 200 ms. */
    fun <T> springSnappy(): SpringSpec<T> = specSpring(0.86f, 200)
    /** Floating dock, scroll button, toast, popping card: ζ 0.88, T 240 ms. */
    fun <T> springSurface(): SpringSpec<T> = specSpring(0.88f, 240)
    /** New object appearing in a list: ζ 0.92, T 320 ms. */
    fun <T> springObject(): SpringSpec<T> = specSpring(0.92f, 320)
    /** Any close: ζ 1.0 (zero bounce), T 160 ms ≈ 65 % of enter. */
    fun <T> springExit(): SpringSpec<T> = specSpring(1.00f, 160)

    // --- menus and sheets --------------------------------------------------------------------------

    /** Dropdown / context menus — slight overshoot. */
    fun <T> menuPop(): SpringSpec<T> = spring(dampingRatio = 0.72f, stiffness = 503.6f)
    /** Bottom sheet / dock window appearing. */
    fun <T> sheetPresent(): SpringSpec<T> = spring(dampingRatio = 0.86f, stiffness = 380f)
    /** Forced dismiss after a fling — critical damping, no bounce. */
    fun <T> sheetDismissForced(): SpringSpec<T> = spring(dampingRatio = 1.0f, stiffness = 700f)
    /** Rubber-band return after overscroll — critical damping. */
    fun <T> overscrollReturn(): SpringSpec<T> = spring(dampingRatio = 1.0f, stiffness = 400f)
    /** Floating bar returning when scrolling up. */
    fun <T> barReveal(): SpringSpec<T> = spring(dampingRatio = 0.9f, stiffness = 500f)
    /** Popup dialog "settling from the top" 1.15 -> 1.0 with a light impact. */
    fun <T> dialogPop(): SpringSpec<T> = spring(dampingRatio = 0.75f, stiffness = 500f)

    // --- large surfaces: windows and full-screen sheets (heavier: longer period) -------------------

    /** Appear: T ≈ 0.46 s, ζ 0.92 — overshoot ≈ 0.1 %, invisible, but the motion is soft. */
    fun <T> largeSurfaceEnter(): SpringSpec<T> = spring(dampingRatio = 0.92f, stiffness = 186.6f)
    /** Leave: critical damping and ≈ 60-65 % of the enter time (T ≈ 0.30 s). */
    fun <T> largeSurfaceExit(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 438.6f)

    /** The same for boundsTransform of a window that grows out of a button. */
    val largeSurfaceEnterBounds: SpringSpec<Rect> =
        spring(dampingRatio = 0.92f, stiffness = 186.6f, visibilityThreshold = Rect.VisibilityThreshold)
    val largeSurfaceExitBounds: SpringSpec<Rect> =
        spring(dampingRatio = 1f, stiffness = 438.6f, visibilityThreshold = Rect.VisibilityThreshold)

    // --- easings for opacity of content ------------------------------------------------------------

    /** Content appears when the shell already has area. */
    val EaseEnter: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    const val EaseEnterMillis: Int = 140
    /** Content fades before the edge of the shell passes through it. */
    val EaseExit: Easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
    const val EaseExitMillis: Int = 100

    /**
     * When the content of a window growing out of a button appears: by then the shell on
     * [largeSurfaceEnterBounds] has covered ≈ 80 % of its path, so text shows almost in place instead
     * of inside a tiny plate.
     */
    const val WindowContentRevealDelayMillis: Int = 200

    // --- notification plates -------------------------------------------------------------------------

    /** Arrival from the top: ζ 0.80, T 380 ms — overshoot ≈ 1.5 %, soft landing. */
    fun <T> notificationArrive(): SpringSpec<T> = specSpring(0.80f, 380)
    /** A half-swiped plate returns: ζ 0.78, T 300 ms — the rest of the throw dies in one wobble. */
    fun <T> notificationRebound(): SpringSpec<T> = specSpring(0.78f, 300)
    /** Fling off the edge: critical damping, T 220 ms — it neither returns nor wobbles. */
    fun <T> notificationFling(): SpringSpec<T> = specSpring(1.00f, 220)
    /** Neighbours take the freed slot: ζ 0.72, T 340 ms — overshoot ≈ 4 %. */
    fun <T> notificationReflow(): SpringSpec<T> = specSpring(0.72f, 340)

    /**
     * Shift of a list item into the freed place. A cascade without timers: every next plate has a period 8 %
     * longer, so the wave runs down by itself without cutting off anybody's velocity.
     */
    fun notificationReflowOffset(order: Int): SpringSpec<IntOffset> {
        val stretch = 1f + 0.08f * order.coerceIn(0, StaggerMaxItems)
        val omega = 2f * Math.PI.toFloat() / (0.340f * stretch)
        return spring(
            dampingRatio = 0.72f,
            stiffness = omega * omega,
            visibilityThreshold = IntOffset.VisibilityThreshold,
        )
    }

    /** Fraction of the width after which a released plate leaves. */
    const val NotificationDismissFraction: Float = 0.32f
    /** A throw faster than this removes the plate even from a short travel (in the direction of the throw). */
    const val NotificationFlingVelocityDpPerSec: Float = 1000f

    /** Content cascade: shift per item and the cap of the queue. */
    const val StaggerMillis: Int = 24
    const val StaggerMaxItems: Int = 6
    /** When content starts to appear relative to the start of the shell. */
    const val ContentRevealDelayMillis: Int = 60

    /** Stiffness from a period: k = (2π / T)² for mass 1. */
    fun stiffnessForPeriod(periodMillis: Int): Float {
        val omega = (2.0 * Math.PI / (periodMillis / 1000.0))
        return (omega * omega).toFloat()
    }

    private fun <T> specSpring(dampingRatio: Float, periodMillis: Int): SpringSpec<T> =
        spring(dampingRatio = dampingRatio, stiffness = stiffnessForPeriod(periodMillis))

    // --- light and colour are short curves ---------------------------------------------------------

    const val DurationFastMillis: Int = 160
    const val DurationStandardMillis: Int = 220
    const val DurationEmphasizedMillis: Int = 300
    fun <T> tweenFast(): FiniteAnimationSpec<T> = tween(DurationFastMillis)
    fun <T> tweenStandard(delayMillis: Int = 0): FiniteAnimationSpec<T> = tween(DurationStandardMillis, delayMillis = delayMillis)
    fun <T> tweenEmphasized(): FiniteAnimationSpec<T> = tween(DurationEmphasizedMillis)

    fun <T> easeEnter(durationMillis: Int = EaseEnterMillis, delayMillis: Int = 0): FiniteAnimationSpec<T> =
        tween(durationMillis, delayMillis = delayMillis, easing = EaseEnter)
    fun <T> easeExit(durationMillis: Int = EaseExitMillis): FiniteAnimationSpec<T> =
        tween(durationMillis, easing = EaseExit)
}
