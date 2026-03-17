package pathfind

/**
 * Min-heap priority queue. Pure Kotlin, no java.* imports.
 * Elements with the lowest key are dequeued first.
 */
class BinaryHeap<T>(private val keyOf: (T) -> Float) {

    private val data = mutableListOf<T>()

    val size: Int get() = data.size

    fun isEmpty(): Boolean = data.isEmpty()

    fun isNotEmpty(): Boolean = data.isNotEmpty()

    fun add(element: T) {
        data.add(element)
        siftUp(data.lastIndex)
    }

    /** Remove and return the element with the smallest key. */
    fun poll(): T {
        val top = data[0]
        val last = data.removeAt(data.lastIndex)
        if (data.isNotEmpty()) {
            data[0] = last
            siftDown(0)
        }
        return top
    }

    fun peek(): T = data[0]

    private fun siftUp(startIndex: Int) {
        var i = startIndex
        while (i > 0) {
            val parent = (i - 1) / 2
            if (keyOf(data[i]) < keyOf(data[parent])) {
                swap(i, parent)
                i = parent
            } else break
        }
    }

    private fun siftDown(startIndex: Int) {
        var i = startIndex
        while (true) {
            val left = 2 * i + 1
            val right = 2 * i + 2
            var smallest = i

            if (left < data.size && keyOf(data[left]) < keyOf(data[smallest])) {
                smallest = left
            }
            if (right < data.size && keyOf(data[right]) < keyOf(data[smallest])) {
                smallest = right
            }
            if (smallest != i) {
                swap(i, smallest)
                i = smallest
            } else break
        }
    }

    private fun swap(a: Int, b: Int) {
        val tmp = data[a]
        data[a] = data[b]
        data[b] = tmp
    }
}
