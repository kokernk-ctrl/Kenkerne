package com.kenkern.engine.render

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * AUXILIARES DE EDITOR (E5, v0.1.18): todo Nick SEM malha própria — luz,
 * câmera, área, sinal, surgimento e o Nick Vazio — ganha um AUXILIAR
 * desenhado SÓ NO EDITOR (nunca em Play, nunca no jogo exportado):
 *  - tamanho CONSTANTE na tela (escala pela distância da câmera, como o
 *    gizmo de transformação);
 *  - COR POR GRUPO (luz âmbar-claro, câmera azul-claro, áreas verde, base
 *    cinza); SELECIONADO = âmbar #FFA940 (mesma cor do contorno de seleção);
 *  - seleção por toque com tolerância 24dp (pickNickAt no KenkernSurfaceView);
 *  - formas: luz DIRECIONAL = SETA na direção do Nick; luz PONTUAL = ESFERA
 *    (+ anel externo de alcance, simbólico até a classe de luz do LUME);
 *    luz de FOCO = CONE; CÂMERA = PIRÂMIDE com o FOV REAL (o da câmera da
 *    engine até a LENTE criar o campo FOV no Nick — registrado no NICKS.md);
 *    ÁREA = volume em ARAME (caixa 1×1×1); demais tipos = LOSANGO (ícone).
 *
 * Geometria TODA local, tamanho UNITÁRIO (o renderer aplica escala de tela
 * constante), pares GL_LINES (6 floats/vértice: pos3 + rgb) — o mesmo formato
 * do gizmo de eixos e do wireframe do colisor. Puro Kotlin (testável em JVM);
 * a classificação de tipos é a MESMA de MalhaNick.ehKindAuxiliar.
 */
object EditorHelpers {

    // ---------------- formas ----------------

    const val FORMA_SETA = 0
    const val FORMA_ESFERA = 1
    const val FORMA_CONE = 2
    const val FORMA_PIRAMIDE = 3
    const val FORMA_CAIXA = 4
    const val FORMA_DIAMANTE = 5

    /** Forma do auxiliar pela chave do tipo (classificação do E5). */
    fun formaDo(kind: String): Int = when (kind) {
        "light.directional" -> FORMA_SETA
        "light.point" -> FORMA_ESFERA
        "light.spot" -> FORMA_CONE
        "camera.basic", "camera.follow" -> FORMA_PIRAMIDE
        "area.volume" -> FORMA_CAIXA
        else -> FORMA_DIAMANTE // area.spawn, area.signal, nick.empty, …
    }

    // ---------------- cores por grupo ----------------

    // LUZ #FFD54F · CÂMERA #4FC3F7 · ÁREAS #66BB6A · BASE #B0BEC5
    private val COR_LUZ = floatArrayOf(1.00f, 0.835f, 0.310f)
    private val COR_CAMERA = floatArrayOf(0.310f, 0.765f, 0.969f)
    private val COR_AREAS = floatArrayOf(0.400f, 0.733f, 0.416f)
    private val COR_BASE = floatArrayOf(0.690f, 0.745f, 0.773f)

    /** Cor do auxiliar pelo GRUPO do tipo (out[0..2]). */
    fun corDoGrupo(kind: String, out: FloatArray) {
        val c = when {
            kind.startsWith("light.") -> COR_LUZ
            kind.startsWith("camera.") -> COR_CAMERA
            kind.startsWith("area.") -> COR_AREAS
            else -> COR_BASE
        }
        out[0] = c[0]; out[1] = c[1]; out[2] = c[2]
    }

    // ---------------- geometrias (GL_LINES, 6 floats/vértice) ----------------

    private fun linha(dados: ArrayList<Float>, r: Float, g: Float, b: Float,
                      ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float) {
        dados.add(ax); dados.add(ay); dados.add(az); dados.add(r); dados.add(g); dados.add(b)
        dados.add(bx); dados.add(by); dados.add(bz); dados.add(r); dados.add(g); dados.add(b)
    }

    /** Círculo unitário no plano pedido ('xy', 'xz' ou 'yz'), raio [raio], centro (cx,cy,cz). */
    private fun circulo(dados: ArrayList<Float>, r: Float, g: Float, b: Float,
                        cx: Float, cy: Float, cz: Float, raio: Float, plano: String, segmentos: Int = 24) {
        for (i in 0 until segmentos) {
            val a0 = (i.toFloat() / segmentos) * (2.0 * Math.PI).toFloat()
            val a1 = ((i + 1).toFloat() / segmentos) * (2.0 * Math.PI).toFloat()
            val x0 = cos(a0) * raio; val y0 = sin(a0) * raio
            val x1 = cos(a1) * raio; val y1 = sin(a1) * raio
            when (plano) {
                "xy" -> linha(dados, r, g, b, cx + x0, cy + y0, cz, cx + x1, cy + y1, cz)
                "xz" -> linha(dados, r, g, b, cx + x0, cy, cz + y0, cx + x1, cy, cz + y1)
                else -> linha(dados, r, g, b, cx, cy + x0, cz + y0, cx, cy + x1, cz + y1)
            }
        }
    }

