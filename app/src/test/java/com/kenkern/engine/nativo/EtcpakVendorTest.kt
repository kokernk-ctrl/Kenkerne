package com.kenkern.engine.nativo

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * F8/X-CI (v0.1.31) — contrato do subconjunto etcpak vendorizado.
 *
 * Causa raiz do link quebrado no emulador (Portão 2, execução 4): o
 * `ProcessRGB.cpp` chama `Dither(uint8_t*)` no caminho `__SSE4_1__`
 * (compilado para x86_64 — a ABI do emulador do CI), mas o subconjunto
 * vendorizado OMITIU o `Dither.cpp` do upstream, onde a função é definida.
 * No arm64 (caminho NEON, sem SSE) a referência nunca é gerada e o bug
 * ficava invisível no release (abiFilters = arm64-v8a).
 *
 * Este teste REPROVA o contrato: Dither.cpp existe, está no CMake do NDK e
 * o par ProcessRGB+Dither LINKA com SSE4.1 — o mesmo conjunto de macros que
 * o clang do NDK define para x86_64.
 */
class EtcpakVendorTest {

    private val raiz = File("..").canonicalFile

    @Test
    fun `Dither cpp existe e está registrado no build`() {
        val dither = File(raiz, "third_party/etcpak/Dither.cpp")
        assertTrue("third_party/etcpak/Dither.cpp ausente (contrato do subconjunto)", dither.isFile)
        assertTrue("Dither.cpp define Dither(uint8_t*)", dither.readText().contains("void Dither( uint8_t* data )"))

        val cmake = File("src/main/cpp/CMakeLists.txt").readText()
        assertTrue("CMakeLists.txt não compila Dither.cpp", cmake.contains("Dither.cpp"))
    }

    @Test
    fun `ProcessRGB mais Dither linka com SSE4_1 (caminho do emulador x86_64)`() {
        val gpp = executar("which", "g++").trim()
        assertTrue("g++ necessário para a prova de link do host", gpp.isNotEmpty())

        val saida = File("/tmp/kenkern-vendor-test/libkenkern-sse41.so")
        saida.parentFile.mkdirs()
        val log = executar(
            "g++", "-std=c++17", "-msse4.1", "-O1", "-fPIC", "-shared",
            "-I${File(raiz, "third_party").absolutePath}",
            File(raiz, "third_party/etcpak/ProcessRGB.cpp").absolutePath,
            File(raiz, "third_party/etcpak/Dither.cpp").absolutePath,
            File(raiz, "third_party/etcpak/Tables.cpp").absolutePath,
            "-o", saida.absolutePath
        )
        assertTrue("link SSE4.1 falhou: $log", saida.isFile)

        val nm = executar("nm", "-D", "--defined-only", saida.absolutePath)
        assertTrue("Dither deve estar DEFINIDO na .so do caminho SSE4.1", nm.contains("Dither"))
    }

    private fun executar(vararg comando: String): String =
        ProcessBuilder(*comando).redirectErrorStream(true).start().let { p ->
            val saida = p.inputStream.readBytes().toString(Charsets.UTF_8)
            p.waitFor()
            saida
        }
}
