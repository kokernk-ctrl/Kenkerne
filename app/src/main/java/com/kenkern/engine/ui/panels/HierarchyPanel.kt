package com.kenkern.engine.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kenkern.engine.R
import com.kenkern.engine.core.Nick
import com.kenkern.engine.editor.EditorViewModel
import com.kenkern.engine.ui.nickDisplayName
import com.kenkern.engine.ui.nickKindIcon
import com.kenkern.engine.ui.renameHint
import com.kenkern.engine.ui.theme.KenkernAmber
import com.kenkern.engine.ui.theme.KenkernOutline
import com.kenkern.engine.ui.theme.KenkernSelection
import com.kenkern.engine.ui.theme.KenkernTextHigh
import com.kenkern.engine.ui.theme.KenkernTextLow
import com.kenkern.engine.ui.theme.KenkernTextMid

private class RowEntry(val nick: Nick, val depth: Int, val hasChildren: Boolean, val expanded: Boolean)

/** Ícone por TIPO do Nick (T0.2): o kind manda; sem tipo registrado, o velho
 *  critério (pasta com filhos / cubo com classe / vazio) decide. */
private fun nickIcon(nick: Nick): ImageVector = when {
    com.kenkern.engine.core.NickKindRegistry.find(nick.kind) != null -> nickKindIcon(nick.kind)
    nick.children.isNotEmpty() -> Icons.Filled.Folder
    nick.classes.isNotEmpty() || nick.name?.contains("Cubo", ignoreCase = true) == true -> Icons.Filled.ViewInAr
    else -> Icons.Filled.Adjust
}

/**
 * Painel HIERARQUIA: árvore de Nicks com criar, renomear, apagar,
 * reordenar (subir/descer) e reparentar.
 *
 * Visual (paleta neutra): linhas de 28dp, ícone por tipo, guias verticais de
 * indentação (1px #3C3C3C), seta de expandir; item selecionado com fundo
 * #37373D + barra âmbar de 2dp à esquerda; olho de visibilidade em cinza
 * (#9D9D9D), destacado só quando oculto; vermelho apenas na confirmação.
 */
@Composable
fun HierarchyPanel(vm: EditorViewModel) {
    val revision = vm.revision.intValue
    val scene = vm.scene.value
    val selection = vm.selection.value

    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showReparent by remember { mutableStateOf(false) }

    val expandedMap = remember { HashMap<String, Boolean>() }

    Column(Modifier.fillMaxSize()) {
        // Barra de ferramentas (rolável: cabe em painéis estreitos de 140dp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp)
        ) {
            IconButton(onClick = { vm.addNick() }, enabled = scene != null, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Add, stringResource(R.string.hierarchy_create), tint = KenkernTextHigh, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { showRename = true }, enabled = selection != null, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Edit, stringResource(R.string.hierarchy_rename), tint = KenkernTextHigh, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { showDelete = true }, enabled = selection != null, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Delete, stringResource(R.string.hierarchy_delete), tint = KenkernTextHigh, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { selection?.let { vm.reorderSelected(-1) } }, enabled = selection != null, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.hierarchy_up), tint = KenkernTextHigh, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { selection?.let { vm.reorderSelected(1) } }, enabled = selection != null, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.hierarchy_down), tint = KenkernTextHigh, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = { showReparent = true }, enabled = selection != null && scene != null, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.AccountTree, stringResource(R.string.hierarchy_reparent), tint = KenkernTextHigh, modifier = Modifier.size(20.dp))
            }
        }

        HorizontalDividerSoft()

        if (scene == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.hierarchy_no_scene), style = MaterialTheme.typography.labelMedium)
            }
        } else {
            // Flatten da árvore (feito por recomposição; UI thread apenas)
            val rows = ArrayList<RowEntry>(32)
            fun walk(nick: Nick, depth: Int) {
                val key = nick.id.value
                val expanded = expandedMap[key] ?: (depth < 3)
                rows.add(RowEntry(nick, depth, nick.children.isNotEmpty(), expanded))
                if (expanded) {
                    for (child in nick.children) walk(child, depth + 1)
                }
            }
            for (root in scene.roots) walk(root, 0)

            LazyColumn(Modifier.fillMaxSize()) {
                items(rows, key = { it.nick.id.value }) { entry ->
                    HierarchyRow(
                        entry = entry,
                        selected = entry.nick.id == selection,
                        onSelect = { vm.select(entry.nick.id) },
                        onToggleExpand = {
                            expandedMap[entry.nick.id.value] = !entry.expanded
                            vm.bump()
                        },
                        onToggleVisible = { vm.setNickVisible(entry.nick.id, !entry.nick.visible) }
                    )
                }
            }
        }
    }

    if (showRename && selection != null) {
        val nick = scene?.findNick(selection)
        var text by remember(selection) { mutableStateOf(nick?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(stringResource(R.string.hierarchy_rename_title)) },
            text = {
                Column {
                    OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
                    Spacer(Modifier.height(4.dp))
                    Text(renameHint(), fontSize = 11.sp, color = KenkernTextMid)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // VAZIO = name null = volta a exibir o rótulo do TIPO (DECISIONS #26).
                    vm.renameSelected(text.trim())
                    showRename = false
                }) { Text(stringResource(R.string.hub_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text(stringResource(R.string.hub_cancel)) }
            }
        )
    }

    if (showDelete && selection != null) {
        val nick = scene?.findNick(selection)
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.hierarchy_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.hierarchy_delete_text,
                        nick?.let { nickDisplayName(it) } ?: ""
                    ),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteSelected()
                    showDelete = false
                }) { Text(stringResource(R.string.hierarchy_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.hub_cancel)) }
            }
        )
    }

    if (showReparent && selection != null && scene != null) {
        val nick = scene.findNick(selection)
        val candidates = ArrayList<Nick>(16)
        if (nick != null) {
            fun collect(n: Nick) {
                if (!nick.isAncestorOf(n) && n !== nick) candidates.add(n)
                for (c in n.children) collect(c)
            }
            for (root in scene.roots) collect(root)
        }
        var chosen by remember(selection) { mutableStateOf<Nick?>(null) }
        AlertDialog(
            onDismissRequest = { showReparent = false },
            title = { Text(stringResource(R.string.hierarchy_move_title)) },
            text = {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { chosen = null }
                    ) {
                        RadioButton(selected = chosen == null, onClick = { chosen = null })
                        Text(stringResource(R.string.hierarchy_root), fontSize = 13.sp)
                    }
                    LazyColumn(Modifier.height(240.dp)) {
                        items(candidates, key = { it.id.value }) { cand ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { chosen = cand }
                            ) {
                                RadioButton(selected = chosen == cand, onClick = { chosen = cand })
                                Text(cand.let { nickDisplayName(it) }, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.reparentSelected(chosen?.id)
                    showReparent = false
                }) { Text(stringResource(R.string.hierarchy_move)) }
            },
            dismissButton = {
                TextButton(onClick = { showReparent = false }) { Text(stringResource(R.string.hub_cancel)) }
            }
        )
    }
}

