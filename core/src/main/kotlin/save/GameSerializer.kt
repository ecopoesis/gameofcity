package save

import kotlinx.serialization.json.Json
import tick.TickEngine

object GameSerializer {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun serialize(engine: TickEngine): String =
        json.encodeToString(SaveData.serializer(), SaveConverter.toSaveData(engine))

    fun deserialize(jsonStr: String): TickEngine =
        SaveConverter.fromSaveData(json.decodeFromString(SaveData.serializer(), jsonStr))
}
