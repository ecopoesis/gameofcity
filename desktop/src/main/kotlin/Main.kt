import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration

fun main() {
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Game of City")
        setWindowedMode(1280, 800)
        setForegroundFPS(60)
        setBackBufferConfig(8, 8, 8, 8, 24, 0, 0) // 24-bit depth buffer (default 16-bit causes Z-fighting)
    }
    Lwjgl3Application(GameOfCityApp(), config)
}
