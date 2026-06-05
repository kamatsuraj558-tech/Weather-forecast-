package com.example.repository

import com.example.db.SavedLocationDao
import com.example.model.SavedLocation
import kotlinx.coroutines.flow.Flow

class WeatherRepository(private val dao: SavedLocationDao) {
    val allSavedLocations: Flow<List<SavedLocation>> = dao.getAllLocations()

    suspend fun insertLocation(location: SavedLocation) {
        dao.insertLocation(location)
    }

    suspend fun deleteLocation(location: SavedLocation) {
        dao.deleteLocation(location)
    }
}