    /** SETA da luz direcional: aponta para o −Z local (o "para onde a luz viaja"). */
    fun buildSeta(cor: FloatArray): FloatArray {
        val d = ArrayList<Float>(24 * 6)
        val (r, g, b) = Triple(cor[0], cor[1], cor[2])
        // haste + cabeça (4 linhas) + cruz na cauda
        linha(d, r, g, b, 0f, 0f, 0.45f, 0f, 0f, -0.45f)
        for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
            linha(d, r, g, b, 0f, 0f, -0.45f, sx * 0.18f, sy * 0.18f, -0.45f + 0.28f)
        }
        linha(d, r, g, b, -0.14f, 0f, 0.45f, 0.14f, 0f, 0.45f)
        linha(d, r, g, b, 0f, -0.14f, 0.45f, 0f, 0.14f, 0.45f)
        return d.toFloatArray()
    }

    /** ESFERA da luz pontual (ícone r=0,5) + ANEL DE ALCANCE simbólico (r=1). */
    fun buildEsfera(cor: FloatArray): FloatArray {
        val d = ArrayList<Float>(24 * 4 * 6)
        val (r, g, b) = Triple(cor[0], cor[1], cor[2])
        circulo(d, r, g, b, 0f, 0f, 0f, 0.5f, "xy")
        circulo(d, r, g, b, 0f, 0f, 0f, 0.5f, "xz")
        circulo(d, r, g, b, 0f, 0f, 0f, 0.5f, "yz")
        // anel de alcance: mais apagado (60%) — o valor real entra com a classe de luz (LUME)
        circulo(d, r * 0.6f, g * 0.6f, b * 0.6f, 0f, 0f, 0f, 1f, "xz")
        return d.toFloatArray()
    }

    /**
     * H2 (v0.1.20) — ESFERA do ALCANCE REAL da luz pontual: raio = alcance
     * (m) em escala de MUNDO (o renderer NÃO aplica escala de tela constante
     * nesta forma — o que se vê é o raio de influência de verdade). Anel
     * externo em 60% da cor para não competir com o ícone.
     */
    fun buildEsferaAlcance(cor: FloatArray, alcance: Float): FloatArray {
        val raio = alcance.coerceAtLeast(0.01f)
        val d = ArrayList<Float>(24 * 4 * 6)
        val (r, g, b) = Triple(cor[0], cor[1], cor[2])
        circulo(d, r, g, b, 0f, 0f, 0f, raio, "xy")
        circulo(d, r, g, b, 0f, 0f, 0f, raio, "xz")
        circulo(d, r, g, b, 0f, 0f, 0f, raio, "yz")
        return d.toFloatArray()
    }

    /** CONE da luz de foco: ápice no Nick, abrindo para −Z. */
    fun buildCone(cor: FloatArray): FloatArray {
        val d = ArrayList<Float>((24 + 4) * 6)
        val (r, g, b) = Triple(cor[0], cor[1], cor[2])
        val raio = 0.45f
        val z = -0.8f
        circulo(d, r, g, b, 0f, 0f, z, raio, "xy", 20)
        // 4 geratrizes (a cada 90°)
        for (i in 0 until 4) {
            val a = (i.toFloat() / 4) * (2.0 * Math.PI).toFloat()
            linha(d, r, g, b, 0f, 0f, 0f, cos(a) * raio, sin(a) * raio, z)
        }
        return d.toFloatArray()
    }

    /**
     * H2 (v0.1.20) — CONE do ÂNGULO E ALCANCE REAIS da luz de foco: ápice no
     * Nick, abrindo para −Z com a meia-abertura = ângulo EXTERNO/2 e altura =
     * alcance (m), em escala de MUNDO. O anel da boca fica em 60% da cor.
     */
    fun buildConeAlcance(cor: FloatArray, anguloExternoGraus: Float, alcance: Float): FloatArray {
        val altura = alcance.coerceAtLeast(0.01f)
        val raio = altura * tan(Math.toRadians((anguloExternoGraus.coerceIn(1f, 179f) * 0.5f).toDouble())).toFloat()
        val d = ArrayList<Float>((20 + 4) * 6)
        val (r, g, b) = Triple(cor[0], cor[1], cor[2])
        val z = -altura
        circulo(d, r * 0.6f, g * 0.6f, b * 0.6f, 0f, 0f, z, raio, "xy", 20)
        for (i in 0 until 4) {
            val a = (i.toFloat() / 4) * (2.0 * Math.PI).toFloat()
            linha(d, r, g, b, 0f, 0f, 0f, cos(a) * raio, sin(a) * raio, z)
        }
        return d.toFloatArray()
    }

    /**
     * PIRÂMIDE da câmera com o FOV REAL: meia-altura = tan(fov/2), meia-largura
     * = meia-altura × aspect, base a 1 unidade do olho (escala constante na
     * tela é aplicada pelo renderer). A fov é a da CÂMERA DA ENGINE hoje; o
     * campo FOV no Nick entra com a LENTE (registrado no NICKS.md).
     */
    fun buildPiramide(cor: FloatArray, fovGraus: Float, aspect: Float): FloatArray {
        val d = ArrayList<Float>(8 * 6)
        val (r, g, b) = Triple(cor[0], cor[1], cor[2])
        val meiaAltura = tan(Math.toRadians((fovGraus * 0.5f).toDouble())).toFloat()
        val meiaLargura = meiaAltura * aspect
        val z = -1f
        // 4 geratrizes do olho (0,0,0) aos cantos da base
        linha(d, r, g, b, 0f, 0f, 0f, -meiaLargura, -meiaAltura, z)
        linha(d, r, g, b, 0f, 0f, 0f, meiaLargura, -meiaAltura, z)
        linha(d, r, g, b, 0f, 0f, 0f, meiaLargura, meiaAltura, z)
        linha(d, r, g, b, 0f, 0f, 0f, -meiaLargura, meiaAltura, z)
        // retângulo da base
        linha(d, r, g, b, -meiaLargura, -meiaAltura, z, meiaLargura, -meiaAltura, z)
        linha(d, r, g, b, meiaLargura, -meiaAltura, z, meiaLargura, meiaAltura, z)
        linha(d, r, g, b, meiaLargura, meiaAltura, z, -meiaLargura, meiaAltura, z)
        linha(d, r, g, b, -meiaLargura, meiaAltura, z, -meiaLargura, -meiaAltura, z)
        return d.toFloatArray()
    }

    /** VOLUME EM ARAME da área: caixa 1×1×1 centrada no Nick. */
    fun buildCaixa(cor: FloatArray): FloatArray {
        val d = ArrayList<Float>(12 * 6)
        val (r, g, b) = Triple(cor[0], cor[1], cor[2])
        val h = 0.5f
        val c = arrayOf(
            floatArrayOf(-h, -h, -h), floatArrayOf(h, -h, -h), floatArrayOf(h, h, -h), floatArrayOf(-h, h, -h),
            floatArrayOf(-h, -h, h), floatArrayOf(h, -h, h), floatArrayOf(h, h, h), floatArrayOf(-h, h, h)
        )
        val arestas = arrayOf(
            0 to 1, 1 to 2, 2 to 3, 3 to 0,
            4 to 5, 5 to 6, 6 to 7, 7 to 4,
            0 to 4, 1 to 5, 2 to 6, 3 to 7
        )
        for ((a, bIdx) in arestas) {
            linha(d, r, g, b, c[a][0], c[a][1], c[a][2], c[bIdx][0], c[bIdx][1], c[bIdx][2])
        }
        return d.toFloatArray()
    }

    /** LOSANGO (octaedro em arame): ícone genérico dos demais tipos sem malha. */
    fun buildDiamante(cor: FloatArray): FloatArray {
        val d = ArrayList<Float>(12 * 6)
        val (r, g, b) = Triple(cor[0], cor[1], cor[2])
        val s = 0.45f
        val v = arrayOf(
            floatArrayOf(s, 0f, 0f), floatArrayOf(-s, 0f, 0f),
            floatArrayOf(0f, s, 0f), floatArrayOf(0f, -s, 0f),
            floatArrayOf(0f, 0f, s), floatArrayOf(0f, 0f, -s)
        )
        val arestas = arrayOf(
            0 to 2, 2 to 1, 1 to 3, 3 to 0,
            0 to 4, 4 to 1, 1 to 5, 5 to 0,
            2 to 4, 4 to 3, 3 to 5, 5 to 2
        )
        for ((a, bIdx) in arestas) {
            linha(d, r, g, b, v[a][0], v[a][1], v[a][2], v[bIdx][0], v[bIdx][1], v[bIdx][2])
        }
        return d.toFloatArray()
    }

    /** Meia-extensão (half extents) da AABB de TOQUE do auxiliar (mundo). */
    const val AABB_TOQUE = 0.4f

    /** Fração da meia-tela usada pela escala constante na tela (como o gizmo). */
    const val FRACAO_TELA = 0.055f
}
