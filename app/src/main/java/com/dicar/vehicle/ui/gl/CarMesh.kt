package com.dicar.vehicle.ui.gl

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Triangle mesh with interleaved position xyz and normal xyz; flat shaded without shared vertices. */
class Mesh(val data: FloatArray, val vertexCount: Int)

/**
 * Triangle accumulator with per-face normals for an intentionally low-poly appearance.
 * Flat shading avoids shared vertices and smooth normals, keeping geometry light for the head-unit GPU.
 */
class MeshBuilder {
    private val out = ArrayList<Float>(2048)
    private var count = 0

    fun tri(a: FloatArray, b: FloatArray, c: FloatArray) {
        val n = normalOf(a, b, c)
        for (p in arrayOf(a, b, c)) {
            out.add(p[0]); out.add(p[1]); out.add(p[2])
            out.add(n[0]); out.add(n[1]); out.add(n[2])
            count++
        }
    }

    fun quad(a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray) {
        tri(a, b, c); tri(a, c, d)
    }

    /**
     * Determine winding automatically so the normal points away from [center].
     * A dot product avoids error-prone manual winding of lofted faces, which can disappear under back-face culling.
     */
    fun quadOutward(a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray, center: FloatArray) {
        val n = normalOf(a, b, c)
        val mx = (a[0] + b[0] + c[0] + d[0]) / 4f - center[0]
        val my = (a[1] + b[1] + c[1] + d[1]) / 4f - center[1]
        val mz = (a[2] + b[2] + c[2] + d[2]) / 4f - center[2]
        if (n[0] * mx + n[1] * my + n[2] * mz >= 0f) quad(a, b, c, d) else quad(d, c, b, a)
    }

    fun triOutward(a: FloatArray, b: FloatArray, c: FloatArray, center: FloatArray) {
        val n = normalOf(a, b, c)
        val mx = (a[0] + b[0] + c[0]) / 3f - center[0]
        val my = (a[1] + b[1] + c[1]) / 3f - center[1]
        val mz = (a[2] + b[2] + c[2]) / 3f - center[2]
        if (n[0] * mx + n[1] * my + n[2] * mz >= 0f) tri(a, b, c) else tri(c, b, a)
    }

    val isEmpty: Boolean get() = count == 0

    fun build() = Mesh(out.toFloatArray(), count)
}

private fun normalOf(a: FloatArray, b: FloatArray, c: FloatArray): FloatArray {
    val ux = b[0] - a[0]; val uy = b[1] - a[1]; val uz = b[2] - a[2]
    val vx = c[0] - a[0]; val vy = c[1] - a[1]; val vz = c[2] - a[2]
    var nx = uy * vz - uz * vy
    var ny = uz * vx - ux * vz
    var nz = ux * vy - uy * vx
    val len = sqrt(nx * nx + ny * ny + nz * nz)
    if (len > 1e-6f) { nx /= len; ny /= len; nz /= len } else { ny = 1f }
    return floatArrayOf(nx, ny, nz)
}

private fun v(x: Float, y: Float, z: Float) = floatArrayOf(x, y, z)

/** One door with separate metal and glass; the hinge is the vertical axis at its front edge. */
class DoorPart(
    val panel: Mesh,
    val glass: Mesh,
    /** Hinge position in world coordinates; rotates around Y. */
    val hingeX: Float,
    val hingeZ: Float,
    /** Window glass scales along Y around this height to simulate lowering. */
    val beltlineY: Float,
    /** Left is -1 and right is +1, determining the opening direction. */
    val side: Float,
)

class CarParts(
    val body: Mesh,
    val glass: Mesh,
    val interior: Mesh,
    val doors: List<DoorPart>,
    val wheel: Mesh,
    /** Separate left and right lamps for indicators. Order: [front left, front right, rear left, rear right]. */
    val lights: List<Mesh>,
    val shadow: Mesh,
)

/**
 * Procedurally generated sedan.
 *
 * Cross sections along the length are lofted into faces. Each traces the outline from the lower left,
 * over the roof and down to the lower right. Section parameters control the nose, beltline and roof.
 *
 * Door sections own the corresponding side faces instead of overlaying the body.
 * The body is open there, revealing a dark interior shell when doors open.
 */
object CarMesh {

    /** Car length in meters, used for camera distance and shadow size. */
    const val LENGTH = 4.62f
    const val WIDTH = 1.85f
    const val HEIGHT = 1.47f

    private val CENTER = v(0f, 0.72f, 0f)

