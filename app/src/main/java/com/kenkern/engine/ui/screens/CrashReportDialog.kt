package com.kenkern.engine.ui.screens

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.kenkern.engine.diag.BootTrace
import com.kenkern.engine.ui.theme.KenkernAmber
import com.kenkern.engine.ui.theme.KenkernBackground
import com.kenkern.engine.ui.theme.KenkernError
import com.kenkern.engine.ui.theme.KenkernSurfaceHigh
import com.kenkern.engine.ui.theme.KenkernTextHigh
import com.kenkern.engine.ui.theme.KenkernTextMid
import java.io.File

/**
 * Armazém/leitura do relatório de crash exibido AO INICIAR O HUB quando existe
 * `filesDir/crash_logs/last_crash.txt` (gravado pelo KenkernApplication).
 * Mantido separado do composable para não confundir nomes no pacote.
 */
object CrashReportStore {

    private const val CRASH_DIR = "crash_logs"
    private const val CRASH_FILE = "last_crash.txt"

    /** Arquivo de crash no mesmo formato que o KenkernApplication grava. */
    private fun crashFile(context: Context): File =
        File(File(context.filesDir, CRASH_DIR), CRASH_FILE)

    /** True se existe relatório de crash pendente (chamado 1x na composição raiz). */
    fun hasPendingReport(context: Context): Boolean =
        runCatching { crashFile(context).isFile }.getOrDefault(false)

    /** Apaga o relatório de crash e o marcador de boot (botão "Apagar"). */
    fun clearReports(context: Context) {
        runCatching { crashFile(context).delete() }
        runCatching { BootTrace.file(context).delete() }
    }

    /** Relatório completo: crash (versão/thread/stack) + etapas do boot_trace. */
    fun buildReport(context: Context): String = buildString {
        val crash = runCatching { crashFile(context).readText() }.getOrNull()
        if (crash.isNullOrBlank()) {
            appendLine("(arquivo de crash ausente ou ilegível)")
        } else {
            append(crash.trimEnd())
            appendLine()
        }
        appendLine()
        appendLine("— última etapa alcançada na abertura do editor —")
        appendLine(BootTrace.lastStep(context) ?: "(não registrada)")
        val trace = runCatching { BootTrace.file(context).readText() }.getOrNull()
        if (!trace.isNullOrBlank()) {
            appendLine()
            appendLine("— etapas registradas (boot_trace) —")
            append(trace.trimEnd())
            appendLine()
        }
    }
}

/**
 * Diagnóstico de crash exibido AO INICIAR O HUB: texto rolável com versão,
 * thread, exceção e stack trace, mais a última etapa alcançada na abertura
 * do editor (boot_trace) — que localiza crash nativo, que o handler de
 * exceção não captura.
 *
 * Correção de layout (só este arquivo mudou; visual/cores/fontes mantidos):
 *  - Área rolável: o limite fixo de 360.dp virou altura RELATIVA à tela
 *    (45% de screenHeightDp) — em paisagem ela encolhe e o Row de botões
 *    não sai mais da tela; em telas altas ela cresce até o mesmo teto.
 *  - Botões Copiar/Apagar num Row FIXO abaixo da área rolável (slot de
 *    botões do AlertDialog — nunca entra no scroll, sempre visível) com
 *    alvo de toque de 48dp em cada botão.
 *  - PAISAGEM: DialogProperties(usePlatformDefaultWidth = false) e largura
 *    da área de texto em fillMaxWidth(0.9f) para o texto ter mais espaço;
 *    em retrato mantém a largura padrão da plataforma.
 *  - Texto do relatório em SelectionContainer: além do botão Copiar, o
 *    usuário pode selecionar trechos com toque longo e copiar.
 *
 * Fechar sem apagar (toque fora): o relatório volta a aparecer no próximo
 * início do app.
 */
@Composable
fun CrashReportDialog(
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val report = remember { CrashReportStore.buildReport(context) }
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KenkernSurfaceHigh,
        titleContentColor = KenkernTextHigh,
        textContentColor = KenkernTextMid,
        shape = RoundedCornerShape(16.dp),
        properties = if (isLandscape) {
            // Paisagem: libera a largura da janela para o texto ter mais espaço.
            DialogProperties(usePlatformDefaultWidth = false)
        } else {
            DialogProperties()
        },
        title = {
            Text("O app fechou na última vez", fontWeight = FontWeight.SemiBold)
        },
        text = {
            // Texto ROLÁVEL com altura relativa à tela (45% de screenHeightDp):
            // stack traces podem passar de uma tela, mas o limite acompanha o
            // tamanho real, deixando espaço para o Row de botões fixo.
            SelectionContainer {
                Column(
                    (if (isLandscape) Modifier.fillMaxWidth(0.9f) else Modifier)
                        .heightIn(max = (configuration.screenHeightDp * 0.45f).dp)
                        .verticalScroll(rememberScrollState())
                        .background(KenkernBackground, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        report,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        color = KenkernTextMid
                    )
                }
            }
        },
        confirmButton = {
            // Row FIXO abaixo da área rolável: sempre visível (fora do scroll),
            // alvo de toque mínimo de 48dp em cada botão.
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(report))
                        copied = true
                    },
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Text(
                        if (copied) "Copiado" else "Copiar",
                        color = KenkernAmber,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.width(8.dp))
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Text("Apagar", color = KenkernError, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    )
}
