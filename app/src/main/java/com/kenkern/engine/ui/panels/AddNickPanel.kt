package com.kenkern.engine.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kenkern.engine.R
import com.kenkern.engine.core.NickKindGroup
import com.kenkern.engine.core.NickKindRegistry
import com.kenkern.engine.editor.AddNickStore
import com.kenkern.engine.editor.EditorViewModel
import com.kenkern.engine.ui.AppLanguage
import com.kenkern.engine.ui.nickKindDescription
import com.kenkern.engine.ui.nickKindIcon
import com.kenkern.engine.ui.theme.KenkernAmber
import com.kenkern.engine.ui.theme.KenkernBackground
import com.kenkern.engine.ui.theme.KenkernOutline
import com.kenkern.engine.ui.theme.KenkernSelection
import com.kenkern.engine.ui.theme.KenkernSurface
import com.kenkern.engine.ui.theme.KenkernSurfaceHigh
import com.kenkern.engine.ui.theme.KenkernTextHigh
import com.kenkern.engine.ui.theme.KenkernTextLow
import com.kenkern.engine.ui.theme.KenkernTextMid

/**
 * JANELA "ADICIONAR NICK" (T0.3, adendo v4 — seção 3):
 *  - abre pelo "+" da Hierarquia; painel lateral (~40% da largura em paisagem)
 *    DENTRO do viewport — nunca cobre a Hierarquia, a TopBar nem a StatusBar;
 *  - busca, abas por GRUPO (só grupos com tipos IMPLEMENTADOS) + aba Recentes;
 *  - cartões com ícone, nome localizado e 1 linha de descrição;
 *  - FAVORITOS com estrela (persistidos); favoritos primeiro no grupo;
 *  - opção "Como filho do selecionado / Na raiz" (padrão: filho);
 *  - toque cria: UM Command (desfazer remove), classes padrão do tipo,
 *    nasce SELECIONADO no centro da tela projetado no chão ([spawnXz]).
 */
