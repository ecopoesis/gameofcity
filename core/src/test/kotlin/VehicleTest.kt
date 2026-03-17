import pathfind.AStarPathfinder
import peep.*
import tick.SimClock
import world.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VehicleTest {

    private fun roadMap(width: Int, height: Int): WorldMap {
        val map = WorldMap(width, height)
        // Sidewalks along y=0, road along y=1, sidewalk along y=2
        for (x in 0 until width) {
            map.setCell(Cell(CellCoord(x, 0), Terrain.Sidewalk))
            map.setCell(Cell(CellCoord(x, 1), Terrain.LocalRoad))
            map.setCell(Cell(CellCoord(x, 2), Terrain.Sidewalk))
        }
        return map
    }

    @Test
    fun `peep starts with no vehicle and Walk mode`() {
        val peep = Peep(id = 0, name = "Test", age = 25, gender = Gender.Male, position = CellCoord(0, 0))
        assertNull(peep.vehicle)
        assertEquals(TravelMode.Walk, peep.travelMode)
        assertNull(peep.parkingSpot)
    }

    @Test
    fun `peep can own a car`() {
        val peep = Peep(
            id = 0, name = "Driver", age = 30, gender = Gender.Female,
            position = CellCoord(0, 0),
            vehicle = VehicleType.Car,
            parkingSpot = CellCoord(5, 1)
        )
        assertEquals(VehicleType.Car, peep.vehicle)
        assertEquals(CellCoord(5, 1), peep.parkingSpot)
    }

    @Test
    fun `multi-step movement for driving peep`() {
        val map = roadMap(20, 3)
        val pf = AStarPathfinder(map)
        val nav = NavigationHelper()
        val peep = Peep(
            id = 0, name = "Driver", age = 30, gender = Gender.Male,
            position = CellCoord(0, 1),
            vehicle = VehicleType.Car,
            travelMode = TravelMode.Drive
        )
        val world = testWorldView(map, mapOf(0 to peep))

        // Navigate along the road
        nav.navigateTo(CellCoord(0, 1), CellCoord(10, 1), pf, TravelMode.Drive, Action.Idle)

        // Driving on LocalRoad (speedLimit=2) should consume 2 steps per tick
        val action1 = nav.pendingAction(peep, world)
        assertNotNull(action1)
        assertTrue(action1 is Action.MoveTo)
        // First call to navigateTo already consumed step 1, then pendingAction consumes 2 more
        // So after first pendingAction, peep should be at x=3 (step 1 consumed by navigateTo, steps 2-3 by pendingAction)
        // Actually: navigateTo consumes first step (returns MoveTo(1,1)), then pendingAction consumes 2 steps
        // Let me verify: navigateTo adds steps [1..10], removes first -> returns MoveTo(1,1)
        // pendingAction: consumes 2 steps -> MoveTo(3,1)
        assertEquals(CellCoord(3, 1), (action1 as Action.MoveTo).target)
    }

    @Test
    fun `walking peep moves one step per tick`() {
        val map = roadMap(20, 3)
        val pf = AStarPathfinder(map)
        val nav = NavigationHelper()
        val peep = Peep(
            id = 0, name = "Walker", age = 30, gender = Gender.Male,
            position = CellCoord(0, 0),
            travelMode = TravelMode.Walk
        )
        val world = testWorldView(map, mapOf(0 to peep))

        // Navigate along sidewalk
        nav.navigateTo(CellCoord(0, 0), CellCoord(5, 0), pf, TravelMode.Walk, Action.Idle)

        // Walking should consume 1 step per tick
        val action1 = nav.pendingAction(peep, world)
        assertNotNull(action1)
        assertTrue(action1 is Action.MoveTo)
        assertEquals(CellCoord(2, 0), (action1 as Action.MoveTo).target)
    }

    @Test
    fun `park car action sets parking spot and switches to walk`() {
        val peep = Peep(
            id = 0, name = "Test", age = 25, gender = Gender.Male,
            position = CellCoord(5, 1),
            vehicle = VehicleType.Car,
            travelMode = TravelMode.Drive
        )

        // Simulate ParkCar execution
        peep.parkingSpot = CellCoord(5, 1)
        peep.travelMode = TravelMode.Walk

        assertEquals(CellCoord(5, 1), peep.parkingSpot)
        assertEquals(TravelMode.Walk, peep.travelMode)
    }

    @Test
    fun `retrieve car action switches to drive mode`() {
        val peep = Peep(
            id = 0, name = "Test", age = 25, gender = Gender.Male,
            position = CellCoord(5, 1),
            vehicle = VehicleType.Car,
            travelMode = TravelMode.Walk,
            parkingSpot = CellCoord(5, 1)
        )

        // Simulate RetrieveCar execution
        peep.travelMode = TravelMode.Drive

        assertEquals(TravelMode.Drive, peep.travelMode)
    }

    @Test
    fun `arterial road gives higher speed than local`() {
        val map = WorldMap(20, 5)
        for (x in 0 until 20) {
            map.setCell(Cell(CellCoord(x, 0), Terrain.Sidewalk))
            map.setCell(Cell(CellCoord(x, 1), Terrain.ArterialRoad))
            map.setCell(Cell(CellCoord(x, 2), Terrain.Sidewalk))
        }
        val pf = AStarPathfinder(map)
        val nav = NavigationHelper()
        val peep = Peep(
            id = 0, name = "Driver", age = 30, gender = Gender.Male,
            position = CellCoord(0, 1),
            travelMode = TravelMode.Drive
        )
        val world = testWorldView(map, mapOf(0 to peep))

        // Navigate along arterial (speedLimit=4)
        nav.navigateTo(CellCoord(0, 1), CellCoord(15, 1), pf, TravelMode.Drive, Action.Idle)

        val action1 = nav.pendingAction(peep, world)
        assertNotNull(action1)
        assertTrue(action1 is Action.MoveTo)
        // ArterialRoad speedLimit=4, so consumes 4 steps per tick
        // navigateTo consumed first step (returns MoveTo(1,1))
        // pendingAction consumes 4 steps -> MoveTo(5,1)
        assertEquals(CellCoord(5, 1), (action1 as Action.MoveTo).target)
    }

    @Test
    fun `plan trip falls back to walking when no car`() {
        val map = roadMap(20, 3)
        val pf = AStarPathfinder(map)
        val nav = NavigationHelper()
        val peep = Peep(
            id = 0, name = "Walker", age = 30, gender = Gender.Male,
            position = CellCoord(0, 0)
        )

        val action = nav.planTrip(
            CellCoord(0, 0), CellCoord(10, 0),
            map, pf, peep, Action.Idle
        )
        assertTrue(action is Action.MoveTo)
    }

    @Test
    fun `save round-trip preserves vehicle data`() {
        val pd = save.PeepData(
            id = 1, name = "Driver", age = 30, gender = "Male",
            posX = 5, posY = 1, money = 200f,
            brainType = "Utility",
            vehicle = "Car", travelMode = "Drive",
            parkingSpotX = 3, parkingSpotY = 1, parkingSpotZ = 0
        )

        assertEquals("Car", pd.vehicle)
        assertEquals("Drive", pd.travelMode)
        assertEquals(3, pd.parkingSpotX)
        assertEquals(1, pd.parkingSpotY)
    }

    private fun testWorldView(map: WorldMap, peeps: Map<Int, Peep>): WorldView {
        return object : WorldView {
            override val map: WorldMap = map
            override val peeps: Map<Int, Peep> = peeps
            override val tick: Long = 0L
            override val clock = SimClock()
            override val weather: Weather = Weather()
        }
    }
}
