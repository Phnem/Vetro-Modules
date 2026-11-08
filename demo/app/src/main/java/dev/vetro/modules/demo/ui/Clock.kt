package dev.vetro.modules.demo.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos

/**
 * Loop clock of a demo scene. Everything that happens on screen is a function of [t] (seconds inside the loop),
 * so the recording loops without a seam: a window of exactly [period] seconds starts and ends in the same state.
 * The scene only decides WHEN something is triggered; the motion itself is produced by the real springs.
 *
 * Read [t] inside draw/layout lambdas (offset { }, graphicsLayer { }) to avoid recomposing every frame.
 */
class LoopClock(val period: Float) {
    var t by mutableFloatStateOf(0f)
        internal set
    var loop by mutableIntStateOf(0)
        internal set
}

@Composable
fun rememberLoopClock(periodSeconds: Float): LoopClock {
    val clock = remember { LoopClock(periodSeconds) }
    LaunchedEffect(clock) {
        var origin = -1L
        while (true) {
            withFrameNanos { now ->
                if (origin < 0) origin = now
                val elapsed = (now - origin) / 1_000_000_000.0
                clock.loop = (elapsed / periodSeconds).toInt()
                clock.t = (elapsed % periodSeconds).toFloat()
            }
        }
    }
    return clock
}
