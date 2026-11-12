package com.facet.music.core.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class Id3WriterTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun newFile(bytes: ByteArray = ByteArray(64) { 0x55 }): File =
        folder.newFile("track.mp3").apply { writeBytes(bytes) }

    private fun frameIndex(bytes: ByteArray, id: String): Int {
        val needle = id.toByteArray(Charsets.US_ASCII)
        outer@ for (i in 0..bytes.size - needle.size) {
            for (j in needle.indices) if (bytes[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    @Test
    fun `writes title artist and cover frames`() {
        val file = newFile()
        val artwork = ByteArray(16) { it.toByte() }

        assertTrue(Id3Writer.write(file, "Название", "Артист", artwork))

        val bytes = file.readBytes()
        assertEquals('I'.code.toByte(), bytes[0])
        assertEquals('D'.code.toByte(), bytes[1])
        assertEquals('3'.code.toByte(), bytes[2])
        assertEquals(0x03.toByte(), bytes[3])
        assertTrue(frameIndex(bytes, "TIT2") > 0)
        assertTrue(frameIndex(bytes, "TPE1") > 0)
        assertTrue(frameIndex(bytes, "APIC") > 0)
    }

    @Test
    fun `second write does not duplicate tags`() {
        val file = newFile()
        Id3Writer.write(file, "Title", "Artist", null)
        Id3Writer.write(file, "Title", "Artist", null)

        assertEquals(1, countOccurrences(file.readBytes(), "TIT2"))
    }

    private fun countOccurrences(bytes: ByteArray, id: String): Int {
        val needle = id.toByteArray(Charsets.US_ASCII)
        var count = 0
        outer@ for (i in 0..bytes.size - needle.size) {
            for (j in needle.indices) if (bytes[i + j] != needle[j]) continue@outer
            count++
        }
        return count
    }

    @Test
    fun `detects artwork mime by signature`() {
        assertEquals("image/jpeg", Id3Writer.artworkMimeType(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
        assertEquals(
            "image/png",
            Id3Writer.artworkMimeType(
                byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()),
            ),
        )
        assertEquals(
            "image/webp",
            Id3Writer.artworkMimeType(
                "RIFF0000WEBP".toByteArray(Charsets.US_ASCII),
            ),
        )
    }

    @Test
    fun `missing file is not modified`() {
        val file = File(folder.root, "absent.mp3")

        assertTrue(!Id3Writer.write(file, "Title", "Artist", null))
    }
}
