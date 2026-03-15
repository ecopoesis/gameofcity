package gen

import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 2D OpenSimplex-style noise generator. Pure Kotlin, no java.* imports.
 * Based on the public-domain OpenSimplex2 algorithm.
 */
class SimplexNoise(seed: Long) {

    private val perm = IntArray(256)

    init {
        val rng = Random(seed)
        for (i in perm.indices) perm[i] = i
        for (i in perm.indices.reversed()) {
            val j = rng.nextInt(i + 1)
            val tmp = perm[i]; perm[i] = perm[j]; perm[j] = tmp
        }
    }

    /** Returns noise value in [-1, 1] for the given 2D coordinate. */
    fun noise2D(x: Double, y: Double): Double {
        val s = (x + y) * F2
        val i = floor(x + s).toInt()
        val j = floor(y + s).toInt()
        val t = (i + j) * G2

        val x0 = x - (i - t)
        val y0 = y - (j - t)

        val i1: Int; val j1: Int
        if (x0 > y0) { i1 = 1; j1 = 0 } else { i1 = 0; j1 = 1 }

        val x1 = x0 - i1 + G2
        val y1 = y0 - j1 + G2
        val x2 = x0 - 1.0 + 2.0 * G2
        val y2 = y0 - 1.0 + 2.0 * G2

        val ii = i and 255
        val jj = j and 255

        var n = 0.0

        var t0 = 0.5 - x0 * x0 - y0 * y0
        if (t0 >= 0) {
            t0 *= t0
            n += t0 * t0 * grad(perm[(ii + perm[jj % 256]) % 256], x0, y0)
        }

        var t1 = 0.5 - x1 * x1 - y1 * y1
        if (t1 >= 0) {
            t1 *= t1
            n += t1 * t1 * grad(perm[(ii + i1 + perm[(jj + j1) % 256]) % 256], x1, y1)
        }

        var t2 = 0.5 - x2 * x2 - y2 * y2
        if (t2 >= 0) {
            t2 *= t2
            n += t2 * t2 * grad(perm[(ii + 1 + perm[(jj + 1) % 256]) % 256], x2, y2)
        }

        return 70.0 * n
    }

    /** Fractal noise: multiple octaves summed with decreasing amplitude. */
    fun octaveNoise(x: Double, y: Double, octaves: Int, persistence: Double = 0.5): Double {
        var total = 0.0
        var frequency = 1.0
        var amplitude = 1.0
        var maxValue = 0.0
        repeat(octaves) {
            total += noise2D(x * frequency, y * frequency) * amplitude
            maxValue += amplitude
            amplitude *= persistence
            frequency *= 2.0
        }
        return total / maxValue
    }

    private fun grad(hash: Int, x: Double, y: Double): Double {
        return when (hash % 4) {
            0 ->  x + y
            1 -> -x + y
            2 ->  x - y
            else -> -x - y
        }
    }

    companion object {
        private val F2 = 0.5 * (sqrt(3.0) - 1.0)
        private val G2 = (3.0 - sqrt(3.0)) / 6.0
    }
}
