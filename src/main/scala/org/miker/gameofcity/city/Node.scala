package org.miker.gameofcity.city

/**
 * a node is a point in the city
 * it knows its immutable coordinates, how it is zoned and what people are on it
 * @author miker
 */
case class Node(
  l: Location,
  zone: Option[Zone],
  people: Seq[Person]
)
