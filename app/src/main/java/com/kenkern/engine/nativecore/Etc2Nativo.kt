package com.kenkern.engine.nativecore

import com.kenkern.engine.diag.KenkernLog
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ENCODER ETC2 NATIVO (B4, v0.1.17): ponte JNI para o etcpak (BSD-3-Clause,
 * vendorizado em third_party/etcpak) dentro do libkenkern.so.
 *
 * API: [codificar] recebe RGBA8888 (ByteArray) e devolve os blocos ETC2 —
 * RGB8 (8 bytes/bloco) ou RGBA8 (16 bytes/bloco, alfa EAC antes do RGB),
 * linha-major, a MESMA convenção do encoder Kotlin e do glCompressedTexImage2D.
 *
 * FALLBACK: qualquer falha (biblioteca ausente, erro JNI, tamanho inválido)
 * devolve null COM O MOTIVO NO CONSOLE — o chamador (Etc2.codificarRgb/Rgba)
 * usa o encoder Kotlin. Nada silencioso, nada derruba o app.
 *
 * MULTITHREAD e validação de limites moram no lado C++
 * (app/src/main/cpp/kenkern_etc2.cpp): N−1 threads em faixas de linhas,
 * dimensões/buffers conferidos ANTES de qualquer leitura, padding de borda
 * com limite de memória para dimensões não múltiplas de 4.
 */
object Etc2Nativo {

    private const val TAG = "Kenkern/Etc2Nativo"

    /** True quando o núcleo nativo (com o etcpak) está carregado. */
    fun disponivel(): Boolean = NativeCore.carregar()

    /**
     * Codifica [rgba] (largura×altura×4 bytes) em blocos ETC2 via etcpak.
     * [qualidadeRapida] = heurísticas do etcpak (padrão da importação);
     * false = busca exaustiva por bloco ("Alta qualidade").
     * Retorna os bytes dos blocos ou NULL (falha — motivo no Console; o
     * chamador cai no encoder Kotlin).
     */
    fun codificar(
        rgba: ByteArray,
        largura: Int,
        altura: Int,
        temAlfa: Boolean,
        qualidadeRapida: Boolean
    ): ByteArray? {
        if (!disponivel()) return null // fallback silencioso de DISPONIBILIDADE (já registrado no carregar())
        if (largura <= 0 || altura <= 0) {
            KenkernLog.e(TAG, "encoder nativo recusou dimensões inválidas ${largura}×$altura — usando Kotlin")
            return null
        }
        if (rgba.size < largura * altura * 4) {
            KenkernLog.e(TAG, "buffer RGBA de ${rgba.size} bytes menor que ${largura}×${altura}×4 — usando Kotlin")
            return null
        }
        val blocosX = (largura + 3) / 4
        val blocosY = (altura + 3) / 4
        val bytesPorBloco = if (temAlfa) 16 else 8
        val tamanhoSaida = blocosX * blocosY * bytesPorBloco
        return try {
            val entrada = ByteBuffer.allocateDirect(rgba.size).order(ByteOrder.nativeOrder())
            entrada.put(rgba)
            entrada.flip()
            val saida = ByteBuffer.allocateDirect(tamanhoSaida).order(ByteOrder.nativeOrder())
            val escrito = codificarNativo(entrada, saida, largura, altura, temAlfa, qualidadeRapida)
            if (escrito != tamanhoSaida) {
                KenkernLog.e(TAG, "encoder etcpak devolveu $escrito bytes (esperado $tamanhoSaida) — usando Kotlin")
                return null
            }
            val bytes = ByteArray(tamanhoSaida)
            saida.position(0)
            saida.get(bytes)
            bytes
        } catch (e: Throwable) {
            KenkernLog.e(TAG, "encoder etcpak falhou (${e.message ?: e.javaClass.simpleName}) — usando Kotlin", e)
            null
        }
    }

    /**
     * JNI: codifica de [entrada] (DIRETO, largura×altura×4) para [saida]
     * (DIRETO, blocos×(8|16)). Retorna bytes escritos ou código negativo de
     * erro (validações de tamanho/limite no C++ — ver kenkern_etc2.cpp).
     */
    private external fun codificarNativo(
        entrada: ByteBuffer,
        saida: ByteBuffer,
        largura: Int,
        altura: Int,
        temAlfa: Boolean,
        qualidadeRapida: Boolean
    ): Int
}
