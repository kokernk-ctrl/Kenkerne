package com.kenkern.engine.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kenkern.engine.core.Nick
import com.kenkern.engine.editor.EditorViewModel

private class RowEntry(val nick: Nick, val depth: Int, val hasChildren: Boolean, val expanded: Boolean)

/**
 * Painel HIERARQUIA: árvore de Nicks com criar, renomear, apagar,
 * reordenar (subir/descer) e reparentar.
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
        // Barra de ferramentas
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
            IconButton(onClick = { vm.addNick() }, enabled = scene != null) {
                Icon(Icons.Filled.Add, "Criar nick", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { showRename = true }, enabled = selection != null) {
                Icon(Icons.Filled.Edit, "Renomear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { showDelete = true }, enabled = selection != null) {
                Icon(Icons.Filled.Delete, "Apagar", tint = MaterialTheme.colorScheme.error)
            }
            IconButton(onClick = { selection?.let { vm.reorderSelected(-1) } }, enabled = selection != null) {
                Icon(Icons.Filled.KeyboardArrowUp, "Subir", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { selection?.let { vm.reorderSelected(1) } }, enabled = selection != null) {
                Icon(Icons.Filled.KeyboardArrowDown, "Descer", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { showReparent = true }, enabled = selection != null && scene != null) {
                Icon(Icons.Filled.AccountTree, "Reparentar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        HorizontalDividerSoft()

        if (scene == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nenhuma cena", style = MaterialTheme.typography.labelMedium)
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
                    val nick = entry.nick
                    val selected = nick.id == selection
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                else ColorTransparent
                            )
                            .clickable { vm.select(nick.id) }
                            .padding(start = (10 + entry.depth * 14).dp, end = 6.dp)
                    ) {
                        if (entry.hasChildren) {
                            Icon(
                                if (entry.expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                                contentDescription = if (entry.expanded) "Recolher" else "Expandir",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier
                                    .size(22.dp)
                                    .clickable {
                                        expandedMap[nick.id.value] = !entry.expanded
                                        vm.bump()
                                    }
                            )
                        } else {
                            Spacer(Modifier.width(22.dp))
                        }
                        Text(
                            nick.name + if (nick.children.isNotEmpty()) "  (${nick.children.size})" else "",
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = when {
                                !nick.active -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                selected -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { vm.setNickVisible(nick.id, !nick.visible) },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                if (nick.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (nick.visible) "Ocultar" else "Mostrar",
                                tint = if (nick.visible) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRename && selection != null) {
        val nick = scene?.findNick(selection)
        var text by remember(selection) { mutableStateOf(nick?.name ?: "") }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Renomear Nick") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) vm.renameSelected(text.trim())
                    showRename = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("Cancelar") }
            }
        )
    }

    if (showDelete && selection != null) {
        val nick = scene?.findNick(selection)
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Apagar Nick") },
            text = {
                Text(
                    "Apagar \"${nick?.name}\" e toda a sua subárvore? " +
                        "Você pode desfazer com o botão de undo.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteSelected()
                    showDelete = false
                }) { Text("Apagar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancelar") }
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
            title = { Text("Mover para novo pai") },
            text = {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { chosen = null }
                    ) {
                        RadioButton(selected = chosen == null, onClick = { chosen = null })
                        Text("Raiz da cena", fontSize = 13.sp)
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
                                Text(cand.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.reparentSelected(chosen?.id)
                    showReparent = false
                }) { Text("Mover") }
            },
            dismissButton = {
                TextButton(onClick = { showReparent = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun HorizontalDividerSoft() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    )
}

private val ColorTransparent = androidx.compose.ui.graphics.Color.Transparent
