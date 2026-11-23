package com.kenkern.engine.core

import com.kenkern.engine.core.input.TouchControlSchema

/**
 * TIPO DE NICK = PRESET DE DADOS (T0.2, adendo v4 — DECISIONS #24):
 * cada tipo é um REGISTRO aqui (chave estável em inglês, rótulos en/pt,
 * descrição, grupo, ícone, classes padrão, filhos padrão) — NÃO existe uma
 * classe de código por tipo. O `.ck` (CK2) guarda a CHAVE (ex.: `body.player`),
 * nunca o rótulo; `name` nulo no Nick exibe o rótulo do tipo no idioma atual
 * (DECISIONS #26). A janela "Adicionar Nick" lista só os tipos registrados
 * aqui (IMPLEMENTADOS); o catálogo completo vive em NICKS.md (DECISIONS #28).
 */
enum class NickKindGroup(val key: String, val labelEn: String, val labelPt: String) {
    BASE("base", "Base", "Base"),
    LUZ("luz", "Light", "Luz"),
    CAMERA("camera", "Camera", "Câmera"),
    CORPOS("corpos", "Bodies", "Corpos"),
    AREAS("areas", "Areas & Signals", "Áreas e Sinais"),
    IA("ia", "AI & Dialogue", "IA e Diálogo"),
    ANIMACAO("animacao", "Animation", "Animação"),
    AMBIENTE("ambiente", "Environment", "Ambiente"),
    EFEITOS("efeitos", "Effects", "Efeitos"),
    AUDIO("audio", "Audio", "Áudio"),
    MIDIA("midia", "Media", "Mídia"),
    CURVAS("curvas", "Curves", "Curvas"),
    LOGICA("logica", "Logic", "Lógica"),
    MUNDO("mundo", "World", "Mundo"),
    UI("ui", "UI", "UI")
}

/** Definição de um tipo de Nick (preset de dados; chaves estáveis em inglês). */
class NickKind(
    val key: String,
    val labelEn: String,
    val labelPt: String,
    val descEn: String,
    val descPt: String,
    val group: NickKindGroup,
    /** Chave do ícone (resolvida para ImageVector pela UI; core sem Compose). */
    val iconKey: String,
    /** Nomes de classes aplicadas ao Nick criado deste tipo. */
    val defaultClasses: List<String> = emptyList(),
    /** Chaves dos filhos criados junto (ex.: prefab → filhos padrão). */
    val childKinds: List<String> = emptyList()
) {
    fun label(language: String): String = if (language == "pt") labelPt else labelEn

    fun description(language: String): String = if (language == "pt") descPt else descEn
}

/** Registro global dos tipos de Nick conhecidos pela engine. */
object NickKindRegistry {

    const val NICK_EMPTY = "nick.empty"
    const val NICK_GROUP = "nick.group"
    const val NICK_MESH = "nick.mesh"

    private val kinds = LinkedHashMap<String, NickKind>()

    fun register(kind: NickKind) {
        kinds[kind.key] = kind
    }

    fun find(key: String): NickKind? = kinds[key]

    fun all(): List<NickKind> = kinds.values.toList()

    fun byGroup(group: NickKindGroup): List<NickKind> =
        kinds.values.filter { it.group == group }

    /**
     * Rótulo do tipo no idioma pedido; chave DESCONHECIDA devolve a própria
     * chave (robusto contra projeto com tipo de versão mais nova — nunca
     * substitui o dado do usuário por "???").
     */
    fun labelOf(key: String, language: String): String =
        find(key)?.label(language) ?: key

    fun descriptionOf(key: String, language: String): String =
        find(key)?.description(language) ?: key

    fun clear() = kinds.clear()
}

/**
 * CATÁLOGO dos tipos IMPLEMENTADOS (T0.2 — a lista inicial do adendo v4).
 * Registrar no Application (e nos testes JVM); cada sistema novo registra o
 * seu tipo aqui e no NICKS.md (status PLANEJADO → IMPLEMENTADO).
 */
