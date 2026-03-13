package ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable

object GameSkin {

    fun create(): Skin {
        val skin = Skin()
        val font = BitmapFont()
        skin.add("default-font", font)

        fun drawable(color: Color): TextureRegionDrawable {
            val pm = Pixmap(1, 1, Pixmap.Format.RGBA8888)
            pm.setColor(color); pm.fill()
            val d = TextureRegionDrawable(TextureRegion(Texture(pm)))
            pm.dispose()
            return d
        }

        // Label styles
        skin.add("default", Label.LabelStyle(font, Color.WHITE))
        skin.add("dim",     Label.LabelStyle(font, Color.LIGHT_GRAY))
        skin.add("header",  Label.LabelStyle(font, Color.YELLOW))

        // Window
        val winStyle = Window.WindowStyle()
        winStyle.titleFont      = font
        winStyle.titleFontColor = Color.WHITE
        winStyle.background     = drawable(Color(0.08f, 0.08f, 0.12f, 0.93f))
        skin.add("default", winStyle)

        // TextButton (also used as radio-style brain selector)
        val btn = TextButton.TextButtonStyle()
        btn.font      = font
        btn.fontColor = Color.WHITE
        btn.up      = drawable(Color(0.25f, 0.25f, 0.35f, 1f))
        btn.over    = drawable(Color(0.35f, 0.35f, 0.50f, 1f))
        btn.down    = drawable(Color(0.15f, 0.15f, 0.25f, 1f))
        btn.checked = drawable(Color(0.20f, 0.50f, 0.80f, 1f))
        skin.add("default", btn)

        // ProgressBar (read-only needs display)
        val pb = ProgressBar.ProgressBarStyle()
        pb.background  = drawable(Color(0.25f, 0.25f, 0.25f, 1f)).also { it.minHeight = 12f }
        pb.knob        = drawable(Color(0f,    0f,    0f,    0f)).also { it.minWidth  = 0f  }
        pb.knobBefore  = drawable(Color(0.20f, 0.70f, 0.30f, 1f)).also { it.minHeight = 12f }
        skin.add("default-horizontal", pb)

        // Slider (editable needs)
        val sl = Slider.SliderStyle()
        sl.background = drawable(Color(0.20f, 0.20f, 0.20f, 1f)).also { it.minHeight = 8f }
        sl.knob       = drawable(Color(0.80f, 0.80f, 0.85f, 1f)).also { it.minWidth = 12f; it.minHeight = 20f }
        skin.add("default-horizontal", sl)

        return skin
    }
}
