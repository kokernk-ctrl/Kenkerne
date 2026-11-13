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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kenkern.engine.projects.ProjectHandle
import com.kenkern.engine.projects.ProjectManager

/**
 * Tela inicial da KENKERN: fundo escuro com gradiente sutil, título animado,
 * botões "Novo projeto" / "Abrir projeto" e lista de projetos recentes.
 */
@Composable
fun HomeScreen(
    projects: List<ProjectHandle>,
    onCreateProject: (String) -> Unit,
    onOpenProject: (ProjectHandle) -> Unit,
    onDeleteProject: (ProjectHandle) -> Unit
) {
    var showNewDialog by remember { mutableStateOf(false) }
    var showList by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { entered = true }

    val glow = rememberInfiniteTransition(label = "glow")
    val glowAlpha by glow.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    val glowScale by glow.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(1700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowScale"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0B0B12),
                        Color(0xFF16141F),
                        Color(0xFF1C1724),
                        Color(0xFF0B0B12)
                    )
                )
            )
            .safeDrawingPadding()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.weight(0.85f))

            // Título animado
            Text(
                "Kenkern",
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp,
                color = Color(0xFFFFA940).copy(alpha = glowAlpha),
                modifier = Modifier.graphicsLayer {
                    scaleX = glowScale
                    scaleY = glowScale
                }
            )
            Text(
                "ENGINE 3D MOBILE-FIRST  ·  KOTLIN  ·  FASE 1",
                fontSize = 11.sp,
                letterSpacing = 2.sp,
                color = Color(0xFF8D8DA0)
            )

            Spacer(Modifier.weight(0.8f))

            Button(
                onClick = { showNewDialog = true },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFA940),
                    contentColor = Color(0xFF201200)
                ),
                modifier = Modifier
                    .width(260.dp)
                    .height(52.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Novo projeto", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = { showList = !showList },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .width(260.dp)
                    .height(52.dp)
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (showList) "Fechar lista" else "Abrir projeto", fontSize = 15.sp)
            }

            AnimatedVisibility(
                visible = showList,
                enter = fadeIn() + slideInVertically { it / 3 },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF15151F).copy(alpha = 0.92f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, Color(0xFF3C3C4E)
                    ),
                    modifier = Modifier.width(320.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "PROJETOS RECENTES",
                            fontSize = 10.sp,
                            letterSpacing = 2.sp,
                            color = Color(0xFF8D8DA0)
                        )
                        Spacer(Modifier.height(8.dp))
                        if (projects.isEmpty()) {
                            Text(
                                "Nenhum projeto ainda.\nCrie o primeiro com \"Novo projeto\".",
                                fontSize = 12.sp,
                                color = Color(0xFF8D8DA0)
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.height(220.dp)
                            ) {
                                items(projects, key = { it.root.absolutePath }) { project ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenProject(project) }
                                            .padding(vertical = 6.dp, horizontal = 4.dp)
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                project.name,
                                                fontSize = 14.sp,
                                                color = Color(0xFFEDEDF2)
                                            )
                                            Text(
                                                project.createdAt,
                                                fontSize = 10.sp,
                                                color = Color(0xFF8D8DA0)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDeleteProject(project) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = "Apagar ${project.name}",
                                                tint = Color(0xFFB0483F),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1.2f))

            Text(
                "v${ProjectManager.ENGINE_VERSION}  ·  Nicks .ck  ·  Malhas .KERN  ·  Linkerf",
                fontSize = 10.sp,
                color = Color(0xFF62627A)
            )
            Spacer(Modifier.height(20.dp))
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

@Composable
private fun NewProjectDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo projeto") },
        text = {
            Column {
                Text(
                    "Serão criadas as pastas assets/, scenes/, nicks/, scripts/, audio/, animations/, shaders/ e build/, além do project.kenkern.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do projeto") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("Criar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
