package com.kenkern.engine.diag

import com.kenkern.engine.R

/**
 * S1 (v0.1.24) — MAPEAMENTO id → título LOCALIZADO da checagem do Diagnóstico.
 *
 * CAUSA RAIZ (bug S1a, DiagnosticoScreen.kt:68-92 na v0.1.23): o `when`
 * id→recurso NÃO tinha casos para "materiais", "luz", "layout" e
 * "joystick" — os quatro caíam no `else -> R.string.diag_check_ck` e
 * exibiam o título ".ck (cena)" sobre as mensagens dessas checagens
 * (exatamente o que o usuário reportou: joystick e materiais sob .ck).
 *
 * PURO (sem Compose) para a JVM provar o contrato
 * (DiagNomesChecagemTest): TODO id do catálogo
 * ([Diagnostico.novaListaDeChecagens]) tem título, NENHUM título serve
 * para dois ids, e id desconhecido ERRA alto — nunca mais um `else`
 * silencioso reutilizando o título de outra checagem.
 */
object DiagNomesChecagem {

    /** id → recurso do título (values/strings.xml + values-pt). */
    private val mapa: Map<String, Int> = mapOf(
        "nativo" to R.string.diag_check_nativo,
        "etc2-nativo" to R.string.diag_check_etc2_nativo,
        "malha-nativa" to R.string.diag_check_malha_nativa,
        "ck" to R.string.diag_check_ck,
        "kern" to R.string.diag_check_kern,
        "materiais" to R.string.diag_check_materiais,
        "gltf" to R.string.diag_check_gltf,
        "fbx" to R.string.diag_check_fbx,
        "importar" to R.string.diag_check_importar,
        "kimg" to R.string.diag_check_kimg,
        "etc2" to R.string.diag_check_etc2,
        "tex-cena" to R.string.diag_check_tex_cena,
        "cena" to R.string.diag_check_cena,
        "luz" to R.string.diag_check_luz,
        "astc" to R.string.diag_check_astc,
        "picking" to R.string.diag_check_picking,
        "toque" to R.string.diag_check_toque,
        "shaders" to R.string.diag_check_shaders,
        "buffers" to R.string.diag_check_buffers,
        "glcaps" to R.string.diag_check_glcaps,
        "memoria" to R.string.diag_check_memoria,
        "release" to R.string.diag_check_release,
        "layout" to R.string.diag_check_layout,
        "joystick" to R.string.diag_check_joystick,
        "lua-api" to R.string.diag_check_lua_api,
        "jogo" to R.string.diag_check_jogo,
        "play-sair" to R.string.diag_check_play_sair
    )

    /**
     * Recurso do título da checagem. Id fora do catálogo ERRA
     * (nada de `else` silencioso reutilizando o título de outra checagem —
     * é exatamente assim que o bug S1a nasceu).
     */
    fun resource(id: String): Int = mapa[id]
        ?: error("checagem '$id' não tem título mapeado em DiagNomesChecagem")
}
