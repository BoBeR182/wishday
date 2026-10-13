package io.github.wishday.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT * FROM people ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Person>>

    @Query("SELECT * FROM people")
    suspend fun getAll(): List<Person>

    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun get(id: Long): Person?

    @Upsert
    suspend fun upsert(person: Person): Long

    /** Inserts imported contacts, skipping any whose lookup key is already present. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringExisting(people: List<Person>): List<Long>

    /**
     * Claims this year's greeting. Returns 1 if this call claimed it and 0 if it was already
     * claimed, so two overlapping runs (daily check and catch-up) can't both send.
     */
    @Query(
        "UPDATE people SET lastGreetedYear = :year " +
            "WHERE id = :id AND (lastGreetedYear IS NULL OR lastGreetedYear != :year)",
    )
    suspend fun markGreeted(id: Long, year: Int): Int

    @Delete
    suspend fun delete(person: Person)
}
