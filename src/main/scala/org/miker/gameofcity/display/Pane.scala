package org.miker.gameofcity.display

import javax.swing.JPanel
import java.awt.{Graphics, Dimension}
import java.awt.image.BufferedImage
import org.miker.gameofcity.city._

class Pane(val city: City) extends JPanel {

  override def getPreferredSize : Dimension = new Dimension(city.size, city.size)

  override def paintComponent(g: Graphics) {
    super.paintComponent(g)

    val image = new BufferedImage(city.size, city.size, BufferedImage.TYPE_INT_ARGB)

    for (x <- 0 until city.size; y <- 0 until city.size) {
      val node = city.get(x, y)

      node.zone match {
        case Some(Zone.Commercial) => image.setRGB(x, y, 0xFF6666FF)
        case Some(Zone.Industrial) => image.setRGB(x, y, 0xFFFFFF00)
        case Some(Zone.Residential) => image.setRGB(x, y, 0xFF00FF00)
        case None => {
          if (node.people.nonEmpty) {
            image.setRGB(x, y, 0xFF000000)
          } else {
            image.setRGB(x, y, 0xFFFFFFFF)
          }
        }
      }

      g.drawImage(image, 0, 0, city.size, city.size, this)
    }
  }
}
