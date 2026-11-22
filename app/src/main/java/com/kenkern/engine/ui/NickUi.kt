package com.kenkern.engine.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.kenkern.engine.R
import com.kenkern.engine.core.Nick
import com.kenkern.engine.core.NickKindRegistry

/**
 * T0.2 (adendo v4): pontes UI ↔ NICK COM TIPO.
 *  - displayName: texto do usuário OU rótulo do tipo NO IDIOMA do app
 *    (DECISIONS #26 — name nulo nunca vira "null");
 *  - ícone por CHAVE do tipo (iconKey do registro; o catálogo é DADOS,
 *    o vetor mora aqui na UI porque o core não depende de Compose).
 */
@Composable
fun nickDisplayName(nick: Nick): String =
    nick.displayName(AppLanguage.currentTag(LocalContext.current))

/** Rótulo do tipo no idioma atual (para cabeçalhos "vazios" da T0.3). */
@Composable
fun nickKindLabel(kind: String): String =
    NickKindRegistry.labelOf(kind, AppLanguage.currentTag(LocalContext.current))

/** Descrição de 1 linha do tipo no idioma atual (cartões da janela T0.3). */
@Composable
fun nickKindDescription(kind: String): String =
    NickKindRegistry.descriptionOf(kind, AppLanguage.currentTag(LocalContext.current))

/** Ícone do tipo pela chave (fallback: ajuste — nick vazio). */
fun nickKindIcon(kind: String): ImageVector = when (kind) {
    "nick.group", "nick.prefab" -> Icons.Filled.Folder
    "nick.mesh" -> Icons.Filled.ViewInAr
    "light.directional", "light.point", "light.spot", "light.probe" -> Icons.Filled.Lightbulb
    "camera.basic", "camera.follow", "camera.cinematic" -> Icons.Filled.PhotoCamera
    "body.player" -> Icons.Filled.SportsEsports
    "body.character", "body.enemy", "body.npc", "body.rigid",
    "body.kinematic", "body.ragdoll", "body.car", "body.bike" -> Icons.Filled.Accessibility
    "body.static", "body.collider" -> Icons.Filled.CropFree
    "area.volume", "area.signal", "area.spawn", "area.checkpoint", "area.navigation" -> Icons.Filled.Podcasts
    else -> if (kind.startsWith("ui.")) Icons.Filled.BlurOn else Icons.Filled.Adjust
}

/** Texto do diálogo de renomear: vazio = volta ao rótulo do tipo (dica). */
@Composable
fun renameHint(): String = stringResource(R.string.hierarchy_rename_hint)
