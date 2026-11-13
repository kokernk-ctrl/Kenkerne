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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kenkern.engine.core.NickClassInstance
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.ReferenceId
import com.kenkern.engine.editor.EditorViewModel
import com.kenkern.engine.ui.common.FloatField
import com.kenkern.engine.ui.common.SectionTitle
import com.kenkern.engine.ui.common.Vec3Field
import com.kenkern.engine.ui.common.formatFloat

/**
 * Painel INSPETOR: edita nome, ativo/visível, transform (posição, rotação em
 * quaternion + euler, escala), classes e campos do Nick selecionado.
 */
@Composable
fun InspectorPanel(vm: EditorViewModel) {
    vm.revision.intValue
    val scene = vm.scene.value
    val selection = vm.selection.value
    val nick = if (scene != null && selection != null) scene.findNick(selection) else null

    if (nick == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Selecione um Nick na Hierarquia",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val scroll = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Nome
        var nameText by remember(nick.id) { mutableStateOf(nick.name) }
        OutlinedTextField(
            value = nameText,
            onValueChange = { nameText = it },
            label = { Text("Nome", fontSize = 10.sp) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { if (nameText.isNotBlank() && nameText != nick.name) {
                vm.renameSelected(nameText.trim())
            } }) { Text("Aplicar nome", fontSize = 11.sp) }
            TextButton(onClick = { vm.select(nick.id) }) { Text("Reverter", fontSize = 11.sp) }
        }

        // Flags
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ativo", fontSize = 13.sp, modifier = Modifier.weight(1f))
            Switch(checked = nick.active, onCheckedChange = { vm.setNickActive(nick.id, it) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Visível", fontSize = 13.sp, modifier = Modifier.weight(1f))
            Switch(checked = nick.visible, onCheckedChange = { vm.setNickVisible(nick.id, it) })
        }

        SectionTitle("TRANSFORM")

        SectionTitle("Posição", Modifier.padding(top = 2.dp))
        Vec3Field(
            "X", "Y", "Z",
            nick.transform.position.x, nick.transform.position.y, nick.transform.position.z
        ) { x, y, z ->
            val snap = nick.transform.snapshot()
            vm.setTransform(
                nick.id, snap.copy(px = x, py = y, pz = z)
            )
        }

        SectionTitle("Escala", Modifier.padding(top = 2.dp))
        Vec3Field(
            "X", "Y", "Z",
            nick.transform.scale.x, nick.transform.scale.y, nick.transform.scale.z
        ) { x, y, z ->
            val snap = nick.transform.snapshot()
            vm.setTransform(nick.id, snap.copy(sx = x, sy = y, sz = z))
        }

        SectionTitle("Rotação (euler)", Modifier.padding(top = 2.dp))
        val euler = remember(nick.id, vm.revision.intValue) {
            FloatArray(3).also { nick.transform.rotation.toEulerDegrees(it) }
        }
        Vec3Field(
            "X", "Y", "Z",
            euler[0], euler[1], euler[2]
        ) { x, y, z ->
            val snap = nick.transform.snapshot()
            val q = com.kenkern.engine.core.math.Quaternion()
            q.setFromEulerDegrees(x, y, z)
            vm.setTransform(nick.id, snap.copy(qx = q.x, qy = q.y, qz = q.z, qw = q.w))
        }

        SectionTitle("Rotação (quaternion)", Modifier.padding(top = 2.dp))
        Vec3Field(
            "qx", "qy", "qz",
            nick.transform.rotation.x, nick.transform.rotation.y, nick.transform.rotation.z
        ) { x, y, z ->
            val snap = nick.transform.snapshot()
            vm.setTransform(nick.id, snap.copy(qx = x, qy = y, qz = z))
        }
        FloatField(
            "qw",
            nick.transform.rotation.w,
            { w ->
                val snap = nick.transform.snapshot()
                vm.setTransform(nick.id, snap.copy(qw = w))
            },
            Modifier.fillMaxWidth()
        )

        SectionTitle("CLASSES")
        for (inst in nick.classes.toList()) {
            ClassCard(vm, nick.id, inst)
        }

        var addMenu by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { addMenu = true }) {
                Icon(Icons.Filled.Add, contentDescription = null, Modifier.width(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Adicionar classe", fontSize = 12.sp)
            }
            DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                val available = com.kenkern.engine.core.NickClassRegistry.all()
                if (available.isEmpty()) {
                    DropdownMenuItem(text = { Text("Nenhuma classe", fontSize = 12.sp) }, onClick = {})
                }
                for (def in available) {
                    val already = nick.findClassInstance(def.name) != null
                    DropdownMenuItem(
                        text = { Text(def.name + if (already) "  (já aplicada)" else "", fontSize = 12.sp) },
                        enabled = !already,
                        onClick = {
                            addMenu = false
                            vm.addClassToSelected(def.name)
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ClassCard(vm: EditorViewModel, nickId: ReferenceId, inst: NickClassInstance) {
    var expanded by remember(inst.typeName) { mutableStateOf(true) }

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { expanded = !expanded }
            )
            Spacer(Modifier.width(4.dp))
            Text(
                inst.typeName,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { vm.removeClassFromSelected(inst.typeName) }, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Remover classe ${inst.typeName}",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (expanded) {
            for ((fieldName, value) in inst.fields.toList()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    when (value) {
                        is NickValue.FloatValue -> FloatField(
                            fieldName, value.value,
                            { nv -> vm.setClassField(nickId, inst.typeName, fieldName, NickValue.FloatValue(nv)) },
                            Modifier.weight(1f)
                        )
                        is NickValue.IntValue -> FloatField(
                            fieldName, value.value.toFloat(),
                            { nv -> vm.setClassField(nickId, inst.typeName, fieldName, NickValue.IntValue(nv.toInt())) },
                            Modifier.weight(1f)
                        )
                        is NickValue.BoolValue -> {
                            Text(fieldName, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Switch(
                                checked = value.value,
                                onCheckedChange = { nv ->
                                    vm.setClassField(nickId, inst.typeName, fieldName, NickValue.BoolValue(nv))
                                }
                            )
                        }
                        is NickValue.StringValue -> {
                            var text by remember(fieldName, value.value) { mutableStateOf(value.value) }
                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it },
                                label = { Text(fieldName, fontSize = 10.sp) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                vm.setClassField(nickId, inst.typeName, fieldName, NickValue.StringValue(text))
                            }) { Text("OK", fontSize = 11.sp) }
                        }
                        is NickValue.Vec3Value -> Column(Modifier.weight(1f)) {
                            Text(fieldName, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Vec3Field("x", "y", "z", value.x, value.y, value.z) { nx, ny, nz ->
                                vm.setClassField(
                                    nickId, inst.typeName, fieldName, NickValue.Vec3Value(nx, ny, nz)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
