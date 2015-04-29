package org.miker.gameofcity.city

import scala.util.Random

class City {

  val size = 1000
  private val grid = Array.ofDim[Node](size, size)
  private val people = Seq.empty[Person]

  def setup = {
    for (x <- 0 until size; y <- 0 until size) {
      val l = Location(x, y)
      Random.nextInt(40) match {
        case 0 => grid(x)(y) = Node(l, Some(Zone.Commercial), Seq.empty[Person])
        case 1 => grid(x)(y) = Node(l, Some(Zone.Industrial), Seq.empty[Person])
        case 2 => grid(x)(y) = Node(l, Some(Zone.Residential), Seq.empty[Person])
        case _ => Random.nextInt(40) match {
          case 0 => grid(x)(y) = Node(l, None, Seq(Person(l)))
          case _ => grid(x)(y) = Node(l, None, Seq.empty[Person])
        }
      }
    }
  }

  def get(x: Int, y: Int) : Node = {
    grid(x)(y)
  }
}
