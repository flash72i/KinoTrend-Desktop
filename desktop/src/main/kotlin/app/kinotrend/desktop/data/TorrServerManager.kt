package app.kinotrend.desktop.data

import app.kinotrend.desktop.core.AppPaths
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject

/**
 * Локальный TorrServer: превращает torrent-ссылку KinoTrend в HTTP-поток,
 * который открывается во внешнем плеере. Сервер запускается по требованию и живёт рядом
 * с приложением, поэтому скачивать torrent-файл пользователю не нужно.
 *
 * Важно для отзывчивости: долгие операции (опрос метаданных, буферизация) НЕ держат
 * общий монитор — иначе клик по «Смотреть» молча вставал в очередь на 30 секунд.
 * Подготовленные раздачи кэшируются, поэтому повторный клик не делает работу заново.
 */
object TorrServerManager {
    private const val PORT = 8091
    private const val BASE = "http://127.0.0.1:$PORT"
    private const val DOWNLOAD_URL =
        "https://github.com/YouROK/TorrServer/releases/download/MatriX.150/TorrServer-windows-amd64.exe"

    /** Сколько секунд TorrServer держит неактивный торрент в кэше. */
    private const val TORRENT_KEEP_ALIVE_SEC = 900

    /** Подготовленный URL живёт меньше, чем TorrServer держит торрент. */
    private const val PREPARED_TTL_MS = 10 * 60_000L

    /** Верхняя граница кэша: старые раздачи вытесняются, а не копятся до перезапуска. */
    private const val PREPARED_MAX_ENTRIES = 40

    /** Готовые раздачи: magnet -> поток с уже найденным индексом видеофайла. */
    private val preparedCache = ConcurrentHashMap<String, Prepared>()

    /** Блокировки по magnet: одна и та же раздача готовится только один раз. */
    private val keyLocks = ConcurrentHashMap<String, Any>()

    /** Только старт сервера; сетевые запросы по раздачам идут вне этого монитора. */
    private val startLock = Any()

    @Volatile
    private var serverProcess: Process? = null

    @Volatile
    private var startedByUs = false

    private var shutdownHookInstalled = false

    /**
     * Внешний плеер, который сейчас проигрывает поток. Пока процесс жив,
     * TorrServer нельзя останавливать при выходе из приложения.
     */
    @Volatile
    private var playbackProcess: Process? = null

    data class Prepared(
        val url: String,
        val peers: Int,
        val hash: String,
        val index: Int,
        val createdAt: Long = System.currentTimeMillis(),
    )

    fun start() = ensureStarted()

    fun markPlaybackStarted(process: Process? = null) {
        playbackProcess = process
    }

    /** Уже подготовленная раздача — мгновенный ответ без обращения к серверу. */
    fun preparedFor(magnet: String): Prepared? = validPrepared(magnet)

    private fun validPrepared(key: String): Prepared? {
        val entry = preparedCache[key] ?: return null
        if (System.currentTimeMillis() - entry.createdAt > PREPARED_TTL_MS) {
            preparedCache.remove(key, entry)
            log("prepared expired hash=${entry.hash}")
            return null
        }
        return entry
    }

