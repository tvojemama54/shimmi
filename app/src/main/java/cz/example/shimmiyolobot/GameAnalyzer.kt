package cz.example.shimmiyolobot

import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Prototype image-only player & pipe detector. Needs calibration for SHIMMI theme changes.
 * No network, OCR, game file access, injection or leaderboard modification.
 */
class GameAnalyzer {
    data class Decision(val tap: Boolean, val playerY: Float?, val targetY: Float?, val confidence: Float)
    private var smoothPlayer = -1f
    private var smoothTarget = .49f
    private var oldPlayer = -1f
    private var oldTime = 0L
    private var lastTapDecision = 0L

    fun analyze(buf: ByteBuffer, w: Int, h: Int, rowStride: Int, ps: Int, now: Long): Decision {
        if (w < 100 || h < 200 || ps < 3) return Decision(false, null, null, 0f)
        fun rgb(x: Int, y: Int): Int {
            if (x !in 0 until w || y !in 0 until h) return 0
            val i = y * rowStride + x * ps
            if (i < 0 || i + 2 >= buf.limit()) return 0
            return ((buf.get(i).toInt() and 255) shl 16) or
                ((buf.get(i + 1).toInt() and 255) shl 8) or
                (buf.get(i + 2).toInt() and 255)
        }
        fun luma(x: Int, y: Int): Int {
            val c = rgb(x, y)
            return (((c shr 16) and 255) * 77 + ((c shr 8) and 255) * 150 + (c and 255) * 29) / 256
        }

        // Detect the bright green moving sprite, ignoring the left HUD.
        val bins = IntArray(45)
        val xStep = max(3, w / 180)
        val yStep = max(4, h / 260)
        var y = (h * .15f).toInt()
        while (y < (h * .86f).toInt()) {
            var x = (w * .21f).toInt()
            while (x < (w * .39f).toInt()) {
                val c = rgb(x, y)
                val r = (c shr 16) and 255
                val g = (c shr 8) and 255
                val b = c and 255
                if (g > 90 && g > r * 1.38 && g > b * 1.22) {
                    val bin = ((y.toFloat() / h - .15f) / .71f * bins.size).toInt().coerceIn(0, bins.lastIndex)
                    bins[bin]++
                }
                x += xStep
            }
            y += yStep
        }
        var peak = -1
        var score = 0
        for (i in 1 until bins.lastIndex) {
            val count = bins[i - 1] + bins[i] + bins[i + 1]
            val loc = .15f + (.71f * (i + .5f) / bins.size)
            val proximity = if (smoothPlayer > 0) (1f - abs(loc - smoothPlayer) * 1.5f).coerceIn(.20f, 1f) else 1f
            val weighted = (count * proximity).toInt()
            if (weighted > score) { score = weighted; peak = i }
        }
        val py = if (peak >= 0 && score >= 5) .15f + .71f * (peak + .5f) / bins.size else -1f
        if (py > 0) smoothPlayer = if (smoothPlayer < 0) py else smoothPlayer * .50f + py * .50f

        // Leopard-print pipes have far more short-range local contrast than the backdrop.
        // Sample the nearest strong vertical textured column to the RIGHT of the player.
        var bestGap = -1f
        var bestStrength = 0f
        var bestX = 2f
        val ys = 58
        for (q in 0..13) {
            val fx = .385f + q * .032f
            val xx = (w * fx).toInt()
            val masked = BooleanArray(ys)
            var count = 0
            for (i in 0 until ys) {
                val yy = (h * (.12 + (.76 * i / ys))).toInt()
                var n = 0
                for (dx in -1..1) {
                    val px = xx + dx * max(4, w / 130)
                    val a = luma(px - max(4, w / 160), yy)
                    val b = luma(px + max(4, w / 160), yy)
                    val c = luma(px, yy - max(5, h / 270))
                    val d = luma(px, yy + max(5, h / 270))
                    if (max(max(a, b), max(c, d)) - min(min(a, b), min(c, d)) > 73) n++
                }
                masked[i] = n >= 2
                if (masked[i]) count++
            }
            // Largest contiguous untextured gap between textured pipe sections.
            var run = 0
            var start = 0
            var longest = 0
            var longestStart = 0
            for (i in 0..ys) {
                val empty = i < ys && !masked[i]
                if (empty) {
                    if (run == 0) start = i
                    run++
                } else {
                    if (run > longest) { longest = run; longestStart = start }
                    run = 0
                }
            }
            val above = masked.take(longestStart).count { it }
            val below = masked.drop(longestStart + longest).count { it }
            val texture = count.toFloat() / ys
            val valid = longest >= 9 && above >= 4 && below >= 4 && texture > .18f
            if (valid) {
                val center = .12f + .76f * (longestStart + longest * .5f) / ys
                // Choose the first sufficiently recognizable obstacle approaching player.
                if (fx < bestX || (abs(fx - bestX) < .01 && texture > bestStrength)) {
                    bestX = fx
                    bestGap = center
                    bestStrength = texture
                }
            }
        }
        if (bestGap > 0f) smoothTarget = smoothTarget * .62f + bestGap * .38f
        else smoothTarget = smoothTarget * .985f + .49f * .015f

        if (smoothPlayer <= 0) return Decision(false, null, smoothTarget, 0f)
        val elapsed = max(1L, now - oldTime)
        val vy = if (oldPlayer > 0 && oldTime > 0) (smoothPlayer - oldPlayer) / elapsed * 1000f else 0f
        oldPlayer = smoothPlayer
        oldTime = now

        // Predict 120ms ahead: a falling character needs an earlier tap.
        val forecast = smoothPlayer + vy.coerceIn(-.7f, .7f) * .12f
        val center = smoothTarget.coerceIn(.23f, .73f)
        val shouldTap = forecast > center + .028f && now - lastTapDecision > 120L
        if (shouldTap) lastTapDecision = now
        return Decision(shouldTap, smoothPlayer, center, if (bestGap > 0f) bestStrength else .12f)
    }
}
