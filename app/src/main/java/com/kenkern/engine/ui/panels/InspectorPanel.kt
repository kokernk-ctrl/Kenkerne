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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kenkern.engine.R
import com.kenkern.engine.core.LenteNick
import com.kenkern.engine.core.LuzNick
import com.kenkern.engine.core.MalhaNick
import com.kenkern.engine.core.NickClassInstance
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.ReferenceId
import com.kenkern.engine.editor.EditorViewModel
import com.kenkern.engine.ui.nickDisplayName
import com.kenkern.engine.ui.nickKindIcon
import com.kenkern.engine.ui.nickKindLabel
import com.kenkern.engine.ui.common.AxisNumberField
import com.kenkern.engine.ui.common.CollapsibleSection
import com.kenkern.engine.ui.common.FloatField
import com.kenkern.engine.ui.common.SectionTitle
import com.kenkern.engine.ui.common.Vec3AxisField
import com.kenkern.engine.ui.theme.KenkernAmber
import com.kenkern.engine.ui.theme.KenkernAxisX
import com.kenkern.engine.ui.theme.KenkernAxisY
import com.kenkern.engine.ui.theme.KenkernAxisZ
import com.kenkern.engine.ui.theme.KenkernTextHigh
import com.kenkern.engine.ui.theme.KenkernTextLow
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
fun InspectorPanel(
    vm: EditorViewModel,
    onAbrirEditorLua: (com.kenkern.engine.ui.screens.AlvoEditorLua) -> Unit
) {
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

    // R5.5: ao TROCAR o Nick selecionado a rolagem VOLTA AO TOPO — a posição
    // era PRESERVADA entre seleções (print v0.1.28: campo Nome cortado fora
    // da tela; o usuário descia para ler e o próximo Nick já vinha descido).
    androidx.compose.runtime.LaunchedEffect(nick.id) {
        com.kenkern.engine.diag.KenkernLog.w("Inspetor", "seleção trocada — rolagem de volta ao topo")
        scroll.scrollTo(0)
    }

    Column(Modifier.fillMaxSize()) {
    // ---------- R5.2: CABEÇALHO FIXO (fora da rolagem) ----------
    // ícone + rótulo do tipo + chave técnica + interruptor "Ativo" de 36dp.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(horizontal = 12.dp)
    ) {
        Icon(
            nickKindIcon(nick.kind),
            contentDescription = null,
            tint = KenkernAmber,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                nickKindLabel(nick.kind),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = KenkernTextHigh,
                maxLines = 1
            )
            Text(
                nick.kind,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = KenkernTextLow,
                maxLines = 1
            )
        }
        // R5.1: "Ativo" de 36dp NO CABEÇALHO — CAUSA RAIZ dos interruptores
        // gigantes e sobrepostos: os dois KToggle usavam Modifier.weight(1f)
        // DENTRO de uma Column VERTICAL ROLÁVEL (altura infinita — peso de
        // COLUNA distribui altura, não largura): InspectorPanel.kt:161 e 167
        // na v0.1.28. Sem weight, 36dp, no cabeçalho.
        Switch(
            checked = nick.active,
            onCheckedChange = { vm.setNickActive(nick.id, it) },
            modifier = Modifier.height(36.dp)
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ---------- R5.3: NOME em uma linha; Aplicar/Reverter SÓ quando muda ----------
        var nameText by remember(nick.id) { mutableStateOf(nick.name ?: "") }
        val nomeAlterado = nameText.trim() != (nick.name ?: "")
        OutlinedTextField(
            value = nameText,
            onValueChange = { nameText = it },
            label = { Text(stringResource(R.string.inspector_name), fontSize = 12.sp) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            // R5.3: "Concluído" do teclado APLICA o nome (sem precisar do botão)
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Done
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = { if (nomeAlterado) vm.renameSelected(nameText.trim()) }
            ),
            modifier = Modifier.fillMaxWidth()
        )
        if (nomeAlterado) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { vm.renameSelected(nameText.trim()) }) {
                    Text(stringResource(R.string.inspector_apply_name), fontSize = 12.sp)
                }
                TextButton(onClick = { nameText = nick.name ?: "" }) {
                    Text(stringResource(R.string.inspector_revert), fontSize = 12.sp)
                }
            }
        }
        // R5: "Visível" — rótulo à esquerda, interruptor à direita, 36dp (linha).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
        ) {
            Text(
                stringResource(R.string.inspector_visible),
                fontSize = 12.sp,
                color = KenkernTextHigh,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = nick.visible,
                onCheckedChange = { vm.setNickVisible(nick.id, it) },
                modifier = Modifier.height(36.dp)
            )
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

        // ---------- T0.3: FORMA DO COLISOR (Nick com classe Colisor) ----------
        // Seletor Caixa/Esfera/Cápsula/Cilindro/Convexo/Malha; a troca AJUSTA À
        // AABB atual e é UM Command (SetColliderShapeCommand — desfazer devolve).
        val instColisor = nick.findClassInstance(MalhaNick.CLASSE_COLISOR)
        if (instColisor != null) {
            val formaAtual = instColisor.getString("forma", "box")
            CollapsibleSection(stringResource(R.string.collider_shape)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (forma in MalhaNick.FORMAS_COLISOR.take(3)) {
                            FilterChip(
                                selected = formaAtual == forma,
                                onClick = { vm.setColliderShape(nick.id, forma) },
                                label = { Text(formaLocalizada(forma), fontSize = 11.sp) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (forma in MalhaNick.FORMAS_COLISOR.drop(3)) {
                            FilterChip(
                                selected = formaAtual == forma,
                                onClick = { vm.setColliderShape(nick.id, forma) },
                                label = { Text(formaLocalizada(forma), fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }
        }

        // ---------- H2 (v0.1.20): LUZ DO LUME — Inspetor en/pt ----------
        // cor, intensidade, alcance (pontual/spot), ângulo interno e externo
        // (spot), ligado. Nick ANTIGO sem a classe "Luz": valores do default
        // + botão para aplicar a classe (um Command, entra no undo/redo).
        if (nick.kind.startsWith("light.")) {
            val instLuz = nick.findClassInstance(LuzNick.CLASSE)
            CollapsibleSection(stringResource(R.string.light_section)) {
                if (instLuz == null) {
                    Text(stringResource(R.string.light_missing), fontSize = 12.sp, color = KenkernTextMid)
                    TextButton(onClick = { vm.addClassToSelected(LuzNick.CLASSE) }) {
                        Text(stringResource(R.string.light_apply), color = KenkernAmber, fontSize = 12.sp)
                    }
                } else {
                    val luz = remember(nick.id, vm.revision.intValue) {
                        LuzNick.Valores().also { LuzNick.ler(nick, it) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.light_on), fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = luz.ligado,
                            onCheckedChange = {
                                vm.setClassField(nick.id, LuzNick.CLASSE, LuzNick.CAMPO_LIGADO, NickValue.BoolValue(it))
                            }
                        )
                    }
                    SectionTitle(stringResource(R.string.light_color))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AxisNumberField(
                            "R", KenkernAxisX, luz.corX,
                            { v -> vm.setClassField(nick.id, LuzNick.CLASSE, LuzNick.CAMPO_COR, NickValue.Vec3Value(v, luz.corY, luz.corZ)) },
                            Modifier.weight(1f)
                        )
                        AxisNumberField(
                            "G", KenkernAxisY, luz.corY,
                            { v -> vm.setClassField(nick.id, LuzNick.CLASSE, LuzNick.CAMPO_COR, NickValue.Vec3Value(luz.corX, v, luz.corZ)) },
                            Modifier.weight(1f)
                        )
                        AxisNumberField(
                            "B", KenkernAxisZ, luz.corZ,
                            { v -> vm.setClassField(nick.id, LuzNick.CLASSE, LuzNick.CAMPO_COR, NickValue.Vec3Value(luz.corX, luz.corY, v)) },
                            Modifier.weight(1f)
                        )
                    }
                    FloatField(
                        stringResource(R.string.light_intensity), luz.intensidade,
                        { v -> vm.setClassField(nick.id, LuzNick.CLASSE, LuzNick.CAMPO_INTENSIDADE, NickValue.FloatValue(v)) },
                        Modifier.fillMaxWidth()
                    )
                    // Alcance só existe na pontual e no spot (a direcional é infinita).
                    if (nick.kind != "light.directional") {
                        FloatField(
                            stringResource(R.string.light_range), luz.alcance,
                            { v -> vm.setClassField(nick.id, LuzNick.CLASSE, LuzNick.CAMPO_ALCANCE, NickValue.FloatValue(v)) },
                            Modifier.fillMaxWidth()
                        )
                    }
                    // Ângulos só no spot (cone de luz).
                    if (nick.kind == "light.spot") {
                        FloatField(
                            stringResource(R.string.light_inner_angle), luz.anguloInterno,
                            { v -> vm.setClassField(nick.id, LuzNick.CLASSE, LuzNick.CAMPO_ANGULO_INTERNO, NickValue.FloatValue(v)) },
                            Modifier.fillMaxWidth()
                        )
                        FloatField(
                            stringResource(R.string.light_outer_angle), luz.anguloExterno,
                            { v -> vm.setClassField(nick.id, LuzNick.CLASSE, LuzNick.CAMPO_ANGULO_EXTERNO, NickValue.FloatValue(v)) },
                            Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // ---------- H3 (v0.1.20): CÂMERA DA LENTE — Inspetor en/pt ----------
        // fov, near, far, ortográfica com tamanho, "Câmera principal" (só uma
        // por cena — SetLentePrincipalCommand) e os campos da seguidora
        // (alvo/distância/altura). Nick antigo sem a classe: default + aplicar.
        if (nick.kind.startsWith("camera.")) {
            val instLente = nick.findClassInstance(LenteNick.CLASSE)
            CollapsibleSection(stringResource(R.string.camera_section)) {
                if (instLente == null) {
                    Text(stringResource(R.string.camera_missing), fontSize = 12.sp, color = KenkernTextMid)
                    TextButton(onClick = { vm.addClassToSelected(LenteNick.CLASSE) }) {
                        Text(stringResource(R.string.camera_apply), color = KenkernAmber, fontSize = 12.sp)
                    }
                } else {
                    val lente = remember(nick.id, vm.revision.intValue) {
                        LenteNick.Valores().also { LenteNick.ler(nick, it) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.camera_main), fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = lente.principal,
                            onCheckedChange = { vm.setLentePrincipal(nick.id, it) }
                        )
                    }
                    FloatField(
                        stringResource(R.string.camera_fov), lente.fov,
                        { v -> vm.setClassField(nick.id, LenteNick.CLASSE, LenteNick.CAMPO_FOV, NickValue.FloatValue(v)) },
                        Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FloatField(
                            stringResource(R.string.camera_near), lente.near,
                            { v -> vm.setClassField(nick.id, LenteNick.CLASSE, LenteNick.CAMPO_NEAR, NickValue.FloatValue(v)) },
                            Modifier.weight(1f)
                        )
                        FloatField(
                            stringResource(R.string.camera_far), lente.far,
                            { v -> vm.setClassField(nick.id, LenteNick.CLASSE, LenteNick.CAMPO_FAR, NickValue.FloatValue(v)) },
                            Modifier.weight(1f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.camera_ortho), fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Switch(
                            checked = lente.ortografica,
                            onCheckedChange = {
                                vm.setClassField(nick.id, LenteNick.CLASSE, LenteNick.CAMPO_ORTOGRAFICA, NickValue.BoolValue(it))
                            }
                        )
                    }
                    if (lente.ortografica) {
                        FloatField(
                            stringResource(R.string.camera_ortho_size), lente.tamanhoOrto,
                            { v -> vm.setClassField(nick.id, LenteNick.CLASSE, LenteNick.CAMPO_TAMANHO_ORTO, NickValue.FloatValue(v)) },
                            Modifier.fillMaxWidth()
                        )
                    }
                    // Seguidora: alvo (ReferenceId), distância e altura — usados em Play.
                    SectionTitle(stringResource(R.string.camera_follow_title))
                    var alvoTexto by remember(nick.id, lente.alvo) { mutableStateOf(lente.alvo) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = alvoTexto,
                            onValueChange = { alvoTexto = it },
                            label = { Text(stringResource(R.string.camera_target), fontSize = 11.sp) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            vm.setClassField(nick.id, LenteNick.CLASSE, LenteNick.CAMPO_ALVO, NickValue.StringValue(alvoTexto.trim()))
                        }) { Text(stringResource(R.string.hub_ok), fontSize = 12.sp) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FloatField(
                            stringResource(R.string.camera_distance), lente.distancia,
                            { v -> vm.setClassField(nick.id, LenteNick.CLASSE, LenteNick.CAMPO_DISTANCIA, NickValue.FloatValue(v)) },
                            Modifier.weight(1f)
                        )
                        FloatField(
                            stringResource(R.string.camera_height), lente.altura,
                            { v -> vm.setClassField(nick.id, LenteNick.CLASSE, LenteNick.CAMPO_ALTURA, NickValue.FloatValue(v)) },
                            Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // ---------- R5.4: UMA SEÇÃO POR CLASSE (ordem fixa de nick.classes) ----------
        // As classes com seção ESPECIAL acima (Colisor = forma, Luz, Lente)
        // já renderizaram seus campos — o cartão genérico os duplicaria, por
        // isso ficam de fora. O estado recolhido de cada seção é SALVO POR
        // SEÇÃO (remember(inst.typeName) no ClassCard — não reseta ao trocar
        // de Nick, não depende da seleção).
        for (inst in nick.classes.toList()) {
            val temSecaoEspecial = inst.typeName == MalhaNick.CLASSE_COLISOR ||
                (inst.typeName == LuzNick.CLASSE && nick.kind.startsWith("light.")) ||
                (inst.typeName == LenteNick.CLASSE && nick.kind.startsWith("camera."))
            if (!temSecaoEspecial) {
                ClassCard(vm, nick.id, inst, onAbrirEditorLua)
            }
        }

        // R5.4: "Adicionar classe" NO FIM (depois de todas as seções).
        var addMenu by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { addMenu = true }) {
                Icon(Icons.Filled.Add, contentDescription = null, Modifier.width(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.inspector_add_class), fontSize = 12.sp)
            }
            DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                // T0.3: classes AGRUPADAS POR FUNÇÃO (categoria), com
                // cabeçalho de grupo; "já aplicada" continua desabilitada.
                val porCategoria = com.kenkern.engine.core.NickClassRegistry.porCategoria()
                if (porCategoria.isEmpty()) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.inspector_no_class), fontSize = 12.sp) }, onClick = {})
                }
                for ((categoria, definicoes) in porCategoria) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                categoriaLocalizada(categoria),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = KenkernTextMid
                            )
                        },
                        enabled = false,
                        onClick = {}
                    )
                    for (def in definicoes) {
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

        Spacer(Modifier.height(12.dp))
    } // fecha a Column ROLÁVEL (R5: cabeçalho fixo ficou FORA dela)
    } // fecha a Column raiz (cabeçalho fixo + corpo rolável)
}

/** Rótulo localizado da forma do colisor (chave estável → texto do idioma). */
@Composable
private fun formaLocalizada(forma: String): String = stringResource(
    when (forma) {
        "box" -> R.string.collider_box
        "sphere" -> R.string.collider_sphere
        "capsule" -> R.string.collider_capsule
        "cylinder" -> R.string.collider_cylinder
        "convex" -> R.string.collider_convex
        "mesh" -> R.string.collider_mesh
        else -> R.string.collider_box
    }
)

/** Rótulo localizado da CATEGORIA de classes (chave estável → texto do idioma). */
@Composable
private fun categoriaLocalizada(categoria: String): String = stringResource(
    when (categoria) {
        "movimento" -> R.string.class_cat_movimento
        "geometria" -> R.string.class_cat_geometria
        "fisica" -> R.string.class_cat_fisica
        "iluminacao" -> R.string.class_cat_iluminacao
        "camera" -> R.string.class_cat_camera
        "logica" -> R.string.class_cat_logica
        else -> R.string.class_cat_geral
    }
)

@Composable
private fun ClassCard(
    vm: EditorViewModel,
    nickId: ReferenceId,
    inst: NickClassInstance,
    onAbrirEditorLua: (com.kenkern.engine.ui.screens.AlvoEditorLua) -> Unit
) {
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
            // C1 (v0.1.26): classe SCRIPT — botão "Abrir no editor" abre o
            // editor de código Lua (retrato) na fonte inline ou no arquivo.
            if (inst.typeName == "Script") {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val arquivo = (inst.fields["arquivo"] as? com.kenkern.engine.core.NickValue.StringValue)?.value ?: ""
                        if (arquivo.isNotBlank()) {
                            onAbrirEditorLua(com.kenkern.engine.ui.screens.AlvoEditorLua.Arquivo(arquivo))
                        } else {
                            onAbrirEditorLua(com.kenkern.engine.ui.screens.AlvoEditorLua.Inline(nickId))
                        }
                    }
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.width(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.editor_lua_abrir), fontSize = 12.sp)
                }
            }
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
