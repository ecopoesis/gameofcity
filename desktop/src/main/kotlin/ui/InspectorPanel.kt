package ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.viewport.ScreenViewport
import peep.*
import tick.TickEngine
import world.Building

class InspectorPanel(private val engine: TickEngine, private val skin: Skin) {

    val stage = Stage(ScreenViewport())

    private var window: Window? = null
    private var selectedPeepId: Int? = null
    private var selectedBuildingId: Int? = null

    // Dynamic peep widgets updated in-place each frame
    private var hungerBar: ProgressBar? = null
    private var fatigueBar: ProgressBar? = null
    private var socialBar: ProgressBar? = null
    private var entBar: ProgressBar? = null
    private var actionLabel: Label? = null
    private var moneyLabel: Label? = null
    private var homeLabel: Label? = null

    // Dynamic building widget
    private var insideLabel: Label? = null

    // ---- Selection ----

    fun selectPeep(id: Int) {
        selectedPeepId     = id
        selectedBuildingId = null
        rebuild()
    }

    fun selectBuilding(id: Int) {
        selectedBuildingId = id
        selectedPeepId     = null
        rebuild()
    }

    fun clear() {
        selectedPeepId     = null
        selectedBuildingId = null
        window?.remove()
        window = null
        clearRefs()
    }

    // ---- Per-frame update ----

    fun update() {
        val peepId = selectedPeepId
        val bldgId = selectedBuildingId
        when {
            peepId != null -> engine.peeps[peepId]?.let { updatePeep(it) }
            bldgId != null -> engine.map.buildings[bldgId]?.let { updateBuilding(it) }
        }
    }

    private fun updatePeep(p: Peep) {
        hungerBar?.also  { it.value = p.needs.hunger;        it.color = barColor(p.needs.hunger,  0.6f) }
        fatigueBar?.also { it.value = p.needs.fatigue;       it.color = barColor(p.needs.fatigue, 0.8f) }
        socialBar?.also  { it.value = p.needs.social;        it.color = barColor(p.needs.social,  0.7f) }
        entBar?.also     { it.value = p.needs.entertainment; it.color = barColor(p.needs.entertainment, 0.7f) }
        actionLabel?.setText(fmtAction(p.lastAction))
        moneyLabel?.setText("$%.2f".format(p.money))
        homeLabel?.setText(p.homeId?.let { "bldg$it" } ?: "EVICTED")
    }

    private fun updateBuilding(b: Building) {
        val inside = engine.peeps.values.count { p ->
            engine.map.getCell(p.position)?.buildingId == b.id
        }
        insideLabel?.setText("$inside")
    }

    // ---- Build windows ----

    private fun rebuild() {
        window?.remove()
        clearRefs()
        window = when {
            selectedPeepId != null     -> buildPeepWindow(engine.peeps[selectedPeepId!!]         ?: return)
            selectedBuildingId != null -> buildBuildingWindow(engine.map.buildings[selectedBuildingId!!] ?: return)
            else -> return
        }
        stage.addActor(window)
        window!!.pack()
        window!!.setPosition(
            stage.viewport.worldWidth - window!!.width - 10f,
            10f
        )
    }

    private fun clearRefs() {
        hungerBar = null; fatigueBar = null; socialBar = null; entBar = null
        actionLabel = null; moneyLabel = null; homeLabel = null; insideLabel = null
    }

