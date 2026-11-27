package com.kenkern.engine.diag

import com.kenkern.engine.core.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R1 (v0.1.29) — PROVA GEOMÉTRICA da câmera da checagem "luz" (REPRODUZIR
 * ANTES DE CORRIGIR). O comentário antigo de checarLuz AFIRMAVA "o pixel
 * central cai na FACE +Z" — era FALSO: com a câmera padrão do OrbitCamera
 * (yaw 35°/pitch 28°/dist 9) o olho caía em (−4,56, −3,73, −6,51) — os
 * MESMOS números que o aparelho reportou na v0.1.28 — e o raio central
 * ENTRAVA pela face −Z (VERSO), iluminada por nenhuma das luzes da
 * checagem. Este teste provou o defeito (vermelho) e agora PROVA a correção:
 * a câmera lida de [LuzChecagemContrato] — o MESMO objeto que checarLuz
 * usa — tem que ver a face +Z (a iluminada).
 *
 * CONTRATO DE NORMAIS E DUPA FACE do renderizador (documentado em
 * [LuzChecagemContrato]): normais para FORA + culling GL_BACK — só a face
 * de entrada do raio é vista; luz pontual ilumina dot(n, →luz) > 0;
 * direcional viaja pelo −Z local (rotação padrão (0,0,−1)) — ilumina a +Z.
 */
class R1LuzCameraContratoTest {

    private val alvo = Vec3(0f, 0.5f, 0f)
    private val luzPos = Vec3(0.9f, 1.4f, 1.1f)
    private val viagemDirecional = Vec3(0f, 0f, -1f)

    @Test
    fun `raio central entra pela face +Z (a face iluminada) — pixel central na luz`() {
        val camera = LuzChecagemContrato.camera()
        val eye = Vec3()
        camera.eyePoint(eye)
        val look = Vec3().set(alvo).sub(eye).normalize()

        val face = LuzChecagemContrato.faceDeEntrada(look)
        assertEquals(
            "olho=$eye look=$look — a câmera da checagem TEM que ver a face +Z " +
                "(a iluminada pela pontual de $luzPos e pela direcional)",
            "+Z",
            face
        )

        // O olho está no lado +Z, acima do cubo (não no octante −X/−Y/−Z da v0.1.28).
        assertTrue("olho no lado +Z (eye.z>0)", eye.z > 0.5f)
        assertTrue("olho acima do cubo (eye.y>1)", eye.y > 1f)

        // O hit do raio central na face +Z está DENTRO da face (sem tocar bordas):
        // interseção do raio (eye→alvo) com o plano z=+0,5 do cubo centrado no alvo.
        val t = (eye.z - 0.5f) / (eye.z - alvo.z)
        val hx = eye.x + (alvo.x - eye.x) * t
        val hy = eye.y + (alvo.y - eye.y) * t
        assertTrue("hit x dentro da face ($hx)", kotlin.math.abs(hx) < 0.45f)
        assertTrue("hit y dentro da face ($hy)", hy > 0.05f && hy < 0.95f)
    }

    @Test
    fun `a face +Z é iluminada pela pontual E pela direcional (contrato das luzes)`() {
        val n = Vec3(0f, 0f, 1f)
        val centroFace = Vec3(0f, 0.5f, 0.5f)
        val paraLuz = Vec3().set(luzPos).sub(centroFace).normalize()
        assertTrue("pontual ilumina a +Z (dot>0)", n.dot(paraLuz) > 0f)
        assertTrue(
            "direcional ilumina a +Z (dot com −viagem > 0)",
            n.dot(Vec3(-viagemDirecional.x, -viagemDirecional.y, -viagemDirecional.z)) > 0f
        )
    }

    @Test
    fun `camera antiga da v0_1_28 via o verso — registro da causa raiz`() {
        // Documentação executável do defeito: a câmera PADRÃO do OrbitCamera
        // com o alvo (0, 0,5, 0) gera olho (−4,56, −3,73, −6,51) — o número
        // do aparelho — e entra pela face −Z. Se um dia o padrão do OrbitCamera
        // mudar e este contrato quebrar, a checagem precisa ser reavaliada.
        val cameraPadrao = com.kenkern.engine.render.OrbitCamera()
        cameraPadrao.target.set(0f, 0.5f, 0f)
        val eye = Vec3()
        cameraPadrao.eyePoint(eye)
        val look = Vec3().set(alvo).sub(eye).normalize()
        assertEquals("-Z", LuzChecagemContrato.faceDeEntrada(look))
        assertEquals(-4.56f, eye.x, 0.01f)
        assertEquals(-3.73f, eye.y, 0.01f)
        assertEquals(-6.51f, eye.z, 0.01f)
    }
}
