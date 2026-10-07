package dev.cantabile.tsugi.ui

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.cantabile.tsugi.TsugiApplication
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.cantabile.tsugi.data.AddressHit
import dev.cantabile.tsugi.data.MapPin
import dev.cantabile.tsugi.data.Bus
import dev.cantabile.tsugi.data.Crowding
import dev.cantabile.tsugi.data.LiftMaintenanceDto
import dev.cantabile.tsugi.data.NearbyStation
import dev.cantabile.tsugi.data.STATION_NEAR_STOP_M
import dev.cantabile.tsugi.data.STOPS_NEAR_STATION_M
import dev.cantabile.tsugi.data.Station
import dev.cantabile.tsugi.data.StationIndex
import dev.cantabile.tsugi.data.byStation
import dev.cantabile.tsugi.data.crowdLineOf
import dev.cantabile.tsugi.data.forecastByStation
import dev.cantabile.tsugi.data.BusOnRoute
import dev.cantabile.tsugi.data.BusStop
import dev.cantabile.tsugi.data.locate
import dev.cantabile.tsugi.data.distanceM
import dev.cantabile.tsugi.data.stopsAway
import dev.cantabile.tsugi.data.Favourite
import dev.cantabile.tsugi.data.inCardOrder
import dev.cantabile.tsugi.data.NearbyStop
import dev.cantabile.tsugi.data.ServiceArrivals
import dev.cantabile.tsugi.data.StopArrivals
import dev.cantabile.tsugi.data.StopSort
import dev.cantabile.tsugi.data.ThemeMode
import dev.cantabile.tsugi.data.SINGAPORE
import dev.cantabile.tsugi.data.withTimetable
import dev.cantabile.tsugi.data.TrainStatus
import dev.cantabile.tsugi.data.placeNameFrom
import dev.cantabile.tsugi.data.serviceOrder
import dev.cantabile.tsugi.data.toDomain
import dev.cantabile.tsugi.tracking.DisruptionWorker
import dev.cantabile.tsugi.widget.Shortcuts
import dev.cantabile.tsugi.widget.refreshFavouritesWidget
import kotlinx.coroutines.async
import dev.cantabile.tsugi.data.ageMillis
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZonedDateTime
import java.util.UUID

sealed interface StopsStatus {
    data object Loading : StopsStatus
    data object Ready : StopsStatus
    data class Failed(val message: String) : StopsStatus
}

