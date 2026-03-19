package transit

data class TrainRoute(
    val id: Int,
    val name: String,
    val stations: List<TrainStation>,
    val headwayTicks: Int = 200,
    val isSubway: Boolean = false
)
