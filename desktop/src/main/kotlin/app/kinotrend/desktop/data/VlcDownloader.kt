package app.kinotrend.desktop.data

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

object VlcDownloader {
    private const val INDEX_URL = "https://get.videolan.org/vlc/last/win64/"
    private val ZIP_PATTERN = Regex("""vlc-[0-9][0-9A-Za-z._-]*-win64\.zip""")

    fun pickZipName(html: String): String? =
        ZIP_PATTERN.findAll(html)
            .map { it.value }
            .distinct()
            .maxWithOrNull { a, b -> compareVersions(versionKey(a), versionKey(b)) }

    private fun compareVersions(a: List<Int>, b: List<Int>): Int {
        val size = maxOf(a.size, b.size)
        for (index in 0 until size) {
            val left = a.getOrElse(index) { 0 }
            val right = b.getOrElse(index) { 0 }
            if (left != right) return left.compareTo(right)
        }
        return 0
    }

    fun install(root: File, progress: (Long, Long) -> Unit): File {
        val zipName = pickZipName(readIndex())
            ?: throw IOException("не удалось определить последнюю версию VLC")
        val tempDir = File(root, ".vlc-download").apply { mkdirs() }
        val zip = File(tempDir, zipName)
        download(INDEX_URL + zipName, zip, progress)
        if (!looksLikeZip(zip)) throw IOException("скачанный архив VLC повреждён")

        val unpacked = File(tempDir, "unpacked").apply {
            deleteRecursively()
            mkdirs()
        }
        unzip(zip, unpacked)

        val exe = unpacked.walkTopDown().firstOrNull {
            it.isFile && it.name.equals("vlc.exe", true)
        } ?: throw IOException("в архиве VLC не найден vlc.exe")

        val sourceDir = exe.parentFile ?: throw IOException("не удалось распаковать VLC")
        val relative = exe.relativeTo(sourceDir).path
        val vlcDir = File(root, "vlc")
        if (vlcDir.exists()) vlcDir.deleteRecursively()
        if (!sourceDir.renameTo(vlcDir)) {
            sourceDir.copyRecursively(vlcDir, overwrite = true)
            sourceDir.deleteRecursively()
        }
        zip.delete()
        unpacked.deleteRecursively()

        val installed = File(vlcDir, relative)
        if (!installed.isFile) throw IOException("VLC распакован, но vlc.exe не найден")
        return installed
    }

    private fun versionKey(name: String): List<Int> {
        val base = name.removePrefix("vlc-").removeSuffix("-win64.zip")
        return base.split('.', '-', '_').mapNotNull { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull()
        }
    }

    private fun readIndex(): String {
        val connection = open(INDEX_URL)
        return try {
            if (connection.responseCode !in 200..299) {
                throw IOException("HTTP ${connection.responseCode} для списка версий VLC")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun download(url: String, target: File, progress: (Long, Long) -> Unit) {
        var connection: HttpURLConnection? = null
        try {
            connection = open(url)
            if (connection.responseCode !in 200..299) {
                throw IOException("HTTP ${connection.responseCode} при скачивании VLC")
            }
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var received = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        received += read
                        progress(received, total)
                    }
                }
            }
            if (target.length() < 1_000_000) throw IOException("архив VLC слишком мал")
        } finally {
            connection?.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        var current = URL(url)
        repeat(6) {
            val connection = current.openConnection() as HttpURLConnection
            connection.connectTimeout = 20_000
            connection.readTimeout = 120_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "KinoTrendDesktop/2.3.8")
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) throw IOException("редирект без Location")
                current = URL(current, location)
                return@repeat
            }
            return connection
        }
        throw IOException("слишком много редиректов при загрузке VLC")
    }

    private fun looksLikeZip(file: File): Boolean = runCatching {
        file.inputStream().use { stream ->
            val header = ByteArray(2)
            stream.read(header) == 2 &&
                header[0] == 'P'.code.toByte() &&
                header[1] == 'K'.code.toByte()
        }
    }.getOrDefault(false)

    private fun unzip(zip: File, target: File) {
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            var entry = input.nextEntry
            while (entry != null) {
                val name = entry.name.replace('\\', '/')
                if (name.startsWith("/") || name.contains("..")) {
                    entry = input.nextEntry
                    continue
                }
                val out = File(target, name)
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { output -> input.copyTo(output) }
                }
                entry = input.nextEntry
            }
        }
    }
}
