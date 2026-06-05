package com.example.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.example.model.SavedLocation

@Dao
interface SavedLocationDao {
    @Query("SELECT * FROM saved_locations ORDER BY id DESC")
    fun getAllLocations(): Flow<List<SavedLocation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: SavedLocation)

    @Delete
    suspend fun deleteLocation(location: SavedLocation)
}
