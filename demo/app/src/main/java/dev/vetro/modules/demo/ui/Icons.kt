package dev.vetro.modules.demo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A 24x24 line icon: stroked paths plus optional filled ones (SVG path syntax). */
class Ic(val stroke: List<String>, val fill: List<String> = emptyList())

private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0z"

private fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float) =
    "M${x + r} ${y}H${x + w - r}A$r $r 0 0 1 ${x + w} ${y + r}V${y + h - r}A$r $r 0 0 1 ${x + w - r} ${y + h}" +
        "H${x + r}A$r $r 0 0 1 $x ${y + h - r}V${y + r}A$r $r 0 0 1 ${x + r} ${y}z"

object Icons24 {
    val home = Ic(listOf("M3 11 12 3l9 8", "M5 10v10h5v-6h4v6h5V10"))
    val stats = Ic(listOf(rrect(3.5f, 3.5f, 7f, 7f, 2f), rrect(13.5f, 3.5f, 7f, 7f, 2f), rrect(3.5f, 13.5f, 7f, 7f, 2f), "M17 14v6M14 17h6"))
    val plus = Ic(listOf("M12 5v14M5 12h14"))
    val tune = Ic(listOf("M4 7h9M17 7h3M4 17h3M11 17h9", circle(15f, 7f, 2f), circle(9f, 17f, 2f)))
    val search = Ic(listOf(circle(11f, 11f, 6.5f), "m20.5 20.5-4.7-4.7"))
    val back = Ic(listOf("M19 12H5M11.5 5.5 5 12l6.5 6.5"))
    val dots = Ic(emptyList(), listOf(circle(5.5f, 12f, 1.4f), circle(12f, 12f, 1.4f), circle(18.5f, 12f, 1.4f)))
    val check = Ic(listOf(circle(12f, 12f, 8.5f), "m8.2 12.4 2.7 2.7 5-5.6"))
    val image = Ic(listOf(rrect(3.5f, 4.5f, 17f, 15f, 3f), circle(9f, 10f, 1.6f), "m4 17 5-4.5 4 3.2 3-2.4 4 3.2"))
    val unlink = Ic(listOf("m9.5 14.5 5-5", "M11 6.7 12.4 5.3a3.7 3.7 0 0 1 5.3 5.2L16.3 12", "M13 17.3l-1.4 1.4a3.7 3.7 0 0 1-5.3-5.2L7.7 12"))
    val lock = Ic(listOf(rrect(5f, 10.5f, 14f, 9.5f, 3f), "M8.5 10.5V8a3.5 3.5 0 0 1 7 0v2.5"))
    val play = Ic(emptyList(), listOf("M8.5 5.8v12.4a.6.6 0 0 0 .9.5l9.6-6.2a.6.6 0 0 0 0-1L9.4 5.3a.6.6 0 0 0-.9.5z"))
    val heart = Ic(listOf("M12 19.5s-7-4.4-7-9.7A4 4 0 0 1 12 7.5a4 4 0 0 1 7 2.3c0 5.3-7 9.7-7 9.7z"))
    val mark = Ic(listOf("M7 4h10v16l-5-3.6L7 20z"))
    val chev = Ic(listOf("m9 5 7 7-7 7"))
    val sun = Ic(listOf(circle(12f, 12f, 4f), "M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6 7 7M17 17l1.4 1.4M5.6 18.4 7 17M17 7l1.4-1.4"))
    val globe = Ic(listOf(circle(12f, 12f, 8.5f), "M3.5 12h17M12 3.5c3 3.2 3 13.8 0 17M12 3.5c-3 3.2-3 13.8 0 17"))
    val bell = Ic(listOf("M6 17V11a6 6 0 0 1 12 0v6l1.5 2h-15z", "M10 21h4"))
    val sync = Ic(listOf("M20 11a8 8 0 0 0-14-4M4 5v4h4M4 13a8 8 0 0 0 14 4M20 19v-4h-4"))
    val down = Ic(listOf("M12 4v11M7.5 11 12 15.5 16.5 11M5 20h14"))
    val disk = Ic(listOf("M5 6c0-1.7 3.1-3 7-3s7 1.3 7 3-3.1 3-7 3-7-1.3-7-3z", "M5 6v12c0 1.7 3.1 3 7 3s7-1.3 7-3V6", "M5 12c0 1.7 3.1 3 7 3s7-1.3 7-3"))
    val info = Ic(listOf(circle(12f, 12f, 8.5f), "M12 11v5.5M12 7.8v.1"))
}

@Composable
fun Icon24(icon: Ic, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp, strokeWidth: Float = 1.8f) {
    val stroke = remember(icon) { icon.stroke.map { PathParser().parsePathString(it).toPath() } }
    val fill = remember(icon) { icon.fill.map { PathParser().parsePathString(it).toPath() } }
    Canvas(modifier.size(size)) {
        val s = this.size.width / 24f
        withTransform({ scale(s, s, pivot = Offset.Zero) }) {
            val style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            stroke.forEach { drawPath(it as Path, tint, style = style) }
            fill.forEach { drawPath(it as Path, tint, style = Fill) }
        }
    }
}
