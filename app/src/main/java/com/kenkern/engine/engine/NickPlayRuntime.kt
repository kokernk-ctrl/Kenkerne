package com.kenkern.engine.engine

import com.kenkern.engine.core.MalhaNick
import com.kenkern.engine.core.Nick
import com.kenkern.engine.core.NickClassInstance
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.input.GameplayInput
import com.kenkern.engine.core.input.StickSample
import com.kenkern.engine.core.input.TouchControlSchema
import com.kenkern.engine.core.script.LuaJScriptRuntime
import com.kenkern.engine.core.script.installGameplayInput
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Primeiro runtime de jogo: move o primeiro player ativo, aplica gravidade e
 * colide com corpos estáticos AABB. O runtime trabalha na Scene recebida (o
 * chamador é responsável por passar uma cópia descartável para Play).
 *
 * Colisores suportados: body.static (cubo local de 1 m) e body.collider com
 * classe Colisor/forma=box. Formas sphere/capsule/etc. são deliberadamente
 * ignoradas nesta primeira versão.
 */
class NickPlayRuntime(private val scene: Scene) {

    /** Primeiro body.player efetivamente ativo, em ordem pre-order da árvore. */
    val player: Nick?

    /** Primeira câmera ativa de cada tipo; escolhidas uma vez na inicialização. */
    val followCamera: Nick?
    val basicCamera: Nick?
    internal val input: GameplayInput
    private val stick = StickSample()
    private class Motion(val nick: Nick) {
        var vy = 0f
        var x = 0f
        var z = 0f
        var strength = 0f
        var turn = false
    }
    private val motions: List<Motion>
    private val motionByControl: Array<Motion?>
    private var body: Nick? = null

    private val traversal = ArrayList<Nick>()
    private val playerLocalCenter = FloatArray(3)
    private val playerLocalHalf = FloatArray(3)
    private val playerBounds = FloatArray(6)
    private val obstacleBounds = FloatArray(6)
    private val localDeltaScratch = FloatArray(3)


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
        input = GameplayInput(scene, player)
        val targets = LinkedHashMap<Nick, Motion>()
        player?.let { targets[it] = Motion(it) }
        motionByControl = Array(input.controls.size) { i ->
            val control = input.controls[i]
            if (control.config.button || control.config.action != TouchControlSchema.MOVE) null
            else control.target?.let { targets.getOrPut(it) { Motion(it) } }
        }
        motions = targets.values.toList()

