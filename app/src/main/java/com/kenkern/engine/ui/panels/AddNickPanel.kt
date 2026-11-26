package com.kenkern.engine.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kenkern.engine.R
import com.kenkern.engine.ui.components.KChip
import com.kenkern.engine.core.NickKindGroup
import com.kenkern.engine.core.NickKindRegistry
import com.kenkern.engine.editor.AddNickLayout
import com.kenkern.engine.editor.AddNickStore
import com.kenkern.engine.editor.EditorViewModel
import com.kenkern.engine.ui.AppLanguage
import com.kenkern.engine.ui.nickKindDescription
import com.kenkern.engine.ui.nickKindIcon
import com.kenkern.engine.ui.theme.KenkernAmber
import com.kenkern.engine.ui.theme.KenkernBackground
import com.kenkern.engine.ui.theme.KenkernOutline
import com.kenkern.engine.ui.theme.KenkernSurface
import com.kenkern.engine.ui.theme.KenkernSurfaceHigh
import com.kenkern.engine.ui.theme.KenkernTextHigh
import com.kenkern.engine.ui.theme.KenkernTextLow
import com.kenkern.engine.ui.theme.KenkernTextMid

/**
 * JANELA "ADICIONAR NICK" (T0.3; REFEITA no G2, v0.1.19):
 *  - abre pelo "+" da Hierarquia; a janela usa a ALTURA TODA do viewport, tem
 *    60% da largura da TELA e pode passar por cima do Inspetor acoplado
 *    (a decisão de cobrir é do usuário, ao abrir);
 *  - CABEÇALHO FIXO de UMA linha de 48dp: título + busca + fechar (G2.1);
 *  - TODO o resto em UM único LazyColumn que rola junto (G2.2):
 *      1. seletor "Como filho do selecionado / Na raiz" = 2 CHIPS de 36dp;
 *      2. abas de categoria = CHIPS em linha com rolagem horizontal e NOME
 *         COMPLETO (o TabRow antigo cortava para "R…", "B…", "C…" e ocupava
 *         altura preciosa);
 *      3. cartões de tipo (ícone, nome localizado, descrição, favorito);
 *  - contentPadding inferior com os INSETS (G2.4): o último tipo do catálogo
 *    é alcançável com os ~240dp de viewport útil (paisagem 800×360);
 *  - BARREIRA DE TOQUE na raiz do painel (G2.5): rolagem e toques NUNCA
 *    movem a câmera;
 *  - as alturas são matemática pura em [AddNickLayout] (testada na JVM —
 *    cabeçalho 48dp ≤ 20% da altura útil; lista mínima + último cartão
 *    alcançáveis com 240dp);
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
    val recentes = remember { AddNickStore.recentes(context).filter { NickKindRegistry.find(it)?.visivel == true } }

    // Grupos COM tipos registrados (ordem do enum) + aba Recentes no início.
    val gruposComTipos = NickKindGroup.entries.filter { g ->
        NickKindRegistry.visiveisByGroup(g).isNotEmpty()
    }
    val tiposDoGrupo: (Int) -> List<String> = { idx ->
        if (idx == 0) {
            recentes
        } else {
            val g = gruposComTipos.getOrNull(idx - 1)
            if (g == null) {
                emptyList()
            } else {
                val doGrupo = NickKindRegistry.visiveisByGroup(g).map { it.key }
                // Favoritos primeiro (o resto na ordem do registro).
                doGrupo.sortedByDescending { it in favoritos }
            }
        }
    }

    // Rótulos das abas e estado de busca — calculados ANTES do LazyColumn
    // (stringResource é @Composable: não pode rodar dentro do builder).
    val titulosAbas = listOf(stringResource(R.string.addnick_recentes)) +
        gruposComTipos.map { if (idioma == "pt") it.labelPt else it.labelEn }
    val buscaAtiva = busca.isNotBlank()

    Surface(
        color = KenkernSurfaceHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, KenkernOutline),
        shape = RoundedCornerShape(0.dp),
        modifier = Modifier
            .fillMaxHeight()
            // G2.5: BARREIRA DE TOQUE — sem pointerInput o painel não é
            // hit-testável e TODO toque na área vazia atravessava para o
            // viewport (movia a câmera). A rolagem da janela mora no painel:
            // NUNCA chega à câmera.
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(Modifier.fillMaxSize()) {
            // ---------- Cabeçalho FIXO: título + busca + fechar (G2.1) ----------
            // UMA linha de 48dp (AddNickLayout.CABECALHO_DP ≤ 20% da altura útil):
            // nada de cabeçalho em 3 camadas — título, busca e fechar juntos.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(AddNickLayout.CABECALHO_DP.dp).padding(start = 12.dp)
            ) {
                Text(
                    stringResource(R.string.addnick_title),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KenkernTextHigh,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                // Busca COMPACTA: campo de 36dp dentro do cabeçalho de 48dp
                // (o OutlinedTextField antigo media 64dp e empurrava a lista).
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(KenkernSurface)
                        .border(1.dp, KenkernOutline, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp)
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = KenkernTextMid,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    BasicTextField(
                        value = busca,
                        onValueChange = { busca = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 12.sp, color = KenkernTextHigh),
                        cursorBrush = SolidColor(KenkernAmber),
                        modifier = Modifier.weight(1f),
                        decorationBox = { campoInterno ->
                            Box {
                                if (busca.isEmpty()) {
                                    Text(
                                        stringResource(R.string.addnick_search),
                                        fontSize = 11.sp,
                                        color = KenkernTextLow,
                                        maxLines = 1
                                    )
                                }
                                campoInterno()
                            }
                        }
                    )
                }
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

            // ---------- UM único LazyColumn: chips + abas + cartões (G2.2) ----------
            // Tudo rola JUNTO — nada mais tem layout próprio com altura que
            // some (o bug: título+busca+abas+rodapé consumiam os ~240dp e a
            // lista ficava com altura zero).
            val insetInferior = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 8.dp,
                    end = 8.dp,
                    top = 4.dp,
                    // G2.4: padding inferior COM INSETS — o último tipo do
                    // catálogo é alcançável mesmo com a barra de gestos.
                    bottom = AddNickLayout.paddingInferiorDp(insetInferior.value).dp
                )
            ) {
                // 1) Seletor "filho/raiz": 2 CHIPS compactos de 36dp (G2.2) —
                //    os RadioButtons antigos gastavam 96dp de altura.
                item(key = "modo") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        // P2 (v0.1.25): chips do visual Singularidade.
                        KChip(
                            texto = stringResource(R.string.addnick_as_child),
                            onClick = { comoFilho = true },
                            selecionado = comoFilho,
                            modifier = Modifier.height(AddNickLayout.CHIP_DP.dp)
                        )
                        KChip(
                            texto = stringResource(R.string.addnick_at_root),
                            onClick = { comoFilho = false },
                            selecionado = !comoFilho,
                            modifier = Modifier.height(AddNickLayout.CHIP_DP.dp)
                        )
                    }
                }

                // 2) Abas de categoria: CHIPS com NOME COMPLETO em linha com
                //    rolagem horizontal (o TabRow antigo cortava para "R…").
                item(key = "abas") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        for ((i, titulo) in titulosAbas.withIndex()) {
                            // P2 (v0.1.25): chips do visual Singularidade.
                            KChip(
                                texto = titulo,
                                onClick = {
                                    busca = ""
                                    aba = i
                                },
                                selecionado = if (buscaAtiva) i == 0 else aba == i,
                                modifier = Modifier.height(AddNickLayout.CHIP_DP.dp)
                            )
                        }
                    }
                }

                // 3) Cartões de tipo.
                val lista: List<String> = if (buscaAtiva) {
                    // Busca global: nome (en/pt), descrição (en/pt) e chave.
                    val q = busca.trim().lowercase()
                    NickKindRegistry.visiveis().filter { k ->
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
                    item(key = "vazio") {
                        // Mensagem centralizada ocupando o RESTO da lista.
                        Box(
                            Modifier
                                .fillParentMaxSize()
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (aba == 0 && recentes.isEmpty() && !buscaAtiva) {
                                    stringResource(R.string.addnick_no_recent)
                                } else stringResource(R.string.addnick_empty),
                                fontSize = 12.sp,
                                color = KenkernTextMid,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
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
