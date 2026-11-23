package com.kenkern.engine.render

import android.util.Log
import com.kenkern.engine.assets.mesh.KernReader
import com.kenkern.engine.assets.mesh.KernWriter
import com.kenkern.engine.assets.mesh.MeshData
import com.kenkern.engine.core.Nick
import com.kenkern.engine.core.MalhaNick
import com.kenkern.engine.core.ReferenceId
import com.kenkern.engine.core.RotationFromMatrix
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.math.Mat4
import com.kenkern.engine.core.math.Quaternion
import com.kenkern.engine.core.math.Vec3
import com.kenkern.engine.diag.KenkernLog
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renderer do viewport da KENKERN:
 *  - céu (gradiente zênite→horizonte + sol discreto na direção da luz);
 *  - GRADE INFINITA (1 draw call): raio contra o plano Y=0 no fragment shader,
 *    anti-moiré por fwidth, fade por distância, dupla face (câmera abaixo do chão);
 *  - REGRA "CENA = TELA" (Bug A, v0.1.17): desenha SOMENTE Nicks da cena com
 *    malha (classe Malha) ou folhas sem filhos (cubo padrão #9A9A9A, Lambert +
 *    ambiente hemisférico); containers (raiz "Mundo") NÃO são desenhados;
 *    NENHUMA geometria de demonstração existe no caminho de render — o torus
 *    .KERN de teste mora SÓ no Diagnóstico (offscreen);
 *  - contorno laranja #FFA940 (inverted hull) no Nick selecionado;
 *  - gizmo de eixos no canto superior direito, girando com a câmera;
 *  - gizmo de transformação como MALHA (alça sob o dedo amarela, demais apagados).
 *
 * PERF: a fila de geometria é RESETADA a cada frame (sem isso ela acumula
 * submissões até saturar e redesenha a cena centenas de vezes por frame).
 * Orçamento: céu 1 + malhas N + contorno 1 + grade 1 + gizmo ≤ 2 + eixos 1.
 *
 * ANTI-CRASH: [initialize] e [renderFrame] nunca lançam exceção — falhas de GPU
 * degradam o viewport (modo seguro) em vez de derrubar o app.
 */
class EngineRenderer {

    private val geometry: GeometryPipeline = DefaultGeometryPipeline()
    private val lighting: LightingPipeline = DefaultLightingPipeline()

    private var initialized = false
    private var cubeHandle = 0
    private var gizmoHandle = 0
    private var gizmoVertexCount = 0

    // T0.3: WIREFRAME do colisor do Nick SELECIONADO (overlay de edição — a
    // visualização do collider do Inspetor). Buffer reconstruído SÓ quando a
    // chave (nick + forma + centro + tamanho) muda; 1 draw de linhas por frame.
    private var colliderWireHandle = 0
    private var colliderWireCount = 0
    private var colliderWireKey = ""
    private val colliderMvp = Mat4()
    private var kernRoundtripOk = false

    // E5 (v0.1.18): AUXILIARES DE EDITOR — ícones/formas dos Nicks SEM malha
    // (luz/câmera/área/sinal/surgimento/vazio). Um buffer de LINHAS por forma
    // (cor por grupo) + um equivalente em ÂMBAR para o SELECIONADO (contorno de
    // seleção). A PIRÂMIDE da câmera usa o FOV REAL e é reconstruída quando
    // fov/aspect mudam (chave). Desenham só no editor (nunca em Play), NÃO
    // contam como malha na StatusBar (E6) e são pegáveis com 24dp de tolerância.
    private val helperHandles = IntArray(6)
    private val helperHandlesSel = IntArray(6)
    private val helperVertexCount = IntArray(6)
    private var helperPiramideKey = ""
    private val helperCor = FloatArray(3)
    private val helperMvp = Mat4()
    private val helperModel = Mat4()
    private val helperPos = Vec3()
    private val helperRot = Quaternion()
    private val helperScaleVec = Vec3()
    private val helperRotExtractor = RotationFromMatrix()
    private val auxNicks = arrayOfNulls<Nick>(256)
    private var auxCount = 0

    /** E5: botão 'Mostrar auxiliares' da TopBar (padrão LIGADO). */
    @Volatile
    var mostrarAuxiliares: Boolean = true

    /** E5: em Play os auxiliares SOMEM (nunca no jogo exportado). */
    @Volatile
    var emJogo: Boolean = false

    // Buffers GL do gizmo de transformação (MALHA de triângulos, 1 VBO por modo)
    private var tgTrisMove = 0
    private var tgTrisRotate = 0
    private var tgTrisScale = 0
    private var tGizmo: TransformGizmo? = null

    // Estatísticas para a barra de status (zero alocação por frame).
    @Volatile
    var fps: Int = 0
        private set

    @Volatile
    var drawCount: Int = 0
        private set

    /** ms de update (timestep fixo + world transforms), média móvel EMA 0,9. */
    @Volatile
    var updateMs: Float = 0f
        private set

    /** ms de draw (beginFrame → endFrame), média móvel EMA 0,9. */
    @Volatile
    var drawMs: Float = 0f
        private set

    /**
     * M da StatusBar "N nicks · M malhas" (E6): GRUPOS DE MATERIAL desenhados
     * no último frame — 1 por submitGroup (grupo do .KERN) e 1 por submit
     * (malha sem grupos/cubo padrão = grupo implícito). Contornos, gizmos,
     * grade, céu e AUXILIARES DE EDITOR (E5) não entram. Containers não
     * desenhaveis e malhas ainda carregando não contam (não desenharam).
     */
    @Volatile
    var malhasDesenhadas: Int = 0
        private set

    private var framesThisSecond = 0
    private var fpsWindowStartNanos = 0L

    /** Chamado pelo thread GL uma vez por frame (escrita volátil, sem alocação). */
    fun reportUpdateMs(measuredMs: Float) {
        updateMs = if (updateMs == 0f) measuredMs else updateMs * 0.9f + measuredMs * 0.1f
    }

