package com.piptechnologies.stickermaker.feature.create.decor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionMathTest {

    private fun k(t: Float, sx: Float = 1f, sy: Float = sx, rot: Float = 0f, dx: Float = 0f, dy: Float = 0f, e: Easing = Easing.Linear) =
        Keyframe(t, sx, sy, rot, dx, dy, e)

    private val heartbeat = MotionPreset("heartbeat", 1000, 12, 0.88f, 0.5f, 0.5f,
        listOf(k(0f, e = Easing.EaseInOut), k(0.14f, 1.12f, e = Easing.EaseInOut), k(0.28f, e = Easing.EaseInOut),
            k(0.42f, 1.08f, e = Easing.EaseInOut), k(0.6f), k(1f)), null)
    private val bounce = MotionPreset("bounce", 1000, 12, 0.86f, 0.5f, 1f,
        listOf(k(0f, e = Easing.EaseOut), k(0.35f, 0.96f, 1.04f, dy = -0.07f, e = Easing.EaseIn), k(0.7f, 1.06f, 0.94f, e = Easing.EaseOut), k(1f)), null)
    private val shake = MotionPreset("shake", 800, 12, 0.9f, 0.5f, 0.5f,
        listOf(k(0f, e = Easing.Linear), k(0.2f, rot = -1.5f, dx = -0.03f, e = Easing.Linear), k(0.4f, rot = 1.5f, dx = 0.03f, e = Easing.Linear),
            k(0.6f, rot = -1f, dx = -0.02f, e = Easing.Linear), k(0.8f, rot = 1f, dx = 0.02f, e = Easing.Linear), k(1f)), null)
    private val wiggle = MotionPreset("wiggle", 900, 12, 0.9f, 0.5f, 0.8f,
        listOf(k(0f, e = Easing.EaseInOut), k(0.25f, rot = -5f, e = Easing.EaseInOut), k(0.75f, rot = 5f, e = Easing.EaseInOut), k(1f)), null)
    private val hearts = MotionPreset("hearts", 1800, 16, 0.9f, 0.5f, 0.5f, listOf(k(0f), k(1f)),
        ParticleSpec(ParticleKind.Hearts, 6, 0xFFC23359.toInt(), 0.06f, 0.1f, 1800))
    private val sparkle = MotionPreset("sparkle", 1500, 16, 0.9f, 0.5f, 0.5f, listOf(k(0f), k(1f)),
        ParticleSpec(ParticleKind.Sparkle, 6, 0xFFF5C542.toInt(), 0.05f, 0.09f, 700))

    @Test
    fun cubicBezierMatchesCssEndpointsAndSymmetry() {
        assertEquals(0f, Easings.ease(Easing.EaseInOut, 0f), 0f)
        assertEquals(1f, Easings.ease(Easing.EaseInOut, 1f), 0f)
        assertEquals(0.5f, Easings.ease(Easing.EaseInOut, 0.5f), 1e-3f)
        assertTrue(Easings.ease(Easing.EaseOut, 0.5f) > 0.5f)
        assertTrue(Easings.ease(Easing.EaseIn, 0.5f) < 0.5f)
        assertEquals(0.3f, Easings.ease(Easing.Linear, 0.3f), 0f)
        // Curve anchors at specific points
        assertEquals(0.12916f, Easings.ease(Easing.EaseInOut, 0.25f), 1e-3f)
        assertEquals(0.31536f, Easings.ease(Easing.EaseIn, 0.5f), 1e-3f)
        assertEquals(0.68464f, Easings.ease(Easing.EaseOut, 0.5f), 1e-3f)
    }

    @Test
    fun poseHitsKeyframesAndRestsAtZero() {
        assertEquals(Pose.REST, MotionMath.pose(heartbeat, 0f))
        assertEquals(1.12f, MotionMath.pose(heartbeat, 0.14f).scaleX, 1e-4f)
        assertEquals(1.08f, MotionMath.pose(heartbeat, 0.42f).scaleY, 1e-4f)
        assertEquals(Pose.REST, MotionMath.pose(heartbeat, 1f))
        assertEquals(-0.07f, MotionMath.pose(bounce, 0.35f).dy, 1e-4f)
    }

    @Test
    fun poseInterpolatesBetweenKeyframesWithTheEarlierKeyframesEasing() {
        // Bounce: k0 at t=0 (identity, EaseOut), k1 at t=0.35 (dy=-0.07, EaseIn), k2 at t=0.7 (EaseOut), k3 at t=1
        // At t=0.175 (midpoint between k0 and k1), easing is k0's EaseOut
        // Normalized: u = (0.175 - 0) / (0.35 - 0) = 0.5
        // EaseOut(0.5) ≈ 0.68464, so dy ≈ 0 + (−0.07 - 0) * 0.68464 ≈ −0.047925
        assertEquals(-0.047925f, MotionMath.pose(bounce, 0.175f).dy, 1e-3f)
        // Shake: linear motion; at t=0.1, u = 0.1/0.2 = 0.5, rotate = 0 + (−1.5) * 0.5 = −0.75, dx = 0 + (−0.03) * 0.5 = −0.015
        assertEquals(-0.75f, MotionMath.pose(shake, 0.1f).rotate, 1e-3f)
        assertEquals(-0.015f, MotionMath.pose(shake, 0.1f).dx, 1e-3f)
    }

    @Test
    fun transformAppliesBaseScaleAboutTheCentreAndPoseAboutThePivot() {
        val rest = MotionMath.transform(heartbeat, 0f)
        rest.map(256f, 256f).let { assertEquals(256f, it[0], 1e-3f); assertEquals(256f, it[1], 1e-3f) }
        rest.map(0f, 0f).let { assertEquals(256f - 256f * 0.88f, it[0], 1e-3f) }
        // Bounce at t = 0.35 lifts the bottom-centre pivot by 7 % of the canvas, then base-scales about the centre.
        val lifted = MotionMath.transform(bounce, 0.35f).map(256f, 512f)
        assertEquals(256f + 0.86f * ((512f - 0.07f * 512f) - 256f), lifted[1], 1e-2f)
        // Bounce bottom-left after scaleX/scaleY
        val bleft = MotionMath.transform(bounce, 0.35f).map(0f, 0f)
        assertEquals(44.65f, bleft[0], 1e-2f)
        assertEquals(-12.60f, bleft[1], 1e-2f)
        // Shake at t=0.2 with dx=-0.03, scaleX=0.96
        val shaken = MotionMath.transform(shake, 0.2f).map(256f, 256f)
        assertEquals(242.176f, shaken[0], 1e-2f)
        assertEquals(256f, shaken[1], 1e-2f)
        // Wiggle at t=0.25: rotate=-5 (eased), pivot at (0.5, 0.8), rotates about it
        val wiggled = MotionMath.transform(wiggle, 0.25f).map(256f, 0f)
        assertEquals(223.87f, wiggled[0], 1e-2f)
        assertEquals(27.00f, wiggled[1], 1e-2f)
    }

    @Test
    fun rotateIsClockwiseOnScreen() {
        val p = Affine.rotate(90f).map(1f, 0f)          // +x rotates to +y (down) with y pointing down
        assertEquals(0f, p[0], 1e-5f); assertEquals(1f, p[1], 1e-5f)
        val values = Affine.rotate(90f).matrixValues()  // android.graphics.Matrix order [scaleX, skewX, transX, skewY, scaleY, transY, …]
        assertEquals(0f, values[0], 1e-5f); assertEquals(-1f, values[1], 1e-5f); assertEquals(1f, values[3], 1e-5f)
    }

    @Test
    fun frameDurationsAddUpAndRespectTheMinimum() {
        val d = MotionMath.frameDurations(heartbeat)
        assertEquals(12, d.size)
        assertEquals(1000, d.sum())
        assertTrue(d.all { it >= MotionMath.MIN_FRAME_MS })
        assertEquals(List(16) { 125 }, MotionMath.frameDurations(hearts.copy(durationMs = 2000)))
    }

    @Test
    fun heartsRiseFadeAndLoopSeamlessly() {
        val at0 = MotionMath.particles(hearts, 0f)
        assertEquals(6, at0.size)
        assertEquals(0f, at0[0].alpha, 1e-4f)                      // heart 0 is just born
        assertEquals(0.88f * 512f, at0[0].y, 1e-2f)
        val later = MotionMath.particles(hearts, 540f)[0]           // phase 0.3: fully visible, higher up
        assertEquals(1f, later.alpha, 1e-4f)
        assertTrue(later.y < at0[0].y)
        val wrapped = MotionMath.particles(hearts, 1800f)
        at0.zip(wrapped).forEach { (a, b) -> assertEquals(a.y, b.y, 1e-2f); assertEquals(a.alpha, b.alpha, 1e-3f) }
    }

    @Test
    fun sparklesTwinkleWithinTheirLifetime() {
        val s0 = MotionMath.particles(sparkle, 0f)[0]
        assertEquals(0f, s0.scale, 1e-4f)
        val mid = MotionMath.particles(sparkle, 350f)[0]           // u = 0.5
        assertEquals(1f, mid.scale, 1e-4f)
        assertEquals(22.5f, mid.rotation, 1e-3f)
        val gone = MotionMath.particles(sparkle, 900f)[0]          // past its 700 ms life
        assertEquals(0f, gone.alpha, 0f)
    }

    @Test
    fun noneHasNoParticlesAndIdentityTransform() {
        val none = MotionPreset("none", 0, 1, 1f, 0.5f, 0.5f, listOf(k(0f)), null)
        assertEquals(emptyList<ParticleState>(), MotionMath.particles(none, 100f))
        assertEquals(Affine.IDENTITY, MotionMath.transform(none, 0.5f))
    }
}
