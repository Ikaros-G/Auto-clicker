package com.hyperclicker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SchemeDao {
    @Query("SELECT * FROM schemes ORDER BY id DESC")
    fun observeAll(): Flow<List<ClickScheme>>

    @Query("SELECT * FROM schemes WHERE id = :id")
    suspend fun getById(id: Long): ClickScheme?

    /** 返回所有已启用的低频方案（开机自启恢复用） */
    @Query("SELECT * FROM schemes WHERE mode = 1 AND enabled = 1")
    suspend fun getEnabledLowFreq(): List<ClickScheme>

    @Insert
    suspend fun insert(scheme: ClickScheme): Long

    @Update
    suspend fun update(scheme: ClickScheme)

    @Delete
    suspend fun delete(scheme: ClickScheme)
}
