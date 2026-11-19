package app.kinotrend.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.ptr.IntByReference
import java.awt.Frame
import java.awt.Taskbar
import java.awt.Window
import javax.imageio.ImageIO

object WindowStyle {
    private interface DwmApi : Library {
        fun DwmSetWindowAttribute(hwnd: HWND, attribute: Int, value: Pointer, size: Int): Int
    }

    private val dwm: DwmApi by lazy { Native.load("dwmapi", DwmApi::class.java) }

    fun apply(window: Window) {
        runCatching {
            val stream = WindowStyle::class.java.getResourceAsStream("/icon.png")
            if (stream != null) {
                val image = ImageIO.read(stream)
                (window as? Frame)?.iconImage = image
                runCatching { Taskbar.getTaskbar().setIconImage(image) }
            }
        }
        runCatching {
            val title = (window as? Frame)?.title ?: return@runCatching
            val hwnd = User32.INSTANCE.FindWindow(null, title) ?: return@runCatching
            val enabled = IntByReference(1)
            val result = dwm.DwmSetWindowAttribute(hwnd, 20, enabled.pointer, 4)
            if (result != 0) {
                dwm.DwmSetWindowAttribute(hwnd, 19, enabled.pointer, 4)
            }
        }
    }
}
