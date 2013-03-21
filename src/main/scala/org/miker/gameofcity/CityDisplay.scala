package org.miker.gameofcity

import javax.swing.JFrame
import java.awt.Graphics

/**
 * 
 * @author miker
 */
class CityDisplay extends JFrame {

  override def paint(g: Graphics) {
    g.drawLine(10, 10, 150, 150);
  }
}
