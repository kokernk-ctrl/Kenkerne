package com.kenkern.engine.ui.panels

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kenkern.engine.R
import com.kenkern.engine.assets.browser.AssetCategory
import com.kenkern.engine.assets.browser.ProjectAsset
import com.kenkern.engine.assets.browser.ProjectAssetBrowser
import com.kenkern.engine.assets.browser.ProjectAssetIndex
import com.kenkern.engine.assets.browser.SceneMeshReference
import com.kenkern.engine.core.MalhaNick
import com.kenkern.engine.core.Nick
import com.kenkern.engine.core.Scene
import com.kenkern.engine.ui.theme.KenkernBackground
import com.kenkern.engine.ui.theme.KenkernSelection
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@Composable
internal fun AssetBrowserDialog(root: File, scene: Scene?, onClose: () -> Unit) {
    var scanToken by remember(root) { mutableIntStateOf(0) }
    var catalog by remember(root) { mutableStateOf<ProjectAssetIndex?>(null) }
    var scanFailed by remember(root) { mutableStateOf(false) }
    var filter by rememberSaveable(root.path) { mutableStateOf(AssetFilter.ALL) }
    var query by rememberSaveable(root.path) { mutableStateOf("") }
    var selectedKey by rememberSaveable(root.path) { mutableStateOf<String?>(null) }
    LaunchedEffect(root, scene, scanToken) {
        catalog = null
        scanFailed = false
        // Copy the editor-owned scene values on Main. The scanner only sees immutable data.
        try {
            val references = scene?.withEditorAccess { sceneMeshReferences(scene) }.orEmpty()
            catalog = withContext(Dispatchers.IO) {
                ProjectAssetBrowser.scan(root, references) { ensureActive() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            scanFailed = true
        }
    }
    val visible = remember(catalog, filter, query) {
        catalog?.filter(filter.category, query, filter == AssetFilter.BROKEN).orEmpty()
    }
    val selected = remember(visible, selectedKey) { visible.firstOrNull { it.listKey == selectedKey } }
    val listState = rememberLazyListState()
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth().fillMaxHeight(),
                shape = RoundedCornerShape(12.dp)
            ) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val twoPanes = maxWidth >= 540.dp
                    val showingSingleDetail = !twoPanes && selected != null
                    val focus = LocalFocusManager.current
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (showingSingleDetail) {
                                IconButton(onClick = { selectedKey = null }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.asset_browser_back))
                                }
                            }
                            Text(
                                stringResource(R.string.asset_browser_title), Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            IconButton(onClick = { focus.clearFocus(); scanToken++ }) {
                                Icon(Icons.Filled.Refresh, stringResource(R.string.asset_browser_refresh))
                            }
                            IconButton(onClick = onClose) {
                                Icon(Icons.Filled.Close, stringResource(R.string.asset_browser_close))
                            }
                        }
                        HorizontalDivider()
                        if (showingSingleDetail && selected != null) {
                            key(selected.listKey, scanToken) { AssetDetails(root, selected, Modifier.fillMaxSize()) }
                        } else {
                            Row(Modifier.fillMaxSize()) {
                                Column(Modifier.weight(if (twoPanes) 1.15f else 1f).fillMaxHeight()) {
                                    AssetFilterRow(query, { query = it; selectedKey = null }, filter, {
                                        filter = it
                                        selectedKey = null
                                        focus.clearFocus()
                                    })
                                    when {
                                        scanFailed -> BrowserMessage(R.string.asset_browser_scan_failed)
                                        catalog == null -> BrowserMessage(R.string.asset_browser_scanning)
                                        else -> {
                                            if (catalog?.incomplete == true) {
                                                Text(
                                                    stringResource(R.string.asset_browser_partial),
                                                    color = MaterialTheme.colorScheme.error,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.padding(horizontal = 12.dp)
                                                )
                                            }
                                            if (visible.isEmpty()) {
                                                BrowserMessage(if (query.isBlank() && filter == AssetFilter.ALL)
                                                    R.string.asset_browser_empty else R.string.asset_browser_no_match)
                                            } else {
                                                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                                                    items(visible, key = { it.listKey }) { entry ->
                                                        AssetRow(entry, entry == selected) {
                                                            focus.clearFocus()
                                                            selectedKey = entry.listKey
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (twoPanes) {
                                    VerticalDivider()
                                    Box(Modifier.weight(1f).fillMaxHeight()) {
                                        if (selected == null) BrowserMessage(R.string.asset_browser_select)
                                        else key(selected.listKey, scanToken) { AssetDetails(root, selected, Modifier.fillMaxSize()) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun sceneMeshReferences(scene: Scene): List<SceneMeshReference> = buildList {
    val pending = ArrayDeque<Nick>()
    scene.roots.forEach { pending.addLast(it) }
    while (pending.isNotEmpty()) {
        val nick = pending.removeLast()
        MalhaNick.path(nick)?.let { add(SceneMeshReference(it, nick.name?.takeIf(String::isNotBlank) ?: nick.id.value)) }
        nick.children.forEach { pending.addLast(it) }
    }
}

private enum class AssetFilter(val label: Int, val category: AssetCategory? = null) {
    ALL(R.string.asset_browser_all),
    MESH(R.string.asset_browser_mesh, AssetCategory.MESH),
    TEXTURE(R.string.asset_browser_texture, AssetCategory.TEXTURE),
    SCENE(R.string.asset_browser_scene, AssetCategory.SCENE),
    SCRIPT(R.string.asset_browser_script, AssetCategory.SCRIPT),
    AUDIO(R.string.asset_browser_audio, AssetCategory.AUDIO),
    OTHER(R.string.asset_browser_other, AssetCategory.OTHER),
    BROKEN(R.string.asset_browser_broken)
}

@Composable
private fun AssetFilterRow(query: String, onQuery: (String) -> Unit, filter: AssetFilter, onFilter: (AssetFilter) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        OutlinedTextField(
            value = query, onValueChange = onQuery, modifier = Modifier.weight(1f),
            singleLine = true, label = { Text(stringResource(R.string.asset_browser_search)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() })
        )
        Box(Modifier.widthIn(max = 158.dp)) {
            TextButton(onClick = { focus.clearFocus(); expanded = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(stringResource(filter.label), Modifier.weight(1f, fill = false), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.asset_browser_filter))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                AssetFilter.entries.forEach { option ->
                    DropdownMenuItem(text = { Text(stringResource(option.label)) }, onClick = { expanded = false; onFilter(option) })
                }
            }
        }
    }
}

@Composable
private fun AssetRow(asset: ProjectAsset, selected: Boolean, onSelect: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(if (selected) KenkernSelection else MaterialTheme.colorScheme.surface)
            .selectable(selected, onClick = onSelect).heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(asset.path, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        Text(
            if (asset.broken) stringResource(R.string.asset_browser_broken_row)
            else stringResource(R.string.asset_browser_file_info, categoryName(asset), asset.sizeBytes ?: 0L),
            style = MaterialTheme.typography.bodySmall,
            color = if (asset.broken) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    HorizontalDivider()
}

@Composable
private fun categoryName(asset: ProjectAsset): String = stringResource(AssetFilter.entries.first { it.category == asset.category }.label)

@Composable
private fun BrowserMessage(label: Int) {
    Text(stringResource(label), Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun AssetDetails(root: File, asset: ProjectAsset, modifier: Modifier) {
    var preview by remember { mutableStateOf<ProjectAssetPreview?>(null) }
    LaunchedEffect(root, asset) { if (!asset.broken) preview = loadAssetPreview(root, asset) }
    Column(modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SelectionContainer { Text(asset.path, style = MaterialTheme.typography.titleSmall) }
        if (asset.broken) {
            Text(stringResource(R.string.asset_browser_broken_row), color = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.asset_browser_referenced_by), style = MaterialTheme.typography.bodyMedium)
            SelectionContainer { Text(asset.brokenOwners.joinToString("\n"), style = MaterialTheme.typography.bodySmall) }
        } else {
            Text(stringResource(R.string.asset_browser_file_info, categoryName(asset), asset.sizeBytes ?: 0L),
                style = MaterialTheme.typography.bodySmall)
            when (val current = preview) {
                null -> Text(stringResource(R.string.asset_browser_preview_loading))
                is ProjectAssetPreview.Image -> {
                    Text(stringResource(R.string.asset_browser_image_dimensions, current.width, current.height))
                    Image(
                        remember(current) { current.bitmap.asImageBitmap() }, asset.name,
                        Modifier.fillMaxWidth().heightIn(max = 256.dp).background(KenkernBackground)
                    )
                }
                is ProjectAssetPreview.Mesh -> with(current.metadata) {
                    Text(stringResource(R.string.asset_browser_mesh_metadata, version, vertices, triangles, lods))
                }
                ProjectAssetPreview.Unavailable -> Text(stringResource(R.string.asset_browser_preview_unavailable))
                ProjectAssetPreview.TooLarge -> Text(stringResource(R.string.asset_browser_preview_too_large))
                ProjectAssetPreview.CannotRead -> Text(stringResource(R.string.asset_browser_preview_error))
                ProjectAssetPreview.Gone -> Text(stringResource(R.string.asset_browser_preview_gone))
            }
        }
    }
}
