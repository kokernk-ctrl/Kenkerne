package com.kenkern.engine.commands

import com.kenkern.engine.assets.import.ImportException
import com.kenkern.engine.core.Nick
import com.kenkern.engine.core.NickClassInstance
import com.kenkern.engine.core.ReferenceId
import com.kenkern.engine.core.Scene
import com.kenkern.engine.diag.KenkernLog
import java.io.File

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
 * Falha de arquivo NÃO engole erro: lança [ImportException] — o ViewModel
 * registra no KenkernLog e mostra na tela (regra #17).
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

    private fun pastaLixeira(): File = File(raizProjeto, LIXEIRA + importId)

    private val importId: String = "imp-" + System.nanoTime().toString(36)

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
        val lixeira = pastaLixeira()
        if (!lixeira.exists() && !lixeira.mkdirs()) {
            throw ImportException("não criou a lixeira de importação ($lixeira.name)")
        }
        for (rel in arquivosCriados) {
            val origem = File(raizProjeto, rel)
            if (!origem.exists()) {
                // Arquivo já foi embora (apagado à mão): registrar e seguir —
                // desfazer não pode falhar por causa de um arquivo que não existe.
                KenkernLog.w(TAG, "undo da importação: '$rel' já não existia")
                continue
            }
            val destino = File(lixeira, rel)
            destino.parentFile?.mkdirs()
            if (!origem.renameTo(destino)) {
                // rename pode falhar entre volumes: copia+apaga como fallback EXPLICITO.
                origem.copyTo(destino, overwrite = true)
                if (!origem.delete()) {
                    KenkernLog.w(TAG, "undo da importação: não apagou '$rel' após copiar para a lixeira")
                }
            }
            limparPastasVazias(origem.parentFile)
        }
    }

    /** Traz os arquivos de volta da lixeira (redo). */
    private fun restaurarDaLixeira() {
        val lixeira = pastaLixeira()
        for (rel in arquivosCriados) {
            val origem = File(lixeira, rel)
            if (!origem.exists()) {
                KenkernLog.w(TAG, "redo da importação: '$rel' não estava na lixeira (arquivo mantido se existir)")
                continue
            }
            val destino = File(raizProjeto, rel)
            destino.parentFile?.mkdirs()
            if (!origem.renameTo(destino)) {
                origem.copyTo(destino, overwrite = true)
                if (!origem.delete()) {
                    KenkernLog.w(TAG, "redo da importação: não apagou a cópia da lixeira de '$rel'")
                }
            }
        }
        lixeira.deleteRecursively()
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
    }
}
