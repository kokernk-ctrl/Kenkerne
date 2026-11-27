package com.kenkern.engine.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kenkern.engine.editor.EditorViewModel
import com.kenkern.engine.projects.ProjectHandle
import com.kenkern.engine.ui.screens.CrashReportDialog
import com.kenkern.engine.ui.screens.DiagnosticoScreen
import com.kenkern.engine.ui.screens.EditorScreen
import com.kenkern.engine.ui.screens.HomeScreen
import com.kenkern.engine.ui.screens.CrashReportStore

/**
 * Raiz do app: navegação simples entre a tela inicial e o editor.
 * Todo I/O de projeto passa pelo ViewModel (background + tratamento de erro).
 */
@Composable
fun KenkernAppRoot() {
    val context = LocalContext.current
    val vm: EditorViewModel = viewModel()
    val currentProject by vm.project

    // DIAGNÓSTICO DE CRASH: ao iniciar o Hub, se a última sessão terminou em
    // crash (crash_logs/last_crash.txt existe), mostra o relatório — versão,
    // thread, stack trace e a última etapa alcançada na abertura do editor.
    // Estado aqui na raiz (vive enquanto o app vive): aparece 1x por início,
    // não reaparece ao voltar do editor para o Hub.
    var showCrashReport by remember { mutableStateOf(CrashReportStore.hasPendingReport(context)) }
    // DIAGNÓSTICO (Fase 2): tela de checagens da engine, acessível pelo Hub.
    var showDiagnostico by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refreshProjects() }

    BackHandler(enabled = currentProject != null) {
        vm.exitProject()
    }

    if (currentProject == null) {
        if (showDiagnostico) {
            BackHandler { showDiagnostico = false }
            DiagnosticoScreen(onVoltar = { showDiagnostico = false })
        } else {
            HomeScreen(
                projects = vm.projects.value,
                isLoading = vm.isLoading.value,
                statusMessage = vm.statusMessage.value,
                onConsumeStatus = { vm.consumeStatus() },
                onCreateProject = { name, template, storageId, pastaPai ->
                    vm.createAndEnterProject(name, template, storageId, pastaPai) },
                onMoverProject = { handle, paraAparelho -> vm.moverProjeto(handle, paraAparelho) },
                onOpenProject = { handle -> vm.openProject(handle) },
                onRenameProject = { handle, name -> vm.renameProject(handle, name) },
                onDeleteProject = { handle -> vm.deleteProject(handle) },
                onAbrirDiagnostico = { showDiagnostico = true },
                onDuplicateProject = { handle -> vm.duplicateProject(handle) },
                onImportarKernpack = { uri -> vm.importarKernpack(uri) },
                onExportarProjeto = { handle, uri -> vm.exportarProjetoPara(handle, uri) }
            )
        }
    } else {
        EditorScreen(vm, onExit = { vm.exitProject() })
    }

    if (showCrashReport) {
        CrashReportDialog(
            onDismiss = { showCrashReport = false },
            onDelete = {
                CrashReportStore.clearReports(context)
                showCrashReport = false
            }
        )
    }
}
