package app.exteraless.appearance

import android.graphics.Matrix
import android.graphics.Path
import android.util.LruCache
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import androidx.graphics.shapes.transformed
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Material Design 3 Expressive avatar shapes.
 * Definitions are ported directly from official AndroidX upstream (MaterialShapes.kt).
 */
object Md3AvatarShapes {

    const val PEER_TYPE_USER = 0
    const val PEER_TYPE_BOT = 1
    const val PEER_TYPE_GROUP = 2
    const val PEER_TYPE_CHANNEL = 3
    const val PEER_TYPE_SELF = 4

    const val SHAPE_CIRCLE = 0
    const val SHAPE_SQUARE = 1
    const val SHAPE_SLANTED = 2
    const val SHAPE_ARCH = 3
    const val SHAPE_SEMICIRCLE = 4
    const val SHAPE_OVAL = 5
    const val SHAPE_PILL = 6
    const val SHAPE_TRIANGLE = 7
    const val SHAPE_ARROW = 8
    const val SHAPE_FAN = 9
    const val SHAPE_DIAMOND = 10
    const val SHAPE_CLAMSHELL = 11
    const val SHAPE_PENTAGON = 12
    const val SHAPE_GEM = 13
    const val SHAPE_SUNNY = 14
    const val SHAPE_VERY_SUNNY = 15
    const val SHAPE_COOKIE_4 = 16
    const val SHAPE_COOKIE_6 = 17
    const val SHAPE_COOKIE_7 = 18
    const val SHAPE_COOKIE_9 = 19
    const val SHAPE_COOKIE_12 = 20
    const val SHAPE_CLOVER_4 = 21
    const val SHAPE_CLOVER_8 = 22
    const val SHAPE_BURST = 23
    const val SHAPE_SOFT_BURST = 24
    const val SHAPE_BOOM = 25
    const val SHAPE_SOFT_BOOM = 26
    const val SHAPE_FLOWER = 27
    const val SHAPE_PUFFY = 28
    const val SHAPE_PUFFY_DIAMOND = 29
    const val SHAPE_GHOSTISH = 30
    const val SHAPE_PIXEL_CIRCLE = 31
    const val SHAPE_PIXEL_TRIANGLE = 32
    const val SHAPE_BUN = 33
    const val SHAPE_HEART = 34

    const val SHAPES_COUNT = 35

    @JvmField
    val SHAPE_NAMES = arrayOf(
        "Circle",
        "Square",
        "Slanted",
        "Arch",
        "Semicircle",
        "Oval",
        "Pill",
        "Triangle",
        "Arrow",
        "Fan",
        "Diamond",
        "Clamshell",
        "Pentagon",
        "Gem",
        "Sunny",
        "Very sunny",
        "4-sided cookie",
        "6-sided cookie",
        "7-sided cookie",
        "9-sided cookie",
        "12-sided cookie",
        "4-leaf clover",
        "8-leaf clover",
        "Burst",
        "Soft burst",
        "Boom",
        "Soft boom",
        "Flower",
        "Puffy",
        "Puffy diamond",
        "Ghost-ish",
        "Pixel circle",
        "Pixel triangle",
        "Bun",
        "Heart"
    )

    private val cornerRound15 = CornerRounding(radius = .15f)
    private val cornerRound20 = CornerRounding(radius = .2f)
    private val cornerRound30 = CornerRounding(radius = .3f)
    private val cornerRound50 = CornerRounding(radius = .5f)
    private val cornerRound100 = CornerRounding(radius = 1f)

    private val rotateNeg45 = Matrix().apply { setRotate(-45f) }
    private val rotateNeg90 = Matrix().apply { setRotate(-90f) }
    private val rotateNeg135 = Matrix().apply { setRotate(-135f) }

    data class Offset(val x: Float, val y: Float) {
        operator fun plus(other: Offset) = Offset(x + other.x, y + other.y)
        operator fun minus(other: Offset) = Offset(x - other.x, y - other.y)
        operator fun times(scale: Float) = Offset(x * scale, y * scale)
        fun getDistance(): Float = hypot(x, y)

        companion object {
            val Zero = Offset(0f, 0f)
        }
    }

    private data class PointNRound(
        val o: Offset,
        val r: CornerRounding = CornerRounding.Unrounded,
    )

