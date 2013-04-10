package org.miker.gameofcity.city.people

import org.miker.gameofcity.city.Node

/**
 * representation of a person
 * people know what node they're in, this could change each turn
 * @author miker
 */
class Person(val id: Int, var node: Node) {
}
