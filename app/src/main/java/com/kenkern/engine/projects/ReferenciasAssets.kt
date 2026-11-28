package com.kenkern.engine.projects

import com.kenkern.engine.core.Nick
import com.kenkern.engine.core.Scene
import java.io.File

/**
 * F5 (v0.1.31) — REFERÊNCIAS ENTRE NICKS E ASSETS + PROBLEMAS (puro).
 *
 * Hoje os Nicks referenciam assets pelo CAMINHO RELATIVO (classe "Malha",
 * campo "path" — MalhaNick.path). O índice assets.index dá o ID ESTÁVEL; o
 * mover/renomear (ComandoMoverArquivo/ComandoRenomearArquivo) atualiza
 * caminho no índice E nos Nicks; o que ficar quebrado aparece aqui.
 */
object ReferenciasAssets {

    /** Uma referência de um Nick a um asset (por enquanto: Malha.path). */
    data class Referencia(val nickId: String, val nickNome: String, val campo: String, val caminho: String)

    /** Uma referência quebrada: o caminho apontado NÃO existe no disco. */
    data class Problema(
        val nickId: String,
        val nickNome: String,
        val campo: String,
        val caminho: String,
        val mensagem: String
    )

    /** varre TODOS os Nicks da cena e devolve as referências a assets. */
    fun varrer(scene: Scene): List<Referencia> {
        val saida = ArrayList<Referencia>(8)
        val pilha = ArrayDeque<Nick>()
        for (r in scene.roots) pilha.addLast(r)
        while (!pilha.isEmpty()) {
            val n = pilha.removeLast()
            val inst = n.findClassInstance("Malha")
            if (inst != null) {
                val path = inst.getString("path", "")
                if (path.isNotBlank()) {
                    saida.add(Referencia(n.id.value, n.displayName("pt"), "Malha.path", path))
                }
            }
            for (c in n.children) pilha.addLast(c)
        }
        return saida
    }

    /** referências quebradas = caminho não existe no projeto. */
    fun quebradas(raiz: File, refs: List<Referencia>): List<Problema> {
        val saida = ArrayList<Problema>(2)
        for (r in refs) {
            val f = File(raiz, r.caminho)
            if (!f.isFile) {
                saida.add(
                    Problema(
                        r.nickId, r.nickNome, r.campo, r.caminho,
                        "'${r.nickNome}' aponta ${r.campo}='${r.caminho}' e o arquivo não existe"
                    )
                )
            }
        }
        return saida
    }

    /** todas as quebradas da cena (atalho para o painel Problemas). */
    fun problemas(raiz: File, scene: Scene): List<Problema> = quebradas(raiz, varrer(scene))

    /**
     * atualizar caminhos após mover/renomear: TODA referência ao caminho
     * antigo (exato) passa a apontar o novo. Devolve QUANTAS mudaram — o
     * Command usa esse retorno para logar (nada silencioso).
     */
    fun repontar(scene: Scene, caminhoAntigo: String, caminhoNovo: String): Int {
        var mudou = 0
        val pilha = ArrayDeque<Nick>()
        for (r in scene.roots) pilha.addLast(r)
        while (!pilha.isEmpty()) {
            val n = pilha.removeLast()
            val inst = n.findClassInstance("Malha")
            if (inst != null && inst.getString("path", "") == caminhoAntigo) {
                inst.fields["path"] = com.kenkern.engine.core.NickValue.StringValue(caminhoNovo)
                mudou++
            }
            for (c in n.children) pilha.addLast(c)
        }
        return mudou
    }

    /** "Corrigir…" — reponta UMA referência quebrada para o arquivo escolhido. */
    fun corrigir(scene: Scene, nickId: String, novoCaminho: String): Boolean {
        val alvo = buscarNick(scene, nickId) ?: return false
        val inst = alvo.findClassInstance("Malha") ?: return false
        inst.fields["path"] = com.kenkern.engine.core.NickValue.StringValue(novoCaminho)
        return true
    }

    /** "Remover referência" — limpa o campo (o Nick fica sem malha, sem crash). */
    fun removerReferencia(scene: Scene, nickId: String): Boolean {
        val alvo = buscarNick(scene, nickId) ?: return false
        val inst = alvo.findClassInstance("Malha") ?: return false
        inst.fields["path"] = com.kenkern.engine.core.NickValue.StringValue("")
        return true
    }

    private fun buscarNick(scene: Scene, id: String): Nick? {
        val pilha = ArrayDeque<Nick>()
        for (r in scene.roots) pilha.addLast(r)
        while (!pilha.isEmpty()) {
            val n = pilha.removeLast()
            if (n.id.value == id) return n
            for (c in n.children) pilha.addLast(c)
        }
        return null
    }
}
