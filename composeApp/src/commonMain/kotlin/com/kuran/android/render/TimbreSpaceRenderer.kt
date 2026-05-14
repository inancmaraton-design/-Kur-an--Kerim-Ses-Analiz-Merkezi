package com.kuran.android.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuran.android.models.AudioPoint3D
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// Dahili projeksiyon modeli
// ─────────────────────────────────────────────────────────────────────────────
private data class ProjectedPoint(
    val z: Float,
    val screenX: Float,
    val screenY: Float,
    val alpha: Float,
    val isActive: Boolean,
    val r: Float,
    val g: Float,
    val b: Float,
    val radius: Float,
    val skor: Float,
    val harf: String?,
    val harf_isim: String?,
    val kelime: String?
)

/** Skor → nokta yarıçapı (px cinsinden) */
private fun skorToRadius(skor: Float, isActive: Boolean): Float {
    val base = when {
        skor >= 85f -> 6f
        skor >= 70f -> 5f
        skor >= 55f -> 4f
        else        -> 3f
    }
    return if (isActive) base * 1.6f else base
}

// ─────────────────────────────────────────────────────────────────────────────
// TimbreSpaceRenderer
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun TimbreSpaceRenderer(
    points: List<AudioPoint3D>,
    currentTime: Float = Float.MAX_VALUE,
    modifier: Modifier = Modifier
) {
    var rotationX by remember { mutableStateOf(15f) }
    var rotationY by remember { mutableStateOf(0f) }
    var scale     by remember { mutableStateOf(100f) }

    // Tooltip state
    var hoverScreenPos by remember { mutableStateOf<Offset?>(null) }
    var hoveredPoint   by remember { mutableStateOf<ProjectedPoint?>(null) }

    // Aktif (en yakın zamanlı) nokta — Arapça harf overlay için
    var activePoint by remember { mutableStateOf<ProjectedPoint?>(null) }

    // Projeksiyon listesi — canvas dışında tutuyoruz ki hover hesabı yapabilelim
    var projectedSnapshot by remember { mutableStateOf<List<ProjectedPoint>>(emptyList()) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // Sol tık sürükle = döndürme
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        rotationY += dragAmount.x / 3f
                        rotationX -= dragAmount.y / 3f
                    }
                }
                // Scroll wheel = zoom + mouse move
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            when (event.type) {
                                PointerEventType.Scroll -> {
                                    val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                    scale = (scale * (1f - delta * 0.12f)).coerceIn(10f, 2000f)
                                    event.changes.forEach { it.consume() }
                                }
                                PointerEventType.Move -> {
                                    val pos = event.changes.firstOrNull()?.position
                                    hoverScreenPos = pos
                                }
                                PointerEventType.Exit -> {
                                    hoverScreenPos = null
                                    hoveredPoint = null
                                }
                                else -> {}
                            }
                        }
                    }
                }
        ) {
            val centerX = size.width  / 2
            val centerY = size.height / 2
            val radX = rotationX * (PI / 180.0)
            val radY = rotationY * (PI / 180.0)

            val cosY = cos(radY).toFloat()
            val sinY = sin(radY).toFloat()
            val cosX = cos(radX).toFloat()
            val sinX = sin(radX).toFloat()

            // ── Projeksiyon ──────────────────────────────────────────────────
            val projected = mutableListOf<ProjectedPoint>()
            for (point in points) {
                if (point.timeSec > currentTime) continue

                var x = point.x
                var y = point.y
                var z = point.z

                // Y ekseni döndürme
                val tx = x * cosY - z * sinY
                val tz = x * sinY + z * cosY
                x = tx; z = tz

                // X ekseni döndürme
                val ty = y * cosX - z * sinX
                val tz2 = y * sinX + z * cosX
                y = ty; z = tz2

                val depth = 500f + z * scale * 0.01f
                if (depth <= 0f) continue
                val perspective = 500f / depth
                val screenX = centerX + x * scale * perspective
                val screenY = centerY - y * scale * perspective

                val pointAge = currentTime - point.timeSec
                val alpha = when {
                    currentTime == Float.MAX_VALUE -> 0.85f
                    pointAge < 0.1f -> (pointAge / 0.1f).coerceIn(0f, 1f)
                    else            -> 0.85f
                }

                val isActive = currentTime != Float.MAX_VALUE &&
                        kotlin.math.abs(point.timeSec - currentTime) < 0.2f

                val radius = skorToRadius(point.skor, isActive)

                projected.add(
                    ProjectedPoint(
                        z          = z,
                        screenX    = screenX,
                        screenY    = screenY,
                        alpha      = alpha,
                        isActive   = isActive,
                        r          = point.r,
                        g          = point.g,
                        b          = point.b,
                        radius     = radius,
                        skor       = point.skor,
                        harf       = point.harf,
                        harf_isim  = point.harf_isim,
                        kelime     = point.kelime
                    )
                )
            }

            // Uzaktan yakına sırala (painter's algorithm)
            projected.sortBy { it.z }

            // Hover tespiti — fare konumuna en yakın noktayı bul
            val hover = hoverScreenPos
            var closestHover: ProjectedPoint? = null
            if (hover != null) {
                var minDist = 20f  // 20px eşiği
                for (pp in projected) {
                    val dx = pp.screenX - hover.x
                    val dy = pp.screenY - hover.y
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist < minDist) {
                        minDist = dist
                        closestHover = pp
                    }
                }
            }
            hoveredPoint = closestHover

            // Aktif nokta
            activePoint = projected.firstOrNull { it.isActive }

            // Snapshot'ı sakla
            projectedSnapshot = projected

            // ── Çizim ────────────────────────────────────────────────────────
            for (pp in projected) {
                val center = Offset(pp.screenX, pp.screenY)
                val r = if (pp == closestHover) pp.radius * 1.4f else pp.radius

                // Aktif nokta — hale efekti
                if (pp.isActive) {
                    drawCircle(
                        color  = Color(pp.r, pp.g, pp.b, 0.18f),
                        radius = r * 5f,
                        center = center
                    )
                    drawCircle(
                        color  = Color(pp.r, pp.g, pp.b, 0.38f),
                        radius = r * 2.8f,
                        center = center
                    )
                    // Parlak çerçeve
                    drawCircle(
                        color  = Color(pp.r, pp.g, pp.b, 0.9f),
                        radius = r + 1.5f,
                        center = center,
                        style  = Stroke(width = 1.5f)
                    )
                }

                // Hover nokta — vurgu
                if (pp == closestHover) {
                    drawCircle(
                        color  = Color(1f, 1f, 1f, 0.25f),
                        radius = r * 2.5f,
                        center = center
                    )
                }

                // Ana nokta
                drawCircle(
                    color  = Color(pp.r, pp.g, pp.b, pp.alpha),
                    radius = r,
                    center = center
                )
            }
        }

        // ── Aktif nokta üstünde Arapça harf overlay ──────────────────────────
        val active = activePoint
        if (active != null && active.harf != null) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (active.screenX - 40).toInt().coerceIn(0, Int.MAX_VALUE),
                            (active.screenY - 70).toInt().coerceIn(0, Int.MAX_VALUE)
                        )
                    }
            ) {
                Surface(
                    color = Color(active.r, active.g, active.b, 0.85f),
                    shape = RoundedCornerShape(8.dp),
                    shadowElevation = 4.dp
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                    ) {
                        // Arapça harf — büyük
                        Text(
                            text  = active.harf,
                            style = TextStyle(
                                fontSize   = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color      = Color.White
                            )
                        )
                        // Harf ismi (Latin)
                        if (active.harf_isim != null) {
                            Text(
                                text  = active.harf_isim,
                                style = TextStyle(
                                    fontSize = 9.sp,
                                    color    = Color.White.copy(0.85f)
                                )
                            )
                        }
                    }
                }
            }
        }

        // ── Hover Tooltip ─────────────────────────────────────────────────────
        val hover2 = hoveredPoint
        val hPos   = hoverScreenPos
        if (hover2 != null && hPos != null && hover2.harf_isim != null) {
            val tooltipX = (hPos.x + 12f).toInt().coerceIn(0, Int.MAX_VALUE)
            val tooltipY = (hPos.y - 40f).toInt().coerceIn(0, Int.MAX_VALUE)
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.offset { IntOffset(tooltipX, tooltipY) }
            ) {
                Surface(
                    color = Color(0xFF1A1A2E.toInt()).copy(alpha = 0.92f),
                    shape = RoundedCornerShape(6.dp),
                    shadowElevation = 6.dp
                ) {
                    val tooltipText = buildString {
                        if (hover2.harf != null)      append("${hover2.harf}  ")
                        append(hover2.harf_isim ?: "")
                        append("  |  Skor: ${hover2.skor.toInt()}")
                        if (hover2.kelime != null)    append("  |  ${hover2.kelime}")
                    }
                    Text(
                        text     = tooltipText,
                        style    = TextStyle(
                            fontSize = 10.sp,
                            color    = Color.White,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}
