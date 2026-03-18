package transit

data class BusRoute(
    val id: Int,
    val name: String,
    val stops: List<BusStop>,
    val headwayTicks: Int = 300
)
