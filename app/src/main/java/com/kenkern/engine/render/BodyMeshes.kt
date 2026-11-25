package com.kenkern.engine.render

import com.kenkern.engine.assets.mesh.MeshData
import com.kenkern.engine.core.math.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * C0 (v0.1.25): FORMAS VISÍVEIS DOS CORPOS NO EDITOR (nunca cubo cinza genérico).
 * Cada kind `body.*` sem malha importada recebe uma geometria procedural própria
 * (em escala unitária [-0.5, 0.5] coerente com o picking e o Colisor) e cor
 * distinta por tipo.
 *
 * Puro Kotlin (sem GL), zero alocação por frame: construído UMA vez no
 * `EngineRenderer.initialize` e consultado por índice fixo + `corDoCorpo`.
 */
object BodyMeshes {

    const val IDX_PLAYER = 0
    const val IDX_CHARACTER = 1
    const val IDX_ENEMY = 2
    const val IDX_NPC = 3
    const val IDX_RIGID = 4
    const val IDX_COLLIDER = 5
    const val IDX_STATIC = 6
    const val TOTAL = 7

    val KINDS_CORPO = listOf(
        "body.player",
        "body.character",
        "body.enemy",
        "body.npc",
        "body.rigid",
        "body.collider",
        "body.static"
    )

    fun indiceDe(kind: String): Int = when (kind) {
        "body.player" -> IDX_PLAYER
        "body.character" -> IDX_CHARACTER
        "body.enemy" -> IDX_ENEMY
        "body.npc" -> IDX_NPC
        "body.rigid" -> IDX_RIGID
        "body.collider" -> IDX_COLLIDER
        "body.static" -> IDX_STATIC
        else -> -1
    }

    fun temFormaPropria(kind: String): Boolean = indiceDe(kind) >= 0

    /** Cor distinta de cada corpo (zero alocação; grava em [out] RGB 0..1). */
    fun corDoCorpo(indice: Int, out: FloatArray) {
        when (indice) {
            IDX_PLAYER -> { out[0] = 0.24f; out[1] = 0.54f; out[2] = 0.96f }     // Azul jogador
            IDX_CHARACTER -> { out[0] = 0.16f; out[1] = 0.68f; out[2] = 0.62f }  // Verde-água
            IDX_ENEMY -> { out[0] = 0.90f; out[1] = 0.24f; out[2] = 0.22f }      // Vermelho inimigo
            IDX_NPC -> { out[0] = 0.36f; out[1] = 0.76f; out[2] = 0.44f }        // Verde NPC
            IDX_RIGID -> { out[0] = 0.96f; out[1] = 0.64f; out[2] = 0.18f }      // Laranja rígido
            IDX_COLLIDER -> { out[0] = 0.58f; out[1] = 0.80f; out[2] = 0.30f }   // Lima colisor
            IDX_STATIC -> { out[0] = 0.36f; out[1] = 0.44f; out[2] = 0.68f }     // Azul-ardósia estático
            else -> { out[0] = 0.604f; out[1] = 0.604f; out[2] = 0.604f }
        }
    }

    fun construir(indice: Int): MeshData = when (indice) {
        IDX_PLAYER -> construirJogador()
        IDX_CHARACTER -> construirCapsula(segs = 14, aneis = 5, raio = 0.34f, meiaAlturaCil = 0.16f)
        IDX_ENEMY -> construirInimigo()
        IDX_NPC -> construirNpc()
        IDX_RIGID -> construirCaixaChanfrada(chamfer = 0.12f)
        IDX_COLLIDER -> construirPrismaOctogonal(raio = 0.48f, meiaAltura = 0.48f)
        IDX_STATIC -> construirPlintoEstatico()
        else -> construirCapsula(segs = 12, aneis = 4, raio = 0.35f, meiaAlturaCil = 0.15f)
    }

    fun construirPorKind(kind: String): MeshData? {
        val idx = indiceDe(kind)
        return if (idx >= 0) construir(idx) else null
    }

