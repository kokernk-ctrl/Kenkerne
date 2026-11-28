package com.kenkern.engine.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.kenkern.engine.ui.theme.KenkernTokenApi
import com.kenkern.engine.ui.theme.KenkernTokenComentario
import com.kenkern.engine.ui.theme.KenkernTokenNumero
import com.kenkern.engine.ui.theme.KenkernTokenPalavra
import com.kenkern.engine.ui.theme.KenkernTokenString

/**
 * X3 (v0.1.31) — realce de UMA linha do editor Lua, FUNÇÃO PURA do texto.
 *
 * Causa raiz do texto ilegível (EditorLuaScreen.kt, antes da v0.1.31):
 * 1. `corApi = KenkernSurfaceHigh` (#2D2D30, 1,21:1 contra o fundo #1E1E1E) —
 *    cor de SUPERFÍCIE usada como cor de TEXTO: todo identificador da API Lua
 *    ficava cinza-invisível.
 * 2. A linha com o cursor era um BasicTextField PLANO (sem spans) e a linha
 *    sem cursor usava os spans coloridos — ao mover o cursor, os tokens da
 *    linha anterior ganhavam cor e os da nova perdiam ("a palavra afetada
 *    muda conforme o cursor se move").
 *
 * A correção: UMA função pura usada nos DOIS caminhos — `Text` das linhas sem
 * cursor e `VisualTransformation` do BasicTextField da linha com cursor. O
 * estilo de cada linha NÃO depende da posição do cursor, e as cores vêm de
 * Theme.kt com contraste >= 4,5:1 (X3ContrasteEditorLuaTest).
 *
 * Estado de bloco: como antes, cada linha recomeça limpo (Estado() novo) — o
 * comentário de linha cobre o caso comum; comentário de bloco multilinha
 * cruzando a linha do cursor é um limite conhecido (P4, rodada seguinte).
 */
fun realceLinhaLua(texto: String): AnnotatedString {
    val spans: List<SpanLua> = TokenizadorLua.marcarLinha(texto, TokenizadorLua.Estado())
    return buildAnnotatedString {
        append(texto)
        for (s in spans) {
            val cor = when (s.tipo) {
                TokenTipo.PALAVRA_CHAVE -> KenkernTokenPalavra
                TokenTipo.STRING -> KenkernTokenString
                TokenTipo.COMENTARIO -> KenkernTokenComentario
                TokenTipo.NUMERO -> KenkernTokenNumero
                TokenTipo.API -> KenkernTokenApi
                else -> null
            } ?: continue
            addStyle(
                SpanStyle(
                    color = cor,
                    fontWeight = if (s.tipo == TokenTipo.PALAVRA_CHAVE) FontWeight.SemiBold else null
                ),
                s.ini, s.fim
            )
        }
    }
}

/**
 * A MESMA transformação para o BasicTextField da linha do cursor: os spans de
 * cor são aplicados POR CIMA do textStyle (o texto continua editável e os
 * offsets são preservados — nenhum caractere muda, só o estilo).
 */
val TransformacaoRealceLua = VisualTransformation { texto ->
    TransformedText(realceLinhaLua(texto.text), OffsetMapping.Identity)
}

/** Cor de um tipo de token (uso do teste e do desenho). */
fun corDoToken(tipo: TokenTipo): Color? = when (tipo) {
    TokenTipo.PALAVRA_CHAVE -> KenkernTokenPalavra
    TokenTipo.STRING -> KenkernTokenString
    TokenTipo.COMENTARIO -> KenkernTokenComentario
    TokenTipo.NUMERO -> KenkernTokenNumero
    TokenTipo.API -> KenkernTokenApi
    else -> null
}
