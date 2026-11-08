package dev.vetro.modules.demo

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.vetro.modules.demo.ui.rememberLoopClock

/**
 * Demo host used to record the videos on a real phone.
 *
 *   adb shell am start -n dev.vetro.modules.demo/.MainActivity --es scene glass  --es theme dark
 *   adb shell am start -n dev.vetro.modules.demo/.MainActivity --es scene motion --es theme light
 *
 * The scene plays in a loop by itself (no touches needed); system bars are hidden and the screen stays on.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scene = intent.getStringExtra("scene") ?: "glass"
        val isDark = (intent.getStringExtra("theme") ?: "dark") != "light"

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        window.setBackgroundDrawable(ColorDrawable(if (isDark) AndroidColor.parseColor("#0A0A0B") else AndroidColor.parseColor("#EDEDF0")))
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            val motion = scene == "motion"
            val clock = rememberLoopClock(if (motion) MotionLoopSeconds else GlassLoopSeconds)
            Box(Modifier.fillMaxSize()) {
                if (motion) MotionScene(clock, isDark) else GlassScene(clock, isDark)
                // Loop-start marker for the recording script: a magenta dot at the left edge during the first 50 ms
                // of every loop. The script finds it, cuts exactly one loop, and crops the edge it sits on.
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .size(4.dp)
                        .graphicsLayer { alpha = if (clock.t < 0.05f) 1f else 0f }
                        .background(Color(0xFFFF00FF)),
                )
            }
        }
    }
}
