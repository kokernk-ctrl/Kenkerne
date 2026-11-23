package com.kenkern.engine.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kenkern.engine.R
import com.kenkern.engine.projects.Kernpack
import com.kenkern.engine.projects.ProjectHandle
import com.kenkern.engine.projects.ProjectManager
import com.kenkern.engine.ui.AppLanguage
import com.kenkern.engine.ui.theme.KenkernAmber
import com.kenkern.engine.ui.theme.KenkernBackground
import com.kenkern.engine.ui.theme.KenkernError
import com.kenkern.engine.ui.theme.KenkernOutlineSoft
import com.kenkern.engine.ui.theme.KenkernSurface
import com.kenkern.engine.ui.theme.KenkernSurfaceHigh
import com.kenkern.engine.ui.theme.KenkernTextHigh
import com.kenkern.engine.ui.theme.KenkernTextLow
import com.kenkern.engine.ui.theme.KenkernTextMid
import java.io.File
import java.util.Date
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.kenkern.engine.editor.EditorWorkspace
import com.kenkern.engine.projects.RecentProject
import com.kenkern.engine.ui.hub.HubLayout
import com.kenkern.engine.ui.hub.HubSpace
import com.kenkern.engine.ui.hub.icon
import com.kenkern.engine.ui.hub.label
import java.text.DateFormat

private const val HERO_KEY = "hub.hero"

