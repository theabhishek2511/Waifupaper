package fyi.ryujin.waifu.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val LOG_FILE = "waifupaper.log"
    private const val MAX_LOG_SIZE = 2 * 1024 * 1024 // 2 MB
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private lateinit var logFile: File
    private val lock = Any()

    fun init(context: Context) {
        logFile = File(context.filesDir, LOG_FILE)
    }

    private fun write(level: String, tag: String, message: String, throwable: Throwable? = null) {
        if (!::logFile.isInitialized) return
        synchronized(lock) {
            try {
                rotateIfNeeded()
                FileWriter(logFile, true).use { writer ->
                    val timestamp = dateFormat.format(Date())
                    writer.appendLine("$timestamp $level/$tag: $message")
                    if (throwable != null) {
                        val sw = java.io.StringWriter()
                        throwable.printStackTrace(PrintWriter(sw))
                        writer.appendLine(sw.toString().trimEnd())
                    }
                }
            } catch (_: Exception) {
                // Don't let logging failures crash the app
            }
        }
    }

    private fun rotateIfNeeded() {
        if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
            val old = File(logFile.parent, "waifupaper_old.log")
            old.delete()
            logFile.renameTo(old)
        }
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        write("D", tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        write("I", tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
        write("W", tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
        write("E", tag, message, throwable)
    }

    fun getLogFile(): File? {
        if (!::logFile.isInitialized || !logFile.exists()) return null
        return logFile
    }

    fun clearLogs() {
        if (!::logFile.isInitialized) return
        synchronized(lock) {
            logFile.delete()
            File(logFile.parent, "waifupaper_old.log").delete()
        }
    }
}
