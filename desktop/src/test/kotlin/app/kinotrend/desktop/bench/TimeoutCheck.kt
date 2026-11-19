package app.kinotrend.desktop.bench

import app.kinotrend.desktop.core.AppPaths
import app.kinotrend.desktop.data.TorrServerManager
import org.json.JSONObject
import java.io.File

/**
 * Проверка патча: TorrServer по умолчанию выбрасывает неактивный торрент через 30 секунд,
 * из-за чего подготовленный кэш протухает раньше, чем пользователь нажмёт «Смотреть».
 */
fun main() {
    if (System.getenv("KINOTREND_BENCH") != "1") {
        println("TimeoutCheck запускает TorrServer. Запуск: KINOTREND_BENCH=1")
        return
    }

    val dataDir = File(AppPaths.dataDir, "torrserver")
    val settings = File(dataDir, "settings.json")

    val before = if (settings.isFile) {
        JSONObject(settings.readText()).optJSONObject("BitTorr")?.optInt("TorrentDisconnectTimeout", -1)
    } else {
        null
    }
    println("settings.json до старта: TorrentDisconnectTimeout=$before (файл есть: ${settings.isFile})")

    val t = System.currentTimeMillis()
    TorrServerManager.start()
    println("start(): ${System.currentTimeMillis() - t} ms")

    val after = if (settings.isFile) {
        JSONObject(settings.readText()).optJSONObject("BitTorr")?.optInt("TorrentDisconnectTimeout", -1)
    } else {
        null
    }
    println("settings.json после старта: TorrentDisconnectTimeout=$after")
    println("патч применён: ${before != null && after != null && after > before}")
}
