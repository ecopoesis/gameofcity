package save

import tick.TickEngine
import java.io.File

object DesktopSaveManager {

    private val saveDir: File by lazy {
        val dir = File(System.getProperty("user.home"), ".gameofcity/saves")
        dir.mkdirs()
        dir
    }

    fun save(engine: TickEngine, filename: String = "quicksave.json") {
        val json = GameSerializer.serialize(engine)
        File(saveDir, filename).writeText(json)
        println("[Save] Saved to $filename (tick ${engine.tick})")
    }

    fun load(filename: String = "quicksave.json"): TickEngine? {
        val file = File(saveDir, filename)
        if (!file.exists()) {
            println("[Save] No save file: $filename")
            return null
        }
        val engine = GameSerializer.deserialize(file.readText())
        println("[Save] Loaded $filename (tick ${engine.tick}, ${engine.peeps.size} peeps)")
        return engine
    }

    fun hasSave(filename: String = "quicksave.json"): Boolean =
        File(saveDir, filename).exists()
}
