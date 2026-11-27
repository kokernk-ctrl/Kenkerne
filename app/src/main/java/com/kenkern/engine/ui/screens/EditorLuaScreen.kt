package com.kenkern.engine.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import android.content.pm.ActivityInfo
import com.kenkern.engine.R
import com.kenkern.engine.editor.EditorViewModel
import com.kenkern.engine.diag.KenkernLog
import com.kenkern.engine.ui.components.KButton
import com.kenkern.engine.ui.components.KIconButton
import com.kenkern.engine.ui.editor.AutocompletarLua
import com.kenkern.engine.ui.editor.DocumentoLua
import com.kenkern.engine.ui.editor.ModelosScript
import com.kenkern.engine.ui.editor.SpanLua
import com.kenkern.engine.ui.editor.TokenTipo
import com.kenkern.engine.ui.editor.TokenizadorLua
import com.kenkern.engine.ui.theme.KenkernAmber
import com.kenkern.engine.ui.theme.KenkernBackground
import com.kenkern.engine.ui.theme.KenkernError
import com.kenkern.engine.ui.theme.KenkernSingCobre
import com.kenkern.engine.ui.theme.KenkernSuccess
import com.kenkern.engine.ui.theme.KenkernSurface
import com.kenkern.engine.ui.theme.KenkernSurfaceHigh
import com.kenkern.engine.ui.theme.KenkernTextHigh
import com.kenkern.engine.ui.theme.KenkernTextLow
import com.kenkern.engine.ui.theme.KenkernTextMid
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * C1 (v0.1.26) — EDITOR DE CÓDIGO LUA (TELAR de scripts), em RETRATO,
 * estilo VS Code: abas de arquivos (scripts .lua), números de linha,
 * realce de sintaxe (tokenizador puro), indentação automática, busca e
 * substituir, undo/redo, SALVAR AUTOMÁTICO, fonte monoespaçada com tamanho
 * ajustável, quebra de linha opcional, linha de TECLAS DE APOIO sobre o
 * teclado, AUTOCOMPLETAR OFFLINE (LuaApiCatalog = LUA_API.md) e "Verificar"
 * (compila no LuaJ e sublinha a linha com a mensagem).
 *
 * DESEMPENHO (C1.6): lista de linhas VIRTUALIZADA (LazyColumn) — o realce é
 * construído SÓ para as linhas visíveis; NUNCA um TextField gigante. Arquivos
 * acima de 200 KB mostram o aviso de tamanho.
 *
 * ORIENTAÇÃO (C1.1): ao abrir muda para RETRATO e ao sair volta a PAISAGEM —
 * sem recriar a Activity (configChanges orientation|screenSize|screenLayout|
 * keyboardHidden|density|uiMode no Manifest, ajuste D5 da v0.1.16) + adjustResize
 * (janela redimensionada pelo teclado) + imePadding.
 */

/** Alvo de edição: arquivo do projeto (scripts .lua) ou fonte INLINE do Nick. */
sealed class AlvoEditorLua {
    /** Arquivo dentro de <projeto>/scripts/ (nome simples, ex. "portas.lua"). */
    data class Arquivo(val nome: String) : AlvoEditorLua()

    /** Fonte INLINE da classe Script de um Nick (campo `fonte`; salvo na classe). */
    data class Inline(val nickId: com.kenkern.engine.core.ReferenceId) : AlvoEditorLua()
}

private const val LIMITE_AVISO_BYTES = 200 * 1024

private val corPalavra = KenkernAmber
private val corString = KenkernSuccess
private val corComentario = KenkernTextLow
private val corNumero = KenkernSingCobre
private val corApi = KenkernSurfaceHigh

