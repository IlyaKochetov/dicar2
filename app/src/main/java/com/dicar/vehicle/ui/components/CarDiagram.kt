package com.dicar.vehicle.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.dicar.vehicle.data.model.Radar
import com.dicar.vehicle.data.model.VehicleState
import com.dicar.vehicle.ui.theme.carBody
import com.dicar.vehicle.ui.theme.carGlass
import com.dicar.vehicle.ui.theme.carTyre
import java.util.Locale

/**
 * Top-down twin view of the vehicle, facing upward.
 *
 * The sedan body uses a Bézier outline, thin stroke, dark tires and neutral gray glass.
 * Color highlights states and actions: red doors, amber indicators, green charging,
 * and green-to-red radar according to distance.
 *
 * Components reflect live door, window, sunroof, tire, indicator, radar and charging states.
 * Unavailable readings retain their default appearance rather than using warning colors.
 */
@Composable
fun CarDiagram(state: VehicleState, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val body = MaterialTheme.carBody
    val glass = MaterialTheme.carGlass
    val tyreColor = MaterialTheme.carTyre
    val outline = scheme.outline
    val hairline = scheme.outlineVariant
    val warn = scheme.error
    val accent = scheme.primary
    val signal = scheme.tertiary
    val charge = scheme.secondary
    val onSurface = scheme.onSurface
    val dim = scheme.onSurfaceVariant

    // Animate door, window and sunroof movements so their transitions are visible.
    val doorFl by animateFloatAsState(if (state.doors.fl == true) 1f else 0f, label = "doorFl")
    val doorFr by animateFloatAsState(if (state.doors.fr == true) 1f else 0f, label = "doorFr")
    val doorRl by animateFloatAsState(if (state.doors.rl == true) 1f else 0f, label = "doorRl")
    val doorRr by animateFloatAsState(if (state.doors.rr == true) 1f else 0f, label = "doorRr")
    val hood by animateFloatAsState(if (state.doors.hood == true) 1f else 0f, label = "hood")
    val trunk by animateFloatAsState(if (state.doors.trunk == true) 1f else 0f, label = "trunk")
    val sunroof by animateFloatAsState((state.sunroofPercent ?: 0) / 100f, label = "sunroof")
    val winFl by animateFloatAsState((state.windowPercent.fl ?: 0) / 100f, label = "winFl")
    val winFr by animateFloatAsState((state.windowPercent.fr ?: 0) / 100f, label = "winFr")
    val winRl by animateFloatAsState((state.windowPercent.rl ?: 0) / 100f, label = "winRl")
    val winRr by animateFloatAsState((state.windowPercent.rr ?: 0) / 100f, label = "winRr")

    val driving = (state.speed ?: 0f) > 1f
    val charging = state.chargeGunConnected == true || state.chargeStatus == "Charging"

    // Shrink the car while radar is active to leave room for three arcs at each end.
    // At 0.82 height the arcs are clipped; 0.68 fits the outer arc, about 0.23 car lengths away.
    val radarActive = state.radar.hasAnyReading || state.radar.reverseSwitchOn == true
    val heightFraction by animateFloatAsState(
        targetValue = if (radarActive) 0.68f else 0.82f,
        label = "carScale",
    )

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Use a 1:2.35 sedan ratio, fitting height first and then width to leave space for corner labels.
        var bodyH = h * heightFraction
        var bodyW = bodyH * CAR_ASPECT
        if (bodyW > w * 0.40f) {
            bodyW = w * 0.40f
            bodyH = bodyW / CAR_ASPECT
        }
        val left = (w - bodyW) / 2f
        val top = (h - bodyH) / 2f
        val right = left + bodyW
        val bottom = top + bodyH
        val cx = left + bodyW / 2f
        fun bx(f: Float) = left + bodyW * f      // Body coordinates: 0 = left edge, 1 = right edge
        fun by(f: Float) = top + bodyH * f       // 0 = front, 1 = rear
        val line = bodyW * 0.012f

        if (driving) drawLaneLines(dim, left, right, bodyW, h)

        // ---- Body outline ----
        val shell = carShell(left, top, bodyW, bodyH)
        // Soft shadow: draw two faint thick outlines, more reliably than BlurMaskFilter with hardware acceleration.
        drawPath(shell, outline.copy(alpha = 0.06f), style = Stroke(width = line * 5f))
        drawPath(shell, outline.copy(alpha = 0.10f), style = Stroke(width = line * 2.5f))
        drawPath(shell, body)
        drawPath(shell, outline.copy(alpha = 0.55f), style = Stroke(width = line))

        // ---- Wheels: draw the darkest elements first so the body overlaps their edges ----
        val tyreW = bodyW * 0.17f
        val tyreH = bodyH * 0.125f
        fun tyre(isLeft: Boolean, isFront: Boolean, kpa: Float?) {
            val x = if (isLeft) left - tyreW * 0.52f else right - tyreW * 0.48f
            val y = if (isFront) by(0.135f) else by(0.705f)
            val abnormal = kpa != null && (kpa < TYRE_MIN_KPA || kpa > TYRE_MAX_KPA)
            val r = CornerRadius(tyreW * 0.32f, tyreW * 0.32f)
            drawRoundRect(
                color = if (abnormal) warn else tyreColor,
                topLeft = Offset(x, y),
                size = Size(tyreW, tyreH),
                cornerRadius = r,
            )
            // Outline the outer wheel edges so they remain visible against a dark background.
            drawRoundRect(
                color = outline.copy(alpha = 0.55f),
                topLeft = Offset(x, y),
                size = Size(tyreW, tyreH),
                cornerRadius = r,
                style = Stroke(width = line * 0.9f),
            )
        }
        tyre(isLeft = true, isFront = true, kpa = state.tirePressure.fl)
        tyre(isLeft = false, isFront = true, kpa = state.tirePressure.fr)
        tyre(isLeft = true, isFront = false, kpa = state.tirePressure.rl)
        tyre(isLeft = false, isFront = false, kpa = state.tirePressure.rr)

        // Redraw the body over the inner tires so the wheels appear beneath it.
        drawPath(shell, body)
        drawPath(shell, outline.copy(alpha = 0.55f), style = Stroke(width = line))

        // ---- Hood / trunk: a faint seam when closed and an amber opening when open ----
        fun lid(isHood: Boolean, openFraction: Float) {
            val bandH = bodyH * 0.075f
            val y = if (isHood) by(0.055f) else bottom - bandH - bodyH * 0.055f
            val r = CornerRadius(bodyW * 0.05f, bodyW * 0.05f)
            if (openFraction > 0f) {
                drawRoundRect(signal.copy(alpha = 0.75f), Offset(bx(0.16f), y), Size(bodyW * 0.68f, bandH), r)
            }
            // Shorten the panel from its hinge when open to reveal the opening.
            val visible = bandH * (1f - openFraction)
            val lidY = if (isHood) y + (bandH - visible) else y
            drawRoundRect(body, Offset(bx(0.16f), lidY), Size(bodyW * 0.68f, visible), r)
            drawRoundRect(hairline, Offset(bx(0.16f), lidY), Size(bodyW * 0.68f, visible), r, style = Stroke(line * 0.8f))
        }
        lid(isHood = true, openFraction = hood)
        lid(isHood = false, openFraction = trunk)

        // ---- Front and rear windscreens ----
        // The A and C pillars narrow toward the roof, so the front and rear windscreens
        // taper in opposite directions.
        drawPath(trapezoid(bx(0.13f), by(0.198f), bx(0.87f), by(0.288f), inset = bodyW * 0.10f, wideAtBottom = false), glass)
        drawPath(trapezoid(bx(0.13f), by(0.662f), bx(0.87f), by(0.752f), inset = bodyW * 0.10f, wideAtBottom = true), glass)

        // ---- Roof and sunroof ----
        val roofTop = by(0.30f)
        val roofBottom = by(0.65f)
        val roofRadius = CornerRadius(bodyW * 0.10f, bodyW * 0.10f)
        drawRoundRect(body, Offset(bx(0.13f), roofTop), Size(bodyW * 0.74f, roofBottom - roofTop), roofRadius)
        drawRoundRect(hairline, Offset(bx(0.13f), roofTop), Size(bodyW * 0.74f, roofBottom - roofTop), roofRadius, style = Stroke(line * 0.8f))

        val roofGlassTop = by(0.335f)
        val roofGlassH = bodyH * 0.185f
        val roofGlassRadius = CornerRadius(bodyW * 0.06f, bodyW * 0.06f)
        // Draw the dark opening first, then slide the glass back according to its opening.
        drawRoundRect(tyreColor.copy(alpha = 0.55f), Offset(bx(0.22f), roofGlassTop), Size(bodyW * 0.56f, roofGlassH), roofGlassRadius)
        val slide = roofGlassH * sunroof
        if (sunroof < 1f) {
            drawRoundRect(
                color = if (sunroof > 0f) accent.copy(alpha = 0.45f) else glass,
                topLeft = Offset(bx(0.22f), roofGlassTop + slide),
                size = Size(bodyW * 0.56f, roofGlassH - slide),
                cornerRadius = roofGlassRadius,
            )
        }

        // ---- Four side windows ----
        // Closed windows use a fine seam; lowered windows reveal the dark interior.
        // Avoid solid gray blocks for closed windows, which would dominate the body.
        fun sideWindow(isLeft: Boolean, isFront: Boolean, openFraction: Float) {
            val sw = bodyW * 0.045f
            val x = if (isLeft) bx(0.055f) else bx(0.90f)
            val y = if (isFront) by(0.345f) else by(0.492f)
            val sh = bodyH * (if (isFront) 0.13f else 0.115f)
            val r = CornerRadius(sw * 0.5f, sw * 0.5f)
            drawRoundRect(hairline, Offset(x, y), Size(sw, sh), r, style = Stroke(line * 0.7f))
            val gap = sh * openFraction
            if (gap > 0.5f) {
                drawRoundRect(tyreColor.copy(alpha = 0.60f), Offset(x, y), Size(sw, gap), r)
            }
        }
        sideWindow(true, true, winFl)
        sideWindow(false, true, winFr)
        sideWindow(true, false, winRl)
        sideWindow(false, false, winRr)

        // ---- Mirrors: small projections beside the lower edge of the windscreen ----
        val mirrorW = bodyW * 0.13f
        val mirrorH = bodyH * 0.022f
        fun mirror(isLeft: Boolean) {
            val x = if (isLeft) left - mirrorW * 0.62f else right - mirrorW * 0.38f
            val r = CornerRadius(mirrorH, mirrorH)
            drawRoundRect(body, Offset(x, by(0.318f)), Size(mirrorW, mirrorH), r)
            drawRoundRect(outline.copy(alpha = 0.45f), Offset(x, by(0.318f)), Size(mirrorW, mirrorH), r, style = Stroke(line * 0.7f))
        }
        mirror(isLeft = true)
        mirror(isLeft = false)

        // ---- Tail light: continuous pale red styling, not an alert ----
        drawRoundRect(
            color = warn.copy(alpha = 0.30f),
            topLeft = Offset(bx(0.16f), by(0.935f)),
            size = Size(bodyW * 0.68f, bodyH * 0.022f),
            cornerRadius = CornerRadius(bodyH * 0.011f, bodyH * 0.011f),
        )

        // ---- Doors: rotate outward around the front hinges ----
        fun door(isLeft: Boolean, isFront: Boolean, openFraction: Float) {
            if (openFraction <= 0.001f) return
            val dw = bodyW * 0.30f
            val dh = bodyH * (if (isFront) 0.155f else 0.14f)
            val y = if (isFront) by(0.325f) else by(0.48f)
            val x = if (isLeft) left - dw else right
            val pivot = Offset(if (isLeft) left else right, y)
            val angle = MAX_DOOR_ANGLE * openFraction * (if (isLeft) -1f else 1f)
            rotate(degrees = angle, pivot = pivot) {
                val r = CornerRadius(dh * 0.35f, dh * 0.35f)
                drawRoundRect(warn.copy(alpha = 0.22f), Offset(x, y), Size(dw, dh), r)
                drawRoundRect(warn, Offset(x, y), Size(dw, dh), r, style = Stroke(width = line * 1.4f))
            }
        }
        door(isLeft = true, isFront = true, openFraction = doorFl)
        door(isLeft = false, isFront = true, openFraction = doorFr)
        door(isLeft = true, isFront = false, openFraction = doorRl)
        door(isLeft = false, isFront = false, openFraction = doorRr)

        // ---- Indicators: small arrows outside the front, above the tire pressure labels ----
        fun turnArrow(isLeft: Boolean, on: Boolean?) {
            if (on != true) return
            val s = bodyW * 0.09f
            val tipX = if (isLeft) left - bodyW * 0.26f else right + bodyW * 0.26f
            val cy = by(0.03f)
            val path = Path().apply {
                if (isLeft) {
                    moveTo(tipX - s, cy); lineTo(tipX + s * 0.45f, cy - s * 0.85f); lineTo(tipX + s * 0.45f, cy + s * 0.85f)
                } else {
                    moveTo(tipX + s, cy); lineTo(tipX - s * 0.45f, cy - s * 0.85f); lineTo(tipX - s * 0.45f, cy + s * 0.85f)
                }
                close()
            }
            drawPath(path, signal)
        }
        turnArrow(isLeft = true, on = state.turnLeft)
        turnArrow(isLeft = false, on = state.turnRight)

        // ---- Charging: the left front port glows green and the body shows charge fill when connected ----
        if (charging) {
            drawCircle(charge, radius = bodyW * 0.055f, center = Offset(left, by(0.20f)))
            drawCircle(charge.copy(alpha = 0.25f), radius = bodyW * 0.11f, center = Offset(left, by(0.20f)))
            state.soc?.let { soc ->
                val barH = bodyH * 0.16f * (soc / 100f).coerceIn(0f, 1f)
                drawRoundRect(
                    color = charge.copy(alpha = 0.30f),
                    topLeft = Offset(bx(0.28f), by(0.70f) - barH),
                    size = Size(bodyW * 0.44f, barH),
                    cornerRadius = CornerRadius(bodyW * 0.04f, bodyW * 0.04f),
                )
            }
        }

        // ---- 泊车雷达 ----
        // Draw arcs only while radar is active; gray arcs without readings or reverse gear
        // convey no information and cross the central speed reading.
        if (radarActive) {
            drawRadar(state.radar.front, Offset(cx, top), upward = true, bodyW = bodyW, bodyH = bodyH, warn = warn, near = signal, far = charge, idle = outline)
            drawRadar(state.radar.rear, Offset(cx, bottom), upward = false, bodyW = bodyW, bodyH = bodyH, warn = warn, near = signal, far = charge, idle = outline)
        }

        // ---- Tire pressure and temperature labels at the four corners ----
        val big = Paint().apply {
            isAntiAlias = true
            textSize = bodyH * 0.062f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            fontFeatureSettings = "tnum"
        }
        val small = Paint().apply {
            isAntiAlias = true
            textSize = bodyH * 0.040f
            fontFeatureSettings = "tnum"
            color = dim.toArgb()
        }
        fun label(isLeft: Boolean, isFront: Boolean, kpa: Float?, temp: Float?) {
            val x = if (isLeft) left - tyreW * 0.85f else right + tyreW * 0.85f
            val y = if (isFront) by(0.135f) else by(0.705f)
            val align = if (isLeft) Paint.Align.RIGHT else Paint.Align.LEFT
            big.textAlign = align
            small.textAlign = align
            val abnormal = kpa != null && (kpa < TYRE_MIN_KPA || kpa > TYRE_MAX_KPA)
            big.color = when {
                kpa == null -> dim.toArgb()
                abnormal -> warn.toArgb()
                else -> onSurface.toArgb()
            }
            // Convert the head-unit pressure from kPa to bar for display.
            val bar = kpa?.let { String.format(Locale.US, "%.1f", it / 100f) } ?: "--"
            val caption = "bar" + (temp?.let { " · ${Math.round(it)}℃" } ?: "")
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(bar, x, y + bodyH * 0.045f, big)
                canvas.nativeCanvas.drawText(caption, x, y + bodyH * 0.095f, small)
            }
        }
        label(isLeft = true, isFront = true, kpa = state.tirePressure.fl, temp = state.tireTemp.fl)
        label(isLeft = false, isFront = true, kpa = state.tirePressure.fr, temp = state.tireTemp.fr)
        label(isLeft = true, isFront = false, kpa = state.tirePressure.rl, temp = state.tireTemp.rl)
        label(isLeft = false, isFront = false, kpa = state.tirePressure.rr, temp = state.tireTemp.rr)
    }
}

