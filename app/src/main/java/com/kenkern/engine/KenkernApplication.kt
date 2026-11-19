package com.kenkern.engine

import android.app.Application
import android.util.Log
import com.kenkern.engine.core.KenkernBuiltInClasses
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Application da KENKERN: registra as classes/funções nativas da engine e
 * captura logs de crash em arquivos locais (auxilia diagnóstico pós-implantação;
 * a recuperação da cena acontece via auto-save em ProjectManager.loadScene).
 */
class KenkernApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        KenkernBuiltInClasses.registerAll()
        installCrashLogger()
    }

    private fun installCrashLogger() {
        val platform = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val dir = File(filesDir, "crash_logs")
                if (!dir.isDirectory) dir.mkdirs()
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())
                File(dir, "last_crash.txt").writeText(
                    buildString {
                        appendLine("KENKERN crash report — $stamp")
                        appendLine("app: ${appVersion()}")
                        appendLine("thread: ${thread.name}")
                        appendLine(throwable.javaClass.name)
                        appendLine(throwable.message ?: "")
                        appendLine()
                        appendLine(throwable.stackTraceToString())
                    }
                )
                // Mantém apenas o crash mais recente (economiza espaço em disco).
                dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(1)?.forEach { it.delete() }
            }
            Log.e("Kenkern/Application", "Crash capturado", throwable)
            platform?.uncaughtException(thread, throwable)
        }
    }

    /** Versão instalada (versionName + versionCode) para correlacionar o crash à release. */
    private fun appVersion(): String = runCatching {
        val pm = packageManager.getPackageInfo(packageName, 0)
        "${pm.versionName} (vc${pm.versionCode})"
    }.getOrDefault("desconhecida")
}
