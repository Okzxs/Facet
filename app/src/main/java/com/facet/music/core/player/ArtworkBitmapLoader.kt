package com.facet.music.core.player

import android.content.Context
import android.graphics.Bitmap
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.session.CacheBitmapLoader
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

/**
 * Обложка для Now Bar/шторки: CacheBitmapLoader не перезагружает картинку
 * на каждом обновлении, а размер ограничивается 512 px.
 */
@UnstableApi
class ArtworkBitmapLoader(context: Context) : BitmapLoader {

    private val delegate = CacheBitmapLoader(DataSourceBitmapLoader(context))

    override fun supportsMimeType(mimeType: String): Boolean =
        delegate.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        scale(delegate.decodeBitmap(data))

    override fun loadBitmap(uri: android.net.Uri): ListenableFuture<Bitmap> =
        scale(delegate.loadBitmap(uri))

    private fun scale(source: ListenableFuture<Bitmap>): ListenableFuture<Bitmap> =
        Futures.transform(
            source,
            { bitmap ->
                val width = bitmap.width
                val height = bitmap.height
                if (width <= MAX_SIZE && height <= MAX_SIZE) {
                    bitmap
                } else {
                    val ratio = MAX_SIZE.toFloat() / maxOf(width, height)
                    val scaled = Bitmap.createScaledBitmap(
                        bitmap,
                        (width * ratio).toInt().coerceAtLeast(1),
                        (height * ratio).toInt().coerceAtLeast(1),
                        true,
                    )
                    if (scaled !== bitmap) bitmap.recycle()
                    scaled
                }
            },
            MoreExecutors.directExecutor(),
        )

    private companion object {
        const val MAX_SIZE = 512
    }
}
