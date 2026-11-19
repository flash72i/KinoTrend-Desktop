package app.kinotrend.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kinotrend.desktop.player.FxPlayer
import kotlinx.coroutines.delay
import java.awt.Desktop
import java.net.URI

/** Состояние плеера одним значением: иначе каждый тик перерисовывал экран четыре раза. */
private data class PlayerTick(
    val position: Float = 0f,
    val playing: Boolean = false,
    val timeText: String = "0:00 / 0:00",
    val ready: Boolean = false,
    val error: String? = null,
)

@Composable
fun PlayerScreen(url: String, title: String, onClose: () -> Unit, onToggleFullscreen: () -> Unit = {}) {
    val player = remember(url) { FxPlayer(url) }
    DisposableEffect(player) {
        onDispose { player.dispose() }
    }

    var tick by remember { mutableStateOf(PlayerTick()) }
    var drag by remember { mutableStateOf<Float?>(null) }
    var volume by remember { mutableStateOf(1f) }

    LaunchedEffect(player) {
        while (true) {
            delay(400)
            val next = PlayerTick(
                position = if (player.durationMs > 0) {
                    (player.positionMs.toFloat() / player.durationMs).coerceIn(0f, 1f)
                } else {
                    0f
                },
                playing = player.playing,
                timeText = player.timeText(),
                ready = player.ready,
                error = player.error,
            )
            if (next != tick) tick = next
        }
    }

    val ready = tick.ready
    val playing = tick.playing
    val timeText = tick.timeText
    val error = tick.error

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).background(Surface).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "←", color = Accent, fontSize = 24.sp,
                modifier = Modifier.clickable { onClose() }.padding(end = 16.dp),
            )
            Text(title, color = TextMain, fontSize = 16.sp)
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            SwingPanel(factory = { player.panel }, modifier = Modifier.fillMaxSize())

            if (!ready && error == null) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = Accent)
                    Text(
                        "Загрузка трейлера…", color = TextMuted, fontSize = 13.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            error?.let { message ->
                Column(
                    Modifier.fillMaxSize().background(Bg).padding(32.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Не удалось воспроизвести трейлер", color = TextMain, fontSize = 18.sp)
                    Text(message, color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    Button(
                        onClick = { runCatching { Desktop.getDesktop().browse(URI(url)) } },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
                        modifier = Modifier.padding(top = 16.dp),
                    ) { Text("Открыть во внешнем плеере") }
                    Button(onClick = onClose, modifier = Modifier.padding(top = 8.dp)) { Text("Закрыть") }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().height(72.dp).background(Surface).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { player.toggle() },
                enabled = ready,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
            ) { Text(if (playing) "Пауза" else "Играть") }
            Row(
                Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Slider(
                    value = drag ?: tick.position,
                    onValueChange = { drag = it },
                    onValueChangeFinished = {
                        drag?.let { player.seek(it) }
                        drag = null
                    },
                    enabled = ready,
                    modifier = Modifier.weight(1f),
                )
                Text(timeText, color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}
