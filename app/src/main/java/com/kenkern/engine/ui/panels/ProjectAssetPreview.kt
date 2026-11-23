package com.kenkern.engine.ui.panels

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.kenkern.engine.assets.browser.AssetCategory
import com.kenkern.engine.assets.browser.AssetPreviewMetadata
import com.kenkern.engine.assets.browser.MeshPreviewMetadata
import com.kenkern.engine.assets.browser.PreviewTooLarge
import com.kenkern.engine.assets.browser.PreviewUnsupported
import com.kenkern.engine.assets.browser.ProjectAsset
import com.kenkern.engine.assets.browser.ProjectAssetBrowser
import java.io.File
import java.io.IOException
import java.nio.channels.Channels
import java.nio.channels.SeekableByteChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardOpenOption.READ
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal sealed interface ProjectAssetPreview {
    data class Image(val bitmap: Bitmap, val width: Int, val height: Int) : ProjectAssetPreview
    data class Mesh(val metadata: MeshPreviewMetadata) : ProjectAssetPreview
    data object Unavailable : ProjectAssetPreview
    data object TooLarge : ProjectAssetPreview
    data object CannotRead : ProjectAssetPreview
    data object Gone : ProjectAssetPreview
}

// BitmapFactory is blocking. Keep canceled reads from piling up on rapid selection changes.
private val previewMutex = Mutex()

internal suspend fun loadAssetPreview(root: File, asset: ProjectAsset): ProjectAssetPreview = previewMutex.withLock {
    withContext(Dispatchers.IO) {
        val extension = asset.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension != "kern" && asset.category != AssetCategory.TEXTURE) return@withContext ProjectAssetPreview.Unavailable
        try {
            val file = ProjectAssetBrowser.resolve(root, asset.path) ?: return@withContext ProjectAssetPreview.Gone
            Files.newByteChannel(file, READ, NOFOLLOW_LINKS).use { channel ->
                when (extension) {
                    "kern" -> ProjectAssetPreview.Mesh(AssetPreviewMetadata.mesh(channel))
                    "kimg" -> {
                        val image = AssetPreviewMetadata.texture(channel)
                        ProjectAssetPreview.Image(
                            Bitmap.createBitmap(image.argb, image.width, image.height, Bitmap.Config.ARGB_8888),
                            image.originalWidth, image.originalHeight
                        )
                    }
                    else -> decodeSampledImage(channel)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: PreviewTooLarge) {
            ProjectAssetPreview.TooLarge
        } catch (_: PreviewUnsupported) {
            ProjectAssetPreview.Unavailable
        } catch (_: java.nio.file.NoSuchFileException) {
            ProjectAssetPreview.Gone
        } catch (_: Exception) {
            ProjectAssetPreview.CannotRead
        }
    }
}

private fun decodeSampledImage(channel: SeekableByteChannel): ProjectAssetPreview.Image {
    if (channel.size() > 64L * 1024 * 1024) throw PreviewTooLarge()
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeStream(Channels.newInputStream(channel), null, bounds)
    val w = bounds.outWidth
    val h = bounds.outHeight
    if (w <= 0 || h <= 0) throw IOException("No image dimensions")
    if (maxOf(w, h) > 32768 || w.toLong() * h > 100_000_000) throw PreviewTooLarge()
    var sample = 1
    while ((maxOf(w, h) + sample - 1) / sample > AssetPreviewMetadata.THUMBNAIL_EDGE) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
    channel.position(0)
    val bitmap = BitmapFactory.decodeStream(Channels.newInputStream(channel), null, options)
        ?: throw IOException("Image decode failed")
    return ProjectAssetPreview.Image(bitmap, w, h)
}
