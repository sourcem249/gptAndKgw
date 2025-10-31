package com.example.weather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.weather.ui.theme.WeatherAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

data class WeatherData(
    val city: String,
    val temperature: Double,
    val humidity: Int,
    val windSpeed: Double,
    val description: String,
    val lastUpdated: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen() {
    var query by remember { mutableStateOf("Seoul") }
    var weatherItems by remember { mutableStateOf(listOf<WeatherData>()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    fun loadWeather(input: String) {
        coroutineScope.launch {
            try {
                val (cityName, latitude, longitude) = resolveLocation(input)
                val weather = fetchWeather(latitude, longitude)
                weatherItems = listOf(
                    weather.copy(
                        city = cityName,
                        lastUpdated = currentTimeStamp()
                    )
                )
                errorMessage = null
            } catch (exception: Exception) {
                errorMessage = exception.message
                weatherItems = emptyList()
            }
        }
    }

    LaunchedEffect(Unit) {
        loadWeather(query)
    }

    val title = stringResource(R.string.weather_title)
    val searchHint = stringResource(R.string.search_hint)
    val searchButton = stringResource(R.string.search_button)
    val errorFallback = stringResource(R.string.error_loading_weather)

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
                onSearch = { loadWeather(query) },
                label = searchHint,
                buttonLabel = searchButton
            )
            errorMessage?.let {
                Text(
                    text = it.ifBlank { errorFallback },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(weatherItems) { weather ->
                    WeatherCard(weather)
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
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
            onClick = onSearch
        ) {
            Text(buttonLabel)
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

private suspend fun fetchWeather(lat: Double, lon: Double): WeatherData = withContext(Dispatchers.IO) {
    val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code")
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
            WeatherData(
                city = "",
                temperature = temperature,
                humidity = humidity,
                windSpeed = windSpeed,
                description = weatherDescription(weatherCode),
                lastUpdated = ""
            )
        }
    } finally {
        connection.disconnect()
    }
}

private suspend fun resolveLocation(input: String): Triple<String, Double, Double> = withContext(Dispatchers.IO) {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) {
        throw IllegalArgumentException("입력값을 확인해주세요.")
    }

    val coordinateRegex = Regex("^(-?\\d+(?:\\.\\d+)?),\\s*(-?\\d+(?:\\.\\d+)?)$")
    coordinateRegex.matchEntire(trimmed)?.let { matchResult ->
        val (lat, lon) = matchResult.destructured
        return@withContext Triple("사용자 지정", lat.toDouble(), lon.toDouble())
    }

    val query = trimmed.replace(" ", "+")
    val url = URL("https://geocoding-api.open-meteo.com/v1/search?name=$query&count=1&language=ko&format=json")
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
            val results = json.optJSONArray("results")
            if (results == null || results.length() == 0) {
                throw IllegalArgumentException("결과를 찾을 수 없습니다.")
            }
            val location = results.getJSONObject(0)
            val name = location.getString("name")
            val latitude = location.getDouble("latitude")
            val longitude = location.getDouble("longitude")
            val country = location.optString("country", "")
            val cityName = listOf(name, country).filter { it.isNotBlank() }.joinToString(", ")
            Triple(cityName, latitude, longitude)
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

private fun currentTimeStamp(): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return formatter.format(Date())
}
