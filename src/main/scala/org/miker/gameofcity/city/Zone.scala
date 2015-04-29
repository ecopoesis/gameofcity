package org.miker.gameofcity.city

sealed trait Zone

object Zone {

  case object Commercial extends Zone

  case object Industrial extends Zone

  case object Residential extends Zone

}
