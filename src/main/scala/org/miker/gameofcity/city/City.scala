package org.miker.gameofcity.city

import scala.util.Random
import org.miker.gameofcity.city.people.Person
import scala.collection.parallel.mutable
import scala.collection

/**
 *
 * @author miker
 */
class City {

  val size = 1000;
  private val grid = Array.ofDim[Node](size, size)
  private val people = new collection.mutable.DoubleLinkedList[Person]

  def setup {
    var personId = 0;

    for (x <- 0 until size; y <- 0 until size) {
      val rnd = Random.nextInt(40)
      if (rnd == 0) {
        grid(x)(y) = new Node(x, y, Commercial(), new collection.mutable.DoubleLinkedList[Person])
      } else if (rnd == 1) {
        grid(x)(y) = new Node(x, y, Industrial(), new collection.mutable.DoubleLinkedList[Person])
      } else if (rnd == 2) {
        grid(x)(y) = new Node(x, y, Residential(), new collection.mutable.DoubleLinkedList[Person])
      } else {
        val node = new Node(x, y, None(), new collection.mutable.DoubleLinkedList[Person])
        grid(x)(y) = node

        if (Random.nextInt(40) == 0) {
          val person = new Person(personId, node)
          personId += 1
          node.people = node.people :+ person

        }
      }
    }
  }

  def get(x: Int, y: Int) : Node = {
    return grid(x)(y)
  }
}
