package com.kuran.android.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import com.kuran.android.models.SpectrogramData
import java.awt.image.BufferedImage
import java.util.Base64

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * 3D Spektrogram görsellendirmesi — base64 pixel buffer'dan Canvas'e
 *
 * Özellikler:
 *  - Fare sürükleme: offsetX, offsetY
 *  - Scroll: zoom (0.5x–3x)
 *  - Playback cursor: mevcut zaman göstergesi (sarı çizgi)
 *  - Thermal colorscale: dark blue → teal → yellow
 */
@Composable
actual fun SpectrogramRenderer(
    data: SpectrogramData,
    currentTimeSec: Float,
    modifier: Modifier
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var scale by remember { mutableFloatStateOf(1f) }

    val imageBitmap: ImageBitmap? = remember(data.pixels) {
        try {
            val bytes = Base64.getDecoder().decode(data.pixels)
            val buffered = BufferedImage(data.width, data.height, BufferedImage.TYPE_INT_ARGB)
            for (row in 0 until data.height) {
                for (col in 0 until data.width) {
                    val idx = row * data.width + col
                    val v = bytes[idx].toInt() and 0xFF
                    buffered.setRGB(col, row, thermalColor(v))
                }
            }
            buffered.toComposeImageBitmap()
        } catch (e: Exception) {
            println("[SpectrogramRenderer] Decode hatasi: ${e.message}")
            null
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val delta = event.changes[0].scrollDelta.y
                            scale = (scale * (1f - delta * 0.1f)).coerceIn(0.5f, 3f)
                        }
                    }
                }
            }
    ) {
        if (imageBitmap != null) {
            val scaledWidth = data.width * scale
            val scaledHeight = data.height * scale
            val destX = size.width / 2 - scaledWidth / 2 + offsetX
            val destY = size.height / 2 - scaledHeight / 2 + offsetY

            drawImage(
                imageBitmap,
                dstOffset = IntOffset(destX.roundToInt(), destY.roundToInt()),
                dstSize = IntSize(scaledWidth.roundToInt(), scaledHeight.roundToInt())
            )

            // Playback cursor (sarı dikey çizgi)
            if (currentTimeSec >= 0f && data.durationSec > 0f) {
                val timeRatio = (currentTimeSec / data.durationSec).coerceIn(0f, 1f)
                val cursorX = destX + scaledWidth * timeRatio
                drawLine(
                    Color.Yellow,
                    start = Offset(cursorX, 0f),
                    end = Offset(cursorX, size.height),
                    strokeWidth = 2f,
                    alpha = 0.7f
                )
            }
        }
    }
}

/**
 * Grayscale value (0–255) → ARGB color mapping
 * Thermal colorscale: dark blue (0) → teal → yellow (255)
 */
private fun thermalColor(v: Int): Int {
    val t = v / 255f

    val r = when {
        t < 0.33f -> 0
        t < 0.66f -> ((t - 0.33f) / 0.33f * 128).toInt()
        else -> ((t - 0.66f) / 0.34f * 127 + 128).toInt()
    }.coerceIn(0, 255)

    val g = (t * 200).toInt().coerceIn(0, 255)

    val b = when {
        t < 0.5f -> ((1f - t * 2f) * 255).toInt()
        else -> 0
    }.coerceIn(0, 255)

    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}
