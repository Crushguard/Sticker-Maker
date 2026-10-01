package com.piptechnologies.stickermaker.feature.create.decor

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** CSS timing functions used by presets.json. */
object Easings {
    fun ease(easing: Easing, u: Float): Float = when (easing) {
        Easing.Linear -> u
        Easing.EaseIn -> cubicBezier(0.42f, 0f, 1f, 1f, u)
        Easing.EaseOut -> cubicBezier(0f, 0f, 0.58f, 1f, u)
        Easing.EaseInOut -> cubicBezier(0.42f, 0f, 0.58f, 1f, u)
    }

    /** `cubic-bezier(x1, y1, x2, y2)` at progress [x] in 0..1 (bisection on x, then y). */
    fun cubicBezier(x1: Float, y1: Float, x2: Float, y2: Float, x: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        fun bx(t: Float) = 3f * x1 * t * (1 - t) * (1 - t) + 3f * x2 * t * t * (1 - t) + t * t * t
        fun by(t: Float) = 3f * y1 * t * (1 - t) * (1 - t) + 3f * y2 * t * t * (1 - t) + t * t * t
        var lo = 0f
        var hi = 1f
        var t = x
        repeat(40) {
            val v = bx(t)
            if (abs(v - x) < 1e-6f) return by(t)
            if (v < x) lo = t else hi = t
            t = (lo + hi) / 2f
        }
        return by(t)
    }
}

/** The keyframe transform of one moment; canvas fractions for dx/dy, degrees for rotate. */
data class Pose(val scaleX: Float, val scaleY: Float, val rotate: Float, val dx: Float, val dy: Float) {
    companion object { val REST = Pose(1f, 1f, 0f, 0f, 0f) }
}

/**
 * 2-D affine map: x' = a·x + c·y + e, y' = b·x + d·y + f (y down, positive
 * angles clockwise on screen). [matrixValues] feeds android.graphics.Matrix.
 */
data class Affine(val a: Float, val b: Float, val c: Float, val d: Float, val e: Float, val f: Float) {
    operator fun times(o: Affine) = Affine(
        a = a * o.a + c * o.b, b = b * o.a + d * o.b,
        c = a * o.c + c * o.d, d = b * o.c + d * o.d,
        e = a * o.e + c * o.f + e, f = b * o.e + d * o.f + f
    )

    fun map(x: Float, y: Float) = floatArrayOf(a * x + c * y + e, b * x + d * y + f)

    fun matrixValues() = floatArrayOf(a, c, e, b, d, f, 0f, 0f, 1f)

    companion object {
        val IDENTITY = Affine(1f, 0f, 0f, 1f, 0f, 0f)
        fun translate(tx: Float, ty: Float) = Affine(1f, 0f, 0f, 1f, tx, ty)
        fun scale(sx: Float, sy: Float) = Affine(sx, 0f, 0f, sy, 0f, 0f)
        fun rotate(deg: Float): Affine {
            val r = deg * PI.toFloat() / 180f
            val cs = cos(r)
            val sn = sin(r)
            return Affine(cs, sn, -sn, cs, 0f, 0f)
        }
    }
}

/** One particle at one moment, in canvas px. [size] is the unscaled box edge. */
data class ParticleState(
    val kind: ParticleKind,
    val x: Float,
    val y: Float,
    val size: Float,
    val scale: Float,
    val rotation: Float,
    val alpha: Float,
    val colour: Int
)

/** Evaluates presets.json (spec §7). Pure; shared by the live canvas and the exporter. */
object MotionMath {
    const val CANVAS = 512f
    const val MIN_FRAME_MS = 8

    private val HEART_X = floatArrayOf(0.18f, 0.34f, 0.50f, 0.66f, 0.82f, 0.28f)
    private val SPARKLE_XY = arrayOf(0.16f to 0.20f, 0.82f to 0.16f, 0.88f to 0.60f, 0.12f to 0.66f, 0.50f to 0.08f, 0.70f to 0.88f)
    private const val SPARKLE_STAGGER_MS = 133f

