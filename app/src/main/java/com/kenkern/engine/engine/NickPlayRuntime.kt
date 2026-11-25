package com.kenkern.engine.engine

import com.kenkern.engine.core.MalhaNick
import com.kenkern.engine.core.Nick
import com.kenkern.engine.core.NickClassInstance
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.Scene
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Primeiro runtime de jogo: move o primeiro player ativo, aplica gravidade e
 * colide com corpos estáticos AABB. O runtime trabalha na Scene recebida (o
 * chamador é responsável por passar uma cópia descartável para Play).
 *
 * M1.2 (v0.1.21): COLISORES — body.static (cubo 1 m), body.collider com a
 * classe Colisor em TODAS as formas (caixa/esfera/cápsula/cilindro/convexo/
 * malha — parada pela AABB da forma, a MESMA convenção do wireframe do
 * editor; SEM solver completo: solver exato entra na Fase 8+). GATILHOS:
 * area.volume dispara on_trigger na ENTRADA do jogador (borda, 1×). Eventos
 * on_collision (parada contra corpo) e on_trigger vão para o Lua (ponte M1.1).
 *
 * S2 (v0.1.24): TODOS os corpos dinâmicos caem — não só o player. CAUSAS
 * RAIZ corrigidas:
 *  (1) getBlockingBounds do body.static IGNORAVA a classe Colisor (chão
 *      "Static Body + Colisor" colidia como cubo unitário — o volume 14×2×14
 *      nunca era usado; agora a classe define o volume, igual ao body.collider);
 *  (2) SÓ o primeiro body.player entrava na simulação — Rigid Body,
 *      Character Body e Collider Body NUNCA caíam ("no aparelho nada cai");
 *      agora TODO Nick ativo dos kinds body.player/body.rigid/
 *      body.character/body.collider entra na simulação com gravidade
 *      própria e bloqueio por estáticos/colisores. O INPUT (mover/pular)
 *      continua só do player; o pulo, só com o player apoiado.
 */
class NickPlayRuntime(private val scene: Scene) {

    /** Primeiro body.player efetivamente ativo, em ordem pre-order da árvore. */
    val player: Nick?

    /** Primeira câmera ativa de cada tipo; escolhidas uma vez na inicialização. */
    val followCamera: Nick?
    val basicCamera: Nick?

    /**
     * S2 (v0.1.24): TODOS os corpos dinâmicos ativos da cena (body.player,
     * body.rigid, body.character, body.collider), em ordem da árvore —
     * quem ENTRA na simulação de Play (o overlay de debug e a JVM provam).
     */
    val corposSimulados: List<Nick>

    /** S2: índice do player em [corposSimulados] (−1 se a cena não tem player). */
    private val playerIndex: Int

    /**
     * M1.1/M1.2: ponte Lua dos scripts de Play — eventos on_collision e
     * on_trigger saem daqui (opcional; sem scripts, nada é chamado).
     */
    var scripts: NickLuaScripts? = null

    /** M1.3: input de jogo (pulo com borda de 1 tick). */
    private var pedirPulo = false
    private var apoiado = false

    private val traversal = ArrayList<Nick>()
    private val playerLocalCenter = FloatArray(3)
    private val playerLocalHalf = FloatArray(3)
    private val playerBounds = FloatArray(6)
    private val obstacleBounds = FloatArray(6)
    private val localDeltaScratch = FloatArray(3)

    /** S2: velocidade vertical e estado "no chão" POR corpo (indexado por [corposSimulados]). */
    private val velocidadesY: FloatArray
    private val apoiados: BooleanArray
    private val colisaoPorCorpo: Array<String?>

