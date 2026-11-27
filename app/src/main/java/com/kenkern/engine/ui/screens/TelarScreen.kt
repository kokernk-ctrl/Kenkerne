package com.kenkern.engine.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.kenkern.engine.assets.FontesKenkern
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kenkern.engine.R
import com.kenkern.engine.editor.EditorViewModel
import com.kenkern.engine.diag.KenkernLog
import com.kenkern.engine.ui.components.KButton
import com.kenkern.engine.ui.components.KChip
import com.kenkern.engine.ui.components.KIconButton
import com.kenkern.engine.ui.telar.ALTURA_REF
import com.kenkern.engine.ui.telar.AncoraUi
import com.kenkern.engine.ui.telar.ComandoAdicionar
import com.kenkern.engine.ui.telar.ComandoOrdemZ
import com.kenkern.engine.ui.telar.ComandoPropriedade
import com.kenkern.engine.ui.telar.ComandoRemover
import com.kenkern.engine.ui.telar.ComandoTransformar
import com.kenkern.engine.ui.telar.DocumentoUi
import com.kenkern.engine.ui.telar.ElementoUi
import com.kenkern.engine.ui.telar.ElementoUiInstancia
import com.kenkern.engine.ui.telar.FONTES_OFL
import com.kenkern.engine.ui.telar.LARGURA_REF
import com.kenkern.engine.ui.theme.KenkernAmber
import com.kenkern.engine.ui.theme.KenkernBackground
import com.kenkern.engine.ui.theme.KenkernSingCobre
import com.kenkern.engine.ui.theme.KenkernSurface
import com.kenkern.engine.ui.theme.KenkernSurfaceHigh
import com.kenkern.engine.ui.theme.KenkernTextHigh
import com.kenkern.engine.ui.theme.KenkernTextLow
import com.kenkern.engine.ui.theme.KenkernTextMid

/**
 * U1 (v0.1.27) — TELAR: editor de UI (aba "UI"), canvas 1920x1080 com
 * zoom/pan/régua/snap, painel de camadas (ordem Z, ocultar, travar),
 * Inspetor de UI, pré-visualização 16:9/18:9/20:9 e undo/redo POR COMMAND.
 * Salva em ui .kui (TelarModelo). Fontes OFL: os 8 NOMES ficam no modelo
 * (FONTES_OFL); as TTFs embutidas ficam para a sequência (meta final 24).
 */
