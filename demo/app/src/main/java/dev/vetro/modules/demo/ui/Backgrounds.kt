package dev.vetro.modules.demo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private class Poster(
    val height: Dp,
    val c0: Color,
    val c1: Color,
    val name: String,
    val episode: Int,
    val a: Float,
    val b: Float,
    val c: Float,
)

private val Palettes = listOf(
    0xFFFF7A2E to 0xFFE8185A, 0xFF12C2E9 to 0xFF5B5BF0, 0xFFB6F500 to 0xFF00B87A, 0xFFFFC93C to 0xFFFF5F6D,
    0xFF7F5BFF to 0xFFFF4FA3, 0xFF00D4B0 to 0xFF0A7CFF, 0xFFFF9A8B to 0xFFFF5E3A, 0xFF2DE1C2 to 0xFF1F3A8A,
)
private val Names = listOf("NOVA", "KAIJU", "ECHO", "SORA", "RIN", "MIKU", "ONI", "AKIRA", "LUNA", "HANA", "YUKI", "ZEN")

private fun makePosters(seed: Long, count: Int): List<Poster> {
    val rnd = java.util.Random(seed)
    return List(count) {
        val (c0, c1) = Palettes[rnd.nextInt(Palettes.size)]
        Poster(
            height = (190 + rnd.nextInt(90)).dp,
            c0 = Color(c0), c1 = Color(c1),
            name = Names[rnd.nextInt(Names.size)],
            episode = 1 + rnd.nextInt(24),
            a = rnd.nextFloat(), b = rnd.nextFloat(), c = rnd.nextFloat(),
        )
    }
}

private val PosterGap = 12.dp

/**
 * Two columns of colourful posters scrolling in opposite directions: busy, high-contrast content for the glass to diffuse.
 * Moves with offset { } (never with a changing graphicsLayer around the backdrop provider - that flattens the glass).
 * Put `Modifier.layerBackdrop(backdrop)` on [modifier].
 */
@Composable
fun PosterBackground(clock: LoopClock, page: Color, modifier: Modifier = Modifier) {
    val left = remember { makePosters(11L, 6) }
    val right = remember { makePosters(23L, 6) }
    Box(modifier.background(page)) {
        PosterColumn(left, clock, up = true, Modifier.align(Alignment.TopStart).padding(start = 14.dp))
        PosterColumn(right, clock, up = false, Modifier.align(Alignment.TopEnd).padding(end = 14.dp))
    }
}

@Composable
private fun PosterColumn(posters: List<Poster>, clock: LoopClock, up: Boolean, modifier: Modifier) {
    val periodDp = posters.sumOf { (it.height + PosterGap).value.toDouble() }.toFloat()
    Column(
        modifier
            .width(177.dp)
            .wrapContentHeight(align = Alignment.Top, unbounded = true)
            .offset {
                val period = periodDp.dp.toPx()
                val u = clock.t / clock.period
                IntOffset(0, (if (up) -u * period else -period + u * period).roundToInt())
            },
    ) {
        repeat(2) { posters.forEach { PosterCard(it) } }
    }
}