    init {
        scene.updateWorldTransforms()
        traversal.clear()
        pushRootsInTreeOrder()
        var foundPlayer: Nick? = null
        var foundSpawn: Nick? = null
        var foundFollow: Nick? = null
        var foundBasic: Nick? = null
        val dinamicos = ArrayList<Nick>()
        while (traversal.isNotEmpty()) {
            val nick = traversal.removeAt(traversal.lastIndex)
            if (!nick.active) continue
            if (foundPlayer == null && nick.kind == "body.player") foundPlayer = nick
            if (foundSpawn == null && nick.kind == "area.spawn") foundSpawn = nick
            if (foundFollow == null && nick.kind == "camera.follow") foundFollow = nick
            if (foundBasic == null && nick.kind == "camera.basic") foundBasic = nick
            // S2: TODO corpo dinâmico ativo entra na simulação (não só o player).
            if (isDynamicBody(nick)) dinamicos.add(nick)
            pushChildrenInTreeOrder(nick)
        }
        player = foundPlayer
        followCamera = foundFollow
        basicCamera = foundBasic
        corposSimulados = dinamicos
        playerIndex = dinamicos.indexOfFirst { it === foundPlayer }
        velocidadesY = FloatArray(dinamicos.size)
        apoiados = BooleanArray(dinamicos.size)
        colisaoPorCorpo = arrayOfNulls(dinamicos.size)

        val activePlayer = player
        if (activePlayer != null) {
            val spawn = foundSpawn
            if (spawn != null) setPlayerWorldPosition(
                spawn.transform.worldMatrix.m[12],
                spawn.transform.worldMatrix.m[13],
                spawn.transform.worldMatrix.m[14]
            )
            scene.updateWorldTransforms()
            readPlayerBounds()
        }
    }

    /** S2: kinds que ENTRAM na simulação de Play (gravidade + colisão). */
    private fun isDynamicBody(nick: Nick): Boolean = when (nick.kind) {
        "body.player", "body.rigid", "body.character", "body.collider" -> true
        else -> false
    }

    /**
     * S2 (v0.1.24): snapshot de debug para o OVERLAY DE PLAY — chamado pela
     * UI a ~2 Hz (pode alocar; NUNCA é chamado no hot loop). Uma linha por
     * corpo simulado: nome · kind · velocidade · no chão · última colisão.
     */
    fun snapshotDebug(linhas: MutableList<String>) {
        for (i in corposSimulados.indices) {
            val corpo = corposSimulados[i]
            linhas.add(
                "${corpo.displayName("pt")} · ${corpo.kind} · vy=${velocidadesY[i]} · " +
                    "${if (apoiados[i]) "no chão" else "no ar"} · " +
                    "colisão=${colisaoPorCorpo[i] ?: "—"}"
            )
        }
    }

    /**
     * Avança os corpos. moveX/moveZ são eixos de mundo (SÓ o player recebe
     * input); a entrada diagonal é normalizada. dt é limitado a 0.25 s para
     * proteger o primeiro runtime de pausas longas, e deslocamentos são
     * resolvidos por varredura em cada eixo para não atravessar
     * paredes/floors em um único passo.
     */
    fun step(dt: Float, moveX: Float, moveZ: Float) {
        if (dt <= 0f || !dt.isFinite()) return
        scene.updateWorldTransforms()

        var x = if (moveX.isFinite()) moveX else 0f
        var z = if (moveZ.isFinite()) moveZ else 0f
        val inputLength = sqrt(x * x + z * z)
        if (inputLength > 1f) {
            x /= inputLength
            z /= inputLength
        }

        val deltaTime = dt.coerceAtMost(MAX_STEP_SECONDS)
        // M1.3: PULO — impulso único quando o PLAYER está apoiado (borda
        // consumida 1×; S2: índice do player vem do init, não é mais 0).
        if (pedirPulo) {
            pedirPulo = false
            if (apoiado && playerIndex >= 0) velocidadesY[playerIndex] = VELOCIDADE_PULO
        }
        val dx = x * PLAYER_SPEED * deltaTime
        val dz = z * PLAYER_SPEED * deltaTime

        for (i in corposSimulados.indices) {
            val corpo = corposSimulados[i]
            stepCorpo(i, deltaTime, if (corpo === player) dx else 0f, if (corpo === player) dz else 0f)
        }

        // S2: bounds do PLAYER atualizados antes dos GATILHOS (o loop acima
        // deixou os bounds do ÚLTIMO corpo em playerBounds).
        readPlayerBounds()

        // M1.2: GATILHOS (áreas) — borda de ENTRADA dispara on_trigger 1 vez;
        // sair e voltar dispara de novo. Colisor da área (se houver) define o
        // volume; sem Colisor, o cubo de 1 m do Area Nick é o volume.
        checarGatilhos(deltaTime)

        // M1.2/S2: evento de COLISÃO — cada corpo que PAROU contra um
        // obstáculo neste step dispara on_collision NO SEU script.
        val bridge = scripts
        if (bridge != null) {
            for (i in corposSimulados.indices) {
                val nome = colisaoPorCorpo[i] ?: continue
                colisaoPorCorpo[i] = null
                bridge.emitirColisao(corposSimulados[i], nome)
            }
        }
        scene.updateWorldTransforms()
    }

