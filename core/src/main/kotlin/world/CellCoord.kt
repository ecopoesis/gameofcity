package world

/** Each cell represents a 10ft × 10ft square (≈3m). */
const val CELL_FEET = 10

data class CellCoord(val x: Int, val y: Int, val z: Int = 0) {
    fun distanceTo(other: CellCoord): Int =
        kotlin.math.abs(x - other.x) + kotlin.math.abs(y - other.y)
}
