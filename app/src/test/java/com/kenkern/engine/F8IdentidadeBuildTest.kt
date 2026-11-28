package com.kenkern.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F8 (v0.1.31) — identidade da build. O teste REPROVA uma build sem hash:
 * build sem identidade (versão, versionCode, hash curto do commit, data) não
 * pode ser distribuída — o usuário não conseguiria dizer qual commit está
 * rodando e o Diagnóstico perde a base de comparação.
 *
 * A build real injeta BUILD_HASH/BUILD_DATE em BuildConfig (build.gradle.kts,
 * F8); esta classe prova a linha montada nos DOIS casos (com hash válido e
 * degradada sem hash) e que a build atual TEM hash.
 */
class F8IdentidadeBuildTest {

    @Test
    fun `build real tem hash curto hex valido`() {
        assertTrue(
            "build sem hash é reprovada (F8): '${IdentidadeBuild.hashCurto}'",
            IdentidadeBuild.hashValido()
        )
        assertTrue(IdentidadeBuild.dataBuild.isNotBlank())
        assertTrue(IdentidadeBuild.versao.isNotBlank())
        assertTrue(IdentidadeBuild.versionCode > 0)
    }

    @Test
    fun `linha de identidade contém versão versionCode hash e data`() {
        val linha = IdentidadeBuild.linha()
        assertTrue("versão: $linha", linha.contains("v${IdentidadeBuild.versao}"))
        assertTrue("versionCode: $linha", linha.contains("vc${IdentidadeBuild.versionCode}"))
        assertTrue("hash: $linha", linha.contains(IdentidadeBuild.hashCurto))
        assertTrue("data: $linha", linha.contains(IdentidadeBuild.dataBuild))
        assertFalse("sem hash na build real: $linha", linha.contains("(sem hash)"))
    }

    @Test
    fun `linha degradada sem hash diz (sem hash) — forma reprovada em build real`() {
        val degradada = IdentidadeBuild.linha(versao = "0.1.31", versionCode = 34, hash = "", data = "2026-01-01")
        assertTrue(degradada.contains("(sem hash)"))
        assertFalse(IdentidadeBuild.hashValido(""))
        assertFalse(IdentidadeBuild.hashValido("texto não hex"))
        assertTrue(IdentidadeBuild.hashValido("a1b2c3d"))
        assertTrue(IdentidadeBuild.hashValido("0123456789abcdef0123456789abcdef01234567"))
    }
}
