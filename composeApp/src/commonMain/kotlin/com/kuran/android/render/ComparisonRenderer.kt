package com.kuran.android.render

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kuran.android.models.AnalysisGroup
import com.kuran.android.models.AudioPoint3D
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.abs

private data class ComparisonProjectedPoint(
    val z: Float,
    val screenX: Float,
    val screenY: Float,
    val alpha: Float,
    val r: Float,
    val g: Float,
    val b: Float,
    val label: String,
    val timeSec: Float,
    val f0: Float?
)

@Composable
fun ComparisonRenderer(
    groups: List<AnalysisGroup>,
    onUpdateGroupOffset: (String, Offset) -> Unit,
    onResetGroupOffset: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var rotationX by remember { mutableStateOf(15f) }
    var rotationY by remember { mutableStateOf(0f) }
    var scale by remember { mutableStateOf(100f) }
    var draggingGroupId by remember { mutableStateOf<String?>(null) }
    var dragStartOffset by remember { mutableStateOf(Offset.Zero) }
    var hoveredPoint by remember { mutableStateOf<ComparisonProjectedPoint?>(null) }
    var mousePos by remember { mutableStateOf(Offset.Zero) }

    var lastProjectedPoints by remember { mutableStateOf<Map<String, List<ComparisonProjectedPoint>>>(emptyMap()) }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            val hitGroup = hitTestGroup(pos, lastProjectedPoints)
                            if (hitGroup != null) {
                                draggingGroupId = hitGroup
                                dragStartOffset = pos
                            }
                        },
                        onDrag = { change, dragAmount ->
                            mousePos = change.position
                            if (draggingGroupId != null) {
                                change.consume()
                                onUpdateGroupOffset(draggingGroupId!!, Offset(dragAmount.x, dragAmount.y))
                            } else {
                                // Grup üzerinde değilse, 3D rotasyon yap
                                change.consume()
                                rotationY += dragAmount.x / 3f
                                rotationX -= dragAmount.y / 3f
                            }
                        },
                        onDragEnd = {
                            draggingGroupId = null
                        }
                    )
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                scale = (scale * (1f - delta * 0.12f)).coerceIn(10f, 2000f)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            mousePos = event.changes.first().position

                            hoveredPoint = findHoveredPoint(mousePos, lastProjectedPoints)

                            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                val hitGroup = hitTestGroup(mousePos, lastProjectedPoints)
                                if (hitGroup != null) {
                                    onResetGroupOffset(hitGroup)
                                }
                            }
                        }
                    }
                }
        ) {
            drawRect(Color.Black, size = size)

            val centerX = 80f
            val centerY = size.height / 2
            val radX = rotationX * (PI / 180.0)
            val radY = rotationY * (PI / 180.0)

            val cosY = cos(radY).toFloat()
            val sinY = sin(radY).toFloat()
            val cosX = cos(radX).toFloat()
            val sinX = sin(radX).toFloat()

            drawAxes(centerX, centerY, cosY, sinY, cosX, sinX, scale)

            val projectedMap = mutableMapOf<String, List<ComparisonProjectedPoint>>()
            for (group in groups) {
                val projectedPoints = mutableListOf<ComparisonProjectedPoint>()

                for (point in group.points) {
                    var x = point.x
                    var y = point.y
                    var z = point.z

                    val tx = x * cosY - z * sinY
                    val tz = x * sinY + z * cosY
                    x = tx; z = tz

                    val ty = y * cosX - z * sinX
                    val tz2 = y * sinX + z * cosX
                    y = ty; z = tz2

                    val depth = 500f + z * scale * 0.01f
                    if (depth <= 0f) continue
                    val perspective = 500f / depth
                    val screenX = centerX + x * scale * perspective + group.offset.x
                    val screenY = centerY - y * scale * perspective + group.offset.y

                    val alpha = 0.85f

                    projectedPoints.add(
                        ComparisonProjectedPoint(
                            z = z,
                            screenX = screenX,
                            screenY = screenY,
                            alpha = alpha,
                            r = point.r,
                            g = point.g,
                            b = point.b,
                            label = point.label,
                            timeSec = point.timeSec,
                            f0 = point.f0
                        )
                    )
                }

                projectedPoints.sortBy { it.z }
                projectedMap[group.id] = projectedPoints
            }

            lastProjectedPoints = projectedMap

            for (group in groups) {
                val points = projectedMap[group.id] ?: continue
                for (pp in points) {
                    drawCircle(
                        color = group.color.copy(alpha = pp.alpha),
                        radius = 1.8f,
                        center = Offset(pp.screenX, pp.screenY)
                    )
                }
            }
        }

        // Tooltip
        hoveredPoint?.let { point ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = (mousePos.y - 8).dp, start = (mousePos.x + 12).dp)
                ) {
                    Text(
                        "${point.label}\nt=${String.format("%.2f", point.timeSec)}s  f0=${point.f0?.toInt() ?: "—"}Hz",
                        color = Color.White,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawAxes(
    centerX: Float,
    centerY: Float,
    cosY: Float,
    sinY: Float,
    cosX: Float,
    sinX: Float,
    scale: Float
) {
    val axisLength = 2f

    // X ekseni (kırmızı)
    projectPoint(axisLength, 0f, 0f, cosY, sinY, cosX, sinX, centerX, centerY, scale).let { (sx1, sy1) ->
        projectPoint(0f, 0f, 0f, cosY, sinY, cosX, sinX, centerX, centerY, scale).let { (sx0, sy0) ->
            drawLine(Color.Red.copy(0.4f), Offset(sx0, sy0), Offset(sx1, sy1), strokeWidth = 1f)
        }
    }

    // Y ekseni (yeşil)
    projectPoint(0f, axisLength, 0f, cosY, sinY, cosX, sinX, centerX, centerY, scale).let { (sx1, sy1) ->
        projectPoint(0f, 0f, 0f, cosY, sinY, cosX, sinX, centerX, centerY, scale).let { (sx0, sy0) ->
            drawLine(Color.Green.copy(0.4f), Offset(sx0, sy0), Offset(sx1, sy1), strokeWidth = 1f)
        }
    }

    // Z ekseni (mavi)
    projectPoint(0f, 0f, axisLength, cosY, sinY, cosX, sinX, centerX, centerY, scale).let { (sx1, sy1) ->
        projectPoint(0f, 0f, 0f, cosY, sinY, cosX, sinX, centerX, centerY, scale).let { (sx0, sy0) ->
            drawLine(Color.Blue.copy(0.4f), Offset(sx0, sy0), Offset(sx1, sy1), strokeWidth = 1f)
        }
    }
}

private fun projectPoint(
    x: Float, y: Float, z: Float,
    cosY: Float, sinY: Float, cosX: Float, sinX: Float,
    centerX: Float, centerY: Float, scale: Float
): Pair<Float, Float> {
    var px = x
    var py = y
    var pz = z

    val tx = px * cosY - pz * sinY
    val tz = px * sinY + pz * cosY
    px = tx; pz = tz

    val ty = py * cosX - pz * sinX
    val tz2 = py * sinX + pz * cosX
    py = ty; pz = tz2

    val depth = 500f + pz * scale * 0.01f
    if (depth <= 0f) return Pair(centerX, centerY)
    val perspective = 500f / depth
    val screenX = centerX + px * scale * perspective
    val screenY = centerY - py * scale * perspective

    return Pair(screenX, screenY)
}

private fun hitTestGroup(
    mousePos: Offset,
    projectedMap: Map<String, List<ComparisonProjectedPoint>>
): String? {
    var closestGroupId: String? = null
    var closestDist = Float.MAX_VALUE

    for ((groupId, points) in projectedMap) {
        for (pp in points) {
            val dist = (mousePos - Offset(pp.screenX, pp.screenY)).getDistance()
            if (dist < 40f && dist < closestDist) {
                closestDist = dist
                closestGroupId = groupId
            }
        }
    }

    return closestGroupId
}

private fun findHoveredPoint(
    mousePos: Offset,
    projectedMap: Map<String, List<ComparisonProjectedPoint>>
): ComparisonProjectedPoint? {
    var closestPoint: ComparisonProjectedPoint? = null
    var closestDist = Float.MAX_VALUE

    for ((_, points) in projectedMap) {
        for (pp in points) {
            val dist = (mousePos - Offset(pp.screenX, pp.screenY)).getDistance()
            if (dist < 20f && dist < closestDist) {
                closestDist = dist
                closestPoint = pp
            }
        }
    }

    return closestPoint
}
