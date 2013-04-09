package org.miker.gameofcity

import scala.util.Random

/**
 *
 * @author miker
 */
class City {

  val size = 1000;
  private val grid = Array.ofDim[Int](size, size)

  def mutate {
    for (x <- 0 until size; y <- 0 until size) {
      grid(x)(y) = Random.nextInt(5)
    }
  }

  def get(x: Int, y: Int) : Int = {
    return grid(x)(y)
  }
}
