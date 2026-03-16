import gen.CityGenConfig
import gen.CityGenerator
import gen.PeepSpawner
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.floats.shouldBeBetween
import io.kotest.matchers.shouldBe
import peep.RandomBrain
import peep.UtilityBrain
import save.GameSerializer
import tick.TickEngine
import world.CellCoord

class SaveLoadTest : StringSpec({

    "round-trip preserves engine state" {
        val map = CityGenerator.generate(CityGenConfig(width = 20, height = 20, seed = 42L))
        val engine = TickEngine(map)
        PeepSpawner.spawn(engine, 10)

        // Advance simulation
        repeat(100) { engine.step() }

        val json = GameSerializer.serialize(engine)
        val restored = GameSerializer.deserialize(json)

        restored.tick shouldBe engine.tick
        restored.map.width shouldBe engine.map.width
        restored.map.height shouldBe engine.map.height
        restored.peeps.size shouldBe engine.peeps.size
        restored.map.buildings.size shouldBe engine.map.buildings.size
    }

    "round-trip preserves peep data" {
        val map = CityGenerator.generate(CityGenConfig(width = 20, height = 20, seed = 43L))
        val engine = TickEngine(map)
        PeepSpawner.spawn(engine, 5)
        repeat(50) { engine.step() }

        val json = GameSerializer.serialize(engine)
        val restored = GameSerializer.deserialize(json)

        for ((id, peep) in engine.peeps) {
            val rp = restored.peeps[id]!!
            rp.name shouldBe peep.name
            rp.age shouldBe peep.age
            rp.gender shouldBe peep.gender
            rp.position shouldBe peep.position
            rp.homeId shouldBe peep.homeId
            rp.jobId shouldBe peep.jobId
            rp.money.shouldBeBetween(peep.money - 0.01f, peep.money + 0.01f, 0f)
            rp.needs.hunger.shouldBeBetween(peep.needs.hunger - 0.001f, peep.needs.hunger + 0.001f, 0f)
            rp.needs.sleep.shouldBeBetween(peep.needs.sleep - 0.001f, peep.needs.sleep + 0.001f, 0f)
        }
    }

    "brain type is preserved" {
        val map = CityGenerator.generate(CityGenConfig(width = 20, height = 20, seed = 44L))
        val engine = TickEngine(map)
        PeepSpawner.spawn(engine, 3)
        engine.peeps[1]?.brain = RandomBrain()

        val json = GameSerializer.serialize(engine)
        val restored = GameSerializer.deserialize(json)

        (restored.peeps[0]?.brain is UtilityBrain) shouldBe true
        (restored.peeps[1]?.brain is RandomBrain) shouldBe true
    }

    "cell terrain is preserved" {
        val map = CityGenerator.generate(CityGenConfig(width = 15, height = 15, seed = 45L))
        val engine = TickEngine(map)

        val json = GameSerializer.serialize(engine)
        val restored = GameSerializer.deserialize(json)

        for (x in 0 until 15) for (y in 0 until 15) {
            val orig = engine.map.getCell(CellCoord(x, y))!!
            val rest = restored.map.getCell(CellCoord(x, y))!!
            rest.terrain shouldBe orig.terrain
        }
    }
})
