package com.kenkern.engine.render

import com.kenkern.engine.core.Nick
import com.kenkern.engine.core.MalhaNick
import com.kenkern.engine.core.math.Mat4
import com.kenkern.engine.core.math.Vec3
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * FONTE ÚNICA da matemática de picking (D1, v0.1.16):
 *
 *  - o OLHO e o RAIO de tela vêm de UMA função só — [OrbitCamera.screenRay] —
 *    usada pelo picking do viewport (KenkernSurfaceView.pickNickAt), pelo
 *    hit-test do gizmo (TransformGizmo) e pelas checagens do Diagnóstico
 *    ("picking" e "toque no cubo"). O foco usa OrbitCamera.focusOn, que lê a
 *    MESMA base interna. Nenhum outro lugar recalcula olho/raio;
 *  - o hit-test raio×AABB e a distância de tela até a AABB projetada moram
 *    AQUI (antes eram métodos privados do KenkernSurfaceView — o Diagnóstico
 *    não podia reproduzir o MESMO cálculo, e divergências passavam impunes).
 *
 * Puro Kotlin (sem android.*): os testes JVM rodam o MESMO código do aparelho.
 */
object PickingMath {

    // Rascunhos pré-alocados (sem alocação por chamada).
    private val aabbMeiaLocal = FloatArray(3)
    private val aabbCentroLocal = FloatArray(3)

    /**
     * T0.3 (adendo v4): interseção do RAIO com o PLANO DO CHÃO (y = 0) —
     * usada pelo ponto de surgimento do Nick novo da janela "Adicionar Nick"
     * ("posição do centro da tela projetada no chão"). Puro Kotlin (testes JVM
     * rodam o mesmo código).
     *
     * Escreve x/z em [out] (índices 0 e 1) e devolve o t do raio; devolve -1
     * quando não há interseção À FRENTE (olhando para cima ou paralelo ao
     * chão) — o chamador usa o fallback que preferir.
     */
    fun interseccaoChao(
        ox: Float, oy: Float, oz: Float,
        dx: Float, dy: Float, dz: Float,
        out: FloatArray
    ): Float {
        if (abs(dy) < 1e-6f) return -1f // paralelo ao chão
        val t = -oy / dy
        if (t < 0f) return -1f // interseção ATRÁS (olhando para cima)
        out[0] = ox + dx * t
        out[1] = oz + dz * t
        return t
    }