    /** S2: um corpo dinâmico — gravidade própria, varredura por eixo, apoio. */
    private fun stepCorpo(i: Int, deltaTime: Float, dx: Float, dz: Float) {
        val corpo = corposSimulados[i]
        if (!isEffectivelyActive(corpo)) return

        velocidadesY[i] = (velocidadesY[i] - GRAVITY * deltaTime).coerceAtLeast(-TERMINAL_VELOCITY)
        val dy = velocidadesY[i] * deltaTime

        moveAxis(corpo, i, AXIS_X, dx)
        moveAxis(corpo, i, AXIS_Z, dz)
        val movedY = moveAxis(corpo, i, AXIS_Y, dy)
        val apoiadoAgora = dy < 0f && movedY > dy + COLLISION_EPSILON // parou ao CAIR = no chão
        apoiados[i] = apoiadoAgora
        if (corpo === player) apoiado = apoiadoAgora
        if ((dy < 0f && movedY > dy + COLLISION_EPSILON) ||
            (dy > 0f && movedY < dy - COLLISION_EPSILON)
        ) velocidadesY[i] = 0f
    }

    /** M1.3: botão PULAR (borda) — consumido no próximo step com o jogador apoiado. */
    fun pular() {
        pedirPulo = true
    }

    /**
     * Retorna se a AABB do player sobrepõe a AABB de uma área ativa. O teste é
     * feito na pose mais recente do runtime; tocar apenas pelas faces não conta.
     */
    fun isPlayerInside(area: Nick): Boolean {
        val activePlayer = player ?: return false
        if (area.kind != "area.volume" || !isEffectivelyActive(area) ||
            !isEffectivelyActive(activePlayer)
        ) return false
        scene.updateWorldTransforms()
        readPlayerBounds()
        areaWorldBounds(area, obstacleBounds)
        return overlaps(playerBounds, obstacleBounds)
    }

    /** Move no espaço de mundo e converte a translação para o pai do player. */
    private fun setPlayerWorldPosition(worldX: Float, worldY: Float, worldZ: Float) {
        val activePlayer = player ?: return
        setNickWorldPosition(activePlayer, worldX, worldY, worldZ)
    }

    /** S2: posição mundial para QUALQUER corpo (spawn do player e futuros spawns). */
    private fun setNickWorldPosition(nick: Nick, worldX: Float, worldY: Float, worldZ: Float) {
        val parent = nick.parent
        if (parent == null) {
            nick.transform.position.set(worldX, worldY, worldZ)
            return
        }
        val m = parent.transform.worldMatrix.m
        if (!inverseParentDelta(m, worldX - m[12], worldY - m[13], worldZ - m[14], localDeltaScratch)) return
        nick.transform.position.set(localDeltaScratch[0], localDeltaScratch[1], localDeltaScratch[2])
    }

