package app.kinotrend.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kinotrend.desktop.data.Movie
import app.kinotrend.desktop.data.Torrent
import app.kinotrend.desktop.data.TorrentDownloader
import app.kinotrend.desktop.data.isDubbed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI

@Composable
fun DetailsScreen(state: AppState, movie: Movie) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val statuses = remember { mutableStateMapOf<String, RowStatus>() }
    var showPlayerChoice by remember { mutableStateOf(false) }
    var pendingPicker by remember { mutableStateOf<Torrent?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1_600)
            toast = null
        }
    }

    fun copyMagnet(torrent: Torrent) {
        val magnet = torrent.magnet.ifBlank { torrent.link }
        val copied = magnet.isNotBlank() && runCatching {
            Toolkit.getDefaultToolkit().systemClipboard
                .setContents(StringSelection(magnet), null)
        }.isSuccess
        toast = if (copied) "Скопировано" else "Не удалось скопировать"
    }

    fun startWatch(torrent: Torrent, player: String?) {
        val key = torrent.magnet
        scope.launch {
            statuses[key] = RowStatus("Запуск TorrServer…", null, false)
            val error = state.playTorrent(torrent, player) { text, progress ->
                statuses[key] = RowStatus(text, progress, false)
            }
            if (error != null) statuses[key] = RowStatus(error, null, true)
        }
    }

    fun requestWatch(torrent: Torrent) {
        val available = state.foundPlayers.filter { File(it.second).isFile }
        val preferred = available.firstOrNull { it.second.equals(state.playerPath, true) }
        // Как в LostFilm: спрашиваем, если плеер по умолчанию не найден
        // или включён вопрос и плееров больше одного.
        if (available.isNotEmpty() && (preferred == null || (state.askPlayer && available.size > 1))) {
            pendingPicker = torrent
            return
        }
        startWatch(torrent, preferred?.second)
    }

    // «Только дубляж» повторяет логику Android-версии (hide_pm_audio): остаются
    // D (дубляж), L (лицензия) и раздачи с неизвестной озвучкой. Отката к полному
    // списку нет — иначе переключатель у фильмов без дубляжа молча ничего не делал.
    val torrents = remember(movie, state.dubbingOnly) {
        if (!state.dubbingOnly) movie.torrents else movie.torrents.filter { it.isDubbed() }
    }
    val hiddenByFilter = state.dubbingOnly && torrents.isEmpty() && movie.torrents.isNotEmpty()

    fun download(torrent: Torrent) {
        val suggested = TorrentDownloader.suggestedFile(torrent)
        val owner = Frame.getFrames().firstOrNull { it.isVisible && it.title == "KinoTrend 2.3.8" }
        val dialog = FileDialog(owner, "Сохранить торрент", FileDialog.SAVE)
        dialog.file = suggested.name
        dialog.directory = suggested.parent
        dialog.isVisible = true
        val name = dialog.file ?: return
        val directory = dialog.directory ?: return
        var target = File(directory, name)
        if (!target.name.lowercase().endsWith(".torrent")) {
            target = File(target.parent, target.name + ".torrent")
        }
        val key = torrent.magnet
        scope.launch {
            statuses[key] = RowStatus("Скачивание…", null, false)
            val result = withContext(Dispatchers.IO) {
                runCatching { TorrentDownloader.download(torrent, target) }
                    .getOrElse { "Ошибка: ${it.message}" }
            }
            statuses[key] = RowStatus(result, null, !result.startsWith("Сохранено"))
        }
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Backdrop(movie)

        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp)
                    .background(Color(0xB3222B31)).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "←", color = Accent, fontSize = 24.sp,
                    modifier = Modifier.clickable { state.closeMovie() }.padding(end = 16.dp),
                )
                Text(
                    movie.nameRU, color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Header(movie) { showPlayerChoice = true }
                }
                item {
                    Text(
                        "Раздачи", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                if (torrents.isEmpty()) {
                    item {
                        Column {
                            Text(
                                if (hiddenByFilter) "Нет раздач с дубляжом" else "Раздачи не найдены",
                                color = TextMuted,
                            )
                            if (hiddenByFilter) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Все ${movie.torrents.size} раздач — оригинальные. " +
                                        "Отключите «Только дубляж» в настройках.",
                                    color = TextMuted, fontSize = 12.sp,
                                )
                            }
                        }
                    }
                } else {
                    items(torrents) { torrent ->
                        TorrentRow(
                            torrent = torrent,
                            status = statuses[torrent.magnet],
                            onWatch = { requestWatch(torrent) },
                            onDownload = { download(torrent) },
                            onCopyMagnet = { copyMagnet(torrent) },
                        )
                    }
                }

            }
        }

        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn(tween(120)) + slideInVertically(tween(160)) { -it / 2 },
            exit = fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 68.dp),
        ) {
            Box(
                Modifier.background(Color(0xE6222B31), RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            ) {
                Text(toast ?: "Скопировано", color = TextMain, fontSize = 13.sp)
            }
        }

        pendingPicker?.let { torrent ->
            PlayerPickerDialog(
                title = torrent.type.ifBlank { "Раздача" },
                players = state.foundPlayers,
                defaultPath = state.playerPath,
                onPick = { path, dontAsk ->
                    state.choosePlayer(path, dontAsk)
                    pendingPicker = null
                    startWatch(torrent, path)
                },
                onCancel = { pendingPicker = null },
            )
        }

        if (showPlayerChoice) {
            PlayerChoiceDialog(
                movie = movie,
                state = state,
                onClose = { showPlayerChoice = false },
                onInApp = {
                    showPlayerChoice = false
                    state.openPlayer(movie.trailerURL, movie.nameRU + " / Трейлер")
                },
            )
        }
    }
}

