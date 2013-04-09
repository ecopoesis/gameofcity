package org.miker.gameofcity.display

import javax.swing.JPanel
import org.miker.gameofcity.City
import java.awt.{Graphics, Dimension}
import java.awt.image.BufferedImage

/**
 *
 * @author miker
 */
class Pane(val city: City) extends JPanel {

  override def getPreferredSize : Dimension = city match {
    case null => super.getPreferredSize
    case _ => new Dimension(city.size, city.size)
  }

  override def paintComponent(g: Graphics) {
    super.paintComponent(g)

    if (city != null) {
      val image = new BufferedImage(city.size, city.size, BufferedImage.TYPE_INT_ARGB)
      for (x <- 0 until city.size; y <- 0 until city.size) {
        val node = city.get(x, y)
        if (node == 0) {
          image.setRGB(x, y, 0xFF000000)
        } else if (node == 1) {
          image.setRGB(x, y, 0xFFFF0000)
        } else if (node == 2) {
          image.setRGB(x, y, 0xFF00FF00)
        } else if (node == 3) {
          image.setRGB(x, y, 0xFF0000FF)
        } else if (node == 4) {
          image.setRGB(x, y, 0xFFFFFFFF)
        }
      }

      g.drawImage(image, 0, 0, city.size, city.size, this)
    }
  }
}