object NickKindCatalog {

    fun registerAll() {
        // ---------------- BASE ----------------
        NickKindRegistry.register(
            NickKind(
                key = "nick.empty", labelEn = "Empty Nick", labelPt = "Nick Vazio",
                descEn = "Plain node: transform, children and classes only",
                descPt = "Nó simples: só transform, filhos e classes",
                group = NickKindGroup.BASE, iconKey = "nick.empty"
            )
        )
        NickKindRegistry.register(
            NickKind(
                key = "nick.group", labelEn = "Group Nick", labelPt = "Nick de Grupo",
                descEn = "Container to organize children (draws nothing)",
                descPt = "Container para organizar filhos (não desenha)",
                group = NickKindGroup.BASE, iconKey = "nick.group"
            )
        )
        NickKindRegistry.register(
            NickKind(
                key = "nick.mesh", labelEn = "Mesh Nick", labelPt = "Nick de Malha",
                descEn = "Geometry: imported mesh or the built-in cube",
                descPt = "Geometria: malha importada ou o cubo embutido",
                group = NickKindGroup.BASE, iconKey = "nick.mesh"
            )
        )
        // ---------------- LUZ (LUME) ----------------
        NickKindRegistry.register(
            NickKind(
                key = "light.directional", labelEn = "Directional Light Nick", labelPt = "Nick de Luz Direcional",
                descEn = "Sun-like light from a direction (LUME)",
                descPt = "Luz tipo sol vinda de uma direção (LUME)",
                group = NickKindGroup.LUZ, iconKey = "light.directional"
            )
        )
        NickKindRegistry.register(
            NickKind(
                key = "light.point", labelEn = "Point Light Nick", labelPt = "Nick de Luz Pontual",
                descEn = "Light from a point with reach (LUME)",
                descPt = "Luz de um ponto com alcance (LUME)",
                group = NickKindGroup.LUZ, iconKey = "light.point"
            )
        )
        NickKindRegistry.register(
            NickKind(
                key = "light.spot", labelEn = "Spot Light Nick", labelPt = "Nick de Luz de Foco",
                descEn = "Cone of light pointed at a target (LUME)",
                descPt = "Cone de luz apontado a um alvo (LUME)",
                group = NickKindGroup.LUZ, iconKey = "light.spot"
            )
        )
        // ---------------- CÂMERA (LENTE) ----------------
        NickKindRegistry.register(
            NickKind(
                key = "camera.basic", labelEn = "Camera Nick", labelPt = "Nick de Câmera",
                descEn = "Game camera (modes are a property: 1st/3rd person, ortho, top)",
                descPt = "Câmera de jogo (modos são propriedade: 1ª/3ª pessoa, ortogonal, topo)",
                group = NickKindGroup.CAMERA, iconKey = "camera.basic"
            )
        )
        NickKindRegistry.register(
            NickKind(
                key = "camera.follow", labelEn = "Follow Camera Nick", labelPt = "Nick de Câmera Seguidora",
                descEn = "Follows the active player in Play using its starting offset",
                descPt = "Segue o jogador ativo em Play usando a distância e posição iniciais",
                group = NickKindGroup.CAMERA, iconKey = "camera.follow"
            )
        )

        // ---------------- CORPOS ----------------
        NickKindRegistry.register(
            NickKind(
                key = "body.static", labelEn = "Static Body Nick", labelPt = "Nick de Corpo Estático",
                descEn = "Never moves; collides (walls, floor)",
                descPt = "Nunca se move; colide (paredes, chão)",
                group = NickKindGroup.CORPOS, iconKey = "body.static"
            )
        )
        NickKindRegistry.register(
            NickKind(
                key = "body.rigid", labelEn = "Rigid Body Nick", labelPt = "Nick de Corpo Rígido",
                descEn = "Physics-driven body (mass, gravity, bounce)",
                descPt = "Corpo guiado pela física (massa, gravidade, quique)",
                group = NickKindGroup.CORPOS, iconKey = "body.rigid"
            )
        )
        NickKindRegistry.register(
            NickKind(
                key = "body.collider", labelEn = "Collider Body Nick", labelPt = "Nick de Corpo Colisor",
                descEn = "Collision volume: box, sphere, capsule, cylinder, convex or mesh",
                descPt = "Volume de colisão: caixa, esfera, cápsula, cilindro, convexo ou malha",
                group = NickKindGroup.CORPOS, iconKey = "body.collider",
                defaultClasses = listOf("Colisor")
            )
        )
        NickKindRegistry.register(
            NickKind(
                key = "body.character", labelEn = "Character Body Nick", labelPt = "Nick de Corpo de Personagem",
                descEn = "Walks, jumps and climbs ramps",
                descPt = "Anda, pula e sobe rampas",
                group = NickKindGroup.CORPOS, iconKey = "body.character"
            )
        )
        NickKindRegistry.register(
            NickKind(
                key = "body.player", labelEn = "Player Body Nick", labelPt = "Nick de Corpo de Jogador",
                descEn = "Character + input + follow camera",
                descPt = "Character + input + câmera seguidora",
                group = NickKindGroup.CORPOS, iconKey = "body.player"
            )
        )
        NickKindRegistry.register(
            NickKind(
                key = "body.enemy", labelEn = "Enemy Body Nick", labelPt = "Nick de Corpo Inimigo",
                descEn = "Character + behavior + health and damage",
                descPt = "Character + comportamento + vida e dano",
                group = NickKindGroup.CORPOS, iconKey = "body.enemy"
            )
        )
        NickKindRegistry.register(
            NickKind(
                key = "body.npc", labelEn = "NPC Body Nick", labelPt = "Nick de Corpo NPC",
                descEn = "Character + dialogue + routines",
                descPt = "Character + diálogo + rotinas",
                group = NickKindGroup.CORPOS, iconKey = "body.npc"
            )
        )
        // ---------------- ÁREAS E SINAIS (ZONA) ----------------
        NickKindRegistry.register(
            NickKind(
                key = "area.volume", labelEn = "Area Nick", labelPt = "Nick de Área",
                descEn = "3D detection volume (who is inside)",
                descPt = "Volume 3D de detecção (quem está dentro)",
                group = NickKindGroup.AREAS, iconKey = "area.volume"
            )
        )
        NickKindRegistry.register(
            NickKind(
                key = "area.signal", labelEn = "Signal Nick", labelPt = "Nick de Sinal",
                descEn = "Emits and receives named events (wires into LINKERF)",
                descPt = "Emite e recebe eventos nomeados (liga no LINKERF)",
                group = NickKindGroup.AREAS, iconKey = "area.signal"
            )
        )
        NickKindRegistry.register(
            NickKind(
                key = "area.spawn", labelEn = "Spawn Point Nick", labelPt = "Nick de Ponto de Surgimento",
                descEn = "Where players and objects spawn",
                descPt = "Onde jogadores e objetos surgem",
                group = NickKindGroup.AREAS, iconKey = "area.spawn"
            )
        )
        NickKindRegistry.register(
            NickKind(
                TouchControlSchema.JOYSTICK, "UI Joystick Nick", "Nick de Joystick de UI",
                "Configurable gameplay touch axes", "Eixos de toque configuráveis para o jogo",
                NickKindGroup.UI, "ui.joystick", listOf(TouchControlSchema.STICK_CLASS)
            )
        )
        NickKindRegistry.register(
            NickKind(
                TouchControlSchema.BUTTON, "UI Action Button Nick", "Nick de Botão de Ação de UI",
                "Touch button mapped to a named action", "Botão de toque ligado a uma ação nomeada",
                NickKindGroup.UI, "ui.button", listOf(TouchControlSchema.BUTTON_CLASS)
            )
        )
    }
}
