package ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.viewport.ScreenViewport
import tick.TickEngine

class HUD(private val engine: TickEngine, skin: Skin) {

    val stage = Stage(ScreenViewport())

    private val tickLabel  = Label("", skin)
    private val timeLabel  = Label("", skin)
    private val popLabel   = Label("", skin)
    private val pauseLabel = Label("[PAUSED]", skin).also { it.color = Color.YELLOW }

    init {
        val table = Table()
        table.setFillParent(true)
        table.top().padTop(6f).padLeft(10f).padRight(10f)
        table.add(tickLabel).padRight(20f)
        table.add(timeLabel).padRight(20f)
        table.add(popLabel).expandX().left()
        table.add(pauseLabel)
        stage.addActor(table)
    }

    fun update(paused: Boolean) {
        val tick      = engine.tick
        val day       = tick / 1440 + 1
        val timeInDay = tick % 1440
        val h         = timeInDay / 60
        val m         = timeInDay % 60
        tickLabel.setText("Tick: $tick")
        timeLabel.setText("Day $day  %02d:%02d".format(h, m))
        popLabel.setText("Pop: ${engine.peeps.size}")
        pauseLabel.isVisible = paused
    }

    fun render() { stage.act(); stage.draw() }
    fun resize(w: Int, h: Int) = stage.viewport.update(w, h, true)
    fun dispose() = stage.dispose()
}
