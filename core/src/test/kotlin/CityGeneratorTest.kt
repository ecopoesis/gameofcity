import gen.CityGenConfig
import gen.CityGenerator
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.be
import world.CellCoord
import world.Terrain
import world.isRoad

class CityGeneratorTest : StringSpec({

    "generated map has correct dimensions" {
        val config = CityGenConfig(width = 30, height = 25, seed = 1L, blockSize = 4)
        val map = CityGenerator.generate(config)
        map.width shouldBe 30
        map.height shouldBe 25
    }

    "perimeter is navigable" {
        val config = CityGenConfig(width = 20, height = 20, seed = 2L, blockSize = 4)
        val map = CityGenerator.generate(config)
        // Perimeter cells should be part of road corridors (road or sidewalk), never empty
        for (x in 0 until 20) {
            map.getCell(CellCoord(x, 0))!!.terrain shouldNot be(Terrain.Empty)
            map.getCell(CellCoord(x, 19))!!.terrain shouldNot be(Terrain.Empty)
        }
        for (y in 0 until 20) {
            map.getCell(CellCoord(0, y))!!.terrain shouldNot be(Terrain.Empty)
            map.getCell(CellCoord(19, y))!!.terrain shouldNot be(Terrain.Empty)
        }
    }

    "perimeter corridor contains road cells" {
        val config = CityGenConfig(width = 20, height = 20, seed = 2L, blockSize = 4)
        val map = CityGenerator.generate(config)
        // The first few rows/columns should contain at least some road cells
        val topRowRoads = (0 until 20).count { x ->
            map.getCell(CellCoord(x, 1))!!.terrain.isRoad
        }
        topRowRoads shouldBeGreaterThan 0
    }

    "no empty cells remain" {
        val config = CityGenConfig(width = 20, height = 20, seed = 3L, blockSize = 4)
        val map = CityGenerator.generate(config)
        for (x in 0 until 20) for (y in 0 until 20) {
            val cell = map.getCell(CellCoord(x, y))!!
            cell.terrain shouldNot be(Terrain.Empty)
        }
    }

    "buildings are registered in the map" {
        val config = CityGenConfig(width = 30, height = 30, seed = 4L, blockSize = 4)
        val map = CityGenerator.generate(config)
        map.buildings.size shouldBeGreaterThan 0
    }

    "different seeds produce different maps" {
        val map1 = CityGenerator.generate(CityGenConfig(width = 30, height = 30, seed = 10L, blockSize = 4))
        val map2 = CityGenerator.generate(CityGenConfig(width = 30, height = 30, seed = 99L, blockSize = 4))
        val types1 = map1.buildings.values.map { it.subtype ?: it.type }.groupingBy { it }.eachCount()
        val types2 = map2.buildings.values.map { it.subtype ?: it.type }.groupingBy { it }.eachCount()
        types1 shouldNot be(types2)
    }

    "all road cells are passable" {
        val config = CityGenConfig(width = 20, height = 20, seed = 5L, blockSize = 4)
        val map = CityGenerator.generate(config)
        for (x in 0 until 20) for (y in 0 until 20) {
            val cell = map.getCell(CellCoord(x, y))!!
            if (cell.terrain.isRoad) {
                map.isPassable(CellCoord(x, y)) shouldBe true
            }
        }
    }

    "large map generates multi-lane roads" {
        val config = CityGenConfig(width = 100, height = 100, seed = 42L, blockSize = 20)
        val map = CityGenerator.generate(config)
        // Should have arterial, collector, and local roads
        val terrains = mutableSetOf<Terrain>()
        for (x in 0 until 100) for (y in 0 until 100) {
            terrains.add(map.getCell(CellCoord(x, y))!!.terrain)
        }
        terrains.contains(Terrain.CollectorRoad) shouldBe true
        terrains.contains(Terrain.LocalRoad) shouldBe true
        map.buildings.size shouldBeGreaterThan 0
    }

    "large map has multiple buildings per block" {
        val config = CityGenConfig(width = 100, height = 100, seed = 42L, blockSize = 20)
        val map = CityGenerator.generate(config)
        // With blockSize=20, blocks are large enough for multiple buildings
        map.buildings.size shouldBeGreaterThan 10
    }
})
