package tick

class SimClock(val ticksPerHour: Int = 60) {

    var totalTicks: Long = 0L
        private set
    var day: Int = 1
        private set
    var hour: Int = 6  // start at 6 AM
        private set
    var minute: Int = 0
        private set

    val ticksPerDay: Int get() = ticksPerHour * 24

    val isNight: Boolean get() = hour < 6 || hour >= 22
    val isDawn: Boolean get() = hour in 6..7
    val isDusk: Boolean get() = hour in 20..21
    val isDay: Boolean get() = hour in 8..19

    fun advance() {
        totalTicks++
        val totalMinutesPerTick = 60 / ticksPerHour
        minute += totalMinutesPerTick
        if (minute >= 60) {
            hour += minute / 60
            minute %= 60
            if (hour >= 24) {
                day += hour / 24
                hour %= 24
            }
        }
    }

    fun timeString(): String = "Day $day  %02d:%02d".format(hour, minute)

    fun isNewDay(): Boolean {
        return totalTicks > 0 && hour == 0 && minute == 0
    }

    fun restore(tick: Long, d: Int, h: Int, m: Int) {
        totalTicks = tick
        day = d
        hour = h
        minute = m
    }
}