/** Body aspect ratio (width / length), approximately 1:2.35 for a sedan. */
private const val CAR_ASPECT = 0.425f
private const val MAX_DOOR_ANGLE = 58f
private const val TYRE_MIN_KPA = 190f
private const val TYRE_MAX_KPA = 300f

/**
 * Top-down sedan outline drawn with cubic Béziers: narrower at the front and slightly wider at the middle.
 * The path is symmetric; synchronize control points on both sides.
 */
private fun carShell(left: Float, top: Float, w: Float, h: Float): Path {
    val right = left + w
    val bottom = top + h
    val cx = left + w / 2f
    return Path().apply {
        moveTo(cx, top)
        // Front right → right side → rear right
        cubicTo(cx + w * 0.34f, top, right, top + h * 0.035f, right, top + h * 0.135f)
        cubicTo(right + w * 0.015f, top + h * 0.37f, right + w * 0.015f, top + h * 0.63f, right, top + h * 0.875f)
        cubicTo(right, bottom - h * 0.022f, cx + w * 0.36f, bottom, cx, bottom)
        // Mirror the left side
        cubicTo(cx - w * 0.36f, bottom, left, bottom - h * 0.022f, left, top + h * 0.875f)
        cubicTo(left - w * 0.015f, top + h * 0.63f, left - w * 0.015f, top + h * 0.37f, left, top + h * 0.135f)
        cubicTo(left, top + h * 0.035f, cx - w * 0.34f, top, cx, top)
        close()
    }
}

