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

    // Dynamic widgets updated in-place each frame
    private val needBars = mutableMapOf<NeedType, ProgressBar>()
    private var actionLabel: Label? = null
    private var moneyLabel: Label? = null
    private var homeLabel: Label? = null
    private var insideLabel: Label? = null
    private var friendsLabel: Label? = null

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
        for ((needType, bar) in needBars) {
            val v = p.needs.get(needType)
            bar.value = v
            bar.color = barColor(v, NEED_THRESHOLDS[needType] ?: 0.6f)
        }
        actionLabel?.setText(fmtAction(p.lastAction))
        moneyLabel?.setText("$%.2f".format(p.money))
        homeLabel?.setText(p.homeId?.let { "bldg$it" } ?: "EVICTED")
        friendsLabel?.setText(formatFriends(p))
    }

    private fun updateBuilding(b: Building) {
        insideLabel?.setText("${b.currentOccupants.size} / ${b.capacity}${if (b.isFull) "  FULL" else ""}")
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
        needBars.clear()
        actionLabel = null; moneyLabel = null; homeLabel = null
        insideLabel = null; friendsLabel = null
    }

    private fun buildPeepWindow(p: Peep): Window {
        val win = Window("Peep Inspector", skin)
        win.isMovable = true

        val t = Table(skin).pad(8f)
        val C0 = 130f; val C1 = 155f

        fun addRow(label: String, widget: Actor) {
            t.add(Label(label, skin, "dim")).width(C0).left()
            t.add(widget).width(C1).left()
            t.row().padTop(2f)
        }

        fun dynLabel(label: String, value: String): Label {
            val l = Label(value, skin)
            addRow(label, l)
            return l
        }

        fun needBar(needType: NeedType, label: String, value: Float): ProgressBar {
            val bar = ProgressBar(0f, 1f, 0.01f, false, skin)
            bar.value = value
            bar.color = barColor(value, NEED_THRESHOLDS[needType] ?: 0.6f)
            addRow(label, bar)
            needBars[needType] = bar
            return bar
        }

        // Header
        t.add(Label("${p.name}  (id=${p.id})", skin, "header"))
            .colspan(2).left().padBottom(8f)
        t.row()

        // Maslow needs grouped by level
        for (level in MASLOW_DISPLAY) {
            t.add(Label(level.name, skin, "dim")).colspan(2).left().padTop(6f)
            t.row()
            for ((needType, displayName) in level.needs) {
                needBar(needType, "  $displayName", p.needs.get(needType))
            }
        }

        t.add().colspan(2).height(4f); t.row()

        // Edit sliders for key needs
        fun editSlider(label: String, init: Float, setter: (Float) -> Unit) {
            val sl = Slider(0f, 1f, 0.05f, false, skin)
            sl.value = init
            sl.addListener(object : ChangeListener() {
                override fun changed(event: ChangeEvent, actor: Actor) = setter(sl.value)
            })
            addRow("  Edit $label", sl)
        }
        editSlider("Hunger", p.needs.hunger) { v -> engine.peeps[p.id]?.needs?.hunger = v }
        editSlider("Sleep",  p.needs.sleep)  { v -> engine.peeps[p.id]?.needs?.sleep  = v }

        t.add().colspan(2).height(4f); t.row()

        // Info
        actionLabel = dynLabel("Action", fmtAction(p.lastAction))
        moneyLabel  = dynLabel("Money",  "$%.2f".format(p.money))
        homeLabel   = dynLabel("Home",   p.homeId?.let { "bldg$it" } ?: "EVICTED")
        dynLabel("Job", p.jobId?.let { "bldg$it" } ?: "none")
        friendsLabel = dynLabel("Friends", formatFriends(p))

        t.add().colspan(2).height(4f); t.row()

        // Brain selector
        t.add(Label("Brain", skin, "dim")).width(C0).left()
        val utilBtn    = TextButton("Util", skin)
        val pyramidBtn = TextButton("Pyr", skin)
        val waveBtn    = TextButton("Wave", skin)
        val randBtn    = TextButton("Rand", skin)
        val idleBtn    = TextButton("Idle", skin)

        val group = ButtonGroup<TextButton>()
        group.setMinCheckCount(0); group.setMaxCheckCount(1)
        group.add(utilBtn, pyramidBtn, waveBtn, randBtn, idleBtn)
        when (p.brain) {
            is UtilityBrain -> utilBtn.isChecked = true
            is PyramidBrain -> pyramidBtn.isChecked = true
            is WaveBrain    -> waveBtn.isChecked = true
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
        brainBtn(utilBtn)    { UtilityBrain() }
        brainBtn(pyramidBtn) { PyramidBrain() }
        brainBtn(waveBtn)    { WaveBrain()    }
        brainBtn(randBtn)    { RandomBrain()  }
        brainBtn(idleBtn)    { IdleBrain()    }

        val brainRow = Table()
        brainRow.add(utilBtn).padRight(3f)
        brainRow.add(pyramidBtn).padRight(3f)
        brainRow.add(waveBtn).padRight(3f)
        brainRow.add(randBtn).padRight(3f)
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

        val displayName = b.subtype?.name ?: b.type.name
        t.add(Label("bldg${b.id}  -  $displayName", skin, "header"))
            .colspan(2).left().padBottom(8f)
        t.row()

        val inside = engine.peeps.values.count { p ->
            engine.map.getCell(p.position)?.buildingId == b.id
        }
        insideLabel = row("Inside now", "$inside / ${b.capacity}${if (b.isFull) "  FULL" else ""}")
        row("Category", b.type.name)
        if (b.subtype != null) row("Subtype", b.subtype!!.name)
        row("Cells", "${b.cells.size}")
        if (b.isWorkplace) row("Wage", "$${b.wage}/tick")

        // Show residents and workers
        val residents = engine.peeps.values.filter { it.homeId == b.id }
        val workers = engine.peeps.values.filter { it.jobId == b.id }
        if (residents.isNotEmpty()) {
            t.add(Label("Residents", skin, "dim")).colspan(2).left().padTop(6f); t.row()
            residents.take(5).forEach { p ->
                t.add(Label("  ${p.name}", skin)).colspan(2).left(); t.row()
            }
            if (residents.size > 5) {
                t.add(Label("  +${residents.size - 5} more", skin, "dim")).colspan(2).left(); t.row()
            }
        }
        if (workers.isNotEmpty()) {
            t.add(Label("Workers", skin, "dim")).colspan(2).left().padTop(6f); t.row()
            workers.take(5).forEach { p ->
                t.add(Label("  ${p.name}", skin)).colspan(2).left(); t.row()
            }
            if (workers.size > 5) {
                t.add(Label("  +${workers.size - 5} more", skin, "dim")).colspan(2).left(); t.row()
            }
        }

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

    private fun formatFriends(p: Peep): String {
        val top = p.friendships.entries.sortedByDescending { it.value }.take(3)
        return if (top.isEmpty()) "none"
        else top.joinToString(", ") { "P${it.key}(%.2f)".format(it.value) }
    }

    private fun fmtAction(a: Action): String = when (a) {
        is Action.Work      -> "Work(bldg${a.buildingId})"
        is Action.Eat       -> "Eat(bldg${a.buildingId})"
        is Action.Drink     -> "Drink(bldg${a.buildingId})"
        is Action.Sleep     -> "Sleep(bldg${a.buildingId})"
        is Action.Shop      -> "Shop(bldg${a.buildingId})"
        is Action.Heal      -> "Heal(bldg${a.buildingId})"
        is Action.Learn     -> "Learn(bldg${a.buildingId})"
        is Action.Exercise  -> "Exercise(bldg${a.buildingId})"
        is Action.Relax     -> "Relax(bldg${a.buildingId})"
        is Action.Watch     -> "Watch(bldg${a.buildingId})"
        is Action.MoveTo    -> "Walk->(${a.target.x},${a.target.y})"
        is Action.Socialize -> "Socialize(p${a.targetPeepId})"
        Action.Idle         -> "Idle"
    }

    fun render() { stage.act(); stage.draw() }
    fun resize(w: Int, h: Int) = stage.viewport.update(w, h, true)
    fun dispose() = stage.dispose()

    companion object {
        private data class LevelDisplay(val name: String, val needs: List<Pair<NeedType, String>>)

        private val MASLOW_DISPLAY = listOf(
            LevelDisplay("PHYSIOLOGICAL", listOf(
                NeedType.Hunger to "Hunger", NeedType.Thirst to "Thirst",
                NeedType.Sleep to "Sleep", NeedType.Warmth to "Warmth"
            )),
            LevelDisplay("SAFETY", listOf(
                NeedType.Shelter to "Shelter", NeedType.Health to "Health",
                NeedType.Financial to "Financial"
            )),
            LevelDisplay("LOVE/BELONGING", listOf(
                NeedType.Friendship to "Friendship", NeedType.Family to "Family",
                NeedType.Community to "Community"
            )),
            LevelDisplay("ESTEEM", listOf(
                NeedType.Recognition to "Recognition", NeedType.Accomplishment to "Accomplishment",
                NeedType.Status to "Status"
            )),
            LevelDisplay("SELF-ACTUALIZATION", listOf(
                NeedType.Creativity to "Creativity", NeedType.Learning to "Learning",
                NeedType.Purpose to "Purpose"
            ))
        )

        private val NEED_THRESHOLDS = mapOf(
            NeedType.Hunger to 0.6f, NeedType.Thirst to 0.6f,
            NeedType.Sleep to 0.8f, NeedType.Warmth to 0.7f,
            NeedType.Shelter to 0.7f, NeedType.Health to 0.7f,
            NeedType.Financial to 0.7f, NeedType.Friendship to 0.7f,
            NeedType.Family to 0.7f, NeedType.Community to 0.7f,
            NeedType.Recognition to 0.7f, NeedType.Accomplishment to 0.7f,
            NeedType.Status to 0.7f, NeedType.Creativity to 0.7f,
            NeedType.Learning to 0.7f, NeedType.Purpose to 0.7f
        )
    }
}