    private const val WHEEL_RADIUS = 0.33f
    private const val WHEEL_WIDTH = 0.22f

    /**
     * Wheel center lateral position. Tires must protrude slightly beyond the body (beltline half-width 0.925)
     * or they will be hidden inside it.
     */
    const val WHEEL_X = 0.845f
    const val WHEEL_Z_FRONT = 1.40f
    const val WHEEL_Z_REAR = -1.36f

    /**
     * A top-height change above this threshold identifies a front or rear windscreen.
     * A lower threshold would also classify the 0.15 drop from the rear C pillar to the trunk as glass.
     */
    private const val SCREEN_DROP = 0.20f

    /**
     * Cross section: [halfW] beltline half-width, [yBot] bottom height, [yBelt] beltline height,
     * [yTop] top height, and [topHalfW] top half-width.
     */
    private class Sec(
        val z: Float,
        val halfW: Float,
        val yBot: Float,
        val yBelt: Float,
        val yTop: Float,
        val topHalfW: Float,
    ) {
        /** Eleven points: 0..4 rise along the left, 5 is the roof center, 6..10 mirror the right. */
        fun outline(): Array<FloatArray> {
            val yMid = yBot + (yBelt - yBot) * 0.45f
            val yShoulder = yBelt + (yTop - yBelt) * 0.55f
            return arrayOf(
                floatArrayOf(-halfW * 0.80f, yBot),
                floatArrayOf(-halfW, yMid),
                floatArrayOf(-halfW, yBelt),
                floatArrayOf(-topHalfW, yShoulder),
                floatArrayOf(-topHalfW * 0.86f, yTop),
                floatArrayOf(0f, yTop),
                floatArrayOf(topHalfW * 0.86f, yTop),
                floatArrayOf(topHalfW, yShoulder),
                floatArrayOf(halfW, yBelt),
                floatArrayOf(halfW, yMid),
                floatArrayOf(halfW * 0.80f, yBot),
            )
        }
    }

    // The front points along +Z. yTop rises from 0.82 at the nose to 1.47 at the cabin and falls toward the rear;
    // the steep sections form the front and rear windscreens.
    private val SECTIONS = listOf(
        Sec(z = 2.31f, halfW = 0.70f, yBot = 0.44f, yBelt = 0.70f, yTop = 0.72f, topHalfW = 0.66f),
        Sec(z = 2.02f, halfW = 0.86f, yBot = 0.33f, yBelt = 0.77f, yTop = 0.80f, topHalfW = 0.82f),
        Sec(z = 1.56f, halfW = 0.915f, yBot = 0.30f, yBelt = 0.84f, yTop = 0.86f, topHalfW = 0.88f),
        Sec(z = 1.02f, halfW = 0.925f, yBot = 0.30f, yBelt = 0.92f, yTop = 1.16f, topHalfW = 0.78f),
        Sec(z = 0.44f, halfW = 0.925f, yBot = 0.30f, yBelt = 0.95f, yTop = 1.46f, topHalfW = 0.66f),
        Sec(z = -0.26f, halfW = 0.925f, yBot = 0.30f, yBelt = 0.95f, yTop = 1.47f, topHalfW = 0.66f),
        Sec(z = -0.96f, halfW = 0.905f, yBot = 0.31f, yBelt = 0.94f, yTop = 1.37f, topHalfW = 0.63f),
        Sec(z = -1.52f, halfW = 0.885f, yBot = 0.33f, yBelt = 0.92f, yTop = 1.08f, topHalfW = 0.74f),
        Sec(z = -1.98f, halfW = 0.845f, yBot = 0.36f, yBelt = 0.90f, yTop = 0.93f, topHalfW = 0.81f),
        Sec(z = -2.31f, halfW = 0.70f, yBot = 0.44f, yBelt = 0.82f, yTop = 0.84f, topHalfW = 0.66f),
    )

    // Door section ranges: front 3→5, rear 5→7
    private const val FRONT_DOOR_FROM = 3
    private const val FRONT_DOOR_TO = 5
    private const val REAR_DOOR_FROM = 5
    private const val REAR_DOOR_TO = 7

    /**
     * Profile edges assigned to doors (edge e connects points e and e+1).
     * Left edges 1 and 2 are the door panel and window; edge 0 is the sill and edge 3 the roof rail.
     * Keep the latter on the body to avoid a roof gap when opening. Right edges are 8 and 7.
     */
    private val LEFT_DOOR_EDGES = setOf(1, 2)
    private val RIGHT_DOOR_EDGES = setOf(7, 8)

