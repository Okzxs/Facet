package com.facet.music.core.player

import com.facet.music.data.model.Track

/**
 * Результат вставки трека «играть следующим»: новая очередь и новый индекс
 * текущего трека (после чистки дубликатов индекс может сдвинуться).
 */
data class QueueInsertion(
    val queue: List<Track>,
    val currentIndex: Int,
)

/**
 * Вставляет [track] сразу после текущего трека [currentIndex].
 *
 * Поведение:
 * - пустой id трека игнорируется (в очереди не появляются безымянные строки);
 * - если очередь пуста или индекс вне диапазона, очередь не меняется —
 *   вызывающая сторона сама решает, начать воспроизведение или нет;
 * - трек, который уже играет, повторно не вставляется;
 * - прежнее вхождение трека в очереди удаляется, иначе один id встречался бы
 *   дважды и трек сыграл бы два раза;
 * - текущий трек остаётся текущим, индекс пересчитывается после удаления.
 *
 * Функция чистая: без Compose и корутин, поэтому покрыта JVM-тестами.
 */
fun insertNextTrack(queue: List<Track>, currentIndex: Int, track: Track): QueueInsertion {
    if (track.id.isBlank()) return QueueInsertion(queue, currentIndex)
    if (currentIndex !in queue.indices) return QueueInsertion(queue, currentIndex)

    val currentTrack = queue[currentIndex]
    if (currentTrack.id == track.id) return QueueInsertion(queue, currentIndex)

    // Считаем, сколько вхождений нового трека удалено до текущего: на столько
    // сдвинется индекс текущего в очищенном списке.
    var removedBeforeCurrent = 0
    val cleaned = ArrayList<Track>(queue.size)
    queue.forEachIndexed { index, item ->
        if (index != currentIndex && item.id == track.id) {
            if (index < currentIndex) removedBeforeCurrent++
        } else {
            cleaned.add(item)
        }
    }

    val anchorIndex = currentIndex - removedBeforeCurrent
    cleaned.add(anchorIndex + 1, track)
    return QueueInsertion(cleaned, anchorIndex)
}
