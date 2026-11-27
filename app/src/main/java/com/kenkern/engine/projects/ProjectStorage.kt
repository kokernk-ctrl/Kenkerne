package com.kenkern.engine.projects

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File

/**
 * F1 (v0.1.30) — PROJETO EM DOIS LOCAIS: interface [ProjectStorage].
 *
 * DECISÃO (registrada em DECISIONS.md): o app distribuído por APK do GitHub
 * pede "Acesso a todos os arquivos" (MANAGE_EXTERNAL_STORAGE) e usa
 * java.io.File DIRETO em /storage/emulated/0/Kenkern/Projetos/<nome> — sem
 * SAF, sem cópia por stream. Alternativa descartada: só SAF (fricção alta,
 * uri de permissão expira a cada reboot para uns fabricantes).
 *
 *  (a) "App"    — filesDir/projects (o atual, rápido, sem permissão).
 *  (b) "Aparelho" — pasta VISÍVEL ao usuário, acesso direto por File com a
 *      permissão concedida; SEM a permissão o app SEGUE em (a) e segue
 *      oferecendo .kernpack e SAF (nada trava).
 *
 * Guardas comuns (PURO — JVM-testado em [GuardasCaminhoTest]): caminho
 * dentro da raiz do projeto, bloqueio de "..", nomes válidos, espaço livre,
 * gravação atômica (ProjectManager.writeFileAtomic) e auto-save IGUAL nos
 * dois locais (o handle aponta para a raiz real — o AutoSaveManager segue).
 */
interface ProjectStorage {
    /** id estável do local ("app" | "aparelho"). */
    val id: String

    /** Raiz onde os projetos deste local vivem. */
    fun raiz(context: Context): File

    /** O local está USÁVEL agora (permissão concedida / armazenamento ok)? */
    fun disponivel(context: Context): Boolean
}

/** (a) Armazenamento interno do app — sempre disponível, sem permissões. */
object AppStorage : ProjectStorage {
    override val id: String = "app"
    override fun raiz(context: Context): File =
        File(context.filesDir ?: File("/data/local/tmp/kenkern"), "projects").also { it.mkdirs() }

    override fun disponivel(context: Context): Boolean = true
}

/**
 * (b) Pasta visível do aparelho: /storage/emulated/0/Kenkern/Projetos.
 * MANAGE_EXTERNAL_STORAGE no API 30+; no 26–29 usa WRITE_EXTERNAL_STORAGE
 * (legacy). Sem permissão: disponivel()=false e o app segue em AppStorage.
 */
object AparelhoStorage : ProjectStorage {
    override val id: String = "aparelho"

    override fun raiz(context: Context): File =
        File(Environment.getExternalStorageDirectory(), "Kenkern/Projetos")

    override fun disponivel(context: Context): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            @Suppress("DEPRECATION")
            val write = android.content.pm.PackageManager.PERMISSION_GRANTED ==
                context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            val read = android.content.pm.PackageManager.PERMISSION_GRANTED ==
                context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            read && write
        }
    }.getOrDefault(false)

    /** Tenta criar a raiz (só faz sentido COM a permissão). */
    fun garantirRaiz(): Boolean = runCatching {
        File(Environment.getExternalStorageDirectory(), "Kenkern").mkdirs() &&
            File(Environment.getExternalStorageDirectory(), "Kenkern/Projetos").mkdirs()
    }.getOrDefault(false)
}

/** utilitário: troca de local por id ("app"/"aparelho"). */
object Storages {
    fun porId(id: String?): ProjectStorage = when (id) {
        AparelhoStorage.id -> AparelhoStorage
        else -> AppStorage
    }

    /** O local PADRÃO ao criar: "Aparelho" SE permitido (tarefa F1), senão App. */
    fun padrao(context: Context): ProjectStorage =
        if (AparelhoStorage.disponivel(context)) AparelhoStorage else AppStorage

    val TODOS = listOf(AppStorage, AparelhoStorage)
}

/**
 * F1 — utilidades de PERMISSÃO do "Acesso a todos os arquivos": a tela que
 * explica a permissão chama [abrirConfiguracao] (tela do sistema); negada,
 * o app segue em (a) e continua com .kernpack/SAF.
 */
object PermissaoAparelho {

    /** Concede já? (mesmo critério de [AparelhoStorage.disponivel]). */
    fun concedida(context: Context): Boolean = AparelhoStorage.disponivel(context)

    /** Abre a tela do sistema que concede "Acesso a todos os arquivos". */
    fun abrirConfiguracao(context: Context): Boolean = runCatching {
        val intent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrElse {
        // fabricante sem a tela específica: cai na lista de apps com permissões
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrDefault(false)
    }
}
