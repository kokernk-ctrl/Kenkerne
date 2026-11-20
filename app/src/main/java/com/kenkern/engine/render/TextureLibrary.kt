package com.kenkern.engine.render

import com.kenkern.engine.assets.image.KimgException
import com.kenkern.engine.assets.image.KimgReader
import com.kenkern.engine.diag.KenkernLog
import java.io.File
import java.util.ArrayDeque
import java.util.HashMap

/**
 * BIBLIOTECA DE TEXTURAS do projeto (ETAPA C.4/C.5): caminho relativo
 * ("assets/textures/<hash>.kimg") → handle de GPU. Descoberta LAZY pelo render
 * (nunca lê arquivo no meio do flush), carga FORA do frame com orçamento
 * (mesma política do [AssetMeshLibrary]), falha = erro CLARO UMA vez por caminho.
 */
object TextureLibrary {

    private const val TAG = "TextureLibrary"

    /** Orçamento por frame para subir texturas pendentes (ns). */
    private const val ORCAMENTO_NS = 6_000_000L

    @Volatile
    var baseDir: File? = null

    private val trava = Any()
    private val handles = HashMap<String, Int>()
    private val pendentes = ArrayDeque<String>()
    private val falhados = HashMap<String, String>()

    fun limpar() {
        synchronized(trava) {
            handles.clear()
            pendentes.clear()
            falhados.clear()
        }
    }

    /** Handle da textura (0 = ainda não carregada / falhou — cor sólida no lugar). */
    fun handleDe(path: String): Int = synchronized(trava) { handles[path] ?: 0 }

    /** Enfileira a carga (thread GL). */
    fun marcarNecessario(path: String) {
        synchronized(trava) {
            if (handles.containsKey(path) || falhados.containsKey(path)) return
            if (pendentes.contains(path)) return
            pendentes.addLast(path)
        }
    }

    fun esquecer(path: String) {
        synchronized(trava) {
            pendentes.remove(path)
            handles.remove(path)
            falhados.remove(path)
        }
    }

    /** Sobe pendentes dentro do orçamento (thread GL, 1×/frame). */
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
                    // Textura ainda não convertida (importação antiga) = AVISO 1×, cor sólida.
                    falhar(path, "textura .KIMG não existe: $path (importada antes da conversão de texturas?)")
                    continue
                }
                val handle = backend.createTextureFromKimg(arquivo.readBytes())
                if (handle == 0) {
                    falhar(path, "GPU recusou a textura $path (createTextureFromKimg devolveu 0)")
                    continue
                }
                synchronized(trava) { handles[path] = handle }
                subidas++
            } catch (e: KimgException) {
                falhar(path, ".KIMG inválido $path: ${e.message}")
            } catch (e: Exception) {
                falhar(path, "falha ao carregar textura $path: ${e.message ?: e.javaClass.simpleName}")
            }
        }
        return subidas
    }

    private fun falhar(path: String, motivo: String) {
        val primeira = synchronized(trava) {
            val jaTinha = falhados.containsKey(path)
            falhados[path] = motivo
            !jaTinha
        }
        if (primeira) KenkernLog.e(TAG, motivo)
    }
}
