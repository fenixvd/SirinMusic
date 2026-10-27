package ru.rainedev.sirinmusic.sharing

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.ContextCompat
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.rainedev.sirinmusic.R
import ru.rainedev.sirinmusic.data.Track
import ru.rainedev.sirinmusic.strings
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.max

/** Render only public track metadata and artwork, never the app screen or server URL/token. */
internal suspend fun createTrackShareCard(context: Context, track: Track, artworkUrl: String?): File {
    val artwork = try {
        withTimeoutOrNull(8_000) {
            val request = ImageRequest.Builder(context).data(artworkUrl)
                .size(1024).allowHardware(false).build()
            (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null // A missing/offline cover still produces a branded card.
    }
    val bitmap = withContext(Dispatchers.Default) { renderTrackShareCard(context, track, artwork) }
    try {
        return withContext(Dispatchers.IO) {
            val folder = File(context.cacheDir, "shared_tracks").apply { check(mkdirs() || isDirectory) }
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
            folder.listFiles()?.filter { it.isFile && it.lastModified() < cutoff }?.forEach { it.delete() }
            val target = File(folder, "sirin-${UUID.randomUUID()}.png")
            try {
                target.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                target
            } catch (e: Exception) {
                target.delete()
                throw e
            }
        }
    } finally {
        bitmap.recycle()
    }
}

private fun renderTrackShareCard(context: Context, track: Track, artwork: Bitmap?): Bitmap {
    val width = 1080
    val margin = 88f
    val contentWidth = width - (margin * 2).toInt()
    val ivory = Color.rgb(248, 238, 228)
    val copper = Color.rgb(223, 142, 93)
    fun text(value: String, size: Float, color: Int, bold: Boolean = false, lines: Int): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        return StaticLayout.Builder.obtain(value, 0, value.length, paint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setIncludePad(false).setLineSpacing(8f, 1f)
            .setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END)
            .build()
    }
    val title = text(track.title?.takeIf { it.isNotBlank() } ?: "—", 64f, ivory, bold = true, lines = 3)
    val artist = track.artist?.takeIf { it.isNotBlank() }?.let { text(it, 42f, ivory, lines = 2) }
    val album = track.album?.takeIf { it.isNotBlank() }?.let { text(it, 30f, Color.rgb(181, 169, 165), lines = 1) }
    val coverTop = 224f
    val textTop = coverTop + contentWidth + 56f
    val height = max(1450, (textTop + title.height + (artist?.height?.plus(20) ?: 0) +
        (album?.height?.plus(20) ?: 0) + 96).toInt())
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(),
        intArrayOf(Color.rgb(20, 28, 35), Color.rgb(45, 29, 34), Color.rgb(24, 25, 32)),
        null, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    paint.shader = RadialGradient(1000f, 160f, 850f,
        intArrayOf(Color.argb(50, 223, 142, 93), Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    paint.shader = null

    // The same vector mark as the application icon, rather than a screenshot of app chrome.
    val logo = checkNotNull(ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground)).mutate()
    paint.color = Color.rgb(49, 37, 36)
    canvas.drawRoundRect(margin, 64f, margin + 112f, 176f, 28f, 28f, paint)
    logo.setBounds(margin.toInt() - 8, 56, margin.toInt() + 120, 184)
    logo.draw(canvas)
    paint.color = ivory
    paint.textSize = 38f
    paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    canvas.drawText("Sirin Music", margin + 140f, 110f, paint)
    paint.color = copper
    paint.textSize = 27f
    paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    canvas.drawText(strings(R.string.track_share_listening), margin + 140f, 154f, paint)

    val cover = RectF(margin, coverTop, margin + contentWidth, coverTop + contentWidth)
    paint.color = Color.argb(55, 0, 0, 0)
    canvas.drawRoundRect(RectF(cover).apply { offset(0f, 18f) }, 36f, 36f, paint)
    val saved = canvas.save()
    canvas.clipPath(Path().apply { addRoundRect(cover, 36f, 36f, Path.Direction.CW) })
    if (artwork != null) {
        val edge = minOf(artwork.width, artwork.height)
        val left = (artwork.width - edge) / 2
        val top = (artwork.height - edge) / 2
        canvas.drawBitmap(artwork, Rect(left, top, left + edge, top + edge), cover, paint.apply { color = Color.WHITE })
    } else {
        paint.shader = LinearGradient(cover.left, cover.top, cover.right, cover.bottom,
            Color.rgb(85, 54, 50), Color.rgb(29, 36, 47), Shader.TileMode.CLAMP)
        canvas.drawRect(cover, paint)
        paint.shader = null
        logo.setBounds(cover.left.toInt(), cover.top.toInt(), cover.right.toInt(), cover.bottom.toInt())
        logo.draw(canvas)
    }
    canvas.restoreToCount(saved)
    paint.color = Color.argb(40, 248, 238, 228)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2f
    canvas.drawRoundRect(cover, 36f, 36f, paint)
    paint.style = Paint.Style.FILL

    var y = textTop
    fun draw(layout: StaticLayout) {
        val count = canvas.save()
        canvas.translate(margin, y)
        layout.draw(canvas)
        canvas.restoreToCount(count)
        y += layout.height + 20f
    }
    draw(title)
    artist?.let(::draw)
    album?.let(::draw)
    return bitmap
}
