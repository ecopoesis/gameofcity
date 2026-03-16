package world

enum class WeatherState {
    Clear, Rain, Snow, Heatwave;

    val movementMultiplier: Float get() = when (this) {
        Clear -> 1f
        Rain -> 0.7f
        Snow -> 0.5f
        Heatwave -> 0.9f
    }

    val warmthDecayMultiplier: Float get() = when (this) {
        Clear -> 1f
        Rain -> 2f
        Snow -> 3f
        Heatwave -> 1f
    }

    val thirstDecayMultiplier: Float get() = when (this) {
        Heatwave -> 2f
        else -> 1f
    }

    val prefersIndoors: Boolean get() = this == Rain || this == Snow
}

class Weather {
    var current: WeatherState = WeatherState.Clear; private set
    private var ticksUntilChange: Int = 180  // 2-4 sim-hours (120-240 ticks)

    // Markov transition probabilities (simplified)
    private val transitions = mapOf(
        WeatherState.Clear to floatArrayOf(0.6f, 0.2f, 0.1f, 0.1f),
        WeatherState.Rain to floatArrayOf(0.4f, 0.4f, 0.15f, 0.05f),
        WeatherState.Snow to floatArrayOf(0.3f, 0.2f, 0.45f, 0.05f),
        WeatherState.Heatwave to floatArrayOf(0.5f, 0.1f, 0.0f, 0.4f)
    )

    fun advance() {
        ticksUntilChange--
        if (ticksUntilChange <= 0) {
            current = nextState()
            ticksUntilChange = 120 + (Math.random() * 120).toInt()
        }
    }

    private fun nextState(): WeatherState {
        val probs = transitions[current] ?: return WeatherState.Clear
        val r = Math.random().toFloat()
        var cumulative = 0f
        for (i in probs.indices) {
            cumulative += probs[i]
            if (r <= cumulative) return WeatherState.entries[i]
        }
        return WeatherState.Clear
    }

    fun restore(state: String) {
        current = try { WeatherState.valueOf(state) } catch (_: Exception) { WeatherState.Clear }
    }
}