@Composable
fun TelarScreen(vm: EditorViewModel, onVoltarEditor: () -> Unit) {
    // R4: doc como ESTADO — o botão do estado vazio troca o documento inteiro
    // (template) e a tela recompõe.
    var doc by remember { mutableStateOf(vm.carregarUiDoc() ?: DocumentoUi("ui")) }
    var revisao by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var proporcao by remember { mutableStateOf("16:9") }
    var menuAdd by remember { mutableStateOf(false) }
    var menuFonte by remember { mutableStateOf(false) }

    // R4: proporção de pré-visualização — a FONTE ÚNICA é o TelarAjuste
    val (larguraPrev, alturaPrev) = com.kenkern.engine.ui.telar.TelarAjuste.dimensoesDe(proporcao)

    // U1.5: SALVAR AUTOMÁTICO do .kui (500 ms após cada mudança)
    LaunchedEffect(revisao) {
        if (revisao == 0) return@LaunchedEffect
        kotlinx.coroutines.delay(500)
        vm.salvarUiDoc(doc)
    }

    // R4: insets de status E navegação pelo ÚNICO KScaffold — a barra
    // superior do TELAR desenhava SOB a barra de status (print v0.1.28:
    // título, desfazer, refazer e Adicionar cobertos pelos ícones do sistema).
    com.kenkern.engine.ui.components.KScaffold {
    Column(Modifier.fillMaxSize().background(KenkernBackground)) {
        // ---------- barra superior do TELAR ----------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            KIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.telar_voltar),
                onClick = onVoltarEditor
            )
            Text(
                stringResource(R.string.telar_titulo) + " · " + doc.nome,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = KenkernTextHigh,
                modifier = Modifier.weight(1f)
            )
            // pré-visualização em 3 proporções
            for (p in listOf("16:9", "18:9", "20:9")) {
                KChip(texto = p, onClick = { proporcao = p }, selecionado = proporcao == p)
            }
            // undo/redo por COMMAND
            KIconButton(
                icon = Icons.AutoMirrored.Filled.Undo,
                contentDescription = stringResource(R.string.editor_lua_desfazer),
                onClick = { if (doc.desfazer()) revisao++ }
            )
            KIconButton(
                icon = Icons.AutoMirrored.Filled.Redo,
                contentDescription = stringResource(R.string.editor_lua_refazer),
                onClick = { if (doc.refazer()) revisao++ }
            )
            // adicionar elemento (os 8)
            Box {
                KButton(
                    texto = stringResource(R.string.telar_adicionar),
                    onClick = { menuAdd = true },
                    primario = true,
                    modifier = Modifier.height(44.dp)
                )
                DropdownMenu(expanded = menuAdd, onDismissRequest = { menuAdd = false }) {
                    for (tipo in ElementoUi.entries) {
                        DropdownMenuItem(
                            text = { Text(tipo.key, fontSize = 12.sp) },
                            onClick = {
                                menuAdd = false
                                doc.executar(
                                    ComandoAdicionar(
                                        ElementoUiInstancia(
                                            doc.proximoId(), tipo, tipo.key.substringAfter('.'),
                                            LARGURA_REF / 4, ALTURA_REF / 4,
                                            if (tipo == ElementoUi.JOYSTICK) 448 else 320,
                                            if (tipo == ElementoUi.JOYSTICK) 448 else 96
                                        )
                                    )
                                )
                                revisao++
                                KenkernLog.w("Telar", "elemento adicionado: ${tipo.key}")
                            }
                        )
                    }
                }
            }
        }

        Row(Modifier.fillMaxSize()) {
            // ---------- painel de camadas (ordem Z, ocultar, travar) ----------
            Column(
                Modifier
                    .width(190.dp)
                    .fillMaxHeight()
                    .background(KenkernSurface)
                    .padding(4.dp)
            ) {
                Text(
                    stringResource(R.string.telar_camadas),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = KenkernTextMid
                )
                LazyColumn {
                    items(doc.elementos.asReversed(), key = { it.id }) { e ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { doc.selecaoId = e.id; revisao++ }
                                .background(
                                    if (doc.selecaoId == e.id) KenkernAmber.copy(alpha = 0.15f)
                                    else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 4.dp)
                        ) {
                            Text(
                                e.nome,
                                fontSize = 11.sp,
                                color = if (e.oculto) KenkernTextLow else KenkernTextHigh,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.Filled.Visibility,
                                contentDescription = null,
                                tint = if (e.oculto) KenkernTextLow else KenkernTextMid,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable {
                                        doc.executar(ComandoPropriedade(e.id, "oculto", e.oculto, !e.oculto))
                                        revisao++
                                    }
                            )
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = null,
                                tint = if (e.travado) KenkernAmber else KenkernTextLow,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable {
                                        doc.executar(ComandoPropriedade(e.id, "travado", e.travado, !e.travado))
                                        revisao++
                                    }
                            )
                            Icon(
                                Icons.Filled.KeyboardArrowUp,
                                contentDescription = null,
                                tint = KenkernTextMid,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable {
                                        doc.executar(ComandoOrdemZ(e.id, 1))
                                        revisao++
                                    }
                            )
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                tint = KenkernTextMid,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable {
                                        doc.executar(ComandoOrdemZ(e.id, -1))
                                        revisao++
                                    }
                            )
                        }
                    }
                }
            }

            // ---------- R4: canvas com LETTERBOX — INTEIRO cabe, na proporção ----------
            // Causa raiz do "16:9 quase quadrado (~1,25:1)" do print: escala
            // FIXA 0,28 sobre 1920dp×1080dp (537×302dp) CORTADA pelos painéis
            // (190+230dp) — só ~380dp de largura visível. Agora a escala é de
            // AJUSTE ao espaço disponível (TelarAjuste) e o canvas usa a
            // dimensão da PROPORÇÃO ESCOLHIDA (16:9/18:9/20:9), nunca a fixa.
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(KenkernBackground)
            ) {
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                    val escalaAjuste = com.kenkern.engine.ui.telar.TelarAjuste.escalaDeAjuste(
                        constraints.maxWidth.toFloat(),
                        constraints.maxHeight.toFloat(),
                        larguraPrev.toFloat(),
                        alturaPrev.toFloat()
                    )
                    val esc = escalaAjuste * zoom

                    // R4: ESTADO VAZIO — orientação + botão que cria a tela do
                    // template (as camadas não ficam mais "vazias sem orientação").
                    if (doc.elementos.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Text(
                                stringResource(R.string.telar_vazio),
                                fontSize = 12.sp,
                                color = KenkernTextMid,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            KButton(
                                texto = stringResource(R.string.telar_criar_tela),
                                onClick = {
                                    doc = com.kenkern.engine.ui.telar.TemplateUiMoedas.montar()
                                    zoom = 1f; panX = 0f; panY = 0f
                                    revisao++
                                    KenkernLog.w("Telar", "tela de UI criada do template")
                                },
                                primario = true,
                                modifier = Modifier.height(44.dp)
                            )
                        }
                    }

                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .graphicsLayer {
                                scaleX = esc
                                scaleY = esc
                                translationX = panX
                                translationY = panY
                            }
                    ) {
                        Box(
                            Modifier
                                .width(larguraPrev.dp) // px de referência; escala no graphicsLayer
                                .height(alturaPrev.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF12151C))
                                .border(1.dp, KenkernSingCobre)
                                .pointerInput("pan") {
                                    detectTransformGestures { _, pan, zoomDelta, _ ->
                                        zoom = (zoom * zoomDelta).coerceIn(0.6f, 4f)
                                        panX += pan.x
                                        panY += pan.y
                                    }
                                }
                                .pointerInput("tap") {
                                    detectTapGestures { offset ->
                                        // selecção por toque (px da tela → px de referência)
                                        val e2 = (escalaAjuste * zoom).coerceAtLeast(0.01f)
                                        val docX = ((offset.x - panX - size.width / 2f + (larguraPrev * e2) / 2f) / e2).toInt()
                                        val docY = ((offset.y - panY - size.height / 2f + (alturaPrev * e2) / 2f) / e2).toInt()
                                        val achou = doc.elementos.lastOrNull { e ->
                                            if (e.oculto) return@lastOrNull false
                                            val r = com.kenkern.engine.ui.telar.resolverRect(e, larguraPrev, alturaPrev)
                                            docX >= r.x && docX <= r.x + r.largura && docY >= r.y && docY <= r.y + r.altura
                                        }
                                        doc.selecaoId = achou?.id ?: -1
                                        revisao++
                                    }
                                }
                        ) {
                            // régua superior (marcas a cada 240px de referência)
                            Box(Modifier.fillMaxSize()) {
                                for (e in doc.elementos) {
                                    val r = com.kenkern.engine.ui.telar.resolverRect(e, larguraPrev, alturaPrev)
                                    ElementoCanvas(e, r, doc.selecaoId == e.id) { dxTela, dyTela ->
                                        // U1.1: arrasto → px de REFERÊNCIA com SNAP de 8px
                                        val e3 = (escalaAjuste * zoom).coerceAtLeast(0.01f)
                                        val dxRef = (dxTela / e3).toInt()
                                        val dyRef = (dyTela / e3).toInt()
                                        val novoX = ((e.x + dxRef) / 8) * 8
                                        val novoY = ((e.y + dyRef) / 8) * 8
                                        doc.executar(ComandoTransformar(e.id, novoX, novoY, e.largura, e.altura))
                                        revisao++
                                    }
                                }
                                // contorno da tela de referência
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .border(2.dp, KenkernSingCobre.copy(alpha = 0.6f))
                                )
                            }
                        }
                    }
                }
            }

            // ---------- Inspetor de UI (elemento selecionado) ----------
            Column(
                Modifier
                    .width(230.dp)
                    .fillMaxHeight()
                    .background(KenkernSurface)
                    .verticalScroll(rememberScrollState())
                    .padding(6.dp)
            ) {
                val sel = doc.selecionado()
                if (sel == null) {
                    Text(
                        stringResource(R.string.telar_selecione),
                        fontSize = 11.sp,
                        color = KenkernTextMid
                    )
                } else {
                    Text(sel.tipo.key, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = KenkernAmber)
                    CampoUiTexto(stringResource(R.string.telar_nome), sel.nome) {
                        doc.executar(ComandoPropriedade(sel.id, "nome", sel.nome, it))
                        revisao++
                    }
                    CampoUiTexto("aoClicar (Lua)", sel.aoClicar) {
                        doc.executar(ComandoPropriedade(sel.id, "aoClicar", sel.aoClicar, it))
                        revisao++
                    }
                    if (sel.tipo == ElementoUi.TEXTO || sel.tipo == ElementoUi.BOTAO || sel.tipo == ElementoUi.BARRA) {
                        CampoUiTexto("ligaTexto", sel.ligaTexto) {
                            doc.executar(ComandoPropriedade(sel.id, "ligaTexto", sel.ligaTexto, it))
                            revisao++
                        }
                    }
                    if (sel.tipo == ElementoUi.TEXTO) {
                        CampoUiTexto(stringResource(R.string.telar_texto), sel.texto.conteudo) {
                            doc.executar(ComandoPropriedade(sel.id, "texto", sel.texto.conteudo, it))
                            revisao++
                        }
                        Box {
                            KChip(
                                texto = "fonte: " + sel.texto.fonte,
                                onClick = { menuFonte = true },
                                selecionado = true
                            )
                            DropdownMenu(expanded = menuFonte, onDismissRequest = { menuFonte = false }) {
                                for (f in FONTES_OFL) {
                                    DropdownMenuItem(
                                        text = { Text(f, fontSize = 12.sp) },
                                        onClick = {
                                            menuFonte = false
                                            doc.executar(ComandoPropriedade(sel.id, "fonte", sel.texto.fonte, f))
                                            revisao++
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.editor_inspector_oculto), fontSize = 11.sp, color = KenkernTextMid, modifier = Modifier.weight(1f))
                        Switch(
                            checked = sel.oculto,
                            onCheckedChange = { novo ->
                                doc.executar(ComandoPropriedade(sel.id, "oculto", sel.oculto, novo))
                                revisao++
                            }
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.editor_inspector_travado), fontSize = 11.sp, color = KenkernTextMid, modifier = Modifier.weight(1f))
                        Switch(
                            checked = sel.travado,
                            onCheckedChange = { novo ->
                                doc.executar(ComandoPropriedade(sel.id, "travado", sel.travado, novo))
                                revisao++
                            }
                        )
                    }
                    // âncoras (9 + esticar)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        for (a in AncoraUi.entries) {
                            KChip(
                                texto = a.key.substringBefore('_'),
                                onClick = {
                                    doc.executar(ComandoPropriedade(sel.id, "ancora", sel.ancora.key, a.key))
                                    revisao++
                                },
                                selecionado = sel.ancora == a
                            )
                        }
                    }
                    // remover
                    KButton(
                        texto = stringResource(R.string.telar_remover),
                        onClick = {
                            doc.executar(ComandoRemover(sel.id))
                            revisao++
                        },
                        modifier = Modifier.height(44.dp)
                    )
                }
            }
        }
    }
    } // R4: fecha o KScaffold (insets de status/navegação)
}