/** Linha da árvore: 28dp, guias de indentação 1px, ícone de tipo, seta e olho. */
@Composable
private fun HierarchyRow(
    entry: RowEntry,
    selected: Boolean,
    onSelect: () -> Unit,
    onToggleExpand: () -> Unit,
    onToggleVisible: () -> Unit
) {
    val nick = entry.nick
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(if (selected) KenkernSelection else Color.Transparent)
            .clickable(onClick = onSelect)
    ) {
        // Barra âmbar de seleção (2dp à esquerda)
        if (selected) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(KenkernAmber))
        }
        // Guias verticais de indentação (1px por nível, passo de 12dp)
        repeat(entry.depth) {
            Box(
                Modifier
                    .padding(start = 11.dp)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(KenkernOutline)
            )
        }
        Spacer(Modifier.width(4.dp))

        // Seta de expandir/recolher (ou espaço reservado)
        if (entry.hasChildren) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onToggleExpand),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (entry.expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                    contentDescription = if (entry.expanded) stringResource(R.string.hierarchy_collapse)
                    else stringResource(R.string.hierarchy_expand),
                    tint = KenkernTextMid,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            Spacer(Modifier.width(20.dp))
        }

        // Ícone por tipo de Nick
        Icon(
            nickIcon(nick),
            contentDescription = null,
            tint = if (nick.children.isNotEmpty()) KenkernTextMid else KenkernTextLow,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(4.dp))

        Text(
            nickDisplayName(nick) + (if (nick.children.isNotEmpty()) "  " + stringResource(R.string.hierarchy_children, nick.children.size) else ""),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = when {
                !nick.active -> KenkernTextLow
                selected -> KenkernTextHigh
                else -> KenkernTextHigh
            },
            modifier = Modifier.weight(1f)
        )

        // Olho de visibilidade: cinza #9D9D9D; destaca (#CCCCCC) apenas quando oculto.
        IconButton(
            onClick = onToggleVisible,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                if (nick.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                contentDescription = if (nick.visible) stringResource(R.string.hierarchy_hide)
                else stringResource(R.string.hierarchy_show),
                tint = if (nick.visible) KenkernTextMid else KenkernTextHigh,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(4.dp))
    }
}

@Composable
private fun HorizontalDividerSoft() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(KenkernOutline)
    )
}