    /**
     * AABB em MUNDO do Nick: 8 cantos da AABB LOCAL (malha importada tem AABB
     * própria pela classe "Malha"; Nick padrão = cubo de 1 m) transformados
     * pela matriz de mundo. Escreve em [outMin]/[outMax] (pré-alocados).
     */
    fun worldAabb(nick: Nick, outMin: Vec3, outMax: Vec3) {
        MalhaNick.meiaExtensaoLocal(nick, aabbMeiaLocal)
        MalhaNick.centroLocal(nick, aabbCentroLocal)
        val m = nick.transform.worldMatrix.m
        outMin.set(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
        outMax.set(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        var i = 0
        while (i < 8) {
            val lx = aabbCentroLocal[0] + if (i and 1 == 0) -aabbMeiaLocal[0] else aabbMeiaLocal[0]
            val ly = aabbCentroLocal[1] + if (i and 2 == 0) -aabbMeiaLocal[1] else aabbMeiaLocal[1]
            val lz = aabbCentroLocal[2] + if (i and 4 == 0) -aabbMeiaLocal[2] else aabbMeiaLocal[2]
            val wx = m[0] * lx + m[4] * ly + m[8] * lz + m[12]
            val wy = m[1] * lx + m[5] * ly + m[9] * lz + m[13]
            val wz = m[2] * lx + m[6] * ly + m[10] * lz + m[14]
            if (wx < outMin.x) outMin.x = wx
            if (wx > outMax.x) outMax.x = wx
            if (wy < outMin.y) outMin.y = wy
            if (wy > outMax.y) outMax.y = wy
            if (wz < outMin.z) outMin.z = wz
            if (wz > outMax.z) outMax.z = wz
            i++
        }
    }

    /**
     * Slab test raio-AABB; retorna t (>= 0) do primeiro cruzamento ou -1 se
     * não há interseção. Exatamente o algoritmo que o viewport usa no tap.
     */
    fun rayAabb(
        o: Vec3, d: Vec3,
        cx: Float, cy: Float, cz: Float,
        ex: Float, ey: Float, ez: Float
    ): Float {
        var tmin = 0f
        var tmax = Float.MAX_VALUE
        // X
        if (abs(d.x) < 1e-9f) {
            if (abs(o.x - cx) > ex) return -1f
        } else {
            var t1 = (cx - ex - o.x) / d.x
            var t2 = (cx + ex - o.x) / d.x
            if (t1 > t2) { val tmp = t1; t1 = t2; t2 = tmp }
            if (t1 > tmin) tmin = t1
            if (t2 < tmax) tmax = t2
            if (tmin > tmax) return -1f
        }
        // Y
        if (abs(d.y) < 1e-9f) {
            if (abs(o.y - cy) > ey) return -1f
        } else {
            var t1 = (cy - ey - o.y) / d.y
            var t2 = (cy + ey - o.y) / d.y
            if (t1 > t2) { val tmp = t1; t1 = t2; t2 = tmp }
            if (t1 > tmin) tmin = t1
            if (t2 < tmax) tmax = t2
            if (tmin > tmax) return -1f
        }
        // Z
        if (abs(d.z) < 1e-9f) {
            if (abs(o.z - cz) > ez) return -1f
        } else {
            var t1 = (cz - ez - o.z) / d.z
            var t2 = (cz + ez - o.z) / d.z
            if (t1 > t2) { val tmp = t1; t1 = t2; t2 = tmp }
            if (t1 > tmin) tmin = t1
            if (t2 < tmax) tmax = t2
            if (tmin > tmax) return -1f
        }
        return tmin
    }

    /**
     * Distância (px da VIEW) do toque à AABB projetada na tela: projeta os 8
     * cantos da caixa para o framebuffer, converte para px da view e mede a
     * distância ao retângulo. Retorna -1 se algum canto cair atrás da câmera
     * (a caixa cruza o plano da câmera — só o teste exato vale nesse caso).
     */
    fun projectedAabbDistance(
        vp: Mat4, renderW: Float, renderH: Float, bufToView: Float,
        touchX: Float, touchY: Float,
        cx: Float, cy: Float, cz: Float,
        ex: Float, ey: Float, ez: Float
    ): Float {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        val mm = vp.m
        var i = 0
        while (i < 8) {
            val px = if (i and 1 == 0) cx - ex else cx + ex
            val py = if (i and 2 == 0) cy - ey else cy + ey
            val pz = if (i and 4 == 0) cz - ez else cz + ez
            val cw = mm[3] * px + mm[7] * py + mm[11] * pz + mm[15]
            if (abs(cw) < 1e-6f || cw <= 0f) return -1f
            val inv = 1f / cw
            val ndcX = (mm[0] * px + mm[4] * py + mm[8] * pz + mm[12]) * inv
            val ndcY = (mm[1] * px + mm[5] * py + mm[9] * pz + mm[13]) * inv
            val bx = (ndcX * 0.5f + 0.5f) * renderW * bufToView
            val by = (1f - (ndcY * 0.5f + 0.5f)) * renderH * bufToView
            if (bx < minX) minX = bx
            if (bx > maxX) maxX = bx
            if (by < minY) minY = by
            if (by > maxY) maxY = by
            i++
        }
        val dx = maxOf(minX - touchX, 0f, touchX - maxX)
        val dy = maxOf(minY - touchY, 0f, touchY - maxY)
        return sqrt(dx * dx + dy * dy)
    }
}
