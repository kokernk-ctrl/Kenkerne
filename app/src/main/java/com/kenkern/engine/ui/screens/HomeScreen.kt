package com.kenkern.engine.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.WarningAmber
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
import com.kenkern.engine.diag.KenkernLog
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * HUB da KENKERN (ETAPA B):
 *  - Marca: wordmark "Kenkern" (semibold, sem tracking) + versão discreta.
 *  - "Novo projeto" compacto (40dp) no cabeçalho, à direita; sem "+" redundante.
 *  - Cartões de recentes: miniatura 16:9 (placeholder céu + cubo), nome,
 *    data relativa e menu de três pontos (Abrir, Renomear, Apagar).
 *  - Paisagem: grade de 2 colunas (LazyVerticalGrid).
 *  - Estado vazio: mensagem curta + um único botão de criar.
 *  - Fundo plano #1E1E1E, sem gradientes decorativos.
 *
 * T0.1 (idiomas): TODAS as strings vêm de recursos (strings.xml/values-pt);
 *  - seletor de idioma (Português / English / idioma do aparelho) no
 *    cabeçalho — troca imediata via AppLanguage.apply (recreate da Activity).
 */
@Composable
fun HomeScreen(
    projects: List<ProjectHandle>,
    isLoading: Boolean,
    statusMessage: String?,
    onConsumeStatus: () -> Unit,
    onCreateProject: (String) -> Unit,
    onOpenProject: (ProjectHandle) -> Unit,
    onRenameProject: (ProjectHandle, String) -> Unit,
    onDeleteProject: (ProjectHandle) -> Unit,
    onAbrirDiagnostico: () -> Unit = {},
    onDuplicateProject: (ProjectHandle) -> Unit = {},
    onImportarKernpack: (android.net.Uri) -> Unit = {},
    onExportarProjeto: (ProjectHandle, android.net.Uri) -> Unit = { _, _ -> }
) {
    var showNewDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ProjectHandle?>(null) }
    var pendingRename by remember { mutableStateOf<ProjectHandle?>(null) }

    // SAF (F2.6): importar .kernpack (abrir) e exportar (criar documento)
    val importarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onImportarKernpack(uri) }
    var exportando by remember { mutableStateOf<ProjectHandle?>(null) }
    val exportarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val alvo = exportando
        exportando = null
        if (uri != null && alvo != null) onExportarProjeto(alvo, uri)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(KenkernBackground)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ---------- Cabeçalho (56dp): wordmark + versão + ação compacta ----------
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Column {
                    Text(
                        "Kenkern",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = KenkernTextHigh
                    )
                    Text(
                        "v${ProjectManager.ENGINE_VERSION}",
                        fontSize = 12.sp,
                        color = KenkernTextLow
                    )
                }
                Spacer(Modifier.weight(1f))
                // T0.1: SELETOR DE IDIOMA (en/pt/padrão do aparelho; troca imediata)
                LanguageSelector()
                // Importar .kernpack (SAF OPEN_DOCUMENT)
                IconButton(
                    onClick = { importarLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Filled.Folder,
                        contentDescription = stringResource(R.string.hub_import_cd),
                        tint = KenkernTextMid
                    )
                }
                // Diagnóstico: checagens da engine no aparelho (tela da Fase 2)
                IconButton(
                    onClick = onAbrirDiagnostico,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Filled.BugReport,
                        contentDescription = stringResource(R.string.hub_diagnostico_cd),
                        tint = KenkernTextMid
                    )
                }
                Button(
                    onClick = { showNewDialog = true },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KenkernAmber,
                        contentColor = Color(0xFF1E1E1E)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text(stringResource(R.string.hub_new_project), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            HorizontalDivider(thickness = 1.dp, color = KenkernOutlineSoft)

            // ---------- Grade de recentes (2 colunas em paisagem/largo) ----------
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val columns = if (maxWidth >= 600.dp) 2 else 1
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(16.dp, 12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(R.string.hub_recent_projects),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = KenkernTextMid
                        )
                    }

                    if (projects.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyProjectsState(isLoading, onCreate = { showNewDialog = true })
                        }
                    } else {
                        items(projects, key = { it.root.absolutePath }) { project ->
                            ProjectCard(
                                project = project,
                                onOpen = { onOpenProject(project) },
                                onRename = { pendingRename = project },
                                onDelete = { pendingDelete = project },
                                onDuplicate = { onDuplicateProject(project) },
                                onExportar = {
                                    exportando = project
                                    exportarLauncher.launch("${project.name}${Kernpack.EXTENSAO}")
                                }
                            )
                        }
                    }
                }
            }
        }

        // ---------- Status/erro ----------
        AnimatedVisibility(
            visible = statusMessage != null,
            enter = fadeIn() + slideInVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Snackbar(
                containerColor = KenkernSurfaceHigh,
                contentColor = KenkernTextHigh,
                action = {
                    TextButton(onClick = onConsumeStatus) {
                        Text(stringResource(R.string.hub_ok), color = KenkernAmber)
                    }
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = KenkernError,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        statusMessage ?: "",
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
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

/**
 * Data relativa curta localizada: "agora", "há 2 min", "ontem", "há 3 dias",
 * dd/MM/yy (a data absoluta usa o formato curto do locale corrente).
 */
@Composable
private fun relativeDate(created: String): String {
    val parsed = remember(created) {
        runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).parse(created)?.time
        }.onFailure { KenkernLog.w("Kenkern/HomeScreen", "data de criação do projeto ilegível — mostrando texto cru", it) }
            .getOrNull()
    }
    if (parsed == null) return created
    val diffMinutes = (System.currentTimeMillis() - parsed) / 60000L
    return when {
        diffMinutes < 1L -> stringResource(R.string.hub_time_now)
        diffMinutes < 60L -> stringResource(R.string.hub_time_min, diffMinutes)
        diffMinutes < 60L * 24L -> stringResource(R.string.hub_time_hour, diffMinutes / 60L)
        diffMinutes < 60L * 48L -> stringResource(R.string.hub_time_yesterday)
        diffMinutes < 60L * 24L * 7L -> stringResource(R.string.hub_time_days, diffMinutes / (60L * 24L))
        else -> remember(parsed) {
            SimpleDateFormat.getDateInstance(java.text.SimpleDateFormat.SHORT, Locale.getDefault()).format(Date(parsed))
        }
    }
}

/** Estado vazio: mensagem curta + um único botão de criar. */
@Composable
private fun EmptyProjectsState(isLoading: Boolean, onCreate: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp)
    ) {
        Text(
            if (isLoading) stringResource(R.string.hub_loading) else stringResource(R.string.hub_no_projects),
            fontSize = 13.sp,
            color = KenkernTextMid
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onCreate,
            enabled = !isLoading,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = KenkernAmber,
                contentColor = Color(0xFF1E1E1E)
            ),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.height(40.dp)
        ) {
            Text(stringResource(R.string.hub_create_project), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Cartão de projeto: miniatura 16:9 à esquerda (placeholder: céu em gradiente
 * com silhueta de cubo, até haver captura real), nome em destaque, data
 * relativa e menu de três pontos. Toque no cartão abre o projeto.
 */
@Composable
private fun ProjectCard(
    project: ProjectHandle,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onExportar: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = KenkernSurface,
        border = BorderStroke(1.dp, KenkernOutlineSoft),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onOpen)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
        ) {
            // Miniatura 16:9 (placeholder até haver captura real)
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
                    relativeDate(project.createdAt),
                    fontSize = 12.sp,
                    color = KenkernTextLow,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
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
    var name by remember { mutableStateOf("") }
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