    private fun doRepeat(
        points: List<PointNRound>,
        reps: Int,
        center: Offset,
        mirroring: Boolean,
    ): List<PointNRound> =
        if (mirroring) {
            buildList {
                val angles = points.map { (it.o - center).angleDegrees() }
                val distances = points.map { (it.o - center).getDistance() }
                val actualReps = reps * 2
                val sectionAngle = 360f / actualReps
                repeat(actualReps) { rep ->
                    points.indices.forEach { index ->
                        val i = if (rep % 2 == 0) index else points.lastIndex - index
                        if (i > 0 || rep % 2 == 0) {
                            val a =
                                (sectionAngle * rep +
                                        if (rep % 2 == 0) angles[i]
                                        else sectionAngle - angles[i] + 2 * angles[0])
                                    .toRadians()
                            val finalPoint = Offset(cos(a), sin(a)) * distances[i] + center
                            add(PointNRound(finalPoint, points[i].r))
                        }
                    }
                }
            }
        } else {
            points.size.let { np ->
                (0 until np * reps).map {
                    val point = points[it % np].o.rotateDegrees((it / np) * 360f / reps, center)
                    PointNRound(point, points[it % np].r)
                }
            }
        }

    private fun Offset.rotateDegrees(angle: Float, center: Offset = Offset.Zero): Offset =
        (angle.toRadians()).let { a ->
            val off = this - center
            Offset(off.x * cos(a) - off.y * sin(a), off.x * sin(a) + off.y * cos(a)) + center
        }

    private fun Float.toRadians(): Float = this / 360f * 2 * PI.toFloat()

    private fun Offset.angleDegrees(): Float = atan2(y, x) * 180f / PI.toFloat()

    private fun customPolygon(
        pnr: List<PointNRound>,
        reps: Int,
        center: Offset = Offset(0.5f, 0.5f),
        mirroring: Boolean = false,
    ): RoundedPolygon {
        val actualPoints = doRepeat(pnr, reps, center, mirroring)
        return RoundedPolygon(
            vertices = FloatArray(actualPoints.size * 2) { ix ->
                actualPoints[ix / 2].o.let { if (ix % 2 == 0) it.x else it.y }
            },
            rounding = CornerRounding.Unrounded,
            perVertexRounding = actualPoints.map { it.r },
            centerX = center.x,
            centerY = center.y,
        )
    }