    // ---------------------------------------------------------------- builders

    private class MeshBuilder {
        private val pos = ArrayList<Float>(512)
        private val norm = ArrayList<Float>(512)
        private val uv = ArrayList<Float>(384)
        private val idx = ArrayList<Int>(512)

        fun addVertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, u: Float, v: Float): Int {
            val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
            val i = pos.size / 3
            pos.add(x); pos.add(y); pos.add(z)
            norm.add(nx / len); norm.add(ny / len); norm.add(nz / len)
            uv.add(u); uv.add(v)
            return i
        }

        fun addTri(a: Int, b: Int, c: Int) {
            idx.add(a); idx.add(b); idx.add(c)
        }

        fun addQuad(a: Int, b: Int, c: Int, d: Int) {
            idx.add(a); idx.add(b); idx.add(c)
            idx.add(a); idx.add(c); idx.add(d)
        }

        fun addBox(cx: Float, cy: Float, cz: Float, hx: Float, hy: Float, hz: Float) {
            val faces = arrayOf(
                floatArrayOf(0f, 0f, 1f,  -hx, -hy, hz,  hx, -hy, hz,  hx, hy, hz,  -hx, hy, hz),
                floatArrayOf(0f, 0f, -1f,  hx, -hy, -hz, -hx, -hy, -hz, -hx, hy, -hz, hx, hy, -hz),
                floatArrayOf(0f, 1f, 0f,  -hx, hy, hz,   hx, hy, hz,   hx, hy, -hz, -hx, hy, -hz),
                floatArrayOf(0f, -1f, 0f, -hx, -hy, -hz, hx, -hy, -hz, hx, -hy, hz, -hx, -hy, hz),
                floatArrayOf(1f, 0f, 0f,   hx, -hy, hz,  hx, -hy, -hz, hx, hy, -hz,  hx, hy, hz),
                floatArrayOf(-1f, 0f, 0f, -hx, -hy, -hz, -hx, -hy, hz, -hx, hy, hz, -hx, hy, -hz)
            )
            for (f in faces) {
                val nx = f[0]; val ny = f[1]; val nz = f[2]
                val a = addVertex(cx + f[3], cy + f[4], cz + f[5], nx, ny, nz, 0f, 0f)
                val b = addVertex(cx + f[6], cy + f[7], cz + f[8], nx, ny, nz, 1f, 0f)
                val c = addVertex(cx + f[9], cy + f[10], cz + f[11], nx, ny, nz, 1f, 1f)
                val d = addVertex(cx + f[12], cy + f[13], cz + f[14], nx, ny, nz, 0f, 1f)
                addQuad(a, b, c, d)
            }
        }

        fun addLatheCapsule(segs: Int, aneis: Int, raio: Float, meiaAlturaCil: Float, cx: Float = 0f, cy: Float = 0f, cz: Float = 0f) {
            val perfil = ArrayList<FloatArray>()
            // Hemisfério inferior: -PI/2 .. 0
            for (i in 0..aneis) {
                val a = -PI.toFloat() * 0.5f + (i.toFloat() / aneis) * (PI.toFloat() * 0.5f)
                val r = cos(a) * raio
                val y = -meiaAlturaCil + sin(a) * raio
                perfil.add(floatArrayOf(r, y, cos(a), sin(a)))
            }
            // Hemisfério superior: 0 .. +PI/2
            for (i in 0..aneis) {
                val a = (i.toFloat() / aneis) * (PI.toFloat() * 0.5f)
                val r = cos(a) * raio
                val y = meiaAlturaCil + sin(a) * raio
                perfil.add(floatArrayOf(r, y, cos(a), sin(a)))
            }
            val cols = segs + 1
            val startIdx = pos.size / 3
            for (row in perfil.indices) {
                val p = perfil[row]
                val v = row.toFloat() / (perfil.size - 1).coerceAtLeast(1)
                for (col in 0..segs) {
                    val u = col.toFloat() / segs
                    val theta = u * 2f * PI.toFloat()
                    val ct = cos(theta); val st = sin(theta)
                    addVertex(
                        cx + p[0] * ct, cy + p[1], cz + p[0] * st,
                        p[2] * ct, p[3], p[2] * st,
                        u, v
                    )
                }
            }
            for (row in 0 until perfil.size - 1) {
                for (col in 0 until segs) {
                    val a = startIdx + row * cols + col
                    val b = a + 1
                    val c = a + cols + 1
                    val d = a + cols
                    addQuad(a, b, c, d)
                }
            }
        }

