package com.kenkern.engine.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kenkern.engine.projects.ProjectHandle
import com.kenkern.engine.projects.ProjectManager
import com.kenkern.engine.ui.theme.KenkernAmber
import com.kenkern.engine.ui.theme.KenkernBackground
import com.kenkern.engine.ui.theme.KenkernError
import com.kenkern.engine.ui.theme.KenkernOutline
import com.kenkern.engine.ui.theme.KenkernOutlineSoft
import com.kenkern.engine.ui.theme.KenkernSurface
import com.kenkern.engine.ui.theme.KenkernSurfaceHigh
import com.kenkern.engine.ui.theme.KenkernSurfaceOverlay
import com.kenkern.engine.ui.theme.KenkernTextHigh
import com.kenkern.engine.ui.theme.KenkernTextLow
import com.kenkern.engine.ui.theme.KenkernTextMid

/**
 * HUB da KENKERN: funcional e direto.
 *  - Cabeçalho compacto (marca + versão + "Novo projeto") — sem legendas de tecnologia.
 *  - Lista de projetos recentes imediatamente visível e ROLÁVEL (paisagem inclusive):
 *    todo o conteúdo abaixo do cabeçalho vive numa LazyColumn.
 *  - Apagar pede confirmação e remove o projeto do disco de verdade.
 */
@Composable
fun HomeScreen(
    projects: List<ProjectHandle>,
    isLoading: Boolean,
    statusMessage: String?,
    onConsumeStatus: () -> Unit,
    onCreateProject: (String) -> Unit,
    onOpenProject: (ProjectHandle) -> Unit,
    onDeleteProject: (ProjectHandle) -> Unit
) {
    var showNewDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ProjectHandle?>(null) }

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
            // ---------- Cabeçalho fixo e compacto (56dp) ----------
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp)
            ) {
                BrandMark()
                Spacer(Modifier.width(12.dp))
                Text(
                    "KENKERN",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = KenkernTextHigh
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = KenkernSurfaceHigh
                ) {
                    Text(
                        "v${ProjectManager.ENGINE_VERSION}",
                        fontSize = 11.sp,
                        color = KenkernTextMid,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { showNewDialog = true },
                    enabled = !isLoading,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = KenkernAmber,
                        contentColor = Color(0xFF241300)
                    )
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Novo projeto",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = KenkernOutlineSoft)

            // ---------- Conteúdo rolável (paisagem inclusive) ----------
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(16.dp, 12.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                item {
                    Button(
                        onClick = { showNewDialog = true },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KenkernAmber,
                            contentColor = Color(0xFF241300)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Novo projeto", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Projetos recentes",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = KenkernTextMid
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${projects.size}",
                            fontSize = 12.sp,
                            color = KenkernAmber,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (projects.isEmpty()) {
                    item { EmptyProjectsCard(isLoading) }
                } else {
                    items(projects, key = { it.root.absolutePath }) { project ->
                        ProjectCard(
                            project = project,
                            onOpen = { onOpenProject(project) },
                            onDelete = { pendingDelete = project }
                        )
                    }
                }
                item { Spacer(Modifier.height(4.dp)) }
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
                containerColor = KenkernSurfaceOverlay,
                contentColor = KenkernTextHigh,
                action = {
                    TextButton(onClick = onConsumeStatus) { Text("OK", color = KenkernAmber) }
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

    pendingDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = KenkernSurfaceHigh,
            titleContentColor = KenkernTextHigh,
            textContentColor = KenkernTextMid,
            shape = RoundedCornerShape(16.dp),
            title = { Text("Apagar projeto", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "Apagar \"${project.name}\" e todos os arquivos dele? " +
                        "Esta ação não pode ser desfeita.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val toDelete = project
                    pendingDelete = null
                    onDeleteProject(toDelete)
                }) {
                    Text("Apagar", color = KenkernError, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancelar", color = KenkernTextMid)
                }
            }
        )
    }
}

/** Marca da engine: quadrado âmbar com "K", estático e discreto. */
@Composable
private fun BrandMark() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(KenkernAmber)
    ) {
        Text(
            "K",
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF241300)
        )
    }
}

@Composable
private fun EmptyProjectsCard(isLoading: Boolean) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = KenkernSurface,
        border = BorderStroke(1.dp, KenkernOutlineSoft)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 16.dp)
        ) {
            Icon(
                Icons.Filled.Folder,
                contentDescription = null,
                tint = KenkernTextLow,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Nenhum projeto ainda",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = KenkernTextMid
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (isLoading) "Carregando..." else "Toque em \"Novo projeto\" para criar o primeiro.",
                fontSize = 12.sp,
                color = KenkernTextLow,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ProjectCard(
    project: ProjectHandle,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = KenkernSurface,
        border = BorderStroke(1.dp, KenkernOutlineSoft),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onOpen)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(KenkernSurfaceHigh)
                    .border(1.dp, KenkernOutline.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            ) {
                Icon(
                    Icons.Filled.Folder,
                    contentDescription = null,
                    tint = KenkernAmber,
                    modifier = Modifier.size(16.dp)
                )
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
                    "criado em ${project.createdAt}",
                    fontSize = 12.sp,
                    color = KenkernTextLow,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = "Apagar ${project.name}",
                    tint = KenkernTextLow,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
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
        title = { Text("Novo projeto", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(
                    "A estrutura padrão da engine será criada:",
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = KenkernBackground,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "assets/   scenes/   nicks/\nscripts/  audio/   animations/\nshaders/  build/\n→ project.kenkern  +  scenes/cena.ck",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = KenkernAmber.copy(alpha = 0.85f),
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 60) name = it },
                    label = { Text("Nome do projeto", fontSize = 12.sp) },
                    supportingText = {
                        Text(
                            if (name.isBlank()) "Ex.: Meu Jogo 3D"
                            else "pasta: ${ProjectManager.sanitize(name)}",
                            fontSize = 12.sp
                        )
                    },
                    singleLine = true,
                    isError = name.isNotBlank() && !valid,
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (valid) onCreate(name.trim()) },
                enabled = valid
            ) { Text("Criar", color = KenkernAmber, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = KenkernTextMid) }
        }
    )
}
