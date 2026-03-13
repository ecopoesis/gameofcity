import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.utils.ScreenUtils
import rendering.CityRenderer
import world.WorldMap
import world.MapLoader

class GameOfCityApp : ApplicationAdapter() {

    private lateinit var map: WorldMap
    private lateinit var renderer: CityRenderer

    private var cameraX = 0f
    private var cameraY = 0f
    private val cameraSpeed = 200f

    override fun create() {
        map = MapLoader.loadFromJson("maps/starter.json")
        renderer = CityRenderer(map)
    }

    override fun render() {
        val delta = Gdx.graphics.deltaTime

        // Camera pan with arrow keys
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT))  cameraX -= cameraSpeed * delta
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) cameraX += cameraSpeed * delta
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN))  cameraY -= cameraSpeed * delta
        if (Gdx.input.isKeyPressed(Input.Keys.UP))    cameraY += cameraSpeed * delta

        ScreenUtils.clear(0.1f, 0.1f, 0.15f, 1f)
        renderer.render(cameraX, cameraY)
    }

    override fun dispose() {
        renderer.dispose()
    }
}
