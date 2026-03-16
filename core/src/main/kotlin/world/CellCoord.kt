package world

data class CellCoord(val x: Int, val y: Int, val z: Int = 0) {
    fun distanceTo(other: CellCoord): Int =
        kotlin.math.abs(x - other.x) + kotlin.math.abs(y - other.y)
}