@Composable
private fun PosterCard(p: Poster) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(bottom = PosterGap)
            .height(p.height)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(p.c0, p.c1)))
            .drawBehind {
                val d = (110 + p.b * 70).dp.toPx()
                drawCircle(Color.White.copy(alpha = 0.22f), d / 2, Offset(size.width + (30 + p.c * 30).dp.toPx() - d / 2, (40 + p.a * 40).dp.toPx() + d / 2))
                drawCircle(Color.Black.copy(alpha = 0.18f), 30.dp.toPx(), Offset((10 + p.c * 50).dp.toPx() + 30.dp.toPx(), (20 + p.b * 30).dp.toPx() + 30.dp.toPx()))
                var x = -size.height
                while (x < size.width) {
                    drawLine(Color.White.copy(alpha = 0.22f), Offset(x, size.height), Offset(x + size.height * 0.47f, 0f), strokeWidth = 3.dp.toPx())
                    x += 14.dp.toPx()
                }
                for (i in 0 until 3) {
                    drawRoundRect(
                        Color.White.copy(alpha = 0.75f),
                        Offset(size.width - 12.dp.toPx() - 54.dp.toPx() * (if (i == 0) 1f else if (i == 1) 0.7f else 0.45f), 14.dp.toPx() + i * 9.dp.toPx()),
                        androidx.compose.ui.geometry.Size(54.dp.toPx() * (if (i == 0) 1f else if (i == 1) 0.7f else 0.45f), 4.dp.toPx()),
                        androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
                    )
                }
            },
    ) {
        BasicText(
            p.name,
            Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 34.dp),
            style = TextStyle(color = Color.White, fontSize = 46.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp),
        )
        BasicText(
            "EP ${p.episode.toString().padStart(2, '0')} · 2026",
            Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 12.dp),
            style = TextStyle(color = Color.White.copy(alpha = 0.92f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
        )
    }
}

private class Blob(val color: Color, val d: Dp, val x: Dp, val y: Dp, val ax: Dp, val ay: Dp, val m: Int, val ph: Float, val alpha: Float)
private class Tile(val c0: Color, val c1: Color, val w: Dp, val h: Dp, val x: Dp, val y: Dp, val ax: Dp, val ay: Dp, val ph: Float, val rot: Float)

private val Blobs = listOf(
    Blob(Color(0xFFE85002), 300.dp, 20.dp, 90.dp, 70.dp, 90.dp, 1, 0.00f, 0.85f),
    Blob(Color(0xFF7F5BFF), 340.dp, 170.dp, 330.dp, 80.dp, 70.dp, 1, 0.33f, 0.75f),
    Blob(Color(0xFF00C2A8), 280.dp, 40.dp, 520.dp, 90.dp, 60.dp, 1, 0.66f, 0.75f),
    Blob(Color(0xFFFF4FA3), 220.dp, 220.dp, 600.dp, 60.dp, 80.dp, 2, 0.10f, 0.70f),
)
private val Tiles = listOf(
    Tile(Color(0xFFFFC93C), Color(0xFFFF5F6D), 150.dp, 210.dp, 28.dp, 190.dp, 24.dp, 40.dp, 0.20f, -8f),
    Tile(Color(0xFF12C2E9), Color(0xFF5B5BF0), 150.dp, 250.dp, 210.dp, 150.dp, 20.dp, 46.dp, 0.70f, 7f),
    Tile(Color(0xFFB6F500), Color(0xFF00B87A), 160.dp, 190.dp, 120.dp, 470.dp, 30.dp, 36.dp, 0.45f, -4f),
)

/** Calm drifting colour: the background of the motion scene (the motion itself is the star there). */
@Composable
fun BlobBackground(clock: LoopClock, page: Color, modifier: Modifier = Modifier) {
    Box(modifier.background(page)) {
        Blobs.forEach { b ->
            Box(
                Modifier
                    .offset {
                        val a = 2 * PI * (clock.t / clock.period * b.m + b.ph)
                        IntOffset((b.x.toPx() + sin(a) * b.ax.toPx()).roundToInt(), (b.y.toPx() + cos(a) * b.ay.toPx()).roundToInt())
                    }
                    .size(b.d)
                    .background(Brush.radialGradient(listOf(b.color.copy(alpha = b.alpha), b.color.copy(alpha = 0f)))),
            )
        }
        Tiles.forEach { t ->
            Box(
                Modifier
                    .offset {
                        val a = 2 * PI * (clock.t / clock.period + t.ph)
                        IntOffset((t.x.toPx() + sin(a) * t.ax.toPx()).roundToInt(), (t.y.toPx() + cos(a) * t.ay.toPx()).roundToInt())
                    }
                    .size(t.w, t.h)
                    .graphicsLayer { rotationZ = t.rot + sin(2 * PI * (clock.t / clock.period + t.ph)).toFloat() * 3f }
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(t.c0, t.c1))),
            )
        }
    }
}

