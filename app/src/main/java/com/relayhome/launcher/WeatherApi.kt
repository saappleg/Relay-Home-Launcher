package com.relayhome.launcher

import com.relayhome.launcher.data.RelaySettingsRepository
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class WeatherLocation(val name: String, val latitude: Double, val longitude: Double)

internal data class WeatherCurrent(
    val locationName: String,
    val temperatureCelsius: Double,
    val weatherCode: Int
)

internal open class WeatherApiException(message: String, cause: Throwable? = null) : IOException(message, cause)

internal class WeatherNotConfiguredException : WeatherApiException(
    "Local weather is not configured. Add a city in Settings to show weather on Home."
)

internal class WeatherInvalidLocationException(message: String) : WeatherApiException(message)

internal class WeatherHttpException(val statusCode: Int) : WeatherApiException(
    "Weather lookup failed (HTTP $statusCode)."
)

internal class WeatherTransientException(
    val operation: String,
    val attempts: Int,
    val statusCode: Int? = null,
    cause: Throwable? = null
) : WeatherApiException(
    buildString {
        append("Weather ").append(operation).append(" is temporarily unavailable after ")
        append(attempts).append(if (attempts == 1) " attempt" else " attempts")
        statusCode?.let { append(" (HTTP ").append(it).append(")") }
        append(".")
    },
    cause
)

internal data class WeatherHttpResponse(val statusCode: Int, val body: String)

internal fun interface WeatherTransport {
    fun get(url: String): WeatherHttpResponse
}

/** Synchronous facade for the manual city setting; blank intentionally hides weather. */
internal object WeatherCitySettings {
    fun load(context: android.content.Context): String = RelaySettingsRepository.loadWeatherCity(context)

    fun save(context: android.content.Context, city: String) {
        RelaySettingsRepository.saveWeatherCity(context, WeatherApi.normalizeCity(city))
    }
}

/** Keyless Open-Meteo client with bounded retry and a Home-friendly memory cache. */
internal object WeatherApi {
    private const val geocodingBaseUrl = "https://geocoding-api.open-meteo.com/v1/search"
    private const val forecastBaseUrl = "https://api.open-meteo.com/v1/forecast"
    private const val maxAttempts = 3
    private const val cacheTtlMs = 15 * 60 * 1000L

    private data class CachedWeather(val value: WeatherCurrent, val storedAtMs: Long)

    private val cache = ConcurrentHashMap<String, CachedWeather>()
    private val defaultTransport = WeatherTransport { url -> HttpTransport.get(url) }

    suspend fun current(city: String): Result<WeatherCurrent> {
        val normalized = normalizeCity(city)
        if (normalized.isBlank()) return Result.failure(WeatherNotConfiguredException())
        val now = System.currentTimeMillis()
        cache[normalized]?.takeIf { now - it.storedAtMs < cacheTtlMs }?.let {
            return Result.success(it.value)
        }
        val result = fetchCurrent(normalized, defaultTransport)
        result.getOrNull()?.let { cache[normalized] = CachedWeather(it, System.currentTimeMillis()) }
        return result
    }

    /** Injectable entry point for hermetic tests; it never calls the real service. */
    internal suspend fun fetchCurrent(
        city: String,
        transport: WeatherTransport,
        sleeper: suspend (Long) -> Unit = { delay(it) }
    ): Result<WeatherCurrent> = withContext(Dispatchers.IO) {
        val normalized = normalizeCity(city)
        if (normalized.isBlank()) return@withContext Result.failure(WeatherNotConfiguredException())
        runCatching {
            val locationUrl = "$geocodingBaseUrl?name=${encode(normalized)}&count=1&language=en&format=json"
            val location = parseLocation(request("geocoding", locationUrl, transport, sleeper))
            val forecastUrl = "$forecastBaseUrl?latitude=${location.latitude}&longitude=${location.longitude}" +
                "&current=temperature_2m,weather_code&temperature_unit=celsius&timezone=auto"
            val current = parseCurrent(request("forecast", forecastUrl, transport, sleeper))
            WeatherCurrent(location.name, current.first, current.second)
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { error ->
                Result.failure(if (error is WeatherApiException) error else WeatherApiException("Weather returned an invalid response.", error))
            }
        )
    }

