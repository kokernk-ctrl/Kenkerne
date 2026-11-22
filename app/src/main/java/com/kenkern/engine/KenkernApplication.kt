package com.kenkern.engine

import android.app.Application
import android.util.Log
import com.kenkern.engine.core.KenkernBuiltInClasses
import com.kenkern.engine.core.NickKindCatalog
import com.kenkern.engine.diag.BootTrace
import com.kenkern.engine.diag.KenkernLog
import com.kenkern.engine.nativecore.NativeCore
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
        // Marcador de progresso da abertura do editor (diagnóstico de crash nativo):
        // captura o contexto de app para gravação com flush a partir de qualquer thread.
        BootTrace.init(this)
        KenkernBuiltInClasses.registerAll()
        NickKindCatalog.registerAll()
        instalarHandlerNativo()
        installCrashLogger()
    }

    /**
     * B3 (v0.1.17): handler de SINAIS NATIVOS (SIGSEGV/SIGABRT/SIGBUS/SIGFPE) —
     * o handler Kotlin de exceções NÃO VÊ crash nativo. O handler C++ grava
     * crash_logs/native_crash.txt com sinal, endereço e backtrace ANTES de o
     * processo morrer; o diálogo de crash do Hub mostra esse arquivo também.
     * Falha de carga/instalação = registro no Console e app segue normalmente.
     */
    private fun instalarHandlerNativo() {
        runCatching {
            if (NativeCore.carregar()) {
                val dir = File(filesDir, "crash_logs")
                if (!dir.isDirectory) dir.mkdirs()
                val ok = NativeCore.instalarHandlerSinais(File(dir, "native_crash.txt").absolutePath)
                if (!ok) {
                    KenkernLog.w("Kenkern/Application", "handler de sinais nativo não instalou — crash nativo não gera native_crash.txt", null)
                }
            } else {
                KenkernLog.w("Kenkern/Application", "núcleo nativo indisponível — módulos nativos em fallback Kotlin (ver Console)", null)
            }
        }.onFailure {
            KenkernLog.w("Kenkern/Application", "instalação do handler nativo falhou — app segue sem ele", it)
        }
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
                // B3: native_crash.txt (handler C++) NUNCA é apagado daqui — ele
                // sobrevive até o usuário ver o relatório no Hub.
                dir.listFiles()
                    ?.filter { it.isFile && it.name != "native_crash.txt" }
                    ?.sortedByDescending { it.lastModified() }
                    ?.drop(1)
                    ?.forEach { it.delete() }
            }.onFailure {
                // A gravação do crash report falhou — registrar (a repropagação
                // ao handler do sistema acontece abaixo, incondicionalmente).
                KenkernLog.e("Kenkern/Application", "crash report não gravado no disco", it)
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
