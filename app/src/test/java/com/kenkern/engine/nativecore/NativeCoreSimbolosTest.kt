package com.kenkern.engine.nativecore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * E1 (v0.1.18) — SÍMBOLOS JNI DO NÚCLEO NATIVO no HOST (regressão do bug
 * "handler nativo não instala"):
 *
 * FATOS DO APARELHO (v0.1.17): a .so carregava (versão 1.0.0, arm64-v8a),
 * mas instalarHandlerSinaisNativo/handlerInstaladoNativo davam
 * "No implementation found" — o C++ exportava …_instalarHandlerSinais /
 * …_handlerInstalado (NOME sem o sufixo "Nativo" da declaração Kotlin;
 * kenkern_native.cpp linhas 205/236 do commit 425ad44).
 *
 * PROVA AQUI: a MESMA fonte C++ compilada para o HOST (task
 * compilarNativoHost, g++) é carregada via -Dkenkern.native.hostpath; TODAS
 * as externas são resolvidas e CHAMADAS — se qualquer símbolo JNI sumir de
 * novo, instalarHandlerSinais devolve false (não lança) e o teste falha com
 * o motivo. No CI há uma segunda prova sobre o APK: scripts/check_jni_symbols.sh
 * (llvm-nm -D --defined-only) confere TODA `external fun` × os símbolos da
 * libkenkern.so arm64 extraída do APK release.
 *
 * RESTAURAÇÃO OBRIGATÓRIA (bug do próprio E1 no CI): a JVM de testes usa
 * SIGSEGV para o NullPointer implícito (HotSpot) — o teste instala os
 * handlers, valida e RESTAURA em finally (NativeCore.restaurarSinais devolve
 * as ações antigas salvas no install); sem o restore, o primeiro NPE em
 * código JIT-ado depois deste teste derrubava o executor com exit 139
 * (SIGSEGV) no CI. No APP o restore NÃO é chamado: o handler fica ativo até
 * o processo morrer (B3).
 */
class NativeCoreSimbolosTest {

    private fun hostDisponivel(): Boolean {
        val caminho = System.getProperty("kenkern.native.hostpath") ?: return false
        return File(caminho).isFile && NativeCore.carregar()
    }

    @Test
    fun todasAsExternasDeNativeCoreResolvemEFuncionam() {
        assumeTrue("libkenkern.so do HOST ausente (sem g++) — teste assumido-skip", hostDisponivel())
        assertTrue("carregar() precisa deixar isAvailable=true", NativeCore.isAvailable)

        val caminhoCrash = File.createTempFile("kenkern-e1", ".txt").absolutePath
        try {
            val resultados = NativeCore.testarExternas(caminhoCrash)

            // E1: as 4 externas testadas pela checagem "nativo" precisam resolver
            // NA MESMA FONTE C++ do APK. Falha individual = símbolo ausente/quebrado.
            assertEquals("todas as externas foram testadas", 4, resultados.size)
            val quebradas = resultados.filter { !it.ok }
            assertTrue(
                "externas sem símbolo JNI: " + quebradas.joinToString("; ") { "${it.nome} (${it.motivoFalha})" },
                quebradas.isEmpty()
            )
            // Cada função com o nome esperado (a checagem "nativo" lista exatamente estas).
            assertEquals(
                listOf("versaoNativa", "ecoNativo", "instalarHandlerSinaisNativo", "handlerInstaladoNativo"),
                resultados.map { it.nome }
            )
        } finally {
            // OBRIGATÓRIO: devolve os handlers da JVM (sem isto o CI morria com 139).
            assertTrue("restaurarSinais precisa devolver true após instalar", NativeCore.restaurarSinais())
            assertFalse("após restaurar, handlerInstalado devolve false", NativeCore.handlerInstalado())
        }
    }

    @Test
    fun handlerDeSinaisInstalaConsultaRestauraNoHost() {
        assumeTrue("libkenkern.so do HOST ausente (sem g++) — teste assumido-skip", hostDisponivel())
        // As DUAS funções que faltavam no aparelho, chamadas diretamente:
        // instalar devolve true (sigaction ok), a consulta devolve true e o
        // RESTORE devolve os handlers da JVM (false na segunda chamada — nada instalado).
        val caminhoCrash = File.createTempFile("kenkern-e1-crash", ".txt").absolutePath
        try {
            assertTrue("instalarHandlerSinais precisa devolver true no host", NativeCore.instalarHandlerSinais(caminhoCrash))
            assertTrue("handlerInstalado precisa devolver true após instalar", NativeCore.handlerInstalado())
            assertEquals("1.0.0", NativeCore.versao)
        } finally {
            assertTrue(NativeCore.restaurarSinais())
            assertFalse("restaurar de novo (sem handler instalado) devolve false SEM lançar", NativeCore.restaurarSinais())
        }
    }
}