    private val proj = Mat4()
    private val view = Mat4()
    private val viewProj = Mat4()

    // Scratches do frame (pré-alocados; nada é criado no loop).
    private val scratchDir = FloatArray(3)
    private val scratchAmbient = FloatArray(3)
    private val scratchLightColor = FloatArray(3)
    private val camFwd = Vec3()
    private val camEye = Vec3()
    private val sunDir = Vec3()

    // Gizmo: rotação da view (translação zerada) × escala fixa.
    private val gizmoRot = Mat4()
    private val gizmoScale = Mat4()
    private val gizmoMvp = Mat4()

    // Contorno (inverted hull): world × inflação.
    private val outlineInflation = Mat4()
    private val outlineScratch = Mat4()

    // Gizmo de transformação (scratches do thread GL)
    private val tgWorldPos = Vec3()
    private val tgWorldRot = Quaternion()
    private val tgModel = Mat4()
    private val tgMvp = Mat4()
    private val tgIdentRot = Quaternion()
    private val tgScaleVec = Vec3()
    private val tgRotExtractor = RotationFromMatrix()

    private val nickStack = arrayOfNulls<Nick>(512)

    // Cores do editor (especificação de visual).
    private val materialGray = 0.6039f   // #9A9A9A
    private val outlineR = 1.0f
    private val outlineG = 0.6627f       // #FFA940
    private val outlineB = 0.2510f

    /**
     * Inicialização segura: céu/grade infinita/cubo/gizmos; falha = viewport limpo.
     * [registrarBootTrace] = false SOMENTE no Diagnóstico offscreen (a checagem
     * "cena = tela" inicializa o backend sem marcar o boot_trace — armadilha 12).
     */
    fun initialize(
        backend: RenderBackend,
        transformGizmo: TransformGizmo? = null,
        registrarBootTrace: Boolean = true
    ) {
        initialized = false
        cubeHandle = 0
        gizmoHandle = 0
        tgTrisMove = 0; tgTrisRotate = 0; tgTrisScale = 0
        // O wireframe do collider é cacheado por seleção/parâmetros, mas seu
        // handle pertence ao contexto GL. Invalide-o mesmo quando a chave não
        // mudou: no primeiro frame após initialize ele será recriado.
        if (colliderWireHandle != 0) backend.destroyLineBuffer(colliderWireHandle)
        colliderWireHandle = 0
        colliderWireCount = 0
        colliderWireKey = ""
        tGizmo = transformGizmo
        kernRoundtripOk = false
        malhasDesenhadas = 0
        for (i in helperHandles.indices) {
            if (helperHandles[i] != 0) backend.destroyLineBuffer(helperHandles[i])
            if (helperHandlesSel[i] != 0) backend.destroyLineBuffer(helperHandlesSel[i])
            helperHandles[i] = 0; helperHandlesSel[i] = 0; helperVertexCount[i] = 0
        }
        helperPiramideKey = ""
        auxCount = 0
        outlineInflation.setIdentity()
        outlineInflation.m[0] = 1.06f; outlineInflation.m[5] = 1.06f; outlineInflation.m[10] = 1.06f
        gizmoScale.setIdentity()
        gizmoScale.m[0] = 0.62f; gizmoScale.m[5] = 0.62f; gizmoScale.m[10] = 0.62f
        if (!backend.initialize(registrarBootTrace)) {
            Log.e(TAG, "Backend não inicializou; viewport em modo seguro")
            return
        }
        // Cada construção é independente: se uma falhar, o resto ainda desenha —
        // mas a falha É REGISTRADA no Console (regra: runCatching não engole).
        runCatching { buildCube(backend) }.onFailure { KenkernLog.w(TAG, "cube não construído (viewport segue sem cubo)", it) }
        runCatching { buildGizmo(backend) }.onFailure { KenkernLog.w(TAG, "gizmo de eixos não construído", it) }
        runCatching { buildTransformGizmoBuffers(backend) }.onFailure { KenkernLog.w(TAG, "buffers do gizmo de transformação não construídos", it) }
        runCatching { buildEditorHelpers(backend) }.onFailure { KenkernLog.w(TAG, "auxiliares de editor não construídos (luz/câmera/área ficam invisíveis)", it) }
        // Validação do formato .KERN (torus gerado → serializado → relido), SEM
        // upload de GPU e SEM desenho: geometria de demonstração NÃO existe mais
        // no caminho de render (Bug A). O torus renderiza SÓ no Diagnóstico.
        runCatching { validarKernRoundtrip() }.onFailure { KenkernLog.w(TAG, "roundtrip .KERN do torus de teste falhou", it) }
        initialized = true
    }

    /** Upload ÚNICO dos buffers estáticos do gizmo de transformação (1 VBO de triângulos por modo). */
    private fun buildTransformGizmoBuffers(backend: RenderBackend) {
        val g = tGizmo ?: return
        if (g.moveData.tris.isNotEmpty()) tgTrisMove = backend.createGizmoBuffer(g.moveData.tris)
        if (g.rotateData.tris.isNotEmpty()) tgTrisRotate = backend.createGizmoBuffer(g.rotateData.tris)
        if (g.scaleData.tris.isNotEmpty()) tgTrisScale = backend.createGizmoBuffer(g.scaleData.tris)
    }

