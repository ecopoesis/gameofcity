package org.miker.gameofcity.city

import scala.collection.parallel.mutable
import org.miker.gameofcity.city.people.Person
import scala.collection

/**
 * a node is a point in the city
 * it knows its immutable coordinates, how it is zoned and what people are on it
 * @author miker
 */
class Node(val x: Int, val y: Int, var zone: Zone, var people: collection.mutable.DoubleLinkedList[Person]) {

}
