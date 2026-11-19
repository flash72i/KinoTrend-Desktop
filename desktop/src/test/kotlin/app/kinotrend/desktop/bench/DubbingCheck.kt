package app.kinotrend.desktop.bench

import app.kinotrend.desktop.data.Torrent
import app.kinotrend.desktop.data.isDubbed
import org.json.JSONArray
import java.io.File

/**
 * Сверка фильтра «Только дубляж» со старой реализацией и с логикой Android-версии.
 * Запуск из корня проекта:
 *   java -cp "desktop/build/classes/kotlin/main;desktop/build/classes/kotlin/test;KinoTrend/app-jars" \
 *     app.kinotrend.desktop.bench.DubbingCheckKt
 */
fun main() {
    val dataFile = File("re/data/data.json")
    if (!dataFile.isFile) {
        println("Не найден re/data/data.json — запускайте из корня проекта")
        return
    }
    val root = org.json.JSONObject(dataFile.readText())
    val movies = root.optJSONArray("movies") ?: JSONArray()

    var filmsTotal = 0
    var filmsOldWrong = 0
    var filmsNewEmpty = 0
    var torrentsHiddenNew = 0
    val examples = mutableListOf<String>()

    for (i in 0 until movies.length()) {
        val movie = movies.optJSONObject(i) ?: continue
        val torrents = parseTorrents(movie.optJSONArray("torrents"))
        if (torrents.isEmpty()) continue
        filmsTotal++

        // Старая реализация: оставляем D/L, но при пустом результате показываем всё.
        val old = torrents
            .filter { it.audio.equals("D", true) || it.audio.equals("L", true) }
            .ifEmpty { torrents }
        // Новая реализация: логика Android (D, L и неизвестная озвучка), без отката.
        val new = torrents.filter { it.isDubbed() }

        if (old.size != new.size) {
            filmsOldWrong++
            if (examples.size < 5) {
                examples += "${movie.optString("nameRU")}: " +
                    "старое=${old.size} (${old.map { it.audio }.joinToString(",")}), " +
                    "новое=${new.size} (${new.map { it.audio }.joinToString(",")})"
            }
        }
        if (new.isEmpty()) {
            filmsNewEmpty++
        }
        torrentsHiddenNew += old.size - new.size
    }

    println("Фильмов с раздачами: $filmsTotal")
    println("Фильмов, где старое поведение отличается от Android: $filmsOldWrong")
    println("Фильмов, где после честного фильтра раздач не остаётся: $filmsNewEmpty")
    println("Раздач, которые старое поведение показывало зря: $torrentsHiddenNew")
    println("Фильмов, которые каталог должен скрыть при «Только дубляж»: $filmsNewEmpty из $filmsTotal")
    println()
    println("Примеры (старое -> новое):")
    examples.forEach { println("  $it") }
}

private fun parseTorrents(array: JSONArray?): List<Torrent> {
    if (array == null) return emptyList()
    val result = ArrayList<Torrent>()
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        result += Torrent(
            name = item.optString("name"),
            link = item.optString("link"),
            magnet = item.optString("magnet"),
            date = item.optString("date"),
            type = item.optString("type"),
            audio = item.optString("audio"),
            size = item.optString("size"),
            seeders = item.optInt("seeders", -1).takeIf { it >= 0 },
            leechers = item.optInt("leechers", -1).takeIf { it >= 0 },
            quality = item.optInt("quality", 0),
            dv = item.optBoolean("dv", false),
        )
    }
    return result
}
