package com.kenkern.engine.render

import com.kenkern.engine.assets.mesh.KernReader
import com.kenkern.engine.assets.mesh.MeshData
import com.kenkern.engine.diag.KenkernLog
import java.io.File
import java.util.ArrayDeque
import java.util.HashMap

/**
 * BIBLIOTECA DE MALHAS importadas do projeto (ETAPA A.3): Nick com classe
 * "Malha" (campo path relativo, ex. "assets/models/carro.KERN") → handle de GPU.
 *
 * Regras do thread GL:
 *  - o CARREGAMENTO acontece fora do frame de desenho ([processarPendentes]
 *    é chamado 1×/frame com orçamento de tempo) — nunca trava a renderização
 *    (base do anti-trave; o streaming completo fica no F2.11);
 *  - descoberta LAZY: quando o render encontra um Nick-Malha ainda não na
 *    cache, enfileira o caminho (nunca lê arquivo no meio do flush);
 *  - FALHA = erro CLARO uma única vez no KenkernLog/Console (não reler o
 *    arquivo quebrado a cada frame; o Console mostra o motivo);
 *  - [baseDir] é a RAIZ DO PROJETO atual (definida pelo EditorViewModel ao
 *    abrir/sair — leitura volátil, thread GL e UI nunca colidem).
 *
 * Sem cache LRU de disco aqui (F2.10 é item próprio da Fase 2): a cache desta
 * biblioteca é de GPU por sessão do editor.
 */
object AssetMeshLibrary {

    private const val TAG = "AssetMeshLibrary"

    /** Orçamento por frame para carregar malhas pendentes (ns). */
    private const val ORCAMENTO_NS = 6_000_000L // 6 ms

    @Volatile
    var baseDir: File? = null

    private val trava = Any()
    private val handles = HashMap<String, Int>()          // path → handle GPU
    private val malhas = HashMap<String, com.kenkern.engine.assets.mesh.KernMesh>() // path → malha (materiais/grupos)
    private val pendentes = ArrayDeque<String>()          // descobertos, a carregar
    private val falhados = HashMap<String, String>()      // path → motivo (erro 1×)

    /** Limpa a cache (troca de projeto ou destruição do contexto GL). */
    fun limpar() {
        synchronized(trava) {
            handles.clear()
            malhas.clear()
            pendentes.clear()
            falhados.clear()
        }
    }

    /** Handle atual do caminho (0 = ainda não carregado / falhou). */
    fun handleDe(path: String): Int = synchronized(trava) { handles[path] ?: 0 }

    /** Malha .KERN carregada (grupos de material/materiais do v2), ou null. */
    fun malhaDe(path: String): com.kenkern.engine.assets.mesh.KernMesh? =
        synchronized(trava) { malhas[path] }

    /** Motivo da falha de carga (para a StatusBar/Diagnóstico), ou null. */
    fun motivoFalha(path: String): String? = synchronized(trava) { falhados[path] }

    /** Enfileira para carga se ainda não está na cache nem falhou. Thread GL. */
    fun marcarNecessario(path: String) {
        synchronized(trava) {
            if (handles.containsKey(path) || falhados.containsKey(path)) return
            if (pendentes.contains(path)) return
            pendentes.addLast(path)
        }
    }

    /** Cancela espera por um path que sumiu (undo da importação, troca de projeto). */
    fun esquecer(path: String) {
        synchronized(trava) {
            pendentes.remove(path)
            handles.remove(path)
            falhados.remove(path)
        }
    }

    /**
     * Carrega pendentes DENTRO do orçamento (thread GL, 1×/frame antes do draw).
     * Retorna quantas malhas subiram neste frame.
     */
    fun processarPendentes(backend: RenderBackend): Int {
        val raiz = baseDir ?: return 0
        var subidas = 0
        val inicio = System.nanoTime()
        while (true) {
            val path = synchronized(trava) {
                if (pendentes.isEmpty() || System.nanoTime() - inicio > ORCAMENTO_NS) null
                else pendentes.removeFirst()
            } ?: break
            try {
                val arquivo = File(raiz, path)
                if (!arquivo.exists()) {
                    falhar(path, "arquivo .KERN não existe: $path (importação desfeita?)")
                    continue
                }
                val mesh = KernReader.read(arquivo.readBytes())
                val handle = backend.createMesh(MeshData.fromKern(mesh))
                if (handle == 0) {
                    falhar(path, "GPU recusou a malha $path (${mesh.vertexCount} vértices — createMesh devolveu 0)")
                    continue
                }
                synchronized(trava) {
                    handles[path] = handle
                    // guarda a malha quando há materiais (o render usa grupos de material);
                    // malha sem material não precisa ficar em memória além do VBO
                    if (mesh.materiais.isNotEmpty() || mesh.gruposMaterial.isNotEmpty()) {
                        malhas[path] = mesh
                    }
                }
                subidas++
            } catch (e: Exception) {
                falhar(path, "falha ao carregar $path: ${e.message ?: e.javaClass.simpleName}")
            }
        }
        return subidas
    }

    /** Registra a falha UMA vez (regra #17: erro claro no Console, não silêncio). */
    private fun falhar(path: String, motivo: String) {
        val primeira = synchronized(trava) {
            val jaTinha = falhados.containsKey(path)
            falhados[path] = motivo
            !jaTinha
        }
        if (primeira) KenkernLog.e(TAG, motivo)
    }
}
