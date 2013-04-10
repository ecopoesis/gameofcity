package org.miker.gameofcity

import java.util.{Date, Locale}
import java.text.DateFormat._
import org.miker.gameofcity.display.Display
import org.miker.gameofcity.city.City

/**
 * 
 * @author miker
 */
object HelloWorld {


  def main(a: Array[String]) {
    println("Starting the City")

    val city = new City
    city.setup

    val display = new Display

    while (true) {
      display.draw(city)
      Thread.sleep(250)
    }

  }
}