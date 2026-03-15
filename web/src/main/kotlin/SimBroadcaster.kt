import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import save.*
import tick.TickEngine
import java.util.concurrent.ConcurrentHashMap

class SimBroadcaster(private val json: Json) {

    private val sessions = ConcurrentHashMap.newKeySet<WebSocketSession>()

    fun addSession(session: WebSocketSession) {
        sessions.add(session)
    }

    fun removeSession(session: WebSocketSession) {
        sessions.remove(session)
    }

    suspend fun sendSnapshot(session: WebSocketSession, engine: TickEngine) {
        val saveData = SaveConverter.toSaveData(engine)
        val message = json.encodeToString(SnapshotMessage.serializer(), SnapshotMessage(data = saveData))
        try {
            session.send(Frame.Text(message))
        } catch (_: Exception) {
            removeSession(session)
        }
    }

    suspend fun broadcastSnapshot(engine: TickEngine) {
        val saveData = SaveConverter.toSaveData(engine)
        val message = json.encodeToString(SnapshotMessage.serializer(), SnapshotMessage(data = saveData))
        broadcast(message)
    }

    suspend fun broadcastPeepUpdate(engine: TickEngine) {
        val positions = engine.peeps.values.map { peep ->
            PeepPosition(
                id = peep.id,
                x = peep.position.x,
                y = peep.position.y,
                hunger = peep.needs.hunger,
                fatigue = peep.needs.fatigue
            )
        }
        val message = json.encodeToString(
            PeepUpdateMessage.serializer(),
            PeepUpdateMessage(tick = engine.tick, peeps = positions)
        )
        broadcast(message)
    }

    private suspend fun broadcast(message: String) {
        val dead = mutableListOf<WebSocketSession>()
        for (session in sessions) {
            try {
                session.send(Frame.Text(message))
            } catch (_: Exception) {
                dead.add(session)
            }
        }
        dead.forEach { sessions.remove(it) }
    }

    fun hasClients(): Boolean = sessions.isNotEmpty()
}