@Composable
private fun PlayerChoiceDialog(
    movie: Movie,
    state: AppState,
    onClose: () -> Unit,
    onInApp: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(Color(0xB3000000)).clickable { onClose() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(440.dp).background(Surface, RoundedCornerShape(16.dp))
                .clickable { }.padding(22.dp),
        ) {
            Text("Чем смотреть трейлер?", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onInApp,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Встроенный плеер") }
            if (state.foundPlayers.isEmpty()) {
        Text("Плееры не найдены — добавьте в настройках", color = TextMuted, fontSize = 12.sp)
    }
    state.foundPlayers.forEach { (name, path) ->
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        runCatching { ProcessBuilder(path, movie.trailerURL).start() }
                        state.playerPath = path
                        state.persist()
                        onClose()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(name) }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Выбранный плеер запомнится в настройках",
                color = TextMuted, fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun Backdrop(movie: Movie) {
    val image by produceState<ImageBitmap?>(initialValue = null, movie.bigPosterURL) {
        value = PosterLoader.load(movie.bigPosterURL.ifBlank { movie.posterURL }, 1100)
    }
    image?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.30f,
            modifier = Modifier.fillMaxSize(),
        )
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(Color(0xE614181C), Color(0xF214181C), Bg),
            ),
        ),
    )
}

@Composable
private fun Header(movie: Movie, onTrailer: () -> Unit) {
    val poster by produceState<ImageBitmap?>(initialValue = null, movie.bigPosterURL) {
        value = PosterLoader.load(movie.bigPosterURL.ifBlank { movie.posterURL })
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(
            Modifier.width(250.dp).aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp)).background(Surface),
        ) {
            poster?.let {
                Image(
                    bitmap = it, contentDescription = movie.nameRU,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Column(Modifier.weight(1f)) {
            Text(movie.nameRU, color = TextMain, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            if (movie.nameOriginal.isNotBlank() && movie.nameOriginal != movie.nameRU) {
                Text(
                    movie.nameOriginal, color = TextMuted, fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                if (movie.ratingKP.isNotBlank()) Metric(Icons.Filled.Star, "KP ${movie.ratingKP}", Accent)
                if (movie.ratingIMDb.isNotBlank()) Metric(Icons.Filled.Star, "IMDb ${movie.ratingIMDb}", Color(0xFFF5C518))
                if (movie.ratingAgeLimits.isNotBlank()) AgeBadge(movie.ratingAgeLimits)
                if (movie.filmLength.isNotBlank() && movie.filmLength != "0:00") {
                    Metric(Icons.Filled.Schedule, movie.filmLength.toDurationText())
                }
                if (movie.year.isNotBlank()) Metric(Icons.Filled.CalendarMonth, movie.year)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 10.dp),
            ) {
                if (movie.country.isNotBlank()) Metric(Icons.Filled.Public, movie.country)
                if (movie.genre.isNotBlank()) Text(movie.genre, color = TextMuted, fontSize = 13.sp)
            }

            Text(
                movie.description,
                color = Color(0xFFD3D8DE), fontSize = 14.sp, lineHeight = 21.sp,
                modifier = Modifier.padding(top = 14.dp),
            )

            if (movie.trailerURL.isNotBlank()) {
                Button(
                    onClick = onTrailer,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
                    modifier = Modifier.padding(top = 14.dp),
                ) {
                    Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Трейлер")
                }
            }
        }
    }
}

@Composable
private fun Metric(icon: ImageVector, text: String, tint: Color = TextMain) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, color = tint, fontSize = 13.sp)
    }
}