@Composable
fun EditorLuaScreen(
    vm: EditorViewModel,
    alvo: AlvoEditorLua,
    onFechar: () -> Unit,
    onAbrir: (AlvoEditorLua) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // C1.1: RETRATO ao abrir; volta a PAISAGEM ao sair — SEM recriar a
    // Activity (o Manifest já declara os configChanges).
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        KenkernLog.w("EditorLua", "editor de código aberto — orientação RETRATO (Activity preservada)")
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            KenkernLog.w("EditorLua", "editor de código fechado — orientação PAISAGEM restaurada")
        }
    }

    // ---------- documento ----------
    val doc = remember(alvo) { DocumentoLua() }
    var nomeArquivo by remember(alvo) { mutableStateOf("script.lua") }
    var revisao by remember(alvo) { mutableIntStateOf(0) } // dispara realce/autosave
    var carregado by remember(alvo) { mutableStateOf(false) }
    var ultimoErro by remember(alvo) { mutableStateOf<String?>(null) }
    var linhaErro by remember(alvo) { mutableIntStateOf(-1) }
    // R2 (v0.1.29): PAINEL DE ERROS — lista (linha, mensagem) do Verificar e
    // do Console; toque numa linha do painel pula o cursor para a linha.
    var errosPainel by remember(alvo) { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var tamanhoFonte by remember { mutableIntStateOf(12) }
    var quebrarLinhas by remember { mutableStateOf(false) }
    var buscaAberta by remember { mutableStateOf(false) }
    var textoBusca by remember { mutableStateOf("") }
    var textoSubstituir by remember { mutableStateOf("") }
    var avisoGrande by remember { mutableStateOf(false) }
    var sugestoes by remember { mutableStateOf<List<com.kenkern.engine.ui.editor.SugestaoLua>>(emptyList()) }
    var statusSalvo by remember { mutableStateOf("") }
    val listaEstado = rememberLazyListState()
    var campoAtivo by remember(alvo) { mutableStateOf(TextFieldValue("")) }

    // carrega o conteúdo (arquivo ou fonte inline) + último erro do Console
    val scope = rememberCoroutineScope()
    LaunchedEffect(alvo) {
        withContext(Dispatchers.IO) {
            when (alvo) {
                is AlvoEditorLua.Arquivo -> {
                    val f = vm.scriptFile(alvo.nome)
                    nomeArquivo = alvo.nome
                    doc.carregar(f?.readText() ?: "")
                }
                is AlvoEditorLua.Inline -> {
                    nomeArquivo = vm.fonteInlineNome(alvo.nickId)
                    doc.carregar(vm.fonteInline(alvo.nickId) ?: "")
                }
            }
        }
        carregado = true
        // C1.5: erro de execução do Console abre o script NA LINHA (formato
        // [string "..."]:LINHA: — procurado no último erro do KenkernLog).
        val erro = ultimoErroLuaDoConsole(nomeArquivo)
        if (erro != null) {
            ultimoErro = erro.second
            linhaErro = erro.first
            errosPainel = listOf(erro)
        }
        avisoGrande = doc.tamanhoBytes() > LIMITE_AVISO_BYTES
        listaEstado.scrollToItem(doc.linhaCursor.coerceAtLeast(0))
    }

    // C1.2: SALVAR AUTOMÁTICO — cada edição agenda a gravação (500 ms).
    LaunchedEffect(revisao) {
        if (revisao == 0 || !carregado) return@LaunchedEffect
        delay(500)
        val conteudo = doc.conteudo()
        withContext(Dispatchers.IO) {
            when (alvo) {
                is AlvoEditorLua.Arquivo -> {
                    val f = vm.scriptFile(alvo.nome)
                    if (f != null) {
                        f.parentFile?.mkdirs()
                        f.writeText(conteudo)
                    }
                }
                is AlvoEditorLua.Inline -> vm.salvarFonteInline(alvo.nickId, conteudo)
            }
        }
        statusSalvo = nomeArquivo
        avisoGrande = doc.tamanhoBytes() > LIMITE_AVISO_BYTES
    }

    fun notificarEdicao() {
        revisao++
    }

    // ---------- teclado/cursor ----------
    fun textoAntesDoCursor(): String {
        val l = doc.linhas[doc.linhaCursor]
        return l.substring(0, doc.colunaCursor.coerceAtMost(l.length))
    }

    fun atualizarSugestoes() {
        sugestoes = AutocompletarLua.sugestoes(textoAntesDoCursor())
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(KenkernBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            // ---------- barra superior ----------
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                KIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.editor_back_hub),
                    onClick = onFechar
                )
                Text(
                    nomeArquivo,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KenkernTextHigh,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (statusSalvo.isNotEmpty()) {
                    Text(
                        stringResource(R.string.editor_lua_salvo),
                        fontSize = 10.sp,
                        color = KenkernSuccess,
                        maxLines = 1
                    )
                }
                KButton(
                    texto = stringResource(R.string.editor_lua_verificar),
                    onClick = {
                        // C1.5 + R2: compila no LuaJ (SÓ sintaxe — nada executa),
                        // sublinha a linha e LISTA os erros no painel.
                        val erro = verificarLua(doc.conteudo())
                        if (erro == null) {
                            ultimoErro = null
                            linhaErro = -1
                            errosPainel = emptyList()
                            KenkernLog.w("EditorLua", "verificar: $nomeArquivo compila sem erros")
                        } else {
                            ultimoErro = erro.second
                            linhaErro = erro.first
                            errosPainel = listOf(erro)
                            doc.moverCursor((erro.first - 1).coerceIn(0, doc.linhas.size - 1), 0)
                            scope.launch { listaEstado.scrollToItem((erro.first - 1).coerceIn(0, doc.linhas.size - 1)) }
                            KenkernLog.e("EditorLua", "verificar: $nomeArquivo → linha ${erro.first}: ${erro.second}")
                        }
                        notificarEdicao()
                    },
                    modifier = Modifier.height(44.dp)
                )
            }

            // ---------- abas de arquivos (scripts .lua) ----------
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                val arquivos = vm.scriptsDoProjeto()
                for (arq in arquivos) {
                    com.kenkern.engine.ui.components.KChip(
                        texto = arq,
                        onClick = {
                            if (alvo !is com.kenkern.engine.ui.screens.AlvoEditorLua.Arquivo || alvo.nome != arq) {
                                onAbrir(AlvoEditorLua.Arquivo(arq))
                            }
                        },
                        selecionado = alvo is com.kenkern.engine.ui.screens.AlvoEditorLua.Arquivo && alvo.nome == arq
                    )
                }
                var menuNovo by remember { mutableStateOf(false) }
                Box {
                    KIconButton(
                        icon = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.editor_lua_novo_script),
                        onClick = { menuNovo = true }
                    )
                    androidx.compose.material3.DropdownMenu(expanded = menuNovo, onDismissRequest = { menuNovo = false }) {
                        for (modelo in ModelosScript.NOMES) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            when (modelo) {
                                                "movimento" -> R.string.editor_lua_modelo_movimento
                                                "coletar" -> R.string.editor_lua_modelo_coletar
                                                "gatilho" -> R.string.editor_lua_modelo_gatilho
                                                else -> R.string.editor_lua_modelo_vazio
                                            }
                                        ),
                                        fontSize = 12.sp
                                    )
                                },
                                onClick = {
                                    menuNovo = false
                                    val nome = vm.criarScriptModelo(modelo)
                                    if (nome.isNotEmpty()) onAbrir(AlvoEditorLua.Arquivo(nome))
                                }
                            )
                        }
                    }
                }
            }

            // ---------- busca e substituir ----------
            if (buscaAberta) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    OutlinedTextField(
                        value = textoBusca,
                        onValueChange = { textoBusca = it },
                        label = { Text(stringResource(R.string.editor_lua_buscar), fontSize = 10.sp) },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1f).height(56.dp)
                    )
                    OutlinedTextField(
                        value = textoSubstituir,
                        onValueChange = { textoSubstituir = it },
                        label = { Text(stringResource(R.string.editor_lua_substituir), fontSize = 10.sp) },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1f).height(56.dp)
                    )
                    KButton(
                        texto = stringResource(R.string.editor_lua_buscar_proximo),
                        onClick = {
                            if (!doc.buscar(textoBusca)) {
                                KenkernLog.w("EditorLua", "busca sem resultado: '$textoBusca'")
                            }
                            revisao++
                        },
                        modifier = Modifier.height(44.dp)
                    )
                    KButton(
                        texto = stringResource(R.string.editor_lua_substituir_todos),
                        onClick = {
                            val n = doc.substituirTudo(textoBusca, textoSubstituir)
                            KenkernLog.w("EditorLua", "substituir todos: $n ocorrência(s) de '$textoBusca'")
                            revisao++
                        },
                        modifier = Modifier.height(44.dp)
                    )
                }
            }

            // ---------- aviso de arquivo grande (C1.6: 200 KB) ----------
            if (avisoGrande) {
                Text(
                    stringResource(R.string.editor_lua_aviso_grande),
                    fontSize = 11.sp,
                    color = KenkernError,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
            }

            // ---------- R2: painel de erros (LISTA; toque pula para a linha) ----------
            if (errosPainel.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(KenkernSurface)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        stringResource(R.string.editor_lua_problemas),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = KenkernError
                    )
                    for ((linha, msg) in errosPainel.take(3)) {
                        Text(
                            stringResource(R.string.editor_lua_erro_linha, linha, msg),
                            fontSize = 11.sp,
                            color = KenkernError,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (doc.linhas.isEmpty()) return@clickable
                                    val alvoLinha = (linha - 1).coerceIn(0, doc.linhas.size - 1)
                                    doc.moverCursor(alvoLinha, 0)
                                    scope.launch { listaEstado.scrollToItem(alvoLinha) }
                                    campoAtivo = TextFieldValue(doc.linhas[doc.linhaCursor])
                                    revisao++
                                }
                        )
                    }
                }
            }

            // ---------- EDITOR: lista de linhas VIRTUALIZADA ----------
            val fonteSp = tamanhoFonte.sp
            LazyColumn(
                state = listaEstado,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(
                    count = doc.linhas.size,
                    key = { it }
                ) { i ->
                    LinhaEditor(
                        indice = i,
                        texto = doc.linhas[i],
                        eCursor = i == doc.linhaCursor,
                        linhaComErro = i + 1 == linhaErro,
                        erroEmLinhaCursor = ultimoErro != null && i == doc.linhaCursor && linhaErro == doc.linhaCursor + 1,
                        fonteSp = fonteSp,
                        quebrar = quebrarLinhas,
                        campoAtivo = campoAtivo,
                        onCampoAtivo = { campoAtivo = it },
                        onToque = { col ->
                            doc.moverCursor(i, col)
                            revisao++ // recompõe: a linha ativa muda
                            campoAtivo = TextFieldValue(doc.linhas[i])
                            atualizarSugestoes()
                        },
                        onEdicao = { novo ->
                            if (novo.text.contains('\n')) {
                                // ENTER → quebra com INDENTAÇÃO AUTOMÁTICA
                                doc.aplicarLinha(novo.text.substringBefore('\n'))
                                doc.quebrarLinha()
                                campoAtivo = TextFieldValue(doc.linhas[doc.linhaCursor])
                            } else {
                                doc.aplicarLinha(novo.text)
                                campoAtivo = novo
                            }
                            notificarEdicao()
                            atualizarSugestoes()
                        },
                        onVoltarInicioLinha = {
                            // backspace com a linha VAZIA → junta com a anterior
                            if (doc.apagarAntes()) {
                                campoAtivo = TextFieldValue(doc.linhas[doc.linhaCursor])
                                revisao++
                            }
                        }
                    )
                }
            }

            // ---------- sugestões do autocompletar (offline) ----------
            if (sugestoes.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp)
                ) {
                    for (s in sugestoes) {
                        com.kenkern.engine.ui.components.KChip(
                            texto = s.assinatura,
                            onClick = {
                                // insere o NOME (o resto vem da assinatura na dica)
                                doc.inserirTexto(s.nome)
                                campoAtivo = TextFieldValue(doc.linhas[doc.linhaCursor])
                                sugestoes = emptyList()
                                revisao++
                            }
                        )
                    }
                }
            }

            // ---------- linha de teclas de apoio (C1.3) ----------
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .background(KenkernSurface)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                TeclaChave("Tab") { doc.inserirTexto("    "); campoAtivo = TextFieldValue(doc.linhas[doc.linhaCursor]); revisao++ }
                for (t in listOf("(", ")", "[", "]", "{", "}", "\"", "'", "=", ".", ",", ":", "_", "--")) {
                    TeclaChave(t) {
                        doc.inserirTexto(if (t == "\"") "\"\"" else t)
                        if (t == "\"") doc.moverCursor(doc.linhaCursor, doc.colunaCursor - 1)
                        campoAtivo = TextFieldValue(doc.linhas[doc.linhaCursor])
                        revisao++
                        atualizarSugestoes()
                    }
                }
                KIconButton(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = stringResource(R.string.editor_lua_desfazer),
                    onClick = {
                        if (doc.desfazerEdicao()) {
                            campoAtivo = TextFieldValue(doc.linhas[doc.linhaCursor])
                            revisao++
                        }
                    }
                )
                KIconButton(
                    icon = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = stringResource(R.string.editor_lua_refazer),
                    onClick = {
                        if (doc.refazerEdicao()) {
                            campoAtivo = TextFieldValue(doc.linhas[doc.linhaCursor])
                            revisao++
                        }
                    }
                )
            }
        }

        // ---------- controles de fonte/quebra/busca (canto superior direito) ----------
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
        ) {
            com.kenkern.engine.ui.components.KChip(texto = "A−", onClick = { if (tamanhoFonte > 9) tamanhoFonte-- })
            com.kenkern.engine.ui.components.KChip(texto = "A+", onClick = { if (tamanhoFonte < 20) tamanhoFonte++ })
            com.kenkern.engine.ui.components.KChip(
                texto = if (quebrarLinhas) "⏎ ✓" else "⏎",
                onClick = { quebrarLinhas = !quebrarLinhas }
            )
            com.kenkern.engine.ui.components.KChip(
                texto = stringResource(R.string.editor_lua_buscar),
                onClick = { buscaAberta = !buscaAberta }
            )
        }
    }
}

