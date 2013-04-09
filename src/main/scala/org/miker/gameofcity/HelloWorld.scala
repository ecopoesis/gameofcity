package org.miker.gameofcity

import java.util.{Date, Locale}
import java.text.DateFormat._
import org.miker.gameofcity.display.Display

/**
 * 
 * @author miker
 */
object HelloWorld {


  def main(a: Array[String]) {
    println("Starting the City")

    val city = new City

    val display = new Display

    while (true) {
      city.mutate
      display.draw(city)
      Thread.sleep(250)
    }

  }
}