package com.kenkern.engine.core

/**
 * H2 (v0.1.20) — LUME: classe "Luz" dos Nicks de luz (light.directional,
 * light.point, light.spot) e leitura DEFENSIVA dos valores.
 *
 * Campos (serializados no .ck pela classe "Luz"; chaves estáveis):
 *   cor             VEC3   cor linear 0–1
 *   intensidade     FLOAT  multiplicador da cor
 *   alcance         FLOAT  raio de influência da pontual/spot (m)
 *   anguloInterno   FLOAT  cone cheio do spot (graus)
 *   anguloExterno   FLOAT  fim do falloff suave do spot (graus)
 *   ligado          BOOL   interruptor do usuário (o Play nunca altera)
 *
 * Nick ANTIGO sem a classe (criado antes do LUME): usa os DEFAULTS por tipo —
 * a engine nunca crasha por classe ausente/parcial. Registrar a definição
 * acontece em KenkernBuiltInClasses.registerAll (Inspetor lista e aplica).
 */
object LuzNick {

    const val CLASSE = "Luz"

    const val CAMPO_COR = "cor"
    const val CAMPO_INTENSIDADE = "intensidade"
    const val CAMPO_ALCANCE = "alcance"
    const val CAMPO_ANGULO_INTERNO = "anguloInterno"
    const val CAMPO_ANGULO_EXTERNO = "anguloExterno"
    const val CAMPO_LIGADO = "ligado"

    const val DEF_INTENSIDADE = 1f
    const val DEF_ALCANCE = 8f
    const val DEF_ANGULO_INTERNO = 25f
    const val DEF_ANGULO_EXTERNO = 40f

    /** Snapshot dos valores de UMA luz (reutilizado; nunca criado por frame). */
    class Valores {
        @JvmField var corX: Float = 1f
        @JvmField var corY: Float = 1f
        @JvmField var corZ: Float = 1f
        @JvmField var intensidade: Float = DEF_INTENSIDADE
        @JvmField var alcance: Float = DEF_ALCANCE
        @JvmField var anguloInterno: Float = DEF_ANGULO_INTERNO
        @JvmField var anguloExterno: Float = DEF_ANGULO_EXTERNO
        @JvmField var ligado: Boolean = true
    }

    /**
     * Lê os campos da instância "Luz" se existir; campos ausentes/errados
     * caem no default (Nick antigo ou classe parcial). Ângulos defensivos:
     * interno nunca passa do externo (spot com falloff mínimo de 1°).
     */
    fun ler(nick: Nick, out: Valores) {
        val inst = nick.findClassInstance(CLASSE)
        if (inst == null) {
            out.corX = 1f; out.corY = 1f; out.corZ = 1f
            out.intensidade = DEF_INTENSIDADE
            out.alcance = DEF_ALCANCE
            out.anguloInterno = DEF_ANGULO_INTERNO
            out.anguloExterno = DEF_ANGULO_EXTERNO
            out.ligado = true
            return
        }
        val cor = inst.fields[CAMPO_COR] as? NickValue.Vec3Value
        out.corX = cor?.x ?: 1f; out.corY = cor?.y ?: 1f; out.corZ = cor?.z ?: 1f
        out.intensidade = inst.getFloat(CAMPO_INTENSIDADE, DEF_INTENSIDADE)
        out.alcance = inst.getFloat(CAMPO_ALCANCE, DEF_ALCANCE)
        out.anguloInterno = inst.getFloat(CAMPO_ANGULO_INTERNO, DEF_ANGULO_INTERNO)
        out.anguloExterno = inst.getFloat(CAMPO_ANGULO_EXTERNO, DEF_ANGULO_EXTERNO)
        out.ligado = inst.getBool(CAMPO_LIGADO, true)
        out.intensidade = out.intensidade.coerceAtLeast(0f)
        out.alcance = out.alcance.coerceAtLeast(0.01f)
        if (!(out.anguloExterno.isFinite()) || out.anguloExterno <= 0f) out.anguloExterno = DEF_ANGULO_EXTERNO
        if (!(out.anguloInterno.isFinite()) || out.anguloInterno <= 0f) out.anguloInterno = DEF_ANGULO_INTERNO
        if (out.anguloInterno > out.anguloExterno) {
            out.anguloInterno = (out.anguloExterno * 0.625f).coerceAtLeast(0.5f)
        }
        if (!(out.corX.isFinite())) out.corX = 1f
        if (!(out.corY.isFinite())) out.corY = 1f
        if (!(out.corZ.isFinite())) out.corZ = 1f
    }
}
