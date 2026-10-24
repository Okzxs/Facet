package com.my.music.data.local.embedding

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Преобразование FloatArray в BLOB для Room.
 *
 * Формат намеренно фиксирован: little-endian IEEE-754 Float32.
 * Так байты одинаково читаются на всех Android-устройствах.
 */
internal fun FloatArray.toBlob(): ByteArray {
    require(isNotEmpty()) { "Нельзя сохранить пустой аудио-эмбеддинг" }
    val buffer = ByteBuffer.allocate(size * Float.SIZE_BYTES)
        .order(ByteOrder.LITTLE_ENDIAN)
    buffer.asFloatBuffer().put(this)
    return buffer.array()
}

/**
 * Обратное преобразование BLOB в FloatArray.
 *
 * Возвращает null для повреждённого или пустого BLOB, чтобы одна
 * битая запись не ломала загрузку всей истории.
 */
internal fun ByteArray.toFloatArrayOrNull(): FloatArray? {
    if (isEmpty() || size % Float.SIZE_BYTES != 0) return null
    val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
    val result = FloatArray(size / Float.SIZE_BYTES)
    buffer.asFloatBuffer().get(result)
    return result
}