    /**
     * E5: geometria estática dos AUXILIARES DE EDITOR — um buffer de linhas por
     * forma na cor do GRUPO + um equivalente em ÂMBAR (selecionado). A pirâmide
     * da câmera (FOV real) é construída por chave no draw (fov/aspect mudam).
     */
    private fun buildEditorHelpers(backend: RenderBackend) {
        val corAmbar = floatArrayOf(outlineR, outlineG, outlineB)
        // LUZ (seta/esfera/cone) · CÂMERA (pirâmide: aqui versão padrão 60°/1,33
        // — o draw refaz com o FOV/aspect reais quando a chave muda) · ÁREAS
        // (caixa) · BASE (losango genérico).
        val formas = arrayOf(
            EditorHelpers.buildSeta(corDe(EditorHelpers.FORMA_SETA)),
            EditorHelpers.buildEsfera(corDe(EditorHelpers.FORMA_ESFERA)),
            EditorHelpers.buildCone(corDe(EditorHelpers.FORMA_CONE)),
            EditorHelpers.buildPiramide(corDe(EditorHelpers.FORMA_PIRAMIDE), 60f, 4f / 3f),
            EditorHelpers.buildCaixa(corDe(EditorHelpers.FORMA_CAIXA)),
            EditorHelpers.buildDiamante(corDe(EditorHelpers.FORMA_DIAMANTE))
        )
        val ambar = arrayOf(
            EditorHelpers.buildSeta(corAmbar),
            EditorHelpers.buildEsfera(corAmbar),
            EditorHelpers.buildCone(corAmbar),
            EditorHelpers.buildPiramide(corAmbar, 60f, 4f / 3f),
            EditorHelpers.buildCaixa(corAmbar),
            EditorHelpers.buildDiamante(corAmbar)
        )
        for (f in 0 until 6) {
            helperVertexCount[f] = formas[f].size / 6
            helperHandles[f] = backend.createColoredLineBuffer(formas[f])
            helperHandlesSel[f] = backend.createColoredLineBuffer(ambar[f])
        }
    }

    /** Cor de GRUPO da forma do auxiliar (mesma paleta do EditorHelpers). */
    private fun corDe(forma: Int): FloatArray {
        return when (forma) {
            EditorHelpers.FORMA_SETA, EditorHelpers.FORMA_ESFERA, EditorHelpers.FORMA_CONE ->
                floatArrayOf(1.00f, 0.835f, 0.310f)
            EditorHelpers.FORMA_PIRAMIDE -> floatArrayOf(0.310f, 0.765f, 0.969f)
            EditorHelpers.FORMA_CAIXA -> floatArrayOf(0.400f, 0.733f, 0.416f)
            else -> floatArrayOf(0.690f, 0.745f, 0.773f)
        }
    }

    fun isInitialized(): Boolean = initialized

    // ---------- Construção (uma vez, fora do loop) ----------

    private fun buildCube(backend: RenderBackend) {
        val p = CUBE_POSITIONS
        val n = CUBE_NORMALS_PER_QUAD
        val positions = FloatArray(24 * 3)
        val normals = FloatArray(24 * 3)
        val uvs = FloatArray(24 * 2)
        val tangents = FloatArray(24 * 3)
        for (v in 0 until 24) {
            positions[v * 3] = p[v * 3]; positions[v * 3 + 1] = p[v * 3 + 1]; positions[v * 3 + 2] = p[v * 3 + 2]
            normals[v * 3] = n[(v / 4) * 3]; normals[v * 3 + 1] = n[(v / 4) * 3 + 1]; normals[v * 3 + 2] = n[(v / 4) * 3 + 2]
            uvs[v * 2] = if (v % 4 == 0 || v % 4 == 3) 0f else 1f
            uvs[v * 2 + 1] = if (v % 4 < 2) 0f else 1f
            tangents[v * 3] = 1f; tangents[v * 3 + 1] = 0f; tangents[v * 3 + 2] = 0f
        }
        val indices = IntArray(36)
        var ii = 0
        for (quad in 0 until 6) {
            val b = quad * 4
            indices[ii++] = b; indices[ii++] = b + 1; indices[ii++] = b + 2
            indices[ii++] = b; indices[ii++] = b + 2; indices[ii++] = b + 3
        }
        cubeHandle = backend.createMesh(MeshData(positions, normals, uvs, indices, Vec3(-0.5f), Vec3(0.5f)))
    }

    /** Gizmo de eixos: 6 linhas (±X, ±Y, ±Z) coloridas por vértice — 1 VBO, 1 draw call. */
    private fun buildGizmo(backend: RenderBackend) {
        val data = FloatArray(12 * 2 * 6)
        var i = 0
        fun line(
            ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float,
            r: Float, g: Float, b: Float
        ) {
            data[i++] = ax; data[i++] = ay; data[i++] = az; data[i++] = r; data[i++] = g; data[i++] = b
            data[i++] = bx; data[i++] = by; data[i++] = bz; data[i++] = r; data[i++] = g; data[i++] = b
        }
        line(0f, 0f, 0f, 1f, 0f, 0f, 0.95f, 0.25f, 0.25f)   // +X vermelho
        line(0f, 0f, 0f, -1f, 0f, 0f, 0.40f, 0.12f, 0.12f)  // -X apagado
        line(0f, 0f, 0f, 0f, 1f, 0f, 0.30f, 0.90f, 0.35f)   // +Y verde
        line(0f, 0f, 0f, 0f, -1f, 0f, 0.12f, 0.34f, 0.14f)  // -Y apagado
        line(0f, 0f, 0f, 0f, 0f, 1f, 0.35f, 0.55f, 0.95f)   // +Z azul
        line(0f, 0f, 0f, 0f, 0f, -1f, 0.12f, 0.18f, 0.40f)  // -Z apagado
        gizmoVertexCount = data.size / 6
        gizmoHandle = backend.createColoredLineBuffer(data)
    }

    /**
     * VALIDAÇÃO do formato .KERN (Bug A, v0.1.17): torus gerado proceduralmente,
     * serializado em .KERN e lido de volta com o KernReader. SEM upload de GPU e
     * SEM desenho — o viewport não tem mais geometria de demonstração; o torus
     * renderiza SÓ na checagem "cena = tela" do Diagnóstico (offscreen).
     */
    private fun validarKernRoundtrip() {
        val kernMesh = gerarTorusKern()
        val bytes = KernWriter.write(kernMesh)
        val reloaded = KernReader.read(bytes)
        if (reloaded.vertexCount != kernMesh.vertexCount) {
            throw IllegalStateException(
                "roundtrip .KERN divergiu: ${kernMesh.vertexCount} → ${reloaded.vertexCount} vértices"
            )
        }
        kernRoundtripOk = true
    }

