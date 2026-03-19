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
import peep.NeedType
import tick.TickEngine
import world.BuildingSubtype
import world.BuildingType
import world.CellCoord
import world.TravelMode
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

    private val boxModel = ModelBuilder().createBox(
        1f, 1f, 1f,
        Material(ColorAttribute.createDiffuse(Color.WHITE)),
        (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal).toLong()
    )

    private val terrainInstances  = buildTerrainInstances()
    private val buildingInstances = buildBuildingInstances()

    private val overlayShapes = ShapeRenderer()

    var selectedCoord: CellCoord? = null

    companion object {
        const val CS        = 32f
        const val TERRAIN_H =  2f
        const val PEEP_H    = 18f
        const val PEEP_DOT  =  6f

        val SUBTYPE_HEIGHTS = mapOf(
            BuildingSubtype.House to 40f,
            BuildingSubtype.Apartment to 80f,
            BuildingSubtype.Luxury to 120f,
            BuildingSubtype.Restaurant to 36f,
            BuildingSubtype.GroceryStore to 28f,
            BuildingSubtype.Cafe to 24f,
            BuildingSubtype.Shop to 32f,
            BuildingSubtype.Office to 96f,
            BuildingSubtype.Factory to 32f,
            BuildingSubtype.Warehouse to 24f,
            BuildingSubtype.Workshop to 28f,
            BuildingSubtype.Hospital to 64f,
            BuildingSubtype.School to 48f,
            BuildingSubtype.Library to 36f,
            BuildingSubtype.CommunityCenter to 40f,
            BuildingSubtype.Park to 4f,
            BuildingSubtype.Gym to 32f,
            BuildingSubtype.Theater to 56f,
            BuildingSubtype.Stadium to 80f,
            BuildingSubtype.Museum to 48f,
            BuildingSubtype.PoliceStation to 40f,
            BuildingSubtype.FireStation to 36f,
            BuildingSubtype.ParkingLot to 8f,
            BuildingSubtype.ParkingGarage to 48f,
            BuildingSubtype.TrainStation to 44f,
            BuildingSubtype.SubwayStation to 32f,
            BuildingSubtype.BusDepot to 28f
        )

        val CATEGORY_HEIGHTS = mapOf(
            BuildingType.Residential to 64f,
            BuildingType.Commercial to 48f,
            BuildingType.Industrial to 32f,
            BuildingType.Civic to 48f,
            BuildingType.Recreation to 32f,
            BuildingType.Entertainment to 96f
        )

        val SUBTYPE_COLORS = mapOf(
            BuildingSubtype.House to Color(0.40f, 0.75f, 0.35f, 1f),
            BuildingSubtype.Apartment to Color(0.30f, 0.65f, 0.25f, 1f),
            BuildingSubtype.Luxury to Color(0.20f, 0.85f, 0.40f, 1f),
            BuildingSubtype.Restaurant to Color(0.20f, 0.50f, 0.90f, 1f),
            BuildingSubtype.GroceryStore to Color(0.30f, 0.60f, 0.85f, 1f),
            BuildingSubtype.Cafe to Color(0.25f, 0.55f, 0.95f, 1f),
            BuildingSubtype.Shop to Color(0.35f, 0.65f, 0.80f, 1f),
            BuildingSubtype.Office to Color(0.15f, 0.40f, 0.80f, 1f),
            BuildingSubtype.Factory to Color(0.80f, 0.70f, 0.20f, 1f),
            BuildingSubtype.Warehouse to Color(0.70f, 0.60f, 0.15f, 1f),
            BuildingSubtype.Workshop to Color(0.90f, 0.80f, 0.30f, 1f),
            BuildingSubtype.Hospital to Color(0.70f, 0.45f, 0.75f, 1f),
            BuildingSubtype.School to Color(0.60f, 0.35f, 0.70f, 1f),
            BuildingSubtype.Library to Color(0.55f, 0.30f, 0.65f, 1f),
            BuildingSubtype.CommunityCenter to Color(0.65f, 0.40f, 0.80f, 1f),
            BuildingSubtype.Park to Color(0.45f, 0.80f, 0.35f, 1f),
            BuildingSubtype.Gym to Color(0.55f, 0.70f, 0.30f, 1f),
            BuildingSubtype.Theater to Color(0.50f, 0.75f, 0.25f, 1f),
            BuildingSubtype.Stadium to Color(0.60f, 0.85f, 0.40f, 1f),
            BuildingSubtype.Museum to Color(0.40f, 0.65f, 0.20f, 1f),
            BuildingSubtype.PoliceStation to Color(0.30f, 0.40f, 0.80f, 1f),
            BuildingSubtype.FireStation to Color(0.85f, 0.25f, 0.20f, 1f),
            BuildingSubtype.ParkingLot to Color(0.50f, 0.50f, 0.55f, 1f),
            BuildingSubtype.ParkingGarage to Color(0.45f, 0.45f, 0.50f, 1f),
            BuildingSubtype.TrainStation to Color(0.20f, 0.45f, 0.75f, 1f),
            BuildingSubtype.SubwayStation to Color(0.55f, 0.30f, 0.60f, 1f),
            BuildingSubtype.BusDepot to Color(0.80f, 0.65f, 0.15f, 1f)
        )

        val CATEGORY_COLORS = mapOf(
            BuildingType.Residential to Color(0.80f, 0.50f, 0.20f, 1f),
            BuildingType.Commercial to Color(0.20f, 0.50f, 0.90f, 1f),
            BuildingType.Industrial to Color(0.60f, 0.30f, 0.30f, 1f),
            BuildingType.Civic to Color(0.65f, 0.40f, 0.75f, 1f),
            BuildingType.Recreation to Color(0.50f, 0.75f, 0.30f, 1f),
            BuildingType.Entertainment to Color(0.90f, 0.80f, 0.10f, 1f)
        )

        val TERRAIN_COLORS = mapOf(
            Terrain.Interstate    to Color(0.25f, 0.25f, 0.25f, 1f),
            Terrain.ArterialRoad  to Color(0.31f, 0.31f, 0.31f, 1f),
            Terrain.CollectorRoad to Color(0.37f, 0.37f, 0.37f, 1f),
            Terrain.LocalRoad     to Color(0.50f, 0.50f, 0.50f, 1f),
            Terrain.RuralRoad     to Color(0.55f, 0.45f, 0.33f, 1f),
            Terrain.BusLane       to Color(0.55f, 0.18f, 0.18f, 1f),
            Terrain.BikePath      to Color(0.13f, 0.55f, 0.13f, 1f),
            Terrain.Sidewalk      to Color(0.70f, 0.70f, 0.60f, 1f),
            Terrain.Park          to Color(0.20f, 0.60f, 0.20f, 1f),
            Terrain.Interior      to Color(0.50f, 0.50f, 0.50f, 1f),
            Terrain.Tunnel        to Color(0.20f, 0.20f, 0.20f, 1f),
            Terrain.Parking       to Color(0.44f, 0.50f, 0.56f, 1f),
            Terrain.RailTrack     to Color(0.27f, 0.51f, 0.71f, 1f),
            Terrain.Platform      to Color(0.66f, 0.66f, 0.66f, 1f),
            Terrain.Empty         to Color(0.10f, 0.10f, 0.15f, 1f)
        )

        val CAR_COLOR = Color(0.85f, 0.20f, 0.20f, 1f)
        val BIKE_COLOR = Color(0.20f, 0.75f, 0.30f, 1f)
        val PARKED_CAR_COLOR = Color(0.60f, 0.15f, 0.15f, 1f)
        val BUS_COLOR = Color(0.95f, 0.75f, 0.10f, 1f)
        val TRAIN_COLOR = Color(0.20f, 0.45f, 0.85f, 1f)

        val NEED_COLORS = mapOf(
            NeedType.Hunger to Color(1f, 0.3f, 0.3f, 1f),
            NeedType.Thirst to Color(0.3f, 0.7f, 1f, 1f),
            NeedType.Sleep to Color(0.3f, 0.5f, 1f, 1f),
            NeedType.Warmth to Color(1f, 0.6f, 0.2f, 1f),
            NeedType.Shelter to Color(0.8f, 0.65f, 1f, 1f),
            NeedType.Health to Color(1f, 0.4f, 0.6f, 1f),
            NeedType.Financial to Color(0.2f, 0.8f, 0.2f, 1f),
            NeedType.Friendship to Color(0.65f, 0.9f, 0.63f, 1f),
            NeedType.Family to Color(0.9f, 0.7f, 0.8f, 1f),
            NeedType.Community to Color(0.6f, 0.8f, 0.9f, 1f),
            NeedType.Recognition to Color(1f, 0.85f, 0.3f, 1f),
            NeedType.Accomplishment to Color(0.9f, 0.75f, 0.4f, 1f),
            NeedType.Status to Color(0.85f, 0.7f, 0.1f, 1f),
            NeedType.Creativity to Color(0.97f, 0.88f, 0.68f, 1f),
            NeedType.Learning to Color(0.7f, 0.5f, 0.9f, 1f),
            NeedType.Purpose to Color(0.8f, 0.6f, 0.95f, 1f)
        )
    }

    private fun buildingHeight(building: world.Building): Float =
        building.subtype?.let { SUBTYPE_HEIGHTS[it] }
            ?: CATEGORY_HEIGHTS[building.type]
            ?: 32f

    private fun buildingColor(building: world.Building): Color =
        building.subtype?.let { SUBTYPE_COLORS[it] }
            ?: CATEGORY_COLORS[building.type]
            ?: Color.WHITE

    private fun buildTerrainInstances(): List<ModelInstance> =
        map.groundCells().filter { it.buildingId == null }.map { cell ->
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
            val height = buildingHeight(building)
            val color  = buildingColor(building)
            building.cells.map { coord ->
                ModelInstance(boxModel).also { inst ->
                    inst.materials[0].set(ColorAttribute.createDiffuse(color))
                    val wx = coord.x * CS + CS / 2f
                    val wz = coord.y * CS + CS / 2f
                    val wy = TERRAIN_H + 0.01f + height / 2f
                    inst.transform.setToTranslationAndScaling(wx, wy, wz, CS - 2f, height, CS - 2f)
                }
            }
        }

    fun render() {
        // Day/night ambient lighting based on SimClock
        val hour = engine.clock.hour
        val ambientLevel = when {
            hour in 7..17  -> 0.45f                            // day
            hour in 18..20 -> 0.45f - (hour - 17) * 0.06f     // dusk
            hour in 5..6   -> 0.15f + (hour - 4) * 0.10f      // dawn
            else           -> 0.12f                            // night
        }
        val warmth = if (hour in 6..18) 0f else 0.08f  // blue tint at night
        // Weather tint
        val w = engine.weather.current
        val weatherR = when (w) {
            world.WeatherState.Rain -> -0.05f
            world.WeatherState.Snow -> 0.03f
            world.WeatherState.Heatwave -> 0.06f
            else -> 0f
        }
        val weatherG = when (w) {
            world.WeatherState.Rain -> -0.03f
            world.WeatherState.Snow -> 0.03f
            else -> 0f
        }
        val weatherB = when (w) {
            world.WeatherState.Rain -> 0.05f
            world.WeatherState.Snow -> 0.05f
            world.WeatherState.Heatwave -> -0.04f
            else -> 0f
        }
        environment.set(ColorAttribute(
            ColorAttribute.AmbientLight,
            (ambientLevel - warmth + weatherR).coerceIn(0.05f, 1f),
            (ambientLevel - warmth + weatherG).coerceIn(0.05f, 1f),
            (ambientLevel + warmth + weatherB).coerceIn(0.05f, 1f),
            1f
        ))

        modelBatch.begin(controller.camera)
        terrainInstances.forEach  { modelBatch.render(it, environment) }
        buildingInstances.forEach { modelBatch.render(it, environment) }
        modelBatch.end()

        val sw = Gdx.graphics.width.toFloat()
        val sh = Gdx.graphics.height.toFloat()
        overlayShapes.projectionMatrix = Matrix4().setToOrtho2D(0f, 0f, sw, sh)

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST)
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        val tmp = Vector3()

        // Parked vehicles
        overlayShapes.begin(ShapeRenderer.ShapeType.Filled)
        engine.map.parkedVehicles.forEach { (coord, _) ->
            tmp.set(coord.x * CS + CS / 2f, TERRAIN_H + 4f, coord.y * CS + CS / 2f)
            controller.camera.project(tmp)
            if (tmp.z in 0f..1f) {
                overlayShapes.color = PARKED_CAR_COLOR
                overlayShapes.rect(tmp.x - 5f, tmp.y - 3f, 10f, 6f)
            }
        }

        // Buses
        engine.transit.buses.values.forEach { bus ->
            tmp.set(bus.position.x * CS + CS / 2f, TERRAIN_H + PEEP_H + 4f, bus.position.y * CS + CS / 2f)
            controller.camera.project(tmp)
            if (tmp.z in 0f..1f) {
                overlayShapes.color = BUS_COLOR
                overlayShapes.rect(tmp.x - 8f, tmp.y - 5f, 16f, 10f)
            }
        }

        // Trains
        engine.transit.trains.values.forEach { train ->
            tmp.set(train.position.x * CS + CS / 2f, TERRAIN_H + PEEP_H + 6f, train.position.y * CS + CS / 2f)
            controller.camera.project(tmp)
            if (tmp.z in 0f..1f) {
                overlayShapes.color = TRAIN_COLOR
                overlayShapes.rect(tmp.x - 12f, tmp.y - 5f, 24f, 10f)
            }
        }

        // Peeps and moving vehicles
        engine.peeps.values.forEach { peep ->
            tmp.set(peep.position.x * CS + CS / 2f, TERRAIN_H + PEEP_H, peep.position.y * CS + CS / 2f)
            controller.camera.project(tmp)
            if (tmp.z in 0f..1f) {
                when (peep.travelMode) {
                    TravelMode.Drive -> {
                        overlayShapes.color = CAR_COLOR
                        overlayShapes.rect(tmp.x - 6f, tmp.y - 4f, 12f, 8f)
                    }
                    TravelMode.Bike -> {
                        overlayShapes.color = BIKE_COLOR
                        overlayShapes.triangle(
                            tmp.x, tmp.y + 5f,
                            tmp.x - 4f, tmp.y - 4f,
                            tmp.x + 4f, tmp.y - 4f
                        )
                    }
                    else -> {
                        overlayShapes.color = peepColor(peep)
                        overlayShapes.circle(tmp.x, tmp.y, PEEP_DOT, 8)
                    }
                }
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

    sealed class PickResult {
        data class PeepPick(val id: Int)     : PickResult()
        data class BuildingPick(val id: Int) : PickResult()
    }

    fun pick(screenX: Int, screenY: Int): PickResult? {
        val ray          = controller.camera.getPickRay(screenX.toFloat(), screenY.toFloat())
        val intersection = Vector3()

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

        var closestBuilding: Pair<Int, Float>? = null
        engine.map.buildings.values.forEach { building ->
            val height = buildingHeight(building)
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

    private fun peepColor(peep: peep.Peep): Color {
        if (peep.isHomeless) return Color.GRAY
        val top = peep.needs.topNeed()
        if (top != null && top.second > 0.3f) {
            return NEED_COLORS[top.first] ?: Color.WHITE
        }
        return Color.WHITE
    }

    override fun dispose() {
        modelBatch.dispose()
        boxModel.dispose()
        overlayShapes.dispose()
    }
}
