package com.kenkern.engine.commands

import com.kenkern.engine.assets.import.ImportException
import com.kenkern.engine.core.Nick
import com.kenkern.engine.core.NickClassInstance
import com.kenkern.engine.core.ReferenceId
import com.kenkern.engine.core.Scene
import com.kenkern.engine.diag.KenkernLog
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * UM Command para a importação inteira (ETAPA A.3): criar o Nick com a classe
 * "Malha" + os arquivos de asset gravados no projeto (.KERN + origem + texturas).
 *
 *  - execute()   (1ª vez): insere o Nick — os arquivos JÁ foram gravados pelo
 *                fluxo de importação ANTES do comando ser registrado;
 *  - undo()      : remove o Nick E recolhe os arquivos para a lixeira de
 *                importação (assets/.lixeira-import/<id>/…); nada é apagado de
 *                primeira — o redo é barato e exato;
 *  - redo (execute de novo): devolve os arquivos da lixeira E reinsere o Nick.
 *
 * LIXEIRA (D3, v0.1.16): fica DENTRO da raiz do projeto
 * (assets/.lixeira-import/<importId>/) — MESMO VOLUME por construção, sem
 * cruzar cache e files. Os movimentos usam [Files.move] com fallback EXPLICITO
 * copiar+apagar (verificado por tamanho); qualquer falha lança
 * [ImportException] com os CAMINHOS REAIS (origem, destino, existe,
 * canRead/canWrite, mesmo volume) e registra no [KenkernLog] — erro nunca é
 * engolido (regra #17).
 */
class ImportModelCommand(
    private val scene: Scene,
    private val parentId: ReferenceId?,
    private val nickName: String,
    private val raizProjeto: File,
    /** Caminhos RELATIVOS criados pela importação (ex.: "assets/models/carro.KERN"). */
    private val arquivosCriados: List<String>,
    /** Instância da classe Malha já pronta (campos path + AABB). */
    private val classeMalha: NickClassInstance,
    private val novoId: ReferenceId = ReferenceId.generate()
) : Command {

    override val label: String get() = "Importar $nickName"

    private var created: Nick? = null
    private var detached: Scene.DetachedSubtree? = null

    private val importId: String = "imp-" + System.nanoTime().toString(36)

    /**
     * Pasta da lixeira DESTA importação — pública para a checagem "importar"
     * do Diagnóstico e para os testes JVM conferirem o MESMO caminho real
     * (antes a checagem adivinhava o caminho sem o <importId> e falhava — D3).
     */
    val pastaLixeira: File = File(raizProjeto, LIXEIRA + importId)

    override fun execute() {
        val existing = created
        if (existing != null) {
            // REDO: primeiro restaura os arquivos; só então publica a subárvore.
            val state = detached ?: return
            val movidos = restaurarDaLixeira()
            try {
                scene.attachSubtree(state)
            } catch (e: Exception) {
                transferirArquivos(raizProjeto, pastaLixeira, "rollback redo", movidos)
                throw e
            }
            detached = null
            pastaLixeira.deleteRecursively()
            return
        }
        val parent = parentId?.let { scene.findNick(it) }
        // T0.2: modelo importado nasce como Mesh Nick (kind) com o nome do
        // arquivo como texto do USUÁRIO (identifica o modelo na Hierarquia).
        val nick = scene.createNick(
            nickName, parent, novoId,
            kind = com.kenkern.engine.core.NickKindRegistry.NICK_MESH
        )
        nick.addClassInstance(classeMalha)
        created = nick
    }

    override fun undo() {
        val nick = created ?: return
        if (scene.findNick(nick.id) !== nick) return

        // Arquivos são movidos como uma transação antes de retirar o Nick. Se
        // qualquer movimento falhar, a cena continua publicada e o undo pode
        // ser tentado de novo.
        val movidos = recolherParaLixeira()
        val state = scene.detachSubtree(nick)
        if (state == null) {
            transferirArquivos(pastaLixeira, raizProjeto, "rollback undo", movidos)
            throw ImportException("undo da importação não destacou o Nick '${nick.id.value}'")
        }
        detached = state
    }

    /** Move os arquivos criados para a lixeira; falha reverte os movimentos feitos. */
    private fun recolherParaLixeira(): List<String> {
        val movidos = transferirArquivos(raizProjeto, pastaLixeira, "undo")
        for (rel in movidos) {
            val origem = File(raizProjeto, rel)
            limparPastasVazias(origem.parentFile)
        }
        return movidos
    }

    /** Traz os arquivos da lixeira de volta; falha reverte os movimentos feitos. */
    private fun restaurarDaLixeira(): List<String> =
        transferirArquivos(pastaLixeira, raizProjeto, "redo")

    /**
     * Transfere todos os arquivos existentes. Se qualquer arquivo falhar,
     * desfaz em ordem inversa os movimentos já concluídos antes de propagar o
     * erro; arquivos ausentes continuam sendo avisados e ignorados.
     */
    private fun transferirArquivos(
        origemBase: File,
        destinoBase: File,
        fase: String,
        relativos: List<String> = arquivosCriados
    ): List<String> {
        if (!destinoBase.exists() && !destinoBase.mkdirs()) {
            val causa = "mkdirs do destino devolveu false"
            KenkernLog.e(TAG, "$fase da importação: ${diagnosticoCaminhos(origemBase, destinoBase, causa)}", null)
            throw ImportException("$fase não criou o destino — ${diagnosticoCaminhos(origemBase, destinoBase, causa)}")
        }
        val movidos = ArrayList<String>()
        try {
            for (rel in relativos) {
                val origem = File(origemBase, rel)
                if (!origem.exists()) {
                    KenkernLog.w(TAG, "$fase da importação: '$rel' não existia em ${origemBase.path}")
                    continue
                }
                moverArquivo(origem, File(destinoBase, rel), fase)
                movidos.add(rel)
            }
        } catch (falha: Exception) {
            val falhasRollback = ArrayList<String>()
            for (rel in movidos.asReversed()) {
                try {
                    moverArquivo(
                        File(destinoBase, rel),
                        File(origemBase, rel),
                        "rollback $fase"
                    )
                } catch (rollback: Exception) {
                    falha.addSuppressed(rollback)
                    falhasRollback.add("$rel: ${rollback.message ?: rollback.javaClass.simpleName}")
                }
            }
            if (falhasRollback.isNotEmpty()) {
                KenkernLog.e(
                    TAG,
                    "$fase da importação falhou e o rollback ficou parcial: ${falhasRollback.joinToString()}",
                    falha
                )
                throw ImportException(
                    "${falha.message ?: "falha na transferência"}; rollback parcial: ${falhasRollback.joinToString()}"
                )
            }
            throw falha
        }
        return movidos
    }


    /**
     * Move origem → destino: primeiro [Files.move] (REPLACE_EXISTING — nomes
     * já existentes são substituídos e isso é registrado); se o move falhar
     * (volume diferente, permissão, bloqueio — IOException), fallback
     * COPIAR+APAGAR com verificação de tamanho. Falha final = [ImportException]
     * com os caminhos reais. Nada silencioso.
     */
    private fun moverArquivo(origem: File, destino: File, fase: String) {
        destino.parentFile?.let { pai ->
            if (!pai.exists() && !pai.mkdirs()) {
                val causa = "mkdirs do pai do destino devolveu false"
                KenkernLog.e(TAG, "$fase da importação: ${diagnosticoCaminhos(origem, destino, causa)}", null)
                throw ImportException("$fase não criou a pasta do destino — ${diagnosticoCaminhos(origem, destino, causa)}")
            }
        }
        if (destino.exists()) {
            KenkernLog.w(TAG, "$fase da importação: destino já existia e será substituído: ${destino.path}")
        }
        try {
            val falhaSimulada = testeFalhaNoMove // teste JVM simula volume diferente
            if (falhaSimulada != null) throw falhaSimulada(origem, destino)
            Files.move(origem.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING)
            return
        } catch (e: IOException) {
            KenkernLog.w(
                TAG,
                "$fase da importação: Files.move falhou (${e.javaClass.simpleName}: ${e.message ?: "sem mensagem"}) — " +
                    "fallback copiar+apagar: ${origem.path} → ${destino.path}"
            )
        }
        try {
            Files.copy(origem.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING)
            if (Files.size(origem.toPath()) != Files.size(destino.toPath())) {
                throw IOException("tamanho divergiu após copiar (${Files.size(origem.toPath())} vs ${Files.size(destino.toPath())})")
            }
            Files.delete(origem.toPath())
        } catch (e: Exception) {
            val causa = "fallback copiar+apagar falhou: ${e.javaClass.simpleName}: ${e.message ?: "sem mensagem"}"
            KenkernLog.e(TAG, "$fase da importação: ${diagnosticoCaminhos(origem, destino, causa)}", e)
            throw ImportException("$fase não recolheu o arquivo — ${diagnosticoCaminhos(origem, destino, causa)}")
        }
    }

    /**
     * Diagnóstico de caminhos para a mensagem de erro (D3.1): origem e destino
     * com existe/canRead/canWrite e se estão no MESMO volume (FileStore).
     * FileStore pode falhar em alguns sistemas — nesse caso o campo vira
     * "desconhecido" e o motivo vai NA MENSAGEM (nunca silêncio).
     */
    private fun diagnosticoCaminhos(origem: File, destino: File, causa: String): String {
        val mesmoVolume = try {
            (Files.getFileStore(origem.toPath()) == Files.getFileStore(destino.toPath())).toString()
        } catch (e: Exception) {
            "desconhecido (${e.javaClass.simpleName}: ${e.message ?: "sem mensagem"})"
        }
        val prefixo = if (causa.isBlank()) "" else "causa: $causa · "
        return "${prefixo}origem ${origem.path} (existe=${origem.exists()} canRead=${origem.canRead()} canWrite=${origem.canWrite()}) · " +
            "destino ${destino.path} (existe=${destino.exists()} canWrite=${destino.canWrite()}) · mesmoVolume=$mesmoVolume"
    }

    /** Apaga pastas que sobraram vazias (não toca nas raízes padrão de assets). */
    private fun limparPastasVazias(pasta: File?) {
        var p = pasta ?: return
        val raizes = setOf(
            File(raizProjeto, "assets/models").absolutePath,
            File(raizProjeto, "assets/models/origem").absolutePath,
            File(raizProjeto, "assets/textures").absolutePath
        )
        while (p != null && p.exists() && p.list()?.isEmpty() == true && p.absolutePath !in raizes) {
            p.delete()
            p = p.parentFile
        }
    }

    companion object {
        private const val TAG = "ImportModelCommand"
        private const val LIXEIRA = "assets/.lixeira-import/"

        /**
         * SEAM DE TESTE (só os testes JVM setam): quando não-nulo, o
         * [Files.move] de cada arquivo lança a IOException devolvida por esta
         * função — simula exatamente a falha de mover ENTRE VOLUMES (ExDev) no
         * mesmo ponto do código real; o fallback copiar+apagar roda POR DENTRO
         * nos arquivos de verdade. Nunca setado em produção.
         */
        internal var testeFalhaNoMove: ((origem: File, destino: File) -> IOException)? = null
    }
}
