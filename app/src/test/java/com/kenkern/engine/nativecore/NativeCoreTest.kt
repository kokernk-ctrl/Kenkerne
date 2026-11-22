package com.kenkern.engine.nativecore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B3 (v0.1.17) — NÚCLEO NATIVO no AMBIENTE JVM (fallback):
 *
 * Na JVM dos testes NÃO existe libkenkern.so (ela é compilada para arm64-v8a
 * pelo NDK) — exatamente o cenário de FALLBACK Kotlin automático que o B3b
 * exige: a biblioteca ausente NUNCA derruba o app; o eco devolve a MESMA
 * fórmula em Kotlin; as consultas de handler devolvem false sem lançar.
 * A validação REAL (biblioteca + eco JNI + handler de sinais) roda no
 * APARELHO pela checagem "nativo" do Diagnóstico.
 */
class NativeCoreTest {

    @Test
    fun semBibliotecaOCoreNaoDerrubaEFicaIndisponivel() {
        val carregada = NativeCore.carregar()
        if (carregada) {
            // Máquina exótica com a lib no path? Ainda assim o contrato vale.
            assertTrue(NativeCore.isAvailable)
            return
        }
        assertFalse("sem libkenkern.so: isAvailable = false", NativeCore.isAvailable)
        // Carregar DE NOVO é idempotente (não recarrega nem lança).
        assertFalse(NativeCore.carregar())
    }

    @Test
    fun ecoUsaFallbackKotlinComAMesmaFormula() {
        NativeCore.carregar()
        if (!NativeCore.isAvailable) {
            // FALLBACK Kotlin automático (B3b): mesma fórmula do C++ (n·2+1).
            assertEquals(43, NativeCore.eco(21))
            assertEquals(1, NativeCore.eco(0))
            assertEquals(-1, NativeCore.eco(-1))
            assertEquals(201, NativeCore.eco(100))
        } else {
            assertEquals(43, NativeCore.eco(21))
        }
    }

    @Test
    fun handlerSemBibliotecaDevolveFalseSemLancar() {
        NativeCore.carregar()
        if (!NativeCore.isAvailable) {
            assertFalse(NativeCore.handlerInstalado())
            assertFalse(NativeCore.instalarHandlerSinais("/tmp/kenkern-teste/native_crash.txt"))
        }
        // com a biblioteca (cenário impossível na JVM comum) o teste do
        // aparelho cobre; aqui só garantimos que NÃO lança.
    }

    @Test
    fun versaoEsperadaEFixaEAbiNuncaVazia() {
        assertEquals("1.0.0", NativeCore.VERSAO_ESPERADA)
        assertTrue(NativeCore.abi.isNotBlank())
    }
}