/** Uma linha do editor: número + conteúdo (campo ativo edita; resto realce). */
@Composable
private fun LinhaEditor(
    indice: Int,
    texto: String,
    eCursor: Boolean,
    linhaComErro: Boolean,
    erroEmLinhaCursor: Boolean,
    fonteSp: androidx.compose.ui.unit.TextUnit,
    quebrar: Boolean,
    campoAtivo: TextFieldValue,
    onCampoAtivo: (TextFieldValue) -> Unit,
    onToque: (Int) -> Unit,
    onEdicao: (TextFieldValue) -> Unit,
    onVoltarInicioLinha: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = (fonteSp.value * 1.6f).dp)
            .background(if (linhaComErro) KenkernError.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable { onToque(texto.length) }
            .padding(horizontal = 4.dp)
    ) {
        Text(
            "${indice + 1}",
            fontSize = (fonteSp.value * 0.8f).sp,
            fontFamily = FontFamily.Monospace,
            color = if (eCursor) KenkernAmber else KenkernTextLow,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.width(36.dp)
        )
        Spacer(Modifier.width(8.dp))
        if (eCursor) {
            BasicTextField(
                value = campoAtivo,
                onValueChange = onEdicao,
                singleLine = !quebrar,
                textStyle = TextStyle(
                    fontSize = fonteSp,
                    fontFamily = FontFamily.Monospace,
                    color = KenkernTextHigh
                ),
                cursorBrush = SolidColor(KenkernAmber),
                keyboardOptions = KeyboardOptions.Default,
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (erroEmLinhaCursor) {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .background(KenkernError.copy(alpha = 0.15f))
                            )
                        }
                        inner()
                    }
                }
            )
        } else {
            Text(
                realceLua(texto),
                fontSize = fonteSp,
                fontFamily = FontFamily.Monospace,
                color = KenkernTextHigh,
                maxLines = if (quebrar) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Realce de UMA linha (tokenizador puro; estado de bloco é ignorado aqui — o realce das linhas visíveis começa limpo e o comentário de linha cobre o caso comum). */
@Composable
private fun realceLua(texto: String): AnnotatedString = remember(texto) {
    val spans: List<SpanLua> = TokenizadorLua.marcarLinha(texto, TokenizadorLua.Estado())
    buildAnnotatedString {
        append(texto)
        for (s in spans) {
            val cor = when (s.tipo) {
                TokenTipo.PALAVRA_CHAVE -> corPalavra
                TokenTipo.STRING -> corString
                TokenTipo.COMENTARIO -> corComentario
                TokenTipo.NUMERO -> corNumero
                TokenTipo.API -> corApi
                else -> null
            } ?: continue
            addStyle(
                SpanStyle(color = cor, fontWeight = if (s.tipo == TokenTipo.PALAVRA_CHAVE) FontWeight.SemiBold else null),
                s.ini, s.fim
            )
        }
    }
}

/** Tecla de apoio (C1.3) — insere no cursor. */
@Composable
private fun TeclaChave(texto: String, onClick: () -> Unit) {
    Text(
        texto,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        color = KenkernTextHigh,
        modifier = Modifier
            .background(KenkernSurfaceHigh, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

/**
 * C1.5 + R2 (v0.1.29): verifica a SINTAXE no COMPILADOR do LuaJ (LuaC) —
 * compila e NADA EXECUTA — e devolve (linha, mensagem) do primeiro erro, ou
 * null quando compila.
 *
 * R2: o caminho antigo (JsePlatform.standardGlobals().load) construía o
 * ambiente COMPLETO (com os/io — fora do sandbox do runtime) e passava pelo
 * Globals.load, cuja tabela de exceções ENGOLE Exception genérica
 * (catch Exception → valor de erro sem lançar) — o resultado dependia do
 * estado interno do Globals. LuaC.instance.compile é determinístico:
 * erro de sintaxe = LuaError com a linha na mensagem.
 */
fun verificarLua(conteudo: String): Pair<Int, String>? {
    return try {
        org.luaj.vm2.compiler.LuaC.instance.compile(
            java.io.ByteArrayInputStream(conteudo.toByteArray(Charsets.UTF_8)),
            "verificar"
        )
        null
    } catch (t: Throwable) {
        val msg = t.message ?: t.javaClass.simpleName
        val linha = Regex(":([0-9]+):").find(msg)?.groupValues?.get(1)?.toIntOrNull() ?: 1
        Pair(linha, msg.substringAfterLast(": ", msg))
    }
}

/**
 * C1.5: o ÚLTIMO erro de execução do Console que menciona o script —
 * devolve (linha, mensagem) para abrir o editor NA LINHA.
 */
fun ultimoErroLuaDoConsole(nomeScript: String): Pair<Int, String>? {
    for (registro in KenkernLog.registros().asReversed()) {
        if (registro.nivel != com.kenkern.engine.diag.KenkernLog.Nivel.E) continue
        val texto = registro.mensagem + " " + (registro.detalhe ?: "")
        if (nomeScript.isNotBlank() && !texto.contains(nomeScript.substringBeforeLast('.'))) continue
        val linha = Regex(":([0-9]+):").find(texto)?.groupValues?.get(1)?.toIntOrNull()
        if (linha != null) return Pair(linha, texto.substringAfterLast(": ", texto))
    }
    return null
}
