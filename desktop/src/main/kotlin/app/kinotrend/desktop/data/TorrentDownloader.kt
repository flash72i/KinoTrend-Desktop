package app.kinotrend.desktop.data

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object TorrentDownloader {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun suggestedFile(torrent: Torrent): File {
        val dir = File(System.getProperty("user.home") ?: ".", "Downloads").apply { mkdirs() }
        val base = torrent.name.ifBlank { torrent.type.ifBlank { "torrent" } }
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(120)
        return File(dir, "$base.torrent")
    }

    fun download(torrent: Torrent, target: File): String {
        val url = torrent.link.ifBlank { return "Ссылка на .torrent отсутствует" }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return "Ошибка HTTP ${response.code}"
            val body = response.body ?: return "Пустой ответ"
            target.parentFile?.mkdirs()
            body.byteStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            return "Сохранено: ${target.absolutePath}"
        }
    }
}
