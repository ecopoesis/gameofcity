package peep

enum class MaslowLevel(val index: Int) {
    Physiological(1),
    Safety(2),
    LoveBelonging(3),
    Esteem(4),
    SelfActualization(5)
}

enum class NeedType(val level: MaslowLevel, val derived: Boolean = false) {
    Hunger(MaslowLevel.Physiological),
    Thirst(MaslowLevel.Physiological),
    Sleep(MaslowLevel.Physiological),
    Warmth(MaslowLevel.Physiological),

    Shelter(MaslowLevel.Safety),
    Health(MaslowLevel.Safety),
    Financial(MaslowLevel.Safety, derived = true),

    Friendship(MaslowLevel.LoveBelonging),
    Family(MaslowLevel.LoveBelonging),
    Community(MaslowLevel.LoveBelonging),

    Recognition(MaslowLevel.Esteem),
    Accomplishment(MaslowLevel.Esteem),
    Status(MaslowLevel.Esteem, derived = true),

    Creativity(MaslowLevel.SelfActualization),
    Learning(MaslowLevel.SelfActualization),
    Purpose(MaslowLevel.SelfActualization)
}

data class MaslowNeeds(
    // Level 1 - Physiological
    var hunger: Float = 0f,
    var thirst: Float = 0f,
    var sleep: Float = 0f,
    var warmth: Float = 0f,
    // Level 2 - Safety
    var shelter: Float = 0f,
    var health: Float = 0f,
    // Level 3 - Love/Belonging
    var friendship: Float = 0f,
    var family: Float = 0f,
    var community: Float = 0f,
    // Level 4 - Esteem
    var recognition: Float = 0f,
    var accomplishment: Float = 0f,
    // Level 5 - Self-Actualization
    var creativity: Float = 0f,
    var learning: Float = 0f,
    var purpose: Float = 0f
) {
    // Derived needs (computed in Maintain phase, not stored)
    var financial: Float = 0f
    var status: Float = 0f

    fun get(type: NeedType): Float = when (type) {
        NeedType.Hunger -> hunger
        NeedType.Thirst -> thirst
        NeedType.Sleep -> sleep
        NeedType.Warmth -> warmth
        NeedType.Shelter -> shelter
        NeedType.Health -> health
        NeedType.Financial -> financial
        NeedType.Friendship -> friendship
        NeedType.Family -> family
        NeedType.Community -> community
        NeedType.Recognition -> recognition
        NeedType.Accomplishment -> accomplishment
        NeedType.Status -> status
        NeedType.Creativity -> creativity
        NeedType.Learning -> learning
        NeedType.Purpose -> purpose
    }

    fun set(type: NeedType, value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        when (type) {
            NeedType.Hunger -> hunger = clamped
            NeedType.Thirst -> thirst = clamped
            NeedType.Sleep -> sleep = clamped
            NeedType.Warmth -> warmth = clamped
            NeedType.Shelter -> shelter = clamped
            NeedType.Health -> health = clamped
            NeedType.Financial -> financial = clamped
            NeedType.Friendship -> friendship = clamped
            NeedType.Family -> family = clamped
            NeedType.Community -> community = clamped
            NeedType.Recognition -> recognition = clamped
            NeedType.Accomplishment -> accomplishment = clamped
            NeedType.Status -> status = clamped
            NeedType.Creativity -> creativity = clamped
            NeedType.Learning -> learning = clamped
            NeedType.Purpose -> purpose = clamped
        }
    }

    fun adjust(type: NeedType, delta: Float) {
        set(type, get(type) + delta)
    }

    fun allAtLevel(level: MaslowLevel): List<Pair<NeedType, Float>> =
        NeedType.entries.filter { it.level == level }.map { it to get(it) }

    fun maxAtLevel(level: MaslowLevel): Float =
        allAtLevel(level).maxOfOrNull { it.second } ?: 0f

    fun allBelow(threshold: Float, level: MaslowLevel): Boolean =
        allAtLevel(level).all { it.second < threshold }

    fun topNeed(): Pair<NeedType, Float>? =
        NeedType.entries.map { it to get(it) }.maxByOrNull { it.second }
}
