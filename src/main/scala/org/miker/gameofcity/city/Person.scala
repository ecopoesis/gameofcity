package org.miker.gameofcity.city

import java.util.UUID

/**
 * representation of a person
 * people know where they are, this can change each turn
 * @author miker
 */
case class Person(var l: Location) {
  val id: UUID = UUID.randomUUID
  var money: BigDecimal = 0
  var happiness: BigDecimal = 0
  var action: Option[Action] = None
}

sealed trait Action
case class Traveling(destination: Node) extends Action