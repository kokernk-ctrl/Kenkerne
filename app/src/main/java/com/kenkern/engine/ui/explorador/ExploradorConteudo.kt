package com.kenkern.engine.ui.explorador

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kenkern.engine.R
import com.kenkern.engine.commands.ArquivoCommand
import com.kenkern.engine.commands.ComandoApagarArquivo
import com.kenkern.engine.commands.ComandoCriarArquivo
import com.kenkern.engine.commands.ComandoCriarPasta
import com.kenkern.engine.commands.ComandoDuplicarArquivo
import com.kenkern.engine.commands.ComandoEsvaziarLixeira
import com.kenkern.engine.commands.ComandoMoverArquivo
import com.kenkern.engine.commands.ComandoRenomearArquivo
import com.kenkern.engine.commands.novoIdOperacao
import com.kenkern.engine.editor.EditorViewModel
import com.kenkern.engine.projects.OperacoesArquivos
import com.kenkern.engine.projects.ReferenciasAssets
import com.kenkern.engine.ui.components.KButton
import com.kenkern.engine.ui.components.KIconButton
import com.kenkern.engine.ui.theme.KenkernAmber
import com.kenkern.engine.ui.theme.KenkernBackground
import com.kenkern.engine.ui.theme.KenkernError
import com.kenkern.engine.ui.theme.KenkernSelection
import com.kenkern.engine.ui.theme.KenkernSingCobreClaro
import com.kenkern.engine.ui.theme.KenkernSurface
import com.kenkern.engine.ui.theme.KenkernSurfaceHigh
import com.kenkern.engine.ui.theme.KenkernTextHigh
import com.kenkern.engine.ui.theme.KenkernTextLow
import com.kenkern.engine.ui.theme.KenkernTextMid
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * F3 (v0.1.31) — CONTEÚDO do painel Projeto/explorador (usado PELO painel
 * acoplado e pela ABA "Arquivos"): breadcrumb, busca, ordenação, lista
 * VIRTUALIZADA e paginada (5.000 arquivos não trava), ícone por tipo,
 * miniatura assíncrona de imagem (cache), ações com LIXEIRA e desfazer
 * (Command no histórico do editor), seleção múltipla por toque longo, abrir
 * por tipo (.lua/.kui/.ck/.glb/…/imagem) e a ABA "Problemas" (F5: referências
 * quebradas com Corrigir…/Remover referência). Tudo pelas guardas de caminho
 * do F1 (OperacoesArquivos).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExploradorConteudo(
    vm: EditorViewModel,
    compacto: Boolean,
    onAbrirLua: (String) -> Unit,
    onAbrirKui: (String) -> Unit,
    onCarregarCena: (String) -> Unit,
    onImportarModelo: (String) -> Unit,
    onAbrirImagem: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var pastaAtual by remember { mutableStateOf("") } // relativo à raiz ("" = raiz)
    var busca by remember { mutableStateOf("") }
    var ordem by remember { mutableStateOf(ExploradorModelo.Ordem.NOME) }
    var revisao by remember { mutableIntStateOf(0) }
    var paginasCarregadas by remember(pastaAtual, revisao, busca) { mutableIntStateOf(1) }
    var selecao by remember { mutableStateOf<Set<String>>(emptySet()) }
    var aba by remember { mutableIntStateOf(0) } // 0 = Arquivos, 1 = Problemas
    var erro by remember { mutableStateOf<String?>(null) }
    var menuOrdenar by remember { mutableStateOf(false) }
    var menuNovoArquivo by remember { mutableStateOf(false) }
    var dialogoNome by remember { mutableStateOf<DialogoNome?>(null) }
    var dialogoMover by remember { mutableStateOf<Boolean>(false) }
    var confirmaApagar by remember { mutableStateOf(false) }
    var confirmaLixeira by remember { mutableStateOf(false) }
    var navegadorAberto by remember { mutableStateOf(false) }
    var mensagemImport by remember { mutableStateOf<String?>(null) }

    val raiz = vm.project.value?.root
    if (raiz == null) {
        Box(modifier.fillMaxSize().background(KenkernSurface))
        return
    }

    fun executar(cmd: ArquivoCommand) {
        try {
            vm.run(cmd)
            revisao++
            selecao = emptySet()
            erro = null
        } catch (t: Throwable) {
            erro = t.message ?: t.javaClass.simpleName
            com.kenkern.engine.diag.KenkernLog.e("Explorador", "operação de arquivo falhou", t)
        }
    }

    // ---------- abas internas Arquivos | Problemas ----------
    Column(modifier.fillMaxSize().background(KenkernSurface)) {
        Row(modifier = Modifier.fillMaxWidth().height(30.dp).background(KenkernSurfaceHigh)) {
            Text(
                stringResource(R.string.explorador_aba_arquivos),
                fontSize = 11.sp,
                color = if (aba == 0) KenkernAmber else KenkernTextMid,
                fontWeight = if (aba == 0) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.clickable { aba = 0 }.padding(horizontal = 10.dp, vertical = 8.dp)
            )
            Text(
                stringResource(R.string.explorador_aba_problemas),
                fontSize = 11.sp,
                color = if (aba == 1) KenkernAmber else KenkernTextMid,
                fontWeight = if (aba == 1) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.clickable { aba = 1 }.padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }

        if (aba == 1) {
            PainelProblemas(
                vm = vm,
                revisao = revisao,
                aoAtualizar = { revisao++ },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            return@Column
        }

        // ---------- breadcrumb (F3.1) ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BreadcrumbChip(stringResource(R.string.explorador_projeto_raiz), pastaAtual.isEmpty()) {
                pastaAtual = ""
                selecao = emptySet()
                paginasCarregadas = 1
            }
            var acumulado = ""
            for (parte in pastaAtual.split('/').filter { it.isNotEmpty() }) {
                acumulado = if (acumulado.isEmpty()) parte else "$acumulado/$parte"
                val destino = acumulado
                Text(stringResource(R.string.explorador_separador_caminho), fontSize = 11.sp, color = KenkernTextLow)
                BreadcrumbChip(parte, pastaAtual == destino) {
                    pastaAtual = destino
                    selecao = emptySet()
                    paginasCarregadas = 1
                }
            }
        }

        // ---------- busca + ordenação + ações (F3.1/F3.2) ----------
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedTextField(
                value = busca,
                onValueChange = {
                    busca = it
                    paginasCarregadas = 1
                },
                placeholder = {
                    Text(stringResource(R.string.explorador_busca), fontSize = 11.sp, color = KenkernTextLow)
                },
                singleLine = true,
                textStyle = TextStyle(fontSize = 12.sp, color = KenkernTextHigh, fontFamily = FontFamily.Monospace),
                modifier = Modifier.weight(1f).height(52.dp)
            )
            Box {
                KIconButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.explorador_ordenar),
                    onClick = { menuOrdenar = true }
                )
                DropdownMenu(expanded = menuOrdenar, onDismissRequest = { menuOrdenar = false }) {
                    for (o in ExploradorModelo.Ordem.entries) {
                        DropdownMenuItem(
                            text = { Text(rotuloOrdena(o), fontSize = 12.sp) },
                            onClick = {
                                menuOrdenar = false
                                ordem = o
                                revisao++
                            }
                        )
                    }
                }
            }
            KIconButton(
                icon = Icons.Filled.CreateNewFolder,
                contentDescription = stringResource(R.string.explorador_nova_pasta),
                onClick = { dialogoNome = DialogoNome(DialogoNome.Tipo.PASTA, "") }
            )
            Box {
                KIconButton(
                    icon = Icons.Filled.NoteAdd,
                    contentDescription = stringResource(R.string.explorador_novo_arquivo),
                    onClick = { menuNovoArquivo = true }
                )
                DropdownMenu(expanded = menuNovoArquivo, onDismissRequest = { menuNovoArquivo = false }) {
                    for (ext in ExploradorModelo.TIPOS_NOVO_ARQUIVO) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.explorador_novo_ext, ext), fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
                            onClick = {
                                menuNovoArquivo = false
                                dialogoNome = DialogoNome(DialogoNome.Tipo.ARQUIVO, "novo.$ext")
                            }
                        )
                    }
                }
            }
            KIconButton(
                icon = Icons.Filled.SdCard,
                contentDescription = stringResource(R.string.explorador_navegador),
                onClick = { navegadorAberto = true }
            )
            KIconButton(
                icon = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.explorador_esvaziar_lixeira),
                onClick = { confirmaLixeira = true }
            )
        }

        // ---------- barra da seleção múltipla (F3.2, toque longo) ----------
        if (selecao.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().background(KenkernSurfaceHigh).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    "${selecao.size}",
                    fontSize = 11.sp,
                    color = KenkernAmber,
                    fontFamily = FontFamily.Monospace
                )
                KIconButton(
                    icon = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.explorador_renomear),
                    enabled = selecao.size == 1,
                    onClick = {
                        val rel = selecao.firstOrNull() ?: return@KIconButton
                        dialogoNome = DialogoNome(DialogoNome.Tipo.RENOMEAR, rel.substringAfterLast('/'), alvo = rel)
                    }
                )
                KIconButton(
                    icon = Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.explorador_duplicar),
                    onClick = { for (rel in selecao) executar(ComandoDuplicarArquivo(raiz, rel)) }
                )
                KIconButton(
                    icon = Icons.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.explorador_mover),
                    onClick = { dialogoMover = true }
                )
                KIconButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.explorador_apagar),
                    onClick = { confirmaApagar = true }
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.explorador_nome),
                    fontSize = 10.sp,
                    color = KenkernTextLow,
                    modifier = Modifier.clickable { selecao = emptySet() }
                )
            }
        }

        if (erro != null) {
            Text(
                stringResource(R.string.explorador_erro) + ": " + erro,
                fontSize = 10.sp,
                color = KenkernError,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KenkernError.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        // ---------- listagem da pasta atual (cache do núcleo + filtro + ordem) ----------
        val dirAtual = File(raiz, pastaAtual.ifEmpty { "." })
        val itens = remember(pastaAtual, revisao, busca, ordem) {
            val bruto = com.kenkern.engine.projects.ExploradorArquivos.pagina(dirAtual, 0, Int.MAX_VALUE)
            ExploradorModelo.ordenar(ExploradorModelo.filtrar(bruto, busca), ordem)
        }
        // PAGINAÇÃO incremental (P3): o LazyColumn virtualiza; as PÁGINAS
        // limitam o custo por rolagem (+ mostra quantos faltam).
        val visiveis = remember(itens, paginasCarregadas) {
            itens.take(paginasCarregadas * com.kenkern.engine.projects.ExploradorArquivos.PAGINA_PADRAO)
        }

        if (itens.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.explorador_pasta_vazia), fontSize = 12.sp, color = KenkernTextMid)
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(visiveis, key = { OperacoesArquivos.relativo(raiz, it) }) { f ->
                    val rel = OperacoesArquivos.relativo(raiz, f)
                    ItemArquivo(
                        arquivo = f,
                        rel = rel,
                        selecionado = rel in selecao,
                        compacto = compacto,
                        revisao = revisao,
                        aoAbrir = {
                            when (ExploradorModelo.abrirDe(f.name)) {
                                ExploradorModelo.Abrir.LUA -> onAbrirLua(f.name)
                                ExploradorModelo.Abrir.KUI -> onAbrirKui(f.name)
                                ExploradorModelo.Abrir.CENA -> onCarregarCena(rel)
                                ExploradorModelo.Abrir.MODELO -> onImportarModelo(rel)
                                ExploradorModelo.Abrir.IMAGEM -> onAbrirImagem(rel)
                                ExploradorModelo.Abrir.OUTRO -> Unit
                            }
                        },
                        aoSelecionar = {
                            selecao = if (rel in selecao) selecao - rel else selecao + rel
                        },
                        aoAbrirPasta = {
                            pastaAtual = rel
                            selecao = emptySet()
                            paginasCarregadas = 1
                        }
                    )
                }
                item {
                    if (visiveis.size < itens.size) {
                        Row(
                            Modifier.fillMaxWidth().clickable { paginasCarregadas++ }.padding(10.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "+ ${itens.size - visiveis.size}",
                                fontSize = 11.sp,
                                color = KenkernAmber,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    // ---------- diálogos ----------
    dialogoNome?.let { d ->
        var texto by remember(d) { mutableStateOf(d.inicial) }
        AlertDialog(
            onDismissRequest = { dialogoNome = null },
            title = { Text(tituloDialogo(d.tipo), fontSize = 16.sp, color = KenkernTextHigh) },
            text = {
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = KenkernTextHigh)
                )
            },
            confirmButton = {
                KButton(
                    texto = stringResource(R.string.explorador_nome),
                    onClick = {
                        dialogoNome = null
                        val nome = texto.trim()
                        if (nome.isNotEmpty()) {
                            when (d.tipo) {
                                DialogoNome.Tipo.PASTA ->
                                    executar(ComandoCriarPasta(raiz, pastaAtual, nome))
                                DialogoNome.Tipo.ARQUIVO ->
                                    executar(
                                        ComandoCriarArquivo(raiz, pastaAtual, nome, ExploradorModelo.conteudoNovoArquivo(nome))
                                    )
                                DialogoNome.Tipo.RENOMEAR -> executar(
                                    ComandoRenomearArquivo(raiz, d.alvo ?: "", nome) { antigo, novo ->
                                        vm.atualizarReferencias(antigo, novo)
                                    }
                                )
                            }
                        }
                    }
                )
            },
            dismissButton = {
                KButton(texto = stringResource(R.string.explorador_cancelar), onClick = { dialogoNome = null })
            }
        )
    }

    if (confirmaApagar) {
        AlertDialog(
            onDismissRequest = { confirmaApagar = false },
            title = { Text(stringResource(R.string.explorador_confirma_apagar), fontSize = 16.sp, color = KenkernTextHigh) },
            confirmButton = {
                KButton(
                    texto = stringResource(R.string.explorador_apagar),
                    onClick = {
                        confirmaApagar = false
                        for (rel in selecao) {
                            executar(ComandoApagarArquivo(raiz, rel, novoIdOperacao()))
                        }
                    }
                )
            },
            dismissButton = {
                KButton(texto = stringResource(R.string.explorador_cancelar), onClick = { confirmaApagar = false })
            }
        )
    }

    if (confirmaLixeira) {
        AlertDialog(
            onDismissRequest = { confirmaLixeira = false },
            title = { Text(stringResource(R.string.explorador_confirma_lixeira), fontSize = 16.sp, color = KenkernTextHigh) },
            confirmButton = {
                KButton(
                    texto = stringResource(R.string.explorador_esvaziar_lixeira),
                    onClick = {
                        confirmaLixeira = false
                        executar(ComandoEsvaziarLixeira(raiz))
                    }
                )
            },
            dismissButton = {
                KButton(texto = stringResource(R.string.explorador_cancelar), onClick = { confirmaLixeira = false })
            }
        )
    }

    if (dialogoMover) {
        AlertDialog(
            onDismissRequest = { dialogoMover = false },
            title = { Text(stringResource(R.string.explorador_mover_para), fontSize = 16.sp, color = KenkernTextHigh) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    for (pasta in pastasDestino(raiz)) {
                        val rotulo = pasta.ifEmpty { stringResource(R.string.explorador_projeto_raiz) }
                        Text(
                            rotulo,
                            fontSize = 12.sp,
                            color = if (pasta.isEmpty()) KenkernAmber else KenkernTextHigh,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    dialogoMover = false
                                    for (rel in selecao) {
                                        executar(
                                            ComandoMoverArquivo(raiz, rel, pasta) { antigo, novo ->
                                                vm.atualizarReferencias(antigo, novo)
                                            }
                                        )
                                    }
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                KButton(texto = stringResource(R.string.explorador_cancelar), onClick = { dialogoMover = false })
            }
        )
    }

    // F4.1: navegador do APARELHO (com permissão, qualquer pasta, multi-select)
    if (navegadorAberto) {
        DialogoNavegadorAparelho(
            vm = vm,
            onFechar = { navegadorAberto = false; revisao++ },
            aoImportar = { resumo ->
                mensagemImport = resumo
                revisao++
            }
        )
    }

    mensagemImport?.let { msg ->
        AlertDialog(
            onDismissRequest = { mensagemImport = null },
            title = { Text(stringResource(R.string.explorador_navegador), fontSize = 15.sp, color = KenkernTextHigh) },
            text = { Text(msg, fontSize = 12.sp, color = KenkernTextHigh, fontFamily = FontFamily.Monospace) },
            confirmButton = { KButton(texto = stringResource(R.string.explorador_cancelar), onClick = { mensagemImport = null }) },
            dismissButton = {}
        )
    }
}

/** dados do diálogo de nome (criar pasta/arquivo/renomear). */
private data class DialogoNome(val tipo: Tipo, val inicial: String, val alvo: String? = null) {
    enum class Tipo { PASTA, ARQUIVO, RENOMEAR }
}

@Composable
private fun tituloDialogo(tipo: DialogoNome.Tipo): String = when (tipo) {
    DialogoNome.Tipo.PASTA -> stringResource(R.string.explorador_nova_pasta)
    DialogoNome.Tipo.ARQUIVO -> stringResource(R.string.explorador_novo_arquivo)
    DialogoNome.Tipo.RENOMEAR -> stringResource(R.string.explorador_renomear)
}

@Composable
private fun rotuloOrdena(o: ExploradorModelo.Ordem): String = when (o) {
    ExploradorModelo.Ordem.NOME -> stringResource(R.string.explorador_ordem_nome)
    ExploradorModelo.Ordem.DATA -> stringResource(R.string.explorador_ordem_data)
    ExploradorModelo.Ordem.TAMANHO -> stringResource(R.string.explorador_ordem_tamanho)
    ExploradorModelo.Ordem.TIPO -> stringResource(R.string.explorador_ordem_tipo)
}

/** pastas candidatas para "Mover para…" (árvore F2 + subpastas, sem .kenkern/build). */
private fun pastasDestino(raiz: File): List<String> {
    val saida = mutableListOf("")
    raiz.walkTopDown().maxDepth(3)
        .filter { it.isDirectory }
        .filter { !it.path.contains("/.kenkern") && !it.path.contains("/build/") }
        .forEach { saida.add(OperacoesArquivos.relativo(raiz, it)) }
    return saida.distinct().sorted()
}

@Composable
private fun BreadcrumbChip(texto: String, ativo: Boolean, onClick: () -> Unit) {
    Text(
        texto,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        color = if (ativo) KenkernAmber else KenkernTextMid,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 4.dp)
    )
}

/** cache de MINIATURAS de imagem (F3.1: assíncrono, tamanho limitado, LRU de 60). */
private object Miniaturas {
    private val cache = object : LinkedHashMap<String, Bitmap>(0, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>): Boolean = size > 60
    }
    private val emAndamento = AtomicReference<String?>(null)

    fun pega(rel: String): Bitmap? = synchronized(cache) { cache[rel] }

    /** decodifica com amostragem (miniatura ≤ 96 px de largura). */
    fun carrega(rel: String, arquivo: File): Bitmap? {
        synchronized(cache) { cache[rel] }?.let { return it }
        if (emAndamento.compareAndSet(null, rel) && emAndamento.get() != rel) return null
        return try {
            val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(arquivo.absolutePath, limites)
            var amostra = 1
            while (limites.outWidth / amostra > 96) amostra *= 2
            val opcoes = BitmapFactory.Options().apply { inSampleSize = amostra }
            val bmp = BitmapFactory.decodeFile(arquivo.absolutePath, opcoes)
            if (bmp != null) {
                synchronized(cache) { cache[rel] = bmp }
            }
            bmp
        } finally {
            emAndamento.set(null)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemArquivo(
    arquivo: File,
    rel: String,
    selecionado: Boolean,
    compacto: Boolean,
    revisao: Int,
    aoAbrir: () -> Unit,
    aoSelecionar: () -> Unit,
    aoAbrirPasta: () -> Unit
) {
    val tipo = OperacoesArquivos.tipoDe(arquivo.name)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compacto) 36.dp else 40.dp)
            .background(if (selecionado) KenkernSelection else Color.Transparent)
            .combinedClickable(
                onClick = { if (arquivo.isDirectory) aoAbrirPasta() else aoAbrir() },
                onLongClick = aoSelecionar
            )
            .padding(horizontal = 6.dp)
    ) {
        if (!arquivo.isDirectory && ExploradorModelo.temMiniatura(arquivo.name)) {
            var bmpState by remember(rel, revisao, arquivo.length()) { mutableStateOf<Bitmap?>(Miniaturas.pega(rel)) }
            LaunchedEffect(rel, revisao) {
                if (bmpState == null) bmpState = Miniaturas.carrega(rel, arquivo)
            }
            val bitmap = bmpState
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = arquivo.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(28.dp).background(KenkernBackground)
                )
            } else {
                IconeTipo(tipo, pasta = false)
            }
        } else {
            IconeTipo(tipo, pasta = arquivo.isDirectory)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                arquivo.name,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = if (arquivo.isDirectory) KenkernAmber else KenkernTextHigh,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!compacto) {
                Text(
                    tipo,
                    fontSize = 9.sp,
                    color = KenkernTextLow,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (!arquivo.isDirectory) {
            Text(
                formatarTamanho(arquivo.length()),
                fontSize = 9.sp,
                color = KenkernTextLow,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun IconeTipo(tipo: String, pasta: Boolean) {
    val par: Pair<ImageVector, Color> = when {
        pasta -> Icons.Filled.Folder to KenkernAmber
        tipo == "script" -> Icons.Filled.Description to KenkernTextMid
        tipo == "ui" -> Icons.Filled.TouchApp to KenkernTextMid
        tipo == "cena" -> Icons.Filled.Sensors to KenkernTextMid
        tipo == "modelo" || tipo == "malha" -> Icons.Filled.ViewInAr to KenkernTextMid
        tipo == "imagem" || tipo == "imagem-projeto" -> Icons.Filled.Image to KenkernTextMid
        tipo == "audio" -> Icons.Filled.MusicNote to KenkernTextMid
        tipo == "video" -> Icons.Filled.Videocam to KenkernTextMid
        tipo == "fonte" -> Icons.Filled.TextFields to KenkernTextMid
        else -> Icons.Filled.InsertDriveFile to KenkernTextLow
    }
    Icon(par.first, contentDescription = tipo, tint = par.second, modifier = Modifier.size(22.dp))
}

private fun formatarTamanho(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000f)
    bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000f)
    else -> "$bytes B"
}

/**
 * F4.1 (v0.1.31) — "Navegador do aparelho": navega QUALQUER pasta com a
 * permissão de "Acesso a todos os arquivos" (F1), SELEÇÃO MÚLTIPLA por toque
 * e "Importar pasta inteira" (recursiva, com contagem no resumo). Sem
 * permissão, mostra ONDE conceder — nada silencioso.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialogoNavegadorAparelho(
    vm: EditorViewModel,
    onFechar: () -> Unit,
    aoImportar: (resumo: String) -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val concedida = com.kenkern.engine.projects.PermissaoAparelho.concedida(ctx)
    var atual by remember { mutableStateOf(java.io.File("/storage/emulated/0")) }
    var selecao by remember { mutableStateOf<Set<File>>(emptySet()) }

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text(stringResource(R.string.explorador_navegador), fontSize = 15.sp, color = KenkernTextHigh) },
        text = {
            Column(Modifier.height(280.dp).verticalScroll(rememberScrollState())) {
                if (!concedida) {
                    Text(
                        stringResource(R.string.explorador_sem_permissao),
                        fontSize = 11.sp,
                        color = KenkernError
                    )
                } else {
                    Text(
                        atual.path,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = KenkernTextLow,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val conteudo = remember(atual.path) {
                        atual.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
                    }
                    if (atual.parentFile != null) {
                        Text(
                            "…",
                            fontSize = 12.sp,
                            color = KenkernAmber,
                            modifier = Modifier.fillMaxWidth().clickable { atual = atual.parentFile!! }.padding(vertical = 4.dp)
                        )
                    }
                    for (f in conteudo) {
                        val sel = f in selecao
                        Text(
                            (if (f.isDirectory) "▸ " else "") + f.name,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = when {
                                f.isDirectory -> KenkernAmber
                                sel -> KenkernSingCobreClaro
                                else -> KenkernTextHigh
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { if (f.isDirectory) atual = f else selecao = if (sel) selecao - f else selecao + f },
                                    onLongClick = { selecao = if (sel) selecao - f else selecao + f }
                                )
                                .padding(vertical = 3.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (concedida) {
                    KButton(
                        texto = stringResource(R.string.explorador_importar_selecionados),
                        enabled = selecao.isNotEmpty(),
                        onClick = {
                            vm.importarArquivosLocais(selecao.toList(), onOk = {
                                aoImportar(it); onFechar()
                            }, onErro = { aoImportar(it) })
                        },
                        modifier = Modifier.height(40.dp)
                    )
                    KButton(
                        texto = stringResource(R.string.explorador_importar_pasta_inteira),
                        onClick = {
                            vm.importarPastaLocal(atual, onOk = {
                                aoImportar(it); onFechar()
                            }, onErro = { aoImportar(it) })
                        },
                        modifier = Modifier.height(40.dp)
                    )
                }
                KButton(
                    texto = stringResource(R.string.explorador_cancelar),
                    onClick = onFechar,
                    modifier = Modifier.height(40.dp)
                )
            }
        },
        dismissButton = {}
    )
}
