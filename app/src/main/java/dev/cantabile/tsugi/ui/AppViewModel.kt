package dev.cantabile.tsugi.ui

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.cantabile.tsugi.TsugiApplication
import dev.cantabile.tsugi.data.AddressHit
import dev.cantabile.tsugi.data.BusStop
import dev.cantabile.tsugi.data.Favourite
import dev.cantabile.tsugi.data.NearbyStop
import dev.cantabile.tsugi.data.ServiceArrivals
import dev.cantabile.tsugi.data.StopArrivals
import dev.cantabile.tsugi.data.StopSort
import dev.cantabile.tsugi.data.ThemeMode
import dev.cantabile.tsugi.data.firstBusLabel
import dev.cantabile.tsugi.data.operatorName
import dev.cantabile.tsugi.data.TrainStatus
import dev.cantabile.tsugi.data.placeNameFrom
import dev.cantabile.tsugi.data.serviceOrder
import dev.cantabile.tsugi.data.toDomain
import dev.cantabile.tsugi.widget.refreshFavouritesWidget
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
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
    data class Ready(val stops: List<NearbyStop>, val precise: Boolean, val label: String? = null) : NearbyState
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
    val alertMinutes: StateFlow<Int> =
        c.settings.alertMinutes.stateIn(viewModelScope, SharingStarted.Eagerly, 2)

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

    /** Updates [here] quietly if location is already allowed; never prompts. */
    fun refreshHere() {
        if (!c.location.hasPermission()) return
        viewModelScope.launch { c.location.current()?.let { _here.value = it } }
    }
    private var locationLabel: String? = null

    val routes = c.routes.index

    init {
        loadStops()
        viewModelScope.launch { radiusM.drop(1).collect { recomputeNearby() } }
        // Routes list every service at a stop, including ones LTA's arrivals feed omits because
        // they aren't running now. Once loaded, fill those into data already on screen.
        viewModelScope.launch {
            runCatching { c.routes.ensureLoaded() }
            _arrivals.update { all -> all.mapValues { (code, a) -> a.copy(services = withScheduled(code, a.services)) } }
        }
        // Keep the home-screen widget in step with favourites (skips the initial load).
        viewModelScope.launch {
            c.favourites.favourites.drop(1).collect { runCatching { refreshFavouritesWidget(app) } }
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

    /**
     * Adds services that serve [code] but aren't in LTA's live feed (not running right now),
     * with today's first-bus time, and sorts everything by service number.
     */
    private fun withScheduled(code: String, live: List<ServiceArrivals>): List<ServiceArrivals> {
        val routes = c.routes.index.value?.byStop?.get(code).orEmpty()
        fun firstBus(service: String) = routes.firstOrNull { it.service == service }?.firstBusLabel()
        val present = live.map { it.serviceNo }.toSet()
        val missing = routes.distinctBy { it.service }.filter { it.service !in present }.map {
            ServiceArrivals(it.service, operatorName(it.operator), emptyList(), firstBus = it.firstBusLabel())
        }
        return (live.map { if (it.buses.isEmpty() && it.firstBus == null) it.copy(firstBus = firstBus(it.serviceNo)) else it } + missing)
            .sortedWith(compareBy(serviceOrder) { it.serviceNo })
    }

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
    }

    fun suggestPlaceName(code: String): String = c.stops[code]?.description?.let(::placeNameFrom) ?: code

    fun hasLocationPermission() = c.location.hasPermission()

    fun onPermissionDenied() {
        _nearby.value = NearbyState.NeedsPermission
    }

    suspend fun searchAddresses(query: String): List<AddressHit> =
        runCatching { c.oneMap.search(query) }.getOrDefault(emptyList())

    /** Shows Nearby around a searched address rather than the phone's location. */
    fun showNearbyAt(hit: AddressHit) {
        lastLocation = Location("onemap").apply {
            latitude = hit.lat
            longitude = hit.lng
        }
        locationLabel = hit.name
        recomputeNearby()
    }

    fun locate() {
        locationLabel = null
        if (!c.location.hasPermission()) {
            _nearby.value = NearbyState.NeedsPermission
            return
        }
        _nearby.value = NearbyState.Locating
        viewModelScope.launch {
            val location = c.location.current()
            if (location == null) {
                _nearby.value = NearbyState.Failed("Couldn't get your location. Is location turned on?")
                return@launch
            }
            lastLocation = location
            _here.value = location
            recomputeNearby()
        }
    }

    fun setRadius(metres: Int) {
        viewModelScope.launch { c.settings.setNearbyRadius(metres) }
    }

    private companion object {
        const val FRESH_SECONDS = 10L
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
        )
    }
}
