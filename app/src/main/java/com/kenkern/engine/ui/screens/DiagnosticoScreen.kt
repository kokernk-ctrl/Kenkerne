package com.kenkern.engine.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kenkern.engine.R
import com.kenkern.engine.diag.Diagnostico
import com.kenkern.engine.diag.KenkernLog
import com.kenkern.engine.ui.theme.KenkernAmber
import com.kenkern.engine.ui.theme.KenkernBackground
import com.kenkern.engine.ui.theme.KenkernError
import com.kenkern.engine.ui.theme.KenkernSuccess
import com.kenkern.engine.ui.theme.KenkernOutlineSoft
import com.kenkern.engine.ui.theme.KenkernSurface
import com.kenkern.engine.ui.theme.KenkernTextHigh
import com.kenkern.engine.ui.theme.KenkernTextLow
import com.kenkern.engine.ui.theme.KenkernTextMid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * T0.1: nome da checagem LOCALIZADO por id (o Diagnostico em si roda sem
 * Context — o nome exibido vem do recurso do idioma atual).
 */
@Composable
private fun nomeChecagem(id: String): String = stringResource(
    when (id) {
        "nativo" -> R.string.diag_check_nativo
        "etc2-nativo" -> R.string.diag_check_etc2_nativo
        "malha-nativa" -> R.string.diag_check_malha_nativa
        "ck" -> R.string.diag_check_ck
        "kern" -> R.string.diag_check_kern
        "gltf" -> R.string.diag_check_gltf
        "fbx" -> R.string.diag_check_fbx
        "importar" -> R.string.diag_check_importar
        "kimg" -> R.string.diag_check_kimg
        "etc2" -> R.string.diag_check_etc2
        "tex-cena" -> R.string.diag_check_tex_cena
        "cena" -> R.string.diag_check_cena
        "astc" -> R.string.diag_check_astc
        "picking" -> R.string.diag_check_picking
        "toque" -> R.string.diag_check_toque
        "shaders" -> R.string.diag_check_shaders
        "buffers" -> R.string.diag_check_buffers
        "glcaps" -> R.string.diag_check_glcaps
        "memoria" -> R.string.diag_check_memoria
        "release" -> R.string.diag_check_release
        else -> R.string.diag_check_ck
    }
)

/**
 * TELA DIAGNÓSTICO (Hub): roda as checagens da engine NO APARELHO e mostra
 * APROVADO/FALHOU com o motivo — o usuário abre, roda e manda o print.
 *
 * Regras: nada roda na composição (só no botão, em thread de fundo); sem
 * trabalho por frame; alvos de toque de 48dp; funciona em paisagem com insets.
 */
@Composable
fun DiagnosticoScreen(onVoltar: () -> Unit) {
    val context = LocalContext.current
    val diag = remember { Diagnostico(context.applicationContext) }
    val horaFmt = remember { java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US) }
    val resultados = remember {
        mutableStateListOf<Diagnostico.Checagem>().apply { addAll(diag.novaLista()) }
    }
    var rodando by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun rodar() {
        if (rodando) return
        rodando = true
        // zera os resultados da rodada anterior
        val zerada = diag.novaLista()
        resultados.clear()
        resultados.addAll(zerada)
        scope.launch(Dispatchers.Default) {
            try {
                diag.executar { atualizada ->
                    // atualiza a linha correspondente (por id) na lista observável
                    val idx = resultados.indexOfFirst { it.id == atualizada.id }
                    if (idx >= 0) resultados[idx] = atualizada
                }
            } finally {
                withContext(Dispatchers.Main) { rodando = false }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(KenkernBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ---------- Cabeçalho (mínimo 48dp — D5: altura FIXA de 56dp cortava o
        // subtítulo quando a fonte do sistema crescia; agora o cabeçalho CRESCE,
        // título e subtítulo em 1 linha cada) ----------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            IconButton(onClick = onVoltar, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.editor_back_hub),
                    tint = KenkernTextHigh
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.diag_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = KenkernTextHigh,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    stringResource(R.string.diag_subtitle),
                    fontSize = 11.sp,
                    color = KenkernTextLow,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = { rodar() },
                enabled = !rodando,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KenkernAmber,
                    contentColor = Color(0xFF1E1E1E)
                ),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier
                    .height(40.dp)
                    .padding(end = 8.dp)
            ) {
                Text(
                    if (rodando) stringResource(R.string.diag_running) else stringResource(R.string.diag_run),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = KenkernOutlineSoft)

        // ---------- Resultados ----------
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp, 12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            val aprovadas = resultados.count { it.status == Diagnostico.Status.APROVADO }
            val falharam = resultados.count { it.status == Diagnostico.Status.FALHOU }
            item {
                Text(
                    if (rodando) stringResource(R.string.diag_executing, aprovadas)
                    else if (aprovadas + falharam == 0) stringResource(R.string.diag_tap_run)
                    else stringResource(R.string.diag_result, aprovadas, falharam, resultados.size),
                    fontSize = 12.sp,
                    color = if (falharam > 0) KenkernError else KenkernTextMid
                )
            }
            items(resultados.size) { i ->
                val r = resultados[i]
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(KenkernSurface, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    // Indicador de status: círculo colorido + letra
                    val (cor, letra) = when (r.status) {
                        Diagnostico.Status.PENDENTE -> KenkernTextLow to "•"
                        Diagnostico.Status.RODANDO -> KenkernAmber to "…"
                        Diagnostico.Status.APROVADO -> KenkernSuccess to "OK"
                        Diagnostico.Status.FALHOU -> KenkernError to "X"
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(28.dp)
                            .background(cor.copy(alpha = 0.18f), CircleShape)
                    ) {
                        Text(
                            letra,
                            fontSize = if (letra.length > 1) 11.sp else 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = cor,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(nomeChecagem(r.id), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = KenkernTextHigh)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            r.detalhe,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            fontFamily = FontFamily.Monospace,
                            color = KenkernTextMid
                        )
                    }
                }
            }

            // ---------- CONSOLE (regra do realinhamento) ----------
            // Todo runCatching de render/formato/carregamento registra aqui via
            // KenkernLog (além do logcat e do overlay do editor).
            val consoleRegs = KenkernLog.registros().asReversed()
            item {
                Text(
                    if (consoleRegs.isEmpty()) stringResource(R.string.diag_console_empty)
                    else stringResource(R.string.diag_console_count, consoleRegs.size),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (consoleRegs.any { it.nivel == KenkernLog.Nivel.E }) KenkernError else KenkernTextMid,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(consoleRegs.size) { i ->
                val r = consoleRegs[i]
                Text(
                    r.linha(horaFmt),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (r.nivel == KenkernLog.Nivel.E) KenkernError else KenkernTextMid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(KenkernSurface, RoundedCornerShape(6.dp))
                        .padding(8.dp)
                )
            }
        }
    }
}