    fun isKernRoundtripOk(): Boolean = kernRoundtripOk

    // ---------- Frame ----------

    fun renderFrame(
        backend: RenderBackend,
        scene: Scene?,
        camera: OrbitCamera,
        engineTime: Float,
        selectedId: ReferenceId?,
        gizmoState: GizmoSharedState? = null
    ) {
        updateFps(System.nanoTime())
        val drawStart = System.nanoTime()
        try {
            renderFrameInner(backend, scene, camera, engineTime, selectedId, gizmoState)
        } catch (e: Throwable) {
            // Última linha de defesa: nenhum erro de GL/frame derruba o processo.
            Log.e(TAG, "Frame descartado", e)
            KenkernLog.e(TAG, "Frame descartado", e)
            runCatching { backend.beginFrame(0.075f, 0.075f, 0.105f, 1f) }
                .onFailure { KenkernLog.e(TAG, "clear emergencial do frame também falhou", it) }
        }
        val drawElapsedMs = (System.nanoTime() - drawStart) * 1e-6f
        drawMs = if (drawMs == 0f) drawElapsedMs else drawMs * 0.9f + drawElapsedMs * 0.1f
    }

    private fun renderFrameInner(
        backend: RenderBackend,
        scene: Scene?,
        camera: OrbitCamera,
        engineTime: Float,
        selectedId: ReferenceId?,
        gizmoState: GizmoSharedState?
    ) {
        // CRÍTICO: zera a fila de submissões — sem isso ela acumula entre frames
        // até saturar a capacidade e redesenhar a cena centenas de vezes.
        geometry.reset()

        // Malhas e texturas importadas pendentes: carrega FORA do flush (orçamento de 6 ms
        // cada) — nunca trava o frame que está desenhando (anti-trave; streaming completo = F2.11).
        runCatching { AssetMeshLibrary.processarPendentes(backend) }
            .onFailure { KenkernLog.e(TAG, "carga de malhas importadas falhou", it) }
        runCatching { TextureLibrary.processarPendentes(backend) }
            .onFailure { KenkernLog.e(TAG, "carga de texturas .KIMG falhou", it) }

        backend.beginFrame(0.075f, 0.075f, 0.105f, 1f)
        var skyDrawn = 0
        var gridDrawn = 0
        var gizmoDrawn = 0
        // M da StatusBar "N nicks · M malhas" (E6): conta GRUPOS DE MATERIAL
        // desenhados (malha importada por grupo OU cubo padrão = 1 grupo
        // implícito) — contornos e overlays (incl. auxiliares E5) não contam.
        var malhasDrawnThisFrame = 0

        val aspect = backend.viewportWidth.toFloat() / backend.viewportHeight.toFloat()
        camera.projection(proj, aspect)
        camera.view(view)
        viewProj.multiply(proj, view)

        // Base da câmera derivada da matriz de view (linhas = eixos da câmera em mundo).
        camFwd.set(-view.m[2], -view.m[6], -view.m[10])
        // Olho correto nos DOIS modos (orbital e voo).
        camera.eyePoint(camEye)

        // Sol = direção PARA a luz (oposta ao sentido de viagem da luz direcional).
        lighting.fillDirectional(scratchDir)
        lighting.fillAmbient(scratchAmbient)
        val intensity = lighting.fillLightColor(scratchLightColor)
        sunDir.set(-scratchDir[0], -scratchDir[1], -scratchDir[2]).normalize()

        // Céu (1 draw call): gradiente zênite→horizonte + sol discreto.
        if (sunDir.lengthSquared() > 0.5f) {
            backend.drawSky(
                camFwd.x, camFwd.y, camFwd.z,
                view.m[0], view.m[4], view.m[8],
                view.m[1], view.m[5], view.m[9],
                tanHalfFov(camera.fovDegrees), aspect,
                sunDir.x, sunDir.y, sunDir.z
            )
            skyDrawn = 1
        }

        backend.setLighting(
            scratchDir[0], scratchDir[1], scratchDir[2],
            scratchAmbient[0], scratchAmbient[1], scratchAmbient[2],
            scratchLightColor[0], scratchLightColor[1], scratchLightColor[2],
            intensity
        )

        // NICKS DA CENA (regra "cena = tela"): travessia indexada (Iterator de
        // ArrayList aloca por frame). Containers sem malha (raiz "Mundo") NÃO
        // desenham — só Nicks com malha importada ou folhas (cubo padrão).
        // E5: Nicks AUXILIARES (luz/câmera/área/sinal/surgimento/vazio) são
        // COLETADOS aqui e desenhados DEPOIS como auxiliares de editor.
        auxCount = 0
        if (scene != null) {
            val roots = scene.roots
            var top = 0
            for (ri in roots.indices) {
                if (top >= nickStack.size) break
                nickStack[top++] = roots[ri]
            }
            while (top > 0) {
                val nick = nickStack[--top] ?: continue
                if (emJogo && !nick.active) continue // pai inativo desativa a subárvore em Play
                // REGRA "CENA = TELA": container sem malha (ex.: "Mundo") não
                // desenha — tudo que aparece no viewport é Nick selecionável.
                if (nick.active && nick.visible && MalhaNick.desenhavel(nick)) {
                    val caminhoMalha = MalhaNick.path(nick)
                    if (caminhoMalha != null) {
                        // Nick importado (ETAPA A): desenha a MALHA do .KERN em vez do cubo.
                        val h = AssetMeshLibrary.handleDe(caminhoMalha)
                        if (h != 0) {
                            val malha = AssetMeshLibrary.malhaDe(caminhoMalha)
                            if (malha != null && malha.gruposMaterial.isNotEmpty()) {
                                // GRUPOS DE MATERIAL (B.3/C.4): um sub-range por grupo,
                                // com textura .KIMG do material (fallback = cor do material).
                                for (g in malha.gruposMaterial) {
                                    val material = malha.materiais.getOrNull(g.materialIndex)
                                    var texHandle = 0
                                    var mr = materialGray; var mg = materialGray; var mb = materialGray; var ma = 1f
                                    if (material != null) {
                                        mr = material.baseColorR; mg = material.baseColorG
                                        mb = material.baseColorB; ma = material.baseColorA
                                        if (material.temTexturaBase && material.texturaBasePath.isNotEmpty()) {
                                            texHandle = TextureLibrary.handleDe(material.texturaBasePath)
                                            if (texHandle == 0) {
                                                TextureLibrary.marcarNecessario(material.texturaBasePath)
                                                // textura ainda não pronta: desenha cor do material
                                                texHandle = 0
                                            }
                                        }
                                    }
                                    geometry.submitGroup(
                                        h, g.indexOffset, g.indexCount, texHandle,
                                        nick.transform.worldMatrix, mr, mg, mb, ma
                                    )
                                    malhasDrawnThisFrame++
                                }
                            } else {
                                geometry.submit(
                                    h,
                                    nick.transform.worldMatrix,
                                    materialGray, materialGray, materialGray, 1f
                                )
                                malhasDrawnThisFrame++
                            }
                            if (nick.id == selectedId) {
                                outlineScratch.multiply(nick.transform.worldMatrix, outlineInflation)
                                geometry.submitOutline(
                                    h, outlineScratch, outlineR, outlineG, outlineB, 1f
                                )
                            }
                        } else {
                            // Ainda não na cache de GPU (ou falhou): enfileira a carga.
                            // O Nick continua selecionável/picker pela AABB da classe Malha.
                            AssetMeshLibrary.marcarNecessario(caminhoMalha)
                        }
                    } else if (cubeHandle != 0) {
                        geometry.submit(
                            cubeHandle,
                            nick.transform.worldMatrix,
                            materialGray, materialGray, materialGray, 1f
                        )
                        malhasDrawnThisFrame++
                        if (nick.id == selectedId) {
                            // Contorno #FFA940 (inverted hull): world × inflação 1.06.
                            outlineScratch.multiply(nick.transform.worldMatrix, outlineInflation)
                            geometry.submitOutline(
                                cubeHandle, outlineScratch, outlineR, outlineG, outlineB, 1f
                            )
                        }
                    }
                } else if (nick.active && nick.visible && MalhaNick.ehAuxiliar(nick) && auxCount < auxNicks.size) {
                    // E5: auxiliar de editor (luz/câmera/área/ícone) — desenhado
                    // DEPOIS, com tamanho constante na tela; NÃO conta como malha.
                    auxNicks[auxCount++] = nick
                }
                val children = nick.children
                for (ci in children.indices) {
                    if (top >= nickStack.size) break
                    nickStack[top++] = children[ci]
                }
            }
        }

        geometry.flush(backend, viewProj)

        // GRADE INFINITA (1 draw call): depois das malhas (elas a ocluem via depth),
        // raio contra o plano Y=0 no fragment; dupla face; fade por fwidth + distância.
        backend.drawInfiniteGrid(
            camFwd.x, camFwd.y, camFwd.z,
            view.m[0], view.m[4], view.m[8],
            view.m[1], view.m[5], view.m[9],
            tanHalfFov(camera.fovDegrees), aspect,
            camEye.x, camEye.y, camEye.z
        )
        gridDrawn = 1

        // T0.3: COLISOR do Nick selecionado (wireframe verde, espaço do Nick):
        // Caixa/Convexo/Malha = paralelepípedo; Esfera/Cápsula/Cilindro = curvas.
        var colliderDc = 0
        if (scene != null && selectedId != null) {
            val selNick = scene.findNick(selectedId)
            val colisor = selNick?.findClassInstance(MalhaNick.CLASSE_COLISOR)
            if (selNick != null && colisor != null) {
                val forma = colisor.getString("forma", "box")
                val centro = colisor.fields["centro"] as? com.kenkern.engine.core.NickValue.Vec3Value
                val tamanho = colisor.fields["tamanho"] as? com.kenkern.engine.core.NickValue.Vec3Value
                val cx = centro?.x ?: 0f; val cy = centro?.y ?: 0f; val cz = centro?.z ?: 0f
                val tx = tamanho?.x ?: 1f; val ty = tamanho?.y ?: 1f; val tz = tamanho?.z ?: 1f
                val chave = "${selNick.id.value}|$forma|$cx,$cy,$cz|$tx,$ty,$tz"
                if (chave != colliderWireKey) {
                    if (colliderWireHandle != 0) backend.destroyLineBuffer(colliderWireHandle)
                    val dados = buildColliderWireframe(forma, cx, cy, cz, tx, ty, tz)
                    colliderWireCount = dados.size / 6
                    colliderWireHandle = backend.createColoredLineBuffer(dados)
                    colliderWireKey = chave
                }
                if (colliderWireHandle != 0 && colliderWireCount > 0) {
                    colliderMvp.multiply(viewProj, selNick.transform.worldMatrix)
                    // E5: drawWorldLines — linhas no ESPAÇO DO MUNDO (viewport
                    // completo + depth). O drawGizmo antigo desenhava o collider
                    // no MINI-VIEWPORT do canto (gizmo de eixos): errado desde T0.3.
                    backend.drawWorldLines(colliderWireHandle, colliderWireCount, colliderMvp)
                    colliderDc = 1
                }
            } else if (colliderWireHandle != 0) {
                backend.destroyLineBuffer(colliderWireHandle)
                colliderWireHandle = 0; colliderWireCount = 0; colliderWireKey = ""
            }
        }

        // E5 (v0.1.18): AUXILIARES DE EDITOR — luz/câmera/área/ícone dos Nicks
        // SEM malha. SÓ no editor (nunca em Play, nunca no jogo exportado), com
        // tamanho CONSTANTE na tela (escala pela distância, como o gizmo), cor
        // por grupo e ÂMBAR quando selecionado. NÃO contam como malha (E6):
        // cada auxiliar = 1 draw de linhas no espaço do mundo.
        var helperDc = 0
        if (mostrarAuxiliares && !emJogo && auxCount > 0) {
            // Pirâmide da câmera: reconstruída SÓ quando o FOV REAL/aspect mudam.
            val chavePiramide = "${camera.fovDegrees}|${aspect}"
            if (chavePiramide != helperPiramideKey) {
                if (helperHandles[EditorHelpers.FORMA_PIRAMIDE] != 0) {
                    backend.destroyLineBuffer(helperHandles[EditorHelpers.FORMA_PIRAMIDE])
                }
                if (helperHandlesSel[EditorHelpers.FORMA_PIRAMIDE] != 0) {
                    backend.destroyLineBuffer(helperHandlesSel[EditorHelpers.FORMA_PIRAMIDE])
                }
                EditorHelpers.corDoGrupo("camera.basic", helperCor)
                val p = EditorHelpers.buildPiramide(helperCor, camera.fovDegrees, aspect)
                helperVertexCount[EditorHelpers.FORMA_PIRAMIDE] = p.size / 6
                helperHandles[EditorHelpers.FORMA_PIRAMIDE] = backend.createColoredLineBuffer(p)
                val ps = EditorHelpers.buildPiramide(
                    floatArrayOf(outlineR, outlineG, outlineB), camera.fovDegrees, aspect
                )
                helperHandlesSel[EditorHelpers.FORMA_PIRAMIDE] = backend.createColoredLineBuffer(ps)
                helperPiramideKey = chavePiramide
            }
            val meiaFovTan = tanHalfFov(camera.fovDegrees)
            for (ai in 0 until auxCount) {
                val nick = auxNicks[ai] ?: continue
                val forma = EditorHelpers.formaDo(nick.kind)
                val selecionado = nick.id == selectedId
                val handle = if (selecionado) helperHandlesSel[forma] else helperHandles[forma]
                if (handle == 0 || helperVertexCount[forma] == 0) continue
                val wm = nick.transform.worldMatrix
                helperPos.set(wm.m[12], wm.m[13], wm.m[14])
                val dist = camEye.distanceTo(helperPos).coerceAtLeast(0.5f)
                val escala = dist * meiaFovTan * 2f * EditorHelpers.FRACAO_TELA
                extractWorldRotationTo(wm, helperRot, helperRotExtractor)
                helperScaleVec.set(escala, escala, escala)
                helperModel.setTRS(helperPos, helperRot, helperScaleVec)
                helperMvp.multiply(viewProj, helperModel)
                backend.drawWorldLines(handle, helperVertexCount[forma], helperMvp)
                helperDc++
            }
        }

        // Gizmo de transformação como MALHA: ≤ 2 dc (completo / apagado + alça amarela).
        var tGizmoDc = 0
        val g = tGizmo
        if (g != null && gizmoState != null && selectedId != null && scene != null) {
            val nick = scene.findNick(selectedId)
            if (nick != null && nick.active && nick.visible) {
                val wm = nick.transform.worldMatrix
                tgWorldPos.set(wm.m[12], wm.m[13], wm.m[14])
                val ss = g.screenScaleFor(camera, tgWorldPos)
                extractWorldRotation(wm, tgWorldRot)
                if (gizmoState.space == GizmoSpace.LOCAL) {
                    tgScaleVec.set(ss, ss, ss)
                    tgModel.setTRS(tgWorldPos, tgWorldRot, tgScaleVec)
                } else {
                    tgIdentRot.setIdentity()
                    tgScaleVec.set(ss, ss, ss)
                    tgModel.setTRS(tgWorldPos, tgIdentRot, tgScaleVec)
                }
                tgMvp.multiply(viewProj, tgModel)

                val data = g.dataFor(gizmoState.mode)
                val trisHandle = when (gizmoState.mode) {
                    GizmoMode.MOVE -> tgTrisMove
                    GizmoMode.ROTATE -> tgTrisRotate
                    GizmoMode.SCALE -> tgTrisScale
                }
                val total = data.tris.size / 7
                if (trisHandle != 0 && total > 0) {
                    val hl = gizmoState.highlighted
                    val range = if (hl != GizmoHandle.NONE) data.triRanges[hl] else null
                    if (range != null && range[1] > 0) {
                        // demais eixos APAGADOS + alça sob o dedo em AMARELO
                        backend.drawGizmoTrisDimmed(trisHandle, 0, total, tgMvp, 0.35f)
                        tGizmoDc++
                        backend.drawGizmoTrisSolid(
                            trisHandle, range[0], range[1], tgMvp, 1f, 0.85f, 0.10f, 1f
                        )
                        tGizmoDc++
                    } else {
                        backend.drawGizmoTris(trisHandle, 0, total, tgMvp)
                        tGizmoDc++
                    }
                }
            }
        }

        // Gizmo de eixos (1 draw call): rotação da view × escala, canto superior direito.
        if (!emJogo && gizmoHandle != 0) {
            gizmoRot.copyFrom(view)
            gizmoRot.m[12] = 0f; gizmoRot.m[13] = 0f; gizmoRot.m[14] = 0f
            gizmoMvp.multiply(gizmoScale, gizmoRot)
            backend.drawGizmo(gizmoHandle, gizmoVertexCount, gizmoMvp)
            gizmoDrawn = 1
        }

        backend.endFrame()

        // Total real de draw calls do frame (estatística da StatusBar).
        drawCount = skyDrawn + gridDrawn + colliderDc + helperDc +
            ((geometry as? DefaultGeometryPipeline)?.lastFlushCount ?: 0) +
            gizmoDrawn + tGizmoDc
        malhasDesenhadas = malhasDrawnThisFrame
    }

