package app.kinotrend.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI
import kotlinx.coroutines.launch
import javax.swing.JFileChooser

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FiltersOverlay(state: AppState) {
    Box(
        Modifier.fillMaxSize().background(Color(0xB3000000))
            .clickable { state.showFilters = false },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.width(640.dp)
                .background(Surface, RoundedCornerShape(16.dp))
                .clickable { }
                .padding(24.dp),
        ) {
            Text("Фильтры", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(16.dp))
            Text("Жанры", color = TextMuted, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.heightIn(max = 190.dp).verticalScroll(rememberScrollState())) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.allGenres.forEach { genre ->
                        Chip(genre, genre in state.selectedGenres) {
                            state.selectedGenres = toggle(state.selectedGenres, genre)
                            state.persist()
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Страны", color = TextMuted, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.heightIn(max = 150.dp).verticalScroll(rememberScrollState())) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.allCountries.forEach { country ->
                        Chip(country, country in state.selectedCountries) {
                            state.selectedCountries = toggle(state.selectedCountries, country)
                            state.persist()
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                if (state.minRating <= 0f) "Рейтинг: любой" else "Рейтинг: от ${"%.1f".format(state.minRating)}",
                color = TextMuted, fontSize = 13.sp,
            )
            Slider(
                value = state.minRating,
                onValueChange = { state.minRating = it },
                onValueChangeFinished = { state.persist() },
                valueRange = 0f..10f,
                steps = 9,
            )

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { state.resetFilters() }) {
                    Text("Сбросить", color = TextMuted)
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { state.showFilters = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
                ) { Text("Готово") }
            }
        }
    }
}

@Composable
fun SettingsDrawer(
    state: AppState,
    visible: Boolean,
    onMirror: (Boolean) -> Unit,
    onClearCache: () -> Unit,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var vlcStatus by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(140)),
        ) {
            Box(
                Modifier.fillMaxSize().background(Color(0x99000000))
                    .clickable { onClose() },
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(tween(260)) { it } + fadeIn(tween(160)),
            exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Column(
                Modifier.fillMaxHeight().width(430.dp).background(Surface)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Настройки", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "✕", color = TextMuted, fontSize = 20.sp,
                        modifier = Modifier.clickable { onClose() }.padding(8.dp),
                    )
                }

                SectionTitle("Интерфейс")
                SwitchRow(
                    "Широкие постеры",
                    "Карточки 16:9 вместо 2:3",
                    state.widePoster,
                ) { state.widePoster = it; state.persist() }

                Spacer(Modifier.height(10.dp))
                Text("Плотность каталога", color = TextMain, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("Компактно", state.density == "compact") { state.density = "compact"; state.persist() }
                    Chip("Обычно", state.density == "normal") { state.density = "normal"; state.persist() }
                    Chip("Крупно", state.density == "large") { state.density = "large"; state.persist() }
                }

                SectionTitle("Каталог")
                Text("Сортировка", color = TextMuted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("По дате", state.sortBy == "date") { state.sortBy = "date"; state.persist() }
                    Chip("По рейтингу", state.sortBy == "rating") { state.sortBy = "rating"; state.persist() }
                    Chip("По названию", state.sortBy == "name") { state.sortBy = "name"; state.persist() }
                }
                Spacer(Modifier.height(14.dp))
                SwitchRow(
                    "Сохранять фильтры",
                    "Применять выбранные фильтры при запуске",
                    state.saveFilters,
                ) { state.saveFilters = it; state.persist() }
                Spacer(Modifier.height(10.dp))
                SwitchRow(
                    "Зеркало каталога",
                    "Если основной сервер недоступен",
                    state.useMirror,
                ) { onMirror(it) }

                SectionTitle("Раздачи")
                SwitchRow(
                    "Только дубляж",
                    "Прятать фильмы и раздачи без дубляжа",
                    state.dubbingOnly,
                ) { state.dubbingOnly = it; state.persist() }
                SectionTitle("Плеер")
                val currentPlayerName = state.foundPlayers.firstOrNull { it.second == state.playerPath }?.first
                    ?: if (state.playerPath.endsWith("vlc.exe", true)) "VLC" else File(state.playerPath).nameWithoutExtension
                Text(
                    "Текущий: " + if (state.playerPath.isBlank()) "не выбран" else currentPlayerName,
                    color = TextMain, fontSize = 14.sp,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.foundPlayers.forEach { (name, path) ->
                        Chip(name, state.playerPath == path) {
                            state.playerPath = path
                            state.persist()
                        }
                    }
                }
                if (state.foundPlayers.isEmpty()) {
                    Text(
                        "Плеер не выбран — скачайте VLC или добавьте свой",
                        color = TextMuted, fontSize = 12.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton(onClick = {
                        scope.launch { state.installVlc { vlcStatus = it } }
                    }) { Text("Скачать VLC", color = Accent, fontSize = 12.sp) }

                    TextButton(onClick = {
                        val dialog = FileDialog(null as Frame?, "Выберите плеер", FileDialog.LOAD)
                        dialog.isVisible = true
                        val directory = dialog.directory
                        val name = dialog.file
                        if (!directory.isNullOrBlank() && !name.isNullOrBlank()) {
                            state.addPlayer(File(directory, name).nameWithoutExtension, File(directory, name).absolutePath)
                        }
                    }) { Text("Добавить свой", color = Accent, fontSize = 12.sp) }
                }
                vlcStatus?.let {
                    Text(it, color = TextMuted, fontSize = 11.sp)
                }
                Spacer(Modifier.height(10.dp))
                SwitchRow(
                    "Спрашивать плеер при запуске",
                    "При «Смотреть» показывать список плееров",
                    state.askPlayer,
                ) { state.askPlayer = it; state.persist() }
                SectionTitle("Данные")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Кэш каталога и постеров", color = TextMain, fontSize = 14.sp)
                        Text(
                            state.statusMessage ?: "Будет скачан заново",
                            color = if (state.statusMessage != null) Accent else TextMuted,
                            fontSize = 12.sp,
                        )
                    }
                    TextButton(onClick = onClearCache) { Text("Очистить", color = Accent) }
                }

                SectionTitle("Прочее")
                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF334047)))
                Spacer(Modifier.height(14.dp))
                Text("KinoTrend Desktop 2.3.8  Неофициальная сборка", color = TextMain, fontSize = 14.sp)
                Text("Каталог: kino-trend.ru · зеркало nemiroff.insomnia247.nl", color = TextMuted, fontSize = 12.sp)
                Text("Плеер: JavaFX Media (HLS)", color = TextMuted, fontSize = 12.sp)

                Spacer(Modifier.height(14.dp))
                Text("Рекомендуемый плеер для торрентов", color = TextMuted, fontSize = 12.sp)
                Text(
                    "Frame Player",
                    color = Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        runCatching {
                            Desktop.getDesktop().browse(URI("https://github.com/risenxxx/frame-player"))
                        }
                    },
                )
                Text(
                    "Открытый плеер на libmpv: MKV, HEVC, HDR",
                    color = TextMuted, fontSize = 11.sp,
                )

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Закрыть") }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(22.dp))
    Text(text, color = Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextMain, fontSize = 14.sp)
            Text(subtitle, color = TextMuted, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Accent),
        )
    }
}

@Composable
internal fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.background(
            if (selected) Accent else Color(0xFF2B353C),
            RoundedCornerShape(16.dp),
        ).clickable { onClick() }.padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            color = if (selected) Color.Black else TextMain,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

private fun toggle(source: Set<String>, value: String): Set<String> =
    if (value in source) source - value else source + value