    private fun buildPolygon(shapeId: Int): RoundedPolygon {
        return when (shapeId) {
            SHAPE_CIRCLE -> RoundedPolygon.circle().normalized()
            SHAPE_SQUARE -> RoundedPolygon.rectangle(width = 1f, height = 1f, rounding = cornerRound30).normalized()
            SHAPE_SLANTED -> customPolygon(
                listOf(
                    PointNRound(Offset(0.926f, 0.970f), CornerRounding(0.189f, 0.811f)),
                    PointNRound(Offset(-0.021f, 0.967f), CornerRounding(0.187f, 0.057f)),
                ),
                2,
            ).normalized()
            SHAPE_ARCH -> RoundedPolygon(
                numVertices = 4,
                rounding = CornerRounding.Unrounded,
                perVertexRounding = listOf(cornerRound100, cornerRound100, cornerRound20, cornerRound20),
            ).transformed(rotateNeg135).normalized()
            SHAPE_SEMICIRCLE -> RoundedPolygon.rectangle(
                width = 1.6f,
                height = 1f,
                perVertexRounding = listOf(cornerRound20, cornerRound20, cornerRound100, cornerRound100),
            ).normalized()
            SHAPE_OVAL -> {
                val m = Matrix().apply { setScale(1f, 0.64f) }
                RoundedPolygon.circle().transformed(m).transformed(rotateNeg45).normalized()
            }
            SHAPE_PILL -> customPolygon(
                listOf(
                    PointNRound(Offset(0.961f, 0.039f), CornerRounding(0.426f)),
                    PointNRound(Offset(1.001f, 0.428f)),
                    PointNRound(Offset(1.000f, 0.609f), CornerRounding(1.000f)),
                ),
                reps = 2,
                mirroring = true,
            ).normalized()
            SHAPE_TRIANGLE -> RoundedPolygon(numVertices = 3, rounding = cornerRound20)
                .transformed(rotateNeg90).normalized()
            SHAPE_ARROW -> customPolygon(
                listOf(
                    PointNRound(Offset(0.500f, 0.892f), CornerRounding(0.313f)),
                    PointNRound(Offset(-0.216f, 1.050f), CornerRounding(0.207f)),
                    PointNRound(Offset(0.499f, -0.160f), CornerRounding(0.215f, 1.000f)),
                    PointNRound(Offset(1.225f, 1.060f), CornerRounding(0.211f)),
                ),
                1,
            ).normalized()
            SHAPE_FAN -> customPolygon(
                listOf(
                    PointNRound(Offset(1.004f, 1.000f), CornerRounding(0.148f, 0.417f)),
                    PointNRound(Offset(0.000f, 1.000f), CornerRounding(0.151f)),
                    PointNRound(Offset(0.000f, -0.003f), CornerRounding(0.148f)),
                    PointNRound(Offset(0.978f, 0.020f), CornerRounding(0.803f)),
                ),
                1,
            ).normalized()
            SHAPE_DIAMOND -> customPolygon(
                listOf(
                    PointNRound(Offset(0.500f, 1.096f), CornerRounding(0.151f, 0.524f)),
                    PointNRound(Offset(0.040f, 0.500f), CornerRounding(0.159f)),
                ),
                2,
            ).normalized()
            SHAPE_CLAMSHELL -> customPolygon(
                listOf(
                    PointNRound(Offset(0.171f, 0.841f), CornerRounding(0.159f)),
                    PointNRound(Offset(-0.020f, 0.500f), CornerRounding(0.140f)),
                    PointNRound(Offset(0.170f, 0.159f), CornerRounding(0.159f)),
                ),
                2,
            ).normalized()
            SHAPE_PENTAGON -> customPolygon(
                listOf(
                    PointNRound(Offset(0.500f, -0.009f), CornerRounding(0.172f)),
                    PointNRound(Offset(1.030f, 0.365f), CornerRounding(0.164f)),
                    PointNRound(Offset(0.828f, 0.970f), CornerRounding(0.169f)),
                ),
                reps = 1,
                mirroring = true,
            ).normalized()
            SHAPE_GEM -> customPolygon(
                listOf(
                    PointNRound(Offset(0.499f, 1.023f), CornerRounding(0.241f, 0.778f)),
                    PointNRound(Offset(-0.005f, 0.792f), CornerRounding(0.208f)),
                    PointNRound(Offset(0.073f, 0.258f), CornerRounding(0.228f)),
                    PointNRound(Offset(0.433f, -0.000f), CornerRounding(0.491f)),
                ),
                1,
                mirroring = true,
            ).normalized()
            SHAPE_SUNNY -> RoundedPolygon.star(
                numVerticesPerRadius = 8,
                innerRadius = .8f,
                rounding = cornerRound15,
            ).normalized()
            SHAPE_VERY_SUNNY -> customPolygon(
                listOf(
                    PointNRound(Offset(0.500f, 1.080f), CornerRounding(0.085f)),
                    PointNRound(Offset(0.358f, 0.843f), CornerRounding(0.085f)),
                ),
                8,
            ).normalized()
            SHAPE_COOKIE_4 -> customPolygon(
                listOf(
                    PointNRound(Offset(1.237f, 1.236f), CornerRounding(0.258f)),
                    PointNRound(Offset(0.500f, 0.918f), CornerRounding(0.233f)),
                ),
                4,
            ).normalized()
            SHAPE_COOKIE_6 -> customPolygon(
                listOf(
                    PointNRound(Offset(0.723f, 0.884f), CornerRounding(0.394f)),
                    PointNRound(Offset(0.500f, 1.099f), CornerRounding(0.398f)),
                ),
                6,
            ).normalized()
            SHAPE_COOKIE_7 -> RoundedPolygon.star(
                numVerticesPerRadius = 7,
                innerRadius = .75f,
                rounding = cornerRound50,
            ).transformed(rotateNeg90).normalized()
            SHAPE_COOKIE_9 -> RoundedPolygon.star(
                numVerticesPerRadius = 9,
                innerRadius = .8f,
                rounding = cornerRound50,
            ).transformed(rotateNeg90).normalized()
            SHAPE_COOKIE_12 -> RoundedPolygon.star(
                numVerticesPerRadius = 12,
                innerRadius = .8f,
                rounding = cornerRound50,
            ).transformed(rotateNeg90).normalized()
            SHAPE_CLOVER_4 -> customPolygon(
                listOf(
                    PointNRound(Offset(0.500f, 0.074f)),
                    PointNRound(Offset(0.725f, -0.099f), CornerRounding(0.476f)),
                ),
                reps = 4,
                mirroring = true,
            ).normalized()
            SHAPE_CLOVER_8 -> customPolygon(
                listOf(
                    PointNRound(Offset(0.500f, 0.036f)),
                    PointNRound(Offset(0.758f, -0.101f), CornerRounding(0.209f)),
                ),
                reps = 8,
            ).normalized()
            SHAPE_BURST -> customPolygon(
                listOf(
                    PointNRound(Offset(0.500f, -0.006f), CornerRounding(0.006f)),
                    PointNRound(Offset(0.592f, 0.158f), CornerRounding(0.006f)),
                ),
                reps = 12,
            ).normalized()
            SHAPE_SOFT_BURST -> customPolygon(
                listOf(
                    PointNRound(Offset(0.193f, 0.277f), CornerRounding(0.053f)),
                    PointNRound(Offset(0.176f, 0.055f), CornerRounding(0.053f)),
                ),
                reps = 10,
            ).normalized()
            SHAPE_BOOM -> customPolygon(
                listOf(
                    PointNRound(Offset(0.457f, 0.296f), CornerRounding(0.007f)),
                    PointNRound(Offset(0.500f, -0.051f), CornerRounding(0.007f)),
                ),
                reps = 15,
            ).normalized()
            SHAPE_SOFT_BOOM -> customPolygon(
                listOf(
                    PointNRound(Offset(0.733f, 0.454f)),
                    PointNRound(Offset(0.839f, 0.437f), CornerRounding(0.532f)),
                    PointNRound(Offset(0.949f, 0.449f), CornerRounding(0.439f, 1.000f)),
                    PointNRound(Offset(0.998f, 0.478f), CornerRounding(0.174f)),
                ),
                reps = 16,
                mirroring = true,
            ).normalized()
            SHAPE_FLOWER -> customPolygon(
                listOf(
                    PointNRound(Offset(0.370f, 0.187f)),
                    PointNRound(Offset(0.416f, 0.049f), CornerRounding(0.381f)),
                    PointNRound(Offset(0.479f, 0.001f), CornerRounding(0.095f)),
                ),
                reps = 8,
                mirroring = true,
            ).normalized()
            SHAPE_PUFFY -> {
                val m = Matrix().apply { setScale(1f, 0.742f) }
                customPolygon(
                    listOf(
                        PointNRound(Offset(0.500f, 0.053f)),
                        PointNRound(Offset(0.545f, -0.040f), CornerRounding(0.405f)),
                        PointNRound(Offset(0.670f, -0.035f), CornerRounding(0.426f)),
                        PointNRound(Offset(0.717f, 0.066f), CornerRounding(0.574f)),
                        PointNRound(Offset(0.722f, 0.128f)),
                        PointNRound(Offset(0.777f, 0.002f), CornerRounding(0.360f)),
                        PointNRound(Offset(0.914f, 0.149f), CornerRounding(0.660f)),
                        PointNRound(Offset(0.926f, 0.289f), CornerRounding(0.660f)),
                        PointNRound(Offset(0.881f, 0.346f)),
                        PointNRound(Offset(0.940f, 0.344f), CornerRounding(0.126f)),
                        PointNRound(Offset(1.003f, 0.437f), CornerRounding(0.255f)),
                    ),
                    reps = 2,
                    mirroring = true,
                ).transformed(m).normalized()
            }
            SHAPE_PUFFY_DIAMOND -> customPolygon(
                listOf(
                    PointNRound(Offset(0.870f, 0.130f), CornerRounding(0.146f)),
                    PointNRound(Offset(0.818f, 0.357f)),
                    PointNRound(Offset(1.000f, 0.332f), CornerRounding(0.853f)),
                ),
                reps = 4,
                mirroring = true,
            ).normalized()
            SHAPE_GHOSTISH -> customPolygon(
                listOf(
                    PointNRound(Offset(0.500f, 0f), CornerRounding(1.000f)),
                    PointNRound(Offset(1f, 0f), CornerRounding(1.000f)),
                    PointNRound(Offset(1f, 1.140f), CornerRounding(0.254f, 0.106f)),
                    PointNRound(Offset(0.575f, 0.906f), CornerRounding(0.253f)),
                ),
                reps = 1,
                mirroring = true,
            ).normalized()
            SHAPE_PIXEL_CIRCLE -> customPolygon(
                listOf(
                    PointNRound(Offset(0.500f, 0.000f)),
                    PointNRound(Offset(0.704f, 0.000f)),
                    PointNRound(Offset(0.704f, 0.065f)),
                    PointNRound(Offset(0.843f, 0.065f)),
                    PointNRound(Offset(0.843f, 0.148f)),
                    PointNRound(Offset(0.926f, 0.148f)),
                    PointNRound(Offset(0.926f, 0.296f)),
                    PointNRound(Offset(1.000f, 0.296f)),
                ),
                reps = 2,
                mirroring = true,
            ).normalized()
            SHAPE_PIXEL_TRIANGLE -> customPolygon(
                listOf(
                    PointNRound(Offset(0.110f, 0.500f)),
                    PointNRound(Offset(0.113f, 0.000f)),
                    PointNRound(Offset(0.287f, 0.000f)),
                    PointNRound(Offset(0.287f, 0.087f)),
                    PointNRound(Offset(0.421f, 0.087f)),
                    PointNRound(Offset(0.421f, 0.170f)),
                    PointNRound(Offset(0.560f, 0.170f)),
                    PointNRound(Offset(0.560f, 0.265f)),
                    PointNRound(Offset(0.674f, 0.265f)),
                    PointNRound(Offset(0.675f, 0.344f)),
                    PointNRound(Offset(0.789f, 0.344f)),
                    PointNRound(Offset(0.789f, 0.439f)),
                    PointNRound(Offset(0.888f, 0.439f)),
                ),
                reps = 1,
                mirroring = true,
            ).normalized()
            SHAPE_BUN -> customPolygon(
                listOf(
                    PointNRound(Offset(0.796f, 0.500f)),
                    PointNRound(Offset(0.853f, 0.518f), CornerRounding(1f)),
                    PointNRound(Offset(0.992f, 0.631f), CornerRounding(1f)),
                    PointNRound(Offset(0.968f, 1.000f), CornerRounding(1f)),
                ),
                reps = 2,
                mirroring = true,
            ).normalized()
            SHAPE_HEART -> customPolygon(
                listOf(
                    PointNRound(Offset(0.500f, 0.268f), CornerRounding(0.016f)),
                    PointNRound(Offset(0.792f, -0.066f), CornerRounding(0.958f)),
                    PointNRound(Offset(1.064f, 0.276f), CornerRounding(1.000f)),
                    PointNRound(Offset(0.501f, 0.946f), CornerRounding(0.129f)),
                ),
                reps = 1,
                mirroring = true,
            ).normalized()
            else -> RoundedPolygon.circle().normalized()
        }
    }

