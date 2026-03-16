package peep

enum class ScheduleType {
    Worker,
    Student,
    Retiree,
    Nightshift
}

data class ScheduleTemplate(
    val workStart: Int,
    val workEnd: Int,
    val sleepStart: Int,
    val sleepEnd: Int,
    val mealHours: List<Int>
) {
    fun isWorkTime(hour: Int): Boolean {
        return if (workStart < workEnd) {
            hour in workStart until workEnd
        } else {
            // Wraps midnight (e.g. nightshift 20-5)
            hour >= workStart || hour < workEnd
        }
    }

    fun isSleepTime(hour: Int): Boolean {
        return if (sleepStart < sleepEnd) {
            hour in sleepStart until sleepEnd
        } else {
            hour >= sleepStart || hour < sleepEnd
        }
    }

    fun isMealTime(hour: Int): Boolean = hour in mealHours

    companion object {
        val WORKER = ScheduleTemplate(
            workStart = 8, workEnd = 17,
            sleepStart = 22, sleepEnd = 6,
            mealHours = listOf(7, 12, 18)
        )
        val STUDENT = ScheduleTemplate(
            workStart = 8, workEnd = 15,
            sleepStart = 22, sleepEnd = 7,
            mealHours = listOf(7, 12, 18)
        )
        val RETIREE = ScheduleTemplate(
            workStart = -1, workEnd = -1,  // no work
            sleepStart = 21, sleepEnd = 7,
            mealHours = listOf(8, 12, 17)
        )
        val NIGHTSHIFT = ScheduleTemplate(
            workStart = 20, workEnd = 5,
            sleepStart = 8, sleepEnd = 16,
            mealHours = listOf(19, 1, 7)
        )

        fun forType(type: ScheduleType): ScheduleTemplate = when (type) {
            ScheduleType.Worker -> WORKER
            ScheduleType.Student -> STUDENT
            ScheduleType.Retiree -> RETIREE
            ScheduleType.Nightshift -> NIGHTSHIFT
        }
    }
}
