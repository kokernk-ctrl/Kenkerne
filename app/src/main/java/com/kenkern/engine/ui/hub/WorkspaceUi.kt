package com.kenkern.engine.ui.hub

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import com.kenkern.engine.R
import com.kenkern.engine.editor.EditorWorkspace
import com.kenkern.engine.ui.theme.KenkernAmber
import com.kenkern.engine.ui.theme.KenkernSurfaceHigh

@get:StringRes
internal val EditorWorkspace.label: Int get() = when (this) {
    EditorWorkspace.MODEL -> R.string.hub_workspace_model
    EditorWorkspace.ANIMATE -> R.string.hub_workspace_animate
    EditorWorkspace.TEXTURE -> R.string.hub_workspace_texture
}

internal val EditorWorkspace.icon: ImageVector get() = when (this) {
    EditorWorkspace.MODEL -> Icons.Filled.Category
    EditorWorkspace.ANIMATE -> Icons.Filled.Animation
    EditorWorkspace.TEXTURE -> Icons.Filled.Texture
}

@Composable
internal fun EditorWorkspaceSelector(workspace: EditorWorkspace, switchTo: (EditorWorkspace) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(workspace.icon, stringResource(R.string.hub_editor_workspaces_cd, stringResource(workspace.label)),
                tint = KenkernAmber)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }, containerColor = KenkernSurfaceHigh) {
            EditorWorkspace.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.label)) },
                    leadingIcon = { Icon(option.icon, null) },
                    modifier = Modifier.semantics { selected = option == workspace },
                    onClick = { expanded = false; switchTo(option) }
                )
            }
        }
    }
}
