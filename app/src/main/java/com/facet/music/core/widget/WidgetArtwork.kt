package com.facet.music.core.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object WidgetArtworkStore {

    private const val DIR_NAME = "widget_art"
    private const val COVER_NAME = "cover.png"
    private const val BACKDROP_NAME = "backdrop.png"
    private const val TOKEN_NAME = "token"
    private const val MAX_BYTES = 3 * 1024 * 1024
    private const val DECODE_LIMIT = 512
    private const val COVER_PX = 192
    private const val BACKDROP_WIDTH = 256
    private const val BACKDROP_HEIGHT = 160
    private const val USER_AGENT = "Facet/1.1 (Android)"

    private data class Cached(val path: String, val modified: Long, val bitmap: Bitmap)

    @Volatile
    private var coverCache: Cached? = null

    @Volatile
    private var backdropCache: Cached? = null

    fun cover(context: Context): Bitmap? = decodeCached(coverFile(context), isCover = true)

    fun backdrop(context: Context): Bitmap? = decodeCached(backdropFile(context), isCover = false)

    fun hasArtwork(context: Context): Boolean =
        coverFile(context).exists() && backdropFile(context).exists()

    suspend fun refresh(
        context: Context,
        trackId: String,
        artworkUrl: String,
        client: OkHttpClient,
    ): Boolean = withContext(Dispatchers.IO) {
        if (trackId.isBlank() || artworkUrl.isBlank()) return@withContext false
        val token = readToken(context)
        val ready = hasArtwork(context) && token == trackId
        if (ready) return@withContext false
        val source = runCatching { download(client, artworkUrl) }.getOrNull()
            ?: return@withContext false
        runCatching {
            val cover = renderSquare(source, COVER_PX, 0.22f)
            val backdrop = renderBackdrop(source, BACKDROP_WIDTH, BACKDROP_HEIGHT, 0.16f)
            save(coverFile(context), cover)
            save(backdropFile(context), backdrop)
            if (cover !== source) cover.recycle()
            if (backdrop !== source) backdrop.recycle()
            source.recycle()
            tokenFile(context).writeText(trackId)
            coverCache = null
            backdropCache = null
            true
        }.getOrDefault(false)
    }

    private fun directory(context: Context): File = File(context.cacheDir, DIR_NAME)

    private fun coverFile(context: Context): File = File(directory(context), COVER_NAME)

    private fun backdropFile(context: Context): File = File(directory(context), BACKDROP_NAME)

    private fun tokenFile(context: Context): File = File(directory(context), TOKEN_NAME)

    private fun readToken(context: Context): String =
        runCatching { tokenFile(context).readText() }.getOrDefault("")

    private fun decodeCached(file: File, isCover: Boolean): Bitmap? {
        if (!file.exists()) return null
        val path = file.absolutePath
        val modified = file.lastModified()
        val current = (if (isCover) coverCache else backdropCache)
        if (current != null && current.path == path && current.modified == modified) {
            return current.bitmap
        }
        val bitmap = runCatching { BitmapFactory.decodeFile(path) }.getOrNull() ?: return null
        val cached = Cached(path, modified, bitmap)
        if (isCover) coverCache = cached else backdropCache = cached
        return bitmap
    }

    private fun save(file: File, bitmap: Bitmap) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temp).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    private fun download(client: OkHttpClient, url: String): Bitmap? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            val bytes = readLimited(body.byteStream(), MAX_BYTES) ?: return null
            return decode(bytes)
        }
    }

    private fun readLimited(stream: InputStream, limit: Int): ByteArray? = stream.use { input ->
        val output = ByteArrayOutputStream(16 * 1024)
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > limit) return null
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > DECODE_LIMIT) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    private fun renderSquare(source: Bitmap, size: Int, radiusRatio: Float): Bitmap {
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            shader = coverShader(source, size, size)
        }
        val radius = size * radiusRatio
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radius, radius, paint)
        return output
    }

    private fun renderBackdrop(
        source: Bitmap,
        width: Int,
        height: Int,
        radiusRatio: Float,
    ): Bitmap {
        val cropped = crop(source, width, height)
        val tinyHeight = (24f * height / width).toInt().coerceAtLeast(1)
        val tiny = Bitmap.createScaledBitmap(cropped, 24, tinyHeight, true)
        val soft = Bitmap.createScaledBitmap(tiny, width, height, true)
        if (tiny !== soft) tiny.recycle()
        if (cropped !== soft && cropped !== tiny) cropped.recycle()
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            shader = BitmapShader(soft, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val radius = height * radiusRatio
        canvas.drawRoundRect(
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            radius,
            radius,
            paint,
        )
        soft.recycle()
        return output
    }

    private fun coverShader(source: Bitmap, width: Int, height: Int): BitmapShader {
        val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val scale = maxOf(width.toFloat() / source.width, height.toFloat() / source.height)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                (width - source.width * scale) / 2f,
                (height - source.height * scale) / 2f,
            )
        }
        shader.setLocalMatrix(matrix)
        return shader
    }

    private fun crop(source: Bitmap, width: Int, height: Int): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            shader = coverShader(source, width, height)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return output
    }
}
