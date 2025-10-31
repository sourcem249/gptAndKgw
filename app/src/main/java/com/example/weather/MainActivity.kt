package com.example.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.weather.ui.theme.WeatherAppTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val REFRESH_INTERVAL_MINUTES = 5
private val coordinateRegex = Regex("^(-?\\d+(?:\\.\\d+)?),\\s*(-?\\d+(?:\\.\\d+)?)$")

data class WeatherData(
    val city: String,
    val temperature: Double,
    val humidity: Int,
    val windSpeed: Double,
    val description: String,
    val lastUpdated: String
)

data class LocationSuggestion(
    val name: String,
    val details: String,
    val latitude: Double,
    val longitude: Double
) {
    val displayName: String = listOf(name, details.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" - ")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WeatherAppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    WeatherScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen() {
    val context = LocalContext.current
    val locale = remember {
        val locales = context.resources.configuration.locales
        if (!locales.isEmpty) locales[0] else Locale.getDefault()
    }

    var query by remember {
        mutableStateOf(
            if (locale.language.equals("ko", ignoreCase = true)) "서울" else "Seoul"
        )
    }
    var currentWeather by remember { mutableStateOf<WeatherData?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var suggestions by remember { mutableStateOf<List<LocationSuggestion>>(emptyList()) }
    var selectedSuggestion by remember { mutableStateOf<LocationSuggestion?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isRequestingLocation by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    fun fetchCurrentLocationData() {
        coroutineScope.launch {
            try {
                isRequestingLocation = true
                val location = fusedLocationClient.awaitCurrentLocation()
                if (location != null) {
                    val suggestion = locationSuggestionFromLocation(context, location)
                    suggestions = emptyList()
                    selectedSuggestion = suggestion
                    infoMessage = context.getString(R.string.selected_location, suggestion.displayName)
                    errorMessage = null
                } else {
                    errorMessage = context.getString(R.string.current_location_unavailable)
                    infoMessage = null
                }
            } catch (exception: Exception) {
                errorMessage = exception.message ?: context.getString(R.string.current_location_unavailable)
                infoMessage = null
            } finally {
                isRequestingLocation = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                fetchCurrentLocationData()
            } else {
                errorMessage = context.getString(R.string.location_permission_denied)
                infoMessage = null
            }
        }
    )

    suspend fun updateWeather(suggestion: LocationSuggestion, showLoading: Boolean = true) {
        if (showLoading) {
            isRefreshing = true
        }
        try {
            val weather = fetchWeather(suggestion.latitude, suggestion.longitude)
            currentWeather = weather.copy(
                city = suggestion.displayName,
                lastUpdated = weather.lastUpdated
            )
            errorMessage = null
            infoMessage = context.getString(R.string.selected_location, suggestion.displayName)
        } catch (exception: Exception) {
            errorMessage = exception.message ?: context.getString(R.string.error_loading_weather)
            infoMessage = null
            currentWeather = null
        } finally {
            if (showLoading) {
                isRefreshing = false
            }
        }
    }

    fun performSearch(input: String = query) {
        val trimmed = input.trim()
        query = trimmed
        val coordinateSuggestion = coordinateSuggestion(trimmed, context)
        if (coordinateSuggestion != null) {
            suggestions = emptyList()
            selectedSuggestion = coordinateSuggestion
            infoMessage = context.getString(R.string.selected_location, coordinateSuggestion.displayName)
            errorMessage = null
            return
        }
        coroutineScope.launch {
            try {
                isSearching = true
                val results = searchLocations(trimmed, locale)
                if (results.isEmpty()) {
                    suggestions = emptyList()
                    currentWeather = null
                    errorMessage = context.getString(R.string.no_search_results)
                    infoMessage = null
                } else if (results.size == 1) {
                    suggestions = emptyList()
                    selectedSuggestion = results.first()
                    infoMessage = context.getString(R.string.selected_location, results.first().displayName)
                    errorMessage = null
                } else {
                    suggestions = results
                    currentWeather = null
                    infoMessage = context.getString(R.string.suggestions_instruction)
                    errorMessage = null
                }
            } catch (exception: Exception) {
                suggestions = emptyList()
                currentWeather = null
                errorMessage = exception.message ?: context.getString(R.string.error_loading_weather)
                infoMessage = null
            } finally {
                isSearching = false
            }
        }
    }

    fun requestCurrentLocation() {
        val permissionState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (permissionState == PackageManager.PERMISSION_GRANTED) {
            fetchCurrentLocationData()
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    LaunchedEffect(selectedSuggestion) {
        val suggestion = selectedSuggestion ?: return@LaunchedEffect
        updateWeather(suggestion)
        while (isActive) {
            delay(TimeUnit.MINUTES.toMillis(REFRESH_INTERVAL_MINUTES.toLong()))
            updateWeather(suggestion, showLoading = false)
        }
    }

    LaunchedEffect(Unit) {
        performSearch(query)
    }

    val title = stringResource(R.string.weather_title)
    val searchHint = stringResource(R.string.search_hint)
    val searchButton = stringResource(R.string.search_button)
    val currentLocationButtonLabel = stringResource(R.string.current_location_button)
    val refreshButtonLabel = stringResource(R.string.refresh_button)

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            SearchField(
                value = query,
                onValueChange = { query = it },
                onSearch = { performSearch(it) },
                label = searchHint,
                buttonLabel = searchButton
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { requestCurrentLocation() },
                    enabled = !isRequestingLocation
                ) {
                    Text(currentLocationButtonLabel)
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        selectedSuggestion?.let { suggestion ->
                            coroutineScope.launch { updateWeather(suggestion) }
                        }
                    },
                    enabled = selectedSuggestion != null && !isRefreshing
                ) {
                    Text(refreshButtonLabel)
                }
            }
            if (isSearching || isRefreshing || isRequestingLocation) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            infoMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            errorMessage?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (suggestions.isNotEmpty()) {
                LocationSuggestionsList(
                    suggestions = suggestions,
                    onSelect = { suggestion ->
                        suggestions = emptyList()
                        selectedSuggestion = suggestion
                        infoMessage = context.getString(R.string.selected_location, suggestion.displayName)
                        errorMessage = null
                    }
                )
            }
            currentWeather?.let { weather ->
                WeatherCard(weather = weather)
                Text(
                    text = stringResource(R.string.auto_refresh_hint, REFRESH_INTERVAL_MINUTES),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    label: String,
    buttonLabel: String
) {
    var text by remember(value) { mutableStateOf(value) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TextField(
            value = text,
            onValueChange = {
                text = it
                onValueChange(it)
            },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            modifier = Modifier
                .padding(top = 8.dp)
                .align(Alignment.End),
            onClick = { onSearch(text) }
        ) {
            Text(buttonLabel)
        }
    }
}

@Composable
private fun LocationSuggestionsList(
    suggestions: List<LocationSuggestion>,
    onSelect: (LocationSuggestion) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.suggestions_header),
            style = MaterialTheme.typography.titleMedium
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            items(suggestions) { suggestion ->
                SuggestionCard(
                    suggestion = suggestion,
                    onClick = { onSelect(suggestion) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestionCard(
    suggestion: LocationSuggestion,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = suggestion.name,
                style = MaterialTheme.typography.titleMedium
            )
            if (suggestion.details.isNotBlank()) {
                Text(
                    text = suggestion.details,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(
                    R.string.coordinates_format,
                    suggestion.latitude,
                    suggestion.longitude
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun WeatherCard(weather: WeatherData) {
    val temperatureFormat = stringResource(R.string.temperature_format, weather.temperature)
    val humidityFormat = stringResource(R.string.humidity_format, weather.humidity)
    val windFormat = stringResource(R.string.wind_format, weather.windSpeed)
    val lastUpdatedFormat = stringResource(R.string.last_updated, weather.lastUpdated)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = weather.city,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(text = weather.description, style = MaterialTheme.typography.bodyLarge)
            Text(text = temperatureFormat, style = MaterialTheme.typography.bodyLarge)
            Text(text = humidityFormat, style = MaterialTheme.typography.bodyMedium)
            Text(text = windFormat, style = MaterialTheme.typography.bodyMedium)
            Text(text = lastUpdatedFormat, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private suspend fun searchLocations(query: String, locale: Locale): List<LocationSuggestion> = withContext(Dispatchers.IO) {
    val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
    val language = locale.language.takeIf { it.isNotBlank() } ?: "en"
    val url = URL("https://geocoding-api.open-meteo.com/v1/search?name=$encodedQuery&count=6&language=$language&format=json")
    val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 5000
        readTimeout = 5000
    }

    try {
        val stream = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: connection.inputStream
        }
        stream.bufferedReader().use { reader ->
            val response = reader.readText()
            val json = JSONObject(response)
            val results = json.optJSONArray("results") ?: return@use emptyList<LocationSuggestion>()
            buildList {
                for (index in 0 until results.length()) {
                    val location = results.getJSONObject(index)
                    val name = location.getString("name")
                    val admin1 = location.optString("admin1", "")
                    val admin2 = location.optString("admin2", "")
                    val country = location.optString("country", "")
                    val details = listOf(admin1, admin2, country)
                        .filter { it.isNotBlank() }
                        .joinToString(", ")
                    add(
                        LocationSuggestion(
                            name = name,
                            details = details,
                            latitude = location.getDouble("latitude"),
                            longitude = location.getDouble("longitude")
                        )
                    )
                }
            }
        }
    } finally {
        connection.disconnect()
    }
}

private suspend fun fetchWeather(lat: Double, lon: Double): WeatherData = withContext(Dispatchers.IO) {
    val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code&timezone=auto")
    val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 5000
        readTimeout = 5000
    }

    try {
        val stream = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream ?: connection.inputStream
        }
        stream.bufferedReader().use { reader ->
            val response = reader.readText()
            val json = JSONObject(response)
            val current = json.getJSONObject("current")
            val temperature = current.getDouble("temperature_2m")
            val humidity = current.getInt("relative_humidity_2m")
            val windSpeed = current.getDouble("wind_speed_10m")
            val weatherCode = current.getInt("weather_code")
            val time = current.optString("time", "")
            WeatherData(
                city = "",
                temperature = temperature,
                humidity = humidity,
                windSpeed = windSpeed,
                description = weatherDescription(weatherCode),
                lastUpdated = formatTimeStamp(time)
            )
        }
    } finally {
        connection.disconnect()
    }
}

private fun weatherDescription(code: Int): String = when (code) {
    0 -> "맑음"
    1, 2 -> "대체로 맑음"
    3 -> "흐림"
    45, 48 -> "안개"
    51, 53, 55 -> "이슬비"
    56, 57 -> "얼어붙는 이슬비"
    61, 63, 65 -> "비"
    66, 67 -> "얼어붙는 비"
    71, 73, 75 -> "눈"
    77 -> "눈보라"
    80, 81, 82 -> "소나기"
    85, 86 -> "소낙눈"
    95 -> "뇌우"
    96, 99 -> "우박을 동반한 뇌우"
    else -> "알 수 없음"
}

private fun formatTimeStamp(raw: String): String {
    if (raw.isBlank()) return currentTimeStamp()
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
    return try {
        val instant = Instant.parse(raw)
        formatter.format(instant.atZone(ZoneId.systemDefault()))
    } catch (_: Exception) {
        try {
            val local = LocalDateTime.parse(raw)
            formatter.format(local)
        } catch (_: Exception) {
            raw
        }
    }
}

private fun currentTimeStamp(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return formatter.format(Date())
}

private fun coordinateSuggestion(input: String, context: android.content.Context): LocationSuggestion? {
    val match = coordinateRegex.matchEntire(input)
    return match?.let {
        val (lat, lon) = it.destructured
        val latitude = lat.toDouble()
        val longitude = lon.toDouble()
        LocationSuggestion(
            name = context.getString(R.string.custom_coordinates_label),
            details = context.getString(R.string.coordinates_format, latitude, longitude),
            latitude = latitude,
            longitude = longitude
        )
    }
}

private fun locationSuggestionFromLocation(context: android.content.Context, location: Location): LocationSuggestion {
    return LocationSuggestion(
        name = context.getString(R.string.current_location_label),
        details = context.getString(R.string.coordinates_format, location.latitude, location.longitude),
        latitude = location.latitude,
        longitude = location.longitude
    )
}

@SuppressLint("MissingPermission")
private suspend fun FusedLocationProviderClient.awaitCurrentLocation(): Location? = suspendCancellableCoroutine { continuation ->
    val cancellationTokenSource = CancellationTokenSource()
    getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationTokenSource.token)
        .addOnSuccessListener { location ->
            if (continuation.isActive) {
                continuation.resume(location)
            }
        }
        .addOnFailureListener { exception ->
            if (continuation.isActive) {
                continuation.resumeWithException(exception)
            }
        }
        .addOnCanceledListener {
            if (continuation.isActive) {
                continuation.resume(null)
            }
        }
    continuation.invokeOnCancellation {
        cancellationTokenSource.cancel()
    }
}