    /**
     * Готовит раздачу: хэш, индекс видеофайла и URL потока.
     * Повторный вызов для того же magnet отдаёт кэш.
     */
    fun prepare(torrentUrl: String, timeoutMs: Long = 24_000): Prepared {
        validPrepared(torrentUrl)?.let { return it }

        val keyLock = keyLocks.computeIfAbsent(torrentUrl) { Any() }
        synchronized(keyLock) {
            validPrepared(torrentUrl)?.let { return it }

            val started = System.currentTimeMillis()
            ensureStarted()

            // Хэш берём из самого магнита — это мгновенно и не требует запроса к серверу.
            val magnetHash = hashFromMagnet(torrentUrl)

            var torrent: JSONObject? = null
            var hash = magnetHash

            // Если сервер уже знает этот хэш (предзагрузка шла параллельно) — адд не нужен.
            if (hash != null) {
                torrent = runCatching {
                    JSONObject(post("/torrents", JSONObject().put("action", "get").put("hash", hash).toString()))
                }.getOrNull()
            }

            val known = torrent?.optJSONArray("file_stats")
            if (known == null || known.length() == 0) {
                val added = JSONObject(
                    post(
                        "/torrents",
                        JSONObject()
                            .put("action", "add")
                            .put("link", torrentUrl)
                            .put("save_to_db", false)
                            .toString(),
                    ),
                )
                hash = added.optString("hash").ifBlank { hash }
                if (hash.isNullOrBlank()) throw IOException("TorrServer не вернул hash торрента")
                torrent = added
            }

            val resolvedHash = hash ?: throw IOException("TorrServer не вернул hash торрента")

            var files = torrent.optJSONArray("file_stats")
            val deadline = System.currentTimeMillis() + timeoutMs
            while ((files == null || files.length() == 0) && System.currentTimeMillis() < deadline) {
                Thread.sleep(400)
                val refreshed = runCatching {
                    JSONObject(post("/torrents", JSONObject().put("action", "get").put("hash", resolvedHash).toString()))
                }.getOrNull() ?: break
                files = refreshed.optJSONArray("file_stats")
                torrent = refreshed
            }

            val file = pickVideoFile(files)
                ?: throw IOException(
                    "Торрент не отдал видеофайлы: ${torrent.optString("stat_string").ifBlank { "нет данных" }}",
                )

            val index = file.optInt("id", 0)
            val fileName = File(file.optString("path")).name
                .ifBlank { torrent.optString("title").ifBlank { "video" } }
            val encodedName = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")
            val url = "$BASE/stream/$encodedName?link=$resolvedHash&index=$index&play"

            val result = Prepared(
                url = url,
                peers = torrent.optInt("total_peers", 0),
                hash = resolvedHash,
                index = index,
                createdAt = System.currentTimeMillis(),
            )
            preparedCache[torrentUrl] = result
            trimPreparedCache()
            log("prepare ${System.currentTimeMillis() - started} ms hash=$resolvedHash index=$index peers=${result.peers}")
            return result
        }
    }

    private fun trimPreparedCache() {
        if (preparedCache.size <= PREPARED_MAX_ENTRIES) return
        preparedCache.entries
            .sortedBy { it.value.createdAt }
            .take(preparedCache.size - PREPARED_MAX_ENTRIES)
            .forEach { preparedCache.remove(it.key, it.value) }
    }

    /** Хэш инфо из magnet-ссылки: xt=urn:btih:<hash>. */
    fun hashFromMagnet(magnet: String): String? {
        if (magnet.isBlank()) return null
        val match = MAGNET_HASH.find(magnet) ?: return null
        val raw = match.groupValues[1]
        return if (raw.length == 40) raw.lowercase() else base32ToHex(raw)
    }

    fun streamUrl(torrentUrl: String): String = prepare(torrentUrl).url

