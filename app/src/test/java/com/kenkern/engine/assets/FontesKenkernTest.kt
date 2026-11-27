package com.kenkern.engine.assets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Onda 0 (v0.1.28, P5) — FONTES OFL EMBUTIDAS NO APK.
 *
 * ANTES (v0.1.27): os 8 nomes de FONTES_OFL eram só RÓTULOS no modelo do
 * TELAR — nenhum .ttf existia no APK e `familiaDe()` devolvia os 3 FontFamily
 * genéricos do sistema (o texto NÃO mudava de fonte).
 *
 * AGORA: cada nome tem um .ttf REAL em app/src/main/assets/fonts/ (licenças
 * no LICENSES.md — SIL OFL 1.1). Este teste guarda o EMBEDDING: a lista dos
 * 8 nomes é exatamente a do TELAR, o caminho do asset deriva do nome e
 * CADA arquivo existe e não é vazio — ninguém apaga as fontes do APK sem o
 * CI ficar vermelho. (Carregar o Typeface em si só roda no aparelho — a
 * checagem "ui" do Diagnóstico mostra "8/8 OFL carregadas do APK".)
 */
class FontesKenkernTest {

    @Test
    fun osOitoNomesSaoOsDoTelar() {
        assertEquals(
            listOf(
                "Roboto", "RobotoMono", "NotoSans", "NotoSerif",
                "OpenSans", "Lato", "Montserrat", "JetBrainsMono"
            ),
            FontesKenkern.NOMES
        )
        // mesma lista do modelo do TELAR (FONTES_OFL) — uma só fonte da verdade
        assertEquals(
            com.kenkern.engine.ui.telar.FONTES_OFL,
            FontesKenkern.NOMES
        )
    }

    @Test
    fun caminhoDoAssetDerivaDoNome() {
        assertEquals("fonts/Roboto.ttf", FontesKenkern.arquivoDe("Roboto"))
        assertEquals("fonts/JetBrainsMono.ttf", FontesKenkern.arquivoDe("JetBrainsMono"))
    }

    @Test
    fun nomeConhecidoOuNao() {
        assertTrue(FontesKenkern.conhecida("Lato"))
        assertFalse(FontesKenkern.conhecida("Comic Sans"))
    }

    @Test
    fun osOitoTtfExistemNoProjetoESeNaoVazios() {
        // O teste roda com working dir = módulo app/ (padrão do Gradle):
        val dirAssets = File("src/main/assets/fonts")
        assertTrue("pasta de fontes ausente: ${dirAssets.absolutePath}", dirAssets.isDirectory)
        for (nome in FontesKenkern.NOMES) {
            val f = File(dirAssets, "${nome}.ttf")
            assertTrue("fonte EMBUTIDA ausente (embedding quebrado): ${f.path}", f.isFile)
            assertTrue(
                "fonte '${nome}.ttf' vazia/truncada (${f.length()} B)",
                f.length() > 10_000L // TTF real tem ≥ dezenas de KB
            )
        }
    }
}
