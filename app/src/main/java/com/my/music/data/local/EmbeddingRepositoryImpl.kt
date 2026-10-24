package com.my.music.data.local

import com.my.music.data.local.dao.EmbeddingDao
import com.my.music.data.local.embedding.toBlob
import com.my.music.data.local.embedding.toFloatArrayOrNull
import com.my.music.data.local.entity.AudioEmbeddingEntity
import com.my.music.data.repository.EmbeddingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Реализация хранилища аудио-эмбеддингов на Room.
 *
 * Вектор кодируется в BLOB и декодируется обратно при чтении. Битые
 * BLOB-записи пропускаются, чтобы не ломать пакетную выборку.
 */
@Singleton
class EmbeddingRepositoryImpl @Inject constructor(
    private val embeddingDao: EmbeddingDao,
) : EmbeddingRepository {

    override suspend fun saveEmbedding(
        trackId: String,
        vector: FloatArray,
        createdAt: Long,
    ) = withContext(Dispatchers.IO) {
        require(trackId.isNotBlank()) { "Пустой trackId для аудио-эмбеддинга" }
        require(vector.isNotEmpty()) { "Пустой аудио-эмбеддинг для $trackId" }
        embeddingDao.upsert(
            AudioEmbeddingEntity(
                trackId = trackId,
                vector = vector.toBlob(),
                createdAt = createdAt,
            ),
        )
    }

    override suspend fun getEmbedding(trackId: String): FloatArray? =
        withContext(Dispatchers.IO) {
            embeddingDao.getById(trackId)
                ?.vector
                ?.toFloatArrayOrNull()
        }

    override suspend fun getEmbeddings(
        trackIds: Collection<String>,
    ): Map<String, FloatArray> = withContext(Dispatchers.IO) {
        val ids = trackIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return@withContext emptyMap()

        // SQLite имеет ограничение на число параметров в IN (...).
        // Для больших библиотек читаем пачками, чтобы лог сходства не падал.
        val entities = ArrayList<AudioEmbeddingEntity>(ids.size)
        for (chunk in ids.chunked(SQLITE_IN_LIMIT)) {
            entities.addAll(embeddingDao.getByIds(chunk))
        }

        entities
            .mapNotNull { entity ->
                entity.vector.toFloatArrayOrNull()?.let { vector ->
                    entity.trackId to vector
                }
            }
            .toMap()
    }

    override suspend fun deleteEmbedding(trackId: String) =
        withContext(Dispatchers.IO) {
            embeddingDao.delete(trackId)
        }

    private companion object {
        const val SQLITE_IN_LIMIT = 500
    }
}
