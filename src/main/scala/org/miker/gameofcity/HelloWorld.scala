package org.miker.gameofcity

import java.util.{Date, Locale}
import java.text.DateFormat._
import org.miker.gameofcity.display.Display
import org.miker.gameofcity.city.City

object HelloWorld {

  def main(a: Array[String]) {
    println("Starting the City")

    val display = new Display

    val city = new City
    city.setup

    while (true) {
      display.draw(city)
      Thread.sleep(250)
    }

  }
}