    fun pose(preset: MotionPreset, t: Float): Pose {
        val ks = preset.keyframes
        if (preset.isNone || ks.size == 1) return ks.first().toPose()
        val tt = t.coerceIn(0f, 1f)
        val i = ks.indexOfLast { it.t <= tt }.coerceIn(0, ks.size - 2)
        val k0 = ks[i]
        val k1 = ks[i + 1]
        val span = k1.t - k0.t
        if (span <= 0f) return k1.toPose()
        val u = Easings.ease(k0.easing, ((tt - k0.t) / span).coerceIn(0f, 1f))
        return Pose(lerp(k0.scaleX, k1.scaleX, u), lerp(k0.scaleY, k1.scaleY, u), lerp(k0.rotate, k1.rotate, u), lerp(k0.dx, k1.dx, u), lerp(k0.dy, k1.dy, u))
    }

    /** `S_c(baseScale) · T(p + d) · R · S(scaleX, scaleY) · T(−p)` (spec §7). */
    fun transform(preset: MotionPreset, t: Float, canvas: Float = CANVAS): Affine {
        if (preset.isNone) return Affine.IDENTITY
        val p = pose(preset, t)
        val px = preset.pivotX * canvas
        val py = preset.pivotY * canvas
        val c = canvas / 2f
        val inner = Affine.translate(px + p.dx * canvas, py + p.dy * canvas) *
            Affine.rotate(p.rotate) * Affine.scale(p.scaleX, p.scaleY) * Affine.translate(-px, -py)
        val outer = Affine.translate(c, c) * Affine.scale(preset.baseScale, preset.baseScale) * Affine.translate(-c, -c)
        return outer * inner
    }

    /** Display time of each exported frame: sums to durationMs, none under [MIN_FRAME_MS]. */
    fun frameDurations(preset: MotionPreset): List<Int> {
        val n = preset.frames.coerceAtLeast(1)
        val d = preset.durationMs.toLong()
        return List(n) { i -> ((i + 1L) * d / n - i * d / n).toInt().coerceAtLeast(MIN_FRAME_MS) }
    }

    fun particles(preset: MotionPreset, timeMs: Float, canvas: Float = CANVAS): List<ParticleState> {
        val spec = preset.particles ?: return emptyList()
        return List(spec.count) { i ->
            val size = lerp(spec.sizeMin, spec.sizeMax, (i % 3) / 2f) * canvas
            when (spec.kind) {
                ParticleKind.Hearts -> {
                    val life = spec.lifetimeMs.toFloat()
                    val stagger = life / spec.count
                    val phase = floorMod(timeMs - i * stagger, life) / life
                    val alpha = when {
                        phase < 0.15f -> phase / 0.15f
                        phase < 0.6f -> 1f
                        else -> (1f - phase) / 0.4f
                    }
                    ParticleState(
                        kind = ParticleKind.Hearts,
                        x = (HEART_X[i % HEART_X.size] + 0.04f * sin(2f * PI.toFloat() * phase)) * canvas,
                        y = lerp(0.88f, 0.26f, phase) * canvas,
                        size = size,
                        scale = lerp(0.6f, 1f, phase),
                        rotation = 0f,
                        alpha = alpha.coerceIn(0f, 1f),
                        colour = spec.colour
                    )
                }
                ParticleKind.Sparkle -> {
                    val (sx, sy) = SPARKLE_XY[i % SPARKLE_XY.size]
                    val local = floorMod(timeMs - i * SPARKLE_STAGGER_MS, preset.durationMs.toFloat())
                    val life = spec.lifetimeMs.toFloat()
                    if (local >= life) {
                        ParticleState(ParticleKind.Sparkle, sx * canvas, sy * canvas, size, 0f, 0f, 0f, spec.colour)
                    } else {
                        val u = local / life
                        val s = if (u < 0.5f) u / 0.5f else (1f - u) / 0.5f
                        ParticleState(ParticleKind.Sparkle, sx * canvas, sy * canvas, size, s, 45f * u, 1f, spec.colour)
                    }
                }
            }
        }
    }

    private fun Keyframe.toPose() = Pose(scaleX, scaleY, rotate, dx, dy)
    private fun lerp(a: Float, b: Float, u: Float) = a + (b - a) * u
    private fun floorMod(x: Float, m: Float): Float = ((x % m) + m) % m
}
