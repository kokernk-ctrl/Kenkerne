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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.MoreVert
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
import com.kenkern.engine.ui.editor.CriadorScript
import com.kenkern.engine.ui.editor.LargurasEditorLua
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
    // R3: PERGUNTA O NOME do script novo (padrão script.lua; sufixo _2 só em
    // colisão) — o modelo foi escolhido no menu + e o diálogo confirma o nome.
    var modeloNovo by remember { mutableStateOf<String?>(null) }
    var nomeNovo by remember { mutableStateOf(com.kenkern.engine.ui.editor.CriadorScript.NOME_PADRAO) }
    var textoBusca by remember { mutableStateOf("") }
    var textoSubstituir by remember { mutableStateOf("") }
    var avisoGrande by remember { mutableStateOf(false) }
    var sugestoes by remember { mutableStateOf<List<com.kenkern.engine.ui.editor.SugestaoLua>>(emptyList()) }
    var statusSalvo by remember { mutableStateOf("") }
    val listaEstado = rememberLazyListState()
    var campoAtivo by remember(alvo) { mutableStateOf(TextFieldValue("")) }

    // carrega o conteúdo (arquivo ou fonte inline) + último erro do Console
    val scope = rememberCoroutineScope()
    // R3: clipboard para COPIAR (seleção/linha) e COLAR (multilinha).
    val clipboard = LocalClipboardManager.current
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
        // R3 (v0.1.29) — CAUSA RAIZ do “script abre VAZIO”: a carga é ASSÍNCRONA
        // (withContext IO) e nenhum estado LIDO NA COMPOSIÇÃO mudava depois dela
        // (só nomeArquivo, lido no escopo do título — o LazyColumn e seu
        // items(count = doc.linhas.size) ficavam com o documento INICIAL vazio:
        // a recomposição que atualiza a contagem de linhas NUNCA acontecia).
        // revisao é chave do LaunchedEffect do autosave NA MESMA ESCOPO — bump
        // aqui invalida a composição e o editor mostra as linhas carregadas.
        revisao++
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
            // ---------- barra superior R3: [voltar | nome e estado | Verificar | menu ⋮] ----------
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
                // R3: NOME E ESTADO juntos no espaço de peso — o Verificar
                // (largura FIXA) e o ⋮ ficam SEMPRE visíveis e NUNCA cobrem o
                // “salvo” (sintoma v0.1.28: Verificar cortado na borda e
                // cobrindo o rótulo). Larguras do contrato: LargurasEditorLua.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        nomeArquivo,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = KenkernTextHigh,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (statusSalvo.isNotEmpty()) {
                        Text(
                            stringResource(R.string.editor_lua_salvo),
                            fontSize = 10.sp,
                            color = KenkernSuccess,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
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
                    modifier = Modifier
                        .height(44.dp)
                        .width(LargurasEditorLua.BOTAO_VERIFICAR_DP.dp)
                )
                // R3: menu ⋮ — A−, A+, quebra de linha e Busca moram AQUI (fim
                // do overlay flutuante que cobria a faixa de teclas).
                var menuMais by remember { mutableStateOf(false) }
                Box {
                    KIconButton(
                        icon = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.editor_lua_menu_mais),
                        onClick = { menuMais = true }
                    )
                    androidx.compose.material3.DropdownMenu(
                        expanded = menuMais,
                        onDismissRequest = { menuMais = false }
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(stringResource(R.string.editor_lua_fonte_menos), fontSize = 12.sp) },
                            onClick = {
                                if (tamanhoFonte > 9) tamanhoFonte--
                                menuMais = false
                            }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(stringResource(R.string.editor_lua_fonte_mais), fontSize = 12.sp) },
                            onClick = {
                                if (tamanhoFonte < 20) tamanhoFonte++
                                menuMais = false
                            }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(stringResource(R.string.editor_lua_quebra_linha), fontSize = 12.sp) },
                            leadingIcon = {
                                if (quebrarLinhas) {
                                    androidx.compose.material3.Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.width(16.dp)
                                    )
                                }
                            },
                            onClick = {
                                quebrarLinhas = !quebrarLinhas
                                menuMais = false
                            }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(stringResource(R.string.editor_lua_buscar), fontSize = 12.sp) },
                            onClick = {
                                buscaAberta = !buscaAberta
                                menuMais = false
                            }
                        )
                    }
                }
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
                                    nomeNovo = CriadorScript.NOME_PADRAO
                                    modeloNovo = modelo
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
                // R3: COPIAR (seleção da linha ativa, senão a linha inteira) e
                // COLAR (multilinha — DocumentoLua.colar) na faixa de apoio.
                KIconButton(
                    icon = Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.editor_lua_copiar),
                    onClick = {
                        val linha = doc.linhas[doc.linhaCursor]
                        val sel = campoAtivo.selection
                        val ini = sel.start.coerceIn(0, linha.length)
                        val fim = sel.end.coerceIn(0, linha.length)
                        val texto = if (fim > ini) linha.substring(ini, fim) else linha
                        clipboard.setText(AnnotatedString(texto))
                        KenkernLog.w("EditorLua", "copiar: ${texto.length} caractere(s) da linha ${doc.linhaCursor + 1}")
                    }
                )
                KIconButton(
                    icon = Icons.Filled.ContentPaste,
                    contentDescription = stringResource(R.string.editor_lua_colar),
                    onClick = {
                        val texto = clipboard.getText()?.text ?: ""
                        if (texto.isNotEmpty()) {
                            doc.colar(texto)
                            campoAtivo = TextFieldValue(doc.linhas[doc.linhaCursor])
                            notificarEdicao()
                            atualizarSugestoes()
                        }
                    }
                )
            }
        }

        // R3: DIÁLOGO DO NOME do script novo (padrão script.lua; o modelo já
        // foi escolhido no menu +). Sufixo _2 SÓ em colisão (CriadorScript).
        if (modeloNovo != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { modeloNovo = null },
                title = { Text(stringResource(R.string.editor_lua_nome_titulo), fontSize = 14.sp) },
                text = {
                    OutlinedTextField(
                        value = nomeNovo,
                        onValueChange = { nomeNovo = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                        label = { Text(stringResource(R.string.editor_lua_nome_hint), fontSize = 10.sp) }
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        val nome = vm.criarScriptComNome(nomeNovo, modeloNovo ?: "vazio")
                        modeloNovo = null
                        if (nome.isNotEmpty()) onAbrir(AlvoEditorLua.Arquivo(nome))
                    }) { Text(stringResource(R.string.editor_lua_criar), fontSize = 13.sp) }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { modeloNovo = null }) {
                        Text(stringResource(R.string.hub_cancel), fontSize = 13.sp)
                    }
                }
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