@Composable
fun HomeScreen(
    projects: List<RecentProject>,
    projectsLoaded: Boolean,
    isLoading: Boolean,
    statusMessage: String?,
    onConsumeStatus: () -> Unit,
    onCreateProject: (String) -> Unit,
    onOpenProject: (ProjectHandle) -> Unit,
    onRenameProject: (ProjectHandle, String) -> Unit,
    onDeleteProject: (ProjectHandle) -> Unit,
    onAbrirDiagnostico: () -> Unit,
    onOpenWorkspace: (EditorWorkspace) -> Unit,
    onDuplicateProject: (ProjectHandle) -> Unit,
    onImportarKernpack: (android.net.Uri) -> Unit,
    onExportarProjeto: (ProjectHandle, android.net.Uri) -> Unit
) {
    var showNewDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ProjectHandle?>(null) }
    var pendingRename by remember { mutableStateOf<ProjectHandle?>(null) }

    // SAF (F2.6): importar .kernpack (abrir) e exportar (criar documento)
    val importarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onImportarKernpack(uri) }
    var exportando by rememberSaveable { mutableStateOf<ArrayList<String>?>(null) }
    val exportarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val alvo = exportando?.let { (path, name, created) -> ProjectHandle(File(path), name, created) }
        exportando = null
        if (uri != null && alvo != null) onExportarProjeto(alvo, uri)
    }

    Box(Modifier.fillMaxSize().background(KenkernBackground).windowInsetsPadding(WindowInsets.safeDrawing)) {
        BoxWithConstraints(Modifier.widthIn(max = 1120.dp).fillMaxSize().align(Alignment.TopCenter)) {
            val layout = HubLayout.forBounds(maxWidth.value, maxHeight.value, LocalDensity.current.fontScale)
            val showProjectArt = (maxWidth.value - 32f) / layout.columns /
                LocalDensity.current.fontScale.coerceAtLeast(1f) >= 275f
            val gridState = rememberLazyGridState()
            val heroVisible by remember(gridState) {
                derivedStateOf { gridState.layoutInfo.visibleItemsInfo.any { it.key == HERO_KEY } }
            }
            val newProject = { showNewDialog = true }
            LazyVerticalGrid(
                columns = GridCells.Fixed(layout.columns),
                state = gridState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp,
                    bottom = if (statusMessage != null) 92.dp else 20.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = HERO_KEY, span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = layout.heroHeightDp.dp)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        HubSpace(visible = heroVisible, modifier = Modifier.matchParentSize())
                        Column(Modifier.align(Alignment.CenterStart).padding(20.dp)) {
                            Text(stringResource(R.string.hub_brand), fontSize = 30.sp,
                                fontWeight = FontWeight.SemiBold, color = KenkernTextHigh,
                                modifier = Modifier.semantics { heading() })
                            Text(stringResource(R.string.hub_version, ProjectManager.ENGINE_VERSION),
                                fontSize = 12.sp, color = KenkernTextMid)
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val utilities: @Composable () -> Unit = {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            LanguageSelector()
                            IconButton(
                                onClick = { importarLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                                enabled = !isLoading,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Filled.Folder, stringResource(R.string.hub_import_cd), tint = KenkernTextMid)
                            }
                            IconButton(onClick = onAbrirDiagnostico, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Filled.BugReport, stringResource(R.string.hub_diagnostico_cd), tint = KenkernTextMid)
                            }
                        }
                    }
                    if (layout.shortcutRow) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            utilities()
                            Spacer(Modifier.weight(1f).widthIn(min = 4.dp))
                            NewProjectButton(isLoading, newProject)
                        }
                    } else {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                            utilities()
                            NewProjectButton(isLoading, newProject)
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HubSectionTitle(R.string.hub_workspaces)
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    if (layout.shortcutRow) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EditorWorkspace.entries.forEach { target ->
                                WorkspaceShortcut(target, !isLoading, { onOpenWorkspace(target) }, Modifier.weight(1f))
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            EditorWorkspace.entries.forEach { target ->
                                WorkspaceShortcut(target, !isLoading, { onOpenWorkspace(target) }, Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HubSectionTitle(R.string.hub_recent_projects)
                }
                if (projects.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyProjectsState(isLoading || !projectsLoaded)
                    }
                } else {
                    items(projects, key = { it.project.root.absolutePath }) { recent ->
                        val project = recent.project
                        ProjectCard(
                            recent = recent,
                            showArt = showProjectArt,
                            enabled = !isLoading,
                            onOpen = { onOpenProject(project) },
                            onRename = { pendingRename = project },
                            onDelete = { pendingDelete = project },
                            onDuplicate = { onDuplicateProject(project) },
                            onExportar = {
                                exportando = arrayListOf(project.root.absolutePath, project.name, project.createdAt)
                                exportarLauncher.launch("${project.name}${Kernpack.EXTENSAO}")
                            }
                        )
                    }
                }
            }
        }
        statusMessage?.let { message ->
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).widthIn(max = 560.dp),
                containerColor = KenkernSurfaceHigh,
                contentColor = KenkernTextHigh,
                action = {
                    TextButton(onClick = onConsumeStatus) {
                        Text(stringResource(R.string.hub_ok), color = KenkernAmber)
                    }
                }
            ) { Text(message, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        }
    }

    if (showNewDialog) {
        NewProjectDialog(
            onDismiss = { showNewDialog = false },
            onCreate = { name ->
                showNewDialog = false
                onCreateProject(name)
            }
        )
    }

    pendingRename?.let { project ->
        RenameProjectDialog(
            project = project,
            onDismiss = { pendingRename = null },
            onRename = { newName ->
                val target = project
                pendingRename = null
                onRenameProject(target, newName)
            }
        )
    }

    pendingDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = KenkernSurfaceHigh,
            titleContentColor = KenkernTextHigh,
            textContentColor = KenkernTextMid,
            shape = RoundedCornerShape(16.dp),
            title = { Text(stringResource(R.string.hub_delete_title), fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    stringResource(R.string.hub_delete_text, project.name),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val toDelete = project
                    pendingDelete = null
                    onDeleteProject(toDelete)
                }) {
                    Text(stringResource(R.string.hub_delete), color = KenkernError, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.hub_cancel), color = KenkernTextMid)
                }
            }
        )
    }
}

/**
 * T0.1: seletor de idioma do app — Português / English / idioma do aparelho.
 * A escolha é gravada por AppLanguage e a Activity é RECREADA na hora
 * (attachBaseContext aplica o locale novo; o Hub inteiro recompõe traduzido).
 */
