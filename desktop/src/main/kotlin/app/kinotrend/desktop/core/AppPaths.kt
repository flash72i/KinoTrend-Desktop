package app.kinotrend.desktop.core

import java.io.File

object AppPaths {
    val appDir: File = runCatching {
        val command = ProcessHandle.current().info().command().orElse(null)
        if (command != null) File(command).parentFile else null
    }.getOrNull() ?: File(System.getProperty("user.dir"))

    val portable: Boolean = File(appDir, "portable.marker").exists()

    val dataDir: File = run {
        val dir = if (portable) {
            File(appDir, "data")
        } else {
            File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "KinoTrend")
        }
        dir.mkdirs()
        dir
    }
}
