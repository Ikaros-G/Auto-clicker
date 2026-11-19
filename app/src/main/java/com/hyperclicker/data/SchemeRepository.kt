package com.hyperclicker.data

import kotlinx.coroutines.flow.Flow

class SchemeRepository(private val dao: SchemeDao) {
    fun observeAll(): Flow<List<ClickScheme>> = dao.observeAll()

    suspend fun getById(id: Long): ClickScheme? = dao.getById(id)

    suspend fun getEnabledLowFreq(): List<ClickScheme> = dao.getEnabledLowFreq()

    suspend fun insert(scheme: ClickScheme): Long = dao.insert(scheme)

    suspend fun update(scheme: ClickScheme) = dao.update(scheme)

    suspend fun delete(scheme: ClickScheme) = dao.delete(scheme)
}