/** Um elemento DESENHADO no canvas (preview Compose com o estilo do modelo). */
@Composable
private fun ElementoCanvas(
    e: ElementoUiInstancia,
    r: com.kenkern.engine.ui.telar.RectUi,
    selecionado: Boolean,
    onArrastoFim: (Int, Int) -> Unit
) {
    val acc = remember { androidx.compose.runtime.mutableStateOf(Offset.Zero) }
    val corBase = corDe(e.estilo.cor)
    Box(
        Modifier
            .graphicsLayer {
                translationX = r.x * 1f
                translationY = r.y * 1f
                alpha = e.estilo.opacidade * if (e.oculto) 0f else 1f
            }
            .width(r.largura.dp)
            .height(r.altura.dp)
            .clip(RoundedCornerShape((e.estilo.cantos / 4).coerceAtMost(24)))
            .background(corBase)
            .then(
                if (e.estilo.contornoCor.isNotEmpty()) Modifier.border(
                    1.dp, corDe(e.estilo.contornoCor), RoundedCornerShape((e.estilo.cantos / 4).coerceAtMost(24))
                ) else Modifier
            )
            .then(
                if (selecionado) Modifier.border(2.dp, KenkernAmber, RoundedCornerShape((e.estilo.cantos / 4).coerceAtMost(24))) else Modifier
            )
            .pointerInput(e.id, e.travado) {
                if (!e.travado) {
                    detectDragGestures(
                        onDrag = { change, drag ->
                            change.consume()
                            acc.value += drag
                        },
                        onDragEnd = {
                            onArrastoFim(acc.value.x.toInt(), acc.value.y.toInt())
                            acc.value = Offset.Zero
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (e.tipo == ElementoUi.TEXTO || e.tipo == ElementoUi.BOTAO) {
            Text(
                e.texto.conteudo,
                fontSize = (e.texto.tamanho / 3f).sp,
                fontFamily = familiaDe(e.texto.fonte),
                fontWeight = if (e.texto.peso == "bold") FontWeight.Bold else FontWeight.Normal,
                color = corDe(e.texto.cor)
            )
        } else if (e.tipo == ElementoUi.JOYSTICK) {
            Box(
                Modifier
                    .size(120.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(Color(0x33D98040))
                    .border(2.dp, KenkernAmber, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(KenkernAmber)
                )
            }
        } else if (e.tipo == ElementoUi.BARRA) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(18.dp)
                    .background(Color(0x55000000), RoundedCornerShape(9))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.6f)
                        .height(18.dp)
                        .background(KenkernAmber, RoundedCornerShape(9))
                )
            }
        }
    }
}

private fun corDe(hex: String): Color {
    val limpo = hex.removePrefix("#")
    return try {
        val valor = limpo.toLong(16)
        when (limpo.length) {
            6 -> Color(0xFF000000 or valor)
            8 -> Color(valor)
            else -> Color(0xFF333333)
        }
    } catch (_: Exception) {
        Color(0xFF333333)
    }
}

/**
 * Onda 0 (v0.1.28, P5): família da fonte do elemento — AGORA REAL: carrega o
 * .ttf OFL EMBUTIDO no APK (assets/fonts, via FontesKenkern; sem download —
 * modo avião). ANTES os nomes eram rótulos devolvendo os 3 FontFamily
 * genéricos. Asset ausente (não deveria) → genérico + Console acusa.
 */
@Composable
private fun familiaDe(nome: String): FontFamily {
    val ctx = LocalContext.current
    val tf = remember(nome) { FontesKenkern.carregar(ctx, nome) }
    return if (tf != null) FontFamily(tf) else when (nome) {
        "RobotoMono", "JetBrainsMono" -> FontFamily.Monospace
        "NotoSerif" -> FontFamily.Serif
        else -> FontFamily.Default
    }
}


/** Campo de texto do Inspetor do TELAR (rótulo + valor; aplica por Command). */
@Composable
private fun CampoUiTexto(rotulo: String, valor: String, onMuda: (String) -> Unit) {
    var texto by remember(valor) { mutableStateOf(valor) }
    androidx.compose.material3.OutlinedTextField(
        value = texto,
        onValueChange = { texto = it },
        label = { Text(rotulo, fontSize = 10.sp) },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = KenkernTextHigh),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    )
    Text(
        if (texto != valor) "↵ aplica" else "",
        fontSize = 9.sp,
        color = KenkernTextMid
    )
    androidx.compose.material3.TextButton(onClick = { if (texto != valor) onMuda(texto) }) {
        Text(stringResource(R.string.hub_ok), fontSize = 11.sp)
    }
}
