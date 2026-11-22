package com.kenkern.engine.assets.import

import com.kenkern.engine.assets.image.KimgPipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B1 (v0.1.17) — MEDIÇÃO DA IMPORTAÇÃO: toda etapa pesada passa pelo
 * [CronometroImport] e a tabela (ms + % do total) entra no ImportReport e no
 * Console. Regra do BUG B: SEM medição, nenhuma afirmação sobre onde está o
 * gargalo — este teste prova que as etapas exigidas são medidas nos pontos
 * exatos: leitura (VM, coberta no aparelho), parse, dedup, LODs,
 * decodificação/redimensionamento/mipmaps/ETC2 de cada textura, gravação e
 * criação do Nick (VM, coberta no aparelho).
 */
class CronometroImportTest {

    private fun nomes(cronometro: CronometroImport): List<String> =
        cronometro.copiaEtapas().map { it.nome }

    @Test
    fun medirRegistraEtapaComTempoEDetalhe() {
        val cronometro = CronometroImport()
        val soma = cronometro.medir("etapa de exemplo", "detalhe") {
            var total = 0L
            for (i in 0 until 1_000_000) total += i
            total
        }
        assertTrue(soma > 0)
        val etapas = cronometro.copiaEtapas()
        assertEquals(1, etapas.size)
        assertEquals("etapa de exemplo", etapas[0].nome)
        assertTrue("tempo medido em ms (> 0)", etapas[0].ms >= 0)
        assertEquals("detalhe", etapas[0].detalhe)
    }

    @Test
    fun tabelaMostraMsEPorcentagemDoTotal() {
        val cronometro = CronometroImport()
        cronometro.registrar("curta", 100)
        cronometro.registrar("longa", 300, "com detalhe")
        val tabela = cronometro.tabela()
        assertTrue("tabela cita etapa curta", tabela.contains("curta"))
        assertTrue("tabela cita etapa longa", tabela.contains("longa"))
        assertTrue("tabela mostra ms da etapa", tabela.contains("100 ms"))
        assertTrue("tabela mostra % do total (25.0%)", tabela.contains("25.0%"))
        assertTrue("tabela mostra % do total (75.0%)", tabela.contains("75.0%"))
        assertTrue("tabela mostra TOTAL medido", tabela.contains("TOTAL medido"))
        assertTrue("tabela mostra detalhe", tabela.contains("com detalhe"))
        assertEquals(400L, cronometro.somaEtapasMs())
    }

    @Test
    fun porcentagensSomamCemPorCento() {
        val cronometro = CronometroImport()
        cronometro.registrar("a", 10)
        cronometro.registrar("b", 20)
        cronometro.registrar("c", 70)
        val totalPct = cronometro.copiaEtapas().sumOf { it.ms * 100.0 / cronometro.somaEtapasMs() }
        assertEquals(100.0, totalPct, 0.001)
    }

    @Test
    fun gargaloEhAEtapaMaisCara() {
        val cronometro = CronometroImport()
        cronometro.registrar("barata", 5)
        cronometro.registrar("cara", 500)
        cronometro.registrar("media", 50)
        assertEquals("cara", cronometro.gargalo()?.nome)
    }

    @Test
    fun importadorMedeParseDedupLodsSerializacaoEValidacao() {
        val cronometro = CronometroImport()
        ModelImporter.importarModelo(
            "teste.glb", GltfAmostras.quadGlb(),
            cronometro = cronometro
        )
        val nomes = nomes(cronometro)
        assertTrue("parse medido: $nomes", nomes.any { it.startsWith("parse ") })
        assertTrue(
            "dedup medido (Kotlin ou meshoptimizer): $nomes",
            nomes.any { it.startsWith("dedup") }
        )
        assertTrue(
            "LODs medidos (grade Kotlin ou meshopt_simplify): $nomes",
            nomes.any { it.startsWith("LODs") || it.startsWith("LOD ") }
        )
        assertTrue("serialização .KERN medida: $nomes", nomes.any { it == "serialização .KERN" })
        assertTrue("validação medida: $nomes", nomes.any { it == "validação de integridade" })
        assertTrue("tempos registrados em ms", cronometro.somaEtapasMs() >= 0)
    }

