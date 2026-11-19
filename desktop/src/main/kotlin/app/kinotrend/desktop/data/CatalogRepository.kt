package app.kinotrend.desktop.data

import app.kinotrend.desktop.core.AppPaths
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class CatalogRepository(
    private val client: OkHttpClient = defaultClient(),
) {
    companion object {
        private const val PRIMARY = "http://kino-trend.ru/kinotrend/data.json"
        private const val MIRROR = "http://nemiroff.insomnia247.nl/kinotrend/data.json"
        private const val TTL_MS = 2 * 60 * 60 * 1000L

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val cacheFile: File get() = File(AppPaths.dataDir, "catalog.json")

    fun load(force: Boolean = false, useMirror: Boolean = false): Catalog {
        val cached = readCache()
        if (!force && cached != null && isFresh()) return cached

        var lastError: Throwable? = null
        val urls = if (useMirror) listOf(MIRROR, PRIMARY) else listOf(PRIMARY, MIRROR)
        for (url in urls) {
            try {
                val text = fetch(url)
                val catalog = parse(text)
                if (catalog.movies.isNotEmpty()) {
                    writeCache(text)
                    return catalog
                }
            } catch (error: Throwable) {
                lastError = error
            }
        }

        cached?.let { return it }
        throw lastError ?: IllegalStateException("Каталог недоступен")
    }

    private fun fetch(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "KinoTrendDesktop/2.3.8")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    private fun parse(text: String): Catalog {
        val root = JSONObject(text)
        val moviesJson: JSONArray = root.optJSONArray("movies") ?: JSONArray()
        val movies = ArrayList<Movie>(moviesJson.length())
        for (index in 0 until moviesJson.length()) {
            val item = moviesJson.optJSONObject(index) ?: continue
            movies.add(parseMovie(item))
        }
        return Catalog(count = root.optInt("count", movies.size), movies = movies)
    }

    private fun parseMovie(item: JSONObject): Movie {
        val torrentsJson = item.optJSONArray("torrents") ?: JSONArray()
        val torrents = ArrayList<Torrent>(torrentsJson.length())
        for (index in 0 until torrentsJson.length()) {
            val torrent = torrentsJson.optJSONObject(index) ?: continue
            torrents.add(
                Torrent(
                    name = torrent.optString("name"),
                    link = torrent.optString("link"),
                    magnet = torrent.optString("magnet"),
                    date = torrent.optString("date"),
                    type = torrent.optString("type"),
                    audio = torrent.optString("audio"),
                    size = torrent.optString("size"),
                    seeders = torrent.optInt("seeders", -1).takeIf { it >= 0 },
                    leechers = torrent.optInt("leechers", -1).takeIf { it >= 0 },
                    quality = torrent.optInt("quality", 0),
                    dv = torrent.optBoolean("dv", false),
                )
            )
        }

        return Movie(
            filmID = item.optInt("filmID", 0),
            nameRU = item.optString("nameRU"),
            nameOriginal = item.optString("nameOriginal"),
            description = item.optString("description"),
            year = item.optString("year"),
            country = item.optString("country"),
            genre = item.optString("genre"),
            rating = item.optString("rating"),
            ratingFloat = item.optDouble("ratingFloat", 0.0).toFloat(),
            ratingKP = item.optString("ratingKP"),
            ratingIMDb = item.optString("ratingIMDb"),
            ratingAgeLimits = item.optString("ratingAgeLimits"),
            posterURL = item.optString("posterURL"),
            bigPosterURL = item.optString("bigPosterURL"),
            trailerURL = item.optString("trailerURL"),
            filmLength = item.optString("filmLength"),
            torrents = torrents,
        )
    }

    private fun readCache(): Catalog? = runCatching {
        if (!cacheFile.isFile) return null
        parse(cacheFile.readText())
    }.getOrNull()

    private fun writeCache(text: String) {
        runCatching { cacheFile.writeText(text) }
    }

    private fun isFresh(): Boolean {
        if (!cacheFile.isFile) return false
        return System.currentTimeMillis() - cacheFile.lastModified() < TTL_MS
    }

    fun clearCache() {
        runCatching { cacheFile.delete() }
        runCatching { File(AppPaths.dataDir, "posters").deleteRecursively() }
    }
}
