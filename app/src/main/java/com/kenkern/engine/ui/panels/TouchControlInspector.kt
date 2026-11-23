package com.kenkern.engine.ui.panels

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kenkern.engine.R
import com.kenkern.engine.core.Nick
import com.kenkern.engine.core.NickValue
import com.kenkern.engine.core.Scene
import com.kenkern.engine.core.input.TouchControlSchema
import com.kenkern.engine.editor.EditorViewModel
import com.kenkern.engine.ui.AppLanguage
import com.kenkern.engine.ui.common.CollapsibleSection
import com.kenkern.engine.ui.common.FloatField

@Composable
internal fun TouchControlInspector(vm: EditorViewModel, scene: Scene, nick: Nick) {
    val pt = AppLanguage.currentTag(LocalContext.current) == "pt"
    val button = nick.kind == TouchControlSchema.BUTTON
    val definition = if (button) TouchControlSchema.button else TouchControlSchema.joystick
    val inst = nick.findClassInstance(definition.name)
    if (inst == null) {
        TextButton(onClick = { vm.addClassToSelected(definition.name) }) { Text(stringResource(R.string.touch_input_restore_input_settings)) }
        return
    }
    fun value(field: String) = inst.fields[field] ?: definition.fields.first { it.name == field }.default
    fun set(field: String, value: NickValue) = vm.setClassField(nick.id, definition.name, field, value)
    fun choices(vararg pairs: Pair<String, String>) = pairs.toList()
    val nicks = remember(scene, vm.revision.intValue, nick.id) {
        buildList {
            fun visit(node: Nick) {
                if (node !== nick) add(node)
                for (child in node.children) visit(child)
            }
            for (root in scene.roots) visit(root)
        }
    }
    fun targets(default: String, onlyPlayers: Boolean) = buildList {
        add("" to default)
        for (node in nicks) {
            if (onlyPlayers && node.kind != "body.player") continue
            add(node.id.value to "${node.displayName(if (pt) "pt" else "en")} · ${node.id.value.take(6)}")
        }
    }
    CollapsibleSection(stringResource(R.string.touch_input_gameplay_input)) {
        @Composable fun choice(field: String, title: String, options: List<Pair<String, String>>) =
            TouchChoice(title, (value(field) as NickValue.StringValue).value, options) { set(field, NickValue.StringValue(it)) }
        @Composable fun number(field: String, title: String, range: ClosedFloatingPointRange<Float>) =
            FloatField(title, (value(field) as NickValue.FloatValue).value, { if (it.isFinite()) set(field, NickValue.FloatValue(it.coerceIn(range))) }, Modifier.fillMaxWidth())
        @Composable fun toggle(field: String, title: String) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f))
                Switch((value(field) as NickValue.BoolValue).value, { set(field, NickValue.BoolValue(it)) })
            }
        }
        key(nick.id) {
            if (!button) choice("type", stringResource(R.string.touch_input_type), choices("fixed" to stringResource(R.string.touch_input_fixed),
                "floating" to stringResource(R.string.touch_input_floating), "dynamic" to stringResource(R.string.touch_input_dynamic)))
            number("sizeDp", stringResource(R.string.touch_input_size_dp), 40f..240f)
            number("opacity", stringResource(R.string.touch_input_opacity_0_1), 0f..1f)
            choice("anchor", stringResource(R.string.touch_input_anchor), choices(
                "bottom_left" to stringResource(R.string.touch_input_bottom_left), "bottom_right" to stringResource(R.string.touch_input_bottom_right),
                "top_left" to stringResource(R.string.touch_input_top_left), "top_right" to stringResource(R.string.touch_input_top_right),
                "follow" to stringResource(R.string.touch_input_follow_nick)))
            if ((value("anchor") as NickValue.StringValue).value == "follow") {
                choice("follow", stringResource(R.string.touch_input_follow_nick), targets(stringResource(R.string.touch_input_none), false))
            }
            val action = (value("action") as NickValue.StringValue).value
            var draft by remember(action) { mutableStateOf(action) }
            OutlinedTextField(draft, { draft = it }, label = { Text(stringResource(R.string.touch_input_named_action)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            TextButton(enabled = draft.isNotBlank() && draft.trim() != action, onClick = { set("action", NickValue.StringValue(draft.trim())) }) {
                Text(stringResource(R.string.touch_input_apply_action))
            }
            if (!button) {
                Text(stringResource(R.string.touch_input_mover_moves_a_player_olhar_controls_the_game_camera))
                number("deadZone", stringResource(R.string.touch_input_dead_zone_0_0_95), 0f..0.95f)
                number("response", stringResource(R.string.touch_input_response_exponent), .25f..4f)
                toggle("snap8", stringResource(R.string.touch_input_snap_to_8_directions))
                choice("controls", stringResource(R.string.touch_input_controls), targets(stringResource(R.string.touch_input_first_active_player), true))
                choice("space", stringResource(R.string.touch_input_movement_space), choices("world" to stringResource(R.string.touch_input_world), "camera" to stringResource(R.string.touch_input_camera)))
                number("speed", stringResource(R.string.touch_input_speed_movement_m_s), 0f..100f)
                toggle("turn", stringResource(R.string.touch_input_turn_toward_movement))
            }
        }
    }
}

@Composable
private fun TouchChoice(title: String, value: String, options: List<Pair<String, String>>, select: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text(stringResource(R.string.touch_control_choice, title, options.firstOrNull { it.first == value }?.second ?: value)) }
        DropdownMenu(expanded, { expanded = false }) {
            for ((id, text) in options) DropdownMenuItem(text = { Text(text) }, onClick = { expanded = false; select(id) })
        }
    }
}
