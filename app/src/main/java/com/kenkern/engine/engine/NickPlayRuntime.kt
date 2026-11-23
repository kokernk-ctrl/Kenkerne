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
 */
class NickPlayRuntime(private val scene: Scene) {

    /** Primeiro body.player efetivamente ativo, em ordem pre-order da árvore. */
    val player: Nick?

    /** Primeira câmera ativa de cada tipo; escolhidas uma vez na inicialização. */
    val followCamera: Nick?
    val basicCamera: Nick?

    /**
     * M1.1/M1.2: ponte Lua dos scripts de Play — eventos on_collision e
     * on_trigger saem daqui (opcional; sem scripts, nada é chamado).
     */
    var scripts: NickLuaScripts? = null

    private val traversal = ArrayList<Nick>()
    private val playerLocalCenter = FloatArray(3)
    private val playerLocalHalf = FloatArray(3)
    private val playerBounds = FloatArray(6)
    private val obstacleBounds = FloatArray(6)
    private val localDeltaScratch = FloatArray(3)
    private var verticalVelocity = 0f

    /** M1.2: estado de DENTRO de cada gatilho (borda false→true dispara). */
    private val dentroDoGatilho = HashMap<Nick, Boolean>()

    /** M1.2: nome do Nick que PAROU o movimento neste step (evento de colisão). */
    private var colisaoNome: String? = null

