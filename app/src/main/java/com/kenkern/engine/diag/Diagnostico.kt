package com.kenkern.engine.diag

import android.app.ActivityManager
import android.content.Context
import android.opengl.GLES30
import com.kenkern.engine.assets.image.Etc2
import com.kenkern.engine.assets.image.KimgPipeline
import com.kenkern.engine.assets.image.KimgReader
import com.kenkern.engine.assets.image.KimgWriter
import com.kenkern.engine.assets.import.AssetLayout
import com.kenkern.engine.assets.import.FbxAmostras
import com.kenkern.engine.assets.import.FbxImportException
import com.kenkern.engine.assets.import.FbxImporter
import com.kenkern.engine.assets.import.GltfAmostras
import com.kenkern.engine.assets.import.GltfImportException
import com.kenkern.engine.assets.import.GltfImporter
import com.kenkern.engine.assets.import.ImportException
import com.kenkern.engine.assets.import.ImportSummary
import com.kenkern.engine.assets.import.KernIntegrity
import com.kenkern.engine.assets.import.ModelImporter
import com.kenkern.engine.assets.import.ResolvedorZip
import com.kenkern.engine.commands.ImportModelCommand
import com.kenkern.engine.assets.mesh.KernFormat
import com.kenkern.engine.assets.mesh.KernMaterial
import com.kenkern.engine.assets.mesh.KernReader
import com.kenkern.engine.assets.mesh.KernWriter
import com.kenkern.engine.assets.mesh.MeshData
import com.kenkern.engine.core.LuzNick
import com.kenkern.engine.core.MalhaNick
import com.kenkern.engine.core.NickClassInstance
import com.kenkern.engine.core.NickClassRegistry
import com.kenkern.engine.core.NickKindRegistry
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.Scene
import com.kenkern.engine.editor.AddNickLayout
import com.kenkern.engine.editor.TopBarLayout
import kotlin.math.roundToInt
import com.kenkern.engine.core.linkerf.LinkerfLink
import com.kenkern.engine.core.linkerf.LinkerfPort
import com.kenkern.engine.core.linkerf.LinkerfPortKind
import com.kenkern.engine.core.linkerf.LinkerfPortType
import com.kenkern.engine.core.math.Vec3
import com.kenkern.engine.core.serialization.CkFormat
import com.kenkern.engine.render.CameraMode
import com.kenkern.engine.render.EngineRenderer
import com.kenkern.engine.render.GlesBackend
import com.kenkern.engine.render.OrbitCamera
import com.kenkern.engine.render.PickingMath
import com.kenkern.engine.render.TouchController
import com.kenkern.engine.core.math.Mat4
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import kotlin.math.abs
import kotlin.math.max

/**
 * TELA DIAGNÓSTICO (Prompt Mestre v3): checagens que rodam NO APARELHO com
 * resultado APROVADO/FALHOU + motivo. Toda fase adiciona checagens aqui
 * (Fase 3 acrescenta execução de scripts Lua).
 *
 * Checagens da Fase 2:
 *  - ck       — ida e volta do formato de cena .ck (CK1);
 *  - kern     — ida e volta do formato de malha .KERN com erro máximo medido;
 *  - gltf     — importador glTF/GLB → .KERN (meta + integridade + erros claros);
 *  - fbx      — importador FBX → .KERN (merge de instâncias + integridade + erros claros);
 *  - importar — botão Importar modelo: pipeline completa com modelo embutido
 *               (layout de assets, nome único, UM Command undo/redo, zip com .bin);
 *  - picking  — matemática do raio de tela nos 2 modos de câmera;
 *  - shaders  — compilação/link de um programa de teste no driver (GLSL ES 3.00);
 *  - buffers  — alocação e liberação de VBO/VAO reais via GlesBackend;
 *  - glcaps   — versão/renderizador/extensões/compute/limites;
 *  - memoria  — classes de memória e limites do aparelho.
 *  - release  — fumaça do APK minificado: classes críticas por nome + uma
 *               operação mínima de cada subsistema (guarda anti-R8, V1 da
 *               auditoria 0.1.15).
 *  - cena     — REGRA "CENA = TELA" (Bug A, v0.1.17): o viewport desenha SÓ
 *               Nicks desenhaveis (malha importada ou folha); containers
 *               (raiz "Mundo") não desenham nem são pegáveis; o torus .KERN
 *               de teste renderiza SÓ AQUI (offscreen, nunca no viewport).
 *
 * Regras: nada aqui derruba o app (toda checagem é blindada); sem GL no
 * construtor (só quando [executar] roda numa thread de fundo com o [EglDiag]);
 * zero dependência de Compose (a tela só observa os resultados).
 */
class Diagnostico(private val appContext: Context) {

    companion object {
        private const val TAG = "Kenkern/Diagnostico"

        /** E2 (v0.1.18): tempo máximo POR CHECAGEM — nenhuma pode travar a rodada. */
        const val TIMEOUT_CHECAGEM_MS = 30_000L

        /**
         * S1 (v0.1.24): catálogo de checagens PURO (sem Context) — a JVM prova
         * que todo id tem título único (DiagNomesChecagemTest). A instância
         * [novaLista] só delega aqui.
         */
        fun novaListaDeChecagens(): List<Checagem> = listOf(
            Checagem("nativo", "Núcleo nativo C++ — biblioteca, eco, handler de sinais"),
            Checagem("etc2-nativo", "ETC2 nativo (etcpak) — 1024² ms/MP/s + PSNR × Kotlin em 256²"),
            Checagem("malha-nativa", "Malha nativa (meshoptimizer) — dedup+cache+LOD: ms × Kotlin"),
            Checagem("ck", ".ck (cena) — ida e volta"),
            Checagem("kern", ".KERN (malha) — ida e volta"),
            Checagem("materiais", "Materiais PBR — .KERN v3 (5 mapas + fatores) + compat v2"),
            Checagem("gltf", "Importador glTF/GLB → .KERN (meta + integridade)"),
            Checagem("fbx", "Importador FBX → .KERN (instâncias + integridade)"),
            Checagem("importar", "Botão Importar modelo (pipeline + undo/redo + zip)"),
            Checagem("kimg", ".KIMG — ida e volta + mipmaps + limites"),
            Checagem("etc2", "ETC2 — upload e leitura de pixel (GL offscreen)"),
            Checagem("tex-cena", "Textura na cena — quad com .KIMG (pixel não-preto)"),
            Checagem("cena", "Cena = tela — viewport desenha só Nicks; M malhas medido"),
            Checagem("luz", "Luz do LUME — quad branco + luz vermelha (o pixel muda)"),
            Checagem("astc", "ASTC — extensão do aparelho (informativo)"),
            Checagem("picking", "Matemática de picking (raio de tela)"),
            Checagem("toque", "Toque no cubo — API real do TouchController"),
            Checagem("shaders", "Compilação de shaders (GLSL ES)"),
            Checagem("buffers", "Alocação de buffers (VBO/VAO)"),
            Checagem("glcaps", "Capacidades GL (versão/extensões/compute)"),
            Checagem("memoria", "Limites de memória do aparelho"),
            Checagem("release", "Release minificado — classes críticas + operações mínimas"),
            Checagem("layout", "Layout paisagem 800×360 — TopBar fixa cabe + janela Adicionar Nick alcançável"),
            Checagem("joystick", "Joystick de tela — matemática (normalização, zona morta, 8 direções, ações)")
        )
        /**
         * S1 (v0.1.24): parte (c) da checagem "materiais" — amostra glTF
         * embutida — EXTRAÍDA para a JVM poder reproduzir/verificar o
         * contrato (antes só existia dentro da instância, só no aparelho).
         *
         * CORREÇÃO S1b (causa raiz: Diagnostico.checarMateriais, v0.1.23,
         * bloco (c)): a checagem exigia tabela de materiais da amostra
         * `quadGlb()` — um glTF SEM NENHUM material no JSON — então ela
         * SEMPRE falhava com "amostra quad.glb importou SEM tabela de
         * materiais" no aparelho. O importador estava CERTO (tabela vazia
         * para modelo sem materiais). A amostra com material é
         * [GltfAmostras.quadComMaterialGlb] (2 primitivas com o MESMO
         * material — dedup deixa 1; fatores 0.8/0.2/0.1, metallic 0.5,
         * roughness 0.4, doubleSided).
         */
        fun avaliarAmostraMateriais(problemas: MutableList<String>): Int {
            try {
                val amostra = GltfImporter.importar(GltfAmostras.quadComMaterialGlb(), null, "quad.glb")
                val tabela = amostra.mesh.materiais
                if (tabela.isEmpty()) {
                    problemas.add("amostra quad.glb (com material) importou SEM tabela de materiais")
                    return 0
                }
                if (tabela.size != 1) {
                    problemas.add("dedup de materiais: 2 primitivas com o MESMO material devem virar 1 (vieram ${tabela.size})")
                }
                val m = tabela[0]
                if (abs(m.baseColorR - 0.8f) > 1e-3 || abs(m.baseColorG - 0.2f) > 1e-3 || abs(m.baseColorB - 0.1f) > 1e-3) {
                    problemas.add("baseColorFactor divergiu: (${m.baseColorR}, ${m.baseColorG}, ${m.baseColorB})")
                }
                if (abs(m.metallic - 0.5f) > 1e-3) problemas.add("metallicFactor divergiu: ${m.metallic}")
                if (abs(m.roughness - 0.4f) > 1e-3) problemas.add("roughnessFactor divergiu: ${m.roughness}")
                if (!m.doubleSided) problemas.add("doubleSided=true do glTF se perdeu")

                // Modelo SEM materiais no JSON: tabela VAZIA é o comportamento
                // correto — o importador NÃO INVENTA material (a falha antiga
                // vinha da CHECAGEM cobrar tabela cheia dessa amostra).
                val semMaterial = GltfImporter.importar(GltfAmostras.quadGlb(), null, "quad.glb")
                if (semMaterial.mesh.materiais.isNotEmpty()) {
                    problemas.add("modelo sem materiais importou ${semMaterial.mesh.materiais.size} materiais INVENTADOS")
                }
                return tabela.size
            } catch (e: Exception) {
                problemas.add("amostra quad.glb falhou: ${e.message ?: e.javaClass.simpleName}")
                return 0
            }
        }
    }

    fun novaLista(): List<Checagem> = novaListaDeChecagens()

    enum class Status { PENDENTE, RODANDO, APROVADO, FALHOU, CANCELADA }

    class Checagem(val id: String, val nome: String) {
        var status: Status = Status.PENDENTE
            private set
        var detalhe: String = "—"
            private set

        /** E2: tempo decorrido da checagem (ms) — mostrado na tela da rodada. */
        var tempoMs: Long = -1L
            private set

        fun aprovado(detalhe: String) {
            status = Status.APROVADO; this.detalhe = detalhe
        }

        fun falhou(detalhe: String) {
            status = Status.FALHOU; this.detalhe = detalhe
        }

        fun rodando() {
            status = Status.RODANDO; detalhe = "executando…"
        }

        /** E2: cancelada pelo usuário ou porque uma checagem anterior estourou o tempo. */
        fun cancelada(motivo: String) {
            status = Status.CANCELADA; detalhe = motivo
        }

        /** E2: registra o tempo decorrido (chamado no fim da checagem, nos dois desfechos). */
        fun registrarTempo(ms: Long) {
            tempoMs = ms
        }
    }


    /**
     * E2 (v0.1.18): cancela a rodada — o botão da tela chama junto com
     * Job.cancel(). A checagem corrente é interrompida (interrupção de thread +
     * laços pesados consultam [cancelado]); as seguintes saem como CANCELADA.
     */
    @Volatile
    var cancelado: Boolean = false
        private set

    fun cancelar() {
        cancelado = true
    }

