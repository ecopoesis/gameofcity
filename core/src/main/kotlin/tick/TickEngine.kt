package tick

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import peep.Action
import peep.Demographics
import peep.Gender
import peep.Household
import peep.Peep
import peep.RelationshipTier
import peep.ScheduleType
import peep.UtilityBrain
import peep.WorldView
import gen.PeepSpawner
import transit.TransitSystem
import world.BuildingSubtype
import world.CellCoord
import world.Weather
import world.TravelMode
import world.WorldMap
import world.baseWage
import world.homeQuality
import kotlin.coroutines.CoroutineContext

class TickEngine(val map: WorldMap, private val parallelContext: CoroutineContext = Dispatchers.Default) {

    val peeps: MutableMap<Int, Peep> = mutableMapOf()
    val households: MutableMap<Int, Household> = mutableMapOf()
    private var nextHouseholdId: Int = 0
    private var nextPeepId: Int = 0
    var tick: Long = 0L
    val clock: SimClock = SimClock()

    // Demographic stats (reset daily)
    var birthsToday: Int = 0; private set
    var deathsToday: Int = 0; private set
    var immigrantsToday: Int = 0; private set
    var emigrantsToday: Int = 0; private set

    val eventLog: EventLog = EventLog()
    var stats: CityStats = CityStats(); private set
    val weather: Weather = Weather()
    val transit: TransitSystem = TransitSystem()

    private val worldView = object : WorldView {
        override val map: WorldMap get() = this@TickEngine.map
        override val peeps: Map<Int, Peep> get() = this@TickEngine.peeps
        override val tick: Long get() = this@TickEngine.tick
        override val clock: SimClock get() = this@TickEngine.clock
        override val weather: Weather get() = this@TickEngine.weather
        override val transit: TransitSystem get() = this@TickEngine.transit
    }

    fun addPeep(peep: Peep) {
        peeps[peep.id] = peep
        if (peep.id >= nextPeepId) nextPeepId = peep.id + 1
    }

    fun removePeep(peepId: Int) {
        val peep = peeps.remove(peepId) ?: return
        map.peepsAt[peep.position]?.remove(peepId)
        // Clean up household
        val hId = peep.householdId
        if (hId != null) {
            val household = households[hId]
            household?.removeMember(peepId)
            if (household != null && household.size <= 1) {
                // Dissolve single-member household
                household.members.firstOrNull()?.let { lastId ->
                    val last = peeps[lastId]
                    last?.money = (last?.money ?: 0f) + household.sharedMoney
                    last?.householdId = null
                }
                households.remove(hId)
            }
        }
        // Dissolve partnership
        peep.partnerId?.let { pid ->
            peeps[pid]?.partnerId = null
        }
        // Inherit money to partner or lose it
        peep.partnerId?.let { pid ->
            peeps[pid]?.let { partner -> partner.money += peep.money }
        }
        // Remove from friendships
        peep.friendships.keys.forEach { otherId ->
            peeps[otherId]?.friendships?.remove(peepId)
            peeps[otherId]?.interactionCount?.remove(peepId)
        }
    }

    fun step() {
        // Phase 1: Perceive (worldView is always fresh)

        // Phase 2: Decide (parallel when population is large enough)
        val actions = if (peeps.size > PARALLEL_THRESHOLD) {
            runBlocking(parallelContext) {
                peeps.values.map { peep ->
                    async { peep to peep.brain.decide(peep, worldView) }
                }.map { it.await() }
            }
        } else {
            peeps.values.map { peep -> peep to peep.brain.decide(peep, worldView) }
        }

        // Phase 3: Validate — reject actions targeting full buildings
        val validated = validate(actions)

        // Phase 4: Execute
        validated.forEach { (peep, action) ->
            peep.lastAction = action
            execute(peep, action)
        }

        // Phase 5: Maintain
        maintain()

        // Advance buses and update riding peep positions
        val busPositions = transit.advance()
        busPositions.forEach { (peepId, newPos) ->
            val peep = peeps[peepId] ?: return@forEach
            map.peepsAt[peep.position]?.remove(peepId)
            peep.position = newPos
            map.peepsAt.getOrPut(newPos) { mutableListOf() }.add(peepId)
        }

        weather.advance()
        clock.advance()
        tick++
    }