        fun addCone(cx: Float, cyBase: Float, cz: Float, raio: Float, altura: Float, segs: Int) {
            val tip = addVertex(cx, cyBase + altura, cz, 0f, 1f, 0f, 0.5f, 1f)
            val baseCenter = addVertex(cx, cyBase, cz, 0f, -1f, 0f, 0.5f, 0f)
            val slopeNy = raio / sqrt(raio * raio + altura * altura).coerceAtLeast(1e-5f)
            val slopeNr = altura / sqrt(raio * raio + altura * altura).coerceAtLeast(1e-5f)
            for (i in 0 until segs) {
                val a0 = (i.toFloat() / segs) * 2f * PI.toFloat()
                val a1 = ((i + 1).toFloat() / segs) * 2f * PI.toFloat()
                val c0 = cos(a0); val s0 = sin(a0)
                val c1 = cos(a1); val s1 = sin(a1)
                val v0 = addVertex(cx + c0 * raio, cyBase, cz + s0 * raio, c0 * slopeNr, slopeNy, s0 * slopeNr, i.toFloat() / segs, 0f)
                val v1 = addVertex(cx + c1 * raio, cyBase, cz + s1 * raio, c1 * slopeNr, slopeNy, s1 * slopeNr, (i + 1).toFloat() / segs, 0f)
                addTri(v0, tip, v1)
                val b0 = addVertex(cx + c0 * raio, cyBase, cz + s0 * raio, 0f, -1f, 0f, 0f, 0f)
                val b1 = addVertex(cx + c1 * raio, cyBase, cz + s1 * raio, 0f, -1f, 0f, 1f, 0f)
                addTri(baseCenter, b1, b0)
            }
        }