    /**
     * T0.3: linhas do WIREFRAME do colisor em espaço LOCAL (6 floats/vértice,
     * pares GL_LINES — mesmo formato do gizmo de eixos). Cor verde de física.
     * Convexo/Malha desenham a AABB (limite da forma) nesta fase.
     */
    private fun buildColliderWireframe(
        forma: String, cx: Float, cy: Float, cz: Float,
        tx: Float, ty: Float, tz: Float
    ): FloatArray {
        val r = 0.35f; val g = 0.85f; val b = 0.55f
        val linhas = ArrayList<Float>(24 * 6 * 2)
        val linha: (Float, Float, Float, Float, Float, Float) -> Unit = { ax, ay, az, bx, by, bz ->
            linhas.add(ax); linhas.add(ay); linhas.add(az); linhas.add(r); linhas.add(g); linhas.add(b)
            linhas.add(bx); linhas.add(by); linhas.add(bz); linhas.add(r); linhas.add(g); linhas.add(b)
        }
        val hx = tx * 0.5f; val hy = ty * 0.5f; val hz = tz * 0.5f
        when (forma) {
            "sphere" -> {
                val raio = (hx + hy + hz) / 3f
                circuloXz(linha, cx, cy, cz, raio)
                circuloXy(linha, cx, cy, cz, raio)
                circuloYz(linha, cx, cy, cz, raio)
            }
            "cylinder" -> {
                val raio = (hx + hz) * 0.5f
                circuloXz(linha, cx, cy + hy, cz, raio)
                circuloXz(linha, cx, cy - hy, cz, raio)
                for (a in floatArrayOf(0f, 90f, 180f, 270f)) {
                    val rad = Math.toRadians(a.toDouble())
                    val px = cx + (raio * kotlin.math.cos(rad)).toFloat()
                    val pz = cz + (raio * kotlin.math.sin(rad)).toFloat()
                    linha(px, cy + hy, pz, px, cy - hy, pz)
                }
            }
            "capsule" -> {
                val raio = (hx + hz) * 0.5f
                val cilindro = (ty - raio * 2f).coerceAtLeast(0f)
                val topo = cy + cilindro * 0.5f
                val base = cy - cilindro * 0.5f
                circuloXz(linha, cx, topo, cz, raio)
                circuloXz(linha, cx, base, cz, raio)
                for (a in floatArrayOf(0f, 90f, 180f, 270f)) {
                    val rad = Math.toRadians(a.toDouble())
                    val px = cx + (raio * kotlin.math.cos(rad)).toFloat()
                    val pz = cz + (raio * kotlin.math.sin(rad)).toFloat()
                    linha(px, topo, pz, px, base, pz)
                }
                // Calotas: semicírculos verticais (frente e lado).
                arcoVertical(linha, cx, topo, cz, raio, planoXz = true)
                arcoVertical(linha, cx, base, cz, raio, planoXz = true, paraCima = false)
                arcoVertical(linha, cx, topo, cz, raio, planoXz = false)
                arcoVertical(linha, cx, base, cz, raio, planoXz = false, paraCima = false)
            }
            else -> {
                // box / convex / mesh: AABB (limite da forma) — 12 arestas.
                val x0 = cx - hx; val x1 = cx + hx
                val y0 = cy - hy; val y1 = cy + hy
                val z0 = cz - hz; val z1 = cz + hz
                linha(x0, y0, z0, x1, y0, z0); linha(x1, y0, z0, x1, y0, z1)
                linha(x1, y0, z1, x0, y0, z1); linha(x0, y0, z1, x0, y0, z0)
                linha(x0, y1, z0, x1, y1, z0); linha(x1, y1, z0, x1, y1, z1)
                linha(x1, y1, z1, x0, y1, z1); linha(x0, y1, z1, x0, y1, z0)
                linha(x0, y0, z0, x0, y1, z0); linha(x1, y0, z0, x1, y1, z0)
                linha(x1, y0, z1, x1, y1, z1); linha(x0, y0, z1, x0, y1, z1)
            }
        }
        val out = FloatArray(linhas.size)
        for (i in linhas.indices) out[i] = linhas[i]
        return out
    }

