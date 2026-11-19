package app.kinotrend.desktop.core

import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Боковые клавиши мыши как «назад» и «вперёд».
 *
 * Слушаем AWT-события глобально, а не окна: иначе событие съедает внутренний
 * компонент Compose и до Frame не доходит. Реагируем на MOUSE_PRESSED — для
 * кнопок 4 и 5 именно пресс приходит надёжнее, чем click.
 */
object MouseNavigation {
    /** Кнопка «назад» (XButton1). */
    const val BUTTON_BACK = 4

    /** Кнопка «вперёд» (XButton2). */
    const val BUTTON_FORWARD = 5

    private const val REPEAT_GUARD_MS = 250L

    /** Последний срабатывающий момент по каждой кнопке: чтобы удержание не листало пачками. */
    private val lastTriggerAt = ConcurrentHashMap<Int, Long>()

    fun install(onBack: () -> Unit, onForward: () -> Unit): AWTEventListener {
        val listener = AWTEventListener { event ->
            if (event !is MouseEvent || event.getID() != MouseEvent.MOUSE_PRESSED) {
                return@AWTEventListener
            }
            val button = event.getButton()
            val action = when (button) {
                BUTTON_BACK -> onBack
                BUTTON_FORWARD -> onForward
                else -> null
            } ?: return@AWTEventListener

            // Дебаунс ведём по каждой кнопке отдельно: «назад» и «вперёд» подряд
            // должны срабатывать, а вот удержание одной кнопки — нет.
            val now = System.currentTimeMillis()
            val last = lastTriggerAt[button] ?: 0L
            if (now - last < REPEAT_GUARD_MS) return@AWTEventListener
            lastTriggerAt[button] = now
            log(if (button == BUTTON_BACK) "mouse back" else "mouse forward")
            action()
        }
        // Одноаргументной версии addAWTEventListener в Java 9+ больше нет.
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.MOUSE_EVENT_MASK)
        return listener
    }

    private fun log(message: String) {
        runCatching {
            val file = File(AppPaths.dataDir, "navigation.log")
            file.appendText("${System.currentTimeMillis()} $message\n")
        }
    }

    fun uninstall(listener: AWTEventListener) {
        Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
    }
}
