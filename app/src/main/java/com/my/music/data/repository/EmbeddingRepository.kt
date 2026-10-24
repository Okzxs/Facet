package com.my.music.data.repository

/**
 * Локальное хранилище аудио-эмбеддингов.
 *
 * Репозиторий работает с доменными FloatArray, а детали BLOB-кодирования
 * остаются в data-слое.
 */
interface EmbeddingRepository {

    suspend fun saveEmbedding(
        trackId: String,
        vector: FloatArray,
        createdAt: Long = System.currentTimeMillis(),
    )

    suspend fun getEmbedding(trackId: String): FloatArray?

    /**
     * Пакетная загрузка: нужна для проверки сходства истории и лайков
     * без N отдельных запросов к Room.
     */
    suspend fun getEmbeddings(trackIds: Collection<String>): Map<String, FloatArray>

    suspend fun deleteEmbedding(trackId: String)
}
