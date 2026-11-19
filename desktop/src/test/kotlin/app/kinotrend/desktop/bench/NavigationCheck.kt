package app.kinotrend.desktop.bench

import app.kinotrend.desktop.core.MouseNavigation
import app.kinotrend.desktop.data.Movie
import app.kinotrend.desktop.ui.AppState
import java.awt.Component
import java.awt.EventQueue
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JPanel

/**
 * Проверка навигации «назад/вперёд» и привязки боковых кнопок мыши.
 */
fun main() {
    checkNavigation()
    checkMouseButtons()
}

private fun checkNavigation() {
    println("=== Навигация ===")
    // Без фонового запуска TorrServer: проверяем только навигацию.
    val state = AppState(startBackground = false)
    val a = movie(1, "Первый")
    val b = movie(2, "Второй")

    state.openMovie(a)
    state.openMovie(b)
    println("открыт: ${state.selectedMovie?.nameRU}")

    state.navigateBack()
    println("назад -> ${state.selectedMovie?.nameRU} (ожидалось: Первый)")
    check(state.selectedMovie == a) { "назад должен вернуть первый фильм" }

    state.navigateBack()
    println("назад -> ${state.selectedMovie?.nameRU} (ожидалось: главный экран = null)")
    check(state.selectedMovie == null) { "назад с первого фильма ведёт на главный экран" }

    val forwardOk = state.navigateForward()
    println("вперёд -> ${state.selectedMovie?.nameRU} (ожидалось: Первый), сработало=$forwardOk")
    check(forwardOk) { "вперёд должен вернуть первый фильм" }
    check(state.selectedMovie == a) { "вперёд должен вернуть первый фильм" }

    state.openPlayer("http://example/stream.m3u8", "Трейлер")
    println("плеер открыт: ${state.playerUrl != null}")
    state.navigateBack()
    println("назад из плеера -> плеер закрыт: ${state.playerUrl == null}, экран: ${state.selectedMovie?.nameRU}")
    check(state.playerUrl == null) { "назад должен закрыть плеер" }
    check(state.selectedMovie == a) { "после закрытия плеера остаёмся на карточке" }

    state.showSettings = true
    state.navigateBack()
    println("назад из настроек -> закрыто: ${!state.showSettings}")
    check(!state.showSettings) { "назад должен закрыть настройки" }

    println("навигация: OK")
}

private fun checkMouseButtons() {
    println()
    println("=== Боковые кнопки мыши ===")
    val backs = AtomicInteger()
    val forwards = AtomicInteger()
    val listener = MouseNavigation.install(onBack = { backs.incrementAndGet() }, onForward = { forwards.incrementAndGet() })

    try {
        val source: Component = JPanel()
        val backEvent = MouseEvent(
            source, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
            0, 10, 10, 1, false, MouseNavigation.BUTTON_BACK,
        )
        val forwardEvent = MouseEvent(
            source, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
            0, 10, 10, 1, false, MouseNavigation.BUTTON_FORWARD,
        )

        // 1. Прямая проверка разбора кнопки в слушателе.
        listener.eventDispatched(backEvent)
        listener.eventDispatched(forwardEvent)
        println("прямой вызов: назад=${backs.get()}, вперёд=${forwards.get()}")
        check(backs.get() == 1) { "кнопка 4 должна вызывать назад" }
        check(forwards.get() == 1) { "кнопка 5 должна вызывать вперёд" }

        // 2. Удержание кнопки не должно листать историю пачками.
        listener.eventDispatched(backEvent)
        println("повторное нажатие (эмуляция удержания): назад=${backs.get()}")
        check(backs.get() == 1) { "повторное нажатие той же кнопки должно игнорироваться" }

        // 3. Обычная левая кнопка ничего не делает.
        listener.eventDispatched(
            MouseEvent(source, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, 10, 10, 1, false, 1)
        )
        check(backs.get() == 1 && forwards.get() == 1) { "обычные кнопки мыши игнорируются" }

        // 4. Проверка доставки через очередь событий AWT — так приходят настоящие клики.
        backs.set(0)
        forwards.set(0)
        Thread.sleep(400) // иначе сработает защита от удержания кнопки
        java.awt.Toolkit.getDefaultToolkit().systemEventQueue.postEvent(
            MouseEvent(
                source, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                0, 10, 10, 1, false, MouseNavigation.BUTTON_BACK,
            )
        )
        Thread.sleep(400)
        println("через очередь AWT: назад=${backs.get()}")
        check(backs.get() == 1) { "событие из очереди AWT должно доходить до слушателя" }
    } finally {
        MouseNavigation.uninstall(listener)
    }
    println("боковые кнопки: OK")
}

private fun movie(id: Int, name: String) = Movie(
    filmID = id, nameRU = name, nameOriginal = "", description = "", year = "", country = "",
    genre = "", rating = "", ratingFloat = 0f, ratingKP = "", ratingIMDb = "", ratingAgeLimits = "",
    posterURL = "", bigPosterURL = "", trailerURL = "", filmLength = "", torrents = emptyList(),
)
