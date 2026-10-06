package dev.cantabile.tsugi.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

class LtaApi(
    private val http: OkHttpClient,
    private val json: Json,
    private val accountKey: String,
) {
    val hasKey: Boolean get() = accountKey.isNotBlank()

    /** Returns null when LTA sends an empty body (no buses on the road, or maintenance). */
    suspend fun busArrival(stopCode: String): BusArrivalResponse? =
        get("v3/BusArrival?BusStopCode=$stopCode")

    /** One page of up to 500 route stops (every stop on every service). */
    suspend fun busRoutes(skip: Int): List<BusRouteDto> =
        get<BusRoutesResponse>("BusRoutes?\$skip=$skip")?.value.orEmpty()

    /** One page of up to 500 service directions: category, loop point and frequency. */
    suspend fun busServices(skip: Int): List<BusServiceDto> =
        get<BusServicesResponse>("BusServices?\$skip=$skip")?.value.orEmpty()

    /** Line status and disruption alerts for the whole MRT/LRT network. */
    suspend fun trainServiceAlerts(): TrainAlertsResponse? = get("TrainServiceAlerts")

    /** Crowding at every station on [line] (e.g. "NSL", "CGL", "SLRT") for the last 10 minutes. */
    suspend fun crowdRealTime(line: String): List<CrowdRealTimeDto> =
        get<CrowdRealTimeResponse>("PCDRealTime?TrainLine=$line")?.value.orEmpty()

    /** Forecast crowding at every station on [line], in half-hours, for today. */
    suspend fun crowdForecast(line: String): List<CrowdForecastDayDto> =
        get<CrowdForecastResponse>("PCDForecast?TrainLine=$line")?.value.orEmpty()

    /** Lifts under maintenance across the network. */
    suspend fun liftMaintenance(): List<LiftMaintenanceDto> =
        get<LiftMaintenanceResponse>("v2/FacilitiesMaintenance")?.value.orEmpty()

    /** One page of up to 500 stops. */
    suspend fun busStops(skip: Int): List<BusStopDto> =
        get<BusStopsResponse>("BusStops?\$skip=$skip")?.value.orEmpty()

    private suspend inline fun <reified T> get(path: String): T? = withContext(Dispatchers.IO) {
        if (!hasKey) throw IOException("No LTA AccountKey set")
        val request = Request.Builder()
            .url(BASE_URL + path)
            .header("AccountKey", accountKey)
            .header("Accept", "application/json")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("LTA returned HTTP ${response.code}")
            val body = response.body.string()
            if (body.isBlank()) null else json.decodeFromString<T>(body)
        }
    }

    private companion object {
        const val BASE_URL = "https://datamall2.mytransport.sg/ltaodataservice/"
    }
}