    private fun buildPeepWindow(p: Peep): Window {
        val win = Window("Peep Inspector", skin)
        win.isMovable = true

        val t = Table(skin).pad(8f)
        val C0 = 115f; val C1 = 155f

        fun addRow(label: String, widget: Actor) {
            t.add(Label(label, skin, "dim")).width(C0).left()
            t.add(widget).width(C1).left()
            t.row().padTop(4f)
        }

        fun dynLabel(label: String, value: String): Label {
            val l = Label(value, skin)
            addRow(label, l)
            return l
        }

        fun needBar(label: String, value: Float): ProgressBar {
            val bar = ProgressBar(0f, 1f, 0.01f, false, skin)
            bar.value = value
            bar.color = barColor(value, 0.6f)
            addRow(label, bar)
            return bar
        }

        // Header
        t.add(Label("${p.name}  (id=${p.id})", skin, "header"))
            .colspan(2).left().padBottom(8f)
        t.row()

        // Needs display
        hungerBar  = needBar("Hunger",        p.needs.hunger)
        fatigueBar = needBar("Fatigue",       p.needs.fatigue)
        socialBar  = needBar("Social",        p.needs.social)
        entBar     = needBar("Entertainment", p.needs.entertainment)

        t.add().colspan(2).height(4f); t.row()

        // Edit sliders
        fun editSlider(label: String, init: Float, setter: (Float) -> Unit) {
            val sl = Slider(0f, 1f, 0.05f, false, skin)
            sl.value = init
            sl.addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent, actor: Actor) = setter(sl.value)
            })
            addRow("  Edit $label", sl)
        }
        editSlider("Hunger",  p.needs.hunger)  { v -> engine.peeps[p.id]?.needs?.hunger  = v }
        editSlider("Fatigue", p.needs.fatigue) { v -> engine.peeps[p.id]?.needs?.fatigue = v }

        t.add().colspan(2).height(4f); t.row()

        // Info
        actionLabel = dynLabel("Action", fmtAction(p.lastAction))
        moneyLabel  = dynLabel("Money",  "$%.2f".format(p.money))
        homeLabel   = dynLabel("Home",   p.homeId?.let { "bldg$it" } ?: "EVICTED")
        dynLabel("Job", p.jobId?.let { "bldg$it" } ?: "none")

        t.add().colspan(2).height(4f); t.row()

        // Brain selector
        t.add(Label("Brain", skin, "dim")).width(C0).left()
        val utilBtn = TextButton("Utility", skin)
        val randBtn = TextButton("Random",  skin)
        val idleBtn = TextButton("Idle",    skin)

        // ButtonGroup enforces single-select
        val group = ButtonGroup<TextButton>()
        group.setMinCheckCount(0); group.setMaxCheckCount(1)
        group.add(utilBtn, randBtn, idleBtn)
        when (p.brain) {
            is UtilityBrain -> utilBtn.isChecked = true
            is RandomBrain  -> randBtn.isChecked = true
            else            -> idleBtn.isChecked = true
        }
        group.setMinCheckCount(1)

        fun brainBtn(btn: TextButton, factory: () -> Brain) =
            btn.addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent, actor: Actor) {
                    if (btn.isChecked) engine.peeps[p.id]?.brain = factory()
                }
            })
        brainBtn(utilBtn) { UtilityBrain() }
        brainBtn(randBtn) { RandomBrain()  }
        brainBtn(idleBtn) { IdleBrain()    }

        val brainRow = Table()
        brainRow.add(utilBtn).padRight(4f)
        brainRow.add(randBtn).padRight(4f)
        brainRow.add(idleBtn)
        t.add(brainRow).left(); t.row().padTop(10f)

        // Close
        val close = TextButton("Close", skin)
        close.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) = clear()
        })
        t.add(close).colspan(2).width(80f).center()

        win.add(t)
        return win
    }

    private fun buildBuildingWindow(b: Building): Window {
        val win = Window("Building Inspector", skin)
        win.isMovable = true

        val t = Table(skin).pad(8f)

        fun row(label: String, value: String): Label {
            val l = Label(value, skin)
            t.add(Label(label, skin, "dim")).width(120f).left()
            t.add(l).left()
            t.row().padTop(4f)
            return l
        }

        t.add(Label("bldg${b.id}  —  ${b.type.name}", skin, "header"))
            .colspan(2).left().padBottom(8f)
        t.row()

        val inside = engine.peeps.values.count { p ->
            engine.map.getCell(p.position)?.buildingId == b.id
        }
        insideLabel = row("Inside now", "$inside")
        row("Cells", "${b.cells.size}")

        val close = TextButton("Close", skin)
        close.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) = clear()
        })
        t.add(close).colspan(2).width(80f).center().padTop(12f)

        win.add(t)
        return win
    }

    // ---- Helpers ----

    private fun barColor(value: Float, threshold: Float): Color =
        if (value > threshold) Color.RED else Color.GREEN

    private fun fmtAction(a: Action): String = when (a) {
        is Action.Work      -> "Work(bldg${a.buildingId})"
        is Action.Eat       -> "Eat(bldg${a.buildingId})"
        is Action.Sleep     -> "Sleep(bldg${a.buildingId})"
        is Action.MoveTo    -> "Walk→(${a.target.x},${a.target.y})"
        is Action.Socialize -> "Socialize(p${a.targetPeepId})"
        Action.Idle         -> "Idle"
    }

    fun render() { stage.act(); stage.draw() }
    fun resize(w: Int, h: Int) = stage.viewport.update(w, h, true)
    fun dispose() = stage.dispose()
}
