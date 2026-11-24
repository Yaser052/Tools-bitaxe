package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExperimentDao {

    @Query("SELECT * FROM experiments ORDER BY timestampMillis DESC")
    fun observeAllExperiments(): Flow<List<ExperimentEntity>>

    @Query("SELECT * FROM experiments WHERE isStable = 1 ORDER BY efficiencyJth ASC")
    fun observeStableExperiments(): Flow<List<ExperimentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExperiment(entity: ExperimentEntity): Long

    @Query("UPDATE experiments SET isLastKnownStable = 0 WHERE isLastKnownStable = 1")
    suspend fun clearLastKnownStableFlag()

    @Query("UPDATE experiments SET isLastKnownStable = 1 WHERE id = :experimentId")
    suspend fun markAsLastKnownStable(experimentId: Long)
}
