package app.kinotrend.desktop.data

data class Torrent(
    val name: String,
    val link: String,
    val magnet: String,
    val date: String,
    val type: String,
    val audio: String,
    val size: String,
    val seeders: Int?,
    val leechers: Int?,
    val quality: Int,
    val dv: Boolean,
)

data class Movie(
    val filmID: Int,
    val nameRU: String,
    val nameOriginal: String,
    val description: String,
    val year: String,
    val country: String,
    val genre: String,
    val rating: String,
    val ratingFloat: Float,
    val ratingKP: String,
    val ratingIMDb: String,
    val ratingAgeLimits: String,
    val posterURL: String,
    val bigPosterURL: String,
    val trailerURL: String,
    val filmLength: String,
    val torrents: List<Torrent>,
)

data class Catalog(
    val count: Int,
    val movies: List<Movie>,
)

/**
 * Логика фильтра «Только дубляж» из Android-версии (hide_pm_audio):
 * остаются D (дубляж), L (лицензия) и раздачи с неизвестной озвучкой.
 * P (профессиональный/оригинальный) — единственный отсекаемый код.
 */
fun Torrent.isDubbed(): Boolean {
    val code = audio.trim().uppercase()
    return code == "D" || code == "L" || code.isEmpty()
}
