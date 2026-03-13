import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.utils.ScreenUtils
import gen.PeepSpawner
import rendering.CityRenderer
import tick.TickEngine
import ui.GameSkin
import ui.HUD
import ui.InspectorPanel
import world.CellCoord
import world.MapLoader

class GameOfCityApp : ApplicationAdapter() {

    private lateinit var engine: TickEngine
    private lateinit var renderer: CityRenderer
    private lateinit var hud: HUD
    private lateinit var inspector: InspectorPanel

    private var cameraX = 0f
    private var cameraY = 0f
    private val cameraSpeed = 200f

    private var tickAccum = 0f
    private val tickDuration = 0.05f  // 20 ticks/second

    private var paused = false
    private var verbose = false
    private val logInterval = 100L

    override fun create() {
        val map = MapLoader.loadFromJson("maps/starter.json")
        engine   = TickEngine(map)
        PeepSpawner.spawn(engine, 50)

        val skin  = GameSkin.create()
        hud       = HUD(engine, skin)
        inspector = InspectorPanel(engine, skin)
        renderer  = CityRenderer(engine)

        Gdx.input.inputProcessor = InputMultiplexer(
            inspector.stage,
            hud.stage,
            object : InputAdapter() {
                override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                    handleWorldClick(screenX, screenY)
                    return false
                }
            }
        )
    }

    private fun handleWorldClick(screenX: Int, screenY: Int) {
        // Convert screen coords → world cell (camera bottom-left is at cameraX, cameraY)
        val cellX = ((cameraX + screenX) / CityRenderer.CELL_SIZE).toInt()
        val cellY = ((cameraY + (Gdx.graphics.height - screenY)) / CityRenderer.CELL_SIZE).toInt()
        val coord = CellCoord(cellX, cellY)

        // Peep takes priority over building
        val peep = engine.peeps.values.firstOrNull { it.position == coord }
        if (peep != null) {
            inspector.selectPeep(peep.id)
            renderer.selectedCoord = coord
            return
        }

        val bldgId = engine.map.getCell(coord)?.buildingId
        if (bldgId != null) {
            inspector.selectBuilding(bldgId)
            renderer.selectedCoord = coord
            return
        }

        inspector.clear()
        renderer.selectedCoord = null
    }

    override fun render() {
        val delta = Gdx.graphics.deltaTime

        // Camera pan
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT))  cameraX -= cameraSpeed * delta
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) cameraX += cameraSpeed * delta
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN))  cameraY -= cameraSpeed * delta
        if (Gdx.input.isKeyPressed(Input.Keys.UP))    cameraY += cameraSpeed * delta

        // Space to pause/resume
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) paused = !paused

        // V to toggle verbose logging
        if (Gdx.input.isKeyJustPressed(Input.Keys.V)) {
            verbose = !verbose
            println("[SimLogger] Verbose mode ${if (verbose) "ON" else "OFF"}")
        }

        // Simulation ticks
        if (!paused) {
            tickAccum += delta
            while (tickAccum >= tickDuration) {
                val prevTick = engine.tick
                engine.step()
                if (verbose && prevTick / logInterval < engine.tick / logInterval) {
                    SimLogger.log(engine)
                }
                tickAccum -= tickDuration
            }
        }

        hud.update(paused)
        inspector.update()

        ScreenUtils.clear(0.1f, 0.1f, 0.15f, 1f)
        renderer.render(cameraX, cameraY)
        hud.render()
        inspector.render()
    }

    override fun resize(width: Int, height: Int) {
        hud.resize(width, height)
        inspector.resize(width, height)
    }

    override fun dispose() {
        renderer.dispose()
        hud.dispose()
        inspector.dispose()
    }
}