/** Windscreen trapezoid: [wideAtBottom] selects which end is wider. */
private fun trapezoid(l: Float, t: Float, r: Float, b: Float, inset: Float, wideAtBottom: Boolean): Path = Path().apply {
    if (wideAtBottom) {
        moveTo(l + inset, t); lineTo(r - inset, t); lineTo(r, b); lineTo(l, b)
    } else {
        moveTo(l, t); lineTo(r, t); lineTo(r - inset, b); lineTo(l + inset, b)
    }
    close()
}

/**
 * Decorative lane lines scroll with system time while driving.
 * Draw them near the body rather than the canvas edge to keep them close on wide screens.
 */
private fun DrawScope.drawLaneLines(color: Color, left: Float, right: Float, bodyW: Float, h: Float) {
    val dash = h * 0.07f
    val period = dash + h * 0.055f
    val lineW = bodyW * 0.022f
    val offset = bodyW * 0.72f
    val shift = ((System.nanoTime() / 16_000_000L) % period.toLong().coerceAtLeast(1)).toFloat()
    var y = -shift
    while (y < h) {
        drawRoundRect(color.copy(alpha = 0.14f), Offset(left - offset, y), Size(lineW, dash), CornerRadius(lineW))
        drawRoundRect(color.copy(alpha = 0.14f), Offset(right + offset - lineW, y), Size(lineW, dash), CornerRadius(lineW))
        y += period
    }
}