    private fun validate(actions: List<Pair<Peep, Action>>): List<Pair<Peep, Action>> {
        // Track how many peeps are trying to enter each building this tick
        val buildingDemand = mutableMapOf<Int, Int>()
        return actions.map { (peep, action) ->
            val buildingId = action.targetBuildingId()
            if (buildingId != null) {
                val building = map.buildings[buildingId]
                if (building != null && building.isFull) {
                    // Already at capacity — reject
                    peep to Action.Idle
                } else if (building != null) {
                    val demand = buildingDemand.getOrPut(buildingId) { building.currentOccupants.size }
                    if (demand >= building.capacity) {
                        peep to Action.Idle
                    } else {
                        buildingDemand[buildingId] = demand + 1
                        peep to action
                    }
                } else {
                    peep to action
                }
            } else {
                peep to action
            }
        }
    }

    private fun execute(peep: Peep, action: Action) {
        val n = peep.needs
        when (action) {
            is Action.MoveTo -> {
                if (map.isPassable(action.target)) {
                    map.peepsAt.getOrPut(peep.position) { mutableListOf() }.remove(peep.id)
                    peep.position = action.target
                    map.peepsAt.getOrPut(action.target) { mutableListOf() }.add(peep.id)
                }
            }
            is Action.Eat -> {
                if (peep.money >= 5f) {
                    n.hunger = (n.hunger - 0.5f).coerceAtLeast(0f)
                    n.thirst = (n.thirst - 0.2f).coerceAtLeast(0f)
                    peep.money -= 5f
                }
            }
            is Action.Drink -> {
                if (peep.money >= 3f) {
                    n.thirst = (n.thirst - 0.5f).coerceAtLeast(0f)
                    n.friendship = (n.friendship - 0.1f).coerceAtLeast(0f)
                    peep.money -= 3f
                }
            }
            is Action.Sleep -> {
                val atHome = peep.homeId != null && peep.homeId == action.buildingId
                val recovery = if (atHome) 0.5f else 0.2f  // reduced for homeless/park
                n.sleep = (n.sleep - recovery).coerceAtLeast(0f)
                n.warmth = (n.warmth - if (atHome) 0.3f else 0.1f).coerceAtLeast(0f)
                n.shelter = (n.shelter - if (atHome) 0.2f else 0.05f).coerceAtLeast(0f)
            }
            is Action.Work -> {
                n.recognition = (n.recognition - 0.1f).coerceAtLeast(0f)
                val building = map.buildings[action.buildingId]
                peep.money += (building?.wage ?: 1).toFloat()
            }
            is Action.Socialize -> {
                n.friendship = (n.friendship - 0.3f).coerceAtLeast(0f)
                n.community = (n.community - 0.1f).coerceAtLeast(0f)
                val other = peeps[action.targetPeepId]
                if (other != null) {
                    peep.friendships[other.id]  = (peep.friendships[other.id]  ?: 0f) + 0.05f
                    other.friendships[peep.id]  = (other.friendships[peep.id]  ?: 0f) + 0.05f
                    peep.interactionCount[other.id] = (peep.interactionCount[other.id] ?: 0) + 1
                    other.interactionCount[peep.id] = (other.interactionCount[peep.id] ?: 0) + 1
                }
            }
            is Action.Shop -> {
                if (peep.money >= 10f) {
                    n.accomplishment = (n.accomplishment - 0.2f).coerceAtLeast(0f)
                    n.status = (n.status - 0.1f).coerceAtLeast(0f)
                    peep.money -= 10f
                }
            }
            is Action.Heal -> {
                if (peep.money >= 20f) {
                    n.health = (n.health - 0.5f).coerceAtLeast(0f)
                    peep.money -= 20f
                }
            }
            is Action.Learn -> {
                n.learning = (n.learning - 0.3f).coerceAtLeast(0f)
                n.creativity = (n.creativity - 0.1f).coerceAtLeast(0f)
            }
            is Action.Exercise -> {
                n.health = (n.health - 0.2f).coerceAtLeast(0f)
                n.accomplishment = (n.accomplishment - 0.15f).coerceAtLeast(0f)
            }
            is Action.Relax -> {
                n.creativity = (n.creativity - 0.2f).coerceAtLeast(0f)
                n.community = (n.community - 0.15f).coerceAtLeast(0f)
                n.friendship = (n.friendship - 0.1f).coerceAtLeast(0f)
            }
            is Action.Watch -> {
                if (peep.money >= 8f) {
                    n.creativity = (n.creativity - 0.15f).coerceAtLeast(0f)
                    n.community = (n.community - 0.2f).coerceAtLeast(0f)
                    peep.money -= 8f
                }
            }
            is Action.ParkCar -> {
                peep.parkingSpot = action.spot
                peep.travelMode = TravelMode.Walk
                map.parkedVehicles[action.spot] = peep.id
            }
            is Action.RetrieveCar -> {
                peep.travelMode = TravelMode.Drive
                map.parkedVehicles.remove(action.spot)
                peep.parkingSpot = null
            }
            is Action.WaitForBus -> {
                // Try to board a bus at this stop
                val bus = transit.boardBus(peep.id, action.stopId)
                if (bus != null) {
                    peep.travelMode = TravelMode.Bus
                    peep.ridingBusId = bus.id
                }
            }
            is Action.RideBus -> {
                // Peep is riding — position updated by TransitSystem.advance()
            }
            is Action.Idle -> Unit
        }
    }