    /** Glass edges above the shoulder, only within cabin sections where the top is well above the beltline. */
    private val GLASS_EDGES = setOf(2, 3, 6, 7)

    fun build(): CarParts {
        val body = MeshBuilder()
        val glass = MeshBuilder()
        val doorPanels = Array(4) { MeshBuilder() }
        val doorGlass = Array(4) { MeshBuilder() }

        val outlines = SECTIONS.map { it.outline() }

        for (i in 0 until SECTIONS.size - 1) {
            val s0 = SECTIONS[i]
            val s1 = SECTIONS[i + 1]
            val o0 = outlines[i]
            val o1 = outlines[i + 1]
            // Cabin sections determine whether the two edges above the shoulder are glass.
            val greenhouse = (s0.yTop - s0.yBelt) > 0.18f && (s1.yTop - s1.yBelt) > 0.18f
            // A steep change in top height identifies a front or rear windscreen.
            val screen = abs(s1.yTop - s0.yTop) > SCREEN_DROP

            for (e in 0..10) {
                val e2 = (e + 1) % 11
                val a = v(o0[e][0], o0[e][1], s0.z)
                val b = v(o0[e2][0], o0[e2][1], s0.z)
                val c = v(o1[e2][0], o1[e2][1], s1.z)
                val d = v(o1[e][0], o1[e][1], s1.z)

                val door = doorIndexFor(i, e)
                val isGlass = when {
                    screen && e in 3..6 -> true          // Front and rear windscreens span the roof edges
                    greenhouse && e in GLASS_EDGES -> true
                    else -> false
                }
                val target = when {
                    door >= 0 && isGlass -> doorGlass[door]
                    door >= 0 -> doorPanels[door]
                    isGlass -> glass
                    else -> body
                }
                target.quadOutward(a, b, c, d, CENTER)
            }
        }

        // Close the front and rear
        capSection(body, outlines.first(), SECTIONS.first().z)
        capSection(body, outlines.last(), SECTIONS.last().z)

        return CarParts(
            body = body.build(),
            glass = glass.build(),
            interior = buildInterior(),
            doors = buildDoors(doorPanels, doorGlass),
            wheel = buildWheel(),
            lights = listOf(
                lamp(front = true, side = -1f), lamp(front = true, side = 1f),
                lamp(front = false, side = -1f), lamp(front = false, side = 1f),
            ),
            shadow = buildShadow(),
        )
    }

    /** Door owning this edge: 0 front left, 1 front right, 2 rear left, 3 rear right, or -1 for body. */
    private fun doorIndexFor(segment: Int, edge: Int): Int {
        val front = segment in FRONT_DOOR_FROM until FRONT_DOOR_TO
        val rear = segment in REAR_DOOR_FROM until REAR_DOOR_TO
        if (!front && !rear) return -1
        return when {
            edge in LEFT_DOOR_EDGES -> if (front) 0 else 2
            edge in RIGHT_DOOR_EDGES -> if (front) 1 else 3
            else -> -1
        }
    }

    private fun buildDoors(panels: Array<MeshBuilder>, glasses: Array<MeshBuilder>): List<DoorPart> {
        fun hinge(segFrom: Int): Pair<Float, Float> = SECTIONS[segFrom].let { it.halfW to it.z }
        val specs = listOf(
            Triple(0, FRONT_DOOR_FROM, -1f),
            Triple(1, FRONT_DOOR_FROM, 1f),
            Triple(2, REAR_DOOR_FROM, -1f),
            Triple(3, REAR_DOOR_FROM, 1f),
        )
        return specs.map { (idx, segFrom, side) ->
            val (halfW, z) = hinge(segFrom)
            DoorPart(
                panel = panels[idx].build(),
                glass = glasses[idx].build(),
                hingeX = halfW * side,
                hingeZ = z,
                beltlineY = SECTIONS[segFrom].yBelt,
                side = side,
            )
        }
    }

    /** Front/rear cap: triangle fan centered on the section centroid. */
    private fun capSection(mb: MeshBuilder, outline: Array<FloatArray>, z: Float) {
        var cx = 0f; var cy = 0f
        outline.forEach { cx += it[0]; cy += it[1] }
        cx /= outline.size; cy /= outline.size
        val c = v(cx, cy, z)
        for (e in outline.indices) {
            val e2 = (e + 1) % outline.size
            mb.triOutward(c, v(outline[e][0], outline[e][1], z), v(outline[e2][0], outline[e2][1], z), CENTER)
        }
    }

