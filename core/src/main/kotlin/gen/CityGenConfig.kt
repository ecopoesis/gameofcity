package gen

import kotlin.random.Random

data class CityGenConfig(
    val width: Int = 200,
    val height: Int = 200,
    val seed: Long = Random.nextLong(),
    val populationDensity: Float = 0.5f,
    val urbanizationLevel: Float = 0.5f,
    val blockSize: Int = 20,
    val parkChance: Float = 0.15f,
    val peepCount: Int = 100,
    val organicLevel: Float = 0.0f
)