sealed interface NearbyState {
    data object Idle : NearbyState
    data object NeedsPermission : NearbyState
    data object Locating : NearbyState
    /** [label] is set when showing stops around a searched address instead of your location. */
    data class Ready(
        val stops: List<NearbyStop>,
        val precise: Boolean,
        val label: String? = null,
        val stations: List<NearbyStation> = emptyList(),
        /** Where the stops are around: you, or the searched address. */
        val center: Pair<Double, Double>? = null,
        /** Shown from a recent cached fix while a fresh one is on its way. */
        val refining: Boolean = false,
    ) : NearbyState
    data class Failed(val message: String) : NearbyState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TsugiApplication).container

    val hasApiKey = c.api.hasKey
    val stops: StateFlow<List<BusStop>> = c.stops.stops
    val favourites: StateFlow<List<Favourite>> =
        c.favourites.favourites.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val stopSort: StateFlow<StopSort> =
        c.settings.stopSort.stateIn(viewModelScope, SharingStarted.Eagerly, StopSort.Soonest)
    val theme: StateFlow<ThemeMode> =
        c.settings.theme.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.System)
    val disruptionAlerts: StateFlow<Boolean> =
        c.settings.disruptionAlerts.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Turns background disruption checks on or off; the caller asks for notification permission first. */
    fun sendTestDisruption() {
        viewModelScope.launch { DisruptionWorker.sendTest(getApplication()) }
    }

    fun setDisruptionAlerts(on: Boolean) {
        viewModelScope.launch {
            c.settings.setDisruptionAlerts(on)
            // Forget the last disruption, so turning alerts back on later doesn't say "back to normal".
            if (!on) c.settings.setLastDisruptionKey("")
        }
        if (on) DisruptionWorker.schedule(getApplication()) else DisruptionWorker.cancel(getApplication())
    }

    val alertMinutes: StateFlow<Int> =
        c.settings.alertMinutes.stateIn(viewModelScope, SharingStarted.Eagerly, 2)

    val cardOrder: StateFlow<List<String>> =
        c.settings.cardOrder.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun saveCardOrder(ids: List<String>) {
        viewModelScope.launch { c.settings.setCardOrder(ids) }
    }

    val recentStops: StateFlow<List<String>> =
        c.settings.recentStops.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addRecentStop(code: String) {
        viewModelScope.launch { c.settings.addRecentStop(code) }
    }

    fun clearRecentStops() {
        viewModelScope.launch { c.settings.clearRecentStops() }
    }

    /** Bus services whose number starts with [query] (e.g. "12" → 12, 12e, 120…), from the route data. */
    fun searchServices(query: String): List<String> {
        val q = query.trim()
        if (q.isEmpty() || q.length > 5 || q.contains(' ')) return emptyList()
        val services = c.routes.index.value?.byService?.keys ?: return emptyList()
        return services.filter { it.startsWith(q, ignoreCase = true) }
            .sortedWith(compareBy<String> { !it.equals(q, ignoreCase = true) }.then(serviceOrder))
            .take(6)
    }

    fun setStopSort(sort: StopSort) {
        viewModelScope.launch { c.settings.setStopSort(sort) }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { c.settings.setTheme(mode) }
    }

    fun setAlertMinutes(minutes: Int) {
        viewModelScope.launch { c.settings.setAlertMinutes(minutes) }
    }

    /** False until favourites have been read from disk, so screens don't mistake "loading" for "empty". */
    val favouritesLoaded: StateFlow<Boolean> =
        c.favourites.favourites.map { true }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _stopsStatus = MutableStateFlow<StopsStatus>(StopsStatus.Loading)
    val stopsStatus = _stopsStatus.asStateFlow()

    private val _arrivals = MutableStateFlow<Map<String, StopArrivals>>(emptyMap())
    val arrivals = _arrivals.asStateFlow()

    private val _nearby = MutableStateFlow<NearbyState>(NearbyState.Idle)
    val nearby = _nearby.asStateFlow()

    private val _trainStatus = MutableStateFlow<TrainStatus?>(null)
    val trainStatus = _trainStatus.asStateFlow()

    /** The Nearby radius; the toggle on Nearby and the Settings screen share this saved value. */
    val radiusM: StateFlow<Int> = c.settings.nearbyRadius.stateIn(viewModelScope, SharingStarted.Eagerly, 200)
    private var lastLocation: Location? = null

    private val _here = MutableStateFlow<Location?>(null)
    /** Your last known position (not a searched address), used to pick "Next up". */
    val here: StateFlow<Location?> = _here.asStateFlow()

    /** Updates [here] quietly if location is already allowed; never prompts. Instant first, then fresh. */
    fun refreshHere() {
        if (!c.location.hasPermission()) return
        c.location.lastKnown()?.takeIf { ageMillis(it) < QUICK_FIX_MAX_AGE_MS }?.let { quick ->
            if (_here.value.let { it == null || it.elapsedRealtimeNanos < quick.elapsedRealtimeNanos }) _here.value = quick
        }
        viewModelScope.launch { c.location.current()?.let { _here.value = it } }
    }
    private var locationLabel: String? = null

    val routes = c.routes.index

    init {
        loadStops()
        // Warm up location at launch so Nearby and Next up have a position by the time you look.
        refreshHere()
        viewModelScope.launch { radiusM.drop(1).collect { recomputeNearby() } }
        // Routes list every service at a stop, including ones LTA's arrivals feed omits because
        // they aren't running now. Once loaded, fill those into data already on screen.
        viewModelScope.launch {
            runCatching { c.routes.ensureLoaded() }
            fillScheduled()
        }
        viewModelScope.launch {
            runCatching { c.serviceInfo.ensureLoaded() }
            fillScheduled()
        }
        viewModelScope.launch {
            runCatching { c.stations.ensureLoaded() }
            recomputeNearby()
        }
        viewModelScope.launch {
            if (c.settings.disruptionAlerts.first()) DisruptionWorker.schedule(app)
        }
        // Keep the home-screen widget in step with favourites (skips the initial load).
        viewModelScope.launch {
            c.favourites.favourites.drop(1).collect { runCatching { refreshFavouritesWidget(app) } }
        }
        // Long-press shortcuts follow your first saved stops and places.
        viewModelScope.launch {
            combine(c.favourites.favourites, c.settings.cardOrder, c.stops.stops) { favs, order, _ -> favs to order }
                .collect { (favs, order) -> Shortcuts.updateDynamic(app, inCardOrder(favs, order)) { c.stops[it]?.description } }
        }
    }

    fun loadStops() {
        _stopsStatus.value = StopsStatus.Loading
        viewModelScope.launch {
            _stopsStatus.value = runCatching { c.stops.ensureLoaded() }.fold(
                onSuccess = { StopsStatus.Ready },
                onFailure = { StopsStatus.Failed(it.message ?: "Couldn't load bus stops") },
            )
            if (lastLocation != null) recomputeNearby()
        }
    }

    fun stop(code: String): BusStop? = c.stops[code]

    fun search(query: String): List<BusStop> = c.stops.search(query)

    /**
     * Fetches stops in parallel; keeps the last good data if a call fails.
     * Skips stops fetched in the last few seconds (e.g. when switching screens) unless [force].
     */
    suspend fun refresh(codes: Collection<String>, force: Boolean = false) = coroutineScope {
        val cutoff = Instant.now().minusSeconds(FRESH_SECONDS)
        codes.distinct()
            .filter { it.isNotBlank() }
            .filter { force || _arrivals.value[it]?.fetchedAt?.isAfter(cutoff) != true }
            .map { code -> async { code to fetch(code) } }
            .awaitAll()
            .let { results -> _arrivals.update { it + results } }
    }

    private suspend fun fetch(code: String): StopArrivals {
        val previous = _arrivals.value[code]
        return runCatching { c.api.busArrival(code) }.fold(
            onSuccess = { response ->
                StopArrivals(
                    services = withScheduled(code, response?.services.orEmpty().map { it.toDomain() }),
                    fetchedAt = Instant.now(),
                )
            },
            onFailure = { e ->
                (previous ?: StopArrivals()).copy(error = e.message ?: "Network error")
            },
        )
    }

    /** Keeps the last known status if the call fails, so a flaky network doesn't hide a disruption. */
    suspend fun refreshTrains() {
        runCatching { c.api.trainServiceAlerts() }.getOrNull()?.let {
            _trainStatus.value = it.value.toDomain(Instant.now())
        }
    }

    /** Adds timetable details from the cached route and service data; see [withTimetable]. */
    private fun withScheduled(code: String, live: List<ServiceArrivals>): List<ServiceArrivals> =
        withTimetable(live, c.routes.index.value?.byStop?.get(code).orEmpty(), c.serviceInfo.index.value, ZonedDateTime.now(SINGAPORE))

    /** How many stops away [bus] is from [stopCode], from its reported position; null if unknown. */
    fun stopsAway(stopCode: String, serviceNo: String, bus: Bus): Int? =
        c.routes.index.value?.stopsAway(stopCode, serviceNo, bus) { code -> c.stops[code]?.let { it.lat to it.lng } }

    /** Where [bus] is on [serviceNo]'s route towards [stopCode], for the route screen; null if unknown. */
    fun locateBus(stopCode: String, serviceNo: String, bus: Bus): BusOnRoute? =
        c.routes.index.value?.locate(stopCode, serviceNo, bus) { code -> c.stops[code]?.let { it.lat to it.lng } }

    /** Metres from your last known position to [stopCode], for leave-now alerts; null if unknown. */
    fun walkMetres(stopCode: String): Int? {
        // A position from a while ago can say "leave now" far too late; better no walk than a wrong one.
        val here = _here.value?.takeIf { ageMillis(it) < WALK_FIX_MAX_AGE_MS } ?: return null
        val stop = c.stops[stopCode] ?: return null
        return distanceM(here.latitude, here.longitude, stop.lat, stop.lng)
    }

    // ---- MRT and LRT stations ----

    val stations: StateFlow<StationIndex?> = c.stations.index

    fun station(code: String): Station? = stations.value?.get(code)

    fun searchStations(query: String): List<Station> = stations.value?.search(query).orEmpty()

    /** Stations whose nearest exit is within [STATION_NEAR_STOP_M] of a bus stop: "the stop at Bugis MRT". */
    fun stationsNearStop(code: String): List<NearbyStation> {
        val stop = c.stops[code] ?: return emptyList()
        return stations.value?.nearby(stop.lat, stop.lng, STATION_NEAR_STOP_M).orEmpty()
    }

    /** A bus stop near a station, with the exit it's closest to. */
    data class StopAtStation(val stop: BusStop, val exit: String?, val distanceM: Int)

    /** Bus stops within [STOPS_NEAR_STATION_M] of a station's exits, closest first. */
    fun stopsNearStation(station: Station): List<StopAtStation> =
        c.stops.nearby(station.lat, station.lng, STOPS_NEAR_STATION_M + 400, limit = 80).mapNotNull { near ->
            val s = near.stop
            val exit = station.exits.minByOrNull { distanceM(s.lat, s.lng, it.lat, it.lng) }
            val d = minOf(exit?.let { distanceM(s.lat, s.lng, it.lat, it.lng) } ?: Int.MAX_VALUE, near.distanceM)
            StopAtStation(s, exit?.name?.takeIf { it.isNotBlank() }, d).takeIf { d <= STOPS_NEAR_STATION_M }
        }.sortedBy { it.distanceM }

    private val _crowding = MutableStateFlow<Map<String, Crowding>>(emptyMap())
    /** Crowding by canonical station code, for the lines fetched so far. */
    val crowding = _crowding.asStateFlow()
    private val crowdFetched = mutableMapOf<String, Instant>()

    /**
     * Fetches real-time and forecast crowding for [lines] (crowd-feed line codes, see [crowdLineOf]).
     * LTA updates real-time levels every 10 minutes, so lines fetched more recently are skipped.
     */
    suspend fun refreshCrowding(lines: Collection<String>) = coroutineScope {
        val now = Instant.now()
        lines.distinct()
            .filter { crowdFetched[it]?.isAfter(now.minusSeconds(CROWD_FRESH_SECONDS)) != true }
            .map { line ->
                async {
                    val realtime = runCatching { c.api.crowdRealTime(line) }.getOrNull() ?: return@async emptyMap()
                    val forecast = runCatching { c.api.crowdForecast(line) }.getOrNull().orEmpty()
                        .forecastByStation(ZonedDateTime.now(SINGAPORE))
                    crowdFetched[line] = now
                    realtime.byStation().mapValues { (code, level) -> Crowding(level, forecast[code].orEmpty()) }
                }
            }
            .awaitAll()
            .forEach { fresh -> _crowding.update { it + fresh } }
    }

    private val _lifts = MutableStateFlow<List<LiftMaintenanceDto>?>(null)
    /** Lifts under maintenance network-wide; null until fetched. */
    val lifts = _lifts.asStateFlow()
    private var liftsFetched: Instant? = null

    suspend fun refreshLifts() {
        if (liftsFetched?.isAfter(Instant.now().minusSeconds(CROWD_FRESH_SECONDS)) == true) return
        runCatching { c.api.liftMaintenance() }.getOrNull()?.let {
            _lifts.value = it
            liftsFetched = Instant.now()
        }
    }

    /** Saves [codes] as a new place called [name], e.g. a station's bus stops. */
    fun createPlace(name: String, codes: List<String>) {
        if (codes.isEmpty()) return
        viewModelScope.launch {
            c.favourites.update { it + Favourite.Place(UUID.randomUUID().toString(), name, codes.distinct()) }
        }
    }

    private fun fillScheduled() {
        _arrivals.update { all -> all.mapValues { (code, a) -> a.copy(services = withScheduled(code, a.services)) } }
    }

    /** Category, loop point and frequency of every service direction, once loaded. */
    val serviceInfo = c.serviceInfo.index

    /** Pins a shortcut to [code] on the home screen; false if the launcher can't. */
    fun pinStopShortcut(code: String): Boolean =
        Shortcuts.pinStop(getApplication(), code, c.stops[code]?.description ?: code)

    fun toggleFavourite(favourite: Favourite) {
        viewModelScope.launch { c.favourites.toggle(favourite) }
    }

    fun place(id: String): Favourite.Place? = favourites.value.firstOrNull { it is Favourite.Place && it.id == id } as Favourite.Place?

    /** Adds [code] to an existing place, or creates a new one called [newName]. */
    fun addToPlace(placeId: String?, code: String, newName: String = "") {
        viewModelScope.launch {
            c.favourites.update { list ->
                if (placeId == null) {
                    list + Favourite.Place(UUID.randomUUID().toString(), newName.ifBlank { suggestPlaceName(code) }, listOf(code))
                } else {
                    list.map { if (it is Favourite.Place && it.id == placeId && code !in it.stopCodes) it.copy(stopCodes = it.stopCodes + code) else it }
                }
            }
        }
    }

    /** Removes a stop from a place; an empty place is deleted. */
    fun removeFromPlace(placeId: String, code: String) {
        viewModelScope.launch {
            c.favourites.update { list ->
                list.mapNotNull {
                    if (it is Favourite.Place && it.id == placeId) {
                        it.copy(stopCodes = it.stopCodes - code).takeIf { p -> p.stopCodes.isNotEmpty() }
                    } else it
                }
            }
        }
    }

    fun renamePlace(placeId: String, name: String) {
        viewModelScope.launch {
            c.favourites.update { list -> list.map { if (it is Favourite.Place && it.id == placeId) it.copy(name = name) else it } }
        }
    }

    fun deletePlace(placeId: String) {
        viewModelScope.launch { c.favourites.update { list -> list.filterNot { it is Favourite.Place && it.id == placeId } } }
        // A pinned home-screen shortcut to it would otherwise open a missing place.
        Shortcuts.disable(getApplication(), "place:$placeId")
    }

    fun suggestPlaceName(code: String): String = c.stops[code]?.description?.let(::placeNameFrom) ?: code

    fun hasLocationPermission() = c.location.hasPermission()

    fun onPermissionDenied() {
        _nearby.value = NearbyState.NeedsPermission
    }

    private val mapCache = android.util.LruCache<String, ImageBitmap>(12)

    /** A cached OneMap static map, decoded for Compose; null if it can't be fetched. */
    suspend fun staticMap(lat: Double, lng: Double, zoom: Int, night: Boolean, pins: List<MapPin>): ImageBitmap? {
        val key = listOf(lat, lng, zoom, night, pins).toString()
        mapCache.get(key)?.let { return it }
        val bytes = runCatching { c.oneMap.staticMap(lat, lng, zoom, 512, 256, night, pins) }.getOrNull() ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()?.also { mapCache.put(key, it) }
    }

    /** The closest stop on the same road within 80 m: usually the one across the road. */
    fun acrossTheRoad(code: String): NearbyStop? {
        val stop = c.stops[code] ?: return null
        return c.stops.nearby(stop.lat, stop.lng, 80, limit = 6)
            .firstOrNull { it.stop.code != code && it.stop.road.equals(stop.road, ignoreCase = true) }
    }

    suspend fun searchAddresses(query: String): List<AddressHit> =
        runCatching { c.oneMap.search(query) }.getOrDefault(emptyList())

    /** Shows Nearby around a searched address rather than the phone's location. */
    fun showNearbyAt(hit: AddressHit) {
        locateJob?.cancel()
        refining = false
        lastLocation = Location("onemap").apply {
            latitude = hit.lat
            longitude = hit.lng
        }
        locationLabel = hit.name
        recomputeNearby()
    }

    private var locateJob: Job? = null
    private var refining = false

    /**
     * Shows stops around you straight away from the last fix the phone already has (if recent), then
     * refines with a fresh fix, which can take a few seconds. The list only jumps if you've moved.
     */
    fun locate() {
        locationLabel = null
        if (!c.location.hasPermission()) {
            _nearby.value = NearbyState.NeedsPermission
            return
        }
        val quick = listOfNotNull(_here.value, c.location.lastKnown())
            .filter { ageMillis(it) < QUICK_FIX_MAX_AGE_MS }
            .maxByOrNull { it.elapsedRealtimeNanos }
        refining = true
        if (quick != null) {
            lastLocation = quick
            recomputeNearby()
        } else {
            _nearby.value = NearbyState.Locating
        }
        locateJob?.cancel()
        locateJob = viewModelScope.launch {
            val fresh = c.location.current()
            refining = false
            if (locationLabel != null) return@launch // you picked an address meanwhile
            when {
                fresh == null && quick == null ->
                    _nearby.value = NearbyState.Failed("Couldn't get your location. Is location turned on?")
                fresh == null -> recomputeNearby()
                else -> {
                    _here.value = fresh
                    val moved = quick == null || distanceM(quick.latitude, quick.longitude, fresh.latitude, fresh.longitude) > MOVED_M
                    if (moved) lastLocation = fresh
                    recomputeNearby()
                }
            }
        }
    }

    fun setRadius(metres: Int) {
        viewModelScope.launch { c.settings.setNearbyRadius(metres) }
    }

    private companion object {
        /** A cached fix younger than this is shown at once while a fresh one is found. */
        const val QUICK_FIX_MAX_AGE_MS = 10 * 60_000L
        /** Leave-now only trusts a position this recent. */
        const val WALK_FIX_MAX_AGE_MS = 3 * 60_000L
        /** The fresh fix only reshuffles Nearby if you've moved further than this. */
        const val MOVED_M = 25
        const val FRESH_SECONDS = 10L
        const val CROWD_FRESH_SECONDS = 10 * 60L
    }

    private fun recomputeNearby() {
        val location = lastLocation ?: return
        if (_stopsStatus.value != StopsStatus.Ready) {
            _nearby.value = NearbyState.Locating
            return
        }
        _nearby.value = NearbyState.Ready(
            stops = c.stops.nearby(location.latitude, location.longitude, radiusM.value),
            precise = locationLabel != null || c.location.hasPreciseLocation(),
            label = locationLabel,
            // Stations are bigger than stops, so look a little further for them.
            stations = stations.value?.nearby(location.latitude, location.longitude, radiusM.value + 200).orEmpty().take(3),
            center = location.latitude to location.longitude,
            refining = refining && locationLabel == null,
        )
    }
}
