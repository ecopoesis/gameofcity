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
import save.CommandMessage
import tick.TickEngine
import java.awt.Desktop
import java.net.URI
import kotlin.time.Duration.Companion.seconds

private val json = Json { prettyPrint = false }

@Volatile
private var simEngine: TickEngine = createEngine()

@Volatile
private var paused = false

@Volatile
private var tickDelayMs = 50L // 20 ticks/sec

private val broadcaster = SimBroadcaster(json)

private fun createEngine(): TickEngine {
    val map = CityGenerator.generate(CityGenConfig())
    val eng = TickEngine(map)
    PeepSpawner.spawn(eng, 50)
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
        // Open browser after a short delay to let server bind
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
            simEngine = createEngine()
        }
        "setSpeed" -> {
            val v = cmd.value ?: return
            tickDelayMs = v.toLong().coerceIn(10, 1000)
        }
    }
}
