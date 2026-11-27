package com.kenkern.engine.diag

import com.kenkern.engine.core.math.Vec3
import com.kenkern.engine.render.OrbitCamera

/**
 * R1 (v0.1.29) — CONTRATO DE CÂMERA/LUZ da checagem "luz" do Diagnóstico.
 * PURO (sem GL): constantes USADAS por [Diagnostico.checarLuz] e provadas por
 * R1LuzCameraContratoTest na JVM — o teste lê ESTE objeto, não cópias.
 *
 * CAUSA RAIZ (v0.1.28, comprovada por teste vermelho): a checagem usava a
 * câmera PADRÃO do OrbitCamera (yaw 35°, pitch 28°, dist 9) → olho
 * (−4,56, −3,73, −6,51), no octante −X/−Y/−Z: o raio central ENTRAVA pela
 * face −Z (o VERSO do quad). A luz pontual (0,9; 1,4; 1,1) e a direcional
 * (viagem (0,0,−1)) iluminam a face +Z — DE COSTAS para a câmera. O pixel
 * só tinha ambiente hemisférico: pontual LIGADA (102,63,70) × esperado
 * ~(243,108,111); direcional vermelha IGUAL ao sol padrão (59,63,70) —
 * nenhuma delas conseguia dominar o vermelho no VERSO. O comentário antigo
 * da checagem AFIRMAVA "o pixel central cai na FACE +Z" sem prova: era falso.
 *
 * CONTRATO DE NORMAIS E DUPA FACE do renderizador (dependência da checagem):
 *  - as normais dos cordos apontam PARA FORA (BodyMeshes/KernReader) e o
 *    culling é GL_BACK (GlesBackend.kt:545-546) — a câmera só vê a FACE DE
 *    ENTRADA do raio; a face oposta é descartada (nada de "ver através");
 *  - a luz pontual ilumina a face com dot(normal, direção→luz) > 0;
 *  - a direcional viaja pelo −Z LOCAL do Nick (rotação padrão = (0,0,−1)):
 *    ilumina a face +Z com dot(n, −viagem) = 1 (cheio);
 *  - logo a câmera da checagem PRECISA ver a face +Z: olho no lado +Z,
 *    acima do centro do cubo — yaw 200°, pitch −30°, distância 6
 *    (olho ≈ (1,78, 3,50, 4,89), hit do raio central ≈ (0,19; 0,80; 0,50)
 *    — dentro da face +Z, longe das bordas).
 */
object LuzChecagemContrato {

    /** Olho no lado +Z (200°), acima do alvo (pitch −30°), perto (6 m). */
    const val YAW_GRAUS: Float = 200f
    const val PITCH_GRAUS: Float = -30f
    const val DISTANCIA: Float = 6f

    /** Alvo = CENTRO do cubo (topo 1,0 — pixel central na face +Z). */
    const val ALVO_Y: Float = 0.5f

    /** Luz pontual da checagem (lado +Z, acima — ilumina a face +Z). */
    const val LUZ_X: Float = 0.9f
    const val LUZ_Y: Float = 1.4f
    const val LUZ_Z: Float = 1.1f

    /** Câmera da checagem — EXATAMENTE a que checarLuz renderiza. */
    fun camera(): OrbitCamera {
        val camera = OrbitCamera()
        camera.target.set(0f, ALVO_Y, 0f)
        camera.yawDegrees = YAW_GRAUS
        camera.pitchDegrees = PITCH_GRAUS
        camera.distance = DISTANCIA
        return camera
    }

    /**
     * Face de ENTRADA do raio central no cubo 1×1×1 centrado em (0, ALVO_Y, 0):
     * a normal com MENOR dot(look, n) (face mais de frente para a câmera).
     * Retorna "+X"/−…/"+Z"/"−Z" — prova reutilizável (teste + futuro).
     */
    fun faceDeEntrada(look: Vec3): String {
        var melhor = "+X"
        var menorDot = look.x
        if (-look.x < menorDot) { menorDot = -look.x; melhor = "-X" }
        if (look.y < menorDot) { menorDot = look.y; melhor = "+Y" }
        if (-look.y < menorDot) { menorDot = -look.y; melhor = "-Y" }
        if (look.z < menorDot) { menorDot = look.z; melhor = "+Z" }
        if (-look.z < menorDot) { menorDot = -look.z; melhor = "-Z" }
        return melhor
    }
}
