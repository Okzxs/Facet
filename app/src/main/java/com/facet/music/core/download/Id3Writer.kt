package com.facet.music.core.download

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Минимальный писатель ID3v2.3: название, исполнитель и обложка.
 *
 * Аудио не читается в память: заголовок и фреймы пишутся во временный файл,
 * затем туда же потоково копируется исходный звук. Это важно для больших
 * MP3, где `readBytes()` раздувал heap на десятки мегабайт.
 *
 * Теги пишутся только для MP3. M4A/WebM не трогаем: у них другая структура
 * контейнера, а MP4-атомы moov/udta/meta/ilst слишком рискованны без библиотеки.
 */
object Id3Writer {

    private const val HEADER_SIZE = 10
    private const val ENCODING_UTF16 = 0x01
    private const val PICTURE_TYPE_COVER_FRONT = 0x03
    private const val COPY_BUFFER = 64 * 1024

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

        val temp = File(file.parentFile, file.name + ".tags")
        return try {
            val frames = ByteArrayOutputStream().apply {
                writeTextFrame(this, "TIT2", title)
                writeTextFrame(this, "TPE1", artist)
                if (artwork != null && artwork.isNotEmpty()) {
                    writePictureFrame(this, artwork, artworkMime)
                }
            }.toByteArray()

            val header = ByteArray(HEADER_SIZE).apply {
                this[0] = 'I'.code.toByte()
                this[1] = 'D'.code.toByte()
                this[2] = '3'.code.toByte()
                this[3] = 0x03
                this[4] = 0x00
                this[5] = 0x00
                writeSynchsafe(this, 6, frames.size)
            }

            RandomAccessFile(file, "r").use { source ->
                val audioOffset = existingTagSize(source)
                source.seek(audioOffset)
                temp.outputStream().use { output ->
                    output.write(header)
                    output.write(frames)
                    val buffer = ByteArray(COPY_BUFFER)
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                }
            }

            try {
                Files.move(
                    temp.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } catch (e: FileAlreadyExistsException) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            true
        } catch (e: Exception) {
            temp.delete()
            false
        }
    }

    /** MIME обложки по сигнатуре байтов: JPEG, PNG или WebP. */
    fun artworkMimeType(bytes: ByteArray): String = when {
        bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte() -> "image/jpeg"

        bytes.size >= 4 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'N'.code.toByte() &&
            bytes[3] == 'G'.code.toByte() -> "image/png"

        bytes.size >= 12 &&
            bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray(Charsets.US_ASCII)) &&
            bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray(Charsets.US_ASCII)) ->
            "image/webp"

        else -> "image/jpeg"
    }

    /** Пропускает уже существующий ID3v2-блок в начале файла. */
    private fun existingTagSize(source: RandomAccessFile): Long {
        if (source.length() < HEADER_SIZE) return 0L
        val header = ByteArray(HEADER_SIZE)
        source.seek(0)
        source.readFully(header)
        if (header[0] != 'I'.code.toByte() ||
            header[1] != 'D'.code.toByte() ||
            header[2] != '3'.code.toByte()
        ) {
            return 0L
        }
        val size = ((header[6].toInt() and 0x7F) shl 21) or
            ((header[7].toInt() and 0x7F) shl 14) or
            ((header[8].toInt() and 0x7F) shl 7) or
            (header[9].toInt() and 0x7F)
        val total = HEADER_SIZE.toLong() + size
        return if (total in 0 until source.length()) total else 0L
    }

    private fun writeTextFrame(target: ByteArrayOutputStream, id: String, value: String) {
        if (value.isBlank()) return
        val payload = ByteArrayOutputStream().apply {
            write(ENCODING_UTF16)
            write(utf16(value))
        }
        writeFrame(target, id, payload.toByteArray())
    }

    private fun writePictureFrame(
        target: ByteArrayOutputStream,
        artwork: ByteArray,
        mime: String,
    ) {
        val payload = ByteArrayOutputStream().apply {
            write(ENCODING_UTF16)
            write(mime.toByteArray(Charsets.US_ASCII))
            write(0)
            write(PICTURE_TYPE_COVER_FRONT)
            // Пустое описание: BOM и терминатор UTF-16.
            write(0xFF)
            write(0xFE)
            write(0)
            write(0)
            write(artwork)
        }
        writeFrame(target, "APIC", payload.toByteArray())
    }

    private fun writeFrame(target: ByteArrayOutputStream, id: String, payload: ByteArray) {
        target.write(id.toByteArray(Charsets.US_ASCII))
        target.write(
            byteArrayOf(
                ((payload.size ushr 24) and 0xFF).toByte(),
                ((payload.size ushr 16) and 0xFF).toByte(),
                ((payload.size ushr 8) and 0xFF).toByte(),
                (payload.size and 0xFF).toByte(),
            ),
        )
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
