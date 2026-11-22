package com.kenkern.engine.nativecore

import android.os.Build
import com.kenkern.engine.diag.KenkernLog

/**
 * NÚCLEO NATIVO da KENKERN (B3, v0.1.17): objeto Kotlin de acesso ao
 * libkenkern.so (C++17 via NDK/CMake; arm64-v8a).
 *
 *  - [isAvailable]: a biblioteca carregou? O carregamento é em try/catch
 *    REGISTRADO no KenkernLog — biblioteca ausente NUNCA derruba o app;
 *  - [versao]/[abi]: versão do núcleo nativo e ABI do aparelho;
 *  - [eco]: chamada JNI mínima de validação (eco(n) = n·2+1) com FALLBACK
 *    Kotlin automático (mesma fórmula) quando a biblioteca não existe;
 *  - [instalarHandlerSinais]/[handlerInstalado]: handler de SIGSEGV/SIGABRT/
 *    SIGBUS/SIGFPE que grava crash_logs/native_crash.txt com sinal, endereço
 *    e backtrace (o diálogo de crash do Hub mostra esse arquivo também).
 *
 * REGRA DO ROADMAP: todo módulo nativo tem FALLBACK Kotlin automático — o
 * aparelho sem a biblioteca (ou com ABI não coberta) continua funcionando,
 * com o motivo registrado no Console.
 */
object NativeCore {

    private const val TAG = "Kenkern/NativeCore"

    /** Versão esperada do núcleo nativo (precisa casar com kenkern_native.cpp). */
    const val VERSAO_ESPERADA = "1.0.0"

    @Volatile
    var isAvailable: Boolean = false
        private set

    /** Versão devolvida PELO NATIVO (null = biblioteca não carregada). */
    @Volatile
    var versao: String? = null
        private set

    /** ABI primária do aparelho (arm64-v8a é a única compilada por enquanto). */
    val abi: String
        get() = Build.SUPPORTED_ABIS?.firstOrNull() ?: "desconhecida"

    @Volatile
    private var cargaTentada = false

    /**
     * Carrega a biblioteca (idempotente). Chamado no Application.onCreate e
     * pela checagem "nativo" do Diagnóstico. Falha = registro no Console +
     * isAvailable false (módulos nativos seguem em fallback Kotlin).
     *
     * SEAM DE TESTE (B4, v0.1.17): a propriedade de sistema
     * `kenkern.native.hostpath` carrega a MESMA fonte nativa compilada para
     * o HOST (g++, x86-64) — os testes JVM exercitam o etcpak nativo×Kotlin
     * sem aparelho. Nunca setada em produção (mesmo padrão do
     * ImportModelCommand.testeFalhaNoMove).
     */
    @Synchronized
    fun carregar(): Boolean {
        if (cargaTentada) return isAvailable
        cargaTentada = true
        try {
            val caminhoHost = System.getProperty("kenkern.native.hostpath")
            if (caminhoHost != null && java.io.File(caminhoHost).isFile) {
                System.load(caminhoHost)
            } else {
                System.loadLibrary("kenkern")
            }
            isAvailable = true
            versao = versaoNativa()
            KenkernLog.w(TAG, "núcleo nativo carregado: versão $versao · ABI $abi")
        } catch (e: Throwable) {
            // ABI não coberta (ex.: emulador x86) ou biblioteca corrompida —
            // motivo REGISTRADO, app segue no fallback Kotlin.
            KenkernLog.w(TAG, "núcleo nativo INDISPONÍVEL (${e.message ?: e.javaClass.simpleName}) — módulos nativos usam fallback Kotlin", e)
        }
        return isAvailable
    }

    /**
     * Eco de validação: NATIVO quando disponível; FALLBACK Kotlin (mesma
     * fórmula n·2+1) caso contrário. A checagem "nativo" do Diagnóstico
     * confere isAvailable + eco(21) == 43 para validar a chamada JNI.
     */
    fun eco(valor: Int): Int {
        if (!isAvailable) return valor * 2 + 1
        return try {
            ecoNativo(valor)
        } catch (e: Throwable) {
            KenkernLog.e(TAG, "eco nativo falhou — usando fallback Kotlin", e)
            valor * 2 + 1
        }
    }

