package rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.VertexAttributes
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Disposable
import tick.TickEngine
import world.BuildingType
import world.CellCoord
import world.Terrain

class CityRenderer(
    private val engine: TickEngine,
    val controller: OrbitController
) : Disposable {

    private val map = engine.map

    private val modelBatch  = ModelBatch()
    private val environment = Environment().apply {
        set(ColorAttribute(ColorAttribute.AmbientLight, 0.45f, 0.45f, 0.45f, 1f))
        add(DirectionalLight().set(0.85f, 0.85f, 0.80f, -1f, -0.8f, -0.3f))
    }

    // Shared unit-box model; each ModelInstance gets its own material copy
    private val boxModel = ModelBuilder().createBox(
        1f, 1f, 1f,
        Material(ColorAttribute.createDiffuse(Color.WHITE)),
        (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
    )

    private val terrainInstances  = buildTerrainInstances()
    private val buildingInstances = buildBuildingInstances()

    // 2D overlay for peep dots and selection ring
    private val overlayShapes = ShapeRenderer()

    var selectedCoord: CellCoord? = null

    companion object {
        const val CS        = 32f   // cell size in world units
        const val TERRAIN_H =  2f   // flat ground slab height
        const val PEEP_H    = 18f   // peep dot height above ground
        const val PEEP_DOT  =  6f   // screen-space dot radius (px)

        val BUILDING_HEIGHTS = mapOf(
            BuildingType.Residential   to 64f,
            BuildingType.Commercial    to 48f,
            BuildingType.Industrial    to 32f,
            BuildingType.Entertainment to 96f
        )
        val TERRAIN_COLORS = mapOf(
            Terrain.Road     to Color(0.30f, 0.30f, 0.30f, 1f),
            Terrain.Sidewalk to Color(0.70f, 0.70f, 0.60f, 1f),
            Terrain.Park     to Color(0.20f, 0.60f, 0.20f, 1f),
            Terrain.Interior to Color(0.50f, 0.50f, 0.50f, 1f),
            Terrain.Tunnel   to Color(0.20f, 0.20f, 0.20f, 1f),
            Terrain.Empty    to Color(0.10f, 0.10f, 0.15f, 1f)
        )
        val BUILDING_COLORS = mapOf(
            BuildingType.Residential   to Color(0.80f, 0.50f, 0.20f, 1f),
            BuildingType.Commercial    to Color(0.20f, 0.50f, 0.90f, 1f),
            BuildingType.Industrial    to Color(0.60f, 0.30f, 0.30f, 1f),
            BuildingType.Entertainment to Color(0.90f, 0.80f, 0.10f, 1f)
        )
    }

    // ---- Instance builders (called once at startup) ----

    private fun buildTerrainInstances(): List<ModelInstance> =
        map.groundCells().map { cell ->
            ModelInstance(boxModel).also { inst ->
                inst.materials[0].set(
                    ColorAttribute.createDiffuse(TERRAIN_COLORS[cell.terrain] ?: Color.MAGENTA)
                )
                val wx = cell.coord.x * CS + CS / 2f
                val wz = cell.coord.y * CS + CS / 2f
                inst.transform.setToTranslationAndScaling(wx, TERRAIN_H / 2f, wz, CS, TERRAIN_H, CS)
            }
        }.toList()

    private fun buildBuildingInstances(): List<ModelInstance> =
        map.buildings.values.flatMap { building ->
            val height = BUILDING_HEIGHTS[building.type] ?: 32f
            val color  = BUILDING_COLORS[building.type]  ?: Color.WHITE
            building.cells.map { coord ->
                ModelInstance(boxModel).also { inst ->
                    inst.materials[0].set(ColorAttribute.createDiffuse(color))
                    val wx = coord.x * CS + CS / 2f
                    val wz = coord.y * CS + CS / 2f
                    val wy = TERRAIN_H + height / 2f
                    inst.transform.setToTranslationAndScaling(wx, wy, wz, CS - 2f, height, CS - 2f)
                }
            }
        }

    // ---- Render ----

    fun render() {
        // 3D pass
        modelBatch.begin(controller.camera)
        terrainInstances.forEach  { modelBatch.render(it, environment) }
        buildingInstances.forEach { modelBatch.render(it, environment) }
        modelBatch.end()

        // 2D overlay: peep dots + selection ring
        val sw = Gdx.graphics.width.toFloat()
        val sh = Gdx.graphics.height.toFloat()
        overlayShapes.projectionMatrix = Matrix4().setToOrtho2D(0f, 0f, sw, sh)

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        val tmp = Vector3()

        overlayShapes.begin(ShapeRenderer.ShapeType.Filled)
        engine.peeps.values.forEach { peep ->
            tmp.set(peep.position.x * CS + CS / 2f, TERRAIN_H + PEEP_H, peep.position.y * CS + CS / 2f)
            controller.camera.project(tmp)
            if (tmp.z in 0f..1f) {
                overlayShapes.color = peepColor(peep.needs.hunger, peep.needs.fatigue)
                overlayShapes.circle(tmp.x, tmp.y, PEEP_DOT, 8)
            }
        }
        overlayShapes.end()

        selectedCoord?.let { coord ->
            tmp.set(coord.x * CS + CS / 2f, TERRAIN_H + PEEP_H, coord.y * CS + CS / 2f)
            controller.camera.project(tmp)
            if (tmp.z in 0f..1f) {
                overlayShapes.begin(ShapeRenderer.ShapeType.Line)
                overlayShapes.color = Color.WHITE
                overlayShapes.circle(tmp.x, tmp.y, 14f, 16)
                overlayShapes.end()
            }
        }

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST)
    }

    // ---- Picking ----

    sealed class PickResult {
        data class PeepPick(val id: Int)     : PickResult()
        data class BuildingPick(val id: Int) : PickResult()
    }

    fun pick(screenX: Int, screenY: Int): PickResult? {
        val ray          = controller.camera.getPickRay(screenX.toFloat(), screenY.toFloat())
        val intersection = Vector3()

        // Peeps: sphere with radius = CS/2 for generous hit area
        var closestPeep: Pair<Int, Float>? = null
        engine.peeps.values.forEach { peep ->
            val center = Vector3(
                peep.position.x * CS + CS / 2f,
                TERRAIN_H + PEEP_H,
                peep.position.y * CS + CS / 2f
            )
            if (Intersector.intersectRaySphere(ray, center, CS / 2f, intersection)) {
                val dist = ray.origin.dst(intersection)
                if (closestPeep == null || dist < closestPeep!!.second)
                    closestPeep = Pair(peep.id, dist)
            }
        }
        if (closestPeep != null) return PickResult.PeepPick(closestPeep!!.first)

        // Buildings: bounding box per cell
        var closestBuilding: Pair<Int, Float>? = null
        engine.map.buildings.values.forEach { building ->
            val height = BUILDING_HEIGHTS[building.type] ?: 32f
            building.cells.forEach { coord ->
                val bb = BoundingBox(
                    Vector3(coord.x * CS, TERRAIN_H, coord.y * CS),
                    Vector3((coord.x + 1) * CS, TERRAIN_H + height, (coord.y + 1) * CS)
                )
                if (Intersector.intersectRayBounds(ray, bb, intersection)) {
                    val dist = ray.origin.dst(intersection)
                    if (closestBuilding == null || dist < closestBuilding!!.second)
                        closestBuilding = Pair(building.id, dist)
                }
            }
        }
        if (closestBuilding != null) return PickResult.BuildingPick(closestBuilding!!.first)

        return null
    }

    fun resize(w: Int, h: Int) {
        controller.camera.viewportWidth  = w.toFloat()
        controller.camera.viewportHeight = h.toFloat()
        controller.camera.update()
    }

    private fun peepColor(hunger: Float, fatigue: Float) = when {
        hunger  > 0.6f -> Color(1f, 0.3f, 0.3f, 1f)
        fatigue > 0.8f -> Color(0.3f, 0.5f, 1f, 1f)
        else           -> Color.WHITE
    }

    override fun dispose() {
        modelBatch.dispose()
        boxModel.dispose()
        overlayShapes.dispose()
    }
}
