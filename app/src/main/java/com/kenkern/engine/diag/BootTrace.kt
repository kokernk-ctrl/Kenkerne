package com.kenkern.engine.diag

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Marcador de progresso da abertura do editor (rastreio de boot).
 *
 * Grava cada etapa em `filesDir/boot_trace.txt` com flush em disco a cada passo.
 * Propósito: crash NATIVO (SIGSEGV em driver GL/EGL, OOM, abort) mata o processo
 * ANTES de o handler de exceção gravar crash_logs/last_crash.txt — a última etapa
 * registrada aqui localiza onde a morte aconteceu (ex.: entre "surface criada" e
 * "shader compilado" = eglCreateWindowSurface/compilação de shader).
 *
 * Etapas, em ordem:
 *  1. "openProject início"     — EditorViewModel abre/cria um projeto
 *  2. "cena carregada"         — ProjectManager.loadScene retornou
 *  3. "EditorScreen composto"  — primeira composição da tela do editor
 *  4. "surface criada"         — GLSurfaceView.onSurfaceCreated (EGL pronta)
 *  5. "shader compilado"       — backend GLES linkou o programa principal
 *  6. "primeiro frame"         — primeiro renderFrame completo no thread GL
 *
 * Nunca lança exceção (runCatching): uma falha de disco não pode derrubar o app.
 */
object BootTrace {

    private const val TAG = "Kenkern/BootTrace"
    private const val FILE_NAME = "boot_trace.txt"

    /** Contexto de app capturado no Application.onCreate (gravação de qualquer thread). */
    @Volatile
    private var appContext: Context? = null

    /** Trava de escrita: as etapas podem vir do thread de UI e do thread GL. */
    private val lock = Any()

    /** Deve ser chamado uma vez no Application.onCreate. */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Arquivo do marcador (usado pelo diálogo de crash do Hub). */
    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    /** Zera o marcador: início de uma NOVA tentativa de abrir o editor. */
    fun reset() {
        val ctx = appContext ?: return
        synchronized(lock) {
            runCatching { file(ctx).delete() }
                .onFailure { KenkernLog.w(TAG, "reset do boot_trace falhou — rastro antigo pode sobreviver", it) }
        }
    }

    /**
     * Registra uma etapa (append) com flush explícito antes de fechar o arquivo.
     * Cada linha só aparece DEPOIS que a etapa correspondente foi concluída:
     * a última linha do arquivo = última etapa alcançada.
     */
    fun mark(step: String) {
        val ctx = appContext ?: return
        synchronized(lock) {
            runCatching {
                val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT).format(Date())
                val thread = Thread.currentThread().name
                val line = "[$stamp] [${thread}] $step\n"
                FileWriter(file(ctx), true).use { writer ->
                    writer.write(line)
                    writer.flush()
                }
            }.onFailure {
                // Etapa perdida = rastro de crash incompleto — registrar (regra nova).
                KenkernLog.w(TAG, "etapa '$step' não gravada no boot_trace", it)
            }
        }
    }

    /** Última etapa registrada (linha completa, com hora/thread), ou null se vazia/ausente. */
    fun lastStep(context: Context): String? {
        val lines = runCatching { file(context).readLines() }.getOrNull() ?: return null
        return lines.lastOrNull { it.isNotBlank() }
    }
}
