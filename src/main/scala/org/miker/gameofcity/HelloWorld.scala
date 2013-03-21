package org.miker.gameofcity

import java.util.{Date, Locale}
import java.text.DateFormat._

/**
 * 
 * @author miker
 */
object HelloWorld {


  def main(a: Array[String]) {
    val now = new Date()
    val df = getDateInstance(LONG, Locale.FRANCE);
    println(df format now)

    val frame = new CityDisplay
    frame.setSize(1000, 1000)
    frame.setVisible(true)
  }
}