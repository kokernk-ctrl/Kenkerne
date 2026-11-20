package com.kenkern.engine.core

/**
 * NICK COM MALHA importada (ETAPA A): o Nick carrega a classe "Malha" criada
 * pelo importador — campos (todos serializados no .ck, nada novo no formato):
 *
 *     path      STRING  caminho RELATIVO do .KERN (ex.: "assets/models/carro.KERN")
 *     aabbMin*  FLOAT   AABB local (para picking/foco SEM carregar a malha)
 *     aabbMax*  FLOAT
 *     escalaFit FLOAT  escala sugerida ("Ajustar ao tamanho" ~2 m; Nick mantém editável)
 *
 * Leitura defensiva: campos ausentes/errados devolvem default (Nick antigo,
 * classe parcial) — a engine NUNCA crasha por classe malformada; falha de
 * carregamento da malha em si é tratada no AssetMeshLibrary com erro claro.
 */
object MalhaNick {

    const val CLASSE = "Malha"

    /** Caminho relativo do .KERN ou null se o Nick não tem malha importada. */
    fun path(nick: Nick): String? {
        val inst = nick.findClassInstance(CLASSE) ?: return null
        val p = (inst.fields["path"] as? NickValue.StringValue)?.value ?: return null
        return if (p.isBlank()) null else p
    }

    fun temMalha(nick: Nick): Boolean = path(nick) != null

    /** Meia-extensão LOCAL (half extents) da AABB da malha; cubo (0.5) sem malha. */
    fun meiaExtensaoLocal(nick: Nick, out: FloatArray) {
        val inst = nick.findClassInstance(CLASSE)
        if (inst == null) {
            out[0] = 0.5f; out[1] = 0.5f; out[2] = 0.5f
            return
        }
        val minX = f(inst, "aabbMinX", -0.5f); val minY = f(inst, "aabbMinY", -0.5f); val minZ = f(inst, "aabbMinZ", -0.5f)
        val maxX = f(inst, "aabbMaxX", 0.5f); val maxY = f(inst, "aabbMaxY", 0.5f); val maxZ = f(inst, "aabbMaxZ", 0.5f)
        out[0] = (maxX - minX) * 0.5f
        out[1] = (maxY - minY) * 0.5f
        out[2] = (maxZ - minZ) * 0.5f
    }

    /** Centro LOCAL da AABB da malha (zero para o cubo padrão). */
    fun centroLocal(nick: Nick, out: FloatArray) {
        val inst = nick.findClassInstance(CLASSE)
        if (inst == null) {
            out[0] = 0f; out[1] = 0f; out[2] = 0f
            return
        }
        val minX = f(inst, "aabbMinX", -0.5f); val minY = f(inst, "aabbMinY", -0.5f); val minZ = f(inst, "aabbMinZ", -0.5f)
        val maxX = f(inst, "aabbMaxX", 0.5f); val maxY = f(inst, "aabbMaxY", 0.5f); val maxZ = f(inst, "aabbMaxZ", 0.5f)
        out[0] = (minX + maxX) * 0.5f
        out[1] = (minY + maxY) * 0.5f
        out[2] = (minZ + maxZ) * 0.5f
    }

    /** Escala sugerida pela opção "Ajustar ao tamanho" (1.0 = sem ajuste). */
    fun escalaFit(nick: Nick): Float {
        val inst = nick.findClassInstance(CLASSE) ?: return 1f
        return f(inst, "escalaFit", 1f)
    }

    private fun f(inst: NickClassInstance, campo: String, def: Float): Float =
        (inst.fields[campo] as? NickValue.FloatValue)?.value ?: def
}
