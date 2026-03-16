package peep

import world.BuildingId
import world.PeepId

data class Household(
    val id: Int,
    val members: MutableSet<PeepId> = mutableSetOf(),
    var homeId: BuildingId? = null,
    var sharedMoney: Float = 0f
) {
    fun addMember(peepId: PeepId) { members.add(peepId) }
    fun removeMember(peepId: PeepId) { members.remove(peepId) }
    val size: Int get() = members.size
}

enum class RelationshipTier(val minFriendship: Float, val decayPerDay: Float) {
    Acquaintance(0.0f, 0.01f),
    Friend(0.3f, 0.005f),
    CloseFriend(0.6f, 0.002f),
    Partner(0.8f, 0.001f);

    companion object {
        fun fromFriendship(value: Float): RelationshipTier = when {
            value >= Partner.minFriendship -> Partner
            value >= CloseFriend.minFriendship -> CloseFriend
            value >= Friend.minFriendship -> Friend
            else -> Acquaintance
        }
    }
}
