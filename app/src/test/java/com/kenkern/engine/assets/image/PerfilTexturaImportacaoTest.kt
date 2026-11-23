package com.kenkern.engine.assets.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PerfilTexturaImportacaoTest {
    private fun perfil(resolucao: Int, maxGpu: Int) =
        PerfilTexturaImportacao.criar(resolucao, maxGpu, Etc2.Qualidade.RAPIDA)

    @Test
    fun `Original usa o teto do aparelho ou um limite conservador antes do primeiro frame`() {
        assertEquals(8192, perfil(PerfilTexturaImportacao.ORIGINAL, 8192).limite)
        assertEquals(2048, perfil(PerfilTexturaImportacao.ORIGINAL, 0).limite)
        assertEquals(16384, perfil(PerfilTexturaImportacao.ORIGINAL, 32768).limite)
        assertEquals(1024, perfil(2048, 1024).limite)
        assertEquals(512, perfil(512, 4096).limite)
    }

    @Test
    fun `cache separa resolucoes e qualidade de encoder`() {
        val hash = "123"
        val rapido = perfil(512, 4096)
        val alta = PerfilTexturaImportacao.criar(512, 4096, Etc2.Qualidade.ALTA)
        assertEquals(rapido.caminho(hash), perfil(512, 8192).caminho(hash))
        assertNotEquals(rapido.caminho(hash), perfil(2048, 4096).caminho(hash))
        assertNotEquals(rapido.caminho(hash), alta.caminho(hash))
        assertNotEquals(rapido.caminho(hash), rapido.caminho("456"))
    }

    @Test
    fun `decodificacao subamostra sem passar abaixo do limite`() {
        assertEquals(1, PerfilTexturaImportacao.amostraDecodificacao(1023, 512))
        assertEquals(2, PerfilTexturaImportacao.amostraDecodificacao(1024, 512))
        assertEquals(8, PerfilTexturaImportacao.amostraDecodificacao(4096, 512))
    }
}