@Composable
private fun AgeBadge(text: String) {
    Box(
        Modifier.background(Color(0xFFE6E6E6), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(text, color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun openMagnet(magnet: String) {
    if (magnet.isBlank()) return
    runCatching { ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", magnet).start() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TorrentRow(
    torrent: Torrent,
    status: RowStatus?,
    onWatch: () -> Unit,
    onDownload: () -> Unit,
    onCopyMagnet: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().background(Surface, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                torrent.type.ifBlank { "Раздача" },
                color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(10.dp))
            AudioChip(torrent.audio)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            buildString {
                append(formatSize(torrent.size))
                if (torrent.seeders != null) append("  ·  Сиды: ${torrent.seeders}")
                if (torrent.leechers != null) append("  ·  Пиры: ${torrent.leechers}")
                if (torrent.date.isNotBlank()) append("  ·  ${torrent.date}")
            },
            color = TextMuted, fontSize = 12.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onDownload) {
                Icon(Icons.Filled.Download, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Скачать")
            }
            TooltipArea(
                tooltip = {
                    Box(
                        Modifier.background(Color(0xF0222B31), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    ) {
                        Text("Скопировать magnet-ссылку", color = TextMain, fontSize = 12.sp)
                    }
                },
                delayMillis = 500,
            ) {
                IconButton(onClick = onCopyMagnet, modifier = Modifier.size(40.dp)) {
                    Icon(
                        MagnetIcon,
                        contentDescription = "Скопировать magnet-ссылку",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Button(
                onClick = onWatch,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
            ) {
                Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Смотреть")
            }
        }
        status?.let { state ->
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.progress != null) {
                    CircularProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.size(16.dp),
                        color = Accent,
                        strokeWidth = 2.dp,
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Accent,
                        strokeWidth = 2.dp,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    state.text,
                    color = if (state.error) Color(0xFFFF6B6B) else TextMuted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun AudioChip(audio: String) {
    val label = when (audio.uppercase()) {
        "D" -> "Дубляж"
        "L" -> "Лицензия"
        "P" -> "Оригинал"
        else -> audio.ifBlank { "—" }
    }
    Box(
        Modifier.background(Color(0xFF334047), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(label, color = TextMain, fontSize = 11.sp)
    }
}

/** Магнит для кнопки копирования magnet-ссылки: в Material Icons Extended его нет. */
private val MagnetBrush = Brush.linearGradient(
    0f to Color(0xFFE53935),
    0.5f to Color(0xFFE53935),
    0.5f to Color(0xFF1E88E5),
    1f to Color(0xFF1E88E5),
    start = Offset(0f, 0f),
    end = Offset(24f, 0f),
)

private val MagnetIcon: ImageVector = ImageVector.Builder(
    name = "Magnet",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        stroke = MagnetBrush,
        strokeLineWidth = 2.2f,
        strokeLineCap = StrokeCap.Round,
    ) {
        moveTo(6f, 4f)
        lineTo(6f, 12f)
        curveTo(6f, 15.31f, 8.69f, 18f, 12f, 18f)
        curveTo(15.31f, 18f, 18f, 15.31f, 18f, 12f)
        lineTo(18f, 4f)
    }
    path(fill = MagnetBrush) {
        moveTo(4.6f, 3f)
        lineTo(7.4f, 3f)
        lineTo(7.4f, 5.4f)
        lineTo(4.6f, 5.4f)
        close()
        moveTo(16.6f, 3f)
        lineTo(19.4f, 3f)
        lineTo(19.4f, 5.4f)
        lineTo(16.6f, 5.4f)
        close()
    }
}.build()

private data class RowStatus(val text: String, val progress: Float?, val error: Boolean)

private fun formatSize(raw: String): String {
    val bytes = raw.toLongOrNull() ?: return raw
    val gb = bytes / 1024.0 / 1024.0 / 1024.0
    if (gb >= 1.0) return "%.2f ГБ".format(gb)
    val mb = bytes / 1024.0 / 1024.0
    return "%.0f МБ".format(mb)
}

private fun String.toDurationText(): String {
    val parts = split(":")
    if (parts.size != 2) return this
    val hours = parts[0].toIntOrNull() ?: 0
    val minutes = parts[1].toIntOrNull() ?: 0
    if (hours <= 0) return "$minutes мин"
    return "$hours ч $minutes мин"
}