    /** Converte um deslocamento de mundo em translação local, sem alocar. */
    private fun translateNickWorld(nick: Nick, dx: Float, dy: Float, dz: Float): Boolean {
        val parent = nick.parent
        if (parent == null) {
            nick.transform.position.x += dx
            nick.transform.position.y += dy
            nick.transform.position.z += dz
        } else {
            val m = parent.transform.worldMatrix.m
            if (!inverseParentDelta(m, dx, dy, dz, localDeltaScratch)) return false
            nick.transform.position.x += localDeltaScratch[0]
            nick.transform.position.y += localDeltaScratch[1]
            nick.transform.position.z += localDeltaScratch[2]
        }
        refreshNickWorldTransform(nick)
        return true
    }

    private fun refreshNickWorldTransform(nick: Nick) {
        val transform = nick.transform
        transform.localMatrix.setTRS(transform.position, transform.rotation, transform.scale)
        val parent = nick.parent
        if (parent == null) transform.worldMatrix.copyFrom(transform.localMatrix)
        else transform.worldMatrix.multiply(parent.transform.worldMatrix, transform.localMatrix)
    }

    /** Inverte a parte linear da matriz affine do pai (sem temporários). */
    private fun inverseParentDelta(m: FloatArray, dx: Float, dy: Float, dz: Float, out: FloatArray): Boolean {
        val determinant = m[0] * (m[5] * m[10] - m[9] * m[6]) -
            m[4] * (m[1] * m[10] - m[9] * m[2]) + m[8] * (m[1] * m[6] - m[5] * m[2])
        if (abs(determinant) < MATRIX_EPSILON) return false
        val inv = 1f / determinant
        out[0] = ((m[5] * m[10] - m[9] * m[6]) * dx + (m[8] * m[6] - m[4] * m[10]) * dy + (m[4] * m[9] - m[8] * m[5]) * dz) * inv
        out[1] = ((m[9] * m[2] - m[1] * m[10]) * dx + (m[0] * m[10] - m[8] * m[2]) * dy + (m[8] * m[1] - m[0] * m[9]) * dz) * inv
        out[2] = ((m[1] * m[6] - m[5] * m[2]) * dx + (m[4] * m[2] - m[0] * m[6]) * dy + (m[0] * m[5] - m[4] * m[1]) * dz) * inv
        return true
    }

    /** S2: varredura exata em um eixo contra AABBs estáticas ATIVAS, para QUALQUER corpo. */
    private fun moveAxis(corpo: Nick, indiceCorpo: Int, axis: Int, requestedDelta: Float): Float {
        if (requestedDelta == 0f || !requestedDelta.isFinite()) return 0f
        readBodyBounds(corpo)
        var allowed = requestedDelta
        traversal.clear()
        pushRootsInTreeOrder()
        var bloqueouPor: Nick? = null
        while (traversal.isNotEmpty()) {
            val nick = traversal.removeAt(traversal.lastIndex)
            if (!nick.active) continue
            if (nick === corpo) continue // ignora o próprio corpo e sua subárvore
            if (isBlockingStatic(nick)) {
                getBlockingBounds(nick, obstacleBounds)
                if (otherAxesOverlap(axis)) {
                    val obstacleNear = obstacleBounds[axis]
                    val obstacleFar = obstacleBounds[axis + 3]
                    if (requestedDelta > 0f) {
                        val distance = obstacleNear - playerBounds[axis + 3]
                        if (distance >= -COLLISION_EPSILON && distance < allowed) {
                            allowed = distance.coerceAtLeast(0f)
                            bloqueouPor = nick
                        }
                    } else {
                        val distance = obstacleFar - playerBounds[axis]
                        if (distance <= COLLISION_EPSILON && distance > allowed) {
                            allowed = distance.coerceAtMost(0f)
                            bloqueouPor = nick
                        }
                    }
                }
            }
            pushChildrenInTreeOrder(nick)
        }
        if (allowed != 0f) {
            if (!translateNickWorld(
                    corpo,
                    if (axis == AXIS_X) allowed else 0f,
                    if (axis == AXIS_Y) allowed else 0f,
                    if (axis == AXIS_Z) allowed else 0f
                )
            ) return 0f
            readBodyBounds(corpo)
        }
        // M1.2/S2: PAROU (permitido < pedido) → guarda quem bloqueou (evento).
        if (allowed < requestedDelta - COLLISION_EPSILON && bloqueouPor != null) {
            colisaoPorCorpo[indiceCorpo] = bloqueouPor.displayName("pt")
        }
        return allowed
    }

