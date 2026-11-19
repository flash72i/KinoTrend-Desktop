package app.kinotrend.desktop.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import app.kinotrend.desktop.core.AppPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

/**
 * Загрузка постеров. Кэш трёхуровневый: байты на диске → декодированный битмап в памяти.
 * Одинаковые одновременные запросы схлопываются в один (Backdrop и Header на карточке
 * фильма грузят один и тот же постер), а битмапы масштабируются — иначе сетка каталога
 * съедала сотни мегабайт и подтормаживала интерфейс.
 */
object PosterLoader {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Сколько декодированных постеров держать в памяти (раньше было 180 — до 150 МБ). */
    private const val MEMORY_LIMIT = 96

    private val memory = object : LinkedHashMap<String, ImageBitmap?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap?>): Boolean =
            size > MEMORY_LIMIT
    }
    private val memoryLock = Any()

    /** Блокировки по ключу: один запуск загрузки на картинку. */
    private val keyLocks = ConcurrentHashMap<String, Any>()

    private val dir: File by lazy { File(AppPaths.dataDir, "posters").apply { mkdirs() } }

    suspend fun load(url: String, maxSize: Int = 480): ImageBitmap? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        val key = "$url@$maxSize"
        cached(key)?.let { return@withContext it }

        val keyLock = keyLocks.computeIfAbsent(key) { Any() }
        try {
            synchronized(keyLock) {
                cached(key)?.let { return@synchronized it }

                val file = cacheFile(url)
                migrateLegacy(url, file)
                val bytes = if (file.isFile) {
                    runCatching { file.readBytes() }.getOrNull()
                } else {
                    download(url)?.also { data ->
                        runCatching { file.writeBytes(data) }
                    }
                }

                val bitmap = bytes?.let { decodeScaled(it, maxSize) }
                // Ошибки не кэшируем: null в LRU всё равно читается как промах.
                if (bitmap != null) {
                    synchronized(memoryLock) { memory[key] = bitmap }
                }
                bitmap
            }
        } finally {
            // Не держим блокировки для постеров, которые больше не загружаются.
            if (keyLocks.size > 256) keyLocks.remove(key, keyLock)
        }
    }

    private fun cached(key: String): ImageBitmap? = synchronized(memoryLock) { memory[key] }

    private fun cacheFile(url: String): File {
        val digest = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
        val name = digest.joinToString("") { "%02x".format(it) }.take(20)
        return File(dir, "$name.img")
    }

    /** Старые файлы назывались по url.hashCode() — переносим их под новый ключ. */
    private fun migrateLegacy(url: String, target: File) {
        runCatching {
            val legacy = File(dir, Integer.toHexString(url.hashCode()) + ".img")
            if (legacy.isFile && legacy != target) {
                legacy.renameTo(target)
            }
        }
    }

    private fun decodeScaled(bytes: ByteArray, maxSize: Int): ImageBitmap? = runCatching {
        val source = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
        val longest = maxOf(source.width, source.height)
        if (longest <= maxSize) return source.toComposeImageBitmap()

        val scale = maxSize.toDouble() / longest
        val width = maxOf(1, (source.width * scale).toInt())
        val height = maxOf(1, (source.height * scale).toInt())
        val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = scaled.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.drawImage(source, 0, 0, width, height, null)
        } finally {
            graphics.dispose()
        }
        scaled.toComposeImageBitmap()
    }.getOrNull()

    private fun download(url: String): ByteArray? = runCatching {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body!!.bytes()
        }
    }.getOrNull()
}
