package tick

enum class EventType {
    Eviction, Partnership, Breakup, Birth, Death,
    JobChange, Immigration, Emigration, Crowding, WageChange
}

data class SimEvent(
    val tick: Long,
    val day: Int,
    val type: EventType,
    val description: String,
    val involvedPeeps: List<Int> = emptyList()
)

class EventLog(private val maxSize: Int = 500) {
    private val buffer = ArrayDeque<SimEvent>(maxSize)

    fun add(event: SimEvent) {
        if (buffer.size >= maxSize) buffer.removeFirst()
        buffer.addLast(event)
    }

    fun recent(count: Int = 50): List<SimEvent> =
        buffer.takeLast(count)

    fun recentOfType(type: EventType, count: Int = 50): List<SimEvent> =
        buffer.filter { it.type == type }.takeLast(count)

    val size: Int get() = buffer.size

    fun clear() = buffer.clear()
}