/**
 * Front/rear radar arcs. [levels] contains probe levels (0 closest, 6 farthest, null unavailable).
 * Closer obstacles illuminate more arcs, changing from green through amber to red.
 */
private fun DrawScope.drawRadar(
    levels: List<Int?>,
    origin: Offset,
    upward: Boolean,
    bodyW: Float,
    bodyH: Float,
    warn: Color,
    near: Color,
    far: Color,
    idle: Color,
) {
    val nearest = levels.filterNotNull().minOrNull()
    val arcs = 3
    val gap = bodyH * 0.030f
    for (i in 0 until arcs) {
        // Arc zero is closest to the body and represents the nearest level.
        val threshold = (i + 1) * (Radar.OBSTACLE_MAX / arcs.toFloat())
        val lit = nearest != null && nearest <= threshold
        val color = when {
            !lit -> idle.copy(alpha = 0.16f)
            i == 0 -> warn
            i == 1 -> near
            else -> far
        }
        val r = bodyW * 0.32f + gap * (i + 1)
        drawArc(
            color = color,
            startAngle = if (upward) 202f else 22f,
            sweepAngle = 136f,
            useCenter = false,
            topLeft = Offset(origin.x - r, origin.y - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = bodyH * 0.011f, cap = StrokeCap.Round),
        )
    }
}
