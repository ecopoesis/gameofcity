package org.miker.gameofcity.display

import javax.swing.JPanel
import java.awt.{Graphics, Dimension}
import java.awt.image.BufferedImage
import org.miker.gameofcity.city._
import org.miker.gameofcity.city.Residential
import org.miker.gameofcity.city.Industrial
import org.miker.gameofcity.city.Commercial

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

        node.zone match {
          case Commercial() => image.setRGB(x, y, 0xFF6666FF)
          case Industrial() => image.setRGB(x, y, 0xFFFFFF00)
          case Residential() => image.setRGB(x, y, 0xFF00FF00)
          case None() => {
            if (node.people.size > 0) {
              image.setRGB(x, y, 0xFF000000)
            } else {
              image.setRGB(x, y, 0xFFFFFFFF)
            }
          }
        }
      }

      g.drawImage(image, 0, 0, city.size, city.size, this)
    }
  }
}
