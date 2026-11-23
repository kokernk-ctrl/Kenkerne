package com.kenkern.engine.projects

import com.kenkern.engine.diag.KenkernLog
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * M1.8 (v0.1.23) — EXPORTAR APK DO JOGO POR BOTÃO: dispara o GitHub Actions
 * (workflow_dispatch) COM O PROJETO e o APK do jogo sai como artifact ASSINADO
 * do próprio Actions. Montagem da requisição PURA (testável na JVM); o envio
 * é um POST simples via HttpURLConnection (sem dependência nova).
 *
 * CREDENCIAIS (regra da tarefa): o TOKEN do usuário (PAT com escopo `workflow`)
 * é digitado NO DIÁLOGO e fica SÓ em memória (nunca no repo, nunca em log —
 * só o código HTTP de resposta é registrado). Documentação completa
 * (workflow de exemplo e passos) em docs/EXPORTAR_JOGO.md.
 */
object ExportadorJogo {

    /** Configuração pedida no diálogo (token SÓ em memória). */
    class Config(
        val repositorio: String,   // "dono/repo"
        val workflow: String,      // ex.: "exportar-jogo.yml"
        val ref: String,           // branch (ex.: "main")
        val projetoNome: String,   // nome do projeto a embutir no build
        val token: String          // PAT com escopo workflow — nunca persistido
    )

    /** URL do endpoint de dispatch (pura, testável). */
    fun montarUrl(repositorio: String, workflow: String): String {
        val repo = repositorio.trim().removePrefix("https://github.com/").trimEnd('/')
        require(repo.contains('/')) { "repositório deve ser \"dono/repo\" (recebido '$repositorio')" }
        require(repo.split('/').all { it.isNotBlank() }) { "repositório malformado: '$repositorio'" }
        val wf = workflow.trim().ifBlank { "exportar-jogo.yml" }
        return "https://api.github.com/repos/$repo/actions/workflows/$wf/dispatches"
    }

    /** Corpo JSON do dispatch: o NOME DO PROJETO vai para os inputs (puro). */
    fun montarCorpo(projetoNome: String, ref: String): String {
        val refSeguro = ref.trim().ifBlank { "main" }
        val nome = projetoNome.replace("\\", "\\\\").replace("\"", "\\\"")
        return "{\"ref\":\"$refSeguro\",\"inputs\":{\"projeto\":\"$nome\"}}"
    }

    /** Validação do token ANTES de qualquer rede (mensagem clara, sem ecoar). */
    fun validarToken(token: String): String? =
        when {
            token.isBlank() -> "token ausente — um PAT com escopo 'workflow' é necessário"
            token.length < 20 -> "token curto demais — cole o PAT completo"
            else -> null
        }

    /**
     * Dispara o workflow. Devolve null em SUCESSO (HTTP 204 do GitHub) ou a
     * mensagem de erro CLARA. O token NUNCA entra em log — só o status HTTP.
     */
    fun disparar(config: Config): String? {
        validarToken(config.token)?.let { return it }
        val url = try {
            montarUrl(config.repositorio, config.workflow)
        } catch (e: IllegalArgumentException) {
            return e.message
        }
        val corpo = montarCorpo(config.projetoNome, config.ref)
        var conexao: HttpURLConnection? = null
        return try {
            conexao = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Authorization", "Bearer ${config.token}")
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            conexao.outputStream.use { it.write(corpo.toByteArray(Charsets.UTF_8)) }
            val status = conexao.responseCode
            if (status == 204) {
                // SUCESSO: o GitHub devolve 204 para dispatch aceito.
                KenkernLog.w(TAG, "exportação do jogo: workflow_dispatch aceito (HTTP $status) — projeto '${config.projetoNome}'")
                null
            } else {
                val motivo = "GitHub respondeu HTTP $status — confira o repositório/workflow e se o token tem escopo 'workflow'"
                KenkernLog.e(TAG, "exportação do jogo falhou: $motivo")
                motivo
            }
        } catch (e: IOException) {
            val motivo = "falha de rede ao disparar o workflow: ${e.javaClass.simpleName}: ${e.message ?: ""}"
            KenkernLog.e(TAG, motivo)
            motivo
        } finally {
            conexao?.disconnect()
        }
    }

    private const val TAG = "Kenkern/ExportJogo"
}
