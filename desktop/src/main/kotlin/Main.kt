import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration

fun main() {
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Game of City")
        setWindowedMode(1280, 800)
        setForegroundFPS(60)
    }
    Lwjgl3Application(GameOfCityApp(), config)
}
