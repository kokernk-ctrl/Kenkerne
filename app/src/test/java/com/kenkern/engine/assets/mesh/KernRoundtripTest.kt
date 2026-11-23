package com.kenkern.engine.assets.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Teste JVM de IDA E VOLTA do formato .KERN — Fase 2, F2.2.
 *
 * O .KERN é o formato de malha da engine (importação na Fase 4 depende dele).
 * Valida escritor+leitor binários com os ERROS MÁXIMOS documentados:
 *  - posição: quantização u16 na bounding box → erro <= span/65535 por eixo;
 *  - normal/tangente: octaédrico 8:8 → dot(original, lida) >= 0,999;
 *  - UV: half-float → erro absoluto <= 2e-3 em [0,1];
 *  - índices: exatos (u16 e u32);
 *  - LODs, ossos (reservado) e clusters: preservados;
 *  - arquivo corrompido: lança (nunca silencia lixo).
 */
class KernRoundtripTest {

    /** Malha de cubo (24 vértices, 36 índices) com normais por face. */
    private fun cubo(): KernWriter.Builder {
        val b = KernWriter.Builder()
        val faces = arrayOf(
            floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 0f, -1f),
            floatArrayOf(1f, 0f, 0f), floatArrayOf(-1f, 0f, 0f),
            floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, -1f, 0f)
        )
        var v = 0
        for (f in faces) {
            val (nx, ny, nz) = Triple(f[0], f[1], f[2])
            val corners = when {
                nz != 0f -> arrayOf(
                    floatArrayOf(-1f, -1f, nz), floatArrayOf(1f, -1f, nz),
                    floatArrayOf(1f, 1f, nz), floatArrayOf(-1f, 1f, nz)
                )
                nx != 0f -> arrayOf(
                    floatArrayOf(nx, -1f, -1f), floatArrayOf(nx, 1f, -1f),
                    floatArrayOf(nx, 1f, 1f), floatArrayOf(nx, -1f, 1f)
                )
                else -> arrayOf(
                    floatArrayOf(-1f, ny, -1f), floatArrayOf(1f, ny, -1f),
                    floatArrayOf(1f, ny, 1f), floatArrayOf(-1f, ny, 1f)
                )
            }
            for (k in 0 until 4) {
                val c = corners[k]
                b.vertex(
                    c[0], c[1], c[2],
                    nx, ny, nz,
                    if (k == 0 || k == 3) 0f else 1f, if (k < 2) 0f else 1f,
                    nz, 0f, -nx
                )
            }
            b.triangle(v, v + 1, v + 2)
            b.triangle(v, v + 2, v + 3)
            v += 4
        }
        return b
    }

    private fun assertPosDentro(span: Float, esperado: Float, lido: Float, msg: String) {
        val tol = span / 65535f
        assertTrue("$msg: esperado $esperado lido $lido (tol $tol)", kotlin.math.abs(esperado - lido) <= tol)
    }

    @Test
    fun `ida e volta do cubo - contagens índices bounds e erro máximo de posição`() {
        val original = cubo().build()
        val bytes = KernWriter.write(original)
        val lida = KernReader.read(bytes)

        assertEquals(24, lida.vertexCount)
        assertEquals(36, lida.indices.size)
        for (i in original.indices.indices) {
            assertEquals("índice $i", original.indices[i], lida.indices[i])
        }
        assertEquals(original.boundsMin.x, lida.boundsMin.x, 1e-6f)
        assertEquals(original.boundsMax.y, lida.boundsMax.y, 1e-6f)
        val spanX = original.boundsMax.x - original.boundsMin.x
        for (v in 0 until original.vertexCount) {
            assertPosDentro(spanX, original.positions[v * 3], lida.positions[v * 3], "vx do vértice $v")
            assertPosDentro(spanX, original.positions[v * 3 + 1], lida.positions[v * 3 + 1], "vy do vértice $v")
            assertPosDentro(spanX, original.positions[v * 3 + 2], lida.positions[v * 3 + 2], "vz do vértice $v")
        }
    }

    @Test
    fun `normais e tangentes sobrevivem ao octaédrico 8-8 - dot mínimo 0,999`() {
        val original = cubo().build()
        val lida = KernReader.read(KernWriter.write(original))
        for (v in 0 until original.vertexCount) {
            val d = original.normals[v * 3] * lida.normals[v * 3] +
                original.normals[v * 3 + 1] * lida.normals[v * 3 + 1] +
                original.normals[v * 3 + 2] * lida.normals[v * 3 + 2]
            assertTrue("normal do vértice $v: dot=$d", d >= 0.999f)
            val len = sqrt(
                lida.normals[v * 3] * lida.normals[v * 3] +
                    lida.normals[v * 3 + 1] * lida.normals[v * 3 + 1] +
                    lida.normals[v * 3 + 2] * lida.normals[v * 3 + 2]
            )
            assertEquals(1f, len, 0.01f)
        }
    }

    @Test
    fun `UVs sobrevivem ao half-float - erro máximo 2e-3`() {
        val original = cubo().build()
        val lida = KernReader.read(KernWriter.write(original))
        for (v in 0 until original.vertexCount) {
            assertTrue(
                "u do vértice $v",
                kotlin.math.abs(original.uvs[v * 2] - lida.uvs[v * 2]) <= 2e-3f
            )
            assertTrue(
                "v do vértice $v",
                kotlin.math.abs(original.uvs[v * 2 + 1] - lida.uvs[v * 2 + 1]) <= 2e-3f
            )
        }
    }

    @Test
    fun `tabela de LODs é preservada`() {
        val b = cubo()
        b.lods.add(KernLod(0, 36, com.kenkern.engine.core.math.Vec3(-1f, -1f, -1f), com.kenkern.engine.core.math.Vec3(1f, 1f, 1f)))
        b.lods.add(KernLod(36, 18, com.kenkern.engine.core.math.Vec3(-0.5f, -0.5f, -0.5f), com.kenkern.engine.core.math.Vec3(0.5f, 0.5f, 0.5f)))
        repeat(6) { b.triangle(0, 1, 2) } // índices do segundo LOD também existem no stream
        val original = b.build()
        val lida = KernReader.read(KernWriter.write(original))
        assertEquals(2, lida.lods.size)
        assertEquals(0, lida.lods[0].indexOffset)
        assertEquals(36, lida.lods[0].indexCount)
        assertEquals(36, lida.lods[1].indexOffset)
        assertEquals(18, lida.lods[1].indexCount)
        assertEquals(0.5f, lida.lods[1].boundsMax.x, 1e-6f)
    }

    @Test
    fun `ossos reservados - nome pai e inverseBind preservados`() {
        val b = cubo()
        val inverseBind = FloatArray(16) { it.toFloat() }
        b.bones = listOf(
            KernBone("raiz", -1, FloatArray(16) { 0f }.also { it[0] = 1f; it[5] = 1f; it[10] = 1f; it[15] = 1f }),
            KernBone("braco", 0, inverseBind)
        )
        val lida = KernReader.read(KernWriter.write(b.build()))
        assertEquals(2, lida.bones.size)
        assertEquals("raiz", lida.bones[0].name)
        assertEquals(-1, lida.bones[0].parentIndex)
        assertEquals("braco", lida.bones[1].name)
        assertEquals(0, lida.bones[1].parentIndex)
        for (i in 0 until 16) assertEquals(inverseBind[i], lida.bones[1].inverseBind[i], 1e-6f)
    }

    @Test
    fun `malha com mais de 65535 vértices usa índice U32 e volta inteira`() {
        val b = KernWriter.Builder()
        val n = 70000
        for (i in 0 until n) {
            val t = i.toFloat() / n
            b.vertex(
                t * 100f, (t * t) * 50f, t * 10f,
                0f, 1f, 0f,
                t, 1f - t,
                1f, 0f, 0f
            )
        }
        b.triangle(0, 1, 2)
        b.triangle(n - 3, n - 2, n - 1)
        val original = b.build()
        val lida = KernReader.read(KernWriter.write(original))
        assertEquals(n, lida.vertexCount)
        assertEquals(n - 3, lida.indices[3])
        assertEquals(n - 1, lida.indices[5])
    }

    @Test
    fun `bytes corrompidos lançam - nunca viram malha silenciosa`() {
        val bytes = KernWriter.write(cubo().build())
        val semMagica = bytes.copyOf().also { it[0] = 'X'.code.toByte() }
        assertLanca(semMagica, "mágica incorreta")
        assertLanca(bytes.copyOf(bytes.size / 2), "arquivo truncado")
        assertLanca(ByteArray(0), "arquivo vazio")
    }

    @Test
    fun `header e contagens sao validados antes de criar arrays da malha`() {
        val valid = KernWriter.write(cubo().build())
        assertLanca(ByteArray(KernFormat.HEADER_SIZE - 1), "header truncado")
        assertLancaHeader(ByteArray(KernFormat.HEADER_SIZE - 1), "header truncado")

        val vertexCountImpossivel = valid.copyOf()
        putInt(vertexCountImpossivel, 32, Int.MAX_VALUE)
        assertLanca(vertexCountImpossivel, "vertexCount sem bloco correspondente")

        val indexCountImpossivel = valid.copyOf()
        putInt(indexCountImpossivel, 36, 2_000_000_001) // múltiplo de 3; corpo declarado não cabe
        assertLanca(indexCountImpossivel, "indexCount sem bytes correspondentes")
        assertLanca(valid.copyOf(valid.size - 1), "último byte truncado")
    }

    @Test
    fun `LOD e indices fora de range sao rejeitados`() {
        val valid = KernWriter.write(cubo().build())
        val vertexCount = ByteBuffer.wrap(valid).order(ByteOrder.LITTLE_ENDIAN).getInt(32)
        val indexCount = ByteBuffer.wrap(valid).order(ByteOrder.LITTLE_ENDIAN).getInt(36)
        val lodStart = KernFormat.HEADER_SIZE + vertexCount * KernFormat.VERTEX_SIZE

        val lodFora = valid.copyOf()
        putInt(lodFora, lodStart + 4, indexCount + 3)
        assertLanca(lodFora, "range de LOD fora do bloco de índices")

        val indicesStart = lodStart + KernFormat.LOD_ENTRY_SIZE
        val indexFora = valid.copyOf()
        putShort(indexFora, indicesStart, vertexCount.toShort())
        assertLanca(indexFora, "índice fora do range de vértices")
    }

    @Test
    fun `clusters com material usam offset correto e leem offset legado conhecido`() {
        val b = cubo()
        b.clusters = byteArrayOf(9, 8, 7, 6)
        b.material(KernMaterial(
            "mat", 0.8f, 0.4f, 0.2f, 1f, 0.1f, 0.7f,
            false, false, false, "base.kimg", ""
        ))
        b.materialGroup(0, 36, 0)
        val bytes = KernWriter.write(b.build())
        val lida = KernReader.read(bytes)
        assertArrayEquals(byteArrayOf(9, 8, 7, 6), lida.clusters!!)
        assertEquals(1, lida.materiais.size)
        assertEquals(1, lida.gruposMaterial.size)

        // Compatibilidade com writer antigo: clusterOffset = início do cluster
        // + tamanho do bloco de materiais (layout físico continua conhecido).
        val tamanhoClusters = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getLong(58)
        val legado = bytes.copyOf()
        putLong(legado, 50, bytes.size.toLong() - tamanhoClusters)
        val lidaLegada = KernReader.read(legado)
        assertArrayEquals(byteArrayOf(9, 8, 7, 6), lidaLegada.clusters!!)
        assertEquals(1, lidaLegada.materiais.size)

        assertLanca(bytes.copyOf(bytes.size - 1), "tabela material truncada")
    }

    private fun assertLanca(bytes: ByteArray, caso: String) {
        try {
            KernReader.read(bytes)
            fail("deveria lançar com $caso")
        } catch (expected: IllegalArgumentException) {
        }
    }

    private fun assertLancaHeader(bytes: ByteArray, caso: String) {
        try {
            KernReader.readHeader(bytes)
            fail("readHeader deveria lançar com $caso")
        } catch (expected: IllegalArgumentException) {
        }
    }

    private fun putInt(bytes: ByteArray, offset: Int, value: Int) {
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, value)
    }

    private fun putShort(bytes: ByteArray, offset: Int, value: Short) {
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putShort(offset, value)
    }

    private fun putLong(bytes: ByteArray, offset: Int, value: Long) {
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putLong(offset, value)
    }

    @Test
    fun `MeshData fromKern mantém contagens e bounds de render`() {
        val kern = KernReader.read(KernWriter.write(cubo().build()))
        val render = MeshData.fromKern(kern)
        assertEquals(kern.vertexCount, render.vertexCount)
        assertEquals(kern.indices.size, render.indices.size)
        assertEquals(kern.boundsMin.x, render.boundsMin.x, 1e-6f)
        assertEquals(kern.boundsMax.z, render.boundsMax.z, 1e-6f)
    }
}
