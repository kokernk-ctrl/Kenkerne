package com.kenkern.engine.commands

import com.kenkern.engine.projects.OperacoesArquivos
import java.io.File

/**
 * F3 (v0.1.31) — Commands de ARQUIVO do explorador: toda operação do painel
 * Projeto é um Command com desfazer (mesma CommandHistory do editor — o undo
 * da TopBar desfaz tanto cena quanto arquivo). Todos usam o núcleo puro
 * `OperacoesArquivos` (guardas de caminho + lixeira .kenkern/lixeira).
 *
 * F5 (v0.1.31): mover/renomear ATUALIZAM as referências dos assets que
 * apontam o caminho antigo — via [atualizarReferencias] (o chamador injeta o
 * ponte para a cena/UI; o Command continua puro de filesystem).
 */
abstract class ArquivoCommand(protected val raiz: File) : Command {

    /** Caminho relativo resultante após execute (para o painel navegar). */
    var resultadoRelativo: String? = null
        protected set
}

/** Nova pasta (undo = apagar a pasta criada). */
class ComandoCriarPasta(raiz: File, val pastaPaiRelativa: String, val nome: String) :
    ArquivoCommand(raiz) {

    override val label = "nova pasta '$nome'"

    override fun execute() {
        val r = OperacoesArquivos.criarPasta(raiz, File(raiz, pastaPaiRelativa), nome)
        exigir(r)
        resultadoRelativo = r.caminho
    }

    override fun undo() {
        val rel = resultadoRelativo ?: return
        val criada = File(raiz, rel)
        val r = OperacoesArquivos.apagarParaLixeira(raiz, criada, novoIdOperacao())
        exigir(r)
    }
}

/** Novo arquivo com conteúdo inicial (undo = lixeira). */
class ComandoCriarArquivo(
    raiz: File,
    val pastaPaiRelativa: String,
    val nome: String,
    val conteudo: String
) : ArquivoCommand(raiz) {

    override val label = "novo arquivo '$nome'"

    override fun execute() {
        val r = OperacoesArquivos.criarArquivo(raiz, File(raiz, pastaPaiRelativa), nome, conteudo)
        exigir(r)
        resultadoRelativo = r.caminho
    }

    override fun undo() {
        val rel = resultadoRelativo ?: return
        val r = OperacoesArquivos.apagarParaLixeira(raiz, File(raiz, rel), novoIdOperacao())
        exigir(r)
    }
}

/** Renomear (undo = renomear de volta). F5: atualiza referências. */
class ComandoRenomearArquivo(
    raiz: File,
    val caminhoRelativo: String,
    val novoNome: String,
    private val atualizarReferencias: ((antigo: String, novo: String) -> Int)? = null
) : ArquivoCommand(raiz) {

    override val label = "renomear '$caminhoRelativo' → '$novoNome'"
    private var caminhoAnterior: String = caminhoRelativo

    override fun execute() {
        val alvo = File(raiz, caminhoRelativo)
        val r = OperacoesArquivos.renomear(raiz, alvo, novoNome)
        exigir(r)
        caminhoAnterior = caminhoRelativo
        resultadoRelativo = r.caminho
        atualizarReferencias?.invoke(caminhoRelativo, r.caminho ?: return)
    }

    override fun undo() {
        val novo = resultadoRelativo ?: return
        val alvo = File(raiz, novo)
        val nomeAntigo = caminhoAnterior.substringAfterLast('/')
        val r = OperacoesArquivos.renomear(raiz, alvo, nomeAntigo)
        exigir(r)
        atualizarReferencias?.invoke(novo, caminhoAnterior)
        resultadoRelativo = caminhoAnterior
    }
}

/** Duplicar (undo = lixeira da cópia). */
class ComandoDuplicarArquivo(raiz: File, val caminhoRelativo: String) : ArquivoCommand(raiz) {

    override val label = "duplicar '$caminhoRelativo'"

    override fun execute() {
        val r = OperacoesArquivos.duplicar(raiz, File(raiz, caminhoRelativo))
        exigir(r)
        resultadoRelativo = r.caminho
    }

    override fun undo() {
        val rel = resultadoRelativo ?: return
        val r = OperacoesArquivos.apagarParaLixeira(raiz, File(raiz, rel), novoIdOperacao())
        exigir(r)
    }
}

/** Mover (undo = mover de volta). F5: atualiza referências. */
class ComandoMoverArquivo(
    raiz: File,
    val caminhoRelativo: String,
    val pastaDestinoRelativa: String,
    private val atualizarReferencias: ((antigo: String, novo: String) -> Int)? = null
) : ArquivoCommand(raiz) {

    override val label = "mover '$caminhoRelativo' → '$pastaDestinoRelativa'"

    override fun execute() {
        val r = OperacoesArquivos.mover(raiz, File(raiz, caminhoRelativo), File(raiz, pastaDestinoRelativa))
        exigir(r)
        resultadoRelativo = r.caminho
        atualizarReferencias?.invoke(caminhoRelativo, r.caminho ?: return)
    }

    override fun undo() {
        val novo = resultadoRelativo ?: return
        val r = OperacoesArquivos.mover(raiz, File(raiz, novo), File(raiz, caminhoRelativo.substringBeforeLast('/', "")))
        exigir(r)
        atualizarReferencias?.invoke(novo, caminhoRelativo)
        resultadoRelativo = caminhoRelativo
    }
}

/** Apagar → lixeira (undo = restaurar). F5: as referências quebram e aparecem no painel Problemas. */
class ComandoApagarArquivo(
    raiz: File,
    val caminhoRelativo: String,
    val idOperacao: String
) : ArquivoCommand(raiz) {

    override val label = "apagar '$caminhoRelativo'"
    private var caminhoNaLixeira: String? = null

    override fun execute() {
        val r = OperacoesArquivos.apagarParaLixeira(raiz, File(raiz, caminhoRelativo), idOperacao)
        exigir(r)
        caminhoNaLixeira = r.caminho
        resultadoRelativo = r.caminho
    }

    override fun undo() {
        val naLixeira = caminhoNaLixeira ?: return
        val destinoOriginal = File(raiz, caminhoRelativo)
        val r = OperacoesArquivos.restaurarDaLixeira(raiz, naLixeira, destinoOriginal)
        exigir(r)
        resultadoRelativo = caminhoRelativo
    }

    /** Onde o conteúdo está na lixeira (painel Problemas/undo manual). */
    fun lixeiraRelativa(): String? = caminhoNaLixeira
}

/** "Esvaziar lixeira" — sem desfazer (operação definitiva, com confirmação na UI). */
class ComandoEsvaziarLixeira(raiz: File) : ArquivoCommand(raiz) {

    override val label = "esvaziar lixeira"

    override fun execute() {
        val r = OperacoesArquivos.esvaziarLixeira(raiz)
        exigir(r)
    }

    override fun undo() {
        // esvaziar é DEFINITIVO: nada a restaurar (a UI confirma antes).
    }
}

/** Operação falhou → erro CLARO com caminho (nunca silencioso). */
private fun exigir(r: OperacoesArquivos.Resultado) {
    check(r.ok) { "operação de arquivo falhou: ${r.erro ?: "(sem detalhe)"}" }
}

/** id estável para pastas na lixeira (timestamp+contador — sem UUID para leitura fácil). */
fun novoIdOperacao(): String =
    System.currentTimeMillis().toString(36) + "-" + (contador++).toString(36)

private var contador = 0