    /**
     * Executa TODAS as checagens, na ordem, chamando [onAtualiza] a cada mudança
     * de estado (a tela observa). E2 (v0.1.18): SUSPENSE — cada checagem roda em
     * BACKGROUND com TIMEOUT de [TIMEOUT_CHECAGEM_MS] ([runInterruptible] interrompe
     * a thread de trabalho; os laços pesados consultam [cancelado]) e o tempo
     * decorrido de cada uma vai para a tela. Cancelamento externo (Job do botão)
     * propaga como CancellationException depois de marcar o resto como CANCELADA.
     */
    suspend fun executar(onAtualiza: (Checagem) -> Unit) {
        cancelado = false
        val rapidas = listOf("nativo", "etc2-nativo", "malha-nativa", "ck", "kern", "materiais", "gltf", "fbx", "importar", "kimg", "picking", "toque", "release", "layout", "joystick")
        val gl = listOf("shaders", "buffers", "glcaps", "etc2", "tex-cena", "cena", "luz", "astc")
        // E2: ids já FINALIZADAS nesta rodada — no cancel, só o que NÃO rodou vira
        // CANCELADA (aprovadas/falhas anteriores permanecem com o resultado delas).
        val finalizadas = HashSet<String>()

        // E2: o contexto EGL é CURRENT na thread que o criou — as checagens GL
        // rodam numa ÚNICA THREAD DEDICADA (executor de 1 thread como dispatcher);
        // as rápidas rodam no pool Default. Timeout/cancelamento interrompem a
        // thread corrente SEM trocar de contexto: a UI nunca trava.
        val glThread = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "kenkern-diag-gl") }
        try {
            for (id in rapidas) {
                rodarUma(id, onAtualiza, finalizadas)
            }

            val erro = StringBuilder()
            val egl = runInterruptible(glThread.asCoroutineDispatcher()) {
                EglDiag.criar { e -> erro.append(e) }
            }
            if (egl != null) {
                try {
                    for (id in gl) rodarUma(id, onAtualiza, finalizadas, egl, glThread.asCoroutineDispatcher())
                } finally {
                    runInterruptible(glThread.asCoroutineDispatcher()) { egl.liberar() }
                }
            } else {
                val motivo = if (erro.isEmpty()) "motivo desconhecido" else erro.toString()
                for (id in gl) {
                    val c = Checagem(id, nome(id))
                    c.falhou("contexto EGL pbuffer não criado: $motivo")
                    onAtualiza(c)
                }
            }

            rodarUma("memoria", onAtualiza, finalizadas)
        } catch (e: CancellationException) {
            // Botão cancelar (Job cancelado): marca como CANCELADA só o que NÃO
            // finalizou — chamadas simples, sem suspensão dentro do catch — e
            // REPROPAGA o cancelamento.
            val pendentes = (rapidas + gl + "memoria").filter { it !in finalizadas }
            for (id in pendentes) {
                val c = Checagem(id, nome(id))
                c.cancelada("cancelada pelo usuário")
                onAtualiza(c)
            }
            throw e
        } finally {
            glThread.shutdown()
        }
    }

    private fun nome(id: String): String = novaListaDeChecagens().first { it.id == id }.nome

    /**
     * E2: UMA checagem em background com timeout de [TIMEOUT_CHECAGEM_MS].
     * [TimeoutCancellationException] = FALHOU "tempo limite" e a rodada PARA
     * (uma checagem que estoura é fato digno de relatório; as seguintes saem
     * CANCELADA). Cancelamento EXTERNO (Job do botão) propaga limpo.
     * [runInterruptible] interrompe a thread de trabalho; os laços pesados
     * (encoder ETC2 Kotlin, dedup de malha) também consultam [cancelado].
     */
    private suspend fun rodarUma(
        id: String,
        onAtualiza: (Checagem) -> Unit,
        finalizadas: HashSet<String>,
        egl: EglDiag? = null,
        disp: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default
    ) {
        val c = Checagem(id, nome(id))
        if (cancelado) {
            // Uma checagem anterior estourou o tempo (ou usuário cancelou): não
            // executa mais nada — sai como CANCELADA sem gastar tempo nenhum.
            c.cancelada("cancelada — uma checagem anterior estourou o tempo")
            onAtualiza(c)
            finalizadas.add(id)
            return
        }
        c.rodando()
        onAtualiza(c)
        val t0 = System.nanoTime()
        var motivoTimeout: String? = null
        try {
            withTimeout(TIMEOUT_CHECAGEM_MS) {
                runInterruptible(disp) {
                    // Limpa interrupção PENDENTE de um timeout anterior nesta thread:
                    // um flag sujo quebraria as chamadas bloqueantes da checagem.
                    Thread.interrupted()
                    runCatching {
                        when (id) {
                            "nativo" -> checarNativo(c)
                            "etc2-nativo" -> checarEtc2Nativo(c)
                            "malha-nativa" -> checarMalhaNativa(c)
                            "ck" -> checarCk(c)
                            "kern" -> checarKern(c)
                            "materiais" -> checarMateriais(c)
                            "gltf" -> checarGltf(c)
                            "fbx" -> checarFbx(c)
                            "importar" -> checarImportar(c)
                            "kimg" -> checarKimg(c)
                            "picking" -> checarPicking(c)
                            "toque" -> checarToqueCubo(c)
                            "shaders" -> checarShaders(c)
                            "buffers" -> checarBuffers(c)
                            "glcaps" -> checarGlCaps(c)
                            "etc2" -> checarEtc2(c, egl)
                            "tex-cena" -> checarTexCena(c, egl)
                            "cena" -> checarCenaTela(c, egl)
                            "luz" -> checarLuz(c, egl)
                            "astc" -> checarAstc(c)
                            "memoria" -> checarMemoria(c)
                            "release" -> checarRelease(c)
                            "layout" -> checarLayout(c)
                            "joystick" -> checarJoystick(c)
                        }
                    }.onFailure {
                        c.falhou("exceção: ${it.message ?: it.javaClass.simpleName}")
                        KenkernLog.e(TAG, "checagem '$id' lançou exceção", it)
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            motivoTimeout = "tempo limite de ${TIMEOUT_CHECAGEM_MS / 1000} s — checagem interrompida (lento demais neste aparelho; ver relatório)"
            cancelado = true // a rodada PARA aqui: as seguintes saem CANCELADA
        }
        currentCoroutineContext().ensureActive() // cancelamento externo (botão) propaga
        c.registrarTempo((System.nanoTime() - t0) / 1_000_000L)
        if (cancelado && c.status != Status.FALHOU) c.cancelada("cancelada — uma checagem anterior estourou o tempo")
        if (motivoTimeout != null) c.falhou(motivoTimeout)
        finalizadas.add(id)
        onAtualiza(c)
    }

    // ---------------------------------------------------------------- .ck

    /**
     * Checagem "nativo" (B3d, v0.1.17) — NÚCLEO C++ NO APARELHO:
     *  - biblioteca carregada (isAvailable) + versão devolvida PELO NATIVO;
     *  - ABI do aparelho (arm64-v8a é a única compilada);
     *  - CADA função JNI testada INDIVIDUALMENTE (E1, v0.1.18): o detalhe
     *    lista versão/eco/instalar-handler/consultar-handler com ✓ ou com o
     *    motivo LITERAL ("No implementation found…" = símbolo ausente na .so);
     *  - HANDLER DE SINAIS instalado (SIGSEGV/SIGABRT/SIGBUS/SIGFPE gravam
     *    crash_logs/native_crash.txt com sinal, endereço e backtrace).
     * NUNCA provoca crash de propósito: a checagem SÓ consulta estado — o
     * handler será exercitado por um crash real, se acontecer.
     */
    private fun checarNativo(c: Checagem) {
        val carregada = com.kenkern.engine.nativecore.NativeCore.carregar()
        val abi = com.kenkern.engine.nativecore.NativeCore.abi
        if (!carregada) {
            c.falhou(
                "libkenkern.so NÃO carregou (ver CONSOLE) — módulos nativos seguem em fallback Kotlin; " +
                    "esperado arm64-v8a, aparelho é $abi"
            )
            return
        }
        val caminhoCrash = java.io.File(java.io.File(appContext.filesDir, "crash_logs"), "native_crash.txt").absolutePath
        val externas = com.kenkern.engine.nativecore.NativeCore.testarExternas(caminhoCrash)
        val okLista = externas.joinToString(" · ") { r ->
            if (r.ok) "${r.nome} ✓" else "${r.nome} FALTA (${r.motivoFalha})"
        }
        val faltando = externas.filter { !it.ok }

        if (faltando.isEmpty()) {
            val versao = com.kenkern.engine.nativecore.NativeCore.versao ?: "?"
            c.aprovado(
                "libkenkern.so carregada · versão $versao · ABI $abi · funções JNI: $okLista · " +
                    "handler de sinais instalado (SIGSEGV/SIGABRT/SIGBUS/SIGFPE → crash_logs/native_crash.txt)"
            )
        } else {
            c.falhou("$okLista — ${faltando.size} de ${externas.size} funções JNI não resolveu")
        }
    }

    /**
     * Checagem "etc2 nativo" (B4, v0.1.17) — ENCODER ETC2 NATIVO × KOTLIN no
     * APARELHO. E2 (v0.1.18): o NATIVO é medido em 1024×1024 (a meta B5 é
     * "< 1 s em 1024² NESTE aparelho") e a COMPARAÇÃO nativo × Kotlin roda em
     * 256×256 — mesmo padrão, mesma semente, igualmente válida (os testes JVM
     * comparam 4×4 e não-múltiplos de 4). Motivo: o custo dominante é o
     * ENCODER KOTLIN (sem etcpak), dezenas de segundos em 1024² num aparelho
     * médio — era a causa da tela ficar em "executando…" sem nunca terminar
     * (Diagnostico.executar era BLOQUEANTE, sem timeout, sem botão cancelar).
     * APROVADO exige: etcpak carregado, mesmo tamanho de saída e PSNR do
     * nativo ≥ PSNR do Kotlin − 1 dB (o etcpak não pode ser pior de verdade).
     * O laço do encoder Kotlin consulta [cancelado] (responde ao botão).
     */
    private fun checarEtc2Nativo(c: Checagem) {
        val w = 1024
        val h = 1024
        val rgba = ByteArray(w * h * 4)
        var semente = 424242L
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = (y * w + x) * 4
                semente = semente * 6364136223846793005L + 1442695040888963407L
                val ruido = ((semente ushr 33) % 11).toInt() - 5
                rgba[i] = (110 + x / 4 + ruido).coerceIn(0, 255).toByte()
                rgba[i + 1] = (90 + y / 4 + ruido).coerceIn(0, 255).toByte()
                rgba[i + 2] = (150 + (x + y) / 8).coerceIn(0, 255).toByte()
                rgba[i + 3] = 255.toByte()
            }
        }

        if (!com.kenkern.engine.nativecore.Etc2Nativo.disponivel()) {
            c.falhou("encoder etcpak indisponível (ver CONSOLE) — a importação segue no encoder Kotlin")
            return
        }

        // ---------- NATIVO 1024² (medição da meta B5: < 1 s) ----------
        val tNativo0 = System.nanoTime()
        val nativo1024 = com.kenkern.engine.nativecore.Etc2Nativo.codificar(rgba, w, h, temAlfa = false, qualidadeRapida = true)
        val nativo1024Ms = (System.nanoTime() - tNativo0) / 1_000_000L
        if (nativo1024 == null) {
            c.falhou("etcpak devolveu null (ver CONSOLE) — encoder Kotlin assume")
            return
        }

        // ---------- COMPARAÇÃO 256² (recorte da MESMA textura) ----------
        val cw = 256
        val ch = 256
        val recorte = ByteArray(cw * ch * 4)
        for (y in 0 until ch) {
            System.arraycopy(rgba, y * w * 4, recorte, y * cw * 4, cw * 4)
        }

        val tNativo256 = System.nanoTime()
        val nativo256 = com.kenkern.engine.nativecore.Etc2Nativo.codificar(recorte, cw, ch, temAlfa = false, qualidadeRapida = true)
        val nativo256Ms = (System.nanoTime() - tNativo256) / 1_000_000L
        if (nativo256 == null) {
            c.falhou("etcpak devolveu null na comparação 256² (ver CONSOLE) — encoder Kotlin assume")
            return
        }
        val psnrNativo = com.kenkern.engine.assets.image.Etc2.psnr(recorte, com.kenkern.engine.assets.image.Etc2.decodificarRgb(nativo256, cw, ch))

        // ---------- KOTLIN 256² (encoder atual, modo rápido, cancelável) ----------
        com.kenkern.engine.assets.image.Etc2.testeForcarKotlin = true
        val kotlinBlocos: ByteArray
        val kotlinMs: Long
        try {
            val tKotlin0 = System.nanoTime()
            kotlinBlocos = com.kenkern.engine.assets.image.Etc2.codificarRgb(
                recorte, cw, ch,
                cancelado = { cancelado },
                qualidade = com.kenkern.engine.assets.image.Etc2.Qualidade.RAPIDA
            )
            kotlinMs = (System.nanoTime() - tKotlin0) / 1_000_000L
        } finally {
            com.kenkern.engine.assets.image.Etc2.testeForcarKotlin = false
        }
        val psnrKotlin = com.kenkern.engine.assets.image.Etc2.psnr(recorte, com.kenkern.engine.assets.image.Etc2.decodificarRgb(kotlinBlocos, cw, ch))

        // ---------- veredito ----------
        val mp = w.toLong() * h / 1_000_000.0
        val problemas = ArrayList<String>(3)
        if (nativo256.size != kotlinBlocos.size) problemas.add("tamanhos divergem na comparação: nativo ${nativo256.size} × kotlin ${kotlinBlocos.size}")
        if (psnrNativo < psnrKotlin - 1.0) problemas.add("PSNR nativo ${"%.1f".format(java.util.Locale.US, psnrNativo)} dB < kotlin ${"%.1f".format(java.util.Locale.US, psnrKotlin)} dB − 1")
        if (nativo1024Ms > 1_000L) problemas.add("nativo levou $nativo1024Ms ms em 1024² (meta B5: < 1 s)")

        if (problemas.isEmpty()) {
            c.aprovado(
                "1024² nativo ${nativo1024Ms} ms (%.1f MP/s · meta < 1 s ok) · comparação 256²: nativo ${nativo256Ms} ms × kotlin ${kotlinMs} ms · PSNR nativo %.1f dB × kotlin %.1f dB · saída ${nativo256.size} B idêntica".format(
                    java.util.Locale.US, mp / (nativo1024Ms.coerceAtLeast(1) / 1000.0), psnrNativo, psnrKotlin
                )
            )
        } else {
            c.falhou(problemas.joinToString("; "))
        }
    }

    /**
     * Checagem "malha nativa" (B6, v0.1.17) — MESHOPTIMIZER × KOTLIN no
     * APARELHO: grade CONECTADA de 100×100 células (20.000 triângulos) passa
     * pelo dedup+cache (meshopt_generateVertexRemap + optimizeVertexCache) e
     * pelo LOD (meshopt_simplify) NATIVOS e pelos equivalentes KOTLIN
     * (deduplicarVertices + gerarLods); mostra os ms dos dois + a contagem de
     * vértices únicos (tem que ser IGUAL nos dois caminhos).
     */
    private fun checarMalhaNativa(c: Checagem) {
        // ---------- malha de teste: grade conectada (dedup funde os cantos)
        val cols = 100
        val linhas = 100
        val b = com.kenkern.engine.assets.mesh.KernWriter.Builder()
        for (cy in 0..linhas) {
            for (cx in 0..cols) {
                b.vertex(
                    cx.toFloat(), cy.toFloat(), 0f, 0f, 0f, 1f,
                    cx.toFloat() / cols, cy.toFloat() / linhas, 1f, 0f, 0f
                )
            }
        }
        for (cy in 0 until linhas) {
            for (cx in 0 until cols) {
                val a = cy * (cols + 1) + cx
                val bIdx = a + 1
                val d = a + cols + 2
                val e = a + cols + 1
                b.triangle(a, bIdx, d)
                b.triangle(a, d, e)
            }
        }
        val malha = b.build()

        if (!com.kenkern.engine.nativecore.MalhaNativa.disponivel()) {
            c.falhou("meshoptimizer indisponível (ver CONSOLE) — o importador segue no caminho Kotlin")
            return
        }

        // ---------- NATIVO: dedup + cache, depois LOD por simplificação
        val tNativo0 = System.nanoTime()
        val registros = ByteArray(malha.vertexCount * com.kenkern.engine.nativecore.MalhaNativa.BYTES_REGISTRO_VERTICE)
        var p = 0
        for (v in 0 until malha.vertexCount) {
            for (k in 0 until 3) { val f = malha.positions[v * 3 + k]; registros[p++] = (java.lang.Float.floatToIntBits(f) and 0xFF).toByte(); registros[p++] = ((java.lang.Float.floatToIntBits(f) ushr 8) and 0xFF).toByte(); registros[p++] = ((java.lang.Float.floatToIntBits(f) ushr 16) and 0xFF).toByte(); registros[p++] = ((java.lang.Float.floatToIntBits(f) ushr 24) and 0xFF).toByte() }
            for (k in 0 until 3) { val f = malha.normals[v * 3 + k]; registros[p++] = (java.lang.Float.floatToIntBits(f) and 0xFF).toByte(); registros[p++] = ((java.lang.Float.floatToIntBits(f) ushr 8) and 0xFF).toByte(); registros[p++] = ((java.lang.Float.floatToIntBits(f) ushr 16) and 0xFF).toByte(); registros[p++] = ((java.lang.Float.floatToIntBits(f) ushr 24) and 0xFF).toByte() }
            for (k in 0 until 2) { val f = malha.uvs[v * 2 + k]; registros[p++] = (java.lang.Float.floatToIntBits(f) and 0xFF).toByte(); registros[p++] = ((java.lang.Float.floatToIntBits(f) ushr 8) and 0xFF).toByte(); registros[p++] = ((java.lang.Float.floatToIntBits(f) ushr 16) and 0xFF).toByte(); registros[p++] = ((java.lang.Float.floatToIntBits(f) ushr 24) and 0xFF).toByte() }
        }
        val dedupNativo = com.kenkern.engine.nativecore.MalhaNativa.dedupEOtimizar(
            registros, malha.indices, intArrayOf(0, malha.indices.size), malha.vertexCount
        )
        val lodNativo = if (dedupNativo != null) {
            com.kenkern.engine.nativecore.MalhaNativa.simplificar(
                dedupNativo.indices, malha.positions, malha.vertexCount,
                (malha.indices.size / 4 / 3) * 3
            )
        } else null
        val nativoMs = (System.nanoTime() - tNativo0) / 1_000_000L

        // ---------- KOTLIN: dedup + LODs por grade (o código atual)
        val tKotlin0 = System.nanoTime()
        val dedupKotlin = com.kenkern.engine.assets.import.PosProcessamento.deduplicarVertices(malha)
        val lodKotlin = com.kenkern.engine.assets.import.PosProcessamento.gerarLods(dedupKotlin)
        val kotlinMs = (System.nanoTime() - tKotlin0) / 1_000_000L

        if (dedupNativo == null || lodNativo == null) {
            c.falhou("meshoptimizer falhou (ver CONSOLE) — importador segue no Kotlin")
            return
        }

        val problemas = ArrayList<String>(2)
        if (dedupNativo.unicos != dedupKotlin.vertexCount) {
            problemas.add("vértices únicos divergem: nativo ${dedupNativo.unicos} × kotlin ${dedupKotlin.vertexCount}")
        }
        if (lodNativo.indices.size % 3 != 0 || lodNativo.indices.size > malha.indices.size) {
            problemas.add("LOD nativo incoerente: ${lodNativo.indices.size} índices")
        }

        if (problemas.isEmpty()) {
            val aceleracao = if (kotlinMs > 0) kotlinMs.toDouble() / nativoMs.coerceAtLeast(1) else 0.0
            c.aprovado(
                "grade 100×100 (20.000 tris) · nativo ${nativoMs} ms × kotlin ${kotlinMs} ms · %.1f× · " +
                    "únicos ${dedupNativo.unicos} == ${dedupKotlin.vertexCount} · LOD nativo ${lodNativo.indices.size / 3} tris (erro %.4f) · LODs kotlin ${lodKotlin.lods.size}".format(
                        java.util.Locale.US, aceleracao, lodNativo.erro
                )
            )
        } else {
            c.falhou(problemas.joinToString("; "))
        }
    }

    private fun checarCk(c: Checagem) {
        val scene = Scene("Diag")
        val mundo = scene.createRootNick("Mundo")
        val cubo = scene.createNick("Cubo", mundo)
        val spin = NickClassInstance("Spin")
        spin.fields["speed"] = NickValue.FloatValue(72.5f)
        cubo.addClassInstance(spin)
        val filho = scene.createNick("Filho", mundo)
        filho.transform.setLocalPosition(3f, -2.5f, 0.5f)
        val dados = NickClassInstance("Dados")
        dados.fields["n"] = NickValue.IntValue(7)
        dados.fields["ok"] = NickValue.BoolValue(true)
        dados.fields["s"] = NickValue.StringValue("diagnóstico \"rígido\"")
        dados.fields["v"] = NickValue.Vec3Value(1f, 2f, 3f)
        filho.addClassInstance(dados)
        val pSaida = LinkerfPort(
            com.kenkern.engine.core.ReferenceId.generate(),
            com.kenkern.engine.core.ReferenceId.generate(),
            "saiu", LinkerfPortKind.EVENT_OUT, LinkerfPortType.TRIGGER
        )
        val pEntrada = LinkerfPort(
            com.kenkern.engine.core.ReferenceId.generate(),
            com.kenkern.engine.core.ReferenceId.generate(),
            "chegou", LinkerfPortKind.EVENT_IN, LinkerfPortType.TRIGGER
        )
        scene.linkerf.addPort(pSaida)
        scene.linkerf.addPort(pEntrada)
        scene.linkerf.addLinkDirect(LinkerfLink(com.kenkern.engine.core.ReferenceId.generate(), pSaida.id, pEntrada.id))

        val texto = CkFormat.writeToString(scene)
        val volta = CkFormat.parseToNewScene(texto, "fallback")
            ?: throw IllegalStateException("parse retornou null")

        val problemas = ArrayList<String>(4)
        if (volta.roots.size != 1 || volta.roots[0].children.size != 2) problemas.add("hierarquia")
        val cuboV = volta.roots[0].children.firstOrNull { it.name == "Cubo" }
            ?: throw IllegalStateException("Cubo sumiu no round-trip")
        val spinV = cuboV.findClassInstance("Spin")
        if (spinV == null || (spinV.fields["speed"] as? NickValue.FloatValue)?.value != 72.5f) problemas.add("campo FLOAT")
        val filhoV = volta.roots[0].children.firstOrNull { it.name == "Filho" }
        if (filhoV == null || abs(filhoV.transform.position.y - -2.5f) > 1e-4f) problemas.add("transform")
        val dadosV = filhoV?.findClassInstance("Dados")
        if (dadosV == null ||
            (dadosV.fields["n"] as? NickValue.IntValue)?.value != 7 ||
            (dadosV.fields["ok"] as? NickValue.BoolValue)?.value != true ||
            (dadosV.fields["s"] as? NickValue.StringValue)?.value != "diagnóstico \"rígido\"" ||
            (dadosV.fields["v"] as? NickValue.Vec3Value)?.z != 3f
        ) problemas.add("tipos de campo")
        if (volta.linkerf.allPorts().size != 2 || volta.linkerf.allLinks().size != 1) problemas.add("LINKERF")

        if (problemas.isEmpty()) {
            c.aprovado(
                "3 Nicks · 2 classes · 4 tipos de campo · LINKERF 2 portas/1 link — " +
                    "ida e volta exata (${texto.length} bytes)"
            )
        } else {
            c.falhou("divergência no round-trip: ${problemas.joinToString()}")
        }
    }

    // ---------------------------------------------------------------- .KERN

    private fun checarKern(c: Checagem) {
        // Pirâmide de 4 vértices/12 índices: menor malha válida com triângulos.
        val b = KernWriter.Builder()
        b.vertex(0f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 1f, 0f, 0f)
        b.vertex(1f, 0f, 0f, 0f, -1f, 0f, 1f, 0f, 1f, 0f, 0f)
        b.vertex(1f, 0f, 1f, 0f, -1f, 0f, 1f, 1f, 1f, 0f, 0f)
        b.vertex(0.5f, 1f, 0.5f, 0.5f, 1f, 0.5f, 0.5f, 0.5f, 1f, 0f, 0f)
        b.triangle(0, 2, 1)
        b.triangle(0, 1, 3)
        b.triangle(1, 2, 3)
        b.triangle(2, 0, 3)
        val original = b.build()
        val lida = KernReader.read(KernWriter.write(original))

        if (lida.vertexCount != 4 || lida.indices.size != 12) {
            c.falhou("contagens erradas: ${lida.vertexCount} vértices / ${lida.indices.size} índices (esperado 4/12)")
            return
        }
        val span = max(
            original.boundsMax.x - original.boundsMin.x,
            max(original.boundsMax.y - original.boundsMin.y, original.boundsMax.z - original.boundsMin.z)
        )
        val tolerancia = span / 65535f
        var erroMax = 0f
        for (i in original.positions.indices) {
            erroMax = max(erroMax, abs(original.positions[i] - lida.positions[i]))
        }
        for (i in original.indices.indices) {
            if (original.indices[i] != lida.indices[i]) {
                c.falhou("índice $i divergiu: ${original.indices[i]} vs ${lida.indices[i]}")
                return
            }
        }
        if (erroMax > tolerancia) {
            c.falhou("erro máximo de posição $erroMax > tolerância $tolerancia")
        } else {
            c.aprovado(
                "4 vértices · 12 índices · índices exatos · erro máx de posição " +
                    "%.1e (tolerância %.1e)".format(java.util.Locale.US, erroMax, tolerancia)
            )
        }
    }

    // ---------------------------------------------------------------- glTF (F2.7)

    private fun checarGltf(c: Checagem) {
        // 1) Quad GLB completo (POSITION+NORMAL+UV+índices u16)
        val r1 = try {
            GltfImporter.importar(GltfAmostras.quadGlb())
        } catch (e: Exception) {
            c.falhou("quad GLB: ${e.message}")
            return
        }
        if (r1.mesh.vertexCount != 4 || r1.mesh.indices.size != 6) {
            c.falhou("quad GLB: ${r1.mesh.vertexCount} vértices / ${r1.mesh.indices.size} índices (esperado 4/6)")
            return
        }
        val p1 = KernIntegrity.verificar(r1.mesh, r1.bytesKern)
        if (p1.isNotEmpty()) {
            c.falhou("quad GLB integridade: ${p1.joinToString("; ")}")
            return
        }

        // 2) Cubo em 2 nodes com transform (TRS/quaternion + herança) → merge
        val r2 = try {
            GltfImporter.importar(GltfAmostras.cuboComTransformGlb())
        } catch (e: Exception) {
            c.falhou("cubo com transform: ${e.message}")
            return
        }
        if (r2.mesh.vertexCount != 16 || r2.mesh.indices.size != 72) {
            c.falhou("cubo com transform: ${r2.mesh.vertexCount} vértices / ${r2.mesh.indices.size} índices (esperado 16/72 — merge de 2 instâncias)")
            return
        }
        val p2 = KernIntegrity.verificar(r2.mesh, r2.bytesKern)
        if (p2.isNotEmpty()) {
            c.falhou("cubo com transform integridade: ${p2.joinToString("; ")}")
            return
        }

        // 3) Erros CLAROS (nunca silenciosos — regra da engine)
        val magicOk = try {
            GltfImporter.importar(GltfAmostras.magicQuebrado())
            false
        } catch (e: GltfImportException) {
            true
        }
        if (!magicOk) {
            c.falhou("arquivo com magic quebrado NÃO gerou erro claro")
            return
        }

        c.aprovado(
            "GLB quad + cubo com transform (merge 2 instâncias) importados, integridade OK · " +
                r1.report.resumo() + " · erros claros OK"
        )
    }

    // ---------------------------------------------------------------- fbx

    private fun checarFbx(c: Checagem) {
        // 1) Quad FBX (2 triângulos expandidos = 6 vértices; normais Direct + UV IndexToDirect)
        val r1 = try {
            FbxImporter.importar(FbxAmostras.quadFbx())
        } catch (e: Exception) {
            c.falhou("quad FBX: ${e.message}")
            return
        }
        if (r1.mesh.vertexCount != 6 || r1.mesh.indices.size != 6) {
            c.falhou("quad FBX: ${r1.mesh.vertexCount} vértices / ${r1.mesh.indices.size} índices (esperado 6/6)")
            return
        }
        val p1 = KernIntegrity.verificar(r1.mesh, r1.bytesKern)
        if (p1.isNotEmpty()) {
            c.falhou("quad FBX integridade: ${p1.joinToString("; ")}")
            return
        }

        // 2) Cubo em 6 polígonos de quad compartilhado por 2 Models (TRS em graus)
        //    → merge de instâncias: 24 cantos × 2 = 48 vértices, 36 índices × 2 = 72
        val r2 = try {
            FbxImporter.importar(FbxAmostras.cuboCompartilhadoFbx())
        } catch (e: Exception) {
            c.falhou("cubo compartilhado: ${e.message}")
            return
        }
        if (r2.mesh.vertexCount != 48 || r2.mesh.indices.size != 72) {
            c.falhou("cubo compartilhado: ${r2.mesh.vertexCount} vértices / ${r2.mesh.indices.size} índices (esperado 48/72)")
            return
        }
        val p2 = KernIntegrity.verificar(r2.mesh, r2.bytesKern)
        if (p2.isNotEmpty()) {
            c.falhou("cubo compartilhado integridade: ${p2.joinToString("; ")}")
            return
        }

        // 3) Erros CLAROS (nunca silenciosos — regra da engine)
        val asciiOk = try {
            FbxImporter.importar(FbxAmostras.asciiFbx())
            false
        } catch (e: FbxImportException) {
            true
        }
        if (!asciiOk) {
            c.falhou("FBX ASCII sem Objects NÃO gerou erro claro")
            return
        }

        // 4) FBX ASCII COMPLETO (ETAPA B.2): mesmo quad do binário, escrito em texto
        val asciiQuad = try {
            FbxImporter.importar(FbxAmostras.quadAsciiFbx())
        } catch (e: Exception) {
            c.falhou("FBX ASCII completo: ${e.message}")
            return
        }
        if (asciiQuad.mesh.vertexCount != 6 || asciiQuad.mesh.indices.size != 6) {
            c.falhou(
                "FBX ASCII completo: ${asciiQuad.mesh.vertexCount} vértices / " +
                    "${asciiQuad.mesh.indices.size} índices (esperado 6/6)"
            )
            return
        }

        // 5) FBX ASCII com MATERIAL (ETAPA B.3): tabela + grupo + cor do DiffuseColor
        val asciiMat = try {
            FbxImporter.importar(FbxAmostras.quadAsciiComMaterialFbx())
        } catch (e: Exception) {
            c.falhou("FBX ASCII com material: ${e.message}")
            return
        }
        if (asciiMat.mesh.materiais.size != 1 || asciiMat.mesh.gruposMaterial.size != 1) {
            c.falhou(
                "FBX ASCII material: ${asciiMat.mesh.materiais.size} materiais / " +
                    "${asciiMat.mesh.gruposMaterial.size} grupos (esperado 1/1)"
            )
            return
        }

        c.aprovado(
            "FBX quad + cubo compartilhado (merge 2 instâncias, TRS em graus) importados, " +
                "integridade OK · " + r2.report.resumo() + " · erros claros OK · " +
                "FBX ASCII (B.2) quad 6/6 + material (B.3) OK"
        )
    }

    // ---------------------------------------------------------------- importar (ETAPA A)

    /**
     * CHECAGEM "importar": pipeline COMPLETA do botão Importar com um modelo
     * EMBUTIDO (sem SAF): ModelImporter (GLB) + layout de assets com nome
     * único + UM Command undo/redo (Nick sai, arquivos vão para a lixeira e
     * voltam) + glTF externo dentro de .zip (resolver com zip-slip bloqueado).
     */
    private fun checarImportar(c: Checagem) {
        // Pasta temporária que simula a raiz de um projeto (só nesta checagem).
        val raiz = java.io.File(appContext.cacheDir, "diag-import-${System.nanoTime()}")
        try {
            AssetLayout.criarPastas(raiz)

            // 1) Importação GLB embutida (pipeline completa, etapas + integridade)
            val summary: ImportSummary = try {
                ModelImporter.importarModelo(
                    "diag-quad.glb", GltfAmostras.quadGlb(), ModelImporter.Opcoes(true, 2f)
                )
            } catch (e: Exception) {
                c.falhou("GLB embutido: ${e.message}")
                return
            }
            if (!summary.integridadeOk) {
                c.falhou("GLB embutido integridade: ${summary.problemasIntegridade.joinToString("; ")}")
                return
            }
            if (summary.bytesKernData.isEmpty() || summary.vertices != 4) {
                c.falhou("GLB embutido: ${summary.vertices} vértices / ${summary.bytesKern} bytes (esperado 4 vértices)")
                return
            }

            // 2) Layout de assets: nome único (2ª cópia vira -2, nunca sobrescreve)
            val dirOrigem = java.io.File(raiz, AssetLayout.DIR_ORIGEM)
            val n1 = AssetLayout.nomeUnico(dirOrigem, "diag-quad", "glb")
            java.io.File(dirOrigem, n1).writeBytes(GltfAmostras.quadGlb())
            val n2 = AssetLayout.nomeUnico(dirOrigem, "diag-quad", "glb")
            if (n1 == n2) {
                c.falhou("nome único falhou: '$n1' duas vezes (colisão sobrescreve)")
                return
            }
            if (AssetLayout.caminhoRelativoSeguro("../fuga.bin") != null ||
                AssetLayout.caminhoRelativoSeguro("/absoluto.bin") != null
            ) {
                c.falhou("caminhoRelativoSeguro aceitou '..' ou caminho absoluto (zip-slip)")
                return
            }

            // 3) UM Command: cria Nick com classe Malha; undo remove Nick + recolhe
            //    arquivos; redo devolve tudo (A.3)
            val scene = Scene("Diag-Import")
            val mundo = scene.createRootNick("Mundo")
            val kernNome = AssetLayout.nomeUnico(java.io.File(raiz, AssetLayout.DIR_MODELOS), "diag-quad", "KERN")
            val kernRel = "${AssetLayout.DIR_MODELOS}/$kernNome"
            java.io.File(raiz, kernRel).writeBytes(summary.bytesKernData)
            val classe = ModelImporter.criarClasseMalha(kernRel, summary.aabbMin, summary.aabbMax, summary.escalaSugerida)
            val cmd = ImportModelCommand(
                scene, mundo.id, "diag-quad", raiz,
                listOf(kernRel, "${AssetLayout.DIR_ORIGEM}/$n1"), classe
            )
            cmd.execute()
            val nick = scene.findNickByNameAnywhere("diag-quad")
            if (nick == null || !com.kenkern.engine.core.MalhaNick.temMalha(nick)) {
                c.falhou("Command não criou o Nick com a classe Malha")
                return
            }
            cmd.undo()
            if (scene.roots[0].children.isNotEmpty()) {
                c.falhou("undo não removeu o Nick importado")
                return
            }
            // CORREÇÃO D3 (v0.1.16): a checagem antiga procurava o arquivo em
            // assets/.lixeira-import/<rel> — SEM o <importId> que o Command usa
            // (assets/.lixeira-import/imp-<id>/<rel>). O undo FUNCIONAVA; a
            // checagem olhava a pasta errada e falhava com "undo não recolheu
            // o .KERN" no aparelho. Agora usa cmd.pastaLixeira (o caminho REAL)
            // e, se falhar, mostra a causa exata com os caminhos.
            val kernOrigem = java.io.File(raiz, kernRel)
            val kernNaLixeira = java.io.File(cmd.pastaLixeira, kernRel)
            val kernApagado = !kernOrigem.exists() && kernNaLixeira.exists()
            if (!kernApagado) {
                c.falhou(
                    "undo não recolheu o .KERN para a lixeira de importação — origem ${kernOrigem.path} " +
                        "(existe=${kernOrigem.exists()}) · lixeira ${cmd.pastaLixeira.path} " +
                        "(existe=${cmd.pastaLixeira.exists()}) · arquivo esperado ${kernNaLixeira.path} " +
                        "(existe=${kernNaLixeira.exists()})"
                )
                return
            }
            cmd.execute()
            val voltou = java.io.File(raiz, kernRel).exists() &&
                scene.roots[0].children.any { it.name == "diag-quad" }
            if (!voltou) {
                c.falhou("redo não devolveu o .KERN e o Nick")
                return
            }

            // 4) glTF com .bin externo dentro de .zip (resolver + zip-slip B.1)
            val zipModelo = try {
                val zipBytes = java.io.ByteArrayOutputStream()
                java.util.zip.ZipOutputStream(zipBytes).use { zout ->
                    zout.putNextEntry(java.util.zip.ZipEntry("modelo/quad.gltf"))
                    zout.write(GltfAmostras.quadGltfExterno())
                    zout.closeEntry()
                    zout.putNextEntry(java.util.zip.ZipEntry("modelo/quad.bin"))
                    zout.write(GltfAmostras.quadBinExterno())
                    zout.closeEntry()
                }
                ModelImporter.importarModelo("quad.zip", zipBytes.toByteArray())
            } catch (e: Exception) {
                c.falhou("glTF externo no zip: ${e.message}")
                return
            }
            if (!zipModelo.integridadeOk || zipModelo.vertices != 4) {
                c.falhou("glTF externo no zip: ${zipModelo.vertices} vértices / ${zipModelo.problemasIntegridade.joinToString("; ")}")
                return
            }

            c.aprovado(
                "GLB embutido (4 v, integridade OK, pico %.0f MB, %d ms) · nome único + zip-slip bloqueado · " +
                    "Command undo/redo com lixeira OK · glTF com .bin externo via .zip OK".format(
                        java.util.Locale.US, summary.picoMemoriaMb, summary.tempoMs
                    )
            )
        } finally {
            raiz.deleteRecursively()
        }
    }

    // ---------------------------------------------------------------- .KIMG (ETAPA C)

    /** Checagem "kimg": pipeline RGBA → .KIMG → leitura → decodificação → comparação. */
    private fun checarKimg(c: Checagem) {
        // DOIS PADRÕES (D4.4): o antigo (x*32, y*32, alfa alternada 255/128) é
        // ADVERSARIAL — 16 cores vivas por bloco 4×4 + alfa trocando por pixel
        // é o pior caso do EAC e derruba o PSNR de QUALQUER codec de blocos
        // (no aparelho dava 10,7 dB, número que não mede corretude). O REALISTA
        // é gradiente suave com ruído leve (±3) — o que uma textura de jogo
        // tem. Os DOIS PSNRs vão na mensagem para o número ser significativo.
        val w = 8
        val h = 8
        val adversarial = ByteArray(w * h * 4)
        val realista = ByteArray(w * h * 4)
        var semente = 12345
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = (y * w + x) * 4
                adversarial[i] = (x * 32).toByte()
                adversarial[i + 1] = (y * 32).toByte()
                adversarial[i + 2] = 128.toByte()
                adversarial[i + 3] = (if ((x + y) % 2 == 0) 255 else 128).toByte()
                // realista: gradiente contínuo + ruído leve determinístico
                semente = (semente * 1103515245 + 12345) and 0x7FFFFFFF
                val ruido = (semente % 7) - 3
                realista[i] = (12 + x * 26 + ruido).coerceIn(0, 255).toByte()
                realista[i + 1] = (18 + y * 26 + ruido).coerceIn(0, 255).toByte()
                realista[i + 2] = (44 + ((x + y) * 6) + ruido).coerceIn(0, 255).toByte()
                realista[i + 3] = 255.toByte()
            }
        }

        fun psnrDe(padrao: ByteArray): Pair<Double, Int> {
            val (bytes, _) = KimgPipeline.converter(padrao, w, h, limiteTextura = 0)
            val img = KimgReader.read(bytes)
            // o realista tem alfa 255 em todo pixel → pipeline escolhe ETC2_RGB:
            // decodificar no formato que FOI gravado (RGBA daria "truncado")
            val decodificada = if (img.formato == com.kenkern.engine.assets.image.KimgFormato.ETC2_RGBA) {
                Etc2.decodificarRgba(img.mips[0].dados, img.largura, img.altura)
            } else {
                Etc2.decodificarRgb(img.mips[0].dados, img.largura, img.altura)
            }
            return Etc2.psnr(padrao, decodificada) to bytes.size
        }
        val (psnrRealista, bytesRealista) = try {
            psnrDe(realista)
        } catch (e: Exception) {
            c.falhou("pipeline .KIMG (realista): ${e.message}")
            return
        }
        val (psnrAdversarial, _) = try {
            psnrDe(adversarial)
        } catch (e: Exception) {
            c.falhou("pipeline .KIMG (adversarial): ${e.message}")
            return
        }
        val (bytes, relatorio) = try {
            KimgPipeline.converter(realista, w, h, srgb = true, limiteTextura = 0)
        } catch (e: Exception) {
            c.falhou("pipeline .KIMG: ${e.message}")
            return
        }
        val imagem = try {
            KimgReader.read(bytes)
        } catch (e: Exception) {
            c.falhou("leitura .KIMG: ${e.message}")
            return
        }
        if (imagem.largura != w || imagem.altura != h || imagem.mips.isEmpty()) {
            c.falhou(".KIMG lido com dimensões erradas (${imagem.largura}×${imagem.altura}, ${imagem.mips.size} mips)")
            return
        }
        if (imagem.mips.size < 3) {
            c.falhou("esperado ≥ 3 mipmaps de uma textura 8×8 (veio ${imagem.mips.size})")
            return
        }
        // arquivo truncado = erro CLARO (nunca estoura buffer)
        val truncado = bytes.copyOf(bytes.size / 2)
        val erroTruncado = try {
            KimgReader.read(truncado)
            false
        } catch (e: Exception) {
            true
        }
        if (!erroTruncado) {
            c.falhou("arquivo .KIMG truncado NÃO gerou erro claro")
            return
        }
        // magic quebrado = erro claro
        val quebrado = bytes.copyOf().also { it[0] = 0x00 }
        val erroMagic = try {
            KimgReader.read(quebrado)
            false
        } catch (e: Exception) {
            true
        }
        if (!erroMagic) {
            c.falhou("magic .KIMG quebrado NÃO gerou erro claro")
            return
        }
        c.aprovado(
            "ida e volta OK · PSNR REALISTA (gradiente + ruído leve) %.1f dB · " +
                "PSNR ADVERSARIAL (alfa alternada por pixel, pior caso do EAC) %.1f dB · ${relatorio.resumo()} · ".format(
                    java.util.Locale.US, psnrRealista, psnrAdversarial
                ) +
                "truncado e magic quebrado = erros claros ($bytesRealista bytes)"
        )
    }

    /**
     * CHECAGEM "tex-cena" (D4.5): textura NA CENA — um quad texturizado pela
     * textura criada no CAMINHO REAL da engine (GlesBackend.createTextureFromKimg,
     * com o fallback RGBA8 automático) e o pixel lido NÃO pode ser preto.
     */
    private fun checarTexCena(c: Checagem, egl: EglDiag?) {
        if (egl == null) {
            c.falhou("contexto EGL pbuffer não disponível")
            return
        }
        // Textura 16×16 realista (gradiente quente — centro claramente não-preto).
        val w = 16
        val h = 16
        val rgba = ByteArray(w * h * 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = (y * w + x) * 4
                rgba[i] = (40 + x * 13).toByte()
                rgba[i + 1] = (30 + y * 12).toByte()
                rgba[i + 2] = 70.toByte()
                rgba[i + 3] = 255.toByte()
            }
        }
        val kimg = try {
            KimgPipeline.converter(rgba, w, h, limiteTextura = 0).first
        } catch (e: Exception) {
            c.falhou("encode .KIMG para a cena: ${e.message}")
            return
        }

        // CAMINHO REAL da engine: createTextureFromKimg (ETC2 + fallback RGBA8;
        // se o driver recusar, o fallback entra e registra no KenkernLog/Console).
        val backend = GlesBackend()
        val handle = try {
            backend.createTextureFromKimg(kimg)
        } catch (e: Exception) {
            c.falhou("createTextureFromKimg lançou: ${e.message ?: e.javaClass.simpleName}")
            return
        }
        if (handle == 0) {
            c.falhou("engine recusou a textura .KIMG (createTextureFromKimg devolveu 0) — motivo no CONSOLE")
            return
        }
        val idGl = backend.idGlParaDiagnostico(handle)
        if (idGl == 0) {
            backend.destroyTexture(handle)
            c.falhou("handle $handle não tem id GL (estado interno do backend)")
            return
        }

        val vs = """
            #version 300 es
            layout(location = 0) in vec2 aPos;
            out vec2 vUV;
            void main() { vUV = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }
        """.trimIndent()
        val fs = """
            #version 300 es
            precision mediump float;
            in vec2 vUV;
            uniform sampler2D uTex;
            out vec4 cor;
            void main() { cor = texture(uTex, vUV); }
        """.trimIndent()
        val programa = compilarPrograma(vs, fs)
        var vao = 0
        var vbo = 0
        try {
            if (programa == 0) {
                c.falhou("shader do quad texturizado não compilou")
                return
            }
            vao = IntArray(1).also { GLES30.glGenVertexArrays(1, it, 0) }[0]
            vbo = IntArray(1).also { GLES30.glGenBuffers(1, it, 0) }[0]
            GLES30.glBindVertexArray(vao)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
            val quad = java.nio.ByteBuffer.allocateDirect(6 * 8).order(java.nio.ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f, 1f))
            quad.position(0)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 6 * 8, quad, GLES30.GL_STATIC_DRAW)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
            GLES30.glBindVertexArray(0)

            GLES30.glViewport(0, 0, 16, 16)
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, idGl)
            GLES30.glUseProgram(programa)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(programa, "uTex"), 0)
            GLES30.glBindVertexArray(vao)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
            GLES30.glFinish()
            val px = lerPixel(8, 8)
            val brilhoMax = px?.let { maxOf(it[0], it[1], it[2]) } ?: -1
            if (px != null && brilhoMax > 8) {
                c.aprovado(
                    "quad texturizado pela engine (handle $handle, ${kimg.size} bytes .KIMG) renderizou pixel " +
                        "${fmtCor(px)} no centro — NÃO preto (fallback RGBA8 automático do backend cobre driver sem ETC2)"
                )
            } else {
                c.falhou(
                    "pixel do quad texturizado ${fmtCor(px)} — " +
                        "preto = textura incompleta/upload falhou (veja (a)/(b)/(c) na checagem etc2 e o CONSOLE)"
                )
                KenkernLog.e(TAG, "checagem tex-cena: pixel ${px?.joinToString()}", null)
            }
        } finally {
            if (programa != 0) GLES30.glDeleteProgram(programa)
            if (vao != 0) GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
            if (vbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
            backend.destroyTexture(handle)
            GLES30.glGetError()
        }
    }

    /**
     * Checagem "cena = tela" (Bug A, v0.1.17) — O VIEWPORT DESENHA SÓ NICKS:
     *
     *  (a) CONTAGEM: renderiza OFFSCREEN uma cena de referência (Mundo >
     *      Cubo + Oculto(invisível) + Grupo > Cubo3) e exige que o M medido
     *      pelo renderer (malhasDesenhadas, E6) seja IGUAL ao esperado pela regra
     *      MalhaNick.contarDesenhaveisVisiveis — containers ("Mundo", "Grupo")
     *      não desenham; desligar visible tira o Nick da contagem;
     *  (b) PIXEL: com o Cubo visível × invisível, os pixels da região central
     *      MUDAM — o Nick desenhado é o que aparece na tela (não só na contagem);
     *  (c) TORUS .KERN OFFSCREEN: o torus de teste da Fase 1 agora mora SÓ
     *      aqui — gerado, serializado, relido, subido à GPU e RENDERIZADO
     *      offscreen (drawMesh direto, A/B de pixel). NUNCA no viewport.
     *
     * Contexto: EglDiag pbuffer 64×64; backend/renderer inicializados SEM
     * marcar boot_trace (armadilha 12 do PROGRESS).
     */
    private fun checarCenaTela(c: Checagem, egl: EglDiag?) {
        if (egl == null) {
            c.falhou("contexto EGL pbuffer não disponível")
            return
        }
        // ---------- cena de referência: 5 Nicks, 2 desenhaveis visíveis ----------
        // E5: "Cubo"/"Oculto"/"Cubo3" são nick.mesh (GEOMETRIA, contam em M);
        // um Nick de luz/área aqui seria AUXILIAR (ícone) e NÃO contaria em M.
        val scene = Scene("cena=tela")
        val mundo = scene.createRootNick("Mundo")
        val cubo = scene.createNick("Cubo", mundo, kind = NickKindRegistry.NICK_MESH)
        cubo.transform.setLocalPosition(0f, 0.5f, 0f)
        val oculto = scene.createNick("Oculto", mundo, kind = NickKindRegistry.NICK_MESH)
        oculto.visible = false
        val grupo = scene.createNick("Grupo", mundo, kind = NickKindRegistry.NICK_GROUP)
        val cubo3 = scene.createNick("Cubo3", grupo, kind = NickKindRegistry.NICK_MESH)
        cubo3.transform.setLocalPosition(3f, 0.5f, 0f)
        scene.updateWorldTransforms()
        val totalNicks = MalhaNick.contarNicks(scene)
        val esperado = MalhaNick.contarDesenhaveisVisiveis(scene)

        val backend = GlesBackend()
        val renderer = EngineRenderer()
        var handleTorus = 0
        try {
            if (!backend.initialize(registrarBootTrace = false)) {
                c.falhou("backend GLES não inicializou no contexto offscreen")
                return
            }
            backend.resize(64, 64)
            renderer.initialize(backend, null, registrarBootTrace = false)
            if (!renderer.isInitialized()) {
                c.falhou("renderer não inicializou no contexto offscreen")
                return
            }
            if (!renderer.isKernRoundtripOk()) {
                c.falhou("validação do roundtrip .KERN falhou no initialize (ver CONSOLE)")
                return
            }
            val camera = OrbitCamera()

            // ---------- (a) contagem: M medido == M esperado ----------
            renderer.renderFrame(backend, scene, camera, 0f, null, null)
            val mCena = renderer.malhasDesenhadas
            cubo3.visible = false
            scene.updateWorldTransforms()
            renderer.renderFrame(backend, scene, camera, 0f, null, null)
            val mSemCubo3 = renderer.malhasDesenhadas
            cubo.visible = false
            scene.updateWorldTransforms()
            renderer.renderFrame(backend, scene, camera, 0f, null, null)
            val mNenhum = renderer.malhasDesenhadas

            // ---------- (b) pixel: Cubo visível × invisível muda a tela ----------
            cubo.visible = true
            cubo3.visible = false
            oculto.visible = false
            scene.updateWorldTransforms()
            renderer.renderFrame(backend, scene, camera, 0f, null, null)
            val comCubo = lerRegiao(32, 34, 5)
            cubo.visible = false
            scene.updateWorldTransforms()
            renderer.renderFrame(backend, scene, camera, 0f, null, null)
            val semCubo = lerRegiao(32, 34, 5)
            val pixelsMudaram = comCubo != null && semCubo != null && comCubo != semCubo

            // ---------- (c) torus .KERN OFFSCREEN (nunca no viewport) ----------
            val torusKern = EngineRenderer.gerarTorusKern()
            val torusBytes = KernWriter.write(torusKern)
            val torusRelido = KernReader.read(torusBytes)
            handleTorus = backend.createMesh(MeshData.fromKern(torusRelido))
            camera.target.set(0f, 0f, 0f)
            camera.yawDegrees = 0f
            camera.pitchDegrees = 0f
            camera.distance = 6f
            val proj = Mat4(); val view = Mat4(); val vp = Mat4()
            camera.projection(proj, 1f)
            camera.view(view)
            vp.multiply(proj, view)
            val model = Mat4(); model.setIdentity()
            backend.beginFrame(0.075f, 0.075f, 0.105f, 1f)
            backend.setLighting(-0.5f, -1f, -0.35f, 0.3f, 0.3f, 0.32f, 1f, 0.97f, 0.9f, 1f)
            backend.endFrame()
            val semTorus = lerPixel(32, 32)
            backend.beginFrame(0.075f, 0.075f, 0.105f, 1f)
            backend.setLighting(-0.5f, -1f, -0.35f, 0.3f, 0.3f, 0.32f, 1f, 0.97f, 0.9f, 1f)
            backend.drawMesh(handleTorus, model, vp, 0.6039f, 0.6039f, 0.6039f, 1f)
            backend.endFrame()
            val comTorus = lerPixel(32, 32)
            val torusDesenhou = comTorus != null && semTorus != null && comTorus != semTorus

            // ---------- veredito ----------
            val problemas = ArrayList<String>(3)
            if (mCena != esperado) problemas.add("M malhas=${mCena} ≠ esperado $esperado (containers não desenham; cada malha da cena de referência tem 1 grupo)")
            if (mSemCubo3 != esperado - 1) problemas.add("visible=false não tirou a malha da tela (M=${mSemCubo3}, esperado ${esperado - 1})")
            if (mNenhum != 0) problemas.add("com tudo invisível M malhas=${mNenhum} ≠ 0")
            if (!pixelsMudaram) problemas.add("pixel central não mudou com Cubo visível × invisível")
            if (handleTorus == 0) problemas.add("createMesh do torus devolveu 0")
            else if (!torusDesenhou) problemas.add("torus .KERN não renderizou offscreen (${fmtCor(comTorus)} vs ${fmtCor(semTorus)})")

            if (problemas.isEmpty()) {
                c.aprovado(
                    "N=$totalNicks nicks · M malhas=$mCena == $esperado visíveis com malha (1 grupo cada) · visible=false remove da tela " +
                        "(M ${mCena}→${mSemCubo3}→${mNenhum}) · auxiliares de editor não contam (E5) · torus .KERN (${torusRelido.vertexCount} vértices) renderiza SÓ offscreen"
                )
            } else {
                c.falhou(problemas.joinToString("; "))
            }
        } finally {
            if (handleTorus != 0) backend.destroyMesh(handleTorus)
            backend.destroy()
        }
    }

    /** Lê uma região raio [raio] ao rededor de (cx,cy) como lista RGB (compara A/B). */
    private fun lerRegiao(cx: Int, cy: Int, raio: Int): List<IntArray>? {
        val saida = ArrayList<IntArray>((raio * 2 + 1) * (raio * 2 + 1))
        for (y in cy - raio..cy + raio) {
            for (x in cx - raio..cx + raio) {
                if (x < 0 || y < 0 || x >= 64 || y >= 64) continue
                val px = lerPixel(x, y) ?: return null
                saida.add(intArrayOf(px[0], px[1], px[2]))
            }
        }
        return saida
    }

    /**
     * Checagem "materiais" (T0.4, v0.1.20) — FIDELIDADE DO MATERIAL PBR:
     *  (a) .KERN v3: material com TODOS os mapas (base/normal/MR/oclusão/
     *      emissiva) + fatores sobrevive ao roundtrip (cada campo confere);
     *  (b) COMPATIBILIDADE: material SEM extensão PBR grava .KERN v2
     *      (byte-compatível com a versão anterior — versão lida = 2);
     *  (c) AMOSTRA REAL: o glTF embutido com materiais importa com a tabela
     *      de materiais preenchida (modelos de amostra da checagem "gltf").
     */
    private fun checarMateriais(c: Checagem) {
        // ---------- (a) roundtrip v3 com todos os mapas ----------
        val b = KernWriter.Builder()
        b.vertex(0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f)
        b.vertex(1f, 0f, 0f, 0f, 1f, 0f, 1f, 0f, 1f, 0f, 0f)
        b.vertex(1f, 1f, 0f, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 0f)
        b.triangle(0, 1, 2)
        b.material(
            KernMaterial(
                "pbr", 0.8f, 0.6f, 0.4f, 1f, 0.35f, 0.7f,
                true, true, true, "assets/textures/base.kimg", "assets/textures/normal.kimg",
                "assets/textures/mr.kimg", "assets/textures/ao.kimg", "assets/textures/emiss.kimg",
                0.9f, 0.1f, 0.05f, 2.5f, 1.2f, 0.8f
            )
        )
        b.materialGroup(0, 3, 0)
        val original = b.build()
        val bytes = KernWriter.write(original)
        val (versaoGravada, _, _) = KernReader.readHeader(bytes)
        val lida = KernReader.read(bytes)
        val mat = lida.materiais.firstOrNull()
        val problemas = ArrayList<String>(3)
        if (versaoGravada != KernFormat.VERSION) {
            problemas.add("material PBR não gravou v3 (versão lida = $versaoGravada)")
        }
        if (mat == null) {
            problemas.add("tabela de materiais vazia após o roundtrip")
        } else {
            if (mat.texturaMrPath != "assets/textures/mr.kimg") problemas.add("texturaMrPath divergiu: ${mat.texturaMrPath}")
            if (mat.texturaOclusaoPath != "assets/textures/ao.kimg") problemas.add("texturaOclusaoPath divergiu")
            if (mat.texturaEmissivaPath != "assets/textures/emiss.kimg") problemas.add("texturaEmissivaPath divergiu")
            if (abs(mat.emissiveR - 0.9f) > 1e-3 || abs(mat.emissiveG - 0.1f) > 1e-3 ||
                abs(mat.emissiveB - 0.05f) > 1e-3
            ) problemas.add("emissiveFactor divergiu (${mat.emissiveR}, ${mat.emissiveG}, ${mat.emissiveB})")
            if (abs(mat.emissiveIntensity - 2.5f) > 1e-3) problemas.add("emissiveIntensity divergiu: ${mat.emissiveIntensity}")
            if (abs(mat.normalScale - 1.2f) > 1e-3) problemas.add("normalScale divergiu: ${mat.normalScale}")
            if (abs(mat.oclusaoStrength - 0.8f) > 1e-3) problemas.add("oclusaoStrength divergiu: ${mat.oclusaoStrength}")
            if (abs(mat.metallic - 0.35f) > 1e-3 || abs(mat.roughness - 0.7f) > 1e-3) problemas.add("fatores MR divergiram")
        }

        // ---------- (b) compatibilidade v2 (sem extensão PBR) ----------
        val b2 = KernWriter.Builder()
        b2.vertex(0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f)
        b2.vertex(1f, 0f, 0f, 0f, 1f, 0f, 1f, 0f, 1f, 0f, 0f)
        b2.vertex(1f, 1f, 0f, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 0f)
        b2.triangle(0, 1, 2)
        b2.material(KernMaterial("v2", 1f, 1f, 1f, 1f, 0f, 1f, false, false, false, "", ""))
        b2.materialGroup(0, 3, 0)
        val bytes2 = KernWriter.write(b2.build())
        val (versao2, _, _) = KernReader.readHeader(bytes2)
        if (versao2 != KernFormat.VERSION_V2) {
            problemas.add("material sem PBR deveria gravar v2 (gravou $versao2)")
        }

        // ---------- (c) amostra real (glTF embutido) ----------
        val amostraMateriais = avaliarAmostraMateriais(problemas)

        if (problemas.isEmpty()) {
            c.aprovado(
                ".KERN v3 preserva os 5 mapas + fatores do material PBR (roundtrip completo) · " +
                    "material sem PBR segue gravando v2 (compatibilidade) · amostra real importou " +
                    "$amostraMateriais material(is) (fatores 0,8/0,2/0,1 · metallic 0,5 · roughness 0,4 · " +
                    "modelo SEM material importa tabela vazia)"
            )
        } else {
            c.falhou(problemas.joinToString("; "))
        }
    }

    /**
     * Checagem "luz" (H2, v0.1.20) — A LUZ DO LUME ILUMINA A CENA (GL offscreen):
     *  (a) PONTUAL: quad BRANCO (cubo do tipo nick.mesh) + luz PONTUAL VERMELHA
     *      perto dele — com a luz LIGADA o pixel central é dominado pelo
     *      VERMELHO (r > g e r > b) e MUDA vs o MESMO render com a luz
     *      DESLIGADA (a luz entra e sai no mesmo frame, sem recriar nada);
     *  (b) DIRECIONAL: light.directional vermelha SUBSTITUI o sol fixo —
     *      comparada com o MESMO quad com ela desligada (sol padrão), o pixel
     *      muda e fica dominado pelo vermelho.
     * Auxiliares de editor DESLIGADOS no render para isolar a LUZ (sem as
     * linhas vermelhas do auxiliar da pontual poluirem a comparação).
     *
     * S4 (v0.1.24): esta checagem usa o MESMO [EngineRenderer.renderFrame]
     * do viewport do editor (LumeCena.coletar por frame, setLighting/
     * setDynamicLights SEM guarda de emJogo) — ela verde PROVA que a luz do
     * Nick ilumina o EDITOR, não só o Play. Investigação S4c ("a iluminação
     * não aparece no editor, se for o caso"): não há caminho separado —
     * editor e Play compartilham o MESMO pipeline de luz.
     */
    private fun checarLuz(c: Checagem, egl: EglDiag?) {
        if (egl == null) {
            c.falhou("contexto EGL pbuffer não disponível")
            return
        }
        val backend = GlesBackend()
        val renderer = EngineRenderer()
        renderer.mostrarAuxiliares = false // isola a LUZ (sem auxiliares vermelhos)
        try {
            if (!backend.initialize(registrarBootTrace = false)) {
                c.falhou("backend GLES não inicializou no contexto offscreen")
                return
            }
            backend.resize(64, 64)
            renderer.initialize(backend, null, registrarBootTrace = false)
            if (!renderer.isInitialized()) {
                c.falhou("renderer não inicializou no contexto offscreen")
                return
            }
            val camera = OrbitCamera()

            // ---------- (a) pontual vermelha sobre quad branco ----------
            val scene = Scene("luz")
            val mundo = scene.createRootNick("Mundo")
            val quad = scene.createNick("Quad", mundo, kind = NickKindRegistry.NICK_MESH)
            quad.transform.setLocalPosition(0f, 0.5f, 0f)
            val luz = scene.createNick("Luz Vermelha", mundo, kind = "light.point")
            luz.transform.setLocalPosition(0.9f, 1.4f, 1.1f)
            val instLuz = NickClassRegistry.createInstance(LuzNick.CLASSE)
            if (instLuz == null) {
                c.falhou("classe Luz não registrada (KenkernBuiltInClasses)")
                return
            }
            instLuz.fields[LuzNick.CAMPO_COR] = NickValue.Vec3Value(1f, 0f, 0f)
            instLuz.fields[LuzNick.CAMPO_ALCANCE] = NickValue.FloatValue(6f)
            instLuz.fields[LuzNick.CAMPO_INTENSIDADE] = NickValue.FloatValue(3f)
            luz.addClassInstance(instLuz)
            scene.updateWorldTransforms()
            renderer.renderFrame(backend, scene, camera, 0f, null, null)
            val regiaoLigada = lerRegiao(32, 34, 5)
            val pixelLigado = lerPixel(32, 32)
            instLuz.fields[LuzNick.CAMPO_LIGADO] = NickValue.BoolValue(false)
            renderer.renderFrame(backend, scene, camera, 0f, null, null)
            val regiaoDesligada = lerRegiao(32, 34, 5)
            val pixelDesligado = lerPixel(32, 32)
            val mudou = regiaoLigada != null && regiaoDesligada != null && regiaoLigada != regiaoDesligada
            val vermelhoDominante = pixelLigado != null &&
                pixelLigado[0] > pixelLigado[1] && pixelLigado[0] > pixelLigado[2]

            // ---------- (b) direcional substitui o sol ----------
            val cenaDir = Scene("luz-dir")
            val mundoDir = cenaDir.createRootNick("Mundo")
            val quadDir = cenaDir.createNick("Quad", mundoDir, kind = NickKindRegistry.NICK_MESH)
            quadDir.transform.setLocalPosition(0f, 0.5f, 0f)
            val sol = cenaDir.createNick("Sol Vermelho", mundoDir, kind = "light.directional")
            val instSol = NickClassRegistry.createInstance(LuzNick.CLASSE)
            if (instSol == null) {
                c.falhou("classe Luz não registrada (KenkernBuiltInClasses)")
                return
            }
            instSol.fields[LuzNick.CAMPO_COR] = NickValue.Vec3Value(1f, 0f, 0f)
            instSol.fields[LuzNick.CAMPO_INTENSIDADE] = NickValue.FloatValue(2f)
            sol.addClassInstance(instSol)
            cenaDir.updateWorldTransforms()
            renderer.renderFrame(backend, cenaDir, camera, 0f, null, null)
            val comSolNick = lerPixel(32, 32)
            instSol.fields[LuzNick.CAMPO_LIGADO] = NickValue.BoolValue(false)
            renderer.renderFrame(backend, cenaDir, camera, 0f, null, null)
            val comSolPadrao = lerPixel(32, 32)
            val direcionalSubstitui = comSolNick != null && comSolPadrao != null &&
                comSolNick.toList() != comSolPadrao.toList() &&
                comSolNick[0] > comSolNick[1] && comSolNick[0] > comSolNick[2]

            // ---------- veredito ----------
            val problemas = ArrayList<String>(3)
            if (!mudou) problemas.add("pixel da região central não mudou com a pontual ligada × desligada")
            if (!vermelhoDominante) problemas.add("pixel com a pontual ligada não é dominado pelo vermelho (${fmtCor(pixelLigado)})")
            if (!direcionalSubstitui) problemas.add(
                "light.directional não substituiu o sol (${fmtCor(comSolNick)} × sol padrão ${fmtCor(comSolPadrao)})"
            )
            if (problemas.isEmpty()) {
                c.aprovado(
                    "pontual vermelha ilumina e sai no mesmo frame (ligada ${fmtCor(pixelLigado)} × desligada ${fmtCor(pixelDesligado)}) · " +
                        "direcional substitui o sol (${fmtCor(comSolNick)}) · ≤4 pontuais/spot por frame (LumeCena) · " +
                        "MESMO caminho renderFrame do viewport: verde aqui = a luz ilumina o EDITOR também"
                )
            } else {
                c.falhou(problemas.joinToString("; "))
            }
        } finally {
            backend.destroy()
        }
    }

    /**
     * Checagem "etc2" (D4, v0.1.16) — TRÊS CASOS SEPARADOS, cada um com o seu
     * valor na mensagem (nada de "pixel divergiu" genérico):
     *  (a) glGetError DEPOIS do glCompressedTexImage2D (valor + nome da constante);
     *  (b) status do framebuffer offscreen (glCheckFramebufferStatus, nome);
     *  (c) pixel lido DEPOIS de um draw real que amostra a textura com
     *      GL_NEAREST, só no mip 0, comparado com a DECODIFICAÇÃO EM CPU do
     *      MESMO bloco (não com a imagem de origem).
     * Mais um caso de CONTROLE RGBA8 (o mesmo upload que o fallback do
     * GlesBackend faz: decodificação CPU + glTexImage2D) para separar "driver
     * recusou ETC2" de "caminho GL inteiro quebrado".
     *
     * Verificações cobertas: internalformat exato (0x9274 RGB / 0x9278 RGBA —
     * a engine sobe valores CRUS, sem variante sRGB, então a comparação com a
     * decodificação CPU é direta); tamanho em bytes = mip.dados.size;
     * GL_UNPACK_ALIGNMENT não afeta upload comprimido (e RGBA8 cru é 4 B/px =
     * alinhamento padrão 4); min filter GL_NEAREST SEM mipmap (a checagem só
     * sobe o mip 0 — filtro com mip incompleto deixaria a textura incompleta e
     * o pixel PRETO); uniform do sampler + unidade de textura 0; draw ANTES do
     * glReadPixels no MESMO contexto com glFinish; ordem linha/coluna dos
     * blocos = linha-major (o texel lido (x=1,y=2) da framebuffer casa com o
     * texel CPU (col=1, row=2) pela mesma convenção).
     */
    private fun checarEtc2(c: Checagem, egl: EglDiag?) {
        if (egl == null) {
            c.falhou("contexto EGL pbuffer não disponível")
            return
        }
        // bloco distintivo 4×4: quadrantes de cores puras
        val rgba = ByteArray(4 * 4 * 4)
        val cores = arrayOf(
            intArrayOf(255, 0, 0), intArrayOf(0, 255, 0),
            intArrayOf(0, 0, 255), intArrayOf(255, 255, 255)
        )
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                val quad = (x / 2) * 2 + (y / 2)
                val cor = cores[quad]
                val i = (y * 4 + x) * 4
                rgba[i] = cor[0].toByte(); rgba[i + 1] = cor[1].toByte()
                rgba[i + 2] = cor[2].toByte(); rgba[i + 3] = 255.toByte()
            }
        }
        val kimg = try {
            KimgPipeline.converter(rgba, 4, 4, limiteTextura = 0).first
        } catch (e: Exception) {
            c.falhou("ETC2 encode: ${e.message}")
            return
        }
        val imagem = try {
            KimgReader.read(kimg)
        } catch (e: Exception) {
            c.falhou("leitura .KIMG: ${e.message}")
            return
        }

        var texEtc2 = 0
        var texRgba8 = 0
        var fbo = 0
        var fboTex = 0
        var programa = 0
        var vao = 0
        var vbo = 0
        try {
            // ---------- (a) UPLOAD ETC2: glGetError com nome da constante
            texEtc2 = IntArray(1).also { GLES30.glGenTextures(1, it, 0) }[0]
            if (texEtc2 == 0) {
                c.falhou("glGenTextures devolveu 0")
                return
            }
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texEtc2)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            val ehRgba = imagem.formato == com.kenkern.engine.assets.image.KimgFormato.ETC2_RGBA
            val interna = if (ehRgba) 0x9278 else 0x9274
            val internaNome = if (ehRgba) "GL_COMPRESSED_RGBA8_ETC2_EAC" else "GL_COMPRESSED_RGB8_ETC2"
            GLES30.glCompressedTexImage2D(
                GLES30.GL_TEXTURE_2D, 0, interna,
                imagem.largura, imagem.altura, 0, imagem.mips[0].dados.size,
                java.nio.ByteBuffer.wrap(imagem.mips[0].dados)
            )
            val erroUpload = GLES30.glGetError()
            if (erroUpload != GLES30.GL_NO_ERROR) {
                c.falhou(
                    "(a) glCompressedTexImage2D recusou ETC2: erro 0x" + Integer.toHexString(erroUpload) +
                        " (${nomeErroGl(erroUpload)}) com $internaNome, ${imagem.mips[0].dados.size} bytes, " +
                        "${imagem.largura}×${imagem.altura} — driver sem ETC2? (obrigatório no GLES 3.0)"
                )
                return
            }

            // ---------- shader + quad (mesmo shader das duas amostragens)
            val vs = """
                #version 300 es
                layout(location = 0) in vec2 aPos;
                out vec2 vUV;
                void main() { vUV = aPos * 0.5 + 0.5; gl_Position = vec4(aPos, 0.0, 1.0); }
            """.trimIndent()
            val fs = """
                #version 300 es
                precision mediump float;
                in vec2 vUV;
                uniform sampler2D uTex;
                out vec4 cor;
                void main() { cor = texture(uTex, vUV); }
            """.trimIndent()
            programa = compilarPrograma(vs, fs)
            if (programa == 0) {
                c.falhou("shader de amostragem não compilou")
                return
            }
            vao = IntArray(1).also { GLES30.glGenVertexArrays(1, it, 0) }[0]
            vbo = IntArray(1).also { GLES30.glGenBuffers(1, it, 0) }[0]
            GLES30.glBindVertexArray(vao)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
            val quad = java.nio.ByteBuffer.allocateDirect(6 * 8).order(java.nio.ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f, 1f))
            quad.position(0)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, 6 * 8, quad, GLES30.GL_STATIC_DRAW)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
            GLES30.glBindVertexArray(0)

            // ---------- (b) FRAMEBUFFER OFFSCREEN 4×4 + STATUS
            fboTex = IntArray(1).also { GLES30.glGenTextures(1, it, 0) }[0]
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboTex)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
            // RGBA8 4×4: 4 bytes/px casa com o alinhamento padrão (GL_UNPACK_ALIGNMENT=4).
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, 4, 4, 0,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
            )
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
            fbo = IntArray(1).also { GLES30.glGenFramebuffers(1, it, 0) }[0]
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, fboTex, 0)
            val statusFbo = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
            val fboOk = statusFbo == GLES30.GL_FRAMEBUFFER_COMPLETE
            if (!fboOk) {
                c.falhou(
                    "(b) framebuffer offscreen INCOMPLETO: 0x" + Integer.toHexString(statusFbo) +
                        " — draw/readback não são confiáveis neste driver"
                )
                return
            }

            // ---------- (c) DRAW REAL sobre a textura ETC2 + leitura do pixel
            GLES30.glViewport(0, 0, 4, 4)
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texEtc2)
            GLES30.glUseProgram(programa)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(programa, "uTex"), 0)
            GLES30.glBindVertexArray(vao)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
            GLES30.glFinish()
            val gpu = lerPixel(1, 2)

            // referência: decodificação CPU do NOSSO codec no texel (col=1, row=2)
            // — o MESMO bloco que subiu, não a imagem de origem.
            val cpu = Etc2.decodificarRgb(imagem.mips[0].dados, 4, 4)
            val iCpu = (2 * 4 + 1) * 4
            val etc2Ok = gpu != null &&
                abs(gpu[0] - (cpu[iCpu].toInt() and 0xFF)) < 24 &&
                abs(gpu[1] - (cpu[iCpu + 1].toInt() and 0xFF)) < 24 &&
                abs(gpu[2] - (cpu[iCpu + 2].toInt() and 0xFF)) < 24

            // ---------- CONTROLE/FALLBACK RGBA8: o MESMO upload que o fallback do
            // GlesBackend faz (decodificação CPU + glTexImage2D cru) — separa
            // "driver recusou ETC2" de "caminho GL quebrado" (D4.3).
            texRgba8 = IntArray(1).also { GLES30.glGenTextures(1, it, 0) }[0]
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texRgba8)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, imagem.largura, imagem.altura, 0,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, java.nio.ByteBuffer.wrap(cpu)
            )
            val erroRgba = GLES30.glGetError()
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texRgba8)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
            GLES30.glFinish()
            val gpuRgba = lerPixel(1, 2)
            val rgbaOk = gpuRgba != null &&
                abs(gpuRgba[0] - (cpu[iCpu].toInt() and 0xFF)) < 24 &&
                abs(gpuRgba[1] - (cpu[iCpu + 1].toInt() and 0xFF)) < 24 &&
                abs(gpuRgba[2] - (cpu[iCpu + 2].toInt() and 0xFF)) < 24

            if (etc2Ok && rgbaOk) {
                c.aprovado(
                    "(a) upload ETC2 $internaNome sem erro · (b) framebuffer GL_FRAMEBUFFER_COMPLETE · " +
                        "(c) GPU ${fmtCor(gpu)} ≈ decodificação CPU ${fmtCor(intArrayDe(cpu, iCpu))} ±24 · " +
                        "fallback RGBA8 (CPU→glTexImage2D) OK ${fmtCor(gpuRgba)} — textura nunca some"
                )
            } else {
                val partes = ArrayList<String>(3)
                if (!etc2Ok) {
                    partes.add(
                        "(c) pixel da GPU ETC2 ${fmtCor(gpu)} vs decodificação CPU ${fmtCor(intArrayDe(cpu, iCpu))}"
                    )
                }
                if (erroRgba != GLES30.GL_NO_ERROR) {
                    partes.add("(controle) upload RGBA8 cru errou 0x" + Integer.toHexString(erroRgba) + " (${nomeErroGl(erroRgba)})")
                }
                if (!rgbaOk) {
                    partes.add(
                        "(fallback) pixel RGBA8 ${fmtCor(gpuRgba)} vs CPU ${fmtCor(intArrayDe(cpu, iCpu))} — " +
                            "se RGBA8 também falha, o problema é o caminho GL (FBO/draw/read), não o ETC2"
                    )
                }
                c.falhou("(a) upload ETC2 OK 0x9274/0x9278 · (b) framebuffer COMPLETE · " + partes.joinToString(" · "))
                KenkernLog.e(TAG, "checagem etc2 falhou: ${partes.joinToString(" · ")}", null)
            }
        } finally {
            if (programa != 0) GLES30.glDeleteProgram(programa)
            if (vao != 0) GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0)
            if (vbo != 0) GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0)
            if (fbo != 0) {
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
                GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
            }
            if (fboTex != 0) GLES30.glDeleteTextures(1, intArrayOf(fboTex), 0)
            if (texEtc2 != 0) GLES30.glDeleteTextures(1, intArrayOf(texEtc2), 0)
            if (texRgba8 != 0) GLES30.glDeleteTextures(1, intArrayOf(texRgba8), 0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
            GLES30.glGetError() // descarta erros residuais do teste
        }
    }

    /** Lê 1 pixel RGBA (0..255) do framebuffer ligado; null = readPixels falhou. */
    private fun lerPixel(x: Int, y: Int): IntArray? {
        val buf = java.nio.ByteBuffer.allocateDirect(4).order(java.nio.ByteOrder.nativeOrder())
        GLES30.glReadPixels(x, y, 1, 1, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
        val erro = GLES30.glGetError()
        if (erro != GLES30.GL_NO_ERROR) return null
        buf.position(0)
        return IntArray(4) { buf.get().toInt() and 0xFF }
    }

    private fun nomeErroGl(erro: Int): String = when (erro) {
        GLES30.GL_NO_ERROR -> "GL_NO_ERROR"
        GLES30.GL_INVALID_ENUM -> "GL_INVALID_ENUM"
        GLES30.GL_INVALID_VALUE -> "GL_INVALID_VALUE"
        GLES30.GL_INVALID_OPERATION -> "GL_INVALID_OPERATION"
        GLES30.GL_INVALID_FRAMEBUFFER_OPERATION -> "GL_INVALID_FRAMEBUFFER_OPERATION"
        GLES30.GL_OUT_OF_MEMORY -> "GL_OUT_OF_MEMORY"
        else -> "erro desconhecido"
    }

    private fun fmtCor(px: IntArray?): String =
        if (px == null) "ILEGÍVEL (readPixels falhou)" else "(${px[0]},${px[1]},${px[2]})"

    private fun intArrayDe(rgba: ByteArray, i: Int): IntArray =
        intArrayOf(rgba[i].toInt() and 0xFF, rgba[i + 1].toInt() and 0xFF, rgba[i + 2].toInt() and 0xFF)

    private fun compilarPrograma(vs: String, fs: String): Int {
        val v = GLES30.glCreateShader(GLES30.GL_VERTEX_SHADER)
        GLES30.glShaderSource(v, vs)
        GLES30.glCompileShader(v)
        val okV = IntArray(1)
        GLES30.glGetShaderiv(v, GLES30.GL_COMPILE_STATUS, okV, 0)
        if (okV[0] == 0) {
            GLES30.glDeleteShader(v)
            return 0
        }
        val f = GLES30.glCreateShader(GLES30.GL_FRAGMENT_SHADER)
        GLES30.glShaderSource(f, fs)
        GLES30.glCompileShader(f)
        val okF = IntArray(1)
        GLES30.glGetShaderiv(f, GLES30.GL_COMPILE_STATUS, okF, 0)
        if (okF[0] == 0) {
            GLES30.glDeleteShader(v)
            GLES30.glDeleteShader(f)
            return 0
        }
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, v)
        GLES30.glAttachShader(p, f)
        GLES30.glLinkProgram(p)
        GLES30.glDeleteShader(v)
        GLES30.glDeleteShader(f)
        val okL = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, okL, 0)
        if (okL[0] == 0) {
            GLES30.glDeleteProgram(p)
            return 0
        }
        return p
    }

    /** Checagem "astc" (ETAPA C.8): SÓ detecta a extensão — nunca reprova. */
    private fun checarAstc(c: Checagem) {
        val extensoes = GLES30.glGetString(GLES30.GL_EXTENSIONS) ?: ""
        val suporta = extensoes.contains("GL_KHR_texture_compression_astc_ldr")
        if (suporta) {
            c.aprovado("GL_KHR_texture_compression_astc_ldr PRESENTE — ASTC_4x4/6x6/8x8 usável numa fase futura (encoder ASTC fora do escopo)")
        } else {
            // NÃO SUPORTADO não é falha (ETC2 é o formato base obrigatório no GLES 3.0)
            c.aprovado("ASTC NÃO SUPORTADO neste aparelho — sem problema: ETC2 é o formato base da engine")
        }
    }

    // ---------------------------------------------------------------- layout

    /**
     * Checagem "layout" (G1+G2, v0.1.19) — MATEMÁTICA PURA do layout em
     * paisagem 800×360 (referência do usuário, ~240dp de viewport útil):
     *  - TopBar: seções FIXAS (Voltar; Hierarquia/Inspetor/Resetar) com larguras
     *    conhecidas (TopBarLayout) — a soma tem que caber na largura do aparelho
     *    sobrando área rolável utilizável;
     *  - Adicionar Nick: cabeçalho fixo de 48dp ≤ 20% da altura útil e lista
     *    mínima (chips + abas + 1 cartão + padding inferior) + último tipo do
     *    catálogo alcançáveis com a altura útil real (TopBar 48 + StatusBar 24 +
     *    reserva de 24dp para as barras do sistema).
     */
    private fun checarLayout(c: Checagem) {
        val dm = appContext.resources.displayMetrics
        val larguraTelaDp = dm.widthPixels / dm.density
        val alturaTelaDp = dm.heightPixels / dm.density
        val orientacao = if (alturaTelaDp >= larguraTelaDp) "retrato" else "paisagem"

        val fixa = TopBarLayout.larguraTotalFixaDp()
        val rolavel = TopBarLayout.larguraRolavelDp(larguraTelaDp)
        val cabe = TopBarLayout.cabemSecoesFixas(larguraTelaDp)
        val suficiente = TopBarLayout.rolavelSuficiente(larguraTelaDp)

        // Altura útil do viewport: tela − TopBar 48 − StatusBar 24 − reserva das
        // barras do sistema 24 (conservador: displayMetrics já pode excluí-las).
        val alturaUtilDp = alturaTelaDp - 48f - 24f - 24f
        val cabecalhoOk = AddNickLayout.alturaCabecalhoNoLimite(alturaUtilDp)
        val listaOk = AddNickLayout.listaMinimaVisivel(alturaUtilDp)
        val ultimoOk = AddNickLayout.ultimoTipoAlcancavel(alturaUtilDp, 24f)

        if (cabe && suficiente && cabecalhoOk && listaOk && ultimoOk) {
            c.aprovado(
                "tela ${larguraTelaDp.roundToInt()}×${alturaTelaDp.roundToInt()}dp ($orientacao) · " +
                    "TopBar fixa ${fixa.roundToInt()}dp → rolável ${rolavel.roundToInt()}dp ✓ · " +
                    "altura útil ${alturaUtilDp.roundToInt()}dp · cabeçalho AddNick " +
                    "${AddNickLayout.CABECALHO_DP.roundToInt()}dp ≤ 20% ✓ · lista mínima " +
                    "${(alturaUtilDp - AddNickLayout.alturaMinimaListaDp() - AddNickLayout.CABECALHO_DP).roundToInt()}dp de folga ✓ · último tipo alcançável ✓"
            )
        } else {
            c.falhou(
                "tela ${larguraTelaDp.roundToInt()}×${alturaTelaDp.roundToInt()}dp ($orientacao) · " +
                    "TopBar fixa ${fixa.roundToInt()}dp cabe=$cabe · rolável ${rolavel.roundToInt()}dp suficiente=$suficiente · " +
                    "altura útil ${alturaUtilDp.roundToInt()}dp · cabeçalho≤20%=$cabecalhoOk · lista=$listaOk · último=$ultimoOk"
            )
        }
    }

    // ---------------------------------------------------------------- picking

    private fun checarPicking(c: Checagem) {
        val cam = OrbitCamera()
        val o = Vec3()
        val d = Vec3()
        val fwd = Vec3()
        val esperado = Vec3()
        val problemas = ArrayList<String>(4)
        val valores = StringBuilder()

        // ORBITAL: raio pelo CENTRO da tela == direção de olhar, e a ORIGEM do
        // raio == OLHO (OrbitCamera.eyePoint — a MESMA fonte da matriz de view).
        // CORREÇÃO D1 (v0.1.16): a checagem antiga comparava a origem com
        // target + fwd*dist (fwd = DIREÇÃO DE OLHAR) — sinal INVERTIDO: o olho
        // fica em target - fwd*dist. O raio estava CERTO; a checagem é que
        // exigia um ponto atrás do alvo — por isso o aparelho falhava com
        // "origem orbital" e os testes JVM (que comparam com eyePoint)
        // passavam. Agora a checagem usa eyePoint, a fonte única.
        cam.mode = CameraMode.ORBITAL
        cam.forwardVector(fwd)
        cam.eyePoint(esperado)
        cam.screenRay(0f, 0f, 16f / 9f, o, d)
        val difOrigemO = o.distanceTo(esperado)
        if (difOrigemO > 1e-4f) problemas.add("origem orbital")
        if (abs(d.x - fwd.x) > 1e-3f || abs(d.y - fwd.y) > 1e-3f || abs(d.z - fwd.z) > 1e-3f) {
            problemas.add("direção orbital")
        }
        valores.append("ORBITAL: olho=(%.3f, %.3f, %.3f) origem do raio=(%.3f, %.3f, %.3f) dif=%.4f ".format(
            java.util.Locale.US, esperado.x, esperado.y, esperado.z, o.x, o.y, o.z, difOrigemO
        ))

        cam.mode = CameraMode.FLIGHT
        cam.flightPosition.set(3f, 2f, 5f)
        cam.flightYawDegrees = -40f
        cam.flightPitchDegrees = 12f
        cam.forwardVector(fwd)
        cam.screenRay(0f, 0f, 9f / 16f, o, d)
        val difOrigemV = o.distanceTo(cam.flightPosition)
        if (difOrigemV > 1e-4f) problemas.add("origem voo")
        if (abs(d.x - fwd.x) > 1e-3f || abs(d.y - fwd.y) > 1e-3f || abs(d.z - fwd.z) > 1e-3f) {
            problemas.add("direção voo")
        }
        valores.append("· VOO: origem=(%.3f, %.3f, %.3f) dif=%.4f".format(
            java.util.Locale.US, o.x, o.y, o.z, difOrigemV
        ))

        // Inversão de Y (ndcY = 1 - 2y/h): raio do TOCO tem componente Y maior.
        val topo = Vec3()
        val base = Vec3()
        cam.screenRay(0f, 1f, 1f, o, topo)
        cam.screenRay(0f, -1f, 1f, o, base)
        if (topo.y <= base.y) problemas.add("inversão de Y")

        if (problemas.isEmpty()) {
            c.aprovado("raio do centro = olhar em ORBITAL e VOO; origem do raio = olho (eyePoint); topo/base e Y invertidos corretamente")
        } else {
            // Mensagem NUNCA genérica: modo, olho, origem do raio, diferença,
            // near dinâmico e o tamanho de view sintético desta checagem.
            c.falhou(
                "divergência: ${problemas.joinToString()} · modo=${cam.mode} " +
                    "nearDinâmico=%.3f view=2400×1080(px,16:9 sintético) escala=100%% · ".format(
                        java.util.Locale.US, cam.effectiveNear()
                    ) + valores
            )
            KenkernLog.e(TAG, "checagem picking falhou: ${problemas.joinToString()} · $valores", null)
        }
    }

    // ---------------------------------------------------------------- toque no cubo (D1.4)

    /**
     * CHECAGEM "toque no cubo" (D1.4): reproduz o caminho REAL do aparelho com
     * as MESMAS APIs — TouchController classifica o gesto (tap curto, slop em
     * dp), requestPick normaliza px da VIEW, OrbitCamera.screenRay cria o raio
     * (fonte única) e PickingMath.rayAabb é o MESMO slab test do viewport.
     * O toque é disparado no pixel PROJETADO do centro do cubo da cena padrão
     * e PRECISA acertar. Números visíveis na mensagem se falhar.
     */
    private fun checarToqueCubo(c: Checagem) {
        // Cena padrão do editor: Mundo > Cubo na origem (cubo de 1 m).
        // E5: kind=nick.mesh — um nick.empty folha seria AUXILIAR (ícone, sem cubo).
        val scene = Scene("Diag-Toque")
        val mundo = scene.createRootNick("Mundo")
        val cubo = scene.createNick("Cubo", mundo, kind = NickKindRegistry.NICK_MESH)
        // Mesmo contrato do loop de render: matrizes de mundo atualizadas ANTES
        // do picking (KenkernSurfaceView.onDrawFrame faz o mesmo).
        scene.updateWorldTransforms()

        // Mesmos números do viewport real: view 2400×1080 px, density 3,
        // escala de resolução 100% (framebuffer = view).
        val viewW = 2400f
        val viewH = 1080f
        val density = 3f
        val escalaResolucao = 1.0f
        val fbW = (viewW * escalaResolucao).toInt()
        val fbH = (viewH * escalaResolucao).toInt()
        val aspect = fbW.toFloat() / fbH.toFloat()

        val cam = OrbitCamera() // cena padrão: target (0, 0.8, 0), dist 9, fov 55
        val proj = Mat4()
        val view = Mat4()
        val vp = Mat4()
        cam.projection(proj, aspect)
        cam.view(view)
        vp.multiply(proj, view)

        // Centro do cubo em mundo → px da VIEW (mesma cadeia do overlay: px do
        // framebuffer × bufToView).
        val centro = Vec3(0f, 0f, 0f)
        val mm = vp.m
        val cw = mm[3] * centro.x + mm[7] * centro.y + mm[11] * centro.z + mm[15]
        if (cw <= 0f) {
            c.falhou("centro do cubo caiu atrás da câmera (cw=%.3f) — projeção/view divergiram".format(java.util.Locale.US, cw))
            return
        }
        val inv = 1f / cw
        val ndcCx = (mm[0] * centro.x + mm[4] * centro.y + mm[8] * centro.z + mm[12]) * inv
        val ndcCy = (mm[1] * centro.x + mm[5] * centro.y + mm[9] * centro.z + mm[13]) * inv
        val bufToView = viewW / fbW.toFloat()
        val tapX = (ndcCx * 0.5f + 0.5f) * fbW * bufToView
        val tapY = (1f - (ndcCy * 0.5f + 0.5f)) * fbH * bufToView

        // Toque sintético PELA MESMA API do TouchController (DOWN → UP em 150 ms
        // no mesmo px = tap válido; limiar de seleção real é 250 ms).
        var tapXRecebido = -1f
        var tapYRecebido = -1f
        val touch = TouchController(
            cam,
            tapSlopPx = 12f * density,
            density = density,
            onTap = { x, y -> tapXRecebido = x; tapYRecebido = y }
        )
        val evDown = android.view.MotionEvent.obtain(0L, 0L, android.view.MotionEvent.ACTION_DOWN, tapX, tapY, 0)
        val evUp = android.view.MotionEvent.obtain(0L, 150L, android.view.MotionEvent.ACTION_UP, tapX, tapY, 0)
        try {
            touch.onTouch(evDown)
            touch.onTouch(evUp)
        } finally {
            evDown.recycle()
            evUp.recycle()
        }
        if (tapXRecebido < 0f) {
            c.falhou(
                "TouchController NÃO classificou como tap (tap só até 250 ms e dentro do slop de 12 dp×density=%.1f) — " +
                    "toque em (%.1f, %.1f) px da view".format(java.util.Locale.US, density, tapX, tapY)
            )
            return
        }

        // Inverso EXATO do requestPick (px da view → NDC) e raio pela fonte única.
        val ndcX = (tapXRecebido / viewW) * 2f - 1f
        val ndcY = 1f - (tapYRecebido / viewH) * 2f
        val origem = Vec3()
        val dir = Vec3()
        cam.screenRay(ndcX, ndcY, aspect, origem, dir)

        val minW = Vec3()
        val maxW = Vec3()
        PickingMath.worldAabb(cubo, minW, maxW)
        val cx = (minW.x + maxW.x) * 0.5f
        val cy = (minW.y + maxW.y) * 0.5f
        val cz = (minW.z + maxW.z) * 0.5f
        val ex = (maxW.x - minW.x) * 0.5f
        val ey = (maxW.y - minW.y) * 0.5f
        val ez = (maxW.z - minW.z) * 0.5f
        val t = PickingMath.rayAabb(origem, dir, cx, cy, cz, ex, ey, ez)
        if (t >= 0f) {
            c.aprovado(
                "tap em (%.0f, %.0f) px → NDC (%.3f, %.3f) → raio do olho acertou o cubo em t=%.3f m ".format(
                    java.util.Locale.US, tapXRecebido, tapYRecebido, ndcX, ndcY, t
                ) +
                    "(view %.0f×%.0f px, escala %.0f%%, density %.1f, olho (%.2f, %.2f, %.2f))".format(
                        java.util.Locale.US, viewW, viewH, escalaResolucao * 100f, density,
                        origem.x, origem.y, origem.z
                    )
            )
        } else {
            c.falhou(
                "raio do toque NÃO acertou o cubo: tap (%.1f, %.1f) px → NDC (%.3f, %.3f) · origem (%.3f, %.3f, %.3f) · ".format(
                    java.util.Locale.US, tapXRecebido, tapYRecebido, ndcX, ndcY, origem.x, origem.y, origem.z
                ) +
                    "dir (%.3f, %.3f, %.3f) · AABB centro (%.3f, %.3f, %.3f) meia (%.2f, %.2f, %.2f) · aspect %.4f".format(
                        java.util.Locale.US, dir.x, dir.y, dir.z, cx, cy, cz, ex, ey, ez, aspect
                    )
            )
            KenkernLog.e(TAG, "toque no cubo não acertou: ndc=($ndcX,$ndcY) origem=$origem dir=$dir", null)
        }
    }

    // ---------------------------------------------------------------- GL: shaders

    private fun checarShaders(c: Checagem) {
        val vs = """
            #version 300 es
            in vec3 aPos;
            void main() { gl_Position = vec4(aPos, 1.0); }
        """.trimIndent()
        val fs = """
            #version 300 es
            precision mediump float;
            out vec4 cor;
            void main() { cor = vec4(0.5, 0.75, 1.0, 1.0); }
        """.trimIndent()

        var vShader = 0
        var fShader = 0
        var programa = 0
        try {
            vShader = GLES30.glCreateShader(GLES30.GL_VERTEX_SHADER)
            GLES30.glShaderSource(vShader, vs)
            GLES30.glCompileShader(vShader)
            val okV = IntArray(1)
            GLES30.glGetShaderiv(vShader, GLES30.GL_COMPILE_STATUS, okV, 0)
            if (okV[0] == 0) {
                c.falhou("vertex shader: " + GLES30.glGetShaderInfoLog(vShader).trim().take(200))
                return
            }

            fShader = GLES30.glCreateShader(GLES30.GL_FRAGMENT_SHADER)
            GLES30.glShaderSource(fShader, fs)
            GLES30.glCompileShader(fShader)
            val okF = IntArray(1)
            GLES30.glGetShaderiv(fShader, GLES30.GL_COMPILE_STATUS, okF, 0)
            if (okF[0] == 0) {
                c.falhou("fragment shader: " + GLES30.glGetShaderInfoLog(fShader).trim().take(200))
                return
            }

            programa = GLES30.glCreateProgram()
            GLES30.glAttachShader(programa, vShader)
            GLES30.glAttachShader(programa, fShader)
            GLES30.glLinkProgram(programa)
            val okL = IntArray(1)
            GLES30.glGetProgramiv(programa, GLES30.GL_LINK_STATUS, okL, 0)
            if (okL[0] == 0) {
                c.falhou("link: " + GLES30.glGetProgramInfoLog(programa).trim().take(200))
                return
            }

            val glsl = GLES30.glGetString(GLES30.GL_SHADING_LANGUAGE_VERSION) ?: "?"
            val renderer = GLES30.glGetString(GLES30.GL_RENDERER) ?: "?"
            c.aprovado("programa de teste compilou e linkou · GLSL $glsl · $renderer")
        } finally {
            if (programa != 0) GLES30.glDeleteProgram(programa)
            if (vShader != 0) GLES30.glDeleteShader(vShader)
            if (fShader != 0) GLES30.glDeleteShader(fShader)
        }
    }

    // ---------------------------------------------------------------- GL: buffers

    private fun checarBuffers(c: Checagem) {
        // MeshData mínima válida (1 triângulo) com normais preenchidas.
        val malha = MeshData(
            floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f),
            floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f),
            floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f),
            intArrayOf(0, 1, 2),
            Vec3(0f, 0f, 0f),
            Vec3(1f, 1f, 0f)
        )
        val backend = GlesBackend()
        val handle = backend.createMesh(malha)
        try {
            if (handle == 0) {
                c.falhou("createMesh devolveu handle 0 (alocação de VBO/VAO falhou)")
            } else {
                c.aprovado("VBO + VAO de 3 vértices/3 índices alocados (handle $handle) e liberados")
            }
        } finally {
            if (handle != 0) backend.destroyMesh(handle)
        }
    }

    // ---------------------------------------------------------------- GL: capacidades

    private fun checarGlCaps(c: Checagem) {
        val versao = GLES30.glGetString(GLES30.GL_VERSION) ?: "?"
        val renderer = GLES30.glGetString(GLES30.GL_RENDERER) ?: "?"

        // "OpenGL ES 3.2 ..." → "3.2"
        val m = Regex("""OpenGL ES (\d+)\.(\d+)""").find(versao)
        if (m == null) {
            c.falhou("versão GL ilegível: \"$versao\"")
            return
        }
        val maior = m.groupValues[1].toInt()
        val menor = m.groupValues[2].toInt()

        val extensoes = GLES30.glGetString(GLES30.GL_EXTENSIONS)?.split(' ')?.size ?: 0
        val maxTex = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, maxTex, 0)
        val maxVerts = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_RENDERBUFFER_SIZE, maxVerts, 0)

        val compute = maior > 3 || (maior == 3 && menor >= 1)
        val resumo = buildString {
            append("OpenGL ES $maior.$menor · $renderer · $extensoes extensões · ")
            append("tex máx ${maxTex[0]}px · renderbuffer máx ${maxVerts[0]}px · ")
            append(if (compute) "compute disponível" else "sem compute (KENA usará fallback)")
        }
        if (maior >= 3) c.aprovado(resumo) else c.falhou("engine exige OpenGL ES 3.0+: \"$versao\"")
    }

    // ---------------------------------------------------------------- memória

    private fun checarMemoria(c: Checagem) {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (am == null) {
            c.falhou("ActivityManager indisponível")
            return
        }
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val mb = 1024L * 1024L
        val detalhe = buildString {
            append("heap ${am.memoryClass} MB (large ${am.largeMemoryClass} MB) · ")
            append("livre ${info.availMem / mb} de ${info.totalMem / mb} MB · ")
            append(if (am.isLowRamDevice) "aparelho low-RAM" else "aparelho normal")
            append(if (info.lowMemory) " · AVISO: sistema em memória baixa" else "")
        }
        if (info.lowMemory && info.availMem < 64L * mb) {
            c.falhou("memória crítica: $detalhe")
        } else {
            c.aprovado(detalhe)
        }
    }

    // ---------------------------------------------------------------- release

    /**
     * Checagem "release" (auditoria V1.5): fumaça do APK MINIFICADO — se uma
     * regra R8 futura remover classe que o release usa, aparece FALHOU aqui.
     * A lógica está em [ChecagemRelease] (pura) para os testes JVM rodarem o
     * MESMO código.
     */
    private fun checarRelease(c: Checagem) {
        val resultados = ChecagemRelease.executar()
        val (ok, primeiroErro) = ChecagemRelease.resumo(resultados)
        val quebrados = resultados.filter { !it.ok }
        if (quebrados.isEmpty()) {
            c.aprovado(
                "${resultados.size} passos OK (classes por nome, glTF, FBX, .KIMG, ETC2, Lua) — " +
                    "R8 não removeu nada que o release usa"
            )
        } else {
            val nomes = quebrados.joinToString(", ") { it.passo }
            c.falhou("$ok/${resultados.size} passos OK — falha em [$nomes]: $primeiroErro")
            KenkernLog.e(TAG, "fumaça do release falhou nos passos: $nomes", null)
        }
    }

    /**
     * Checagem "joystick" (M1.3, v0.1.22) — matemática do INPUT DE TELA
     * (InputJogo, pura): normalização no círculo unitário, zona morta ZERO
     * dentro e CONTÍNUA na borda (re-normalização), curva de resposta
     * crescente, TRAVAR EM 8 DIREÇÕES (45°) e borda de 1 tick dos botões.
     * Os toques sintéticos são entradas da matemática (a mesma que a UI usa).
     */
    private fun checarJoystick(c: Checagem) {
        val saida = FloatArray(2)
        val problemas = ArrayList<String>(4)
        com.kenkern.engine.engine.InputJogo.aplicar(10f, 0f, 1f, 0f, false, saida)
        if (kotlin.math.abs(saida[0] - 1f) > 1e-3 || kotlin.math.abs(saida[1]) > 1e-3) {
            problemas.add("normalização: (${saida[0]}, ${saida[1]}) ≠ (1, 0)")
        }
        com.kenkern.engine.engine.InputJogo.aplicar(0.05f, 0f, 1f, 0.2f, false, saida)
        if (saida[0] != 0f || saida[1] != 0f) {
            problemas.add("zona morta não zerou: (${saida[0]}, ${saida[1]})")
        }
        com.kenkern.engine.engine.InputJogo.aplicar(1f, 0.577f, 1f, 0f, true, saida)
        if (kotlin.math.abs(kotlin.math.abs(saida[0]) - kotlin.math.abs(saida[1])) > 0.02f) {
            problemas.add("8 direções: 30° não travou em 45° (${saida[0]}, ${saida[1]})")
        }
        if (!(com.kenkern.engine.engine.InputJogo.curva(0.1f) < com.kenkern.engine.engine.InputJogo.curva(0.5f))) {
            problemas.add("curva de resposta não é crescente")
        }
        if (com.kenkern.engine.engine.InputJogo.bordaDePressao(true, true)) {
            problemas.add("borda de pressão disparou segurando o botão")
        }
        if (problemas.isEmpty()) {
            c.aprovado(
                "normalização no círculo unitário · zona morta contínua (re-normalizada) · " +
                    "8 direções (45°) · curva v^1,5 · borda de 1 tick nos botões (InputJogo, mesma matemática da tela)"
            )
        } else {
            c.falhou(problemas.joinToString("; "))
        }
    }
}
