import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.graphics.PerspectiveCamera
import gen.CityGenConfig
import gen.CityGenerator
import gen.PeepSpawner
import peep.*
import rendering.CityRenderer
import rendering.OrbitController
import save.DesktopSaveManager
import save.SaveConverter
import tick.TickEngine
import ui.GameSkin
import ui.HUD
import ui.InspectorPanel
import world.MapLoader

class GameOfCityApp : ApplicationAdapter() {

    private lateinit var engine: TickEngine
    private lateinit var orbitController: OrbitController
    private lateinit var renderer: CityRenderer
    private lateinit var hud: HUD
    private lateinit var inspector: InspectorPanel
    private lateinit var skin: com.badlogic.gdx.scenes.scene2d.ui.Skin

    private var tickAccum    = 0f
    private var tickDuration = 0.05f   // 20 ticks/second (default 1x)
    private var paused       = false
    private var verbose      = false
    private val logInterval  = 100L

    private var currentBrainType = "Utility"
    private val brainTypes = listOf("Utility", "Pyramid", "Wave", "Random", "Idle")

    override fun create() {
        val camera = PerspectiveCamera(67f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        camera.near = 10f
        camera.far  = 5000f

        orbitController = OrbitController(camera)
        skin = GameSkin.create()

        // Start with the hand-authored starter map
        val map = MapLoader.loadFromJson("maps/starter.json")
        engine = TickEngine(map)
        PeepSpawner.spawn(engine, 50)
        initForEngine(camera)

        val worldInput = object : InputAdapter() {
            override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                if (!orbitController.dragged) handleWorldClick(screenX, screenY)
                return false
            }
        }
        Gdx.input.inputProcessor =
            InputMultiplexer(inspector.stage, hud.stage, orbitController, worldInput)
    }

    private fun initForEngine(camera: PerspectiveCamera) {
        orbitController.target.set(
            engine.map.width * CityRenderer.CS / 2f, 0f,
            engine.map.height * CityRenderer.CS / 2f
        )
        hud       = HUD(engine, skin)
        hud.brainType = currentBrainType
        hud.speedFactor = 0.05f / tickDuration
        hud.onBrainChanged = { name ->
            currentBrainType = name
            engine.peeps.values.forEach { it.brain = SaveConverter.brainFromName(name) }
            println("[Brain] Switched all peeps to $name")
        }
        inspector = InspectorPanel(engine, skin)
        renderer  = CityRenderer(engine, orbitController)
    }

    private fun rebuildForEngine(newEngine: TickEngine) {
        renderer.dispose()
        hud.dispose()
        inspector.dispose()

        engine = newEngine
        initForEngine(orbitController.camera)

        Gdx.input.inputProcessor =
            InputMultiplexer(inspector.stage, hud.stage, orbitController, object : InputAdapter() {
                override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                    if (!orbitController.dragged) handleWorldClick(screenX, screenY)
                    return false
                }
            })

        tickAccum = 0f
    }

    private fun handleWorldClick(screenX: Int, screenY: Int) {
        when (val hit = renderer.pick(screenX, screenY)) {
            is CityRenderer.PickResult.PeepPick -> {
                inspector.selectPeep(hit.id)
                renderer.selectedCoord = engine.peeps[hit.id]?.position
            }
            is CityRenderer.PickResult.BuildingPick -> {
                inspector.selectBuilding(hit.id)
                renderer.selectedCoord = engine.map.buildings[hit.id]?.cells?.firstOrNull()
            }
            null -> {
                inspector.clear()
                renderer.selectedCoord = null
            }
        }
    }

    override fun render() {
        val delta = Gdx.graphics.deltaTime

        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) paused = !paused
        if (Gdx.input.isKeyJustPressed(Input.Keys.V)) {
            verbose = !verbose
            println("[SimLogger] Verbose mode ${if (verbose) "ON" else "OFF"}")
        }

        // B = cycle global brain type
        if (Gdx.input.isKeyJustPressed(Input.Keys.B)) {
            val idx = brainTypes.indexOf(currentBrainType)
            currentBrainType = brainTypes[(idx + 1) % brainTypes.size]
            engine.peeps.values.forEach { it.brain = SaveConverter.brainFromName(currentBrainType) }
            hud.brainType = currentBrainType
            println("[Brain] Switched all peeps to $currentBrainType")
        }

        // +/= = speed up, - = slow down
        if (Gdx.input.isKeyJustPressed(Input.Keys.EQUALS) || Gdx.input.isKeyJustPressed(Input.Keys.PLUS)) {
            tickDuration = (tickDuration - 0.01f).coerceAtLeast(0.01f)
            hud.speedFactor = 0.05f / tickDuration
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.MINUS)) {
            tickDuration = (tickDuration + 0.01f).coerceAtMost(1f)
            hud.speedFactor = 0.05f / tickDuration
        }

        // N = generate new city (uses HUD config), also check HUD button
        if (Gdx.input.isKeyJustPressed(Input.Keys.N) || hud.generateRequested) {
            hud.generateRequested = false
            val config = CityGenConfig(
                width = hud.genWidth,
                height = hud.genHeight,
                peepCount = hud.genPeeps,
                organicLevel = hud.genOrganic
            )
            println("[CityGen] Generating ${config.width}x${config.height} city (seed=${config.seed}, organic=${config.organicLevel})")
            val map = CityGenerator.generate(config)
            val newEngine = TickEngine(map)
            PeepSpawner.spawn(newEngine, config.peepCount)
            println("[CityGen] Spawned ${config.peepCount} peeps in ${map.buildings.size} buildings")
            rebuildForEngine(newEngine)
        }

        // F5 = quick save, F9 = quick load
        if (Gdx.input.isKeyJustPressed(Input.Keys.F5)) {
            DesktopSaveManager.save(engine)
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F9)) {
            DesktopSaveManager.load()?.let { rebuildForEngine(it) }
        }

        if (!paused) {
            tickAccum += delta
            while (tickAccum >= tickDuration) {
                val prev = engine.tick
                engine.step()
                if (verbose && prev / logInterval < engine.tick / logInterval) SimLogger.log(engine)
                tickAccum -= tickDuration
            }
        }

        orbitController.update(delta)
        hud.update(paused)
        inspector.update()

        // Nuclear GL state reset before 3D rendering
        Gdx.gl.glViewport(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
        Gdx.gl.glDepthMask(true)
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL)
        Gdx.gl.glDisable(GL20.GL_BLEND)
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST)
        Gdx.gl.glClearDepthf(1f)
        Gdx.gl.glDepthRangef(0f, 1f)
        Gdx.gl.glClearColor(0.12f, 0.12f, 0.18f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)

        renderer.render()
        hud.render()
        inspector.render()
    }

    override fun resize(width: Int, height: Int) {
        renderer.resize(width, height)
        hud.resize(width, height)
        inspector.resize(width, height)
    }

    override fun dispose() {
        renderer.dispose()
        hud.dispose()
        inspector.dispose()
    }
}