    private fun isBlockingStatic(nick: Nick): Boolean {
        if (nick.kind == "body.static") return true
        if (nick.kind != "body.collider") return false
        // M1.2: TODAS as formas param (sem solver completo — caixa/esfera/
        // cápsula/cilindro/convexo/malha usam a AABB da forma, a MESMA do
        // wireframe do editor; solver exato entra na Fase 8+). S2: um
        // body.collider também é DINÂMICO — ele bloqueia os OUTROS corpos
        // e ele mesmo cai/para nos estáticos.
        return true
    }

    private fun getBlockingBounds(nick: Nick, out: FloatArray) {
        val collider = findClass(nick, MalhaNick.CLASSE_COLISOR)
        // S2: body.static SEM classe Colisor = cubo unitário × escala do
        // transform (world matrix). COM Colisor = volume da classe (igual ao
        // body.collider) — antes a classe era IGNORADA para estáticos.
        if (nick.kind == "body.static" && collider == null) {
            setWorldBounds(nick, 0f, 0f, 0f, 0.5f, 0.5f, 0.5f, out)
            return
        }
        val center = collider?.fields?.get("centro") as? NickValue.Vec3Value
        val size = collider?.fields?.get("tamanho") as? NickValue.Vec3Value
        val hx = abs(size?.x ?: 1f) * 0.5f
        val hy = abs(size?.y ?: 1f) * 0.5f
        val hz = abs(size?.z ?: 1f) * 0.5f
        when (collider?.getString("forma", "box")) {
            "sphere" -> {
                // MESMA convenção do wireframe do editor (raio = média das
                // meia-extensões); AABB da esfera para a parada.
                val raio = (hx + hy + hz) / 3f
                setWorldBounds(nick, center?.x ?: 0f, center?.y ?: 0f, center?.z ?: 0f, raio, raio, raio, out)
            }
            "capsule" -> {
                // MESMA convenção do wireframe: raio = média de hx/hz, altura hy.
                val raio = (hx + hz) * 0.5f
                setWorldBounds(nick, center?.x ?: 0f, center?.y ?: 0f, center?.z ?: 0f, raio, hy, raio, out)
            }
            else ->
                setWorldBounds(
                    nick, center?.x ?: 0f, center?.y ?: 0f, center?.z ?: 0f,
                    hx, hy, hz, out
                )
        }
    }

    /**
     * M1.2: GATILHOS — cada area.volume ATIVO com sobreposição da AABB do
     * jogador dispara on_trigger UMA VEZ na entrada (sai e volta = dispara
     * de novo). O volume é o Colisor da área (se houver) ou o cubo de 1 m.
     */
    private fun areaWorldBounds(area: Nick, out: FloatArray) {
        val collider = findClass(area, MalhaNick.CLASSE_COLISOR)
        val center = collider?.fields?.get("centro") as? NickValue.Vec3Value
        val size = collider?.fields?.get("tamanho") as? NickValue.Vec3Value
        val hx = abs(size?.x ?: 1f) * 0.5f
        val hy = abs(size?.y ?: 1f) * 0.5f
        val hz = abs(size?.z ?: 1f) * 0.5f
        val cx = center?.x ?: 0f
        val cy = center?.y ?: 0f
        val cz = center?.z ?: 0f
        when (collider?.getString("forma", "box")) {
            "sphere" -> {
                val r = (hx + hy + hz) / 3f
                setWorldBounds(area, cx, cy, cz, r, r, r, out)
            }
            "capsule" -> {
                val r = (hx + hz) * 0.5f
                setWorldBounds(area, cx, cy, cz, r, hy, r, out)
            }
            else -> setWorldBounds(area, cx, cy, cz, hx, hy, hz, out)
        }
    }

