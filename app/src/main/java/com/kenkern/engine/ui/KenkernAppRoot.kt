package com.kenkern.engine.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kenkern.engine.editor.EditorViewModel
import com.kenkern.engine.projects.ProjectHandle
import com.kenkern.engine.ui.screens.EditorScreen
import com.kenkern.engine.ui.screens.HomeScreen

/**
 * Raiz do app: navegação simples entre a tela inicial e o editor.
 * Todo I/O de projeto passa pelo ViewModel (background + tratamento de erro).
 */
@Composable
fun KenkernAppRoot() {
    val context = LocalContext.current
    val vm: EditorViewModel = viewModel()
    val currentProject by vm.project

    LaunchedEffect(Unit) { vm.refreshProjects() }

    BackHandler(enabled = currentProject != null) {
        vm.exitProject()
    }

    if (currentProject == null) {
        HomeScreen(
            projects = vm.projects.value,
            isLoading = vm.isLoading.value,
            statusMessage = vm.statusMessage.value,
            onConsumeStatus = { vm.consumeStatus() },
            onCreateProject = { name -> vm.createAndEnterProject(name) },
            onOpenProject = { handle -> vm.openProject(handle) },
            onRenameProject = { handle, name -> vm.renameProject(handle, name) },
            onDeleteProject = { handle -> vm.deleteProject(handle) }
        )
    } else {
        EditorScreen(vm, onExit = { vm.exitProject() })
    }
}
