package com.kenkern.engine.ui.explorador

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kenkern.engine.R
import com.kenkern.engine.editor.EditorViewModel
import com.kenkern.engine.projects.ReferenciasAssets
import com.kenkern.engine.ui.components.KButton
import com.kenkern.engine.ui.theme.KenkernError
import com.kenkern.engine.ui.theme.KenkernTextHigh
import com.kenkern.engine.ui.theme.KenkernTextLow
import com.kenkern.engine.ui.theme.KenkernTextMid

/**
 * F5.2 (v0.1.31) — PAINEL "PROBLEMAS": referências quebradas da cena atual
 * (Malha.path apontando arquivo inexistente), com "Corrigir…" (escolher o
 * arquivo certo entre os assets do projeto) e "Remover referência".
 */
@Composable
fun PainelProblemas(
    vm: EditorViewModel,
    revisao: Int,
    aoAtualizar: () -> Unit,
    modifier: Modifier = Modifier
) {
    var corrigindo by remember { mutableStateOf<ReferenciasAssets.Problema?>(null) }

    val problemas = remember(revisao) { vm.problemasReferencias() }

    Column(
        modifier
            .fillMaxSize()
            .background(com.kenkern.engine.ui.theme.KenkernSurface)
            .verticalScroll(rememberScrollState())
            .padding(6.dp)
    ) {
        if (problemas.isEmpty()) {
            Text(
                stringResource(R.string.explorador_sem_problemas),
                fontSize = 11.sp,
                color = com.kenkern.engine.ui.theme.KenkernTextMid
            )
        } else {
            for (p in problemas) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(KenkernError.copy(alpha = 0.10f))
                        .padding(6.dp)
                ) {
                    Text(
                        p.caminho,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = KenkernError,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        p.mensagem,
                        fontSize = 10.sp,
                        color = com.kenkern.engine.ui.theme.KenkernTextMid,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        KButton(
                            texto = stringResource(R.string.explorador_corrigir),
                            onClick = { corrigindo = p },
                            modifier = Modifier.height(40.dp)
                        )
                        KButton(
                            texto = stringResource(R.string.explorador_remover_ref),
                            onClick = {
                                vm.removerReferencia(p.nickId)
                                aoAtualizar()
                            },
                            modifier = Modifier.height(40.dp)
                        )
                    }
                }
            }
        }
    }

    corrigindo?.let { problema ->
        // "Corrigir…": escolher entre os assets EXISTENTES no projeto
        val candidatos = remember(problema) { vm.candidatosReferencia() }
        AlertDialog(
            onDismissRequest = { corrigindo = null },
            title = {
                Text(stringResource(R.string.explorador_corrigir), fontSize = 16.sp, color = KenkernTextHigh)
            },
            text = {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth()
                ) {
                    if (candidatos.isEmpty()) {
                        Text(
                            stringResource(R.string.explorador_pasta_vazia),
                            fontSize = 11.sp,
                            color = KenkernTextLow
                        )
                    }
                    for (c in candidatos) {
                        Text(
                            c,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = KenkernTextHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(com.kenkern.engine.ui.theme.KenkernSurfaceHigh)
                                .clickable {
                                    corrigindo = null
                                    vm.corrigirReferencia(problema.nickId, c)
                                    aoAtualizar()
                                }
                                .padding(vertical = 8.dp, horizontal = 6.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                KButton(
                    texto = stringResource(R.string.explorador_cancelar),
                    onClick = { corrigindo = null }
                )
            }
        )
    }
}
