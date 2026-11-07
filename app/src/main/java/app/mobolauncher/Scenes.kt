package app.mobolauncher

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

// The one built-in wallpaper: an original, procedurally drawn blue "wave" in the spirit of the
// macOS Tahoe look. It is not Apple artwork. Users can pick their own image in Settings.

private fun DrawScope.drawWave() {
    val w = size.width
    val h = size.height
    // sky: blue at the top, warm sand on the lower left
    drawRect(
        Brush.linearGradient(
            listOf(Color(0xFF3F92D8), Color(0xFF9DB9D2), Color(0xFFDCCBA2)),
            start = Offset(w * 0.62f, 0f), end = Offset(0f, h * 0.72f),
        )
    )
    // pale teal hill, lower left
    val hill = Path().apply {
        moveTo(0f, h * 0.70f)
        cubicTo(w * 0.10f, h * 0.50f, w * 0.27f, h * 0.54f, w * 0.40f, h * 0.70f)
        lineTo(w * 0.40f, h)
        lineTo(0f, h)
        close()
    }
    drawPath(hill, Brush.verticalGradient(listOf(Color(0xFF3CB6CC), Color(0xFF2D74DA)), startY = h * 0.5f, endY = h))
    // glassy ribbon along the crest
    val ribbon = Path().apply {
        moveTo(0f, h * 0.90f)
        cubicTo(w * 0.38f, h * 0.60f, w * 0.60f, h * 0.12f, w * 1.04f, -h * 0.05f)
        lineTo(w * 1.04f, h * 0.10f)
        cubicTo(w * 0.66f, h * 0.27f, w * 0.46f, h * 0.72f, 0f, h * 1.02f)
        close()
    }
    drawPath(
        ribbon,
        Brush.linearGradient(
            listOf(Color(0xFFD8ECFA).copy(alpha = 0.80f), Color(0xFF7DB4EC).copy(alpha = 0.40f)),
            start = Offset(w * 0.25f, h), end = Offset(w, 0f),
        ),
    )
    // main body of the wave
    val body = Path().apply {
        moveTo(0f, h * 0.99f)
        cubicTo(w * 0.42f, h * 0.76f, w * 0.64f, h * 0.30f, w * 1.04f, h * 0.06f)
        lineTo(w * 1.04f, h)
        lineTo(0f, h)
        close()
    }
    drawPath(
        body,
        Brush.linearGradient(
            listOf(Color(0xFF3076EA), Color(0xFF0D36B6), Color(0xFF061B78)),
            start = Offset(w * 0.55f, h * 0.15f), end = Offset(w * 0.5f, h),
        ),
    )
    // soft highlight inside the wave
    val lobe = Offset(w * 0.64f, h * 0.64f)
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFF86BDF7).copy(alpha = 0.60f), Color.Transparent), center = lobe, radius = h * 0.55f),
        radius = h * 0.55f, center = lobe,
    )
    // thin specular edge
    val edge = Path().apply {
        moveTo(0f, h * 0.90f)
        cubicTo(w * 0.38f, h * 0.60f, w * 0.60f, h * 0.12f, w * 1.04f, -h * 0.05f)
    }
    drawPath(edge, Color.White.copy(alpha = 0.65f), style = Stroke(width = h * 0.0035f))
    // pale mound, lower right
    val mound = Offset(w * 0.98f, h * 1.04f)
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFC3D6E8).copy(alpha = 0.85f), Color.Transparent), center = mound, radius = h * 0.52f),
        radius = h * 0.52f, center = mound,
    )
}


// "Glow" wallpaper: an original glowing ring on black, inspired by the publicly described
// WWDC26 theme ("All systems glow"). It is not Apple's artwork.
private fun DrawScope.drawGlow() {
    val w = size.width
    val h = size.height
    drawRect(Color(0xFF040408))
    val c = Offset(w * 0.5f, h * 0.52f)
    val r = h * 0.30f
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFF6E3BFF).copy(alpha = 0.28f), Color.Transparent), center = c, radius = r * 2.6f),
        radius = r * 2.6f, center = c,
    )
    val ring = Brush.sweepGradient(
        listOf(
            Color(0xFFFF4DA6), Color(0xFFFF9A3D), Color(0xFFFFE066), Color(0xFF52E0A8),
            Color(0xFF3DB8FF), Color(0xFF8E5BFF), Color(0xFFFF4DA6),
        ),
        center = c,
    )
    for ((mult, a) in listOf(0.10f to 0.05f, 0.06f to 0.09f, 0.035f to 0.18f, 0.018f to 0.45f)) {
        drawCircle(ring, r, c, alpha = a, style = Stroke(width = h * mult))
    }
    drawCircle(ring, r, c, style = Stroke(width = h * 0.008f))
    drawCircle(ring, r * 1.12f, c, alpha = 0.35f, style = Stroke(width = h * 0.002f))
}

@Composable
fun SceneCanvas(id: String, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        when (id) {
            "wwdc26" -> drawGlow()
            else -> drawWave()
        }
    }
}
