package world

typealias PeepId = Int

enum class BuildingType {
    Residential,
    Commercial,
    Industrial,
    Entertainment
}

data class Building(
    val id: BuildingId,
    val type: BuildingType,
    val cells: Set<CellCoord>,
    val ownerId: PeepId? = null,
    val tenantIds: Set<PeepId> = emptySet(),
    val workerIds: Set<PeepId> = emptySet()
)
