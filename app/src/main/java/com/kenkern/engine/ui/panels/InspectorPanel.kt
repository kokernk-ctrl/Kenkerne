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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kenkern.engine.R
import com.kenkern.engine.core.NickClassInstance
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.ReferenceId
import com.kenkern.engine.editor.EditorViewModel
import com.kenkern.engine.ui.nickDisplayName
import com.kenkern.engine.ui.renameHint
import com.kenkern.engine.ui.common.AxisNumberField
import com.kenkern.engine.ui.common.CollapsibleSection
import com.kenkern.engine.ui.common.FloatField
import com.kenkern.engine.ui.common.SectionTitle
import com.kenkern.engine.ui.common.Vec3AxisField
import com.kenkern.engine.ui.theme.KenkernAxisX
import com.kenkern.engine.ui.theme.KenkernAxisY
import com.kenkern.engine.ui.theme.KenkernAxisZ
import com.kenkern.engine.ui.theme.KenkernTextHigh
import com.kenkern.engine.ui.theme.KenkernTextMid

/**
 * Painel INSPETOR: edita nome, ativo/visível, transform (posição, rotação em
 * quaternion + euler, escala), classes e campos do Nick selecionado.
 *
 * Visual: seções recolhíveis (TRANSFORM, CLASSES) com cabeçalho de 28dp;
 * campos de posição/rotação/escala em linha com rótulos X/Y/Z coloridos por
 * eixo e campos numéricos de 28dp (#1E1E1E, borda 1px).
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
                stringResource(R.string.inspector_select_hint),
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Nome (T0.2: VAZIO = rótulo do tipo no idioma atual — DECISIONS #26)
        var nameText by remember(nick.id) { mutableStateOf(nick.name ?: "") }
        OutlinedTextField(
            value = nameText,
            onValueChange = { nameText = it },
            label = { Text(stringResource(R.string.inspector_name), fontSize = 12.sp) },
            supportingText = { Text(renameHint(), fontSize = 11.sp) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { if (nameText.trim() != (nick.name ?: "")) {
                vm.renameSelected(nameText.trim())
            } }) { Text(stringResource(R.string.inspector_apply_name), fontSize = 12.sp) }
            TextButton(onClick = { vm.select(nick.id) }) { Text(stringResource(R.string.inspector_revert), fontSize = 12.sp) }
        }

        // Flags
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.inspector_active), fontSize = 13.sp, modifier = Modifier.weight(1f))
            Switch(checked = nick.active, onCheckedChange = { vm.setNickActive(nick.id, it) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.inspector_visible), fontSize = 13.sp, modifier = Modifier.weight(1f))
            Switch(checked = nick.visible, onCheckedChange = { vm.setNickVisible(nick.id, it) })
        }

        // ---------- TRANSFORM (seção recolhível) ----------
        CollapsibleSection(stringResource(R.string.inspector_transform)) {
            SectionTitle(stringResource(R.string.inspector_position))
            Vec3AxisField(
                nick.transform.position.x, nick.transform.position.y, nick.transform.position.z
            ) { x, y, z ->
                val snap = nick.transform.snapshot()
                vm.setTransform(nick.id, snap.copy(px = x, py = y, pz = z))
            }

            SectionTitle(stringResource(R.string.inspector_scale))
            Vec3AxisField(
                nick.transform.scale.x, nick.transform.scale.y, nick.transform.scale.z
            ) { x, y, z ->
                val snap = nick.transform.snapshot()
                vm.setTransform(nick.id, snap.copy(sx = x, sy = y, sz = z))
            }

            SectionTitle(stringResource(R.string.inspector_rotation_euler))
            val euler = remember(nick.id, vm.revision.intValue) {
                FloatArray(3).also { nick.transform.rotation.toEulerDegrees(it) }
            }
            Vec3AxisField(euler[0], euler[1], euler[2]) { x, y, z ->
                val snap = nick.transform.snapshot()
                val q = com.kenkern.engine.core.math.Quaternion()
                q.setFromEulerDegrees(x, y, z)
                vm.setTransform(nick.id, snap.copy(qx = q.x, qy = q.y, qz = q.z, qw = q.w))
            }

            SectionTitle(stringResource(R.string.inspector_rotation_quat))
            // SOMENTE LEITURA (girar pelo anel do gizmo ou por euler); campos de 28dp,
            // incluindo o W, iguais aos demais. Normalizado ao editar não se aplica.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AxisNumberField(
                    "X", KenkernAxisX, nick.transform.rotation.x, {}, Modifier.weight(1f), readOnly = true
                )
                AxisNumberField(
                    "Y", KenkernAxisY, nick.transform.rotation.y, {}, Modifier.weight(1f), readOnly = true
                )
                AxisNumberField(
                    "Z", KenkernAxisZ, nick.transform.rotation.z, {}, Modifier.weight(1f), readOnly = true
                )
                AxisNumberField(
                    "W", KenkernTextHigh, nick.transform.rotation.w, {}, Modifier.weight(1f), readOnly = true
                )
            }
        }

        // ---------- CLASSES (seção recolhível) ----------
        CollapsibleSection(stringResource(R.string.inspector_classes)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (inst in nick.classes.toList()) {
                    ClassCard(vm, nick.id, inst)
                }

                var addMenu by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { addMenu = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null, Modifier.width(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.inspector_add_class), fontSize = 12.sp)
                    }
                    DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                        val available = com.kenkern.engine.core.NickClassRegistry.all()
                        if (available.isEmpty()) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.inspector_no_class), fontSize = 12.sp) }, onClick = {})
                        }
                        for (def in available) {
                            val already = nick.findClassInstance(def.name) != null
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        def.name + if (already) "  " + stringResource(R.string.inspector_class_applied) else "",
                                        fontSize = 12.sp
                                    )
                                },
                                enabled = !already,
                                onClick = {
                                    addMenu = false
                                    vm.addClassToSelected(def.name)
                                }
                            )
                        }
                    }
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { expanded = !expanded }
            )
            Spacer(Modifier.width(4.dp))
            Text(
                inst.typeName,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { vm.removeClassFromSelected(inst.typeName) }, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.inspector_remove_class, inst.typeName),
                    tint = KenkernTextMid,
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
                                label = { Text(fieldName, fontSize = 12.sp) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                vm.setClassField(nickId, inst.typeName, fieldName, NickValue.StringValue(text))
                            }) { Text(stringResource(R.string.hub_ok), fontSize = 12.sp) }
                        }
                        is NickValue.Vec3Value -> Column(Modifier.weight(1f)) {
                            Text(fieldName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Vec3AxisField(value.x, value.y, value.z) { nx, ny, nz ->
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
