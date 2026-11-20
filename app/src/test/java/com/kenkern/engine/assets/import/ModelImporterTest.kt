package com.kenkern.engine.assets.import

import com.kenkern.engine.commands.ImportModelCommand
import com.kenkern.engine.core.MalhaNick
import com.kenkern.engine.core.Scene
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream

/**
 * TESTES JVM da ETAPA A (botão "Importar modelo"): detecção de formato, layout
 * de assets (nome único, zip-slip, cópia em streaming com limite/cancelamento),
 * pipeline completa do ModelImporter (GLB embutido + glTF externo em .zip) e o
 * ImportModelCommand (UM Command: undo remove Nick + recolhe arquivos, redo
 * devolve). Regra: nada de Android aqui — tudo java.* (roda no CI).
 */
class ModelImporterTest {

    private fun pastaTemp(): File =
        File(System.getProperty("java.io.tmpdir"), "kenkern-import-test-${System.nanoTime()}")

    // ------------------------------------------------ detectarFormato

    @Test
    fun `detecta formato pela assinatura e nao pela extensao`() {
        assertEquals(TipoFormato.GLB, ModelImporter.detectarFormato(GltfAmostras.quadGlb()))
        assertEquals(TipoFormato.GLTF_JSON, ModelImporter.detectarFormato(GltfAmostras.quadGltfDataUri()))
        assertEquals(TipoFormato.FBX_BIN, ModelImporter.detectarFormato(FbxAmostras.quadFbx()))
        assertEquals(TipoFormato.FBX_ASCII, ModelImporter.detectarFormato(FbxAmostras.asciiFbx()))
        assertEquals(TipoFormato.DESCONHECIDO, ModelImporter.detectarFormato(FbxAmostras.magicQuebrado()))
        assertEquals(TipoFormato.DESCONHECIDO, ModelImporter.detectarFormato(ByteArray(0)))
        // PK\x03\x04 = zip
        assertEquals(TipoFormato.ZIP, ModelImporter.detectarFormato(byteArrayOf(0x50, 0x4B, 3, 4, 0, 0, 0, 0)))
    }

    // ------------------------------------------------ layout de assets