    internal fun normalizeCity(city: String): String = city
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(80)
        .trim()

    internal fun parseLocation(body: String): WeatherLocation {
        val results = JSONObject(body).optJSONArray("results")
            ?: throw WeatherInvalidLocationException("Weather geocoding returned no results.")
        val result = results.optJSONObject(0)
            ?: throw WeatherInvalidLocationException("That city could not be found.")
        val name = result.optString("name").trim()
        val latitude = result.optDouble("latitude", Double.NaN)
        val longitude = result.optDouble("longitude", Double.NaN)
        if (name.isBlank() || !latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0
        ) throw WeatherInvalidLocationException("Weather geocoding returned an invalid location.")
        return WeatherLocation(name, latitude, longitude)
    }

    internal fun parseCurrent(body: String): Pair<Double, Int> {
        val current = JSONObject(body).optJSONObject("current")
            ?: throw WeatherInvalidLocationException("Weather returned no current conditions.")
        val temperature = current.optDouble("temperature_2m", Double.NaN)
        val code = current.optInt("weather_code", -1)
        if (!temperature.isFinite() || code !in 0..99) {
            throw WeatherInvalidLocationException("Weather returned malformed current conditions.")
        }
        return temperature to code
    }

    internal fun isTransientStatus(statusCode: Int): Boolean =
        statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode in 500..599

    internal fun weatherIcon(code: Int): String = when (code) {
        0 -> "☀"
        1, 2 -> "⛅"
        3 -> "☁"
        45, 48 -> "≋"
        in 51..57, in 80..82 -> "☂"
        in 61..67 -> "🌧"
        in 71..77, 85, 86 -> "❄"
        in 95..99 -> "⚡"
        else -> "•"
    }

    internal fun weatherDescription(code: Int): String = when (code) {
        0 -> "Clear sky"
        1, 2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Foggy"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77, 85, 86 -> "Snow"
        in 80..82 -> "Rain showers"
        in 95..99 -> "Thunderstorm"
        else -> "Current weather"
    }

    internal fun clearCacheForTesting() = cache.clear()

    private suspend fun request(
        operation: String,
        url: String,
        transport: WeatherTransport,
        sleeper: suspend (Long) -> Unit
    ): String {
        var attempt = 0
        var lastTransient: Throwable? = null
        while (attempt < maxAttempts) {
            attempt += 1
            try {
                val response = transport.get(url)
                if (response.statusCode in 200..299) return response.body
                if (!isTransientStatus(response.statusCode)) throw WeatherHttpException(response.statusCode)
                lastTransient = WeatherTransientException(operation, attempt, response.statusCode)
            } catch (known: WeatherApiException) {
                if (known !is WeatherTransientException) throw known
                lastTransient = known
            } catch (network: IOException) {
                if (!network.isTransientNetwork()) throw WeatherApiException("Weather network request failed.", network)
                lastTransient = network
            }
            if (attempt < maxAttempts) sleeper(250L * (1L shl (attempt - 1)))
        }
        throw (lastTransient as? WeatherTransientException)
            ?: WeatherTransientException(operation, maxAttempts, cause = lastTransient)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private object HttpTransport {
        fun get(url: String): WeatherHttpResponse {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "RelayHome/1.0")
            }
            return try {
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                WeatherHttpResponse(status, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
            } finally {
                connection.disconnect()
            }
        }
    }
}

private fun IOException.isTransientNetwork(): Boolean = this is InterruptedIOException ||
    this is SocketTimeoutException || this is UnknownHostException || this is ConnectException ||
    this is NoRouteToHostException || this is SocketException || this is java.net.ProtocolException