    init {
        scene.updateWorldTransforms()
        traversal.clear()
        pushRootsInTreeOrder()
        var foundPlayer: Nick? = null
        var foundSpawn: Nick? = null
        var foundFollow: Nick? = null
        var foundBasic: Nick? = null
        while (traversal.isNotEmpty()) {
            val nick = traversal.removeAt(traversal.lastIndex)
            if (!nick.active) continue
            if (foundPlayer == null && nick.kind == "body.player") foundPlayer = nick
            if (foundSpawn == null && nick.kind == "area.spawn") foundSpawn = nick
            if (foundFollow == null && nick.kind == "camera.follow") foundFollow = nick
            if (foundBasic == null && nick.kind == "camera.basic") foundBasic = nick
            pushChildrenInTreeOrder(nick)
        }
        player = foundPlayer
        followCamera = foundFollow
        basicCamera = foundBasic

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

    /**
     * Avança o player. moveX/moveZ são eixos de mundo; a entrada diagonal é
     * normalizada. dt é limitado a 0.25 s para proteger o primeiro runtime de
     * pausas longas, e deslocamentos são resolvidos por varredura em cada eixo
     * para não atravessar paredes/floors em um único passo.
     */
    fun step(dt: Float, moveX: Float, moveZ: Float) {
        val activePlayer = player ?: return
        if (dt <= 0f || !dt.isFinite() || !isEffectivelyActive(activePlayer)) return
        scene.updateWorldTransforms()

        var x = if (moveX.isFinite()) moveX else 0f
        var z = if (moveZ.isFinite()) moveZ else 0f
        val inputLength = sqrt(x * x + z * z)
        if (inputLength > 1f) {
            x /= inputLength
            z /= inputLength
        }

        val deltaTime = dt.coerceAtMost(MAX_STEP_SECONDS)
        val dx = x * PLAYER_SPEED * deltaTime
        val dz = z * PLAYER_SPEED * deltaTime
        verticalVelocity = (verticalVelocity - GRAVITY * deltaTime).coerceAtLeast(-TERMINAL_VELOCITY)
        val dy = verticalVelocity * deltaTime

        moveAxis(AXIS_X, dx)
        moveAxis(AXIS_Z, dz)
        val movedY = moveAxis(AXIS_Y, dy)
        if ((dy < 0f && movedY > dy + COLLISION_EPSILON) ||
            (dy > 0f && movedY < dy - COLLISION_EPSILON)
        ) verticalVelocity = 0f
        scene.updateWorldTransforms()

        // M1.2: GATILHOS (áreas) — borda de ENTRADA dispara on_trigger 1 vez;
        // sair e voltar dispara de novo. Colisor da área (se houver) define o
        // volume; sem Colisor, o cubo de 1 m do Area Nick é o volume.
        checarGatilhos()

        // M1.2: evento de COLISÃO — o jogador PAROU contra um corpo neste step.
        val colidiu = colisaoNome
        if (colidiu != null) {
            colisaoNome = null
            scripts?.emitirColisao(activePlayer, colidiu)
        }
        scene.updateWorldTransforms()
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
        unitCubeWorldBounds(area, obstacleBounds)
        return overlaps(playerBounds, obstacleBounds)
    }

    /** Move no espaço de mundo e converte a translação para o pai do player. */
    private fun setPlayerWorldPosition(worldX: Float, worldY: Float, worldZ: Float) {
        val activePlayer = player ?: return
        val parent = activePlayer.parent
        if (parent == null) {
            activePlayer.transform.position.set(worldX, worldY, worldZ)
            return
        }
        val m = parent.transform.worldMatrix.m
        if (!inverseParentDelta(m, worldX - m[12], worldY - m[13], worldZ - m[14], localDeltaScratch)) return
        activePlayer.transform.position.set(localDeltaScratch[0], localDeltaScratch[1], localDeltaScratch[2])
    }

    /** Converte um deslocamento de mundo em translação local, sem alocar. */
    private fun translatePlayerWorld(dx: Float, dy: Float, dz: Float): Boolean {
        val activePlayer = player ?: return false
        val parent = activePlayer.parent
        if (parent == null) {
            activePlayer.transform.position.x += dx
            activePlayer.transform.position.y += dy
            activePlayer.transform.position.z += dz
        } else {
            val m = parent.transform.worldMatrix.m
            if (!inverseParentDelta(m, dx, dy, dz, localDeltaScratch)) return false
            activePlayer.transform.position.x += localDeltaScratch[0]
            activePlayer.transform.position.y += localDeltaScratch[1]
            activePlayer.transform.position.z += localDeltaScratch[2]
        }
        refreshPlayerWorldTransform()
        return true
    }

    private fun refreshPlayerWorldTransform() {
        val activePlayer = player ?: return
        val transform = activePlayer.transform
        transform.localMatrix.setTRS(transform.position, transform.rotation, transform.scale)
        val parent = activePlayer.parent
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

    /** Varredura exata em um eixo contra AABBs estáticos ativos. */
    private fun moveAxis(axis: Int, requestedDelta: Float): Float {
        if (requestedDelta == 0f || !requestedDelta.isFinite()) return 0f
        readPlayerBounds()
        var allowed = requestedDelta
        traversal.clear()
        pushRootsInTreeOrder()
        var bloqueouPor: Nick? = null
        while (traversal.isNotEmpty()) {
            val nick = traversal.removeAt(traversal.lastIndex)
            if (!nick.active) continue
            if (nick === player) continue // ignora o player e sua subárvore
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
            if (!translatePlayerWorld(
                    if (axis == AXIS_X) allowed else 0f,
                    if (axis == AXIS_Y) allowed else 0f,
                    if (axis == AXIS_Z) allowed else 0f
                )
            ) return 0f
            readPlayerBounds()
        }
        // M1.2: PAROU (permitido < pedido) → guarda quem bloqueou (evento).
        if (allowed < requestedDelta - COLLISION_EPSILON && bloqueouPor != null) {
            colisaoNome = bloqueouPor.displayName("pt")
        }
        return allowed
    }

    private fun isBlockingStatic(nick: Nick): Boolean {
        if (nick.kind == "body.static") return true
        if (nick.kind != "body.collider") return false
        // M1.2: TODAS as formas param (sem solver completo — caixa/esfera/
        // cápsula/cilindro/convexo/malha usam a AABB da forma, a MESMA do
        // wireframe do editor; solver exato entra na Fase 8+).
        return true
    }

    private fun getBlockingBounds(nick: Nick, out: FloatArray) {
        if (nick.kind == "body.static") {
            setWorldBounds(nick, 0f, 0f, 0f, 0.5f, 0.5f, 0.5f, out)
            return
        }
        val collider = findClass(nick, MalhaNick.CLASSE_COLISOR)
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
    private fun checarGatilhos() {
        val activePlayer = player ?: return
        val pilha = traversal
        pilha.clear()
        pushRootsInTreeOrder()
        while (pilha.isNotEmpty()) {
            val area = pilha.removeAt(pilha.lastIndex)
            if (!area.active) continue
            if (area.children.isNotEmpty()) pushChildrenInTreeOrder(area)
            if (area.kind != "area.volume") continue
            val collider = findClass(area, MalhaNick.CLASSE_COLISOR)
            val center = collider?.fields?.get("centro") as? NickValue.Vec3Value
            val size = collider?.fields?.get("tamanho") as? NickValue.Vec3Value
            val hx = abs(size?.x ?: 1f) * 0.5f
            val hy = abs(size?.y ?: 1f) * 0.5f
            val hz = abs(size?.z ?: 1f) * 0.5f
            setWorldBounds(area, center?.x ?: 0f, center?.y ?: 0f, center?.z ?: 0f, hx, hy, hz, obstacleBounds)
            val dentro = overlaps(playerBounds, obstacleBounds)
            val antes = dentroDoGatilho[area]
            if (dentro && antes != true) {
                val nomeArea = area.displayName("pt")
                // evento no script do JOGADOR (self) e no script da ÁREA.
                scripts?.emitirGatilho(activePlayer, nomeArea)
                scripts?.emitirGatilho(area, nomeArea)
            }
            dentroDoGatilho[area] = dentro
        }
    }

    private fun readPlayerBounds() {
        val activePlayer = player ?: return
        val collider = findClass(activePlayer, MalhaNick.CLASSE_COLISOR)
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
            val mesh = findClass(activePlayer, MalhaNick.CLASSE)
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
            activePlayer,
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
        private const val TERMINAL_VELOCITY = 50f
        private const val MAX_STEP_SECONDS = 0.25f
        private const val COLLISION_EPSILON = 1e-5f
        private const val MATRIX_EPSILON = 1e-8f
        private const val AXIS_X = 0
        private const val AXIS_Y = 1
        private const val AXIS_Z = 2
    }
}