    @Test
    fun `nomeUnico nunca sobrescreve e sanearNome mata caminho`() {
        val dir = pastaTemp()
        try {
            assertEquals("modelo.glb", AssetLayout.nomeUnico(dir, "modelo", "glb"))
            File(dir, "modelo.glb").writeBytes(byteArrayOf(1))
            assertEquals("modelo-2.glb", AssetLayout.nomeUnico(dir, "modelo", "glb"))
            File(dir, "modelo-2.glb").writeBytes(byteArrayOf(1))
            assertEquals("modelo-3.glb", AssetLayout.nomeUnico(dir, "modelo", "glb"))
            // saneamento: barra/ponto não atravessam (zip-slip por nome)
            assertEquals("a_b_c.glb", AssetLayout.nomeUnico(dir, "a/b/c", "glb"))
            assertTrue(AssetLayout.sanearNome("../../fuga").contains("fuga"))
            assertFalse(AssetLayout.sanearNome("../..").contains(".."))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `caminhoRelativoSeguro bloqueia zip-slip e absoluto`() {
        assertNull(AssetLayout.caminhoRelativoSeguro("../fuga.bin"))
        assertNull(AssetLayout.caminhoRelativoSeguro("pasta/../../fuga.bin"))
        assertNull(AssetLayout.caminhoRelativoSeguro("/absoluto.bin"))
        assertNull(AssetLayout.caminhoRelativoSeguro(""))
        assertNull(AssetLayout.caminhoRelativoSeguro("pasta//vazio.bin"))
        // backslash é normalizado para '/' — "c:/janela/bin" é relativo e seguro
        assertEquals("c:/janela/bin", AssetLayout.caminhoRelativoSeguro("c:\\janela\\bin"))
        assertEquals("pasta/quad.bin", AssetLayout.caminhoRelativoSeguro("pasta/quad.bin"))
        assertEquals("pasta/quad.bin", AssetLayout.caminhoRelativoSeguro("pasta\\quad.bin"))
    }

    @Test
    fun `copiarStreaming respeita limite e cancelamento`() {
        val dir = pastaTemp()
        val dados = ByteArray(300 * 1024) { 7 }
        try {
            val destino = File(dir, "grande.bin")
            AssetLayout.copiarStreaming(
                ByteArrayInputStream(dados), destino,
                limiteBytes = 200 * 1024
            )
            org.junit.Assert.fail("deveria ter lançado ImportException (limite)")
        } catch (e: ImportException) {
            assertTrue(e.message!!.contains("limite"))
        }
        try {
            assertFalse("parcial apagado", File(dir, "grande.bin").exists())

            // cancelamento cooperativo: lança e não deixa parcial
            val destino2 = File(dir, "cancelado.bin")
            try {
                AssetLayout.copiarStreaming(
                    ByteArrayInputStream(dados), destino2,
                    cancelado = { true }
                )
                org.junit.Assert.fail("deveria ter lançado ImportException (cancelado)")
            } catch (e: ImportException) {
                assertTrue(e.message!!.contains("cancelada"))
            }
            assertFalse(destino2.exists())

            // cópia completa sem cancelamento
            val destino3 = File(dir, "ok.bin")
            val total = AssetLayout.copiarStreaming(ByteArrayInputStream(dados), destino3)
            assertEquals(dados.size.toLong(), total)
            assertTrue(destino3.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    // ------------------------------------------------ pipeline completa

    @Test
    fun `importarModelo GLB completo com relatório e integridade`() {
        val summary = ModelImporter.importarModelo("teste.glb", GltfAmostras.quadGlb())
        assertEquals(4, summary.vertices)
        assertEquals(2, summary.triangulos)
        assertTrue(summary.integridadeOk)
        assertTrue(summary.problemasIntegridade.isEmpty())
        assertTrue("tempo medido", summary.tempoMs >= 0)
        assertTrue("pico de memória medido", summary.picoMemoriaMb > 0f)
        assertTrue(summary.bytesKern > 0)
        assertTrue(summary.bytesKernData.size == summary.bytesKern)
        assertEquals("GLB", summary.formato)
        // erro máx de quantização ≤ 1 passo (teste pedido pela ETAPA B.4, já coberto aqui)
        assertTrue("erro máx ≤ 1 passo", summary.erroMaxQuantizacao in -0.01f..1.001f)
        // relatório em texto contém os campos do diálogo (A.4)
        val texto = summary.textoRelatorio()
        assertTrue(texto.contains("ImportReport"))
        assertTrue(texto.contains("origem"))
        assertTrue(texto.contains("KernIntegrity"))
        assertTrue(texto.contains("Tempo"))
    }

    @Test
    fun `importarModelo com ajustarAoTamanho sugere escala ~2m`() {
        val summary = ModelImporter.importarModelo(
            "teste.glb", GltfAmostras.quadGlb(), ModelImporter.Opcoes(ajustarAoTamanho = true, alturaMeta = 2f)
        )
        // quad tem AABB 1×1×0 → maior extensão = 1 m → escala 2
        assertEquals(2f, summary.escalaSugerida, 0.001f)
        assertTrue(summary.avisos.any { it.contains("escala") })
    }

    @Test
    fun `arquivo vazio e formato desconhecido dao erro CLARO`() {
        try {
            ModelImporter.importarModelo("vazio.glb", ByteArray(0))
            org.junit.Assert.fail("deveria ter lançado ImportException")
        } catch (e: ImportException) {
            assertTrue(e.message!!.contains("vazio"))
        }
        try {
            ModelImporter.importarModelo("lixo.bin", "Isso não é um modelo nenhum".toByteArray())
            org.junit.Assert.fail("deveria ter lançado ImportException")
        } catch (e: ImportException) {
            assertTrue(e.message!!.contains("não reconhecido"))
        }
    }

    @Test
    fun `glTF com bin externo dentro de zip importa via resolver`() {
        val zipBytes = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(zipBytes).use { zout ->
            zout.putNextEntry(java.util.zip.ZipEntry("modelo/quad.gltf"))
            zout.write(GltfAmostras.quadGltfExterno())
            zout.closeEntry()
            zout.putNextEntry(java.util.zip.ZipEntry("modelo/quad.bin"))
            zout.write(GltfAmostras.quadBinExterno())
            zout.closeEntry()
        }
        val summary = ModelImporter.importarModelo("quad.zip", zipBytes.toByteArray())
        assertEquals(4, summary.vertices)
        assertTrue(summary.integridadeOk)
        assertTrue(summary.avisos.any { it.contains("zip") })
    }

    @Test
    fun `zip com entrada de caminho inseguro é RECUSADO com erro claro`() {
        val zipBytes = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(zipBytes).use { zout ->
            zout.putNextEntry(java.util.zip.ZipEntry("../fuga.gltf"))
            zout.write(GltfAmostras.quadGltfExterno())
            zout.closeEntry()
        }
        try {
            ModelImporter.importarModelo("hostil.zip", zipBytes.toByteArray())
            org.junit.Assert.fail("deveria ter lançado ImportException (zip-slip)")
        } catch (e: ImportException) {
            assertTrue(e.message!!.contains("inseguro"))
        }
    }

    @Test
    fun `gltf com bin externo SEM resolver dá erro claro com o nome do arquivo`() {
        try {
            ModelImporter.importarModelo("sem-resolver.gltf", GltfAmostras.quadGltfExterno())
            org.junit.Assert.fail("deveria ter lançado ImportException")
        } catch (e: ImportException) {
            // ModelImporter embrulha o erro do glTF mantendo o nome do arquivo faltante
            assertTrue(e.message!!.contains("quad.bin"))
        }
    }

    @Test
    fun `ResolvedorZip resolve na pasta do modelo e pelo nome simples`() {
        val entradas = mapOf(
            "modelo/quad.bin" to byteArrayOf(1, 2),
            "texturas/base.png" to byteArrayOf(3)
        )
        val comBase = ResolvedorZip(entradas, "modelo")
        assertNotNull(comBase.bytes("quad.bin"))
        assertNotNull(comBase.bytes("modelo/quad.bin")) // caminho completo também resolve
        assertNull(comBase.bytes("../base.png"))         // zip-slip recusado
        val semBase = ResolvedorZip(entradas)
        assertNull(semBase.bytes("quad.bin"))   // só o nome não acha (entrada tem pasta)
        assertNotNull(semBase.bytes("modelo/quad.bin")) // caminho completo resolve
    }

    // ------------------------------------------------ ImportModelCommand (A.3)

    @Test
    fun `Command da importação é UM passo no desfazer e refazer com arquivos`() {
        val raiz = pastaTemp()
        AssetLayout.criarPastas(raiz)
        try {
            val scene = Scene("Teste")
            val mundo = scene.createRootNick("Mundo")

            val kernRel = "${AssetLayout.DIR_MODELOS}/carro.KERN"
            val origemRel = "${AssetLayout.DIR_ORIGEM}/carro.glb"
            File(raiz, kernRel).writeBytes(KernBytesAmostra())
            File(raiz, origemRel).writeBytes(byteArrayOf(1, 2, 3))

            val classe = ModelImporter.criarClasseMalha(
                kernRel, floatArrayOf(-0.5f, -0.5f, -0.5f), floatArrayOf(0.5f, 0.5f, 0.5f), 1f
            )
            val cmd = ImportModelCommand(scene, mundo.id, "Carro", raiz, listOf(kernRel, origemRel), classe)

            // 1ª execução: cria o Nick com a classe Malha
            cmd.execute()
            val nick = scene.findNickByNameAnywhere("Carro")
            assertNotNull(nick)
            assertTrue(MalhaNick.temMalha(nick!!))
            assertEquals(kernRel, MalhaNick.path(nick))
            assertEquals("Mundo", nick.parent?.name)

            // UNDO: Nick sai E os arquivos vão para a lixeira (nada apagado de primeira)
            cmd.undo()
            assertNull(scene.findNickByNameAnywhere("Carro"))
            assertFalse(File(raiz, kernRel).exists())
            assertFalse(File(raiz, origemRel).exists())
            // lixeira = assets/.lixeira-import/<id>/… (mesmo caminho relativo dentro)
            val lixeira = File(raiz, "assets/.lixeira-import")
            val naLixeira = lixeira.walkTopDown().filter { it.isFile }.map { it.name }.toList()
            assertTrue("carro.KERN recolhido", naLixeira.contains("carro.KERN"))
            assertTrue("carro.glb recolhido", naLixeira.contains("carro.glb"))

            // REDO: arquivos voltam e o MESMO Nick reentra no mesmo lugar
            cmd.execute()
            val deNovo = scene.findNickByNameAnywhere("Carro")
            assertNotNull(deNovo)
            assertEquals(nick.id, deNovo!!.id)
            assertTrue(File(raiz, kernRel).exists())
            assertTrue(File(raiz, origemRel).exists())
            assertFalse(File(File(raiz, "assets/.lixeira-import"), kernRel).exists())
        assertFalse(File(File(raiz, "assets/.lixeira-import"), origemRel).exists())
        } finally {
            raiz.deleteRecursively()
        }
    }

    @Test
    fun `undo com arquivo já apagado à mão não quebra (registra e segue)`() {
        val raiz = pastaTemp()
        AssetLayout.criarPastas(raiz)
        try {
            val scene = Scene("Teste")
            val mundo = scene.createRootNick("Mundo")
            val kernRel = "${AssetLayout.DIR_MODELOS}/apagado.KERN"
            File(raiz, kernRel).writeBytes(KernBytesAmostra())
            val cmd = ImportModelCommand(
                scene, mundo.id, "Apagado", raiz, listOf(kernRel),
                ModelImporter.criarClasseMalha(kernRel, floatArrayOf(0f, 0f, 0f), floatArrayOf(1f, 1f, 1f))
            )
            cmd.execute()
            File(raiz, kernRel).delete() // o usuário apagou o asset à mão
            cmd.undo()                    // não pode lançar
            assertNull(scene.findNickByNameAnywhere("Apagado"))
        } finally {
            raiz.deleteRecursively()
        }
    }

    // ------------------------------------------------ MalhaNick (classe do Nick)

    @Test
    fun `classe Malha tem caminho e AABB e Nick sem malha devolve cubo padrão`() {
        val scene = Scene("Teste")
        val cubo = scene.createRootNick("Cubo")
        val meia = FloatArray(3)
        val centro = FloatArray(3)
        MalhaNick.meiaExtensaoLocal(cubo, meia)
        MalhaNick.centroLocal(cubo, centro)
        assertEquals(0.5f, meia[0], 1e-6f)
        assertEquals(0f, centro[0], 1e-6f)
        assertNull(MalhaNick.path(cubo))

        val malha = scene.createNick("Malha1", cubo)
        malha.addClassInstance(
            ModelImporter.criarClasseMalha(
                "assets/models/x.KERN",
                floatArrayOf(-1f, -2f, -3f), floatArrayOf(1f, 2f, 3f), 0.5f
            )
        )
        assertEquals("assets/models/x.KERN", MalhaNick.path(malha))
        MalhaNick.meiaExtensaoLocal(malha, meia)
        MalhaNick.centroLocal(malha, centro)
        assertEquals(1f, meia[0], 1e-6f)
        assertEquals(2f, meia[1], 1e-6f)
        assertEquals(3f, meia[2], 1e-6f)
        assertEquals(0f, centro[0], 1e-6f)
        assertEquals(0.5f, MalhaNick.escalaFit(malha), 1e-6f)
    }

    /** .KERN válido mínimo (pirâmide) para os testes de arquivo. */
    private fun KernBytesAmostra(): ByteArray {
        val b = com.kenkern.engine.assets.mesh.KernWriter.Builder()
        b.vertex(0f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 0f, 1f, 0f)
        b.vertex(1f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 0f, 1f, 0f)
        b.vertex(0.5f, 1f, 0.5f, 0.5f, 1f, 0.5f, 0.5f, 0.5f, 0f, 1f, 0f)
        b.triangle(0, 1, 2)
        return com.kenkern.engine.assets.mesh.KernWriter.write(b.build())
    }
}
