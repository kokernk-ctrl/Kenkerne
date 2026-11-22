package com.kenkern.engine.i18n

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * T0.1 (adendo v4 — DECISIONS #27): PARIDADE en/pt dos recursos de string.
 *
 *  1. toda chave de values/strings.xml (inglês) existe em values-pt/strings.xml
 *     e vice-versa (chave órfã = tradução que nunca aparece, ou tela em inglês);
 *  2. nenhuma string vazia em nenhum dos dois arquivos;
 *  3. placeholders (%1$s, %1$d…) IGUAIS nos dois idiomas — placeholder errado
 *     estoura em tempo de execução, não em compilação.
 *
 * A checagem equivalente roda no CI (scripts/check_i18n.sh, que também reprova
 * texto literal em Text("...") nas telas); este teste mantém a regra visível na
 * suíte JVM e roda a cada `testReleaseUnitTest`.
 */
class StringsParidadeTest {

    private fun res(rel: String): File {
        // CWD dos testes unitários = módulo :app; sobe um nível se necessário.
        var dir = File(System.getProperty("user.dir") ?: ".")
        val cand = File(dir, rel)
        if (cand.isFile) return cand
        dir = dir.parentFile ?: dir
        val cand2 = File(dir, rel)
        assertTrue("strings.xml não encontrado (procurei em user.dir e no pai): $rel", cand2.isFile)
        return cand2
    }

    private fun chaves(arquivo: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(arquivo)
        val nodes = doc.getElementsByTagName("string")
        val out = LinkedHashMap<String, String>()
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            val nome = n.attributes.getNamedItem("name").nodeValue
            out[nome] = n.textContent
        }
        return out
    }

    private fun placeholders(valor: String): List<String> =
        Regex("%\\d+\\$[sd]").findAll(valor).map { it.value }.sorted().toList()

    @Test
    fun `toda chave em existe em values-pt e vice-versa`() {
        val en = chaves(res("src/main/res/values/strings.xml"))
        val pt = chaves(res("src/main/res/values-pt/strings.xml"))

        val semTraducao = en.keys - pt.keys
        val orfas = pt.keys - en.keys
        assertTrue("chaves SEM tradução pt: $semTraducao", semTraducao.isEmpty())
        assertTrue("chaves pt ÓRFÃS (sem inglês): $orfas", orfas.isEmpty())
    }

    @Test
    fun `nenhuma string vazia nos dois idiomas`() {
        val en = chaves(res("src/main/res/values/strings.xml"))
        val pt = chaves(res("src/main/res/values-pt/strings.xml"))
        val vaziasEn = en.filterValues { it.isBlank() }.keys
        val vaziasPt = pt.filterValues { it.isBlank() }.keys
        assertTrue("strings VAZIAS em en: $vaziasEn", vaziasEn.isEmpty())
        assertTrue("strings VAZIAS em pt: $vaziasPt", vaziasPt.isEmpty())
    }

    @Test
    fun `placeholders iguais nos dois idiomas`() {
        val en = chaves(res("src/main/res/values/strings.xml"))
        val pt = chaves(res("src/main/res/values-pt/strings.xml"))
        val problemas = ArrayList<String>()
        for ((chave, valorEn) in en) {
            val valorPt = pt[chave] ?: continue
            val phEn = placeholders(valorEn)
            val phPt = placeholders(valorPt)
            if (phEn != phPt) problemas.add("$chave: en=$phEn pt=$phPt")
        }
        assertTrue("placeholders divergentes: $problemas", problemas.isEmpty())
    }
}
