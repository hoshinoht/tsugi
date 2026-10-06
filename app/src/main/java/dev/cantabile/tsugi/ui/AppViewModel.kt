package dev.cantabile.tsugi.ui

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.cantabile.tsugi.TsugiApplication
import dev.cantabile.tsugi.data.BusStop
import dev.cantabile.tsugi.data.Favourite
import dev.cantabile.tsugi.data.NearbyStop
import dev.cantabile.tsugi.data.StopArrivals
import dev.cantabile.tsugi.data.serviceOrder
import dev.cantabile.tsugi.data.toDomain
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

sealed interface StopsStatus {
    data object Loading : StopsStatus
    data object Ready : StopsStatus
    data class Failed(val message: String) : StopsStatus
}

sealed interface NearbyState {
    data object Idle : NearbyState
    data object NeedsPermission : NearbyState
    data object Locating : NearbyState
    data class Ready(val stops: List<NearbyStop>, val precise: Boolean) : NearbyState
    data class Failed(val message: String) : NearbyState
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as TsugiApplication).container

    val hasApiKey = c.api.hasKey
    val stops: StateFlow<List<BusStop>> = c.stops.stops
    val favourites: StateFlow<List<Favourite>> =
        c.favourites.favourites.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _stopsStatus = MutableStateFlow<StopsStatus>(StopsStatus.Loading)
    val stopsStatus = _stopsStatus.asStateFlow()

    private val _arrivals = MutableStateFlow<Map<String, StopArrivals>>(emptyMap())
    val arrivals = _arrivals.asStateFlow()

    private val _nearby = MutableStateFlow<NearbyState>(NearbyState.Idle)
    val nearby = _nearby.asStateFlow()

    private val _radiusM = MutableStateFlow(400)
    val radiusM = _radiusM.asStateFlow()
    private var lastLocation: Location? = null

    init {
        loadStops()
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
                    services = response?.services.orEmpty()
                        .map { it.toDomain() }
                        .sortedWith(compareBy(serviceOrder) { it.serviceNo }),
                    fetchedAt = Instant.now(),
                )
            },
            onFailure = { e ->
                (previous ?: StopArrivals()).copy(error = e.message ?: "Network error")
            },
        )
    }

    fun toggleFavourite(favourite: Favourite) {
        viewModelScope.launch { c.favourites.toggle(favourite) }
    }

    fun hasLocationPermission() = c.location.hasPermission()

    fun onPermissionDenied() {
        _nearby.value = NearbyState.NeedsPermission
    }

    fun locate() {
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
            recomputeNearby()
        }
    }

    fun setRadius(metres: Int) {
        _radiusM.value = metres
        recomputeNearby()
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
            stops = c.stops.nearby(location.latitude, location.longitude, _radiusM.value),
            precise = c.location.hasPreciseLocation(),
        )
    }
}
