package rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3

class OrbitController(val camera: PerspectiveCamera) : InputAdapter() {

    var target    = Vector3(0f, 0f, 0f)
    var azimuth   = 225f   // horizontal rotation, degrees
    var elevation =  50f   // degrees above horizontal
    var distance  = 600f   // from target

    /** True if the last touch gesture was a drag (suppresses click). Reset on touchDown. */
    var dragged = false
        private set

    private var lastX = 0
    private var lastY = 0

    fun update(delta: Float) {
        // Arrow-key pan in the XZ plane
        val pan = 300f * delta
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT))  target.x -= pan
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) target.x += pan
        if (Gdx.input.isKeyPressed(Input.Keys.UP))    target.z -= pan
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN))  target.z += pan

        val az = azimuth   * MathUtils.degreesToRadians
        val el = elevation * MathUtils.degreesToRadians
        camera.position.set(
            target.x + distance * MathUtils.cos(el) * MathUtils.sin(az),
            target.y + distance * MathUtils.sin(el),
            target.z + distance * MathUtils.cos(el) * MathUtils.cos(az)
        )
        camera.lookAt(target)
        camera.up.set(Vector3.Y)
        camera.update()
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        lastX = screenX; lastY = screenY; dragged = false
        return false
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        azimuth   = (azimuth - (screenX - lastX) * 0.4f) % 360f
        elevation = MathUtils.clamp(elevation + (screenY - lastY) * 0.4f, 10f, 85f)
        lastX = screenX; lastY = screenY
        dragged = true
        return true
    }

    override fun scrolled(amountX: Float, amountY: Float): Boolean {
        distance = MathUtils.clamp(distance * (1f + amountY * 0.12f), 80f, 3000f)
        return true
    }
}