    private val basePolygons = arrayOfNulls<RoundedPolygon>(SHAPES_COUNT)
    private val basePaths = arrayOfNulls<Path>(SHAPES_COUNT)

    @Synchronized
    fun getBasePath(shapeId: Int): Path {
        val id = shapeId.coerceIn(0, SHAPES_COUNT - 1)
        val existing = basePaths[id]
        if (existing != null) return existing

        var polygon = basePolygons[id]
        if (polygon == null) {
            polygon = buildPolygon(id)
            basePolygons[id] = polygon
        }
        val path = polygon.toPath()
        basePaths[id] = path
        return path
    }

    /**
     * Cache for transformed Paths keyed by (shapeId, width, height).
     */
    private val pathCache = object : LruCache<Long, Path>(64) {}

    private val tempMatrix = Matrix()

    /**
     * Returns a Path for the given shape scaled to (width, height) with origin (0, 0).
     */
    @JvmStatic
    @Synchronized
    fun getShapePath(shapeId: Int, width: Float, height: Float): Path {
        val wInt = width.toInt().coerceAtLeast(1)
        val hInt = height.toInt().coerceAtLeast(1)
        val id = shapeId.coerceIn(0, SHAPES_COUNT - 1)
        val key = (id.toLong() shl 32) or (wInt.toLong() shl 16) or hInt.toLong()
        val cached = pathCache.get(key)
        if (cached != null) return cached

        val basePath = getBasePath(id)
        val path = Path()
        tempMatrix.reset()
        tempMatrix.setScale(width, height)
        basePath.transform(tempMatrix, path)
        pathCache.put(key, path)
        return path
    }

    /**
     * Returns a transformed Path offset to (left, top).
     */
    @JvmStatic
    fun getShapePath(shapeId: Int, left: Float, top: Float, width: Float, height: Float): Path {
        val basePath = getShapePath(shapeId, width, height)
        if (left == 0f && top == 0f) return basePath
        val path = Path(basePath)
        path.offset(left, top)
        return path
    }
}
