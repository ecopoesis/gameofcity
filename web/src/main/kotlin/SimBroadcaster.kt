import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import save.*
import tick.CityStats
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
        try {
            val saveData = SaveConverter.toSaveData(engine)
            val message = json.encodeToString(SnapshotMessage.serializer(), SnapshotMessage(data = saveData))
            session.send(Frame.Text(message))
        } catch (e: Exception) {
            println("sendSnapshot failed: ${e.message}")
            e.printStackTrace()
            removeSession(session)
        }
    }

    suspend fun broadcastSnapshot(engine: TickEngine) {
        try {
            val saveData = SaveConverter.toSaveData(engine)
            val message = json.encodeToString(SnapshotMessage.serializer(), SnapshotMessage(data = saveData))
            broadcast(message)
        } catch (e: Exception) {
            println("broadcastSnapshot failed: ${e.message}")
            e.printStackTrace()
        }
    }

    suspend fun broadcastPeepUpdate(engine: TickEngine) {
        val positions = engine.peeps.values.map { peep ->
            val top = peep.needs.topNeed()
            PeepPosition(
                id = peep.id,
                x = peep.position.x,
                y = peep.position.y,
                hunger = peep.needs.hunger,
                fatigue = peep.needs.sleep,
                topNeed = top?.first?.name,
                topNeedValue = top?.second ?: 0f,
                homeless = peep.isHomeless
            )
        }
        val message = json.encodeToString(
            PeepUpdateMessage.serializer(),
            PeepUpdateMessage(
                tick = engine.tick,
                hour = engine.clock.hour,
                minute = engine.clock.minute,
                day = engine.clock.day,
                population = engine.peeps.size,
                births = engine.birthsToday,
                deaths = engine.deathsToday,
                immigrants = engine.immigrantsToday,
                emigrants = engine.emigrantsToday,
                peeps = positions
            )
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

    suspend fun broadcastEvents(engine: TickEngine) {
        val events = engine.eventLog.recent(20).map { e ->
            EventData(e.tick, e.day, e.type.name, e.description, e.involvedPeeps)
        }
        val s = engine.stats
        val statsData = StatsData(
            population = s.population,
            births = s.birthsToday,
            deaths = s.deathsToday,
            immigrants = s.immigrantsToday,
            emigrants = s.emigrantsToday,
            employmentRate = s.employmentRate,
            unemployed = s.unemployedCount,
            avgWage = s.avgWage,
            housingOccupancy = s.housingOccupancy,
            homeless = s.homelessCount,
            avgRent = s.avgRent,
            avgHappiness = s.avgHappiness,
            medianMoney = s.medianMoney,
            gini = s.giniCoefficient,
            avgFriends = s.avgFriends,
            households = s.householdCount,
            singles = s.singlesCount
        )
        val message = json.encodeToString(EventsMessage.serializer(), EventsMessage(events = events, stats = statsData))
        broadcast(message)
    }

    fun hasClients(): Boolean = sessions.isNotEmpty()
}