        val activePlayer = player
        if (activePlayer != null) {
            body = activePlayer
            val spawn = foundSpawn
            if (spawn != null) setPlayerWorldPosition(
                spawn.transform.worldMatrix.m[12],
                spawn.transform.worldMatrix.m[13],
                spawn.transform.worldMatrix.m[14]
            )
            scene.updateWorldTransforms()
            body = null
        }
    }

    /** Touch sources feed the real fixed step, with dominant-stick arbitration per target. */
    internal fun stepTouch(dt: Float, cameraYawDegrees: Float) {
        if (dt <= 0f || !dt.isFinite()) return
        val yaw = Math.toRadians(cameraYawDegrees.toDouble())
        val sinYaw = sin(yaw).toFloat()
        val cosYaw = cos(yaw).toFloat()
        for (i in motions.indices) motions[i].strength = 0f
        for (i in motionByControl.indices) {
            val motion = motionByControl[i] ?: continue
            val control = input.controls[i]
            input.read(control, stick)
            if (stick.strength <= motion.strength) continue
            motion.strength = stick.strength
            motion.turn = control.config.turn
            val factor = control.config.speed / PLAYER_SPEED
            val x = stick.x * factor; val z = stick.y * factor
            motion.x = if (control.config.cameraRelative) x * cosYaw + z * sinYaw else x
            motion.z = if (control.config.cameraRelative) z * cosYaw - x * sinYaw else z
        }
        for (i in motions.indices) {
            val motion = motions[i]
            val moving = motion.strength > 0f
            stepMotion(dt, motion, if (moving) motion.x else 0f, if (moving) motion.z else 0f, moving && motion.turn)
        }
    }

    /** Bind a loaded LuaJ script to this Play session before calling iniciar(). */
    fun bindInput(script: LuaJScriptRuntime) = script.installGameplayInput(input)

    /** Degrees per second, from the strongest authored stick mapped to 'olhar'. */
    internal fun readLook(out: FloatArray) {
        out[0] = 0f; out[1] = 0f
        var best = 0f
        for (i in input.controls.indices) {
            val c = input.controls[i]
            if (c.config.button || c.config.action != TouchControlSchema.LOOK) continue
            input.read(c, stick)
            if (stick.strength <= best) continue
            best = stick.strength
            out[0] = stick.x * c.config.speed * 22.5f
            out[1] = stick.y * c.config.speed * 22.5f
        }
    }

    /**
     * Avança o player. moveX/moveZ são eixos de mundo; a entrada diagonal é
     * normalizada. dt é limitado a 0.25 s para proteger o primeiro runtime de
     * pausas longas, e deslocamentos são resolvidos por varredura em cada eixo
     * para não atravessar paredes/floors em um único passo.
     */
    fun step(dt: Float, moveX: Float, moveZ: Float) {
        if (player == null) return
        var x = if (moveX.isFinite()) moveX else 0f
        var z = if (moveZ.isFinite()) moveZ else 0f
        val inputLength = sqrt(x * x + z * z)
        if (inputLength > 1f) {
            x /= inputLength
            z /= inputLength
        }
        stepMotion(dt, motions[0], x, z, false)
    }

    private fun stepMotion(dt: Float, motion: Motion, x: Float, z: Float, turn: Boolean) {
        if (dt <= 0f || !dt.isFinite() || !isEffectivelyActive(motion.nick)) return
        body = motion.nick
        scene.updateWorldTransforms()
        if (turn && (x != 0f || z != 0f)) turnBody(motion.nick, x, z)
        val deltaTime = dt.coerceAtMost(MAX_STEP_SECONDS)
        val dx = x * PLAYER_SPEED * deltaTime
        val dz = z * PLAYER_SPEED * deltaTime
        motion.vy = (motion.vy - GRAVITY * deltaTime).coerceAtLeast(-TERMINAL_VELOCITY)
        val dy = motion.vy * deltaTime

        moveAxis(AXIS_X, dx)
        moveAxis(AXIS_Z, dz)
        val movedY = moveAxis(AXIS_Y, dy)
        if ((dy < 0f && movedY > dy + COLLISION_EPSILON) ||
            (dy > 0f && movedY < dy - COLLISION_EPSILON)
        ) motion.vy = 0f
        scene.updateWorldTransforms()
        body = null
    }

    private fun turnBody(nick: Nick, worldX: Float, worldZ: Float) {
        var localX = worldX
        var localZ = worldZ
        val parent = nick.parent
        if (parent != null) {
            if (!inverseParentDelta(parent.transform.worldMatrix.m, worldX, 0f, worldZ, localDeltaScratch)) return
            localX = localDeltaScratch[0]
            localZ = localDeltaScratch[2]
        }
        nick.transform.rotation.setFromEulerDegrees(0f, Math.toDegrees(atan2(-localX, -localZ).toDouble()).toFloat(), 0f)
        refreshPlayerWorldTransform()
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
        body = activePlayer
        readPlayerBounds()
        body = null
        unitCubeWorldBounds(area, obstacleBounds)
        return overlaps(playerBounds, obstacleBounds)
    }

    /** Move no espaço de mundo e converte a translação para o pai do player. */
    private fun setPlayerWorldPosition(worldX: Float, worldY: Float, worldZ: Float) {
        val activePlayer = body ?: return
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
        val activePlayer = body ?: return false
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
        val activePlayer = body ?: return
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
        while (traversal.isNotEmpty()) {
            val nick = traversal.removeAt(traversal.lastIndex)
            if (!nick.active) continue
            if (nick === body) continue // ignora o corpo e sua subárvore
            if (isBlockingStatic(nick)) {
                getBlockingBounds(nick, obstacleBounds)
                if (otherAxesOverlap(axis)) {
                    val obstacleNear = obstacleBounds[axis]
                    val obstacleFar = obstacleBounds[axis + 3]
                    if (requestedDelta > 0f) {
                        val distance = obstacleNear - playerBounds[axis + 3]
                        if (distance >= -COLLISION_EPSILON && distance < allowed) {
                            allowed = distance.coerceAtLeast(0f)
                        }
                    } else {
                        val distance = obstacleFar - playerBounds[axis]
                        if (distance <= COLLISION_EPSILON && distance > allowed) {
                            allowed = distance.coerceAtMost(0f)
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
        return allowed
    }

    private fun isBlockingStatic(nick: Nick): Boolean {
        if (nick.kind == "body.static") return true
        if (nick.kind != "body.collider") return false
        return findClass(nick, MalhaNick.CLASSE_COLISOR)?.getString("forma", "box") == "box"
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
        setWorldBounds(
            nick, center?.x ?: 0f, center?.y ?: 0f, center?.z ?: 0f,
            hx, hy, hz, out
        )
    }

    private fun readPlayerBounds() {
        val activePlayer = body ?: return
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
