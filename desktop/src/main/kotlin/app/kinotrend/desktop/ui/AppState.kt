package app.kinotrend.desktop.ui

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.kinotrend.desktop.core.AppPaths
import app.kinotrend.desktop.data.Catalog
import app.kinotrend.desktop.data.CatalogRepository
import app.kinotrend.desktop.data.Movie
import app.kinotrend.desktop.data.Torrent
import app.kinotrend.desktop.data.TorrServerManager
import app.kinotrend.desktop.data.VlcDownloader
import app.kinotrend.desktop.data.isDubbed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AppState(private val startBackground: Boolean = true) {
    private val repository = CatalogRepository()
    private val prefsFile = File(AppPaths.dataDir, "preferences.json")

    var loading by mutableStateOf(true)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var catalog by mutableStateOf<Catalog?>(null)
        private set

    var selectedMovie by mutableStateOf<Movie?>(null)
        private set

    /** История открытых карточек: шаг назад и шаг вперёд. */
    private val movieBackStack = ArrayDeque<Movie>()
    private val movieForwardStack = ArrayDeque<Movie>()

    var playerUrl by mutableStateOf<String?>(null)
        private set

    var playerTitle by mutableStateOf("")
        private set

    var query by mutableStateOf("")

    var sortBy by mutableStateOf("date")

    var selectedGenres by mutableStateOf<Set<String>>(emptySet())

    var selectedCountries by mutableStateOf<Set<String>>(emptySet())

    var minRating by mutableStateOf(0f)

    var showFilters by mutableStateOf(false)

    var showSettings by mutableStateOf(false)

    var useMirror by mutableStateOf(false)
        private set

    var widePoster by mutableStateOf(false)

    var density by mutableStateOf("normal")

    var dubbingOnly by mutableStateOf(false)

    var saveFilters by mutableStateOf(true)

    var playerPath by mutableStateOf("")

    /** Показывать список плееров при каждом запуске раздачи. */
    var askPlayer by mutableStateOf(true)

    var statusMessage by mutableStateOf<String?>(null)
        private set

    val allGenres: List<String> by derivedStateOf {
        catalog?.movies.orEmpty()
            .flatMap { it.genre.split(",") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }

    val allCountries: List<String> by derivedStateOf {
        catalog?.movies.orEmpty()
            .flatMap { it.country.split(",") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }

    val hasActiveFilters: Boolean
        get() = selectedGenres.isNotEmpty() || selectedCountries.isNotEmpty() || minRating > 0f

    val filteredMovies: List<Movie> by derivedStateOf {
        val tokens = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val filtered = catalog?.movies.orEmpty().filter { movie ->
            val genres = movie.genre.split(",").map { it.trim() }
            val countries = movie.country.split(",").map { it.trim() }
            val matchesQuery = tokens.isEmpty() || tokens.all { token ->
                movie.nameRU.lowercase().contains(token) ||
                    movie.nameOriginal.lowercase().contains(token)
            }
            // «Только дубляж»: фильмы без раздачи с дубляжом в каталог не попадают.
            val matchesAudio = !dubbingOnly || movie.torrents.any { it.isDubbed() }
            matchesQuery &&
                matchesAudio &&
                (selectedGenres.isEmpty() || selectedGenres.any { it in genres }) &&
                (selectedCountries.isEmpty() || selectedCountries.any { it in countries }) &&
                (minRating <= 0f || movie.ratingFloat >= minRating)
        }
        when (sortBy) {
            "rating" -> filtered.sortedByDescending { it.ratingFloat }
            "name" -> filtered.sortedBy { it.nameRU.lowercase() }
            else -> filtered
        }
    }

    /** Сколько фильмов каталога остаётся при включённом «Только дубляж». */
    val dubbingMovieCount: Int by derivedStateOf {
        val movies = catalog?.movies.orEmpty()
        if (!dubbingOnly) movies.size else movies.count { it.torrents.any { torrent -> torrent.isDubbed() } }
    }

    suspend fun load(force: Boolean = false) {
        loading = true
        error = null
        runCatching { withContext(Dispatchers.IO) { repository.load(force, useMirror) } }
            .onSuccess { catalog = it }
            .onFailure { error = it.message ?: "Не удалось загрузить каталог" }
        loading = false
    }

    suspend fun setMirror(enabled: Boolean) {
        if (useMirror == enabled) return
        useMirror = enabled
        persist()
        load(force = true)
    }

    suspend fun clearCache() {
        withContext(Dispatchers.IO) { repository.clearCache() }
        statusMessage = "Кэш очищен"
        load(force = true)
    }

    fun resetFilters() {
        selectedGenres = emptySet()
        selectedCountries = emptySet()
        minRating = 0f
        persist()
    }

    fun openMovie(movie: Movie) {
        selectedMovie?.let { movieBackStack.addLast(it) }
        movieForwardStack.clear()
        selectedMovie = movie
    }

    fun closeMovie() {
        navigateBack()
    }

    /**
     * Шаг назад: плеер → панель настроек/фильтров → предыдущий фильм → главный экран.
     * Используется и боковой кнопкой мыши, и стрелкой «←» на карточке.
     */
    fun navigateBack(): Boolean {
        if (showSettings) {
            showSettings = false
            return true
        }
        if (showFilters) {
            showFilters = false
            return true
        }
        if (playerUrl != null) {
            closePlayer()
            return true
        }
        val current = selectedMovie
        if (current != null) {
            movieForwardStack.addFirst(current)
            selectedMovie = movieBackStack.removeLastOrNull()
            return true
        }
        if (query.isNotBlank() || hasActiveFilters) {
            query = ""
            resetFilters()
            return true
        }
        return false
    }

    /** Шаг вперёд по истории фильмов. */
    fun navigateForward(): Boolean {
        val next = movieForwardStack.removeFirstOrNull() ?: return false
        selectedMovie?.let { movieBackStack.addLast(it) }
        selectedMovie = next
        return true
    }

    var foundPlayers by mutableStateOf<List<Pair<String, String>>>(emptyList())
        private set

    suspend fun installVlc(onStatus: (String) -> Unit): Boolean {
        onStatus("Скачивание VLC…")
        return runCatching {
            val file = withContext(Dispatchers.IO) {
                VlcDownloader.install(File(AppPaths.appDir, "players")) { done, total ->
                    if (total > 0) onStatus("VLC: ${done * 100 / total}%")
                }
            }
            playerPath = file.absolutePath
            foundPlayers = foundPlayers.filterNot { it.second == file.absolutePath } + ("VLC" to file.absolutePath)
            persist()
            onStatus("VLC установлен")
            true
        }.getOrElse {
            onStatus("Ошибка VLC: ${it.message}")
            false
        }
    }

    suspend fun playTorrent(
        torrent: Torrent,
        selectedPlayer: String? = null,
        onStatus: (String, Float?) -> Unit,
    ): String? {
        val player = selectedPlayer?.takeIf { it.isNotBlank() } ?: playerPath
        if (player.isBlank() || !File(player).isFile) return "Выберите плеер в настройках"
        val source = torrent.magnet.ifBlank { torrent.link }
        if (source.isBlank()) return "У раздачи нет magnet-ссылки"

        val started = System.currentTimeMillis()
        logPlayer("play: start TorrServer")
        onStatus("Запуск TorrServer…", null)
        runCatching {
            withContext(Dispatchers.IO) { TorrServerManager.start() }
        }.getOrElse { return "TorrServer: ${it.message}" }

        // Кэш предзагрузки: раздача уже готова — плеер открывается без обращения к серверу.
        // Дополнительно проверяем поток: TorrServer мог выбросить торрент или перезапуститься.
        val ready = TorrServerManager.preparedFor(source)
        val streamAlive = ready != null && withContext(Dispatchers.IO) {
            TorrServerManager.streamIsReady(ready.url, 3_000)
        }
        val prepared = if (ready != null && streamAlive) {
            logPlayer("play: reuse prepared hash=${ready.hash} index=${ready.index}")
            ready
        } else {
            if (ready != null) logPlayer("play: prepared stream stale hash=${ready.hash}, preparing again")
            onStatus("Подготовка раздачи…", null)
            runCatching {
                withContext(Dispatchers.IO) { TorrServerManager.prepare(source) }
            }.getOrElse { return "TorrServer: ${it.message}" }
        }

        val stream = prepared.url
        logPlayer("play: stream ready in ${System.currentTimeMillis() - started} ms -> $stream")
        onStatus("Открываю плеер…", null)

        val exe = File(player)
        val command = mutableListOf(player)
        if (exe.name.equals("vlc.exe", ignoreCase = true)) {
            command.add("--no-one-instance")
            command.add("--network-caching=500")
            command.add("--no-video-title-show")
        }
        command.add(stream)
        val logFile = File(AppPaths.dataDir, "player.log")
        logFile.appendText("${System.currentTimeMillis()} ${command.joinToString(" ")}\n")

        val process = runCatching {
            ProcessBuilder(command)
                .directory(exe.parentFile)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
                .start()
        }.getOrElse { return "Не удалось запустить плеер: ${it.message}" }

        TorrServerManager.markPlaybackStarted(process)

        // Ждём только появления процесса, а не фиксированную паузу.
        val alive = withContext(Dispatchers.IO) {
            val deadline = System.currentTimeMillis() + 1_500
            while (System.currentTimeMillis() < deadline) {
                if (process.isAlive) return@withContext true
                delay(50)
            }
            process.isAlive
        }
        if (!alive) {
            return "Плеер закрылся (код ${process.exitValue()}). Лог: ${logFile.absolutePath}"
        }
        logPlayer("play: player alive in ${System.currentTimeMillis() - started} ms")
        onStatus("Плеер запущен", 1f)
        return null
    }
    private fun logPlayer(message: String) {
        runCatching {
            File(AppPaths.dataDir, "player.log").appendText("${System.currentTimeMillis()} $message\n")
        }
    }

    fun addPlayer(name: String, path: String) {
        foundPlayers = foundPlayers.filterNot { it.second == path } + (name to path)
        playerPath = path
        persist()
    }

    /** Игрок выбран в окне при запуске: он же становится плеером по умолчанию. */
    fun choosePlayer(path: String, dontAsk: Boolean) {
        if (foundPlayers.none { it.second.equals(path, true) }) return
        playerPath = path
        if (dontAsk) askPlayer = false
        persist()
    }

    fun openInPlayer(url: String): Boolean {
        val path = playerPath
        if (path.isBlank() || !File(path).isFile) return false
        return runCatching { ProcessBuilder(path, url).start() }.isSuccess
    }

    fun openPlayer(url: String, title: String) {
        playerUrl = url
        playerTitle = title
    }

    fun closePlayer() {
        playerUrl = null
    }

    fun persist() {
        runCatching {
            val root = JSONObject()
            root.put("sort", sortBy)
            root.put("mirror", useMirror)
            root.put("widePoster", widePoster)
            root.put("density", density)
            root.put("dubbingOnly", dubbingOnly)
            root.put("saveFilters", saveFilters)
            root.put("player", playerPath)
            root.put("askPlayer", askPlayer)
            root.put("players", JSONArray().apply {
                foundPlayers.forEach { (name, path) -> put(JSONObject().put("name", name).put("path", path)) }
            })
            if (saveFilters) {
                root.put("genres", JSONArray(selectedGenres.toList()))
                root.put("countries", JSONArray(selectedCountries.toList()))
                root.put("minRating", minRating.toDouble())
            }
            prefsFile.writeText(root.toString())
        }
    }

    private fun loadPrefs() {
        runCatching {
            if (!prefsFile.isFile) return
            val root = JSONObject(prefsFile.readText())
            if (root.optInt("schema", 1) < 3) {
                playerPath = ""
                root.remove("player")
                root.put("schema", 3)
                prefsFile.writeText(root.toString())
            }
            sortBy = root.optString("sort", "date")
            useMirror = root.optBoolean("mirror", false)
            widePoster = root.optBoolean("widePoster", false)
            density = root.optString("density", "normal")
            dubbingOnly = root.optBoolean("dubbingOnly", false)
            saveFilters = root.optBoolean("saveFilters", true)
            playerPath = root.optString("player", "")
            askPlayer = root.optBoolean("askPlayer", true)
            foundPlayers = root.optJSONArray("players")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    val path = item.optString("path")
                    if (path.isNotBlank() && File(path).isFile) item.optString("name", "Плеер") to path else null
                }
            } ?: emptyList()
            val downloadedVlc = File(AppPaths.appDir, "players/vlc/vlc.exe")
            if (downloadedVlc.isFile && foundPlayers.none { it.second == downloadedVlc.absolutePath }) {
                foundPlayers = foundPlayers + ("VLC" to downloadedVlc.absolutePath)
            }
            if (playerPath.isBlank() && downloadedVlc.isFile) {
                playerPath = downloadedVlc.absolutePath
            }
            if (playerPath.isNotBlank() && File(playerPath).isFile && foundPlayers.none { it.second == playerPath }) {
                val name = if (playerPath.endsWith("vlc.exe", true)) "VLC" else File(playerPath).nameWithoutExtension
                foundPlayers = foundPlayers + (name to playerPath)
            }
            if (saveFilters) {
                selectedGenres = root.optJSONArray("genres").toStringSet()
                selectedCountries = root.optJSONArray("countries").toStringSet()
                minRating = root.optDouble("minRating", 0.0).toFloat()
            }
        }
    }

    private fun JSONArray?.toStringSet(): Set<String> {
        if (this == null) return emptySet()
        val result = LinkedHashSet<String>()
        for (index in 0 until length()) {
            val value = optString(index)
            if (value.isNotBlank()) result.add(value)
        }
        return result
    }
    init {
        loadPrefs()
        if (startBackground) {
            Thread({
                runCatching { TorrServerManager.start() }
            }, "torrserver-prewarm").apply { isDaemon = true }.start()
        }
    }
}