    private fun maintain() {
        val isNewDay = clock.isNewDay()
        peeps.values.forEach { peep ->
            val n = peep.needs
            // Level 1 - Physiological (fast decay)
            n.hunger  = (n.hunger  + 0.001f).coerceIn(0f, 1f)
            n.thirst  = (n.thirst  + 0.0012f * weather.current.thirstDecayMultiplier).coerceIn(0f, 1f)
            n.sleep   = (n.sleep   + 0.0005f).coerceIn(0f, 1f)
            n.warmth  = (n.warmth  + 0.0002f * weather.current.warmthDecayMultiplier).coerceIn(0f, 1f)

            // Level 2 - Safety (moderate decay)
            if (peep.homeId == null) {
                n.shelter = (n.shelter + 0.0003f).coerceIn(0f, 1f)
            }
            // Hospital radius: -50% health decay for peeps living near hospital
            val nearHospital = peep.homeId?.let { hid ->
                val home = map.buildings[hid]
                home != null && map.buildings.values.any { b ->
                    b.subtype == BuildingSubtype.Hospital &&
                    b.cells.any { c -> c.distanceTo(home.cells.first()) <= 10 }
                }
            } ?: false
            val healthDecay = if (nearHospital) 0.00005f else 0.0001f
            n.health = (n.health + healthDecay).coerceIn(0f, 1f)
            // financial: derived from money
            n.financial = when {
                peep.money >= 200f -> 0f
                peep.money <= 0f -> 1f
                else -> 1f - (peep.money / 200f)
            }

            // Level 3 - Love/Belonging (slow decay)
            n.friendship = (n.friendship + 0.0004f).coerceIn(0f, 1f)
            n.family     = (n.family     + 0.0002f).coerceIn(0f, 1f)
            n.community  = (n.community  + 0.0003f).coerceIn(0f, 1f)

            // Level 4 - Esteem (very slow decay)
            n.recognition    = (n.recognition    + 0.0002f).coerceIn(0f, 1f)
            n.accomplishment = (n.accomplishment + 0.0001f).coerceIn(0f, 1f)
            // status: derived from money + home + job
            val moneyScore = (peep.money / 500f).coerceIn(0f, 1f)
            val homeScore = if (peep.homeId != null) 0.3f else 0f
            val jobScore = if (peep.jobId != null) 0.3f else 0f
            n.status = (1f - (moneyScore + homeScore + jobScore).coerceIn(0f, 1f))

            // Level 5 - Self-Actualization (glacial decay)
            n.creativity = (n.creativity + 0.00005f).coerceIn(0f, 1f)
            n.learning   = (n.learning   + 0.00008f).coerceIn(0f, 1f)
            n.purpose    = (n.purpose    + 0.00005f).coerceIn(0f, 1f)

            // Homeless: shelter decays 3× faster
            if (peep.isHomeless) {
                n.shelter = (n.shelter + 0.0006f).coerceIn(0f, 1f) // extra 2× on top of base
            }
        }

        // Rent collection + eviction (daily)
        if (isNewDay) {
            peeps.values.forEach { peep ->
                if (peep.homeId != null) {
                    val home = map.buildings[peep.homeId!!]
                    val rentDue = home?.rent ?: 20
                    if (peep.money >= rentDue) {
                        peep.money -= rentDue.toFloat()
                        peep.rentGraceDays = 0
                    } else {
                        peep.rentGraceDays++
                        if (peep.rentGraceDays >= 3) {
                            eventLog.add(SimEvent(tick, clock.day, EventType.Eviction,
                                "${peep.name} was evicted", listOf(peep.id)))
                            peep.homeId = null  // evicted
                            peep.rentGraceDays = 0
                        }
                    }
                }
            }

            // Housing search: homeless peeps look for affordable housing
            val vacantHomes = map.buildings.values
                .filter { it.isResidential && !it.isFull }
                .sortedBy { it.rent }
            peeps.values.filter { it.isHomeless }.forEach { peep ->
                val affordable = vacantHomes.firstOrNull { b ->
                    b.rent <= peep.money / 5 && !b.isFull
                }
                if (affordable != null) {
                    peep.homeId = affordable.id
                }
            }

            // Upgrading/downgrading (weekly — every 7 days)
            if (clock.day % 7 == 0) {
                peeps.values.filter { !it.isHomeless }.forEach { peep ->
                    val home = map.buildings[peep.homeId!!] ?: return@forEach
                    val homeQuality = home.subtype?.homeQuality ?: 1

                    // Upgrade: can afford luxury and living in lower quality
                    if (homeQuality < 3 && peep.money > 500f) {
                        val upgrade = vacantHomes.firstOrNull { b ->
                            (b.subtype?.homeQuality ?: 0) > homeQuality &&
                            b.rent <= peep.money / 5 && !b.isFull
                        }
                        if (upgrade != null && (peep.id % 10 < 3)) { // ~30% chance
                            peep.homeId = upgrade.id
                        }
                    }

                    // Downgrade: spending >50% of daily income on rent
                    val dailyIncome = map.buildings[peep.jobId]?.wage?.toFloat() ?: 0f
                    if (dailyIncome > 0 && home.rent > dailyIncome * 0.5f) {
                        val cheaper = vacantHomes.firstOrNull { b ->
                            b.rent < home.rent && !b.isFull
                        }
                        if (cheaper != null && (peep.id % 5 == 0)) { // ~20% chance
                            peep.homeId = cheaper.id
                        }
                    }
                }
            }
        }

        // Wage adjustment (every sim-hour = 60 ticks)
        if (tick > 0 && tick % 60 == 0L) {
            map.buildings.values.filter { it.isWorkplace }.forEach { bld ->
                val workers = peeps.values.count { it.jobId == bld.id }
                val halfCap = bld.capacity / 2
                val oldWage = bld.wage
                if (workers < halfCap) {
                    bld.wage = (bld.wage + 1).coerceAtMost((bld.subtype?.baseWage ?: 10) * 2)
                } else if (workers > (bld.capacity * 0.9).toInt()) {
                    bld.wage = (bld.wage - 1).coerceAtLeast((bld.subtype?.baseWage ?: 1) / 2)
                }
                if (bld.wage != oldWage) {
                    val reason = if (bld.wage > oldWage) "understaffed" else "overstaffed"
                    eventLog.add(SimEvent(tick, clock.day, EventType.WageChange,
                        "${bld.subtype?.name ?: bld.type.name} raised wages to ${bld.wage} ($reason)"))
                }
            }
        }

        // Job switching (daily evaluation)
        if (clock.isNewDay()) {
            val hiringBuildings = map.buildings.values.filter { it.isHiring }.toList()
            peeps.values.forEach { peep ->
                if (peep.jobId != null) {
                    val currentJob = map.buildings[peep.jobId!!]
                    if (currentJob != null) {
                        // Look for better-paying job
                        val betterJob = hiringBuildings.firstOrNull { b ->
                            b.id != peep.jobId && b.wage > currentJob.wage * 1.3
                        }
                        if (betterJob != null && (tick % 5 == peep.id.toLong() % 5)) {
                            // ~20% chance (1 in 5 days)
                            eventLog.add(SimEvent(tick, clock.day, EventType.JobChange,
                                "${peep.name} quit ${currentJob.subtype?.name ?: "job"}, hired at ${betterJob.subtype?.name ?: "job"} (+${betterJob.wage - currentJob.wage} wage)",
                                listOf(peep.id)))
                            peep.jobId = betterJob.id
                        }
                    }
                } else {
                    // Unemployed — find a job
                    val nearestJob = hiringBuildings
                        .sortedBy { it.cells.first().distanceTo(peep.position) }
                        .firstOrNull()
                    if (nearestJob != null) {
                        peep.jobId = nearestJob.id
                    }
                }
            }
        }

        // Update building occupancy from peep positions
        map.buildings.values.forEach { it.currentOccupants.clear() }
        peeps.values.forEach { peep ->
            val cell = map.getCell(peep.position)
            val bldgId = cell?.buildingId
            if (bldgId != null) {
                map.buildings[bldgId]?.currentOccupants?.add(peep.id)
            }
        }

        // Proximity friendship: peeps sharing a cell grow closer slowly
        val occupancy = mutableMapOf<CellCoord, MutableList<Int>>()
        peeps.values.forEach { p -> occupancy.getOrPut(p.position) { mutableListOf() }.add(p.id) }
        occupancy.values.forEach { ids ->
            if (ids.size > 1) {
                for (i in ids.indices) for (j in i + 1 until ids.size) {
                    val a = peeps[ids[i]] ?: continue
                    val b = peeps[ids[j]] ?: continue
                    a.friendships[b.id] = (a.friendships[b.id] ?: 0f) + 0.0001f
                    b.friendships[a.id] = (b.friendships[a.id] ?: 0f) + 0.0001f
                    a.interactionCount[b.id] = (a.interactionCount[b.id] ?: 0) + 1
                    b.interactionCount[a.id] = (b.interactionCount[a.id] ?: 0) + 1
                }
            }
        }

        // Daily: friendship decay + romance checks
        if (isNewDay) {
            // Friendship decay based on tier
            peeps.values.forEach { peep ->
                val toRemove = mutableListOf<Int>()
                peep.friendships.forEach { (otherId, value) ->
                    val tier = RelationshipTier.fromFriendship(value)
                    val newVal = (value - tier.decayPerDay).coerceAtLeast(0f)
                    if (newVal <= 0.01f && tier == RelationshipTier.Acquaintance) {
                        toRemove.add(otherId)
                    } else {
                        peep.friendships[otherId] = newVal
                    }
                }
                toRemove.forEach { peep.friendships.remove(it) }
            }

            // Romance: unpartnered peeps with close friends may become partners
            peeps.values.filter { !it.isPartnered }.forEach { peep ->
                val candidate = peep.friendships.entries
                    .filter { (otherId, value) ->
                        value > 0.6f &&
                        (peep.interactionCount[otherId] ?: 0) >= 5 &&
                        peeps[otherId]?.isPartnered != true
                    }
                    .maxByOrNull { it.value }

                if (candidate != null && (tick + peep.id.toLong()) % 20 == 0L) { // ~5% daily
                    val other = peeps[candidate.key] ?: return@forEach
                    // Form partnership
                    eventLog.add(SimEvent(tick, clock.day, EventType.Partnership,
                        "${peep.name} and ${other.name} moved in together", listOf(peep.id, other.id)))
                    peep.partnerId = other.id
                    other.partnerId = peep.id
                    peep.friendships[other.id] = 0.85f
                    other.friendships[peep.id] = 0.85f

                    // Form household — move into better home
                    val hId = nextHouseholdId++
                    val household = Household(hId)
                    household.addMember(peep.id)
                    household.addMember(other.id)
                    household.sharedMoney = peep.money + other.money
                    peep.money = 0f
                    other.money = 0f

                    // Pick better home
                    val homeA = peep.homeId?.let { map.buildings[it] }
                    val homeB = other.homeId?.let { map.buildings[it] }
                    val bestHome = when {
                        homeA == null && homeB == null -> null
                        homeA == null -> homeB
                        homeB == null -> homeA
                        (homeA.subtype?.homeQuality ?: 0) >= (homeB.subtype?.homeQuality ?: 0) -> homeA
                        else -> homeB
                    }
                    household.homeId = bestHome?.id
                    peep.homeId = bestHome?.id
                    other.homeId = bestHome?.id
                    peep.householdId = hId
                    other.householdId = hId
                    households[hId] = household
                }
            }

            // Partnership dissolution: friendship drops below 0.5
            peeps.values.filter { it.isPartnered }.forEach { peep ->
                val partnerId = peep.partnerId ?: return@forEach
                val friendship = peep.friendships[partnerId] ?: 0f
                if (friendship < 0.5f) {
                    val partner = peeps[partnerId]
                    eventLog.add(SimEvent(tick, clock.day, EventType.Breakup,
                        "${peep.name} and ${partner?.name ?: "unknown"} broke up",
                        listOfNotNull(peep.id, partner?.id)))
                    // Dissolve household
                    val hId = peep.householdId
                    if (hId != null) {
                        val household = households[hId]
                        if (household != null) {
                            val splitMoney = household.sharedMoney / 2
                            peep.money += splitMoney
                            partner?.let { it.money += splitMoney }
                            households.remove(hId)
                        }
                    }
                    peep.partnerId = null
                    peep.householdId = null
                    partner?.partnerId = null
                    partner?.householdId = null
                    // One partner keeps home, other loses it
                    partner?.homeId = null
                }
            }

            // Pool household income
            households.values.forEach { household ->
                household.members.forEach { memberId ->
                    val peep = peeps[memberId] ?: return@forEach
                    household.sharedMoney += peep.money
                    peep.money = 0f
                }
                // Distribute allowance equally
                val allowance = household.sharedMoney / household.size
                household.members.forEach { memberId ->
                    peeps[memberId]?.money = allowance
                }
                household.sharedMoney = allowance * household.size
            }

            // === Demographics (daily) ===
            birthsToday = 0; deathsToday = 0; immigrantsToday = 0; emigrantsToday = 0

            // Aging: 1 sim-day ≈ 1 year of life
            peeps.values.forEach { peep ->
                peep.age++
                // Update schedule based on age
                peep.schedule = when {
                    peep.age < 6 -> ScheduleType.Retiree  // toddlers stay home
                    peep.age < 18 -> ScheduleType.Student
                    peep.age >= 65 -> ScheduleType.Retiree
                    else -> peep.schedule  // keep current schedule
                }
            }

            // Death
            val dead = mutableListOf<Pair<Int, String>>()
            peeps.values.forEach { peep ->
                val rate = Demographics.mortalityRate(peep.age, peep.needs.health)
                if (rate > 0f && (tick + peep.id.toLong()) % ((1f / rate).toInt().coerceAtLeast(1)) == 0L) {
                    dead.add(peep.id to "${peep.name} passed away at age ${peep.age}")
                }
            }
            dead.forEach { (id, desc) ->
                eventLog.add(SimEvent(tick, clock.day, EventType.Death, desc, listOf(id)))
                removePeep(id); deathsToday++
            }

            // Birth
            households.values.toList().forEach { household ->
                val members = household.members.mapNotNull { peeps[it] }
                val partners = members.filter { it.isPartnered }
                if (partners.size >= 2) {
                    val a = partners[0]; val b = partners[1]
                    val childrenInHousehold = members.count { it.isChild }
                    if (Demographics.canHaveChild(a, b, map, childrenInHousehold)) {
                        if ((tick + household.id.toLong()) % 33 == 0L) { // ~3% daily
                            val childId = nextPeepId++
                            val child = Peep(
                                id = childId,
                                name = NAMES[childId % NAMES.size],
                                age = 0,
                                gender = Gender.entries[childId % Gender.entries.size],
                                position = a.position,
                                homeId = household.homeId,
                                schedule = ScheduleType.Retiree, // babies stay home
                                householdId = household.id
                            )
                            addPeep(child)
                            household.addMember(childId)
                            eventLog.add(SimEvent(tick, clock.day, EventType.Birth,
                                "Baby ${child.name} born to ${a.name} and ${b.name}",
                                listOf(childId, a.id, b.id)))
                            birthsToday++
                        }
                    }
                }
            }

            // Immigration
            val unemployed = peeps.values.count { it.jobId == null && !it.isChild && !it.isRetired }
            val jobSlots = map.buildings.values.filter { it.isWorkplace }
                .sumOf { it.capacity - it.currentOccupants.size }
            val housingSlots = map.buildings.values.filter { it.isResidential }
                .sumOf { it.capacity - it.currentOccupants.size }
            if (Demographics.shouldImmigrate(unemployed, jobSlots, housingSlots)) {
                val count = (1 + (tick % 3)).toInt().coerceAtMost(3)
                repeat(count) {
                    val job = map.buildings.values.firstOrNull { it.isHiring }
                    val home = map.buildings.values.firstOrNull { it.isResidential && !it.isFull }
                    val pos = home?.cells?.firstOrNull() ?: map.buildings.values.firstOrNull()?.cells?.firstOrNull() ?: CellCoord(1, 1)
                    val id = nextPeepId++
                    val immigrantMoney = 150f
                    val vehicle = PeepSpawner.assignVehicle(immigrantMoney)
                    val parkingSpot = if (vehicle == world.VehicleType.Car) {
                        PeepSpawner.findInitialParking(pos, this)
                    } else null
                    val immigrant = Peep(
                        id = id,
                        name = NAMES[id % NAMES.size],
                        age = 20 + (id * 7 % 20),
                        gender = Gender.entries[id % Gender.entries.size],
                        position = pos,
                        homeId = home?.id,
                        jobId = job?.id,
                        money = immigrantMoney,
                        brain = UtilityBrain(),
                        schedule = ScheduleType.Worker,
                        vehicle = vehicle,
                        parkingSpot = parkingSpot
                    )
                    if (parkingSpot != null) {
                        map.parkedVehicles[parkingSpot] = id
                    }
                    addPeep(immigrant)
                    eventLog.add(SimEvent(tick, clock.day, EventType.Immigration,
                        "Newcomer ${immigrant.name} arrived in the city", listOf(id)))
                    immigrantsToday++
                }
            }

            // Emigration: peeps with 3+ critical needs for 5+ consecutive days
            peeps.values.forEach { peep ->
                if (Demographics.shouldEmigrate(peep)) {
                    peep.criticalDays++
                } else {
                    peep.criticalDays = 0
                }
            }
            val emigrants = peeps.values
                .filter { it.criticalDays >= 5 && (tick + it.id.toLong()) % 10 == 0L } // ~10% daily
                .map { it.id to it.name }
            emigrants.forEach { (id, name) ->
                eventLog.add(SimEvent(tick, clock.day, EventType.Emigration,
                    "$name left the city (unhappy)", listOf(id)))
                removePeep(id); emigrantsToday++
            }

            // Compute aggregate stats daily
            stats = CityStats.compute(this)
        }

        // === Crime (every tick) ===
        // Police station radius cache
        val policeZones = map.buildings.values
            .filter { it.subtype == BuildingSubtype.PoliceStation }
            .flatMap { b -> b.cells.map { it } }
        peeps.values.forEach { peep ->
            val n = peep.needs
            if (n.financial > 0.9f && n.community > 0.7f) {
                // Check police proximity (-80% crime chance)
                val nearPolice = policeZones.any { it.distanceTo(peep.position) <= 15 }
                val crimeChance = if (nearPolice) 0.004f else 0.02f
                if (Math.random() < crimeChance) {
                    // Theft: steal from random nearby peep
                    val victims = peeps.values.filter {
                        it.id != peep.id && it.position.distanceTo(peep.position) <= 1 && it.money > 0
                    }
                    val victim = victims.randomOrNull()
                    if (victim != null) {
                        val stolen = (victim.money * 0.1f).coerceAtMost(20f)
                        victim.money -= stolen
                        peep.money += stolen
                        // Nearby peeps feel unsafe
                        peeps.values.filter { it.position.distanceTo(peep.position) <= 5 }.forEach {
                            it.needs.shelter = (it.needs.shelter + 0.1f).coerceAtMost(1f)
                        }
                        eventLog.add(SimEvent(tick, clock.day, EventType.Crowding,
                            "${peep.name} committed theft against ${victim.name}",
                            listOf(peep.id, victim.id)))
                    }
                }
            }
        }

        // === Fire (daily check) ===
        if (clock.isNewDay()) {
            // Fire station zones
            val fireStationCells = map.buildings.values
                .filter { it.subtype == BuildingSubtype.FireStation }
                .flatMap { it.cells }

            // Random fire start: 0.1% per building per day
            map.buildings.values.filter { !it.isOnFire && !it.isDamaged }.forEach { bld ->
                if (Math.random() < 0.001) {
                    bld.isOnFire = true
                    val nearFireStation = fireStationCells.any { it.distanceTo(bld.cells.first()) <= 20 }
                    bld.fireTimer = if (nearFireStation) 3 else 10
                    eventLog.add(SimEvent(tick, clock.day, EventType.Crowding,
                        "Fire at ${bld.subtype?.name ?: bld.type.name}!"))
                }
            }
        }

        // Process active fires
        map.buildings.values.filter { it.isOnFire }.forEach { bld ->
            bld.fireTimer--
            // Evacuate occupants
            bld.currentOccupants.toList().forEach { peepId ->
                val p = peeps[peepId]
                if (p != null) {
                    // Flee to nearest passable adjacent cell
                    val pos = p.position
                    val adj = listOf(
                        CellCoord(pos.x + 1, pos.y), CellCoord(pos.x - 1, pos.y),
                        CellCoord(pos.x, pos.y + 1), CellCoord(pos.x, pos.y - 1)
                    )
                    val road = adj.firstOrNull { map.isPassable(it) }
                    if (road != null) {
                        map.peepsAt[pos]?.remove(p.id)
                        p.position = road
                        map.peepsAt.getOrPut(road) { mutableListOf() }.add(p.id)
                    }
                }
            }
            if (bld.fireTimer <= 0) {
                bld.isOnFire = false
                val nearFireStation = map.buildings.values
                    .filter { it.subtype == BuildingSubtype.FireStation }
                    .any { fs -> fs.cells.any { it.distanceTo(bld.cells.first()) <= 20 } }
                if (nearFireStation) {
                    // Contained — building survives but damaged
                    bld.isDamaged = true
                    bld.repairTimer = 50
                } else {
                    // Destroyed — evict all residents/workers
                    peeps.values.filter { it.homeId == bld.id }.forEach { it.homeId = null }
                    peeps.values.filter { it.jobId == bld.id }.forEach { it.jobId = null }
                    map.buildings.remove(bld.id)
                }
            }
        }

        // Repair damaged buildings
        map.buildings.values.filter { it.isDamaged }.forEach { bld ->
            bld.repairTimer--
            if (bld.repairTimer <= 0) {
                bld.isDamaged = false
            }
        }
    }

    companion object {
        private const val PARALLEL_THRESHOLD = 100
        const val TICKS_PER_DAY = 1440
        private val NAMES = listOf(
            "Alex", "Blake", "Casey", "Dana", "Ellis",
            "Finley", "Gray", "Harper", "Indigo", "Jules",
            "Kai", "Lane", "Morgan", "Nova", "Oakley",
            "Parker", "Quinn", "River", "Sage", "Taylor"
        )
    }
}