    /**
     * Dark interior shell blocks the view through the car when doors open.
     * It is inset from the body and only a small portion is visible through the doorway.
     */
    private fun buildInterior(): Mesh {
        val mb = MeshBuilder()
        box(mb, -0.76f, 0.26f, -1.62f, 0.76f, 1.30f, 1.08f, v(0f, 0.8f, 0f))
        return mb.build()
    }

    private fun box(
        mb: MeshBuilder,
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        center: FloatArray,
    ) {
        val p = arrayOf(
            v(x0, y0, z0), v(x1, y0, z0), v(x1, y1, z0), v(x0, y1, z0),
            v(x0, y0, z1), v(x1, y0, z1), v(x1, y1, z1), v(x0, y1, z1),
        )
        mb.quadOutward(p[0], p[1], p[2], p[3], center)
        mb.quadOutward(p[4], p[5], p[6], p[7], center)
        mb.quadOutward(p[0], p[4], p[7], p[3], center)
        mb.quadOutward(p[1], p[5], p[6], p[2], center)
        mb.quadOutward(p[3], p[2], p[6], p[7], center)
        mb.quadOutward(p[0], p[1], p[5], p[4], center)
    }

    /** Tire and wheel disc: cylinder along X, origin at the wheel center, shared by all four wheels. */
    private fun buildWheel(): Mesh {
        val mb = MeshBuilder()
        val seg = 16
        val r = WHEEL_RADIUS
        val hw = WHEEL_WIDTH / 2f
        val center = v(0f, 0f, 0f)
        for (i in 0 until seg) {
            val a0 = 2.0 * PI * i / seg
            val a1 = 2.0 * PI * (i + 1) / seg
            val y0 = (r * cos(a0)).toFloat(); val z0 = (r * sin(a0)).toFloat()
            val y1 = (r * cos(a1)).toFloat(); val z1 = (r * sin(a1)).toFloat()
            // Tread
            mb.quadOutward(v(-hw, y0, z0), v(hw, y0, z0), v(hw, y1, z1), v(-hw, y1, z1), center)
            // Side faces, slightly inset to leave the tire sidewall
            val ri = 0.82f
            mb.quadOutward(
                v(hw, y0, z0), v(hw * 0.72f, y0 * ri, z0 * ri),
                v(hw * 0.72f, y1 * ri, z1 * ri), v(hw, y1, z1), center,
            )
            mb.quadOutward(
                v(-hw, y0, z0), v(-hw * 0.72f, y0 * ri, z0 * ri),
                v(-hw * 0.72f, y1 * ri, z1 * ri), v(-hw, y1, z1), center,
            )
            // Wheel disc with a hint of spokes
            mb.tri(v(hw * 0.72f, 0f, 0f), v(hw * 0.72f, y0 * ri, z0 * ri), v(hw * 0.72f, y1 * ri, z1 * ri))
            mb.tri(v(-hw * 0.72f, 0f, 0f), v(-hw * 0.72f, y1 * ri, z1 * ri), v(-hw * 0.72f, y0 * ri, z0 * ri))
        }
        return mb.build()
    }

    /** Single lamp assembly; separate left and right meshes support independent indicators. */
    private fun lamp(front: Boolean, side: Float): Mesh {
        val mb = MeshBuilder()
        // Inset slightly into the front/rear to avoid z-fighting with body faces.
        val zOuter = if (front) 2.26f else -2.26f
        val zInner = if (front) 2.08f else -2.08f
        val y0 = if (front) 0.60f else 0.73f
        val y1 = if (front) 0.75f else 0.85f
        val x0 = side * 0.26f
        val x1 = side * 0.62f
        box(
            mb,
            minOf(x0, x1), y0, minOf(zOuter, zInner),
            maxOf(x0, x1), y1, maxOf(zOuter, zInner),
            CENTER,
        )
        return mb.build()
    }

    /** Elliptical shadow beneath the car to ground it visually. */
    private fun buildShadow(): Mesh {
        val mb = MeshBuilder()
        val seg = 28
        val rx = WIDTH * 0.62f
        val rz = LENGTH * 0.54f
        val c = v(0f, 0.01f, 0f)
        for (i in 0 until seg) {
            val a0 = 2.0 * PI * i / seg
            val a1 = 2.0 * PI * (i + 1) / seg
            mb.tri(
                c,
                v((rx * cos(a1)).toFloat(), 0.01f, (rz * sin(a1)).toFloat()),
                v((rx * cos(a0)).toFloat(), 0.01f, (rz * sin(a0)).toFloat()),
            )
        }
        return mb.build()
    }
}