@Composable
private fun LanguageSelector() {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val atual = AppLanguage.currentTag(context)
    val escolhido = AppLanguage.chosen(context)

    Box {
        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Filled.Language,
                contentDescription = stringResource(R.string.hub_language_cd),
                tint = if (escolhido != null) KenkernAmber else KenkernTextMid
            )
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            containerColor = KenkernSurfaceHigh
        ) {
            fun marcar(tag: String?): String =
                if (escolhido == tag && (tag != null || escolhido == null)) " ✓" else ""
            DropdownMenuItem(
                text = { Text(stringResource(R.string.hub_language_pt) + marcar(AppLanguage.PT), fontSize = 13.sp, color = KenkernTextHigh) },
                onClick = { menuOpen = false; AppLanguage.apply(context, AppLanguage.PT) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.hub_language_en) + marcar(AppLanguage.EN), fontSize = 13.sp, color = KenkernTextHigh) },
                onClick = { menuOpen = false; AppLanguage.apply(context, AppLanguage.EN) }
            )
            HorizontalDivider(thickness = 1.dp, color = KenkernOutlineSoft)
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.hub_language_device) + marcar(null) +
                            " (" + (if (atual == AppLanguage.PT) stringResource(R.string.hub_language_pt) else stringResource(R.string.hub_language_en)) + ")",
                        fontSize = 13.sp, color = KenkernTextMid
                    )
                },
                onClick = { menuOpen = false; AppLanguage.apply(context, null) }
            )
        }
    }
}

@Composable
private fun HubSectionTitle(@StringRes title: Int) {
    Text(stringResource(title), color = KenkernTextHigh, fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
}

@Composable
private fun NewProjectButton(isLoading: Boolean, onCreate: () -> Unit) {
    Button(
        onClick = onCreate,
        enabled = !isLoading,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = KenkernAmber, contentColor = Color(0xFF1E1E1E)),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier.heightIn(min = 40.dp)
    ) {
        Text(stringResource(R.string.hub_new_project), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun WorkspaceShortcut(workspace: EditorWorkspace, enabled: Boolean, open: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = open,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, KenkernOutlineSoft),
        color = KenkernSurface,
        modifier = modifier.heightIn(min = 52.dp)
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val alpha = if (enabled) 1f else 0.55f
            Icon(workspace.icon, null, tint = KenkernAmber.copy(alpha = alpha), modifier = Modifier.size(18.dp))
            Text(stringResource(workspace.label), color = KenkernTextHigh.copy(alpha = alpha), fontSize = 13.sp)
        }
    }
}

@Composable
private fun EmptyProjectsState(isLoading: Boolean) {
    Text(
        stringResource(if (isLoading) R.string.hub_loading else R.string.hub_no_projects),
        fontSize = 13.sp,
        color = KenkernTextMid,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(20.dp)
    )
}

@Composable
private fun activityLabel(recent: RecentProject): String {
    val opened = recent.lastOpenedAt > 0L
    val instant = if (opened) recent.lastOpenedAt else recent.addedAt
    if (instant <= 0L) return recent.project.createdAt
    val locale = LocalConfiguration.current.locales[0]
    val date = remember(instant, locale) { DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(instant)) }
    return stringResource(if (opened) R.string.hub_last_opened else R.string.hub_added, date)
}

/**
 * Cartão de projeto: miniatura 16:9 à esquerda (placeholder: céu em gradiente
 * com silhueta de cubo, até haver captura real), nome em destaque, data
 * relativa e menu de três pontos. Toque no cartão abre o projeto.
 */