    private fun circuloXz(
        linha: (Float, Float, Float, Float, Float, Float) -> Unit,
        cx: Float, cy: Float, cz: Float, raio: Float
    ) {
        val seg = 24
        var px = cx + raio; var pz = cz
        for (i in 1..seg) {
            val rad = (2.0 * Math.PI * i / seg)
            val nx = cx + (raio * kotlin.math.cos(rad)).toFloat()
            val nz = cz + (raio * kotlin.math.sin(rad)).toFloat()
            linha(px, cy, pz, nx, cy, nz)
            px = nx; pz = nz
        }
    }

    private fun circuloXy(
        linha: (Float, Float, Float, Float, Float, Float) -> Unit,
        cx: Float, cy: Float, cz: Float, raio: Float
    ) {
        val seg = 24
        var px = cx + raio; var py = cy
        for (i in 1..seg) {
            val rad = (2.0 * Math.PI * i / seg)
            val nx = cx + (raio * kotlin.math.cos(rad)).toFloat()
            val ny = cy + (raio * kotlin.math.sin(rad)).toFloat()
            linha(px, py, cz, nx, ny, cz)
            px = nx; py = ny
        }
    }

    private fun circuloYz(
        linha: (Float, Float, Float, Float, Float, Float) -> Unit,
        cx: Float, cy: Float, cz: Float, raio: Float
    ) {
        val seg = 24
        var py = cy + raio; var pz = cz
        for (i in 1..seg) {
            val rad = (2.0 * Math.PI * i / seg)
            val ny = cy + (raio * kotlin.math.cos(rad)).toFloat()
            val nz = cz + (raio * kotlin.math.sin(rad)).toFloat()
            linha(cx, py, pz, cx, ny, nz)
            py = ny; pz = nz
        }
    }

