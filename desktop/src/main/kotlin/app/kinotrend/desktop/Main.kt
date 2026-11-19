package app.kinotrend.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.kinotrend.desktop.core.MouseNavigation
import app.kinotrend.desktop.ui.AppState
import app.kinotrend.desktop.ui.DetailsScreen
import app.kinotrend.desktop.ui.HomeScreen
import app.kinotrend.desktop.ui.PlayerScreen

fun main() {
    // AWT не отдаёт боковые кнопки мыши (4/5), пока Toolkit не создан с этим флагом.
    // Флаг читается один раз при создании Toolkit — ставим до application {}.
    System.setProperty("sun.awt.enableExtraMouseButtons", "true")
    runApp()
}

private fun runApp() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "KinoTrend 2.3.8",
        state = rememberWindowState(width = 1280.dp, height = 800.dp),
    ) {
        val composeWindow = window
        LaunchedEffect(Unit) { WindowStyle.apply(composeWindow) }
        MaterialTheme(
            colorScheme = darkColorScheme(
                background = Color(0xFF14181C),
                surface = Color(0xFF222B31),
                primary = Color(0xFFFF9900),
            )
        ) {
            val toggleFullscreen = {
                val device = window.graphicsConfiguration.device
                device.fullScreenWindow = if (device.fullScreenWindow == null) window else null
            }
            val demoUrl = System.getenv("KINOTREND_PLAY_URL")
            if (!demoUrl.isNullOrBlank()) {
                PlayerScreen(demoUrl, "Тест плеера", { exitApplication() }, toggleFullscreen)
            } else {
                val state = remember { AppState() }
                LaunchedEffect(Unit) { state.load() }

                // Боковые кнопки мыши: назад / вперёд по истории.
                DisposableEffect(state) {
                    val listener = MouseNavigation.install(
                        onBack = { state.navigateBack() },
                        onForward = { state.navigateForward() },
                    )
                    onDispose { MouseNavigation.uninstall(listener) }
                }

                val playerUrl = state.playerUrl
                val selected = state.selectedMovie
                when {
                    playerUrl != null -> PlayerScreen(playerUrl, state.playerTitle, { state.closePlayer() }, toggleFullscreen)
                    selected != null -> DetailsScreen(state, selected)
                    else -> HomeScreen(state)
                }
            }
        }
    }
}