@Composable
private fun ProjectCard(
    recent: RecentProject,
    showArt: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onExportar: () -> Unit
) {
    val project = recent.project
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = KenkernSurface,
        border = BorderStroke(1.dp, KenkernOutlineSoft),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onOpen)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
        ) {
            // Miniatura 16:9 (placeholder até haver captura real)
            if (showArt) {
                Box(
                    Modifier
                        .size(width = 96.dp, height = 54.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF3A5A8C), Color(0xFFB8C9DC))
                            )
                        )
                ) {
                    Canvas(Modifier.fillMaxSize()) { drawCubeSilhouette(size.width, size.height) }
                }
                Spacer(Modifier.width(12.dp))
            }

            Column(Modifier.weight(1f)) {
                Text(
                    project.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KenkernTextHigh,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    activityLabel(recent),
                    fontSize = 12.sp,
                    color = KenkernTextLow,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box {
                IconButton(onClick = { menuOpen = true }, enabled = enabled, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.hub_menu_of, project.name),
                        tint = KenkernTextMid,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = KenkernSurfaceHigh
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.hub_open), fontSize = 13.sp, color = KenkernTextHigh) },
                        onClick = {
                            menuOpen = false
                            onOpen()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.hub_rename), fontSize = 13.sp, color = KenkernTextHigh) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.hub_export), fontSize = 13.sp, color = KenkernTextHigh) },
                        onClick = {
                            menuOpen = false
                            onExportar()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.hub_duplicate), fontSize = 13.sp, color = KenkernTextHigh) },
                        onClick = {
                            menuOpen = false
                            onDuplicate()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.hub_delete), fontSize = 13.sp, color = KenkernError) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

/** Silhueta de cubo isométrico para a miniatura (placeholder do viewport). */
private fun DrawScope.drawCubeSilhouette(w: Float, h: Float) {
    val cx = w * 0.5f
    val cy = h * 0.55f
    val s = h * 0.26f
    val dx = s * 0.87f

    fun poly(points: List<Offset>, color: Color) {
        val path = Path()
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) path.lineTo(points[i].x, points[i].y)
        path.close()
        drawPath(path, color)
    }

    val top = Offset(cx, cy - s)
    val ur = Offset(cx + dx, cy - s * 0.5f)
    val lr = Offset(cx + dx, cy + s * 0.5f)
    val bottom = Offset(cx, cy + s)
    val ll = Offset(cx - dx, cy + s * 0.5f)
    val ul = Offset(cx - dx, cy - s * 0.5f)
    val center = Offset(cx, cy)

    poly(listOf(top, ur, center, ul), Color(0xFF1E2A3A))
    poly(listOf(ul, center, bottom, ll), Color(0xFF141C28))
    poly(listOf(center, ur, lr, bottom), Color(0xFF25344A))
}

@Composable
private fun RenameProjectDialog(
    project: ProjectHandle,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var text by remember { mutableStateOf(project.name) }
    val valid = text.trim().length >= 2
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KenkernSurfaceHigh,
        titleContentColor = KenkernTextHigh,
        textContentColor = KenkernTextMid,
        shape = RoundedCornerShape(16.dp),
        title = { Text(stringResource(R.string.hub_rename_title), fontWeight = FontWeight.SemiBold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 60) text = it },
                label = { Text(stringResource(R.string.hub_project_name), fontSize = 12.sp) },
                singleLine = true,
                isError = text.isNotBlank() && !valid
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (valid) onRename(text.trim()) },
                enabled = valid && text.trim() != project.name
            ) { Text(stringResource(R.string.hub_rename), color = KenkernAmber, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.hub_cancel), color = KenkernTextMid) }
        }
    )
}

@Composable
private fun NewProjectDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val valid = name.trim().length >= 2
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KenkernSurfaceHigh,
        titleContentColor = KenkernTextHigh,
        textContentColor = KenkernTextMid,
        shape = RoundedCornerShape(16.dp),
        title = { Text(stringResource(R.string.hub_new_title), fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(
                    stringResource(R.string.hub_new_structure),
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = KenkernBackground,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.hub_new_folders),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = KenkernAmber.copy(alpha = 0.85f),
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 60) name = it },
                    label = { Text(stringResource(R.string.hub_project_name), fontSize = 12.sp) },
                    supportingText = {
                        Text(
                            if (name.isBlank()) stringResource(R.string.hub_name_hint)
                            else stringResource(R.string.hub_name_folder, ProjectManager.sanitize(name)),
                            fontSize = 12.sp
                        )
                    },
                    singleLine = true,
                    isError = name.isNotBlank() && !valid,
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (valid) onCreate(name.trim()) },
                enabled = valid
            ) { Text(stringResource(R.string.hub_create), color = KenkernAmber, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.hub_cancel), color = KenkernTextMid) }
        }
    )
}
