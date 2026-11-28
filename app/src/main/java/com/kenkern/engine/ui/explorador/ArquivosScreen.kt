package com.kenkern.engine.ui.explorador

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kenkern.engine.R
import com.kenkern.engine.editor.EditorViewModel
import com.kenkern.engine.ui.components.KIconButton
import com.kenkern.engine.ui.theme.KenkernBackground
import com.kenkern.engine.ui.theme.KenkernTextHigh

/**
 * F3 (v0.1.31) — ABA "Arquivos" do seletor Cena | UI | Código | Arquivos:
 * o explorador em TELA CHEIA (o mesmo ExploradorConteudo do painel acoplado
 * "Projeto"; o estado é compartilhado via painelStore/ViewModel).
 */
@Composable
fun ArquivosScreen(
    vm: EditorViewModel,
    onVoltarEditor: () -> Unit,
    onAbrirLua: (String) -> Unit,
    onAbrirTelar: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(KenkernBackground)) {
        Surface(color = com.kenkern.engine.ui.theme.KenkernSurface, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)
            ) {
                KIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.editor_back_hub),
                    onClick = onVoltarEditor
                )
                Text(
                    stringResource(R.string.explorador_aba_arquivos),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KenkernTextHigh
                )
            }
        }
        ExploradorConteudo(
            vm = vm,
            compacto = false,
            onAbrirLua = onAbrirLua,
            onAbrirKui = { nome ->
                vm.definirUiDocAlvo(nome)
                onAbrirTelar()
            },
            onCarregarCena = { rel -> vm.carregarCenaDoArquivo(rel, onOk = {}, onErro = {}) },
            onImportarModelo = { rel -> vm.importarModeloLocal(rel, onOk = {}, onErro = {}) },
            onAbrirImagem = { /* pré-visualização: item do explorador mostra a miniatura ampliada no toque longo futuro (U6) */ },
            modifier = Modifier.fillMaxSize()
        )
    }
}
