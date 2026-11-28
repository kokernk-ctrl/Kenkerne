package com.kenkern.engine.diag

/**
 * S1 (v0.1.24) — TEXTO COMPLETO do relatório do Diagnóstico (PURO — a JVM
 * prova o formato). O botão "Copiar relatório" da tela copia este texto:
 *  - cabeçalho com o resumo (aprovadas · falhas · total);
 *  - FALHAS PRIMEIRO (o mesmo critério da lista na tela), depois o resto
 *    na ordem do catálogo;
 *  - cada checagem com título localizado, palavra de estado, tempo e o
 *    DETALHE completo (o texto inteiro da checagem, sem cortar).
 */
object DiagRelatorioTexto {

    /** Palavra do estado no relatório (texto técnico, igual em qualquer idioma). */
    private fun palavra(status: Diagnostico.Status): String = when (status) {
        Diagnostico.Status.PENDENTE -> "PENDENTE"
        Diagnostico.Status.RODANDO -> "RODANDO"
        Diagnostico.Status.APROVADO -> "APROVADO"
        Diagnostico.Status.FALHOU -> "FALHOU"
        Diagnostico.Status.CANCELADA -> "CANCELADA"
    }

    /**
     * Ordem de exibição: FALHOU no TOPO (S1c); o resto na ordem do
     * catálogo (sort estável — não reordera o que já está em ordem).
     */
    fun ordenar(resultados: List<Diagnostico.Checagem>): List<Diagnostico.Checagem> =
        resultados.sortedBy { it.status != Diagnostico.Status.FALHOU }

    /**
     * Monta o relatório completo. [tituloPorId] devolve o título localizado
     * do id (a tela resolve com o recurso do idioma corrente).
     * [identidade] é a linha F8 (v0.1.31): versão, versionCode, hash curto do
     * commit e data da build — vai no cabeçalho do texto copiado, para o
     * usuário dizer QUAL build gerou o relatório.
     */
    fun montar(
        resultados: List<Diagnostico.Checagem>,
        identidade: String = "",
        tituloPorId: (String) -> String
    ): String {
        val aprovadas = resultados.count { it.status == Diagnostico.Status.APROVADO }
        val falhas = resultados.count { it.status == Diagnostico.Status.FALHOU }
        val sb = StringBuilder()
        if (identidade.isNotBlank()) {
            sb.append(identidade).append('\n')
        }
        sb.append("Diagnóstico Kenkern — ").append(aprovadas).append(" aprovadas · ")
            .append(falhas).append(" falhas (de ").append(resultados.size).append(")\n")
        for (r in ordenar(resultados)) {
            sb.append('\n')
            sb.append(tituloPorId(r.id)).append(" — ").append(palavra(r.status))
            if (r.tempoMs >= 0) {
                sb.append(" (")
                if (r.tempoMs >= 1_000L) sb.append(String.format("%.1f s", r.tempoMs / 1000f))
                else sb.append(r.tempoMs).append(" ms")
                sb.append(')')
            }
            sb.append('\n')
            sb.append(r.detalhe).append('\n')
        }
        return sb.toString()
    }
}
