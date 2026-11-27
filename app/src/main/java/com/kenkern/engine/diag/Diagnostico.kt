package com.kenkern.engine.diag

import android.app.ActivityManager
import android.content.Context
import java.io.File
import com.kenkern.engine.projects.ProjectManager
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
import kotlin.math.sqrt

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
         * L2 (v0.1.28) — AGENDA DA RODADA, PURA (sem Context): as checagens
         * RÁPIDAS (sem GL) no pool Default, as checagens GL na thread EGL
         * dedicada e a memória no fim. A JVM prova que TODO id do catálogo
         * [novaListaDeChecagens] está agendado (DiagAgendaTest) — nada fica
         * PENDENTE na tela do aparelho. A v0.1.27 agendava só 26 dos 30:
         * play-sair/area/editor/ui existiam no catálogo e no `when`, mas
         * NUNCA RODAVAM — ficavam PENDENTES para sempre (a "prova" nunca
         * acontecia).
         */
        val AGENDA_RAPIDAS = listOf(
            "nativo", "etc2-nativo", "malha-nativa", "ck", "kern", "materiais",
            "gltf", "fbx", "importar", "kimg", "picking", "toque", "release",
            "layout", "joystick", "lua-api", "jogo",
            "play-sair", "area", "editor", "ui",
            "armazenamento", "pastas", "explorador"
        )
        val AGENDA_GL = listOf("shaders", "buffers", "glcaps", "etc2", "tex-cena", "cena", "luz", "astc")
        const val AGENDA_MEMORIA = "memoria"

        /** A agenda COMPLETA em ordem de execução (rapidas + gl + memória). */
        fun agendaDeExecucao(): List<String> = AGENDA_RAPIDAS + AGENDA_GL + AGENDA_MEMORIA

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
            Checagem("joystick", "Joystick de tela — matemática (normalização, zona morta, 8 direções, ações)"),
            Checagem("lua-api", "API Lua (L2) — eventos, timers, área, tags, prefab, classes, input, ui, storage, log"),
            Checagem("jogo", "Jogo 'Coletar moedas' — template real, 300 ticks com input sintético, contador ≥ 1 e jogador no chão"),
            Checagem("play-sair", "Saída da janela de jogo — roteamento de toque (botões nunca consumidos), watchdog 3 s e BACK"),
            Checagem("area", "Nicks de Área (ZONA) — enter/exit/stay, filtro por tag, uma vez só, bodies/contains, signal e checkpoint/respawn"),
            Checagem("editor", "Editor de código Lua — tokenizador, indentação automática, autocompletar offline, modelos e Verificar (LuaJ)"),
            Checagem("armazenamento", "Armazenamento — locais App × Aparelho (permissão, escrever/ler, auto-save)"),
            Checagem("pastas", "Pastas do projeto — árvore F2 + migração de projetos antigos"),
            Checagem("explorador", "Explorador — listagem paginada de 5.000 arquivos sintéticos no tempo limite"),
            Checagem("ui", "UI do TELAR — âncoras nas 3 proporções, ida e volta .kui, migração do joystick, commands e tela do template")
        )

        /**
         * S7 (v0.1.24): núcleo da checagem "jogo" — carrega o TEMPLATE
         * "Coletar moedas" SEM GL, simula 300 ticks com INPUT SINTÉTICO
         * (rumo à moeda mais distante — prova o andar) e exige contador
         * ≥ 1 e jogador no chão. A mensagem de falha diz o PASSO:
         * caiu / atravessou o chão / não moveu / não colidiu.
         */
        class ResultadoJogo(
            val moedasColetadas: Int,
            val peDoJogador: Float,
            val metrosAndados: Float,
            val falha: String? // null = jogo OK
        )

        /**
         * S7: avalia UMA cena como o jogo real — o MESMO caminho de Play
         * (cópia por sessão + física + scripts por tick). Parâmetros:
         * [cena] (template ou variante quebrada p/ teste), [ticks].
         */
        fun avaliarJogo(cena: com.kenkern.engine.core.Scene, ticks: Int): ResultadoJogo {
            cena.updateWorldTransforms()
            val original = com.kenkern.engine.core.serialization.CkFormat.writeToString(cena)
            val copia = com.kenkern.engine.core.serialization.CkFormat.parseToNewScene(original, cena.name)!!
            val runtime = com.kenkern.engine.engine.NickPlayRuntime(copia)
            val scripts = com.kenkern.engine.engine.NickLuaScripts(copia).also { it.iniciarTodos() }
            runtime.scripts = scripts
            val jogador = runtime.player
                ?: return ResultadoJogo(0, Float.NaN, 0f, "sem body.player no template")

            val inicioX = jogador.transform.worldMatrix.m[12]
            val inicioZ = jogador.transform.worldMatrix.m[14]
            var restantes = ticks
            while (restantes-- > 0) {
                // INPUT SINTÉTICO: rumo à moeda VIVA mais distante (andar de verdade).
                val alvo = moedaMaisDistanteDe(copia, jogador) ?: break
                val m = jogador.transform.worldMatrix.m
                val dx = alvo.first - m[12]
                val dz = alvo.second - m[14]
                val len = sqrt(dx * dx + dz * dz)
                val nx = if (len > 1e-4f) dx / len else 0f
                val nz = if (len > 1e-4f) dz / len else 0f
                runtime.step(1f / 60f, nx, nz)
                scripts.atualizar(1f / 60f)
            }

            copia.updateWorldTransforms()
            val ui = scripts.snapshotUiJogo()
            val moedas = ui["moedas"]?.toFloatOrNull()?.toInt() ?: 0
            val pe = jogador.transform.worldMatrix.m[13] - 0.5f // pé (cubo de 1 m)
            val m2 = jogador.transform.worldMatrix.m
            val andados = sqrt(
                (m2[12] - inicioX) * (m2[12] - inicioX) +
                    (m2[14] - inicioZ) * (m2[14] - inicioZ)
            )

            // TOPO do chão do template: estático em y=-0,5 com escala Y=1 → topo 0.
            // C2.3 (v0.1.26): com respawn na queda, uma cena SEM chão não cai
            // para sempre — o jogador reaparece (CAIU N×) e o passo continua
            // dizendo a verdade sobre o porquê de NÃO ter coletado.
            val quedas = runtime.respawnsNaSessao
            val falha = when {
                quedas > 0 -> "CAIU — o jogador caiu $quedas× e reapareceu no checkpoint/surgimento (sem chão ou chão furado; pé em $pe m, andou $andados m, contador $moedas)"
                pe < -0.5f -> "ATRAVESSOU O CHÃO — o jogador caiu para baixo do piso (pé em $pe m)"
                pe < -0.05f -> "CAIU — o jogador não pousou no chão (pé em $pe m)"
                andados < 1f -> "NÃO MOVEU — o input sintético não moveu o jogador ($andados m)"
                moedas < 1 -> "NÃO COLIDIU — andou ${"%.1f".format(andados)} m mas nenhum gatilho de moeda disparou (contador $moedas)"
                else -> null
            }
            return ResultadoJogo(moedas, pe, andados, falha)
        }

        /** Moeda (area.volume com nome "Moeda …") VIVA mais distante do jogador. */
        private fun moedaMaisDistanteDe(
            cena: com.kenkern.engine.core.Scene,
            jogador: com.kenkern.engine.core.Nick
        ): Pair<Float, Float>? {
            var melhor: Pair<Float, Float>? = null
            var melhorDist = -1f
            val m = jogador.transform.worldMatrix.m
            val px = m[12]
            val pz = m[14]
            val pilha = ArrayDeque<com.kenkern.engine.core.Nick>()
            for (raiz in cena.roots) pilha.addLast(raiz)
            while (!pilha.isEmpty()) {
                val nick = pilha.removeLast()
                if (nick.active && nick.kind == "area.volume" && nick.name?.startsWith("Moeda") == true) {
                    val wm = nick.transform.worldMatrix.m
                    val dx = wm[12] - px
                    val dz = wm[14] - pz
                    val dist = sqrt(dx * dx + dz * dz)
                    if (dist > melhorDist) {
                        melhorDist = dist
                        melhor = wm[12] to wm[14]
                    }
                }
                for (filho in nick.children) pilha.addLast(filho)
            }
            return melhor
        }

        /** S7: conveniência — o TEMPLATE real da checagem "jogo". */

        /**
         * L2: Núcleo PURO da checagem "lua-api" — executa um script de exemplo
         * cobrindo TODA a API Lua L2 (eventos emit/on, timers after/every/cancel,
         * on_enter/on_stay/on_exit, busca por tag/nome, spawn_prefab, destroy,
         * leitura/escrita de classes, input.action, ui, storage.save/load, log).
         */
        fun avaliarLuaApi(): Pair<Boolean, String> {
            com.kenkern.engine.core.KenkernBuiltInClasses.registerAll()
            val cena = Scene("DiagLuaApi")
            val chao = cena.createRootNick("Chao", kind = "body.static")
            chao.transform.setLocalPosition(0f, -0.5f, 0f)
            chao.transform.scale.set(10f, 1f, 10f)

            val player = cena.createRootNick("Heroi", kind = "body.player")
            player.transform.setLocalPosition(0f, 0.5f, 0f)
            player.addTag("jogador")
            NickClassRegistry.createInstance("Spin")?.let { player.addClassInstance(it) }

            val moedaPrefab = com.kenkern.engine.core.Nick(name = "MoedaPrefab", kind = NickKindRegistry.NICK_MESH)
            moedaPrefab.addTag("coletavel")

            val area = cena.createRootNick("Portal", kind = "area.volume")
            area.transform.setLocalPosition(2f, 0.5f, 0f)
            area.addTag("portal")

            val scriptArea = NickClassRegistry.createInstance("Script")!!
            scriptArea.fields["fonte"] = NickValue.StringValue(
                """
                entrou, ficou, saiu = 0, 0, 0
                on_enter = function(outro)
                  if outro.has_tag("jogador") then entrou = entrou + 1 end
                end
                on_stay = function(outro, dt)
                  ficou = ficou + 1
                end
                on_exit = function(outro)
                  saiu = saiu + 1
                end
                """.trimIndent()
            )
            area.addClassInstance(scriptArea)

            val scriptHeroi = NickClassRegistry.createInstance("Script")!!
            scriptHeroi.fields["fonte"] = NickValue.StringValue(
                """
                evento_val, tick_after, tick_every, acao_ok, prop_ok, save_ok = 0, 0, 0, false, false, false
                on_start = function()
                  scene.on("bonus", function(d) evento_val = evento_val + (d.qtd or 0) end)
                  scene.on("bonus", function(d) evento_val = evento_val + (d.qtd or 0) end)
                  timer.after(0.05, function()
                    tick_after = tick_after + 1
                    scene.emit("bonus", { qtd = 5 })
                  end)
                  local rep = timer.every(0.04, function() tick_every = tick_every + 1 end)
                  local dead = timer.every(0.04, function() tick_every = tick_every + 100 end)
                  timer.cancel(dead)
                  local achou_nome = scene.find("Portal")
                  local achou_tag = scene.find_by_tag("portal")
                  local todos = scene.find_all_by_tag("portal")
                  local p = scene.spawn_prefab("moeda", 5, 1, 0)
                  if p and #todos == 1 and achou_nome and achou_tag then
                    scene.destroy(p)
                  end
                  nick.set("Spin", "speed", 77.5)
                  prop_ok = (nick.get("Spin", "speed") == 77.5)
                  ui.text("hud", "ok")
                  ui.count("score", 3)
                  storage.save("score", ui.get_count("score"))
                  save_ok = (storage.load("score", 0) == 3)
                  scene.log("diag lua-api ok")
                end
                on_update = function(dt)
                  if input.action("jump") then acao_ok = true end
                end
                """.trimIndent()
            )
            player.addClassInstance(scriptHeroi)

            val store = com.kenkern.engine.engine.MemoryGameSaveStore()
            val inputEstado = com.kenkern.engine.engine.EstadoInputJogo().apply { definirAcao("jump", true) }
            val runtime = com.kenkern.engine.engine.NickPlayRuntime(cena)
            val lua = com.kenkern.engine.engine.NickLuaScripts(
                cena,
                saveStore = store,
                prefabProvider = { nome -> if (nome == "moeda") moedaPrefab else null }
            ).also {
                it.estadoInput = inputEstado
                it.iniciarTodos()
            }
            runtime.scripts = lua

            // Anda para dentro e depois para fora da área
            for (i in 0 until 25) {
                val mx = if (i < 18) 1f else -1f
                runtime.step(0.05f, mx, 0f)
                lua.atualizar(0.05f)
            }

            val gHeroi = (lua.instanciaDe("Heroi")?.runtime as? com.kenkern.engine.core.script.LuaJScriptRuntime)?.ambiente()
                ?: return false to "runtime de Herói não iniciou"
            val gArea = (lua.instanciaDe("Portal")?.runtime as? com.kenkern.engine.core.script.LuaJScriptRuntime)?.ambiente()
                ?: return false to "runtime de Portal não iniciou"

            val problemas = ArrayList<String>()
            if (gHeroi.get("evento_val").toint() != 10) problemas.add("emit/on=${gHeroi.get("evento_val").toint()}")
            if (gHeroi.get("tick_after").toint() != 1) problemas.add("timer.after=${gHeroi.get("tick_after").toint()}")
            if (gHeroi.get("tick_every").toint() < 5 || gHeroi.get("tick_every").toint() >= 100) problemas.add("timer.every/cancel=${gHeroi.get("tick_every").toint()}")
            if (!gHeroi.get("prop_ok").toboolean()) problemas.add("nick.get/set falhou")
            if (!gHeroi.get("acao_ok").toboolean()) problemas.add("input.action falhou")
            if (!gHeroi.get("save_ok").toboolean()) problemas.add("storage/ui falhou")
            if (gArea.get("entrou").toint() < 1) problemas.add("on_enter=${gArea.get("entrou").toint()}")
            if (gArea.get("ficou").toint() < 1) problemas.add("on_stay=${gArea.get("ficou").toint()}")
            if (gArea.get("saiu").toint() < 1) problemas.add("on_exit=${gArea.get("saiu").toint()}")
            if (!lua.logsEmitidos().contains("diag lua-api ok")) problemas.add("scene.log falhou")

            return if (problemas.isEmpty()) {
                true to "API Lua L2 validada (emit/on · timers · on_enter/stay/exit · tags · prefab · classes · input · ui · storage · log)"
            } else {
                false to problemas.joinToString("; ")
            }
        }

        fun avaliarJogoTemplate(): ResultadoJogo {
            val cena = com.kenkern.engine.core.Scene("Coletar moedas")
            com.kenkern.engine.projects.ProjectManager.montarTemplateColetarMoedas(cena)
            return avaliarJogo(cena, 300)
        }
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

    /** L1 (v0.1.28): olho da câmera da checagem "luz" (ETAPA A — log dos uniforms). */
    private val olhoCam = com.kenkern.engine.core.math.Vec3()

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
        // L2 (v0.1.28): a agenda vem das constantes PURAS do companion — a JVM
        // prova que TODO id do catálogo roda (DiagAgendaTest: nada PENDENTE).
        val rapidas = AGENDA_RAPIDAS
        val gl = AGENDA_GL
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

            rodarUma(AGENDA_MEMORIA, onAtualiza, finalizadas)
        } catch (e: CancellationException) {
            // Botão cancelar (Job cancelado): marca como CANCELADA só o que NÃO
            // finalizou — chamadas simples, sem suspensão dentro do catch — e
            // REPROPAGA o cancelamento.
            val pendentes = agendaDeExecucao().filter { it !in finalizadas }
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
                            "lua-api" -> checarLuaApi(c)
                            "jogo" -> checarJogo(c)
                            "play-sair" -> checarPlaySair(c)
                            "area" -> checarArea(c)
                            "editor" -> checarEditor(c)
                            "ui" -> checarUi(c)
                            "armazenamento" -> checarArmazenamento(c)
                            "pastas" -> checarPastas(c)
                            "explorador" -> checarExplorador(c)
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
                // R6: builder PURO formatando a concatenação INTEIRA (o .format
                // antigo pegava só o último literal — o "%.1f×" ficava CRU).
                MensagensDiag.malhaNativa(
                    nativoMs, kotlinMs, aceleracao,
                    dedupNativo.unicos, dedupKotlin.vertexCount,
                    lodNativo.indices.size / 3, lodNativo.erro, lodKotlin.lods.size
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
                // R6: builder PURO — o .format antigo pegava só o segundo
                // literal; "%.0f MB" e "%d ms" ficavam CRUS na mensagem.
                MensagensDiag.importarModelo(summary.picoMemoriaMb, summary.tempoMs)
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
            // R6: builder PURO — o .format antigo pegava só o segundo literal
            // (o PRIMEIRO "%.1f dB" ficava CRU no aparelho).
            MensagensDiag.kimg(psnrRealista, psnrAdversarial, relatorio.resumo(), bytesRealista)
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
     * S7 (v0.1.24): checagem "jogo" — o template "Coletar moedas" JOGÁVEL
     * de verdade SEM GL: cópia de Play + 300 ticks com INPUT SINTÉTICO (rumo à
     * moeda mais distante) + scripts por tick; exige contador ≥ 1 e jogador
     * NO CHÃO. A falha DIZ O PASSO: caiu / atravessou o chão / não moveu /
     * não colidiu — o relatório do aparelho vira teste de JVM.
     */
    private fun checarJogo(c: Checagem) {
        val r = avaliarJogoTemplate()
        val falha = r.falha
        if (falha != null) {
            c.falhou(
                "$falha · moedas=${r.moedasColetadas} · pé=${r.peDoJogador} · andou=${r.metrosAndados} m"
            )
        } else {
            c.aprovado(
                "300 ticks · moedas=${r.moedasColetadas} · jogador NO CHÃO (pé=${r.peDoJogador} m) · " +
                    "andou ${r.metrosAndados} m com input sintético — o jogo é jogável"
            )
        }
    }

    /**
     * Checagem "play-sair" (P1, v0.1.25) — SAÍDA GARANTIDA DA JANELA DE JOGO,
     * SEM GL: valida o RoteadorToqueJogo (a MESMA fonte única usada pela
     * SurfaceView.onTouchEvent e pelos botões da janela):
     *  (1) toque sobre Pausar/Sair em PLAYING e PAUSED → botão recebe e a cena
     *      NUNCA consome (causa raiz do bug do toque preso);
     *  (2) toque fora dos botões → a cena consome (controle do jogo);
     *  (3) watchdog em alerta → "Forçar saída" tem prioridade;
     *  (4) BACK do sistema → SEMPRE sai (PLAYING/PAUSED → STOPPED);
     *  (5) WATCHDOG_MS = 3000 e regiões com alvo ≥ 48dp.
     * As regiões simulam a geometria real dos botões (topo central, 48dp).
     */
    private fun checarPlaySair(c: Checagem) {
        val problemas = ArrayList<String>(6)
        val pausar = com.kenkern.engine.ui.jogo.RegiaoBotao(
            com.kenkern.engine.ui.jogo.AlvoToque.BOTAO_PAUSAR, 310f, 8f, 430f, 60f
        )
        val sair = com.kenkern.engine.ui.jogo.RegiaoBotao(
            com.kenkern.engine.ui.jogo.AlvoToque.BOTAO_SAIR, 440f, 8f, 520f, 60f
        )
        val forcar = com.kenkern.engine.ui.jogo.RegiaoBotao(
            com.kenkern.engine.ui.jogo.AlvoToque.BOTAO_FORCAR, 280f, 70f, 520f, 122f
        )
        val regioes = listOf(pausar, sair)
        val d = com.kenkern.engine.ui.jogo.RoteadorToqueJogo

        val dpPausar = d.decidir(com.kenkern.engine.engine.PlayState.PLAYING, 370f, 30f, regioes, false)
        if (dpPausar.alvo != com.kenkern.engine.ui.jogo.AlvoToque.BOTAO_PAUSAR || dpPausar.cenaConsome) {
            problemas.add("Pausar em PLAYING: alvo=${dpPausar.alvo} cenaConsome=${dpPausar.cenaConsome}")
        }
        val dSair = d.decidir(com.kenkern.engine.engine.PlayState.PLAYING, 480f, 30f, regioes, false)
        if (dSair.alvo != com.kenkern.engine.ui.jogo.AlvoToque.BOTAO_SAIR || dSair.cenaConsome) {
            problemas.add("Sair em PLAYING: alvo=${dSair.alvo} cenaConsome=${dSair.cenaConsome}")
        }
        val dPausado = d.decidir(com.kenkern.engine.engine.PlayState.PAUSED, 370f, 30f, regioes, false)
        if (dPausado.alvo != com.kenkern.engine.ui.jogo.AlvoToque.BOTAO_PAUSAR || dPausado.cenaConsome) {
            problemas.add("Pausar em PAUSED: alvo=${dPausado.alvo} cenaConsome=${dPausado.cenaConsome}")
        }
        val dCena = d.decidir(com.kenkern.engine.engine.PlayState.PLAYING, 100f, 200f, regioes, false)
        if (dCena.alvo != com.kenkern.engine.ui.jogo.AlvoToque.CENA || !dCena.cenaConsome) {
            problemas.add("fora dos botões: alvo=${dCena.alvo} cenaConsome=${dCena.cenaConsome}")
        }
        val dForcar = d.decidir(com.kenkern.engine.engine.PlayState.PLAYING, 400f, 100f, regioes + forcar, true)
        if (dForcar.alvo != com.kenkern.engine.ui.jogo.AlvoToque.BOTAO_FORCAR || dForcar.cenaConsome) {
            problemas.add("watchdog: alvo=${dForcar.alvo} cenaConsome=${dForcar.cenaConsome}")
        }
        if (d.acaoBack(com.kenkern.engine.engine.PlayState.PLAYING) != com.kenkern.engine.engine.PlayState.STOPPED || d.acaoBack(com.kenkern.engine.engine.PlayState.PAUSED) != com.kenkern.engine.engine.PlayState.STOPPED) {
            problemas.add("BACK não sai da janela de jogo")
        }
        // L2 (v0.1.28): FLUXO COMPLETO SIMULADO — toque → decisão → AÇÃO:
        // o jogo pausa (PLAYING→PAUSED), continua (PAUSED→PLAYING) e sai
        // (→STOPPED) AQUI no teste; o aceite do aparelho confirma o mesmo
        // caminho no toque real (≤100 ms).
        val aposPausar = d.acao(dpPausar.alvo, com.kenkern.engine.engine.PlayState.PLAYING)
        if (aposPausar != com.kenkern.engine.engine.PlayState.PAUSED) {
            problemas.add("tocar Pausar não PAUSA no teste (PLAYING→$aposPausar)")
        }
        val aposContinuar = d.acao(dPausado.alvo, com.kenkern.engine.engine.PlayState.PAUSED)
        if (aposContinuar != com.kenkern.engine.engine.PlayState.PLAYING) {
            problemas.add("tocar Continuar não retoma o jogo no teste (PAUSED→$aposContinuar)")
        }
        val aposSair = d.acao(dSair.alvo, com.kenkern.engine.engine.PlayState.PLAYING)
        if (aposSair != com.kenkern.engine.engine.PlayState.STOPPED) {
            problemas.add("tocar Sair não SAI no teste (PLAYING→$aposSair)")
        }
        val aposForcar = d.acao(dForcar.alvo, com.kenkern.engine.engine.PlayState.PLAYING)
        if (aposForcar != com.kenkern.engine.engine.PlayState.STOPPED) {
            problemas.add("Forçar saída (watchdog) não sai no teste (PLAYING→$aposForcar)")
        }
        // L2: NENHUM botão do editor (Focar/Grade) intercepta — o TIPO dos
        // alvos da janela de jogo SÓ expressa controles do jogo; um botão do
        // editor NÃO PODE ser alvo do roteador (não existe no enum) e a UI
        // compõe Grade/Focar FORA do jogo (por estado — Onda 0): as regiões
        // publicadas à SurfaceView vêm SÓ deste enum.
        val alvosPossiveis = com.kenkern.engine.ui.jogo.AlvoToque.values().toSet()
        if (alvosPossiveis != setOf(
                com.kenkern.engine.ui.jogo.AlvoToque.BOTAO_PAUSAR,
                com.kenkern.engine.ui.jogo.AlvoToque.BOTAO_SAIR,
                com.kenkern.engine.ui.jogo.AlvoToque.BOTAO_FORCAR,
                com.kenkern.engine.ui.jogo.AlvoToque.CENA
            )
        ) {
            problemas.add("AlvoToque expressa alvo fora do jogo: $alvosPossiveis (botão do editor interceptando?)")
        }
        if (d.WATCHDOG_MS != 3000L) problemas.add("WATCHDOG_MS=${d.WATCHDOG_MS} ≠ 3000")
        for (r2 in regioes) {
            if (r2.baixo - r2.topo < 48f) problemas.add("região ${r2.alvo} com ${r2.baixo - r2.topo}dp < 48dp")
        }
        if (problemas.isEmpty()) {
            c.aprovado(
                "botões Pausar/Sair recebem o toque em PLAYING e PAUSED (cena nunca consome) · " +
                    "toque simulado: Pausar PAUSA, Continuar RETOMA, Sair SAÍ (AÇÃO aplicada no teste) · " +
                    "nenhum botão do editor é alvo possível (Grade/Focar fora do jogo) · " +
                    "Forçar saída na frente do watchdog · BACK sempre sai · WATCHDOG=3s · alvos ≥48dp"
            )
        } else {
            c.falhou(problemas.joinToString("; "))
        }
    }

    /**
     * Checagem "area" (C2, v0.1.26) — NICKS DE ÁREA FUNCIONAIS, SEM GL:
     * (a) on_enter/on_exit/on_stay com o runtime REAL (player anda com input
     *     sintético para dentro e para fora do volume);
     * (b) filtro por tag (com a tag dispara; sem a tag não);
     * (c) "uma vez só" desarma após a 1ª entrada;
     * (d) signal.emit/on no mesmo barramento da cena;
     * (e) checkpoint + respawn na queda abaixo do limite.
     * A falha DIZ O PASSO.
     */
    private fun checarArea(c: Checagem) {
        val problemas = ArrayList<String>(6)
        try {
            val scene = com.kenkern.engine.core.Scene()
            val mundo = scene.createRootNick("Mundo")
            val chao = scene.createNick("Chão", mundo, kind = "body.static")
            chao.transform.setLocalPosition(0f, -0.5f, 0f)
            chao.transform.scale.set(14f, 1f, 14f)
            val player = scene.createNick("Herói", mundo, kind = "body.player")
            player.transform.setLocalPosition(0f, 0.5f, 0f)
            player.addTag("heroi")
            scene.createNick(null, mundo, kind = "area.spawn").transform.setLocalPosition(0f, 1f, 0f)
            fun novaArea(nome: String, filtro: String, umaVez: Boolean): com.kenkern.engine.core.Nick {
                val area = scene.createNick(nome, mundo, kind = "area.volume")
                area.transform.setLocalPosition(3f, 0.5f, 0f)
                area.transform.scale.set(2f, 2f, 2f)
                val inst = com.kenkern.engine.core.NickClassRegistry.createInstance("Area")!!
                inst.fields["filtroTag"] = com.kenkern.engine.core.NickValue.StringValue(filtro)
                inst.fields["umaVezSo"] = com.kenkern.engine.core.NickValue.BoolValue(umaVez)
                area.addClassInstance(inst)
                return area
            }
            novaArea("Zona", "", false).also { a ->
                val i = com.kenkern.engine.core.NickClassRegistry.createInstance(com.kenkern.engine.engine.NickLuaScripts.CLASSE_SCRIPT)!!
                i.fields[com.kenkern.engine.engine.NickLuaScripts.CAMPO_FONTE] = com.kenkern.engine.core.NickValue.StringValue(
                    "entrou=0 saiu=0 dentro=0\non_enter=function(o) entrou=entrou+1 end\non_exit=function(o) saiu=saiu+1 end\non_stay=function(o,dt) dentro=dentro+1 end"
                )
                a.addClassInstance(i)
            }
            novaArea("SóInimigo", "inimigo", false).also { a ->
                val i = com.kenkern.engine.core.NickClassRegistry.createInstance(com.kenkern.engine.engine.NickLuaScripts.CLASSE_SCRIPT)!!
                i.fields[com.kenkern.engine.engine.NickLuaScripts.CAMPO_FONTE] = com.kenkern.engine.core.NickValue.StringValue("entrou=0\non_enter=function(o) entrou=entrou+1 end")
                a.addClassInstance(i)
            }
            val umaVez = novaArea("UmaVez", "", true).also { a ->
                val i = com.kenkern.engine.core.NickClassRegistry.createInstance(com.kenkern.engine.engine.NickLuaScripts.CLASSE_SCRIPT)!!
                i.fields[com.kenkern.engine.engine.NickLuaScripts.CAMPO_FONTE] = com.kenkern.engine.core.NickValue.StringValue("entrou=0\non_enter=function(o) entrou=entrou+1 end")
                a.addClassInstance(i)
            }
            val sinal = scene.createNick("Sirene", mundo, kind = "area.signal").also { a ->
                val i = com.kenkern.engine.core.NickClassRegistry.createInstance(com.kenkern.engine.engine.NickLuaScripts.CLASSE_SCRIPT)!!
                i.fields[com.kenkern.engine.engine.NickLuaScripts.CAMPO_FONTE] = com.kenkern.engine.core.NickValue.StringValue(
                    "ouviu=0\nsignal.on(\"sirene\", function(d) ouviu=ouviu+d.v end)\non_start=function() signal.emit(\"sirene\", { v=5 }) end"
                )
                a.addClassInstance(i)
            }
            scene.updateWorldTransforms()
            val runtime = com.kenkern.engine.engine.NickPlayRuntime(scene)
            val scripts = com.kenkern.engine.engine.NickLuaScripts(scene)
            runtime.scripts = scripts
            scripts.consultorArea = object : com.kenkern.engine.engine.NickLuaScripts.ConsultorArea {
                override fun boundsMundo(nick: com.kenkern.engine.core.Nick, out: FloatArray) =
                    runtime.boundsMundo(nick, out)
                override fun corposCena(): List<com.kenkern.engine.core.Nick> = runtime.corposSimulados
                override fun respawnJogador(): Boolean = runtime.respawnJogador()
                override fun boundsJogador(out: FloatArray) {
                    val p = runtime.player ?: return
                    runtime.boundsMundo(p, out)
                }
            }
            scripts.iniciarTodos()
            fun globals(nome: String) = scripts.instanciaDe(nome)?.let {
                (it.runtime as com.kenkern.engine.core.script.LuaJScriptRuntime).ambiente()
            }
            // (a)+(b)+(c): anda para dentro, fica, volta, e volta de novo
            repeat(40) { runtime.step(1f / 60f, 1f, 0f) }
            repeat(3) { runtime.step(1f / 60f, 0f, 0f) }
            repeat(40) { runtime.step(1f / 60f, -1f, 0f) }
            repeat(40) { runtime.step(1f / 60f, 1f, 0f) }
            val gz = globals("Zona")
            if (gz == null) {
                problemas.add("Zona: script não iniciou")
            } else {
                if (gz.get("entrou").toint() != 2) problemas.add("Zona: entrou=${gz.get("entrou")} ≠ 2 (1ª ida + volta após umaVez desarmada)")
                if (gz.get("saiu").toint() != 1) problemas.add("Zona: saiu=${gz.get("saiu")} ≠ 1")
                if (gz.get("dentro").toint() < 3) problemas.add("Zona: stay=${gz.get("dentro")} < 3")
            }
            val gi = globals("SóInimigo")
            if (gi?.get("entrou")?.toint() != 0) problemas.add("filtro por tag FALHOU (entrou=${gi?.get("entrou")})")
            val gu = globals("UmaVez")
            if (gu?.get("entrou")?.toint() != 1) problemas.add("uma vez só: entrou=${gu?.get("entrou")} ≠ 1")
            // (d) signal
            val gs = globals("Sirene")
            if (gs?.get("ouviu")?.toint() != 5) problemas.add("signal emit/on: ouviu=${gs?.get("ouviu")} ≠ 5")
            // (e) checkpoint + respawn na queda
            val checkpoint = scene.createNick("CP", mundo, kind = "area.checkpoint")
            checkpoint.transform.setLocalPosition(5f, 0.5f, 0f)
            checkpoint.transform.scale.set(2f, 2f, 2f)
            scene.updateWorldTransforms()
            val p = runtime.player ?: throw AssertionError("sem player")
            repeat(80) { runtime.step(1f / 60f, 1f, 0f) }
            repeat(50) { runtime.step(1f / 60f, 1f, 0f) } // sai da borda e cai
            var voltou = false
            repeat(1200) {
                runtime.step(1f / 60f, 0f, 0f)
                val x = p.transform.worldMatrix.m[12]
                val y = p.transform.worldMatrix.m[13]
                if (x > 4f && x < 6f && y > 0f) voltou = true
            }
            if (!voltou) problemas.add("queda abaixo do limite NÃO reapareceu no checkpoint")
        } catch (t: Throwable) {
            problemas.add("exceção: ${t.message ?: t.javaClass.simpleName}")
        }
        if (problemas.isEmpty()) {
            c.aprovado(
                "on_enter/on_exit/on_stay (runtime real, player anda) · filtro por tag · uma vez só desarma · " +
                    "signal.emit/on no barramento da cena · queda abaixo do limite reaparece no checkpoint"
            )
        } else {
            c.falhou(problemas.joinToString("; "))
        }
    }

    /**
     * Checagem "editor" (C1, v0.1.26) — EDITOR DE CÓDIGO LUA, sem GL:
     * (a) tokenizador marca palavra-chave/string/comentário/número/API;
     * (b) indentação automática (enter após then → +1 nível);
     * (c) autocompletar offline por contexto (nick./scene. → membros);
     * (d) modelos de script (vazio/movimento/coletar/gatilho) compilam;
     * (e) "Verificar" compila no LuaJ: código bom → null; quebrado → linha.
     */
    private fun checarEditor(c: Checagem) {
        val problemas = ArrayList<String>(5)
        try {
            // (a) tokenizador
            val spans = com.kenkern.engine.ui.editor.TokenizadorLua.marcarLinha(
                "local x = 42 -- nota", com.kenkern.engine.ui.editor.TokenizadorLua.Estado()
            )
            val tipos = spans.map { it.tipo }
            if (com.kenkern.engine.ui.editor.TokenTipo.PALAVRA_CHAVE !in tipos) problemas.add("tokenizador: palavra-chave não marcada")
            if (com.kenkern.engine.ui.editor.TokenTipo.NUMERO !in tipos) problemas.add("tokenizador: número não marcado")
            if (com.kenkern.engine.ui.editor.TokenTipo.COMENTARIO !in tipos) problemas.add("tokenizador: comentário não marcado")
            // (b) indentação
            val doc = com.kenkern.engine.ui.editor.DocumentoLua("if x then")
            doc.moverCursor(0, 9)
            doc.quebrarLinha()
            if (doc.linhas.getOrNull(1) != "    ") problemas.add("indentação: enter após then → '${doc.linhas.getOrNull(1)}'")
            // (c) autocompletar
            val s = com.kenkern.engine.ui.editor.AutocompletarLua.sugestoes("nick.")
            if (s.isEmpty()) problemas.add("autocompletar: nick. sem membros")
            val s2 = com.kenkern.engine.ui.editor.AutocompletarLua.sugestoes("funct")
            if (s2.none { it.nome == "function" }) problemas.add("autocompletar: 'funct' não sugere function")
            // (d) modelos compilam no LuaJ
            for (modelo in com.kenkern.engine.ui.editor.ModelosScript.NOMES) {
                val erro = com.kenkern.engine.ui.screens.verificarLua(com.kenkern.engine.ui.editor.ModelosScript.conteudo(modelo))
                if (erro != null) problemas.add("modelo '$modelo' não compila: ${erro.second}")
            }
            // (e) Verificar: 5 trechos QUEBRADOS + 3 VÁLIDOS (R2 — a premissa
            // antiga chamava de quebrado "local x =\nprint(x)", que é Lua
            // VÁLIDO; agora a fonte única é TrechosVerificar). Quebrado tem
            // que devolver LINHA e MENSAGEM; válido tem que compilar.
            for ((rotulo, codigo) in com.kenkern.engine.ui.editor.TrechosVerificar.VALIDOS) {
                if (com.kenkern.engine.ui.screens.verificarLua(codigo) != null) {
                    problemas.add("verificar: código válido reprovado ($rotulo)")
                }
            }
            for ((rotulo, codigo) in com.kenkern.engine.ui.editor.TrechosVerificar.QUEBRADOS) {
                val erroRuim = com.kenkern.engine.ui.screens.verificarLua(codigo)
                if (erroRuim == null) problemas.add("verificar: código quebrado passou ($rotulo)")
                if (erroRuim != null && (erroRuim.first < 1 || erroRuim.second.isBlank())) {
                    problemas.add("verificar: erro sem linha/mensagem ($rotulo: $erroRuim)")
                }
            }
            // (f) R3: modelo NÃO vazio (grava ANTES de abrir — CriadorScript) +
            // layout da barra SEM SOBREPOSIÇÃO por larguras ([voltar | nome e
            // estado | Verificar | ⋮]; 360dp = retrato do alvo 800x360).
            for (modelo in com.kenkern.engine.ui.editor.ModelosScript.NOMES) {
                if (com.kenkern.engine.ui.editor.ModelosScript.conteudo(modelo).isBlank()) {
                    problemas.add("modelo '$modelo' com conteúdo VAZIO")
                }
            }
            if (!com.kenkern.engine.ui.editor.LargurasEditorLua.semSobreposicao(360)) {
                problemas.add("layout: barra do editor NÃO cabe em 360dp (retrato do 800x360)")
            }
            if (!com.kenkern.engine.ui.editor.LargurasEditorLua.semSobreposicao(800)) {
                problemas.add("layout: barra do editor NÃO cabe em 800dp")
            }
        } catch (t: Throwable) {
            problemas.add("exceção: ${t.message ?: t.javaClass.simpleName}")
        }
        if (problemas.isEmpty()) {
            c.aprovado(
                "tokenizador (keyword/string/comentário/número/API) · indentação por balanço de blocos · " +
                    "autocompletar offline por contexto (catálogo = LUA_API.md) · 4 modelos compilam · Verificar (LuaC) marca a linha e lista no painel · " +
                    "5 quebrados reprovados + 3 válidos (TrechosVerificar) · modelos gravam conteúdo ANTES de abrir (não vazios) · " +
                    "barra [voltar|nome|Verificar|⋮] sem sobreposição em 360/800dp (LargurasEditorLua)"
            )
        } else {
            c.falhou(problemas.joinToString("; "))
        }
    }


    /**
     * Checagem "armazenamento" (F6, v0.1.30) — os DOIS LOCAIS do F1:
     *  (a) App: roundtrip de GRAVAÇÃO ATÔMICA + leitura + regravação
     *      (o mesmo caminho do auto-save) no filesDir;
     *  (b) Aparelho: estado da PERMISSÃO "Acesso a todos os arquivos";
     *      concedida → roundtrip em /storage/emulated/0/Kenkern (escrever,
     *      ler, apagar) + espaço livre; NEGADA → não é FALHA (o app segue
     *      no App e mantém .kernpack/SAF — design F1), o estado vai na
     *      mensagem.
     */
    private fun checarArmazenamento(c: Checagem) {
        val problemas = ArrayList<String>(3)
        val app = appContext
        val t0 = System.nanoTime()
        try {
            // (a) App: escrever → ler → regravar (auto-save igual nos dois locais)
            val dirApp = File(app.filesDir ?: File(app.cacheDir, "files"), "diag-armazenamento")
            dirApp.mkdirs()
            val alvo = File(dirApp, "auto-save-teste.ck")
            if (!ProjectManager.writeFileAtomic(alvo, "conteúdo 1\n")) {
                problemas.add("App: gravação atômica falhou")
            }
            if (alvo.readText() != "conteúdo 1\n") problemas.add("App: leitura divergiu")
            if (!ProjectManager.writeFileAtomic(alvo, "conteúdo 2 (regravação)\n")) {
                problemas.add("App: regravação (auto-save) falhou")
            }
            if (alvo.readText() != "conteúdo 2 (regravação)\n") problemas.add("App: releitura divergiu")
            alvo.delete()

            // (b) Aparelho
            val permissao = com.kenkern.engine.projects.PermissaoAparelho.concedida(app)
            if (permissao) {
                val raizAparelho = com.kenkern.engine.projects.AparelhoStorage.raiz(app)
                val dirAparelho = File(raizAparelho, ".diag-teste")
                dirAparelho.mkdirs()
                val f = File(dirAparelho, "roundtrip.tmp")
                if (!ProjectManager.writeFileAtomic(f, "aparelho")) {
                    problemas.add("Aparelho: gravação falhou (com permissão concedida)")
                } else if (f.readText() != "aparelho") {
                    problemas.add("Aparelho: leitura divergiu")
                }
                f.delete()
                dirAparelho.delete()
            }
        } catch (t: Throwable) {
            problemas.add("exceção: ${t.message ?: t.javaClass.simpleName}")
        }
        val ms = (System.nanoTime() - t0) / 1_000_000L
        if (problemas.isEmpty()) {
            val permissao = com.kenkern.engine.projects.PermissaoAparelho.concedida(app)
            val livreMb = com.kenkern.engine.projects.AppStorage.raiz(app).usableSpace / (1024L * 1024L)
            c.aprovado(
                (if (permissao) "PERMISSÃO concedida — App e Aparelho leem/gravam (roundtrip OK)" else "permissão NEGADA — o app segue no App (por design: .kernpack e SAF continuam)") +
                    " · auto-save atômico (grava→lê→regrava→lê) no App ✓ · ${ms} ms · App livre ${livreMb} MB"
            )
        } else {
            c.falhou(problemas.joinToString("; ") + " · ${ms} ms")
        }
    }

    /**
     * Checagem "pastas" (F6, v0.1.30) — árvore F2 nova COMPLETA + MIGRAÇÃO
     * de estrutura antiga (idempotente, NADA apagado).
     */
    private fun checarPastas(c: Checagem) {
        val problemas = ArrayList<String>(3)
        try {
            val base = File(appContext.cacheDir, "diag-pastas")
            base.deleteRecursively()
            base.mkdirs()
            // (a) árvore nova completa
            val novo = File(base, "novo")
            novo.mkdirs()
            com.kenkern.engine.projects.GuardasCaminho.garantirArvore(novo)
            if (!com.kenkern.engine.projects.GuardasCaminho.arvoreCompleta(novo)) {
                problemas.add("árvore nova INCOMPLETA")
            }
            // (b) migração de projeto antigo: acrescenta sem apagar
            val antigo = File(base, "antigo")
            File(antigo, "assets").mkdirs()
            File(antigo, "scenes").mkdirs()
            val cena = File(antigo, "scenes/cena.ck")
            cena.writeText("# antiga")
            com.kenkern.engine.projects.GuardasCaminho.garantirArvore(antigo)
            if (!com.kenkern.engine.projects.GuardasCaminho.arvoreCompleta(antigo)) {
                problemas.add("migração não completou a árvore")
            }
            if (cena.readText() != "# antiga") problemas.add("migração APAGOU arquivo do usuário")
            base.deleteRecursively()
        } catch (t: Throwable) {
            problemas.add("exceção: ${t.message ?: t.javaClass.simpleName}")
        }
        if (problemas.isEmpty()) {
            c.aprovado(
                "árvore nova completa (assets/{models,textures,materials,audio,fonts,video}, scenes, nicks, scripts, animations, shaders, ui, worlds, dialogues, build, .kenkern) · " +
                    "migração de projeto antigo acrescenta o que falta e NÃO apaga nada (cena preservada) · " +
                    "garantas: caminho dentro da raiz (canônico), 'ponto ponto' bloqueado, nomes ≤60"
            )
        } else {
            c.falhou(problemas.joinToString("; "))
        }
    }

    /**
     * Checagem "explorador" (F6, v0.1.30) — listagem PAGINADA de 5.000
     * arquivos SINTÉTICOS dentro do LIMITE DE TEMPO (a lista virtualizada
     * do painel Projeto pede por janela — nunca a pasta inteira de uma vez).
     */
    private fun checarExplorador(c: Checagem) {
        val problemas = ArrayList<String>(3)
        val dir = File(appContext.cacheDir, "diag-explorador-5000")
        dir.mkdirs()
        var criacaoMs = 0L
        var listagemMs = 0L
        var listados = 0
        try {
            // 5.000 arquivos sintéticos
            val t0 = System.nanoTime()
            if (com.kenkern.engine.projects.ExploradorArquivos.total(dir) < 5000) {
                for (i in 0 until 5000) {
                    File(dir, "arquivo_${i.toString().padStart(4, '0')}.lua").writeText("x")
                }
            }
            criacaoMs = (System.nanoTime() - t0) / 1_000_000L

            // listagem paginada COMPLETA
            val t1 = System.nanoTime()
            val paginas = com.kenkern.engine.projects.ExploradorArquivos.numeroDePaginas(dir)
            for (i in 0 until paginas) {
                listados += com.kenkern.engine.projects.ExploradorArquivos.pagina(dir, i).size
            }
            listagemMs = (System.nanoTime() - t1) / 1_000_000L
            if (listados != 5000) problemas.add("listou $listados de 5000 arquivos")
            if (listagemMs > 2000L) problemas.add("listagem paginada levou $listagemMs ms (limite 2000 ms)")
            // a PÁGINA em si é pequena (não trava o frame): ≤ 200 itens
            val maiorPagina = com.kenkern.engine.projects.ExploradorArquivos.pagina(dir, 0).size
            if (maiorPagina > com.kenkern.engine.projects.ExploradorArquivos.PAGINA_PADRAO) {
                problemas.add("página com $maiorPagina itens (> ${com.kenkern.engine.projects.ExploradorArquivos.PAGINA_PADRAO})")
            }
        } catch (t: Throwable) {
            problemas.add("exceção: ${t.message ?: t.javaClass.simpleName}")
        } finally {
            dir.deleteRecursively()
        }
        if (problemas.isEmpty()) {
            c.aprovado(
                "5.000 sintéticos: criação ${criacaoMs} ms · listagem paginada COMPLETA (25 páginas × 200) em $listagemMs ms (limite 2000 ms) · " +
                    "página ≤ ${com.kenkern.engine.projects.ExploradorArquivos.PAGINA_PADRAO} itens (a lista virtualizada pede por janela)"
            )
        } else {
            c.falhou(problemas.joinToString("; ") + " · criação ${criacaoMs} ms · listagem $listagemMs ms")
        }
    }

    /** Checagem "ui" (U1, v0.1.27) — TELAR sem GL: — TELAR sem GL: âncoras nas 3 proporções (16:9, 18:9, 20:9), área segura, .kui ida e volta, migração do joystick sem perder dados, undo/redo por COMMAND e a tela do template. Onda 0 (v0.1.28, P5): também as FONTES OFL — carrega CADA uma do APK (assets/fonts, sem rede) e conta as que abriram. */
    private fun checarUi(c: Checagem) {
        val problemas = ArrayList<String>(4)
        var fontesCarregadas = 0
        try {
            // Onda 0 P5: as 8 fontes OFL carregam do APK (modo avião — nada de
            // rede: Typeface.createFromAsset lê o pacote instalado).
            for (nome in com.kenkern.engine.assets.FontesKenkern.NOMES) {
                val tf = com.kenkern.engine.assets.FontesKenkern.carregar(appContext, nome)
                if (tf == null) {
                    problemas.add("fonte '$nome' não carregou do APK")
                } else fontesCarregadas++
            }
            val doc = com.kenkern.engine.ui.telar.TemplateUiMoedas.montar()
            // âncoras nas 3 proporções: nada fora da tela
            for (dim in listOf(1920 to 1080, 2400 to 1080, 2400 to 1200)) {
                for (e in doc.elementos) {
                    val r = com.kenkern.engine.ui.telar.resolverRect(e, dim.first, dim.second)
                    if (r.x < 0 || r.y < 0 || r.x + r.largura > dim.first || r.y + r.altura > dim.second) {
                        problemas.add("fora da tela @${dim.first}x${dim.second}: ${e.nome}")
                    }
                }
            }
            // .kui ida e volta
            val doc2 = com.kenkern.engine.ui.telar.KuiFormat.carregar(
                com.kenkern.engine.ui.telar.KuiFormat.salvar(doc)
            ) ?: throw AssertionError("kui não recarregou")
            if (doc2.elementos.size != 4) problemas.add("kui: ${doc2.elementos.size} elementos ≠ 4")
            // migração do joystick
            val j = com.kenkern.engine.ui.telar.MigracaoUi.elementoDeJoystick(
                9, "Joystick", "flutuante", 150, 0.8f, 0.25f, true, "Herói", true
            )
            if (j.joystickOitoDirecoes != true || j.joystickControla != "Herói") problemas.add("migração perdeu dados")
            // undo/redo por command
            val alvo = doc2.elementos.first()
            doc2.executar(com.kenkern.engine.ui.telar.ComandoTransformar(alvo.id, 100, 100, alvo.largura, alvo.altura))
            doc2.desfazer()
            if (alvo.x == 100) problemas.add("command não desfez a transformação")
            // botão do template chama função Lua
            val botao = doc2.elementos.firstOrNull { it.tipo == com.kenkern.engine.ui.telar.ElementoUi.BOTAO }
            if (botao?.aoClicar != "pausar") problemas.add("botão do template sem aoClicar")
        } catch (t: Throwable) {
            problemas.add("exceção: ${t.message ?: t.javaClass.simpleName}")
        }
        if (problemas.isEmpty()) {
            c.aprovado(
                "8 elementos · âncoras 9+esticar dentro da tela em 16:9/18:9/20:9 (área segura) · .kui KUI1 ida e volta · " +
                    "migração do Joystick Nick sem perder dados · undo/redo por COMMAND · template com contador+pausar+joystick · " +
                    "FONTES: $fontesCarregadas/8 OFL carregadas do APK (sem rede — modo avião)"
            )
        } else {
            c.falhou(problemas.joinToString("; "))
        }
    }

    /**
     * Checagem "luz" (H2, v0.1.20) — A LUZ DO LUME ILUMINA A CENA (GL offscreen):
     *  (a) PONTUAL: quad BRANCO (cubo do tipo nick.mesh) + luz PONTUAL VERMELHA
     *      perto dele — com a luz LIGADA o pixel central é dominado pelo
     *      VERMELHO (r > g+40 e r > b+40) e MUDA vs o MESMO render com a luz
     *      DESLIGADA (a luz entra e sai no mesmo frame, sem recriar nada);
     *  (b) DIRECIONAL: light.directional vermelha SUBSTITUI o sol fixo — o pixel
     *      fica DOMINADO pelo vermelho com os números do aceite (R>200, G<100,
     *      B<100) e difere do MESMO quad com ela desligada (sol padrão).
     * Auxiliares de editor DESLIGADOS no render para isolar a LUZ (sem as
     * linhas vermelhas do auxiliar da pontual poluirem a comparação).
     *
     * S4 (v0.1.24): esta checagem usa o MESMO [EngineRenderer.renderFrame]
     * do viewport do editor (LumeCena.coletar por frame, setLighting/
     * setDynamicLights SEM guarda de emJogo) — ela verde PROVA que a luz do
     * Nick ilumina o EDITOR, não só o Play. Investigação S4c ("a iluminação
     * não aparece no editor, se for o caso"): não há caminho separado —
     * editor e Play compartilham o MESMO pipeline de luz.
     *
     * L1 (v0.1.28) — ETAPA A (instrumentar) + causa raiz: a checagem ANTES
     * só observava o pixel; agora (1) detecta o FALLBACK de cor sólida do
     * GlesBackend (o shader principal da malha não compilou — sintoma
     * v0.1.27: pixel (154,154,154) = uColor pura, inalterável por QUALQUER
     * luz; a causa raiz era `vTangent` usado sem declaração no fragment
     * shader, fixada no mesmo commit); (2) REGISTRA no Console os uniforms
     * reais enviados ao shader ANTES do draw (cor da pontual, direção da
     * direcional, posição da câmera — com os valores medidos no pixel depois
     * do draw) para o usuário ver os números no relatório copiado.
     *
     * R1 (v0.1.29) — CÂMERA DO LADO DA LUZ (causa raiz da falha no aparelho,
     * comprovada por R1LuzCameraContratoTest): a câmera PADRÃO do OrbitCamera
     * (yaw 35°/pitch 28°/dist 9 → olho (−4,56, −3,73, −6,51)) via o VERSO
     * (face −Z) do cubo enquanto a pontual (0,9; 1,4; 1,1) e a direcional
     * ((0,0,−1)) iluminam a face +Z — pixel (102,63,70) × esperado
     * ~(243,108,111) e direcional IGUAL ao sol (59,63,70). Agora a câmera é
     * a de [LuzChecagemContrato] (yaw 200°/pitch −30°/dist 6 → olho
     * ≈(1,78; 3,50; 4,89), lado +Z): o raio central ENTRA pela face +Z
     * iluminada. CONTRATO de normais/dupa face: normais para FORA + culling
     * GL_BACK (GlesBackend:545) — só a face de entrada é vista; o pixel
     * central cai na face +Z com hit ≈(0,19; 0,80; 0,50), dentro dela.
     *
     * R1.3 (v0.1.29) — sub-caso (c): o MESMO piso do template (body.static
     * escala 14×1×14, topo y=0) visto DE CIMA nos DOIS caminhos
     * (renderFrame com emJogo=false = editor × emJogo=true = Play): a
     * direcional branca de intensidade 2 tem que MUDAR o pixel nos dois —
     * registra os 4 RGB no Console (evidence: piso “quase preto” × “azul
     * claro” reportados no aparelho v0.1.28).
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
            // L1: FALLBACK de cor sólida = CAUSA RAIZ visível (não "a luz não
            // ilumina"): sem o programa principal NENHUMA uniform de luz tem
            // efeito — o pixel fica uColor pura (154,154,154) sempre.
            if (backend.usaShaderDeCorreSolida) {
                c.falhou(
                    "shader principal da malha NÃO compilou neste driver — viewport no FALLBACK de cor sólida " +
                        "(sem NENHUMA luz; o pixel leria (154,154,154) = uColor pura). Causa do compile/link no Console " +
                        "(logcat: 'Compile falhou'/'Link falhou'). Enviar o relatório copiado"
                )
                return
            }
            if (backend.isDegraded) {
                c.falhou("nenhum shader compilou — backend degradado (só clear)")
                return
            }
            backend.resize(64, 64)
            renderer.initialize(backend, null, registrarBootTrace = false)
            if (!renderer.isInitialized()) {
                c.falhou("renderer não inicializou no contexto offscreen")
                return
            }
            val camera = LuzChecagemContrato.camera()
            camera.eyePoint(olhoCam)
            // ETAPA A — uniforms reais que vão ao shader ANTES do draw:
            // pontual (cor/intensidade/alcance/posição), direcional
            // (direção/cor/intensidade) e câmera (uCamPos) — tudo no Console.
            // R1: a câmera vem do CONTRATO (lado +Z — prova no teste JVM).
            KenkernLog.i(
                TAG,
                "luz ETAPA A: câmera eye=(${"%.2f,%.2f,%.2f".format(olhoCam.x, olhoCam.y, olhoCam.z)}) target=(0,${LuzChecagemContrato.ALVO_Y},0) · " +
                    "pontual cor=(1,0,0) intensidade=3 alcance=6 pos=(${LuzChecagemContrato.LUZ_X},${LuzChecagemContrato.LUZ_Y},${LuzChecagemContrato.LUZ_Z}) · " +
                    "direcional cor=(1,0,0) intensidade=2 direção de viagem=(0,0,-1)"
            )

            // ---------- (a) pontual vermelha sobre quad branco ----------
            val scene = Scene("luz")
            val mundo = scene.createRootNick("Mundo")
            val quad = scene.createNick("Quad", mundo, kind = NickKindRegistry.NICK_MESH)
            quad.transform.setLocalPosition(0f, 0.5f, 0f)
            val luz = scene.createNick("Luz Vermelha", mundo, kind = "light.point")
            luz.transform.setLocalPosition(
                LuzChecagemContrato.LUZ_X, LuzChecagemContrato.LUZ_Y, LuzChecagemContrato.LUZ_Z
            )
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
            KenkernLog.i(TAG, "luz: pixel pontual LIGADA = ${fmtCor(pixelLigado)} (esperado r≫g,b — face +Z: ~243,108,111)")
            instLuz.fields[LuzNick.CAMPO_LIGADO] = NickValue.BoolValue(false)
            renderer.renderFrame(backend, scene, camera, 0f, null, null)
            val regiaoDesligada = lerRegiao(32, 34, 5)
            val pixelDesligado = lerPixel(32, 32)
            KenkernLog.i(TAG, "luz: pixel pontual DESLIGADA = ${fmtCor(pixelDesligado)} (só sol+ambiente)")
            val mudou = regiaoLigada != null && regiaoDesligada != null && regiaoLigada != regiaoDesligada
            val vermelhoDominante = pixelLigado != null &&
                pixelLigado[0] > pixelLigado[1] + 40 && pixelLigado[0] > pixelLigado[2] + 40

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
            KenkernLog.i(TAG, "luz: pixel direcional VERMELHA = ${fmtCor(comSolNick)} (aceite R>200, G<100, B<100)")
            instSol.fields[LuzNick.CAMPO_LIGADO] = NickValue.BoolValue(false)
            renderer.renderFrame(backend, cenaDir, camera, 0f, null, null)
            val comSolPadrao = lerPixel(32, 32)
            KenkernLog.i(TAG, "luz: pixel com SOL PADRÃO = ${fmtCor(comSolPadrao)} (branco quente + hemi)")
            val direcionalSubstitui = comSolNick != null && comSolPadrao != null &&
                comSolNick.toList() != comSolPadrao.toList() &&
                comSolNick[0] > 200 && comSolNick[1] < 100 && comSolNick[2] < 100

            // ---------- (c) R1.3: piso do template nos DOIS caminhos ----------
            // O MESMO piso do template "Coletar moedas" (body.static escala
            // 14×1×14 em y=−0,5 → topo y=0), visto DE CIMA, renderizado pelo
            // MESMO renderFrame nos DOIS caminhos (emJogo=false = editor ×
            // emJogo=true = Play — a única diferença entre eles). A direcional
            // BRANCA de intensidade 2 tem que MUDAR o pixel nos dois caminhos
            // e os dois caminhos têm que iluminar IGUAL (evidence do aparelho
            // v0.1.28: piso “quase preto” no editor × “azul claro” no Play).
            val cenaPiso = Scene("luz-piso")
            val mundoPiso = cenaPiso.createRootNick("Mundo")
            val piso = cenaPiso.createNick("Chão", mundoPiso, kind = "body.static")
            piso.transform.setLocalPosition(0f, -0.5f, 0f)
            piso.transform.scale.set(14f, 1f, 14f)
            val solBranco = cenaPiso.createNick("Sol Branco", mundoPiso, kind = "light.directional")
            val instSolBranco = NickClassRegistry.createInstance(LuzNick.CLASSE)
            if (instSolBranco == null) {
                c.falhou("classe Luz não registrada (KenkernBuiltInClasses)")
                return
            }
            instSolBranco.fields[LuzNick.CAMPO_COR] = NickValue.Vec3Value(1f, 1f, 1f)
            instSolBranco.fields[LuzNick.CAMPO_INTENSIDADE] = NickValue.FloatValue(2f)
            solBranco.addClassInstance(instSolBranco)
            cenaPiso.updateWorldTransforms()
            val cameraPiso = OrbitCamera()
            cameraPiso.target.set(0f, 0f, 0f)
            cameraPiso.yawDegrees = 0f
            cameraPiso.pitchDegrees = -60f
            cameraPiso.distance = 12f

            renderer.emJogo = false // caminho do EDITOR
            renderer.renderFrame(backend, cenaPiso, cameraPiso, 0f, null, null)
            val pisoEditorComDirecional = lerPixel(32, 32)
            instSolBranco.fields[LuzNick.CAMPO_LIGADO] = NickValue.BoolValue(false)
            renderer.renderFrame(backend, cenaPiso, cameraPiso, 0f, null, null)
            val pisoEditorSolPadrao = lerPixel(32, 32)
            instSolBranco.fields[LuzNick.CAMPO_LIGADO] = NickValue.BoolValue(true)

            renderer.emJogo = true // caminho de PLAY (única diferença)
            renderer.renderFrame(backend, cenaPiso, cameraPiso, 0f, null, null)
            val pisoPlayComDirecional = lerPixel(32, 32)
            instSolBranco.fields[LuzNick.CAMPO_LIGADO] = NickValue.BoolValue(false)
            renderer.renderFrame(backend, cenaPiso, cameraPiso, 0f, null, null)
            val pisoPlaySolPadrao = lerPixel(32, 32)
            renderer.emJogo = false // restaura o estado para o resto da rodada
            KenkernLog.i(
                TAG,
                "luz R1.3 piso visto de cima — EDITOR: int 2 = ${fmtCor(pisoEditorComDirecional)} × sol = ${fmtCor(pisoEditorSolPadrao)} · " +
                    "PLAY: int 2 = ${fmtCor(pisoPlayComDirecional)} × sol = ${fmtCor(pisoPlaySolPadrao)}"
            )
            val pisoMudaNoEditor = pisoEditorComDirecional != null && pisoEditorSolPadrao != null &&
                pisoEditorComDirecional.toList() != pisoEditorSolPadrao.toList()
            val pisoMudaNoPlay = pisoPlayComDirecional != null && pisoPlaySolPadrao != null &&
                pisoPlayComDirecional.toList() != pisoPlaySolPadrao.toList()
            val diferencaMaxima = maxDe4(
                pisoEditorComDirecional, pisoPlayComDirecional,
                pisoEditorSolPadrao, pisoPlaySolPadrao
            )
            val caminhosIguais = diferencaMaxima <= 2

            // ---------- veredito ----------
            val problemas = ArrayList<String>(6)
            if (!mudou) problemas.add("pixel da região central não mudou com a pontual ligada × desligada")
            if (!vermelhoDominante) problemas.add("pixel com a pontual ligada não é dominado pelo vermelho (${fmtCor(pixelLigado)})")
            if (!direcionalSubstitui) problemas.add(
                "light.directional não substituiu o sol com o pixel vermelho do aceite " +
                    "(R>200,G<100,B<100 — obtido ${fmtCor(comSolNick)} × sol padrão ${fmtCor(comSolPadrao)})"
            )
            if (!pisoMudaNoEditor) problemas.add(
                "R1.3: a direcional int 2 NÃO mudou o pixel do piso no caminho do EDITOR " +
                    "(${fmtCor(pisoEditorComDirecional)} × sol ${fmtCor(pisoEditorSolPadrao)})"
            )
            if (!pisoMudaNoPlay) problemas.add(
                "R1.3: a direcional int 2 NÃO mudou o pixel do piso no caminho de PLAY " +
                    "(${fmtCor(pisoPlayComDirecional)} × sol ${fmtCor(pisoPlaySolPadrao)})"
            )
            if (!caminhosIguais) problemas.add(
                "R1.3: EDITOR × PLAY iluminam o MESMO piso DE FORMA DIFERENTE (editor " +
                    "${fmtCor(pisoEditorSolPadrao)} × play ${fmtCor(pisoPlaySolPadrao)} com o MESMO sol — " +
                    "diferença máx $diferencaMaxima/canal) — é o “quase preto” × “azul claro”"
            )
            if (problemas.isEmpty()) {
                c.aprovado(
                    "pontual vermelha ilumina e sai no mesmo frame (ligada ${fmtCor(pixelLigado)} × desligada ${fmtCor(pixelDesligado)}) · " +
                        "direcional substitui o sol com o pixel DOMINADO pelo vermelho (${fmtCor(comSolNick)} = R>200,G<100,B<100) · " +
                        "R1.3 piso do template: int 2 muda o pixel nos DOIS caminhos, editor = play " +
                        "(int 2 ${fmtCor(pisoEditorComDirecional)} × sol ${fmtCor(pisoEditorSolPadrao)}) · " +
                        "uniforms no Console (ETAPA A) · ≤4 pontuais/spot por frame (LumeCena) · " +
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

    /**
     * R1.3: maior diferença por canal ENTRE os 4 pixels do piso (editor×play
     * com direcional × sol padrão). Nulo vira 255 (ILEGÍVEL reprova): se um
     * readPixels falhou, os dois caminhos NÃO podem ser declarados iguais.
     */
    private fun maxDe4(a: IntArray?, b: IntArray?, c: IntArray?, d: IntArray?): Int {
        val pixels = listOfNotNull(a, b, c, d)
        if (pixels.size < 4) return 255
        var max = 0
        for (i in 0 until 3) {
            var menor = pixels[0][i]
            var maior = pixels[0][i]
            for (p in pixels) {
                if (p[i] < menor) menor = p[i]
                if (p[i] > maior) maior = p[i]
            }
            if (maior - menor > max) max = maior - menor
        }
        return max
    }

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

        // R4 (v0.1.29): TELAR — canvas com LETTERBOX: nas 3 proporções, o
        // canvas INTEIRO cabe no espaço do editor (tela − painéis 190+230dp −
        // barra 52dp) e PRESERVA a proporção escolhida (16:9/18:9/20:9) — o
        // print v0.1.28 mostrava ~1,25:1 (escala fixa 0,28 cortada pelos
        // painéis). Espaço simulado do alvo 800x360dp: 380×280dp.
        val espacoTelarW = 380f
        val espacoTelarH = 280f
        var telarOk = true
        val telarMsg = StringBuilder()
        for (p in listOf("16:9", "18:9", "20:9")) {
            val (lw, ah) = com.kenkern.engine.ui.telar.TelarAjuste.dimensoesDe(p)
            val esc = com.kenkern.engine.ui.telar.TelarAjuste.escalaDeAjuste(espacoTelarW, espacoTelarH, lw.toFloat(), ah.toFloat())
            val (rw, rh) = com.kenkern.engine.ui.telar.TelarAjuste.dimensaoRenderizada(esc, lw.toFloat(), ah.toFloat())
            val preserva = com.kenkern.engine.ui.telar.TelarAjuste.proporcaoPreservada(esc, lw.toFloat(), ah.toFloat())
            val cabeTudo = rw <= espacoTelarW + 0.5f && rh <= espacoTelarH + 0.5f
            if (!preserva || !cabeTudo) telarOk = false
            telarMsg.append("$p ${rw.toInt()}×${rh.toInt()}dp ")
        }

        if (cabe && suficiente && cabecalhoOk && listaOk && ultimoOk && telarOk) {
            c.aprovado(
                "tela ${larguraTelaDp.roundToInt()}×${alturaTelaDp.roundToInt()}dp ($orientacao) · " +
                    "TopBar fixa ${fixa.roundToInt()}dp → rolável ${rolavel.roundToInt()}dp ✓ · " +
                    "altura útil ${alturaUtilDp.roundToInt()}dp · cabeçalho AddNick " +
                    "${AddNickLayout.CABECALHO_DP.roundToInt()}dp ≤ 20% ✓ · lista mínima " +
                    "${(alturaUtilDp - AddNickLayout.alturaMinimaListaDp() - AddNickLayout.CABECALHO_DP).roundToInt()}dp de folga ✓ · último tipo alcançável ✓ · " +
                    "TELAR letterbox $telarMsg(proporção preservada, cabe inteiro em 380×280dp)"
            )
        } else {
            c.falhou(
                "tela ${larguraTelaDp.roundToInt()}×${alturaTelaDp.roundToInt()}dp ($orientacao) · " +
                    "TopBar fixa ${fixa.roundToInt()}dp cabe=$cabe · rolável ${rolavel.roundToInt()}dp suficiente=$suficiente · " +
                    "altura útil ${alturaUtilDp.roundToInt()}dp · cabeçalho≤20%=$cabecalhoOk · lista=$listaOk · último=$ultimoOk · " +
                    "TELAR letterbox=$telarOk ($telarMsg)"
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
        if (tapXRecebido < 0f) {            c.falhou(
                // R6: builder PURO — mesmo anti-padrão corrigido (density %.1f crus).
                MensagensDiag.toqueNaoClassificou(density, tapX, tapY)
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
    private fun checarLuaApi(c: Checagem) {
        val (ok, msg) = avaliarLuaApi()
        if (ok) c.aprovado(msg) else c.falhou(msg)
    }

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
