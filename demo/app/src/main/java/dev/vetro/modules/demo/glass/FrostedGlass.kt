package dev.vetro.modules.demo.glass

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlin.random.Random

@Immutable
data class FrostedMaterial(
    /** Local blur radius: the diffusion this material exists for. */
    val blur: Dp,
    /** Backdrop saturation: colour under the glass must stay alive, not wash out. */
    val saturation: Float,
    /** Contrast: a slight lift, otherwise blur flattens the underlay. */
    val contrast: Float,
    /** Neutral tint over the blur: gives the "matte" look and the contrast for text. */
    val tint: Color,
    /** Grain alpha, 0.01-0.03. More is visible dirt. */
    val noiseAlpha: Float,
    /** Rim colour; thickness is always one physical pixel. */
    val rim: Color,
    /** Inner highlight along the top edge. */
    val highlight: Highlight?,
    /** Wide, low-alpha shadow that separates the surface from the plane below. */
    val shadow: Shadow?,
    /** Fill used when the system has no local blur (API < 31). A deliberate material, not fake glass. */
    val fallbackFill: Color,
)

object FrostedMaterials {

    /** Notification plate over a list. Strong blur, denser tint: the plate carries the main text. */
    @Composable
    fun notification(isDark: Boolean): FrostedMaterial = remember(isDark) {
        if (isDark) {
            FrostedMaterial(
                blur = 24.dp, saturation = 1.18f, contrast = 1.05f,
                tint = Color(0xFF121212).copy(alpha = 0.62f),
                noiseAlpha = 0.022f,
                rim = Color.White.copy(alpha = 0.16f),
                highlight = Highlight(width = 0.75.dp, alpha = 0.5f),
                shadow = Shadow(radius = 28.dp, color = Color.Black.copy(alpha = 0.22f)),
                fallbackFill = Color(0xFF1C1C1E),
            )
        } else {
            FrostedMaterial(
                blur = 24.dp, saturation = 1.16f, contrast = 1.04f,
                tint = Color.White.copy(alpha = 0.66f),
                noiseAlpha = 0.016f,
                rim = Color.White.copy(alpha = 0.85f),
                highlight = Highlight(width = 0.75.dp, alpha = 0.7f),
                shadow = Shadow(radius = 26.dp, color = Color.Black.copy(alpha = 0.12f)),
                fallbackFill = Color.White,
            )
        }
    }

    /**
     * A plate lying UNDER the top one in a stack. Same modifier chain as [notification] (so promoting it to the
     * top does not change the node set), but a different MATERIAL: no blur (invisible under the top plate and a
     * waste of GPU), fully opaque tint (text of lower plates must not show through).
     */
    @Composable
    fun stackedNotification(isDark: Boolean): FrostedMaterial {
        val base = notification(isDark)
        return remember(base, isDark) {
            base.copy(
                blur = 0.dp, saturation = 1f, contrast = 1f,
                tint = if (isDark) Color(0xFF1C1C1E) else Color.White,
                noiseAlpha = 0f, highlight = null, shadow = null,
            )
        }
    }

    /** Dock capsule, round buttons next to it, dropdown panels. Lighter tint: icons, no body text. */
    @Composable
    fun dock(isDark: Boolean): FrostedMaterial = remember(isDark) {
        if (isDark) {
            FrostedMaterial(
                blur = 20.dp, saturation = 1.20f, contrast = 1.06f,
                tint = Color(0xFF141414).copy(alpha = 0.48f),
                noiseAlpha = 0.020f,
                rim = Color.White.copy(alpha = 0.14f),
                highlight = Highlight(width = 0.75.dp, alpha = 0.55f),
                shadow = Shadow(radius = 30.dp, color = Color.Black.copy(alpha = 0.26f)),
                fallbackFill = Color(0xFF1A1A1A),
            )
        } else {
            FrostedMaterial(
                blur = 20.dp, saturation = 1.18f, contrast = 1.05f,
                tint = Color.White.copy(alpha = 0.54f),
                noiseAlpha = 0.014f,
                rim = Color.White.copy(alpha = 0.9f),
                highlight = Highlight(width = 0.75.dp, alpha = 0.75f),
                shadow = Shadow(radius = 28.dp, color = Color.Black.copy(alpha = 0.14f)),
                fallbackFill = Color(0xFFF2F2F4),
            )
        }
    }

