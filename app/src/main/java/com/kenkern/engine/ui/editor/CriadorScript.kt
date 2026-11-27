package com.kenkern.engine.ui.editor

import java.io.File

/**
 * R3 (v0.1.29) — CRIAÇÃO DE SCRIPT: PURO (sem Android) para a JVM provar o
 * contrato. O modelo grava o CONTEÚDO no arquivo ANTES de abrir (o sintoma
 * "abre vazio" da v0.1.28 vinha da RECOMPOSIÇÃO que nunca acontecia após a
 * carga assíncrona — o arquivo sempre teve conteúdo; ver commit R3).
 *
 * Nome: PADRÃO "script.lua"; sufixo "_2" SÓ em colisão (nunca sufixo
 * aleatório como o "script_movimento_34934.lua" da v0.1.28):
 *   script.lua → script_2.lua → script_3.lua …
 */
object CriadorScript {

    /** Padrão pedido pela tarefa R3. */
    const val NOME_PADRAO = "script.lua"

    /**
     * Nome único a partir do desejado: sem colisão devolve o PRÓPRIO nome;
     * com colisão acrescenta _2, _3… antes da extensão.
     */
    fun nomeUnico(desejado: String, existentes: Set<String>): String {
        if (desejado !in existentes) return desejado
        val ponto = desejado.lastIndexOf('.')
        val base = if (ponto > 0) desejado.substring(0, ponto) else desejado
        val ext = if (ponto > 0) desejado.substring(ponto) else ""
        var i = 2
        var nome = "${base}_$i$ext"
        while (nome in existentes) {
            i++
            nome = "${base}_$i$ext"
        }
        return nome
    }

    /**
     * Cria scripts/<nome> com o conteúdo do MODELO (grava ANTES de abrir).
     * Devolve o nome EFETIVO (pode ter sufixo _N) ou null se o nome veio
     * vazio. Lança IOException/ exceção de disco para o CHAMADOR logar —
     * nada de engolir aqui.
     */
    fun criar(dirScripts: File, nomeDesejado: String, modelo: String): String? {
        val nomeLimpo = nomeDesejado.trim()
        if (nomeLimpo.isBlank() || nomeLimpo.contains('/') || nomeLimpo.contains('\\')) return null
        val existentes = dirScripts.listFiles()?.mapTo(HashSet()) { it.name } ?: emptySet<String>()
        val nome = nomeUnico(nomeLimpo, existentes)
        val f = File(dirScripts, nome)
        f.parentFile?.mkdirs()
        f.writeText(ModelosScript.conteudo(modelo))
        return nome
    }
}
