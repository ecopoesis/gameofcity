import gen.CityGenConfig
import gen.CityGenerator
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.be
import world.CellCoord
import world.Terrain

class CityGeneratorTest : StringSpec({

    "generated map has correct dimensions" {
        val config = CityGenConfig(width = 30, height = 25, seed = 1L)
        val map = CityGenerator.generate(config)
        map.width shouldBe 30
        map.height shouldBe 25
    }

    "perimeter is all road" {
        val config = CityGenConfig(width = 20, height = 20, seed = 2L)
        val map = CityGenerator.generate(config)
        for (x in 0 until 20) {
            map.getCell(CellCoord(x, 0))!!.terrain shouldBe Terrain.Road
            map.getCell(CellCoord(x, 19))!!.terrain shouldBe Terrain.Road
        }
        for (y in 0 until 20) {
            map.getCell(CellCoord(0, y))!!.terrain shouldBe Terrain.Road
            map.getCell(CellCoord(19, y))!!.terrain shouldBe Terrain.Road
        }
    }

    "no empty cells remain" {
        val config = CityGenConfig(width = 20, height = 20, seed = 3L)
        val map = CityGenerator.generate(config)
        for (x in 0 until 20) for (y in 0 until 20) {
            val cell = map.getCell(CellCoord(x, y))!!
            cell.terrain shouldNot be(Terrain.Empty)
        }
    }

    "buildings are registered in the map" {
        val config = CityGenConfig(width = 30, height = 30, seed = 4L)
        val map = CityGenerator.generate(config)
        map.buildings.size shouldBeGreaterThan 0
    }

    "different seeds produce different maps" {
        val map1 = CityGenerator.generate(CityGenConfig(width = 30, height = 30, seed = 10L))
        val map2 = CityGenerator.generate(CityGenConfig(width = 30, height = 30, seed = 99L))
        // Buildings should differ in type or subtype distribution
        val types1 = map1.buildings.values.map { it.subtype ?: it.type }.groupingBy { it }.eachCount()
        val types2 = map2.buildings.values.map { it.subtype ?: it.type }.groupingBy { it }.eachCount()
        types1 shouldNot be(types2)
    }

    "all road cells are passable" {
        val config = CityGenConfig(width = 20, height = 20, seed = 5L)
        val map = CityGenerator.generate(config)
        for (x in 0 until 20) for (y in 0 until 20) {
            val cell = map.getCell(CellCoord(x, y))!!
            if (cell.terrain == Terrain.Road) {
                map.isPassable(CellCoord(x, y)) shouldBe true
            }
        }
    }
})
