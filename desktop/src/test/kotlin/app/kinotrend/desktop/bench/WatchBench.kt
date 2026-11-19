package app.kinotrend.desktop.bench

import app.kinotrend.desktop.data.TorrServerManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val BASE = "http://127.0.0.1:8091"

/** Раздачи, которые ещё не добавлялись в TorrServer. */
private val FRESH = listOf(
    "magnet:?xt=urn:btih:02b57d2a5f8cd6025f236f367523ae61335fd560&dn=rutor.info&tr=udp://opentor.net:6969&tr=http://retracker.local/announce",
    "magnet:?xt=urn:btih:c3e890beaec6dae5de3841dfb6b43501e568afea&dn=rutor.info&tr=udp://opentor.net:6969&tr=http://retracker.local/announce",
    "magnet:?xt=urn:btih:f5160a49b799289fbd093bab12a289a3d497c33c&dn=rutor.info&tr=udp://opentor.net:6969&tr=http://retracker.local/announce",
    "magnet:?xt=urn:btih:88d691a2e94f3ce44458b31ce0f7e58ce328a339&dn=rutor.info&tr=udp://opentor.net:6969&tr=http://retracker.local/announce",
)

/**
 * Точная копия старой логики TorrServerManager: один глобальный монитор,
 * внутри которого и опрос метаданных, и запрос хэша по клику.
 */
private val oldLock = Any()

private fun oldPrepare(magnet: String): String = synchronized(oldLock) {
    val added = JSONObject(rawPost(JSONObject().put("action", "add").put("link", magnet).put("save_to_db", false).toString()))
    val hash = added.optString("hash")
    var files = added.optJSONArray("file_stats")
    var attempt = 0
    while ((files == null || files.length() == 0) && attempt < 60) {
        Thread.sleep(500)
        val got = JSONObject(rawPost(JSONObject().put("action", "get").put("hash", hash).toString()))
        files = got.optJSONArray("file_stats")
        attempt++
    }
    hash
}

private fun oldClick(magnet: String): String = synchronized(oldLock) {
    JSONObject(rawPost(JSONObject().put("action", "add").put("link", magnet).put("save_to_db", false).toString()))
        .optString("hash")
}

fun main() {
    if (System.getenv("KINOTREND_BENCH") != "1") {
        println("WatchBench — ручной замер на реальной сети. Запуск: KINOTREND_BENCH=1")
        return
    }

    println("=== Прогрев TorrServer ===")
    var t = System.currentTimeMillis()
    TorrServerManager.start()
    println("  start(): ${System.currentTimeMillis() - t} ms")
    println()

    println("=== СЦЕНАРИЙ A: старый код (глобальный монитор) ===")
    val oldMagnet = FRESH[0]
    val oldPrewarm = Thread { runCatching { oldPrepare(oldMagnet) } }
    oldPrewarm.start()
    Thread.sleep(400) // пользователь открыл карточку, предзагрузка пошла
    t = System.currentTimeMillis()
    val oldHash = oldClick(oldMagnet)
    val oldClickMs = System.currentTimeMillis() - t
    println("  клик «Смотреть» отработал за: $oldClickMs ms (hash=$oldHash)")
    oldPrewarm.join()
    println()

    println("=== СЦЕНАРИЙ B: новый код (кэш подготовки, монитор только на старт) ===")
    val newMagnet = FRESH[1]
    val newPrewarm = Thread { runCatching { TorrServerManager.prepare(newMagnet) } }
    newPrewarm.start()
    Thread.sleep(400)
    t = System.currentTimeMillis()
    val newPrepared = TorrServerManager.preparedFor(newMagnet)
    val newClickMs = System.currentTimeMillis() - t
    println("  клик «Смотреть» отработал за: $newClickMs ms (подготовлено=${newPrepared != null})")
    newPrewarm.join()
    println()

    println("=== СЦЕНАРИЙ C: клик, пока предзагрузка ещё идёт ===")
    val busyMagnet = FRESH[2]
    val busyPrewarm = Thread { runCatching { TorrServerManager.prepare(busyMagnet) } }
    busyPrewarm.start()
    Thread.sleep(400)
    t = System.currentTimeMillis()
    TorrServerManager.preparedFor(busyMagnet)
        ?: TorrServerManager.prepare(busyMagnet) // клик ждёт общую подготовку, а не делает свою
    val busyNewMs = System.currentTimeMillis() - t
    println("  НОВЫЙ код: клик отработал за: $busyNewMs ms")
    busyPrewarm.join()

    val busyMagnet2 = FRESH[3]
    val busyPrewarm2 = Thread { runCatching { oldPrepare(busyMagnet2) } }
    busyPrewarm2.start()
    Thread.sleep(400)
    t = System.currentTimeMillis()
    oldClick(busyMagnet2)
    val busyOldMs = System.currentTimeMillis() - t
    println("  СТАРЫЙ код: клик отработал за: $busyOldMs ms")
    busyPrewarm2.join()
    println()

    println("=== ИТОГ ===")
    println("  клик по готовой раздаче: было $oldClickMs ms -> стало $newClickMs ms")
    println("  клик во время предзагрузки: было $busyOldMs ms -> стало $busyNewMs ms")
}

private fun rawPost(body: String): String {
    val connection = URL(BASE + "/torrents").openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 3_000
        connection.readTimeout = 90_000
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
    } finally {
        connection.disconnect()
    }
}
