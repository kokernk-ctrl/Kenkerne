package com.kenkern.engine.diag

import com.kenkern.engine.assets.import.GltfAmostras
import com.kenkern.engine.assets.import.GltfImporter
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1b (v0.1.24) — REPRODUÇÃO da falha "amostra quad.glb importou SEM tabela
 * de materiais" que o usuário vê na checagem "materiais" do Diagnóstico.
 *
 * DIAGNÓSTICO DA CAUSA (importador × checagem): a amostra `quadGlb()` do
 * GltfAmostras é um glTF SEM NENHUM material no JSON — o importador
 * importar uma tabela VAZIA para ela é COMPORTAMENTO CORRETO (o modelo não
 * tem materiais). O bug é da CHECAGEM: checarMateriais (Diagnostico.kt,
 * v0.1.23) exigia tabela cheia justamente dessa amostra sem material. A
 * amostra com material é `quadComMaterialGlb()` (baseColorFactor
 * 0.8/0.2/0.1, metallic 0.5, roughness 0.4, doubleSided) — o importador
 * já traz o material dela (provado em KernMaterialPbrTest).
 */
class DiagMateriaisAmostraTest {

    @Test
    fun `checagem materiais fica verde — amostra COM material importa tabela cheia`() {
        val problemas = ArrayList<String>()
        Diagnostico.avaliarAmostraMateriais(problemas)
        assertTrue(
            "a checagem não pode falhar com a amostra correta: $problemas",
            problemas.isEmpty()
        )
    }

    @Test
    fun `modelo glTF SEM materiais importa tabela vazia — importador não inventa material`() {
        val r = GltfImporter.importar(GltfAmostras.quadGlb())
        assertTrue(
            "quad sem material no JSON importa tabela VAZIA (correto — é a " +
                "prova de que o importador não é o bug do S1b)",
            r.mesh.materiais.isEmpty()
        )
    }
}
