package com.kenkern.engine.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kenkern.engine.R
import com.kenkern.engine.assets.mesh.KernMaterial
import com.kenkern.engine.core.MalhaNick
import com.kenkern.engine.editor.EditorViewModel
import com.kenkern.engine.render.ProjectAssetBindings
import com.kenkern.engine.ui.theme.KenkernTextHigh
import com.kenkern.engine.ui.theme.KenkernTextMid

/** Preview the same materials the renderer uses without reading a second copy of the mesh. */
@Composable
internal fun TextureWorkspacePanel(vm: EditorViewModel, onImport: (folder: Boolean) -> Unit) {
    val revision = vm.revision.intValue
    val scene = vm.scene.value
    val nick = vm.selection.value?.let { scene?.findNick(it) }
    val meshPath = nick?.let(MalhaNick::path)
    val materials by produceState(emptyList<KernMaterial>(), scene, meshPath, revision) {
        value = emptyList()
        if (meshPath != null) ProjectAssetBindings.of(scene)?.materialsFor(meshPath)?.collect { value = it }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(stringResource(R.string.hub_texture_tools), fontSize = 13.sp, color = KenkernTextMid)
        TextButton(onClick = { onImport(false) }) { Text(stringResource(R.string.editor_import_file)) }
        TextButton(onClick = { onImport(true) }) { Text(stringResource(R.string.editor_import_folder)) }
        if (meshPath != null) {
            Text(stringResource(R.string.hub_selected_mesh, meshPath), fontSize = 12.sp, color = KenkernTextMid)
            materials.forEachIndexed { index, material ->
                val name = material.nome.ifBlank { stringResource(R.string.hub_material_unnamed) }
                Text(stringResource(R.string.hub_material_name, index + 1, name), fontSize = 13.sp,
                    color = KenkernTextHigh, fontWeight = FontWeight.SemiBold)
                if (material.temTexturaBase) {
                    Text(stringResource(R.string.hub_base_texture, material.texturaBasePath), fontSize = 12.sp, color = KenkernTextMid)
                }
                if (material.temTexturaNormal) {
                    Text(stringResource(R.string.hub_normal_texture, material.texturaNormalPath), fontSize = 12.sp, color = KenkernTextMid)
                }
            }
        }
    }
}
