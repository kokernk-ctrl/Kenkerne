package com.kenkern.engine.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kenkern.engine.editor.EditorViewModel
import com.kenkern.engine.projects.ProjectHandle
import com.kenkern.engine.projects.ProjectManager
import com.kenkern.engine.ui.screens.EditorScreen
import com.kenkern.engine.ui.screens.HomeScreen

/**
 * Raiz do app: navegação simples entre a tela inicial e o editor
 * (botão de volta do sistema sai do editor salvando a cena).
 */
@Composable
fun KenkernAppRoot() {
    val context = LocalContext.current
    val vm: EditorViewModel = viewModel()
    val currentProject by vm.project

    var projectsVersion by remember { mutableIntStateOf(0) }
    val projects = remember(projectsVersion, currentProject) {
        ProjectManager.listProjects(context)
    }

    BackHandler(enabled = currentProject != null) {
        vm.exitProject()
    }

    if (currentProject == null) {
        HomeScreen(
            projects = projects,
            onCreateProject = { name ->
                val handle = ProjectManager.createProject(context, name)
                if (handle != null) {
                    projectsVersion++
                    vm.enterProject(handle)
                }
            },
            onOpenProject = { handle ->
                projectsVersion++
                vm.enterProject(handle)
            },
            onDeleteProject = { handle ->
                ProjectManager.deleteProject(handle)
                projectsVersion++
            }
        )
    } else {
        EditorScreen(vm, onExit = { vm.exitProject() })
    }
}
