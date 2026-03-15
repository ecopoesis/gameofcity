import gen.SimplexNoise
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeBetween
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class SimplexNoiseTest : StringSpec({

    "same seed produces same output" {
        val a = SimplexNoise(42L)
        val b = SimplexNoise(42L)
        for (x in 0..10) for (y in 0..10) {
            a.noise2D(x.toDouble(), y.toDouble()) shouldBe b.noise2D(x.toDouble(), y.toDouble())
        }
    }

    "output is in [-1, 1] range" {
        val noise = SimplexNoise(123L)
        for (x in -50..50) for (y in -50..50) {
            val v = noise.noise2D(x * 0.1, y * 0.1)
            v.shouldBeBetween(-1.0, 1.0, 0.0)
        }
    }

    "different seeds produce different output" {
        val a = SimplexNoise(1L)
        val b = SimplexNoise(999L)
        val diff = (0..20).count { x ->
            a.noise2D(x.toDouble(), 0.0) != b.noise2D(x.toDouble(), 0.0)
        }
        diff shouldNotBe 0
    }

    "octave noise is in [-1, 1] range" {
        val noise = SimplexNoise(456L)
        for (x in -20..20) for (y in -20..20) {
            val v = noise.octaveNoise(x * 0.05, y * 0.05, 4)
            v.shouldBeBetween(-1.0, 1.0, 0.0)
        }
    }
})