    fun buffer(url: String, targetBytes: Long = 3_000_000, timeoutMs: Long = 60_000, onProgress: (Long) -> Unit): Long {
        val deadline = System.currentTimeMillis() + timeoutMs
        var total = 0L
        while (System.currentTimeMillis() < deadline) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 5_000
                connection.readTimeout = 8_000
                connection.setRequestProperty("Range", "bytes=0-")
                if (connection.responseCode !in 200..299) break
                connection.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (total < targetBytes) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        onProgress(total)
                    }
                }
            } catch (_: Exception) {
            } finally {
                connection.disconnect()
            }
            if (total > 0) return total
        }
        return total
    }

    fun streamIsReady(url: String, timeoutMs: Int = 7_000): Boolean = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 3_000
            connection.readTimeout = timeoutMs
            connection.requestMethod = "GET"
            connection.setRequestProperty("Range", "bytes=0-1023")
            connection.setRequestProperty("User-Agent", "KinoTrendDesktop/2.3.8")
            if (connection.responseCode !in 200..299) return@runCatching false
            val buffer = ByteArray(1024)
            connection.inputStream.use { it.read(buffer) > 0 }
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(false)

    private fun pickVideoFile(files: JSONArray?): JSONObject? {
        if (files == null || files.length() == 0) return null
        val video = ArrayList<JSONObject>()
        for (i in 0 until files.length()) {
            val file = files.optJSONObject(i) ?: continue
            val path = file.optString("path").lowercase()
            if (VIDEO_EXTENSIONS.any { path.endsWith(it) }) video += file
        }
        val candidates = if (video.isNotEmpty()) {
            video
        } else {
            (0 until files.length()).mapNotNull { files.optJSONObject(it) }
        }
        return candidates.maxByOrNull { it.optLong("length", 0L) }
    }

    /**
     * Поднимает сервер. Блокировка удерживается только на время запуска процесса,
     * ожидание готовности идёт вне неё — параллельные вызовы не выстраиваются в очередь.
     */
    private fun ensureStarted() {
        if (echo()) return

        val dataDir = File(AppPaths.dataDir, "torrserver").apply { mkdirs() }
        var launched = false
        synchronized(startLock) {
            if (echo()) return
            if (serverProcess?.isAlive != true) {
                resetBrokenSettings(dataDir)
                launchServer(dataDir)
                launched = true
            }
        }

        if (!awaitEcho(if (launched) 30_000L else 15_000L)) {
            throw IOException("TorrServer не запустился. ${logTail(dataDir)}")
        }

        // На чистой установке settings.json появляется только после первого старта:
        // патчим таймаут и один раз перезапускаем сервер, чтобы он его прочитал.
        if (awaitSettings(dataDir) && keepTorrentsAlive(dataDir)) {
            log("restart TorrServer to apply TorrentDisconnectTimeout=${TORRENT_KEEP_ALIVE_SEC}s")
            runCatching { shutdown() }
            synchronized(startLock) {
                runCatching {
                    serverProcess?.let { process ->
                        if (!process.waitFor(8, java.util.concurrent.TimeUnit.SECONDS)) {
                            process.destroy()
                            Thread.sleep(300)
                        }
                    }
                }
                launchServer(dataDir)
            }
            if (!awaitEcho(30_000L)) {
                throw IOException("TorrServer не перезапустился. ${logTail(dataDir)}")
            }
        }

        startedByUs = true
        installShutdownHook()
    }

    private fun launchServer(dataDir: File) {
        val exe = findBinary() ?: downloadBinary()
        val logFile = File(dataDir, "torrserver.log")
        serverProcess = ProcessBuilder(
            exe.absolutePath,
            "-p", PORT.toString(),
            "-i", "127.0.0.1",
            "-d", dataDir.absolutePath,
            "-l", logFile.absolutePath,
        ).redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(File(dataDir, "launcher.log")))
            .start()
    }

    private fun awaitEcho(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (echo()) return true
            Thread.sleep(250)
        }
        return false
    }

    private fun awaitSettings(dataDir: File, timeoutMs: Long = 8_000): Boolean {
        val settings = File(dataDir, "settings.json")
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (settings.isFile) return true
            Thread.sleep(100)
        }
        return settings.isFile
    }

    private fun logTail(dataDir: File): String = runCatching {
        File(dataDir, "torrserver.log").readLines().takeLast(6).joinToString(" ").take(600)
    }.getOrDefault("").ifBlank { "Лог: ${dataDir.absolutePath}" }

    /**
     * TorrServer по умолчанию выбрасывает неактивный торрент через 30 секунд —
     * подготовленный кэш успевает протухнуть раньше, чем пользователь нажмёт «Смотреть».
     */
    private fun keepTorrentsAlive(dataDir: File): Boolean {
        val settings = File(dataDir, "settings.json")
        if (!settings.isFile) return false
        return runCatching {
            val root = JSONObject(settings.readText())
            val bitTorr = root.optJSONObject("BitTorr") ?: return@runCatching false
            if (bitTorr.optInt("TorrentDisconnectTimeout", 0) >= TORRENT_KEEP_ALIVE_SEC) {
                return@runCatching false
            }
            bitTorr.put("TorrentDisconnectTimeout", TORRENT_KEEP_ALIVE_SEC)
            settings.writeText(root.toString(2))
            log("TorrentDisconnectTimeout -> ${TORRENT_KEEP_ALIVE_SEC}s")
            true
        }.getOrDefault(false)
    }

    private fun resetBrokenSettings(dataDir: File) {
        val marker = File(dataDir, "settings-reset.done")
        if (marker.exists()) return
        runCatching {
            File(dataDir, "settings.json").takeIf { it.exists() }?.delete()
            val db = File(dataDir, "config.db")
            if (db.exists()) {
                val backup = File(dataDir, "config.db.bak")
                if (backup.exists()) backup.delete()
                db.renameTo(backup)
            }
            marker.writeText("settings.json cleanup applied")
        }
    }

    private fun findBinary(): File? {
        val candidates = listOf(
            File(AppPaths.dataDir, "torrserver/TorrServer.exe"),
            File(AppPaths.appDir, "torrserver/TorrServer.exe"),
            File(System.getProperty("user.dir"), "torrserver/TorrServer.exe"),
            File(System.getProperty("user.dir"), "tools/torrserver/TorrServer.exe"),
        )
        return candidates.firstOrNull { it.isFile }
    }

    private fun downloadBinary(): File {
        val dir = File(AppPaths.dataDir, "torrserver").apply { mkdirs() }
        val target = File(dir, "TorrServer.exe")
        val temp = File(dir, "TorrServer.exe.download")

        val connection = open(DOWNLOAD_URL, 30_000, 300_000)
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("Не удалось скачать TorrServer: HTTP ${connection.responseCode}")
            }
            connection.inputStream.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }

        if (!looksLikeExecutable(temp)) {
            temp.delete()
            throw IOException("Скачанный TorrServer повреждён")
        }
        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        return target
    }

    private fun looksLikeExecutable(file: File): Boolean {
        if (file.length() < 1_000_000) return false
        return runCatching {
            file.inputStream().use { stream ->
                val header = ByteArray(2)
                stream.read(header) == 2 &&
                    header[0] == 'M'.code.toByte() &&
                    header[1] == 'Z'.code.toByte()
            }
        }.getOrDefault(false)
    }

    private fun echo(): Boolean = runCatching {
        val connection = open("$BASE/echo", 1_500, 1_500)
        try {
            connection.responseCode == 200
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(false)

    private fun post(path: String, body: String): String {
        val connection = URL(BASE + path).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 3_000
            connection.readTimeout = 90_000
            connection.instanceFollowRedirects = true
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("User-Agent", "KinoTrendDesktop/2.3.8")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IOException("TorrServer HTTP $code: ${text.take(300)}")
            }
            return text
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, connectTimeoutMs: Int, readTimeoutMs: Int): HttpURLConnection {
        var current = URL(url)
        repeat(5) {
            val connection = current.openConnection() as HttpURLConnection
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "KinoTrendDesktop/2.3.8")

            if (connection.responseCode in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) throw IOException("HTTP redirect без Location")
                current = URL(current, location)
                return@repeat
            }
            return connection
        }
        throw IOException("Слишком много HTTP-редиректов")
    }

    private fun shutdown() {
        val connection = open("$BASE/shutdown", 1_000, 1_000)
        try {
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    private fun installShutdownHook() {
        if (shutdownHookInstalled) return
        shutdownHookInstalled = true
        Runtime.getRuntime().addShutdownHook(
            Thread {
                if (!startedByUs) return@Thread
                // Не рвём передачу, которую ещё смотрит внешний плеер.
                if (playbackProcess?.isAlive == true) return@Thread
                runCatching { shutdown() }
            },
        )
    }

    private fun log(message: String) {
        runCatching {
            File(AppPaths.dataDir, "torrserver-client.log")
                .appendText("${System.currentTimeMillis()} $message\n")
        }
    }

    private val MAGNET_HASH = Regex("xt=urn:btih:([A-Za-z0-9]+)")

    private fun base32ToHex(value: String): String? {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var buffer = 0L
        var bitsLeft = 0
        val out = StringBuilder()
        for (char in value.uppercase()) {
            val index = alphabet.indexOf(char)
            if (index < 0) return null
            buffer = (buffer shl 5) or index.toLong()
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                out.append("%02x".format((buffer shr bitsLeft) and 0xFF))
            }
        }
        return out.toString().take(40).takeIf { it.length == 40 }
    }

    private val VIDEO_EXTENSIONS = listOf(
        ".mkv",
        ".mp4",
        ".avi",
        ".ts",
        ".m4v",
        ".mov",
        ".wmv",
        ".webm",
    )
}
