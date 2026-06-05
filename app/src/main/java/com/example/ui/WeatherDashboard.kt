package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.SavedLocation
import com.example.network.CurrentWeather
import com.example.network.DailyForecast
import com.example.network.GeocodingResult
import com.example.network.WeatherForecastResponse
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeatherDashboard(viewModel: WeatherViewModel, modifier: Modifier = Modifier) {
    val activeLoc by viewModel.activeLocation.collectAsStateWithLifecycle()
    val weatherState by viewModel.weatherState.collectAsStateWithLifecycle()
    val geocodingState by viewModel.geocodingState.collectAsStateWithLifecycle()
    val aiInsightState by viewModel.aiInsightState.collectAsStateWithLifecycle()
    val savedLocs by viewModel.savedLocations.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }

    // Map weather code for gradients
    val currentCode = when (val state = weatherState) {
        is WeatherUiState.Success -> state.data.current?.weatherCode ?: 0
        else -> 0
    }
    val themeConfig = WeatherThemeManager.getThemeForCode(currentCode)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(themeConfig.gradientColors))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Header & Search
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            viewModel.searchCity(it)
                        },
                        placeholder = { Text("Search location...", color = Color.White.copy(alpha = 0.7f)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = "Search icon", tint = Color.White) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    viewModel.searchCity("")
                                }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Clear search", tint = Color.White)
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.Black.copy(alpha = 0.15f),
                            unfocusedContainerColor = Color.Black.copy(alpha = 0.08f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("app_city_search_input")
                    )
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    // Search dropdown results
                    if (geocodingState is GeocodingUiState.Success || geocodingState is GeocodingUiState.Loading) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .align(Alignment.TopCenter)
                                .heightIn(max = 240.dp)
                                .testTag("city_search_suggestions")
                                .zIndex(5f),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            when (val state = geocodingState) {
                                is GeocodingUiState.Loading -> {
                                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    }
                                }
                                is GeocodingUiState.Success -> {
                                    if (state.cities.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                            Text("No matches discovered", style = MaterialTheme.typography.bodyMedium)
                                        }
                                    } else {
                                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                            items(state.cities.size) { index ->
                                                val city = state.cities[index]
                                                val subtext = listOfNotNull(city.admin1, city.country).joinToString(", ")
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            viewModel.selectCity(city)
                                                            searchQuery = ""
                                                        }
                                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                                ) {
                                                    Text(city.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text(subtext, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                                }
                                                if (index < state.cities.size - 1) {
                                                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                                                }
                                            }
                                        }
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                }

                // Favorite Quick Locations Row
                if (savedLocs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(savedLocs.size) { index ->
                            val loc = savedLocs[index]
                            val isSelected = loc.name.equals(activeLoc.name, ignoreCase = true)
                            val containerCol = if (isSelected) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f)
                            val borderCol = if (isSelected) Color.White else Color.Transparent

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(containerCol)
                                    .border(1.dp, borderCol, RoundedCornerShape(16.dp))
                                    .clickable { viewModel.selectSavedLocation(loc) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.LocationOn,
                                        contentDescription = "Saved location icon",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = loc.name,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "Remove location",
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clickable { viewModel.deleteLocation(loc) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Active Location Header
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeLoc.name,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val regionInfo = listOfNotNull(activeLoc.admin1, activeLoc.country).joinToString(", ")
                        if (regionInfo.isNotEmpty()) {
                            Text(
                                text = regionInfo,
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Favorite Button
                    val isFav = savedLocs.any {
                        it.name.equals(activeLoc.name, ignoreCase = true) &&
                                Math.abs(it.latitude - activeLoc.latitude) < 0.05 &&
                                Math.abs(it.longitude - activeLoc.longitude) < 0.05
                    }
                    IconButton(
                        onClick = {
                            if (isFav) {
                                val favEntity = savedLocs.find {
                                    it.name.equals(activeLoc.name, ignoreCase = true)
                                }
                                if (favEntity != null) viewModel.deleteLocation(favEntity)
                            } else {
                                viewModel.addActiveToFavorites()
                            }
                        },
                        modifier = Modifier.testTag("city_save_button")
                    ) {
                        Icon(
                            imageVector = if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Save favorite",
                            tint = if (isFav) Color.Red else Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Main Weather Display Scrollable Content
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        when (val state = weatherState) {
                            is WeatherUiState.Loading -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = Color.White)
                                }
                            }
                            is WeatherUiState.Error -> {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.15f)),
                                    border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Outlined.CloudOff, contentDescription = "Error", tint = Color.White, modifier = Modifier.size(40.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Connection or Location Unresolved", fontWeight = FontWeight.Bold, color = Color.White)
                                        Text(state.message, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, textAlign = TextAlign.Center)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = { viewModel.fetchWeatherForCity(activeLoc) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.DarkGray)
                                        ) {
                                            Text("Retry")
                                        }
                                    }
                                }
                            }
                            is WeatherUiState.Success -> {
                                CurrentWeatherPanel(weather = state.data, themeConfig = themeConfig)
                            }
                            else -> {}
                        }
                    }

                    // 24hr Hourly Forecast (LazyRow Inside Column)
                    item {
                        if (weatherState is WeatherUiState.Success) {
                            val data = (weatherState as WeatherUiState.Success).data
                            HourlyForecastOverview(weather = data)
                        }
                    }

                    // AIGemini Insights Card
                    item {
                        AiAssistantInsightPanel(aiState = aiInsightState)
                    }

                    // 10-Day Forecast List Block
                    item {
                        if (weatherState is WeatherUiState.Success) {
                            val data = (weatherState as WeatherUiState.Success).data
                            TenDayForecastPanel(weather = data)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CurrentWeatherPanel(weather: WeatherForecastResponse, themeConfig: WeatherThemeConfig) {
    val current = weather.current ?: return
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.12f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("weather_current_details_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Current Weather",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = themeConfig.title,
                        fontSize = 22.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = themeConfig.icon,
                    contentDescription = "Weather Icon",
                    tint = Color.White,
                    modifier = Modifier.size(52.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Temperature Display
            Text(
                text = "${current.temperature.toInt()}°",
                fontSize = 72.sp,
                fontWeight = FontWeight.Light,
                color = Color.White,
                modifier = Modifier.testTag("current_temp_display")
            )

            Text(
                text = "Feels like ${current.apparentTemperature.toInt()}°",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(16.dp))

            // Extra metrics grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                MetricItem(icon = Icons.Outlined.WaterDrop, label = "Humidity", value = "${current.humidity.toInt()}%")
                MetricItem(icon = Icons.Outlined.Air, label = "Wind", value = "${current.windSpeed.toInt()} km/h")
                MetricItem(icon = Icons.Outlined.Grain, label = "Precip.", value = "${current.precipitation} mm")
            }
        }
    }
}

@Composable
fun MetricItem(icon: ImageVector, label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(text = label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
fun HourlyForecastOverview(weather: WeatherForecastResponse) {
    val hourly = weather.hourly ?: return
    val currentHourIndex = calculateCurrentHourIndex(hourly.time)

    // Slice for the next 18, 24 hourly periods
    val limit = 24
    val indices = (currentHourIndex until (currentHourIndex + limit).coerceAtMost(hourly.time.size)).toList()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Hourly Trend (24 Hr)",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(start = 4.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(indices.size) { index ->
                val realIdx = indices[index]
                val rawTime = hourly.time[realIdx]
                val formattedTime = formatHourlyTime(rawTime)
                val temp = hourly.temperature[realIdx].toInt()
                val code = hourly.weatherCode[realIdx]
                val itemTheme = WeatherThemeManager.getThemeForCode(code)

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.width(72.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = formattedTime, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                        Icon(
                            imageVector = itemTheme.icon,
                            contentDescription = itemTheme.title,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(text = "$temp°", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun TenDayForecastPanel(weather: WeatherForecastResponse) {
    val daily = weather.daily ?: return
    val size = daily.time.size

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("forecast_10_day_list"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "10-Day Forecast",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(start = 4.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.12f)),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (i in 0 until size) {
                    val rawDateStr = daily.time[i]
                    val dayOfWeek = formatDailyDate(rawDateStr)
                    val code = daily.weatherCode[i]
                    val theme = WeatherThemeManager.getThemeForCode(code)
                    val min = daily.tempMin[i].toInt()
                    val max = daily.tempMax[i].toInt()
                    val precipProb = daily.precipProbMax?.getOrNull(i) ?: 0

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Day name
                        Text(
                            text = dayOfWeek,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.width(80.dp)
                        )

                        // Condition & Icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = theme.icon,
                                contentDescription = theme.title,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = theme.title,
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Precip. probability
                        if (precipProb > 10) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier.width(42.dp).padding(end = 4.dp)
                            ) {
                                Icon(Icons.Outlined.WaterDrop, contentDescription = "Rain chance", tint = Color(0xFF64B5F6), modifier = Modifier.size(11.dp))
                                Text(text = "$precipProb%", fontSize = 10.sp, color = Color(0xFF64B5F6), fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Spacer(modifier = Modifier.width(42.dp))
                        }

                        // Temperature gauge text
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.width(72.dp)
                        ) {
                            Text(text = "$min°", fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.width(6.dp))
                            // Bar or divider representation
                            Box(
                                modifier = Modifier
                                    .width(16.dp)
                                    .height(2.dp)
                                    .align(Alignment.CenterVertically)
                                    .background(Color.White.copy(alpha = 0.3f))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "$max°", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    if (i < size - 1) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    }
                }
            }
        }
    }
}

@Composable
fun AiAssistantInsightPanel(aiState: AiInsightUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_weather_wisdom_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = "AI helper star",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "AI Weather Assistant",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (aiState) {
                is AiInsightUiState.Idle -> {
                    Text("Fetching insights...", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
                }
                is AiInsightUiState.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            strokeWidth = 2.dp
                        )
                        Text(
                            "Analyzing 10-day patterns...",
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                            fontSize = 13.sp
                        )
                    }
                }
                is AiInsightUiState.Success -> {
                    Text(
                        text = aiState.insight,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        lineHeight = 18.sp
                    )
                }
                is AiInsightUiState.Error -> {
                    Text(
                        text = "Unable to consult AI companion. Utilizing core local charts instead.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

// Helper time formatting functions
private fun calculateCurrentHourIndex(times: List<String>): Int {
    if (times.isEmpty()) return 0
    val nowStr = SimpleDateFormat("yyyy-MM-dd'T'HH", Locale.getDefault()).format(Date())
    val idx = times.indexOfFirst { it.startsWith(nowStr) }
    return if (idx == -1) 0 else idx
}

private fun formatHourlyTime(rawIso: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault())
        val date = parser.parse(rawIso)
        if (date != null) {
            SimpleDateFormat("h a", Locale.getDefault()).format(date)
        } else {
            rawIso
        }
    } catch (e: Exception) {
        rawIso.split("T").getOrNull(1) ?: rawIso
    }
}

private fun formatDailyDate(rawIsoDate: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = parser.parse(rawIsoDate)
        if (date != null) {
            val nowStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            if (rawIsoDate == nowStr) {
                "Today"
            } else {
                SimpleDateFormat("EEE", Locale.getDefault()).format(date)
            }
        } else {
            rawIsoDate
        }
    } catch (e: Exception) {
        rawIsoDate
    }
}

// Weather theme structures & interpreter
class WeatherThemeConfig(
    val title: String,
    val icon: ImageVector,
    val gradientColors: List<Color>
)

object WeatherThemeManager {
    fun getThemeForCode(code: Int): WeatherThemeConfig {
        return when (code) {
            0 -> WeatherThemeConfig(
                title = "Clear Sunny",
                icon = Icons.Outlined.WbSunny,
                gradientColors = listOf(Color(0xFFF39C12), Color(0xFF3498DB))
            )
            1, 2, 3 -> WeatherThemeConfig(
                title = "Partly Cloudy",
                icon = Icons.Outlined.Cloud,
                gradientColors = listOf(Color(0xFF5DADE2), Color(0xFF85929E))
            )
            45, 48 -> WeatherThemeConfig(
                title = "Dense Fog",
                icon = Icons.Outlined.BlurOn,
                gradientColors = listOf(Color(0xFFBDC3C7), Color(0xFF7F8C8D))
            )
            51, 53, 55 -> WeatherThemeConfig(
                title = "Light Drizzle",
                icon = Icons.Outlined.Grain,
                gradientColors = listOf(Color(0xFF7FB3D5), Color(0xFF5D6D7E))
            )
            61, 63, 65, 80, 81, 82 -> WeatherThemeConfig(
                title = "Moderate Rain",
                icon = Icons.Outlined.WaterDrop,
                gradientColors = listOf(Color(0xFF2E4053), Color(0xFF1B2631))
            )
            56, 57, 66, 67, 71, 73, 75, 77, 85, 86 -> WeatherThemeConfig(
                title = "Snowy Freeze",
                icon = Icons.Outlined.AcUnit,
                gradientColors = listOf(Color(0xFFAED6F1), Color(0xFFD6EAF8))
            )
            95, 96, 99 -> WeatherThemeConfig(
                title = "Thunderstorm",
                icon = Icons.Outlined.Thunderstorm,
                gradientColors = listOf(Color(0xFF4A235A), Color(0xFF1F1235))
            )
            else -> WeatherThemeConfig(
                title = "Overcast Cloudy",
                icon = Icons.Outlined.Cloud,
                gradientColors = listOf(Color(0xFF34495E), Color(0xFF2C3E50))
            )
        }
    }
}
