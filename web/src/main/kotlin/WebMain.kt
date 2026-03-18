import gen.CityGenConfig
import gen.CityGenerator
import gen.PeepSpawner
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.http.content.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import peep.*
import save.CommandMessage
import save.SaveConverter
import tick.TickEngine
import java.awt.Desktop
import java.net.URI
import kotlin.time.Duration.Companion.seconds

private val json = Json { prettyPrint = false; encodeDefaults = true; ignoreUnknownKeys = true }

@Volatile
private var simEngine: TickEngine = createEngine(CityGenConfig())

@Volatile
private var paused = false

@Volatile
private var tickDelayMs = 50L // 20 ticks/sec

@Volatile
private var currentBrainType = "Utility"

private val broadcaster = SimBroadcaster(json)

private fun createEngine(config: CityGenConfig): TickEngine {
    val map = CityGenerator.generate(config)
    val eng = TickEngine(map)
    CityGenerator.generateTransit(map, eng.transit)
    PeepSpawner.spawn(eng, config.peepCount)
    return eng
}

fun main(args: Array<String>) {
    val headless = args.contains("--headless")
    val port = 8080

    val server = embeddedServer(Netty, port = port) {
        install(WebSockets) {
            pingPeriod = 15.seconds
            timeout = 15.seconds
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }

        routing {
            staticResources("/", "static") {
                default("index.html")
            }

            webSocket("/ws") {
                broadcaster.addSession(this)
                try {
                    // Send initial full snapshot
                    broadcaster.sendSnapshot(this, simEngine)
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            handleCommand(frame.readText())
                        }
                    }
                } finally {
                    broadcaster.removeSession(this)
                }
            }
        }

        // Launch simulation loop as a background coroutine in the application scope
        launch {
            var tickCounter = 0L
            println("Simulation started (${simEngine.peeps.size} peeps)")
            while (isActive) {
                delay(tickDelayMs)
                if (paused) continue

                try {
                    simEngine.step()
                    tickCounter++

                    if (broadcaster.hasClients()) {
                        broadcaster.broadcastPeepUpdate(simEngine)
                        if (tickCounter % 5 == 0L) {
                            broadcaster.broadcastSnapshot(simEngine)
                        }
                        if (tickCounter % 60 == 0L) {
                            broadcaster.broadcastEvents(simEngine)
                        }
                    }
                } catch (e: Exception) {
                    println("Simulation error: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    val url = "http://localhost:$port"
    if (headless) {
        println("GAMEOFCITY_URL=$url")
    } else {
        println("Starting Game of City web server at $url")
    }

    if (!headless) {
        Thread {
            Thread.sleep(1000)
            try {
                Desktop.getDesktop().browse(URI(url))
            } catch (_: Exception) {
                println("Could not open browser automatically. Open $url manually.")
            }
        }.start()
    }

    server.start(wait = true)
}

private fun handleCommand(text: String) {
    val cmd = try {
        json.decodeFromString(CommandMessage.serializer(), text)
    } catch (_: Exception) {
        return
    }

    when (cmd.action) {
        "pause" -> paused = true
        "resume" -> paused = false
        "generate" -> {
            val config = CityGenConfig(
                width = cmd.value ?: 40,
                height = cmd.value ?: 40,
                peepCount = 50,
                organicLevel = 0f
            )
            simEngine = createEngine(config)
        }
        "generateWithConfig" -> {
            // Parse config from stringValue as "width,height,peepCount,organicLevel"
            val parts = cmd.stringValue?.split(",") ?: return
            if (parts.size >= 4) {
                val config = CityGenConfig(
                    width = parts[0].toIntOrNull() ?: 40,
                    height = parts[1].toIntOrNull() ?: 40,
                    peepCount = parts[2].toIntOrNull() ?: 50,
                    organicLevel = parts[3].toFloatOrNull() ?: 0f
                )
                simEngine = createEngine(config)
            }
        }
        "setSpeed" -> {
            val v = cmd.value ?: return
            tickDelayMs = v.toLong().coerceIn(10, 1000)
        }
        "setBrainType" -> {
            val brainName = cmd.stringValue ?: return
            currentBrainType = brainName
            simEngine.peeps.values.forEach { peep ->
                peep.brain = SaveConverter.brainFromName(brainName)
            }
        }
        "setPeepBrain" -> {
            val peepId = cmd.value ?: return
            val brainName = cmd.stringValue ?: return
            simEngine.peeps[peepId]?.brain = SaveConverter.brainFromName(brainName)
        }
        "setNeed" -> {
            val peepId = cmd.value ?: return
            val parts = cmd.stringValue?.split(",") ?: return
            if (parts.size >= 2) {
                val needName = parts[0]
                val needValue = parts[1].toFloatOrNull() ?: return
                val peep = simEngine.peeps[peepId] ?: return
                when (needName) {
                    "Hunger" -> peep.needs.hunger = needValue.coerceIn(0f, 1f)
                    "Sleep" -> peep.needs.sleep = needValue.coerceIn(0f, 1f)
                    "Thirst" -> peep.needs.thirst = needValue.coerceIn(0f, 1f)
                    "Warmth" -> peep.needs.warmth = needValue.coerceIn(0f, 1f)
                    "Shelter" -> peep.needs.shelter = needValue.coerceIn(0f, 1f)
                    "Health" -> peep.needs.health = needValue.coerceIn(0f, 1f)
                    "Friendship" -> peep.needs.friendship = needValue.coerceIn(0f, 1f)
                    "Family" -> peep.needs.family = needValue.coerceIn(0f, 1f)
                    "Community" -> peep.needs.community = needValue.coerceIn(0f, 1f)
                    "Recognition" -> peep.needs.recognition = needValue.coerceIn(0f, 1f)
                    "Accomplishment" -> peep.needs.accomplishment = needValue.coerceIn(0f, 1f)
                    "Creativity" -> peep.needs.creativity = needValue.coerceIn(0f, 1f)
                    "Learning" -> peep.needs.learning = needValue.coerceIn(0f, 1f)
                    "Purpose" -> peep.needs.purpose = needValue.coerceIn(0f, 1f)
                }
            }
        }
    }
}