    /**
     * Instala o handler de sinais nativos (SIGSEGV/SIGABRT/SIGBUS/SIGFPE) que
     * grava [caminhoArquivo] (crash_logs/native_crash.txt) com sinal, endereço
     * e backtrace. Retorna false se a biblioteca não existe ou a instalação
     * falhou (app segue SEM handler nativo; nada derruba o boot).
     */
    fun instalarHandlerSinais(caminhoArquivo: String): Boolean {
        if (!isAvailable) return false
        return try {
            instalarHandlerSinaisNativo(caminhoArquivo)
        } catch (e: Throwable) {
            KenkernLog.e(TAG, "handler de sinais nativo não instalou — crash nativo não gera native_crash.txt", e)
            false
        }
    }

    /** True quando os 4 handlers nativos estão instalados (Diagnóstico). */
    fun handlerInstalado(): Boolean {
        if (!isAvailable) return false
        return try {
            handlerInstaladoNativo()
        } catch (e: Throwable) {
            KenkernLog.e(TAG, "consulta do handler nativo falhou", e)
            false
        }
    }

    /**
     * Resultado individual de UMA função JNI externa (checagem "nativo", E1):
     * [nome] é a `external fun` do Kotlin, [ok] diz se a chamada resolveu e
     * [motivoFalha] traz o erro LITERAL do JNI ("No implementation found…")
     * quando o símbolo não existe na .so — nada de diagnóstico genérico.
     */
    class ResultadoExterna(val nome: String, val ok: Boolean, val motivoFalha: String? = null)

    /**
     * TESTA cada função JNI uma a uma (E1, v0.1.18) — a checagem "nativo" do
     * Diagnóstico LISTA cada função testada e as que faltam. [caminhoCrash] é
     * o caminho REAL de crash_logs/native_crash.txt (instalar de novo é
     * idempotente; o Application já instala no boot com o mesmo caminho).
     * NUNCA provoca crash de propósito: só chama as funções e registra os
     * erros que elas próprias devolverem.
     */
    fun testarExternas(caminhoCrash: String?): List<ResultadoExterna> {
        if (!isAvailable) {
            return listOf(ResultadoExterna("biblioteca", false, "libkenkern.so não carregou (isAvailable=false)"))
        }
        val resultados = ArrayList<ResultadoExterna>(4)

        resultados += testarUma("versaoNativa") {
            val v = versaoNativa()
            if (v != VERSAO_ESPERADA) throw IllegalStateException("versão nativa $v ≠ esperada $VERSAO_ESPERADA")
        }
        resultados += testarUma("ecoNativo") {
            if (ecoNativo(21) != 43) throw IllegalStateException("eco(21) ≠ 43 — ponte JNI quebrada")
        }
        resultados += testarUma("instalarHandlerSinaisNativo") {
            if (caminhoCrash == null) throw IllegalStateException("sem caminho de crash_logs para instalar")
            if (!instalarHandlerSinaisNativo(caminhoCrash)) throw IllegalStateException("sigaction/caminho recusado (ver CONSOLE)")
        }
        resultados += testarUma("handlerInstaladoNativo") {
            if (!handlerInstaladoNativo()) throw IllegalStateException("consulta devolveu false (ver CONSOLE)")
        }
        return resultados
    }

    /** Chama UMA externa capturando o erro literal (UnsatisfiedLinkError/Throwable) — motivo visível no relatório. */
    private inline fun testarUma(nome: String, bloco: () -> Unit): ResultadoExterna {
        return try {
            bloco()
            ResultadoExterna(nome, true)
        } catch (e: Throwable) {
            KenkernLog.e(TAG, "externa '$nome' falhou na checagem do Diagnóstico", e)
            ResultadoExterna(nome, false, e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Restaura os handlers ANTERIORES à instalação (CI/CI-E1): usado SÓ pelo
     * teste no HOST — a JVM de testes usa SIGSEGV para NullPointer implícito
     * (HotSpot); handler nosso + NPE em código JIT-ado derrubava o executor
     * com exit 139 (SIGSEGV) no CI. No APP nunca é chamado: o handler fica
     * ativo até o processo morrer (comportamento do B3 preservado).
     */
    fun restaurarSinais(): Boolean {
        if (!isAvailable) return false
        return try {
            restaurarSinaisNativo()
        } catch (e: Throwable) {
            KenkernLog.e(TAG, "restauração dos handlers nativos falhou", e)
            false
        }
    }

    // ------------------------------------------------- externos (JNI)

    private external fun ecoNativo(valor: Int): Int
    private external fun versaoNativa(): String
    private external fun instalarHandlerSinaisNativo(caminhoArquivo: String): Boolean
    private external fun handlerInstaladoNativo(): Boolean
    private external fun restaurarSinaisNativo(): Boolean
}