    private fun checarGatilhos(dt: Float) {
        val activePlayer = player ?: return
        val playerAtivo = isEffectivelyActive(activePlayer)
        val pilha = traversal
        pilha.clear()
        pushRootsInTreeOrder()
        while (pilha.isNotEmpty()) {
            val area = pilha.removeAt(pilha.lastIndex)
            if (!area.active) {
                if (dentroDoGatilho[area] == true) {
                    dentroDoGatilho[area] = false
                    scripts?.emitirAreaExit(area, activePlayer)
                }
                continue
            }
            if (area.children.isNotEmpty()) pushChildrenInTreeOrder(area)
            if (area.kind != "area.volume") continue
            areaWorldBounds(area, obstacleBounds)
            val dentro = playerAtivo && overlaps(playerBounds, obstacleBounds)
            val antes = dentroDoGatilho[area]
            dentroDoGatilho[area] = dentro
            if (dentro && antes != true) {
                val nomeArea = area.displayName("pt")
                // evento legado on_trigger + novo callback on_enter(outro)
                scripts?.emitirGatilho(activePlayer, nomeArea)
                scripts?.emitirGatilho(area, nomeArea)
                scripts?.emitirAreaEnter(area, activePlayer)
            } else if (!dentro && antes == true) {
                scripts?.emitirAreaExit(area, activePlayer)
            }
            if (dentro) {
                scripts?.emitirAreaStay(area, activePlayer, dt)
            }
        }
    }

    private fun readPlayerBounds() {
        val activePlayer = player ?: return
        readBodyBounds(activePlayer)
    }

    /** S2: AABB do corpo (Colisor caixa > Malha AABB > cubo 1 m) em playerBounds. */
    private fun readBodyBounds(corpo: Nick) {
        val collider = findClass(corpo, MalhaNick.CLASSE_COLISOR)
        val boxCenter = collider?.fields?.get("centro") as? NickValue.Vec3Value
        val boxSize = collider?.fields?.get("tamanho") as? NickValue.Vec3Value
        if (collider?.getString("forma", "") == "box" && boxCenter != null && boxSize != null) {
            playerLocalCenter[0] = boxCenter.x
            playerLocalCenter[1] = boxCenter.y
            playerLocalCenter[2] = boxCenter.z
            playerLocalHalf[0] = abs(boxSize.x) * 0.5f
            playerLocalHalf[1] = abs(boxSize.y) * 0.5f
            playerLocalHalf[2] = abs(boxSize.z) * 0.5f
        } else {
            val mesh = findClass(corpo, MalhaNick.CLASSE)
            if (mesh == null) {
                playerLocalCenter[0] = 0f
                playerLocalCenter[1] = 0f
                playerLocalCenter[2] = 0f
                playerLocalHalf[0] = 0.5f
                playerLocalHalf[1] = 0.5f
                playerLocalHalf[2] = 0.5f
            } else {
                val minX = mesh.getFloat("aabbMinX", -0.5f)
                val minY = mesh.getFloat("aabbMinY", -0.5f)
                val minZ = mesh.getFloat("aabbMinZ", -0.5f)
                val maxX = mesh.getFloat("aabbMaxX", 0.5f)
                val maxY = mesh.getFloat("aabbMaxY", 0.5f)
                val maxZ = mesh.getFloat("aabbMaxZ", 0.5f)
                playerLocalCenter[0] = (minX + maxX) * 0.5f
                playerLocalCenter[1] = (minY + maxY) * 0.5f
                playerLocalCenter[2] = (minZ + maxZ) * 0.5f
                playerLocalHalf[0] = abs(maxX - minX) * 0.5f
                playerLocalHalf[1] = abs(maxY - minY) * 0.5f
                playerLocalHalf[2] = abs(maxZ - minZ) * 0.5f
            }
        }
        setWorldBounds(
            corpo,
            playerLocalCenter[0], playerLocalCenter[1], playerLocalCenter[2],
            playerLocalHalf[0], playerLocalHalf[1], playerLocalHalf[2], playerBounds
        )
    }

