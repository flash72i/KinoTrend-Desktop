package app.kinotrend.desktop.player

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

object HlsResolver {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun resolveVariant(masterUrl: String, preferredHeight: Int = 720): String {
        val request = Request.Builder()
            .url(masterUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val text = response.body?.string().orEmpty()
            return pickVariant(masterUrl, text, preferredHeight) ?: masterUrl
        }
    }

    private fun pickVariant(base: String, playlist: String, preferredHeight: Int): String? {
        val lines = playlist.lineSequence().map { it.trim() }.toList()
        var best: Pair<Int, String>? = null
        for (index in lines.indices) {
            val line = lines[index]
            if (!line.startsWith("#EXT-X-STREAM-INF:")) continue
            val height = Regex("RESOLUTION=\\d+x(\\d+)").find(line)
                ?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val uri = lines.drop(index + 1).firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: continue
            if (uri.contains("redundant=")) continue
            val absolute = runCatching { URI(base).resolve(uri).toString() }.getOrNull() ?: continue
            val score = kotlin.math.abs(height - preferredHeight)
            val current = best
            if (current == null || score < kotlin.math.abs(current.first - preferredHeight)) {
                best = height to absolute
            }
        }
        return best?.second
    }
}
