package org.frknkrc44.hma_oss.zygote.util

import android.os.SystemProperties
import android.util.Log
import org.frknkrc44.hma_oss.common.BuildConfig
import org.frknkrc44.hma_oss.zygote.service.HMAService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Suppress("SpellCheckingInspection")
object Logcat {
    private var logdReady: Boolean? = null

    private fun parseLog(level: Int, tag: String, msg: String, cause: Throwable? = null) = buildString {
        val levelStr = when (level) {
            Log.VERBOSE -> "VERBS"
            Log.DEBUG   -> "DEBUG"
            Log.INFO    -> " INFO"
            Log.WARN    -> " WARN"
            Log.ERROR   -> "ERROR"
            else        -> "?WTF?"
        }
        val date = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        append("[$levelStr] $date ($tag) $msg")
        if (!endsWith('\n')) append('\n')
        if (cause != null) append(Log.getStackTraceString(cause))
        if (!endsWith('\n')) append('\n')
    }

    fun logWithLevel(level: Int, tag: String, msg: String, cause: Throwable? = null) {
        if (level != Log.ERROR && HMAService.instance?.config?.errorOnlyLog == true) return
        if (level <= Log.DEBUG && HMAService.instance?.config?.detailLog == false) return
        if (level == Log.VERBOSE && !BuildConfig.DEBUG) return

        val parsedMsg = parseLog(level, tag, msg, cause)

        HMAService.instance?.apply {
            executor.execute {
                addLog(parsedMsg)
                println(parsedMsg)
            }
        } ?: println(parsedMsg)
    }

    private fun println(msg: String) {
        if (logdReady == null) {
            logdReady = SystemProperties.get("init.svc.logd") == "running"
        }

        if (logdReady != true) return

        Log.i("HMA-OSS", msg)
    }

    fun logV(tag: String, msg: String, cause: Throwable? = null) = logWithLevel(Log.VERBOSE, tag, msg, cause)
    fun logD(tag: String, msg: String, cause: Throwable? = null) = logWithLevel(Log.DEBUG, tag, msg, cause)
    fun logI(tag: String, msg: String, cause: Throwable? = null) = logWithLevel(Log.INFO, tag, msg, cause)
    fun logW(tag: String, msg: String, cause: Throwable? = null) = logWithLevel(Log.WARN, tag, msg, cause)
    fun logE(tag: String, msg: String, cause: Throwable? = null) = logWithLevel(Log.ERROR, tag, msg, cause)
}
