package ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.viewport.ScreenViewport
import tick.TickEngine

class HUD(private val engine: TickEngine, private val skin: Skin) {

    val stage = Stage(ScreenViewport())

    private val tickLabel  = Label("", skin)
    private val timeLabel  = Label("", skin)
    private val popLabel   = Label("", skin)
    private val speedLabel = Label("", skin)
    private val brainLabel = Label("", skin)
    private val pauseLabel = Label("[PAUSED]", skin).also { it.color = Color.YELLOW }

    var speedFactor: Float = 1f
    var brainType: String = "Utility"

    // Generation config (read by GameOfCityApp)
    var genWidth: Int = 40; private set
    var genHeight: Int = 40; private set
    var genPeeps: Int = 50; private set
    var genOrganic: Float = 0f; private set
    var generateRequested: Boolean = false

    // Brain change callback (set by GameOfCityApp)
    var onBrainChanged: ((String) -> Unit)? = null

    private val genSizeLabel = Label("40", skin)
    private val genPeepsLabel = Label("50", skin)
    private val genOrganicLabel = Label("0%", skin)

    init {
        // Command bar across the top
        val cmdBar = Table(skin)
        cmdBar.setFillParent(true)
        cmdBar.top()
        val barTable = Table(skin)
        val barPm = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        barPm.setColor(Color(0.06f, 0.06f, 0.10f, 0.92f)); barPm.fill()
        barTable.setBackground(TextureRegionDrawable(TextureRegion(Texture(barPm))))
        barPm.dispose()
        barTable.pad(4f, 10f, 4f, 10f)
        val cmds = listOf(
            "Space" to "Pause", "B" to "Brain", "+/-" to "Speed",
            "N" to "Generate", "V" to "Verbose", "F5" to "Save", "F9" to "Load"
        )
        for ((i, pair) in cmds.withIndex()) {
            val (key, action) = pair
            val keyLbl = Label(key, skin).also { it.color = Color(0.56f, 0.63f, 0.76f, 1f) }
            val actLbl = Label(action, skin).also { it.color = Color(0.80f, 0.84f, 0.96f, 1f) }
            barTable.add(keyLbl).padRight(3f)
            barTable.add(actLbl).padRight(if (i < cmds.size - 1) 16f else 0f)
        }
        cmdBar.add(barTable).expandX().fillX()
        stage.addActor(cmdBar)

        // Stats row just below the command bar
        val statsTable = Table()
        statsTable.setFillParent(true)
        statsTable.top().padTop(28f).padLeft(10f).padRight(10f)
        statsTable.add(tickLabel).padRight(20f)
        statsTable.add(timeLabel).padRight(20f)
        statsTable.add(popLabel).padRight(20f)
        statsTable.add(speedLabel).padRight(20f)
        statsTable.add(brainLabel).expandX().left()
        statsTable.add(pauseLabel)
        stage.addActor(statsTable)

        // Left panel with generation controls
        val panel = Window("City Controls", skin)
        panel.isMovable = true
        val t = Table(skin).pad(8f)
        val C0 = 70f; val C1 = 140f

        // Brain selector
        t.add(Label("Brain", skin, "dim")).width(C0).left()
        val brainGroup = ButtonGroup<TextButton>()
        val brainRow = Table()
        val brainNames = listOf("Util" to "Utility", "Pyr" to "Pyramid", "Wave" to "Wave", "Rand" to "Random", "Idle" to "Idle")
        for ((short, full) in brainNames) {
            val btn = TextButton(short, skin)
            if (full == brainType) btn.isChecked = true
            btn.addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent, actor: Actor) {
                    if (btn.isChecked) {
                        brainType = full
                        onBrainChanged?.invoke(full)
                    }
                }
            })
            brainGroup.add(btn)
            brainRow.add(btn).padRight(3f)
        }
        brainGroup.setMinCheckCount(1); brainGroup.setMaxCheckCount(1)
        t.add(brainRow).left(); t.row().padTop(8f)

        // Generation controls
        t.add(Label("GENERATION", skin, "header")).colspan(2).left().padTop(6f); t.row().padTop(4f)

        // Size slider
        t.add(Label("Size", skin, "dim")).width(C0).left()
        val sizeSlider = Slider(20f, 100f, 1f, false, skin)
        sizeSlider.value = 40f
        sizeSlider.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                val v = sizeSlider.value.toInt()
                genWidth = v; genHeight = v
                genSizeLabel.setText("$v")
            }
        })
        val sizeRow = Table()
        sizeRow.add(sizeSlider).width(90f).padRight(6f)
        sizeRow.add(genSizeLabel).width(30f)
        t.add(sizeRow).left(); t.row().padTop(2f)

        // Peeps slider
        t.add(Label("Peeps", skin, "dim")).width(C0).left()
        val peepsSlider = Slider(10f, 200f, 1f, false, skin)
        peepsSlider.value = 50f
        peepsSlider.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                val v = peepsSlider.value.toInt()
                genPeeps = v
                genPeepsLabel.setText("$v")
            }
        })
        val peepsRow = Table()
        peepsRow.add(peepsSlider).width(90f).padRight(6f)
        peepsRow.add(genPeepsLabel).width(30f)
        t.add(peepsRow).left(); t.row().padTop(2f)

        // Organic slider
        t.add(Label("Organic", skin, "dim")).width(C0).left()
        val organicSlider = Slider(0f, 100f, 1f, false, skin)
        organicSlider.value = 0f
        organicSlider.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                val v = organicSlider.value.toInt()
                genOrganic = v / 100f
                genOrganicLabel.setText("$v%")
            }
        })
        val organicRow = Table()
        organicRow.add(organicSlider).width(90f).padRight(6f)
        organicRow.add(genOrganicLabel).width(30f)
        t.add(organicRow).left(); t.row().padTop(6f)

        // Generate button
        val genBtn = TextButton("Generate City", skin)
        genBtn.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                generateRequested = true
            }
        })
        t.add(genBtn).colspan(2).width(180f).center()

        panel.add(t)
        panel.pack()
        panel.setPosition(10f, 10f)
        stage.addActor(panel)
    }

    fun update(paused: Boolean) {
        tickLabel.setText("Tick: ${engine.tick}")
        timeLabel.setText(engine.clock.timeString())
        popLabel.setText("Pop: ${engine.peeps.size}")
        speedLabel.setText("Speed: ${String.format("%.1f", speedFactor)}x")
        brainLabel.setText("Brain: $brainType")
        pauseLabel.isVisible = paused
    }

    fun render() { stage.act(); stage.draw() }
    fun resize(w: Int, h: Int) = stage.viewport.update(w, h, true)
    fun dispose() = stage.dispose()
}
