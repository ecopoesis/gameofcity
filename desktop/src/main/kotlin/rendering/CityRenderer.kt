package rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import world.Building
import world.BuildingType
import world.Terrain
import world.WorldMap

class CityRenderer(private val map: WorldMap) {

    private val camera = OrthographicCamera()
    private val shapes = ShapeRenderer()

    companion object {
        const val CELL_SIZE = 32f

        val TERRAIN_COLORS = mapOf(
            Terrain.Road     to Color(0.3f, 0.3f, 0.3f, 1f),
            Terrain.Sidewalk to Color(0.7f, 0.7f, 0.6f, 1f),
            Terrain.Park     to Color(0.2f, 0.6f, 0.2f, 1f),
            Terrain.Interior to Color(0.5f, 0.5f, 0.5f, 1f),
            Terrain.Tunnel   to Color(0.2f, 0.2f, 0.2f, 1f),
            Terrain.Empty    to Color(0.1f, 0.1f, 0.15f, 1f)
        )

        val BUILDING_COLORS = mapOf(
            BuildingType.Residential    to Color(0.8f, 0.5f, 0.2f, 1f),
            BuildingType.Commercial     to Color(0.2f, 0.5f, 0.9f, 1f),
            BuildingType.Industrial     to Color(0.6f, 0.3f, 0.3f, 1f),
            BuildingType.Entertainment  to Color(0.9f, 0.8f, 0.1f, 1f)
        )
    }

    fun render(cameraX: Float, cameraY: Float) {
        camera.setToOrtho(false, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        camera.position.set(cameraX + Gdx.graphics.width / 2f, cameraY + Gdx.graphics.height / 2f, 0f)
        camera.update()
        shapes.projectionMatrix = camera.combined

        // Draw terrain
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        map.groundCells().forEach { cell ->
            val color = TERRAIN_COLORS[cell.terrain] ?: Color.MAGENTA
            shapes.color = color
            val px = cell.coord.x * CELL_SIZE
            val py = cell.coord.y * CELL_SIZE
            shapes.rect(px, py, CELL_SIZE, CELL_SIZE)
        }
        shapes.end()

        // Draw building outlines
        shapes.begin(ShapeRenderer.ShapeType.Line)
        map.buildings.values.forEach { building ->
            val color = BUILDING_COLORS[building.type] ?: Color.WHITE
            shapes.color = color
            building.cells.forEach { coord ->
                val px = coord.x * CELL_SIZE
                val py = coord.y * CELL_SIZE
                shapes.rect(px + 1f, py + 1f, CELL_SIZE - 2f, CELL_SIZE - 2f)
            }
        }
        shapes.end()
    }

    fun dispose() {
        shapes.dispose()
    }
}
