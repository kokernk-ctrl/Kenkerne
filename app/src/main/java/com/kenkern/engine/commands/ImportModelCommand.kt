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
    private var storedParent: Nick? = null
    private var storedIndex: Int = 0

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
            // REDO: devolve os arquivos da lixeira e reinsere a MESMA subárvore.
            restaurarDaLixeira()
            scene.attachSubtree(existing, storedParent, storedIndex)
            return
        }
        val parent = parentId?.let { scene.findNick(it) }
        val nick = scene.createNick(nickName, parent, novoId)
        nick.addClassInstance(classeMalha)
        created = nick
        storedParent = nick.parent
        storedIndex = nick.indexInParent().coerceAtLeast(0)
    }

    override fun undo() {
        val nick = created ?: return
        scene.removeNick(nick)
        recolherParaLixeira()
    }

    /** Move os arquivos criados para a lixeira (preserva caminho relativo). */
    private fun recolherParaLixeira() {
        if (!pastaLixeira.exists() && !pastaLixeira.mkdirs()) {
            val causa = "mkdirs devolveu false (papel do caminho? permissão? volume cheio?)"
            KenkernLog.e(TAG, "undo da importação: ${diagnosticoCaminhos(raizProjeto, pastaLixeira, causa)}", null)
            throw ImportException("undo não criou a lixeira de importação — $causa · ${diagnosticoCaminhos(raizProjeto, pastaLixeira, "")}")
        }
        for (rel in arquivosCriados) {
            val origem = File(raizProjeto, rel)
            if (!origem.exists()) {
                // Arquivo já foi embora (apagado à mão): registrar e seguir —
                // desfazer não pode falhar por causa de um arquivo que não existe.
                KenkernLog.w(TAG, "undo da importação: '$rel' já não existia")
                continue
            }
            moverArquivo(origem, File(pastaLixeira, rel), "undo")
            limparPastasVazias(origem.parentFile)
        }
    }

    /** Traz os arquivos de volta da lixeira (redo). */
    private fun restaurarDaLixeira() {
        for (rel in arquivosCriados) {
            val origem = File(pastaLixeira, rel)
            if (!origem.exists()) {
                KenkernLog.w(TAG, "redo da importação: '$rel' não estava na lixeira (arquivo mantido se existir)")
                continue
            }
            moverArquivo(origem, File(raizProjeto, rel), "redo")
        }
        pastaLixeira.deleteRecursively()
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
