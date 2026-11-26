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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kenkern.engine.R
import com.kenkern.engine.ui.components.KButton
import com.kenkern.engine.ui.components.KIconButton
import com.kenkern.engine.diag.DiagNomesChecagem
import com.kenkern.engine.diag.DiagRelatorioTexto
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * T0.1: nome da checagem LOCALIZADO por id (o Diagnostico em si roda sem
 * Context — o nome exibido vem do recurso do idioma atual).
 * S1 (v0.1.24): o mapeamento saiu do `when` daqui para [DiagNomesChecagem]
 * (PURO, JVM-testável — o bug S1a era o `else` que reutilizava o título
 * ".ck (cena)" para materiais/luz/layout/joystick).
 */
@Composable
private fun nomeChecagem(id: String): String = stringResource(DiagNomesChecagem.resource(id))

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
    var jobRodada by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // S1 (v0.1.24): timestamp (SystemClock) da última cópia do relatório —
    // usado para mostrar a confirmação por ~2,5 s sem bloquear a UI.
    var copiadoAte by remember { mutableStateOf(0L) }
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(copiadoAte) {
        if (copiadoAte > 0L) {
            delay(2_500)
            copiadoAte = 0L
        }
    }

    fun rodar() {
        if (rodando) return
        rodando = true
        // zera os resultados da rodada anterior
        val zerada = diag.novaLista()
        resultados.clear()
        resultados.addAll(zerada)
        jobRodada = scope.launch {
            try {
                // E2 (v0.1.18): executar é SUSPENSE — cada checagem tem timeout de 30 s,
                // tempo próprio na tela e cancelamento pelo botão (Job + diag.cancelar).
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

    // E2: botão CANCELAR — interrompe a checagem corrente (interrupção de thread
    // + laços consultam diag.cancelado) e marca o resto como CANCELADA.
    fun cancelar() {
        diag.cancelar()
        jobRodada?.cancel()
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
            KIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.editor_back_hub),
                onClick = onVoltar
            )
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
            // S1 (v0.1.24): COPIAR RELATÓRIO — o texto COMPLETO das checagens
            // (título + estado + tempo + detalhe inteiro; FALHAS no topo) vai
            // para a área de transferência, pronto para colar em qualquer app.
            KButton(
                texto = stringResource(R.string.diag_copy_report),
                onClick = {
                    val relatorio = DiagRelatorioTexto.montar(resultados) { id ->
                        context.getString(DiagNomesChecagem.resource(id))
                    }
                    clipboard.setText(AnnotatedString(relatorio))
                    copiadoAte = android.os.SystemClock.elapsedRealtime()
                },
                modifier = Modifier
                    .height(44.dp)
                    .padding(end = 8.dp)
            )
            // P2 (v0.1.25): primário âmbar para Rodar; Cancelar em superficie
            // escura (o estado de erro fica na cor do texto via KButton padrão).
            KButton(
                texto = if (rodando) stringResource(R.string.diag_cancel)
                else stringResource(R.string.diag_run),
                onClick = { if (rodando) cancelar() else rodar() },
                primario = !rodando,
                modifier = Modifier
                    .height(44.dp)
                    .padding(end = 8.dp)
            )
        }
        HorizontalDivider(thickness = 1.dp, color = KenkernOutlineSoft)

        // S1 (v0.1.24): confirmação da cópia (some sozinha em 2,5 s).
        if (copiadoAte > 0L) {
            Text(
                stringResource(R.string.diag_report_copied),
                fontSize = 11.sp,
                color = KenkernSuccess,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

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
            // S1 (v0.1.24): FALHAS NO TOPO da lista (a ordem de EXIBIÇÃO;
            // a lista em si segue o catálogo para a atualização por id).
            val ordenados = DiagRelatorioTexto.ordenar(resultados)
            items(ordenados.size) { i ->
                val r = ordenados[i]
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
                        Diagnostico.Status.CANCELADA -> KenkernTextLow to "–"
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(nomeChecagem(r.id), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = KenkernTextHigh, modifier = Modifier.weight(1f))
                            // E2: tempo da checagem (ms; s quando ≥ 1 s; limite estourado = 30 s)
                            if (r.tempoMs >= 0) {
                                Text(
                                    if (r.tempoMs >= 1_000L) stringResource(R.string.diag_check_time_s, r.tempoMs / 1000f)
                                    else stringResource(R.string.diag_check_time_ms, r.tempoMs),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = KenkernTextLow,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
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
