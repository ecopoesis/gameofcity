package gen

import kotlin.random.Random

data class CityGenConfig(
    val width: Int = 40,
    val height: Int = 40,
    val seed: Long = Random.nextLong(),
    val populationDensity: Float = 0.5f,
    val urbanizationLevel: Float = 0.5f,
    val blockSize: Int = 4,
    val parkChance: Float = 0.15f,
    val peepCount: Int = 50,
    val organicLevel: Float = 0.0f
)
