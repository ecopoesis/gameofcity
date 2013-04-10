package org.miker.gameofcity.city

import scala.util.Random

/**
 *
 * @author miker
 */
class City {

  val size = 1000;
  private val grid = Array.ofDim[Node](size, size)

  def setup {
    for (x <- 0 until size; y <- 0 until size) {
      val rnd = Random.nextInt(40)
      if (rnd == 0) {
        grid(x)(y) = new Node(Commercial())
      } else if (rnd == 1) {
        grid(x)(y) = new Node(Industrial())
      } else if (rnd == 2) {
        grid(x)(y) = new Node(Residential())
      } else {
        grid(x)(y) = new Node(None())
      }
    }
  }

  def get(x: Int, y: Int) : Node = {
    return grid(x)(y)
  }
}
