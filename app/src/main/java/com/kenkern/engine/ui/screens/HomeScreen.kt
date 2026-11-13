package com.kenkern.engine.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kenkern.engine.projects.ProjectHandle
import com.kenkern.engine.projects.ProjectManager
import com.kenkern.engine.ui.theme.KenkernAmber
import com.kenkern.engine.ui.theme.KenkernAmberBright
import com.kenkern.engine.ui.theme.KenkernError
import com.kenkern.engine.ui.theme.KenkernOutline
import com.kenkern.engine.ui.theme.KenkernSurface
import com.kenkern.engine.ui.theme.KenkernSurfaceHigh
import com.kenkern.engine.ui.theme.KenkernSurfaceOverlay
import com.kenkern.engine.ui.theme.KenkernTextLow
import com.kenkern.engine.ui.theme.KenkernTextMid

/**
 * HUB da KENKERN: vitrine profissional do estúdio.
 * Marca animada, AÇÕES primárias e NAVEGADOR de projetos recentes em cards.
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
    var entered by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) { entered = true }

    val glow = rememberInfiniteTransition(label = "glow")
    val glowAlpha by glow.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF0A0A0F),
                    0.35f to Color(0xFF101018),
                    0.75f to Color(0xFF14121C),
                    1f to Color(0xFF0A0A0F)
                )
            )
            .safeDrawingPadding()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(48.dp))

            // ---------- Marca ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(glowAlpha)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        "KENKERN",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 8.sp,
                        color = Color(0xFFF2F2F7)
                    )
                    Text(
                        "GAME ENGINE 3D  ·  MOBILE-FIRST",
                        fontSize = 10.sp,
                        letterSpacing = 3.sp,
                        color = KenkernTextLow
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "KOTLIN  ·  OPENGL ES 3.0  ·  NICKS .CK  ·  MALHAS .KERN",
                fontSize = 9.sp,
                letterSpacing = 2.sp,
                color = Color(0xFF54546A)
            )

            Spacer(Modifier.height(36.dp))

            // ---------- Ações ----------
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.widthIn(max = 340.dp)
            ) {
                Button(
                    onClick = { showNewDialog = true },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KenkernAmber,
                        contentColor = Color(0xFF241300)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Novo projeto", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        val mostRecent = projects.firstOrNull()
                        if (mostRecent != null) onOpenProject(mostRecent)
                    },
                    enabled = !isLoading && projects.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, KenkernOutline),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFC9C9DA)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Icon(Icons.Filled.Hub, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (projects.isEmpty()) "Abrir projeto" else "Continuar: ${projects.first().name}",
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(30.dp))

            // ---------- Projetos recentes ----------
            Column(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 340.dp)
                    .weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "PROJETOS RECENTES",
                        fontSize = 10.sp,
                        letterSpacing = 2.4.sp,
                        color = KenkernTextLow,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(10.dp))
                    HorizontalDivider(thickness = 1.dp, color = Color(0xFF26263A))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${projects.size}",
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        color = KenkernAmber,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (projects.isEmpty()) {
                    EmptyProjectsCard()
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(projects, key = { it.root.absolutePath }) { project ->
                            ProjectCard(
                                project = project,
                                onOpen = { onOpenProject(project) },
                                onDelete = { onDeleteProject(project) }
                            )
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }

            // ---------- Rodapé ----------
            Text(
                "v${ProjectManager.ENGINE_VERSION}  ·  Linkerf (dados)  ·  KENA/KELU (ganchos)",
                fontSize = 9.5.sp,
                letterSpacing = 1.sp,
                color = Color(0xFF4A4A60)
            )
            Spacer(Modifier.height(16.dp))
        }

        // ---------- Status / erro ----------
        AnimatedVisibility(
            visible = statusMessage != null,
            enter = fadeIn() + slideInVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            Snackbar(
                containerColor = KenkernSurfaceOverlay,
                contentColor = Color(0xFFF2F2F7),
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
}

/** Marca da engine: losango âmbar com "K" — simples, geométrica, de estúdio. */
@Composable
private fun BrandMark(glowAlpha: Float) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(64.dp)
            .graphicsLayer { shadowElevation = 24f * glowAlpha }
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        KenkernAmberBright.copy(alpha = glowAlpha),
                        KenkernAmber
                    )
                )
            )
    ) {
        Text(
            "K",
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF241300)
        )
    }
}

@Composable
private fun EmptyProjectsCard() {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = KenkernSurface.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, Color(0xFF232336))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 28.dp, horizontal = 20.dp)
        ) {
            Icon(
                Icons.Filled.Folder,
                contentDescription = null,
                tint = KenkernTextLow,
                modifier = Modifier.size(30.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Nenhum projeto ainda",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = KenkernTextMid
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Crie o primeiro com \"Novo projeto\".\nCada projeto ganha pastas assets/, scenes/,\nnicks/, scripts/, audio/, animations/,\nshaders/ e build/ + project.kenkern.",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = KenkernTextLow,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
        shape = RoundedCornerShape(12.dp),
        color = KenkernSurface,
        border = BorderStroke(1.dp, Color(0xFF26263A)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // Ícone do projeto
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(KenkernSurfaceHigh)
                    .border(1.dp, KenkernOutline.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            ) {
                Icon(
                    Icons.Filled.Folder,
                    contentDescription = null,
                    tint = KenkernAmber,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    project.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFEDEDF2),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "criado em ${project.createdAt}",
                    fontSize = 10.5.sp,
                    color = KenkernTextLow,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
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
        titleContentColor = Color(0xFFF2F2F7),
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
                    color = Color(0xFF0D0D13),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "assets/   scenes/   nicks/\nscripts/  audio/   animations/\nshaders/  build/\n→ project.kenkern  +  scenes/cena.ck",
                        fontSize = 10.5.sp,
                        lineHeight = 16.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = KenkernAmber.copy(alpha = 0.85f),
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 60) name = it },
                    label = { Text("Nome do projeto") },
                    supportingText = {
                        Text(
                            if (name.isBlank()) "Ex.: Meu Jogo 3D"
                            else "pasta: ${ProjectManager.sanitize(name)}",
                            fontSize = 10.sp
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