    /**
     * Buttons and plates over cover art (player controls, mini player, "continue" card). The backdrop is a
     * picture, so blur is full and the theme does not matter. Tint is darker than the dock: white icons need
     * contrast on a light cover.
     */
    @Composable
    fun playerControl(): FrostedMaterial = remember {
        FrostedMaterial(
            blur = 24.dp, saturation = 1.16f, contrast = 1.05f,
            tint = Color(0xFF141414).copy(alpha = 0.42f),
            noiseAlpha = 0.020f,
            rim = Color.White.copy(alpha = 0.16f),
            highlight = Highlight(width = 0.75.dp, alpha = 0.5f),
            shadow = Shadow(radius = 24.dp, color = Color.Black.copy(alpha = 0.22f)),
            fallbackFill = Color(0xE6202020),
        )
    }
}

/** Local blur exists from Android 12 (RenderEffect.createBlurEffect). Below that we do not fake it. */
private val SystemBlurAvailable: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Apply [material] to a surface of [shape].
 *
 * The modifier never adds or removes nodes during the life of the component: the branch is chosen once by
 * OS version. Structurally changing the modifier chain above a consumer of `layerBackdrop` resets the glass
 * recording - keep the chain constant.
 */
@Composable
fun Modifier.frostedGlass(
    backdrop: Backdrop,
    shape: Shape,
    material: FrostedMaterial,
): Modifier {
    val density = LocalDensity.current
    val blurPx = with(density) { material.blur.toPx() }
    // Exactly one PHYSICAL pixel, not 1 dp: on a dense screen 1 dp is 3-4 px and the hairline becomes a frame.
    val hairline = with(density) { 1f.toDp() }
    val noiseBrush = rememberNoiseBrush()

    // The chain is remembered: drawBackdrop creates a new ShapeProvider without equals every call, so any
    // recomposition of the caller would rebuild the RenderEffect and repaint the shadow.
    val body = remember(backdrop, shape, material, blurPx, noiseBrush) {
        if (SystemBlurAvailable) {
            Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    // Order matters: blur first, colour second. The reverse smears boosted saturation
                    // into a dirty halo.
                    blur(blurPx)
                    colorControls(
                        contrast = material.contrast,
                        saturation = material.saturation,
                    )
                },
                highlight = { material.highlight },
                shadow = { material.shadow },
                // Tint on top of the processed backdrop, not mixed into it: "blur before tint".
                onDrawSurface = { drawRect(material.tint) },
                onDrawFront = { drawNoise(noiseBrush, material.noiseAlpha) },
            )
        } else {
            Modifier
                .background(material.fallbackFill, shape)
                .drawWithContent {
                    drawContent()
                    drawNoise(noiseBrush, material.noiseAlpha)
                }
        }
    }

    // The rim is a separate node: border() builds the stroke from the shape, so capsules and rounded
    // rectangles are outlined correctly without hand-made geometry.
    return this
        .then(body)
        .border(hairline, material.rim, shape)
}

private fun DrawScope.drawNoise(noiseBrush: ShaderBrush, alpha: Float) {
    if (alpha > 0f) drawRect(brush = noiseBrush, alpha = alpha)
}

/**
 * Grain tile. Built once per process and shared by all surfaces: never rebuild a noise texture per frame.
 * Achromatic - coloured noise on a neutral underlay reads as an image defect.
 */
@Composable
private fun rememberNoiseBrush(): ShaderBrush = remember {
    ShaderBrush(
        ImageShader(
            image = NoiseTile,
            tileModeX = TileMode.Repeated,
            tileModeY = TileMode.Repeated,
        ),
    )
}

private val NoiseTile: ImageBitmap by lazy {
    val size = 128
    // Fixed seed: the texture must be identical between launches, otherwise the same surface differs
    // slightly on screenshots and they cannot be compared.
    val random = Random(0x5E7B0)
    val pixels = IntArray(size * size) {
        val v = 128 + random.nextInt(-64, 64)
        (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
    bitmap.asImageBitmap()
}