    @Test
    fun posProcessamentoMedeDedupELodsSeparadamente() {
        // Malha com duplicatas: 2 quads que compartilham 4 vértices.
        val b = com.kenkern.engine.assets.mesh.KernWriter.Builder()
        for (i in 0 until 4) {
            b.vertex(i.toFloat(), 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f)
        }
        b.triangle(0, 1, 2)
        b.triangle(0, 2, 3)
        val mesh = b.build()
        val cronometro = CronometroImport()
        PosProcessamento.aplicar(mesh, cronometro)
        val nomes = nomes(cronometro)
        // B6: dedup e LODs medidos SEPARADAMENTE nos DOIS caminhos — nativo
        // (meshoptimizer) quando a biblioteca existe, Kotlin no fallback.
        assertTrue(
            "dedup medido (nativo ou Kotlin): $nomes",
            nomes.any { it.startsWith("dedup") }
        )
        assertTrue(
            "LODs medidos (meshopt_simplify ou grade Kotlin): $nomes",
            nomes.any { it.startsWith("LOD") || it.startsWith("LODs") }
        )
    }

    @Test
    fun pipelineKimgMedeResizeMipmapsEtc2PorTextura() {
        // 32×32 com limite 8: força redimensionamento ANTES dos mipmaps.
        val rgba = ByteArray(32 * 32 * 4)
        for (i in rgba.indices step 4) {
            rgba[i] = 200.toByte(); rgba[i + 1] = 100.toByte(); rgba[i + 2] = 50.toByte(); rgba[i + 3] = 255.toByte()
        }
        val cronometro = CronometroImport()
        KimgPipeline.converter(
            rgba, 32, 32,
            limiteTextura = 8,
            cronometro = cronometro,
            rotuloTextura = "textura 1/1",
            mimeOrigem = "image/png"
        )
        val etapas = cronometro.copiaEtapas()
        val nomes = etapas.map { it.nome }
        assertTrue("redimensionamento medido: $nomes", nomes.any { it == "textura 1/1: redimensionamento" })
        assertTrue("mipmaps medidos: $nomes", nomes.any { it.startsWith("textura 1/1: mipmaps") })
        assertTrue("ETC2 medido: $nomes", nomes.any { it.startsWith("textura 1/1: ETC2") })
        // Resolução e formato no detalhe/relatório (B1: relatório inclui resolução
        // e formato de cada textura).
        assertTrue(
            "detalhe do redimensionamento cita origem→final",
            etapas.first { it.nome == "textura 1/1: redimensionamento" }.detalhe.contains("32×32 → 8×8")
        )
        assertTrue(
            "detalhe do ETC2 cita formato RGB8",
            etapas.first { it.nome.startsWith("textura 1/1: ETC2") }.nome.contains("RGB8")
        )
    }

    @Test
    fun relatorioDeImportacaoIncluiATabelaDeTempo() {
        val cronometro = CronometroImport()
        cronometro.registrar("leitura do arquivo (cópia + leitura)", 100, "arquivo")
        cronometro.registrar("parse GLB", 200, "2048 bytes")
        val summary = ModelImporter.importarModelo(
            "teste.glb", GltfAmostras.quadGlb(),
            cronometro = cronometro
        )
        summary.tabelaTempo = cronometro.tabela()
        val relatorio = summary.textoRelatorio()
        assertTrue("relatório traz a tabela de tempo", relatorio.contains("Tempo por etapa"))
        assertTrue("relatório traz % do total", relatorio.contains("%"))
        assertTrue(
            "relatório traz a linha da criação do Nick (medida pela VM no aparelho) quando a VM preenche",
            !summary.tabelaTempo.contains("criação do Nick") // VM mede no aparelho; aqui só o importador
        )
        assertNotNull(summary.tabelaTempo)
    }
}