    /** Semicírculo vertical da cápsula (90°→270° ou para cima/baixo). */
    private fun arcoVertical(
        linha: (Float, Float, Float, Float, Float, Float) -> Unit,
        cx: Float, cy: Float, cz: Float, raio: Float,
        planoXz: Boolean,
        paraCima: Boolean = true
    ) {
        val seg = 12
        val inicio = if (paraCima) 0.0 else Math.PI
        var pa = inicio
        for (i in 1..seg) {
            val na = inicio + Math.PI * i / seg
            val ax = (raio * kotlin.math.cos(pa)).toFloat()
            val ay = (raio * kotlin.math.sin(pa)).toFloat()
            val bx = (raio * kotlin.math.cos(na)).toFloat()
            val by = (raio * kotlin.math.sin(na)).toFloat()
            if (planoXz) linha(cx + ax, cy + ay, cz, cx + bx, cy + by, cz)
            else linha(cx, cy + ay, cz + ax, cx, cy + by, cz + bx)
            pa = na
        }
    }

    private fun extractWorldRotation(m: Mat4, out: Quaternion) {
        val mm = m.m
        val sx = kotlin.math.sqrt(mm[0] * mm[0] + mm[1] * mm[1] + mm[2] * mm[2])
        val sy = kotlin.math.sqrt(mm[4] * mm[4] + mm[5] * mm[5] + mm[6] * mm[6])
        val sz = kotlin.math.sqrt(mm[8] * mm[8] + mm[9] * mm[9] + mm[10] * mm[10])
        if (sx > 1e-9f && sy > 1e-9f && sz > 1e-9f) {
            tgRotExtractor.set(
                mm[0] / sx, mm[4] / sy, mm[8] / sz,
                mm[1] / sx, mm[5] / sy, mm[9] / sz,
                mm[2] / sx, mm[6] / sy, mm[10] / sz
            )
            tgRotExtractor.toMatrixComponents(out)
        } else {
            out.setIdentity()
        }
    }

