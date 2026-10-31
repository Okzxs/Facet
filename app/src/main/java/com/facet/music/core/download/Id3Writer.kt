package com.facet.music.core.download

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset

/**
 * Минимальный писатель ID3v2.3: название, исполнитель и обложка.
 *
 * Сторонняя библиотека ради трёх фреймов не нужна: формат простой —
 * заголовок «ID3» + синхсайф-размер, затем фреймы с 4-байтовым id,
 * размером и двумя байтами флагов. Текст пишем в UTF-16 с BOM: так
 * кириллица читается любым плеером, а не только понимающим UTF-8 (v2.4).
 */
object Id3Writer {

    private const val HEADER_SIZE = 10
    private const val ENCODING_UTF16 = 0x01
    private const val PICTURE_TYPE_COVER_FRONT = 0x03

    /**
     * Дописывает теги к MP3-файлу. Существующий ID3v2 в начале файла
     * отбрасывается, чтобы теги не дублировались.
     *
     * @return true, если файл перезаписан.
     */
    fun write(
        file: File,
        title: String,
        artist: String,
        artwork: ByteArray? = null,
        artworkMime: String = "image/jpeg",
    ): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        return runCatching {
            val original = file.readBytes()
            val audio = stripExistingTags(original)

            val frames = ByteArrayOutputStream()
            writeTextFrame(frames, "TIT2", title)
            writeTextFrame(frames, "TPE1", artist)
            if (artwork != null && artwork.isNotEmpty()) {
                writePictureFrame(frames, artwork, artworkMime)
            }
            val framesBytes = frames.toByteArray()

            val header = ByteArray(HEADER_SIZE)
            header[0] = 'I'.code.toByte()
            header[1] = 'D'.code.toByte()
            header[2] = '3'.code.toByte()
            header[3] = 0x03 // версия 2.3
            header[4] = 0x00 // ревизия
            header[5] = 0x00 // флаги
            writeSynchsafe(header, 6, framesBytes.size)

            val temp = File(file.parentFile, file.name + ".tags")
            temp.outputStream().use { output ->
                output.write(header)
                output.write(framesBytes)
                output.write(audio)
            }
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
            true
        }.getOrDefault(false)
    }

    /** Пропускает уже существующий ID3v2-блок в начале файла. */
    private fun stripExistingTags(bytes: ByteArray): ByteArray {
        if (bytes.size < HEADER_SIZE) return bytes
        if (bytes[0] != 'I'.code.toByte() ||
            bytes[1] != 'D'.code.toByte() ||
            bytes[2] != '3'.code.toByte()
        ) {
            return bytes
        }
        val size = ((bytes[6].toInt() and 0x7F) shl 21) or
            ((bytes[7].toInt() and 0x7F) shl 14) or
            ((bytes[8].toInt() and 0x7F) shl 7) or
            (bytes[9].toInt() and 0x7F)
        val total = HEADER_SIZE + size
        return if (total in 0 until bytes.size) bytes.copyOfRange(total, bytes.size) else bytes
    }

    private fun writeTextFrame(target: ByteArrayOutputStream, id: String, value: String) {
        if (value.isBlank()) return
        val payload = ByteArrayOutputStream()
        payload.write(ENCODING_UTF16)
        payload.write(utf16(value))
        writeFrame(target, id, payload.toByteArray())
    }

    private fun writePictureFrame(
        target: ByteArrayOutputStream,
        artwork: ByteArray,
        mime: String,
    ) {
        val payload = ByteArrayOutputStream()
        payload.write(ENCODING_UTF16)
        payload.write(mime.toByteArray(Charsets.US_ASCII))
        payload.write(0)
        payload.write(PICTURE_TYPE_COVER_FRONT)
        // Пустое описание: BOM и терминатор UTF-16.
        payload.write(0xFF)
        payload.write(0xFE)
        payload.write(0)
        payload.write(0)
        payload.write(artwork)
        writeFrame(target, "APIC", payload.toByteArray())
    }

    private fun writeFrame(target: ByteArrayOutputStream, id: String, payload: ByteArray) {
        target.write(id.toByteArray(Charsets.US_ASCII))
        target.write(byteArrayOf(
            ((payload.size ushr 24) and 0xFF).toByte(),
            ((payload.size ushr 16) and 0xFF).toByte(),
            ((payload.size ushr 8) and 0xFF).toByte(),
            (payload.size and 0xFF).toByte(),
        ))
        target.write(0)
        target.write(0)
        target.write(payload)
    }

    /** UTF-16 с BOM: кириллица без потерь в любой читалке тегов. */
    private fun utf16(value: String): ByteArray {
        val bytes = value.toByteArray(Charset.forName("UTF-16LE"))
        return byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + bytes
    }

    /** Синхсайф-размер: 7 бит на байт, старший бит всегда 0. */
    private fun writeSynchsafe(target: ByteArray, offset: Int, value: Int) {
        target[offset] = ((value ushr 21) and 0x7F).toByte()
        target[offset + 1] = ((value ushr 14) and 0x7F).toByte()
        target[offset + 2] = ((value ushr 7) and 0x7F).toByte()
        target[offset + 3] = (value and 0x7F).toByte()
    }
}