        fun build(): MeshData {
            val n = pos.size / 3
            return MeshData(
                positions = pos.toFloatArray(),
                normals = norm.toFloatArray(),
                uvs = uv.toFloatArray(),
                indices = idx.toIntArray(),
                boundsMin = Vec3(-0.5f, -0.5f, -0.5f),
                boundsMax = Vec3(0.5f, 0.5f, 0.5f),
                tangents = FloatArray(n * 3)
            )
        }
    }

    private fun construirCapsula(segs: Int, aneis: Int, raio: Float, meiaAlturaCil: Float): MeshData {
        val mb = MeshBuilder()
        mb.addLatheCapsule(segs, aneis, raio, meiaAlturaCil)
        return mb.build()
    }

    /** Jogador: cápsula + visor frontal apontando para -Z (frente do personagem). */
    private fun construirJogador(): MeshData {
        val mb = MeshBuilder()
        mb.addLatheCapsule(segs = 16, aneis = 5, raio = 0.32f, meiaAlturaCil = 0.16f)
        // Visor indicando frente (-Z) e ombros sutis
        mb.addBox(0f, 0.22f, -0.26f, 0.20f, 0.08f, 0.12f)
        mb.addBox(0f, -0.02f, 0f, 0.40f, 0.08f, 0.18f)
        return mb.build()
    }

    /** Inimigo: prisma angular facetado + crista/chifre no topo (silhueta hostil). */
    private fun construirInimigo(): MeshData {
        val mb = MeshBuilder()
        mb.addLatheCapsule(segs = 6, aneis = 3, raio = 0.34f, meiaAlturaCil = 0.12f)
        mb.addCone(cx = 0f, cyBase = 0.30f, cz = 0f, raio = 0.24f, altura = 0.20f, segs = 6)
        mb.addBox(0f, 0.12f, -0.26f, 0.24f, 0.06f, 0.10f)
        return mb.build()
    }

    /** NPC: cápsula arredondada + colarinho/coroa amigável no topo. */
    private fun construirNpc(): MeshData {
        val mb = MeshBuilder()
        mb.addLatheCapsule(segs = 14, aneis = 4, raio = 0.30f, meiaAlturaCil = 0.14f)
        mb.addBox(0f, 0.38f, 0f, 0.22f, 0.06f, 0.22f)
        return mb.build()
    }

    /** Rigid Body: bloco com cantos chanfrados (prisma octogonal + tampas). */
    private fun construirCaixaChanfrada(chamfer: Float): MeshData {
        val mb = MeshBuilder()
        val h = 0.5f
        val c = h - chamfer
        // Núcleo + faixas chanfradas via 3 caixas cruzadas (forma de bloco chanfrado limpo)
        mb.addBox(0f, 0f, 0f, h, c, c)
        mb.addBox(0f, 0f, 0f, c, h, c)
        mb.addBox(0f, 0f, 0f, c, c, h)
        return mb.build()
    }

    /** Collider Body: prisma octogonal facetado (não é um cubo). */
    private fun construirPrismaOctogonal(raio: Float, meiaAltura: Float): MeshData {
        val mb = MeshBuilder()
        val segs = 8
        val topCenter = mb.addVertex(0f, meiaAltura, 0f, 0f, 1f, 0f, 0.5f, 0.5f)
        val botCenter = mb.addVertex(0f, -meiaAltura, 0f, 0f, -1f, 0f, 0.5f, 0.5f)
        for (i in 0 until segs) {
            val a0 = (i.toFloat() / segs) * 2f * PI.toFloat() + PI.toFloat() / segs
            val a1 = ((i + 1).toFloat() / segs) * 2f * PI.toFloat() + PI.toFloat() / segs
            val x0 = cos(a0) * raio; val z0 = sin(a0) * raio
            val x1 = cos(a1) * raio; val z1 = sin(a1) * raio
            val nx = cos((a0 + a1) * 0.5f); val nz = sin((a0 + a1) * 0.5f)
            val b0 = mb.addVertex(x0, -meiaAltura, z0, nx, 0f, nz, 0f, 0f)
            val b1 = mb.addVertex(x1, -meiaAltura, z1, nx, 0f, nz, 1f, 0f)
            val t1 = mb.addVertex(x1, meiaAltura, z1, nx, 0f, nz, 1f, 1f)
            val t0 = mb.addVertex(x0, meiaAltura, z0, nx, 0f, nz, 0f, 1f)
            mb.addQuad(b0, t0, t1, b1)
            val ut0 = mb.addVertex(x0, meiaAltura, z0, 0f, 1f, 0f, 0f, 0f)
            val ut1 = mb.addVertex(x1, meiaAltura, z1, 0f, 1f, 0f, 1f, 0f)
            mb.addTri(topCenter, ut1, ut0)
            val ub0 = mb.addVertex(x0, -meiaAltura, z0, 0f, -1f, 0f, 0f, 0f)
            val ub1 = mb.addVertex(x1, -meiaAltura, z1, 0f, -1f, 0f, 1f, 0f)
            mb.addTri(botCenter, ub0, ub1)
        }
        return mb.build()
    }

    /** Static Body: plinto arquitetônico em 2 degraus (base firme + topo recuado). */
    private fun construirPlintoEstatico(): MeshData {
        val mb = MeshBuilder()
        mb.addBox(0f, -0.18f, 0f, 0.50f, 0.32f, 0.50f)
        mb.addBox(0f, 0.28f, 0f, 0.42f, 0.22f, 0.42f)
        return mb.build()
    }
}
