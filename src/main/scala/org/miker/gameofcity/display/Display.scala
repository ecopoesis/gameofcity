package org.miker.gameofcity.display

import javax.swing.JFrame
import java.awt.Component

import org.miker.gameofcity.city.City

class Display {

  // setup the frame
  val frame = new JFrame("Game of City")
  frame.setSize(1000, 1000)
  frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)

  // current component
  var component: Component = null

  def draw(city: City) {
    if (component != null) {
      frame.remove(component)
    }

    component = frame.add(new Pane(city))
    frame.pack
    frame.setVisible(true)
  }
}
