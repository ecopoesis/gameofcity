import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.utils.ScreenUtils
import gen.PeepSpawner
import rendering.CityRenderer
import tick.TickEngine
import world.MapLoader

class GameOfCityApp : ApplicationAdapter() {

    private lateinit var engine: TickEngine
    private lateinit var renderer: CityRenderer

    private var cameraX = 0f
    private var cameraY = 0f
    private val cameraSpeed = 200f

    private var tickAccum = 0f
    private val tickDuration = 0.05f  // 20 ticks/second

    override fun create() {
        val map = MapLoader.loadFromJson("maps/starter.json")
        engine = TickEngine(map)
        PeepSpawner.spawn(engine, 50)
        renderer = CityRenderer(engine)
    }

    override fun render() {
        val delta = Gdx.graphics.deltaTime

        // Camera pan with arrow keys
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT))  cameraX -= cameraSpeed * delta
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) cameraX += cameraSpeed * delta
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN))  cameraY -= cameraSpeed * delta
        if (Gdx.input.isKeyPressed(Input.Keys.UP))    cameraY += cameraSpeed * delta

        // Simulation tick at fixed rate
        tickAccum += delta
        while (tickAccum >= tickDuration) {
            engine.step()
            tickAccum -= tickDuration
        }

        ScreenUtils.clear(0.1f, 0.1f, 0.15f, 1f)
        renderer.render(cameraX, cameraY)
    }

    override fun dispose() {
        renderer.dispose()
    }
}