    /**
     * E5: extração de rotação com EXTRACTOR PRÓPRIO (auxiliares de editor) —
     * mesma matemática do [extractWorldRotation] sem disputar a instância do
     * gizmo de transformação no mesmo frame.
     */
    private fun extractWorldRotationTo(m: Mat4, out: Quaternion, extractor: RotationFromMatrix) {
        val mm = m.m
        val sx = kotlin.math.sqrt(mm[0] * mm[0] + mm[1] * mm[1] + mm[2] * mm[2])
        val sy = kotlin.math.sqrt(mm[4] * mm[4] + mm[5] * mm[5] + mm[6] * mm[6])
        val sz = kotlin.math.sqrt(mm[8] * mm[8] + mm[9] * mm[9] + mm[10] * mm[10])
        if (sx > 1e-9f && sy > 1e-9f && sz > 1e-9f) {
            extractor.set(
                mm[0] / sx, mm[4] / sy, mm[8] / sz,
                mm[1] / sx, mm[5] / sy, mm[9] / sz,
                mm[2] / sx, mm[6] / sy, mm[10] / sz
            )
            extractor.toMatrixComponents(out)
        } else {
            out.setIdentity()
        }
    }

    private fun tanHalfFov(fovDegrees: Float): Float {
        val half = Math.toRadians(fovDegrees.toDouble()).toFloat() * 0.5f
        return kotlin.math.tan(half)
    }

    private fun updateFps(nowNanos: Long) {
        framesThisSecond++
        if (fpsWindowStartNanos == 0L) {
            fpsWindowStartNanos = nowNanos
            return
        }
        val elapsed = nowNanos - fpsWindowStartNanos
        if (elapsed >= 1_000_000_000L) {
            fps = (framesThisSecond * 1_000_000_000L / elapsed).toInt().coerceAtLeast(0)
            framesThisSecond = 0
            fpsWindowStartNanos = nowNanos
        }
    }

    companion object {
        private const val TAG = "Kenkern/EngineRenderer"
        private val CUBE_POSITIONS = floatArrayOf(
            -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f,
            -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, -0.5f, -0.5f,
            -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f,
            -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f,
            0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0.5f,
            -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, -0.5f, -0.5f, -0.5f, -0.5f
        )
        private val CUBE_NORMALS_PER_QUAD = floatArrayOf(
            0f, 0f, 1f,
            0f, 0f, -1f,
            0f, 1f, 0f,
            0f, -1f, 0f,
            1f, 0f, 0f,
            -1f, 0f, 0f
        )

        /**
         * TORUS DE TESTE (48×24 segmentos, anel 1,7, tubo 0,6): a MESMA malha
         * que validava o formato .KERN no viewport da Fase 1. Desde a v0.1.17
         * (Bug A) ela NÃO é desenhada no viewport — o Diagnóstico (checagem
         * "cena = tela") usa esta função para gerar, serializar, reler e
         * renderizar o torus OFFSCREEN. Pública para o Diagnóstico.
         */
        fun gerarTorusKern(): com.kenkern.engine.assets.mesh.KernMesh {
            val builder = KernWriter.Builder()
            val segU = 48
            val segV = 24
            val bigR = 1.7f
            val smallR = 0.6f
            for (iu in 0 until segU) {
                val u = (iu.toFloat() / segU) * 2f * Math.PI.toFloat()
                val cu = cos(u); val su = sin(u)
                for (iv in 0 until segV) {
                    val v = (iv.toFloat() / segV) * 2f * Math.PI.toFloat()
                    val cv = cos(v); val sv = sin(v)
                    val ringR = bigR + smallR * cv
                    val px = ringR * cu
                    val py = smallR * sv
                    val pz = ringR * su
                    // normal analítica
                    val nx = cv * cu; val ny = sv; val nz = cv * su
                    // tangente ao longo de u
                    val tx = -su; val ty = 0f; val tz = cu
                    builder.vertex(px, py, pz, nx, ny, nz, iu.toFloat() / segU, iv.toFloat() / segV, tx, ty, tz)
                }
            }
            for (iu in 0 until segU) {
                val iu2 = (iu + 1) % segU
                for (iv in 0 until segV) {
                    val iv2 = (iv + 1) % segV
                    val a = iu * segV + iv
                    val b = iu2 * segV + iv
                    val c = iu2 * segV + iv2
                    val d = iu * segV + iv2
                    builder.triangle(a, b, c)
                    builder.triangle(a, c, d)
                }
            }
            return builder.build()
        }
    }
}
