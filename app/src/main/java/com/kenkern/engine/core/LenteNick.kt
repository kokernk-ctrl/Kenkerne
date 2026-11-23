package com.kenkern.engine.core

/**
 * H3 (v0.1.20) — LENTE: classe "Lente" dos Nicks de câmera (camera.basic,
 * camera.follow) e leitura DEFENSIVA dos valores.
 *
 * Campos (serializados no .ck pela classe "Lente"; chaves estáveis):
 *   fov            FLOAT  campo de visão VERTICAL em graus (perspectiva)
 *   near           FLOAT  plano próximo (m)
 *   far            FLOAT  plano distante (m)
 *   ortografica    BOOL   projeção ortográfica em vez de perspectiva
 *   tamanhoOrto    FLOAT  meia-altura do frustum ortográfico (m)
 *   principal      BOOL   "Câmera principal" — SÓ UMA por cena (H3); renderiza
 *                         em Play e é o alvo padrão do "Ver pela câmera"
 *   alvo           STRING ReferenceId do Nick seguido (Follow Camera); vazio =
 *                         primeiro jogador ativo
 *   distancia      FLOAT  distância horizontal atrás do alvo (Follow Camera, m)
 *   altura         FLOAT  altura acima do alvo (Follow Camera, m)
 *
 * Nick ANTIGO sem a classe: defaults do tipo (nada crasha). Registrar a
 * definição acontece em KenkernBuiltInClasses.registerAll.
 */
object LenteNick {

    const val CLASSE = "Lente"

    const val CAMPO_FOV = "fov"
    const val CAMPO_NEAR = "near"
    const val CAMPO_FAR = "far"
    const val CAMPO_ORTOGRAFICA = "ortografica"
    const val CAMPO_TAMANHO_ORTO = "tamanhoOrto"
    const val CAMPO_PRINCIPAL = "principal"
    const val CAMPO_ALVO = "alvo"
    const val CAMPO_DISTANCIA = "distancia"
    const val CAMPO_ALTURA = "altura"

    const val DEF_FOV = 60f
    const val DEF_NEAR = 0.1f
    const val DEF_FAR = 500f
    const val DEF_TAMANHO_ORTO = 5f
    const val DEF_DISTANCIA = 6f
    const val DEF_ALTURA = 3f

    /** Snapshot dos valores de UMA câmera (reutilizado; nunca criado por frame). */
    class Valores {
        @JvmField var fov: Float = DEF_FOV
        @JvmField var near: Float = DEF_NEAR
        @JvmField var far: Float = DEF_FAR
        @JvmField var ortografica: Boolean = false
        @JvmField var tamanhoOrto: Float = DEF_TAMANHO_ORTO
        @JvmField var principal: Boolean = false
        @JvmField var alvo: String = ""
        @JvmField var distancia: Float = DEF_DISTANCIA
        @JvmField var altura: Float = DEF_ALTURA
    }

    /**
     * Lê os campos da instância "Lente" se existir; campos ausentes/errados
     * caem no default. Defensive: fov 0,1–179,9; far > near; tamanho ≥ 0,01.
     */
    fun ler(nick: Nick, out: Valores) {
        val inst = nick.findClassInstance(CLASSE)
        if (inst == null) {
            out.fov = DEF_FOV; out.near = DEF_NEAR; out.far = DEF_FAR
            out.ortografica = false; out.tamanhoOrto = DEF_TAMANHO_ORTO
            out.principal = false; out.alvo = ""
            out.distancia = DEF_DISTANCIA; out.altura = DEF_ALTURA
            return
        }
        out.fov = inst.getFloat(CAMPO_FOV, DEF_FOV).coerceIn(0.1f, 179.9f)
        out.near = inst.getFloat(CAMPO_NEAR, DEF_NEAR)
        out.far = inst.getFloat(CAMPO_FAR, DEF_FAR)
        out.ortografica = inst.getBool(CAMPO_ORTOGRAFICA, false)
        out.tamanhoOrto = inst.getFloat(CAMPO_TAMANHO_ORTO, DEF_TAMANHO_ORTO)
        out.principal = inst.getBool(CAMPO_PRINCIPAL, false)
        out.alvo = inst.getString(CAMPO_ALVO, "")
        out.distancia = inst.getFloat(CAMPO_DISTANCIA, DEF_DISTANCIA)
        out.altura = inst.getFloat(CAMPO_ALTURA, DEF_ALTURA)
        if (!(out.near.isFinite()) || out.near <= 0f) out.near = DEF_NEAR
        if (!(out.far.isFinite()) || out.far <= out.near) out.far = out.near + 1f
        if (!(out.tamanhoOrto.isFinite()) || out.tamanhoOrto <= 0.01f) out.tamanhoOrto = DEF_TAMANHO_ORTO
        if (!(out.distancia.isFinite())) out.distancia = DEF_DISTANCIA
        if (!(out.altura.isFinite())) out.altura = DEF_ALTURA
    }
}