@Composable
fun AddNickPanel(
    vm: EditorViewModel,
    spawnXz: FloatArray?,
    onFechar: () -> Unit
) {
    val context = LocalContext.current
    val idioma = AppLanguage.currentTag(context)
    var busca by remember { mutableStateOf("") }
    var aba by remember { mutableIntStateOf(0) }
    var comoFilho by remember { mutableStateOf(true) }
    // Favoritos mudam com o toque: estado observável.
    var favoritosRev by remember { mutableIntStateOf(0) }
    val favoritos = remember(favoritosRev) { AddNickStore.favoritos(context) }
    val recentes = remember { AddNickStore.recentes(context) }

    // Grupos COM tipos registrados (ordem do enum) + aba Recentes no início.
    val gruposComTipos = NickKindGroup.entries.filter { g ->
        NickKindRegistry.byGroup(g).isNotEmpty()
    }
    val tiposDoGrupo: (Int) -> List<String> = { idx ->
        if (idx == 0) {
            recentes
        } else {
            val g = gruposComTipos.getOrNull(idx - 1)
            if (g == null) {
                emptyList()
            } else {
                val doGrupo = NickKindRegistry.byGroup(g).map { it.key }
                // Favoritos primeiro (o resto na ordem do registro).
                doGrupo.sortedByDescending { it in favoritos }
            }
        }
    }
    Surface(
        color = KenkernSurfaceHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, KenkernOutline),
        shape = RoundedCornerShape(0.dp),
        modifier = Modifier
            .fillMaxHeight()
            // E3 (v0.1.18): BARREIRA DE TOQUE — sem pointerInput o painel não é
            // hit-testável e TODO toque na área vazia atravessava para o viewport
            // (movia a câmera). Agora o toque mora no painel: rolagem/taps nunca
            // chegam à câmera.
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(Modifier.fillMaxSize()) {
            // ---------- Cabeçalho: título + fechar ----------
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(44.dp).padding(start = 12.dp)
            ) {
                Text(
                    stringResource(R.string.addnick_title),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KenkernTextHigh,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onFechar, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.addnick_close),
                        tint = KenkernTextMid,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            HorizontalDivider(thickness = 1.dp, color = KenkernOutline)

            // ---------- Busca ----------
            OutlinedTextField(
                value = busca,
                onValueChange = { busca = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = KenkernTextMid, modifier = Modifier.size(16.dp)) },
                placeholder = { Text(stringResource(R.string.addnick_search), fontSize = 12.sp, color = KenkernTextLow) },
                textStyle = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
            )

            // ---------- Abas (Recentes + grupos com tipos) ----------
            val titulosAbas = listOf(stringResource(R.string.addnick_recentes)) +
                gruposComTipos.map { if (idioma == "pt") it.labelPt else it.labelEn }
            val buscaAtiva = busca.isNotBlank()
            TabRow(
                selectedTabIndex = if (buscaAtiva) 0 else aba,
                containerColor = KenkernSurfaceHigh,
                contentColor = KenkernAmber,
                divider = {}
            ) {
                for ((i, titulo) in titulosAbas.withIndex()) {
                    Tab(
                        selected = if (buscaAtiva) i == 0 else aba == i,
                        onClick = { busca = ""; aba = i },
                        text = { Text(titulo, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }

            // ---------- Cartões ----------
            val lista: List<String> = if (buscaAtiva) {
                // Busca global: nome (en/pt), descrição (en/pt) e chave.
                val q = busca.trim().lowercase()
                NickKindRegistry.all().filter { k ->
                    k.key.contains(q, true) ||
                        k.labelEn.lowercase().contains(q) ||
                        k.labelPt.lowercase().contains(q) ||
                        k.descEn.lowercase().contains(q) ||
                        k.descPt.lowercase().contains(q)
                }.map { it.key }
            } else {
                tiposDoGrupo(if (aba == 0 && recentes.isEmpty() && gruposComTipos.isNotEmpty()) 1 else aba)
            }

            if (lista.isEmpty()) {
                // E3: weight(1f) — a mensagem ocupa o RESTO (o rodapé continua visível).
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        if (aba == 0 && recentes.isEmpty() && !buscaAtiva) {
                            stringResource(R.string.addnick_no_recent)
                        } else stringResource(R.string.addnick_empty),
                        fontSize = 12.sp,
                        color = KenkernTextMid,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                // E3 (v0.1.18): a lista vive em Column COM RODAPÉ abaixo — fillMaxSize
                // consumia TODA a altura restante (o rodapé media 0 e o FIM da lista
                // ficava sob a borda/gestos: último tipo inalcançável). Agora:
                // weight(1f) = altura limitada ao ESPAÇO QUE SOBRA depois de
                // cabeçalho+busca+abas+rodapé; contentPadding inferior garante o
                // último cartão inteiro dentro da área visível. Sem verticalScroll
                // aninhado (a única rolagem é a da própria LazyColumn).
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(lista, key = { it }) { kind ->
                        KindCard(
                            kind = kind,
                            favorito = kind in favoritos,
                            onCriar = {
                                AddNickStore.registrarCriacao(context, kind)
                                vm.addNickOfKind(kind, comoFilho, spawnXz)
                                onFechar()
                            },
                            onAlternarFavorito = { favoritosRev++ }
                        )
                        HorizontalDivider(thickness = 1.dp, color = KenkernBackground)
                    }
                }
            }

            // ---------- Rodapé: filho do selecionado / na raiz ----------
            HorizontalDivider(thickness = 1.dp, color = KenkernOutline)
            Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = comoFilho, onClick = { comoFilho = true })
                    Text(stringResource(R.string.addnick_as_child), fontSize = 12.sp, color = KenkernTextHigh)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !comoFilho, onClick = { comoFilho = false })
                    Text(stringResource(R.string.addnick_at_root), fontSize = 12.sp, color = KenkernTextHigh)
                }
            }
        }
    }
}

/** Cartão de tipo: ícone + nome localizado + descrição + estrela de favorito. */
@Composable
private fun KindCard(
    kind: String,
    favorito: Boolean,
    onCriar: () -> Unit,
    onAlternarFavorito: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCriar)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            nickKindIcon(kind),
            contentDescription = null,
            tint = KenkernAmber,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                nickKindLabelLocalizado(kind),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = KenkernTextHigh,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                nickKindDescription(kind),
                fontSize = 11.sp,
                color = KenkernTextMid,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            kind,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = KenkernTextLow,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(4.dp))
        IconButton(
            onClick = {
                AddNickStore.alternarFavorito(context, kind)
                onAlternarFavorito()
            },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                if (favorito) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = stringResource(R.string.addnick_favorite),
                tint = if (favorito) KenkernAmber else KenkernTextLow,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Rótulo do tipo no idioma atual (helper local do cartão). */
@Composable
private fun nickKindLabelLocalizado(kind: String): String =
    NickKindRegistry.labelOf(
        kind,
        AppLanguage.currentTag(androidx.compose.ui.platform.LocalContext.current)
    )
