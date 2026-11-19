package app.kinotrend.desktop.player

import app.kinotrend.desktop.core.AppPaths
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.layout.StackPane
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import javafx.scene.paint.Color
import javafx.util.Duration
import java.awt.BorderLayout
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JPanel

class FxPlayer(private val masterUrl: String) {
    val panel = JPanel(BorderLayout())

    @Volatile var ready: Boolean = false
        private set

    @Volatile var error: String? = null
        private set

    @Volatile var playing: Boolean = false
        private set

    @Volatile var positionMs: Long = 0
        private set

    @Volatile var durationMs: Long = 0
        private set

    @Volatile var volume: Float = 1f
        private set

    private var player: MediaPlayer? = null

    init {
        log("FxPlayer created: $masterUrl")
        panel.background = java.awt.Color.BLACK
        Fx.ensure()
        val fxPanel = JFXPanel().apply { background = java.awt.Color.BLACK }
        panel.add(fxPanel, BorderLayout.CENTER)

        Thread({
            try {
                val variant = HlsResolver.resolveVariant(masterUrl)
                log("variant resolved: $variant")
                Platform.runLater { createPlayer(fxPanel, variant) }
            } catch (t: Throwable) {
                log("resolve failed: ${t.stackTraceToString()}")
                error = t.message ?: t.toString()
            }
        }, "kinotrend-hls").apply {
            isDaemon = true
            start()
        }
    }

    private fun createPlayer(fxPanel: JFXPanel, variantUrl: String) {
        try {
            log("createPlayer on FX thread, displayable=${fxPanel.isDisplayable}")
            val media = Media(variantUrl)
            val mediaPlayer = MediaPlayer(media)
            player = mediaPlayer

            media.setOnError {
                log("media error: ${media.error}")
                error = media.error?.toString() ?: "Ошибка медиа"
            }
            mediaPlayer.setOnError {
                log("player error: ${mediaPlayer.error}")
                error = mediaPlayer.error?.toString() ?: "Ошибка плеера"
            }

            mediaPlayer.setOnReady {
                ready = true
                durationMs = media.duration.toMillis().toLong()
                mediaPlayer.volume = volume.toDouble()
                mediaPlayer.play()
                log("ready, duration=$durationMs ms")
            }
            mediaPlayer.setOnPlaying {
                playing = true
                log("playing")
            }
            mediaPlayer.setOnPaused { playing = false }
            mediaPlayer.setOnStopped { playing = false }
            mediaPlayer.currentTimeProperty().addListener { _, _, value ->
                positionMs = value.toMillis().toLong()
            }
            mediaPlayer.setOnEndOfMedia {
                mediaPlayer.pause()
                playing = false
            }

            val view = MediaView(mediaPlayer).apply { isPreserveRatio = true }
            val root = StackPane(view).apply {
                style = "-fx-background-color: black;"
            }
            view.fitWidthProperty().bind(root.widthProperty())
            view.fitHeightProperty().bind(root.heightProperty())

            fxPanel.scene = Scene(root, Color.BLACK)
            log("scene set, panel size=${fxPanel.width}x${fxPanel.height}")
        } catch (t: Throwable) {
            log("createPlayer failed: ${t.stackTraceToString()}")
            error = t.message ?: t.toString()
        }
    }

    fun toggle() {
        Platform.runLater {
            val mediaPlayer = player ?: return@runLater
            if (mediaPlayer.status == MediaPlayer.Status.PLAYING) {
                mediaPlayer.pause()
            } else {
                mediaPlayer.play()
            }
        }
    }

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        Platform.runLater { player?.volume = volume.toDouble() }
    }

    fun seek(fraction: Float) {
        Platform.runLater {
            val mediaPlayer = player ?: return@runLater
            if (durationMs > 0) {
                mediaPlayer.seek(Duration.millis(durationMs * fraction.coerceIn(0f, 1f).toDouble()))
            }
        }
    }

    fun timeText(): String = format(positionMs) + " / " + format(durationMs)

    fun dispose() {
        val mediaPlayer = player
        player = null
        Platform.runLater {
            runCatching {
                mediaPlayer?.stop()
                mediaPlayer?.dispose()
            }
        }
    }

    private fun format(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }

    companion object {
        private val logFile: File by lazy { File(AppPaths.dataDir, "player.log") }

        fun log(message: String) {
            runCatching {
                logFile.appendText("${System.currentTimeMillis()} $message\n")
            }
        }
    }
}

private object Fx {
    private val started = AtomicBoolean(false)

    fun ensure() {
        if (!started.compareAndSet(false, true)) return
        try {
            System.setProperty("javafx.animation.fullspeed", "true")
            Platform.setImplicitExit(false)
            Platform.startup { }
            FxPlayer.log("JavaFX toolkit started, prism=${System.getProperty("prism.order")}")
        } catch (_: IllegalStateException) {
            FxPlayer.log("JavaFX toolkit already started")
        } catch (t: Throwable) {
            FxPlayer.log("JavaFX startup failed: ${t.stackTraceToString()}")
        }
    }
}
