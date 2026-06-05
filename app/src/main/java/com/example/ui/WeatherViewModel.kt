package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.model.SavedLocation
import com.example.network.GeocodingResult
import com.example.network.RetrofitClient
import com.example.network.WeatherForecastResponse
import com.example.network.gemini.GeminiContent
import com.example.network.gemini.GeminiGenerationConfig
import com.example.network.gemini.GeminiPart
import com.example.network.gemini.GeminiRequest
import com.example.repository.WeatherRepository
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface WeatherUiState {
    object Idle : WeatherUiState
    object Loading : WeatherUiState
    data class Success(val data: WeatherForecastResponse) : WeatherUiState
    data class Error(val message: String) : WeatherUiState
}

sealed interface GeocodingUiState {
    object Idle : GeocodingUiState
    object Loading : GeocodingUiState
    data class Success(val cities: List<GeocodingResult>) : GeocodingUiState
    data class Error(val message: String) : GeocodingUiState
}

sealed interface AiInsightUiState {
    object Idle : AiInsightUiState
    object Loading : AiInsightUiState
    data class Success(val insight: String) : AiInsightUiState
    data class Error(val message: String) : AiInsightUiState
}

class WeatherViewModel(private val repository: WeatherRepository) : ViewModel() {

    val savedLocations: StateFlow<List<SavedLocation>> = repository.allSavedLocations
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _activeLocation = MutableStateFlow<SavedLocation>(
        SavedLocation(
            name = "New York",
            latitude = 40.7128,
            longitude = -74.0060,
            country = "United States",
            admin1 = "New York"
        )
    )
    val activeLocation: StateFlow<SavedLocation> = _activeLocation.asStateFlow()

    private val _weatherState = MutableStateFlow<WeatherUiState>(WeatherUiState.Idle)
    val weatherState: StateFlow<WeatherUiState> = _weatherState.asStateFlow()

    private val _geocodingState = MutableStateFlow<GeocodingUiState>(GeocodingUiState.Idle)
    val geocodingState: StateFlow<GeocodingUiState> = _geocodingState.asStateFlow()

    private val _aiInsightState = MutableStateFlow<AiInsightUiState>(AiInsightUiState.Idle)
    val aiInsightState: StateFlow<AiInsightUiState> = _aiInsightState.asStateFlow()

    init {
        // Initialize with default or first saved location
        viewModelScope.launch {
            savedLocations.collect { locations ->
                if (locations.isNotEmpty() && _activeLocation.value.id == 0 && _activeLocation.value.name == "New York") {
                    // Update to first saved location on fresh launch
                    _activeLocation.value = locations.first()
                    fetchWeatherForCity(locations.first())
                } else if (_weatherState.value is WeatherUiState.Idle) {
                    fetchWeatherForCity(_activeLocation.value)
                }
            }
        }
    }

    fun searchCity(query: String) {
        if (query.trim().length < 2) {
            _geocodingState.value = GeocodingUiState.Idle
            return
        }
        _geocodingState.value = GeocodingUiState.Loading
        viewModelScope.launch {
            try {
                val response = RetrofitClient.geocodingService.searchCity(query)
                val results = response.results
                if (results != null) {
                    _geocodingState.value = GeocodingUiState.Success(results)
                } else {
                    _geocodingState.value = GeocodingUiState.Success(emptyList())
                }
            } catch (e: Exception) {
                _geocodingState.value = GeocodingUiState.Error(e.localizedMessage ?: "Geocoding failure")
            }
        }
    }

    fun selectCity(result: GeocodingResult) {
        val location = SavedLocation(
            name = result.name,
            latitude = result.latitude,
            longitude = result.longitude,
            country = result.country,
            admin1 = result.admin1
        )
        selectSavedLocation(location)
    }

    fun selectSavedLocation(location: SavedLocation) {
        _activeLocation.value = location
        _geocodingState.value = GeocodingUiState.Idle
        fetchWeatherForCity(location)
    }

    fun fetchWeatherForCity(location: SavedLocation) {
        _weatherState.value = WeatherUiState.Loading
        _aiInsightState.value = AiInsightUiState.Idle
        viewModelScope.launch {
            try {
                val response = RetrofitClient.weatherService.getForecast(
                    latitude = location.latitude,
                    longitude = location.longitude
                )
                _weatherState.value = WeatherUiState.Success(response)
                generateAiInsights(location.name, response)
            } catch (e: Exception) {
                _weatherState.value = WeatherUiState.Error(e.localizedMessage ?: "Failed to load forecast")
            }
        }
    }

    fun addActiveToFavorites() {
        viewModelScope.launch {
            val active = _activeLocation.value
            // Check if already exists in saved list
            val exists = savedLocations.value.any {
                it.name.equals(active.name, ignoreCase = true) &&
                        Math.abs(it.latitude - active.latitude) < 0.05 &&
                        Math.abs(it.longitude - active.longitude) < 0.05
            }
            if (!exists) {
                repository.insertLocation(active)
            }
        }
    }

    fun deleteLocation(location: SavedLocation) {
        viewModelScope.launch {
            repository.deleteLocation(location)
        }
    }

    private fun generateAiInsights(locationName: String, weather: WeatherForecastResponse) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey.contains("MY_GEMINI_API_KEY")) {
            _aiInsightState.value = AiInsightUiState.Success(
                "Keep a light jacket and comfortable layers ready! Remember to drink plenty of water, and stay ready to seize the outdoor breeze today. (Set your Gemini API key in the Secrets panel to activate direct weather-tailored recommendations!)"
            )
            return
        }

        _aiInsightState.value = AiInsightUiState.Loading
        viewModelScope.launch {
            try {
                val current = weather.current
                val daily = weather.daily
                if (current == null || daily == null) {
                    _aiInsightState.value = AiInsightUiState.Error("Weather data incomplete for AI")
                    return@launch
                }

                val promptText = """
                    You are a stellar, professional, friendly Weather Assistant. Read the weather update below and generate a short, extremely helpful, stylish daily summary (under 65 words). Give practical advice (clothing, activities, or general motivation) based on the conditions. Do not output markdown lists, keep it as conversational flowing paragraph.
                    Weather info for $locationName:
                    Current Temp: ${current.temperature}°C, Feels Like: ${current.apparentTemperature}°C, Weather Code: ${current.weatherCode}, Wind: ${current.windSpeed} km/h, Humidity: ${current.humidity}%.
                    10-day forecast high temps: ${daily.tempMax.joinToString(", ")}°C.
                    Format output: start with a friendly greeting card style!
                """.trimIndent()

                val request = GeminiRequest(
                    contents = listOf(
                        GeminiContent(
                            parts = listOf(GeminiPart(text = promptText))
                        )
                    ),
                    generationConfig = GeminiGenerationConfig(
                        temperature = 0.7f,
                        maxOutputTokens = 150
                    )
                )

                val response = RetrofitClient.geminiService.generateContent(
                    model = "gemini-3.5-flash",
                    apiKey = apiKey,
                    request = request
                )

                val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (text != null) {
                    _aiInsightState.value = AiInsightUiState.Success(text.trim())
                } else {
                    _aiInsightState.value = AiInsightUiState.Success(
                        "Plan for comfortable coordinates and have a spectacular day ahead! (Set Gemini configuration in Secrets panel to details)."
                    )
                }
            } catch (e: Exception) {
                _aiInsightState.value = AiInsightUiState.Error("AI Insight: ${e.localizedMessage}")
            }
        }
    }
}

class WeatherViewModelFactory(private val repository: WeatherRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WeatherViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WeatherViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