    /** Classes are scanned by index because the runtime calls this in its hot loop. */
    private fun findClass(nick: Nick, typeName: String): NickClassInstance? {
        val classes = nick.classes
        var i = 0
        while (i < classes.size) {
            val instance = classes[i]
            if (instance.typeName == typeName) return instance
            i++
        }
        return null
    }

    private fun unitCubeWorldBounds(nick: Nick, out: FloatArray) =
        setWorldBounds(nick, 0f, 0f, 0f, 0.5f, 0.5f, 0.5f, out)

    /** AABB conservadora de uma caixa local transformada por qualquer TRS pai. */
    private fun setWorldBounds(
        nick: Nick,
        cx: Float, cy: Float, cz: Float,
        hx: Float, hy: Float, hz: Float,
        out: FloatArray
    ) {
        val m = nick.transform.worldMatrix.m
        val wx = m[0] * cx + m[4] * cy + m[8] * cz + m[12]
        val wy = m[1] * cx + m[5] * cy + m[9] * cz + m[13]
        val wz = m[2] * cx + m[6] * cy + m[10] * cz + m[14]
        val ex = abs(m[0]) * hx + abs(m[4]) * hy + abs(m[8]) * hz
        val ey = abs(m[1]) * hx + abs(m[5]) * hy + abs(m[9]) * hz
        val ez = abs(m[2]) * hx + abs(m[6]) * hy + abs(m[10]) * hz
        out[0] = wx - ex; out[1] = wy - ey; out[2] = wz - ez
        out[3] = wx + ex; out[4] = wy + ey; out[5] = wz + ez
    }

    private fun otherAxesOverlap(axis: Int): Boolean {
        for (other in 0..2) {
            if (other == axis) continue
            if (playerBounds[other + 3] <= obstacleBounds[other] + COLLISION_EPSILON ||
                obstacleBounds[other + 3] <= playerBounds[other] + COLLISION_EPSILON
            ) return false
        }
        return true
    }

    private fun overlaps(a: FloatArray, b: FloatArray): Boolean =
        a[0] < b[3] && a[3] > b[0] &&
            a[1] < b[4] && a[4] > b[1] &&
            a[2] < b[5] && a[5] > b[2]

    private fun isEffectivelyActive(nick: Nick): Boolean {
        var current: Nick? = nick
        while (current != null) {
            if (!current.active) return false
            current = current.parent
        }
        return true
    }

    /** Pilha LIFO, filhos empilhados ao contrário para percorrer em ordem. */
    private fun pushRootsInTreeOrder() {
        for (i in scene.roots.indices.reversed()) traversal.add(scene.roots[i])
    }

    private fun pushChildrenInTreeOrder(nick: Nick) {
        for (i in nick.children.indices.reversed()) traversal.add(nick.children[i])
    }

    companion object {
        private const val PLAYER_SPEED = 4f
        private const val GRAVITY = 20f
        /** M1.3: impulso do pulo (m/s) — pulo de ~1,6 m com gravidade 20. */
        private const val VELOCIDADE_PULO = 8f
        private const val TERMINAL_VELOCITY = 50f
        private const val MAX_STEP_SECONDS = 0.25f
        private const val COLLISION_EPSILON = 1e-5f
        private const val MATRIX_EPSILON = 1e-8f
        private const val AXIS_X = 0
        private const val AXIS_Y = 1
        private const val AXIS_Z = 2
    }

    /** M1.2: estado de DENTRO de cada gatilho (borda false→true dispara). */
    private val dentroDoGatilho = HashMap<Nick, Boolean>()
}
