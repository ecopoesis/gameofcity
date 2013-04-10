package org.miker.gameofcity.city

/**
 *
 * @author miker
 */
abstract class Zone
case class Commercial() extends Zone
case class Industrial() extends Zone
case class Residential() extends Zone
case class None() extends Zone

