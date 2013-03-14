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
  }
}