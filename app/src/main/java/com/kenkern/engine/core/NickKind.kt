package com.kenkern.engine.core

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

/** Status funcional de um tipo no contrato de Nicks (C0). */
enum class NickKindStatus { FUNCIONAL, PARCIAL, SEM_FUNCAO }

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
    val childKinds: List<String> = emptyList(),
    /** Status funcional no contrato de Nicks (NICKS.md). */
    val status: NickKindStatus = NickKindStatus.FUNCIONAL,
    /** Kinds SEM_FUNCAO ficam fora de Adicionar Nick, mas abrem em projetos antigos. */
    val visivel: Boolean = status != NickKindStatus.SEM_FUNCAO
) {
    fun label(language: String): String = if (language == "pt") labelPt else labelEn

    fun description(language: String): String = if (language == "pt") descPt else descEn

    /**
     * P3 (v0.1.25) — REGRA DE NICK COMPLETO: um Nick só é CRIÁVEL na janela
     * "Adicionar Nick" quando completo (as 5 exigências do contrato —
     * NICKS.md): representação no viewport; classes padrão + Inspetor editável
     * com efeito ao vivo; comportamento em Play; API Lua ou porta LINKERF;
     * teste e checagem no Diagnóstico. PARCIAL aparece com selo "Em breve"
     * e DESABILITADO (não cria Nick); SEM_FUNCAO fica fora da janela.
     */
    fun criavel(): Boolean = status == NickKindStatus.FUNCIONAL
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

    /** C0: somente tipos com função real para exibir em Adicionar Nick. */
    fun visiveis(): List<NickKind> = kinds.values.filter { it.visivel }

    fun visiveisByGroup(group: NickKindGroup): List<NickKind> =
        kinds.values.filter { it.visivel && it.group == group }

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
                group = NickKindGroup.LUZ, iconKey = "light.directional",
                defaultClasses = listOf("Luz")
            )
        )
        NickKindRegistry.register(
            NickKind(
                key = "light.point", labelEn = "Point Light Nick", labelPt = "Nick de Luz Pontual",
                descEn = "Light from a point with reach (LUME)",
                descPt = "Luz de um ponto com alcance (LUME)",
                group = NickKindGroup.LUZ, iconKey = "light.point",
                defaultClasses = listOf("Luz")
            )
        )
        NickKindRegistry.register(
            NickKind(
                key = "light.spot", labelEn = "Spot Light Nick", labelPt = "Nick de Luz de Foco",
                descEn = "Cone of light pointed at a target (LUME)",
                descPt = "Cone de luz apontado a um alvo (LUME)",
                group = NickKindGroup.LUZ, iconKey = "light.spot",
                defaultClasses = listOf("Luz")
            )
        )
        // ---------------- CÂMERA (LENTE) ----------------
        NickKindRegistry.register(
            NickKind(
                key = "camera.basic", labelEn = "Camera Nick", labelPt = "Nick de Câmera",
                descEn = "Game camera (modes are a property: 1st/3rd person, ortho, top)",
                descPt = "Câmera de jogo (modos são propriedade: 1ª/3ª pessoa, ortogonal, topo)",
                group = NickKindGroup.CAMERA, iconKey = "camera.basic",
                defaultClasses = listOf("Lente")
            )
        )
        NickKindRegistry.register(
            NickKind(
                key = "camera.follow", labelEn = "Follow Camera Nick", labelPt = "Nick de Câmera Seguidora",
                descEn = "Follows the active player in Play using its starting offset",
                descPt = "Segue o jogador ativo em Play usando a distância e posição iniciais",
                group = NickKindGroup.CAMERA, iconKey = "camera.follow",
                defaultClasses = listOf("Lente")
            )
        )

        // ---------------- UI (TELAR) ----------------
        NickKindRegistry.register(
            NickKind(
                key = "ui.joystick", labelEn = "Joystick Nick", labelPt = "Nick de Joystick",
                descEn = "On-screen joystick: fixed/floating/dynamic, size, deadzone",
                descPt = "Joystick de tela: fixo/flutuante/dinâmico, tamanho, zona morta",
                group = NickKindGroup.UI, iconKey = "nick.empty",
                defaultClasses = listOf("Joystick")
            )
        )

        // ---------------- CORPOS ----------------
        // S2 (v0.1.24): TODOS os kinds de corpo ganham a classe Colisor por
        // PADRÃO — volume de colisão/gravidade EDITÁVEL no Inspetor (antes
        // só o body.collider tinha; o chão estático usava o cubo unitário).
        NickKindRegistry.register(
            NickKind(
                key = "body.static", labelEn = "Static Body Nick", labelPt = "Nick de Corpo Estático",
                descEn = "Never moves; collides (walls, floor)",
                descPt = "Nunca se move; colide (paredes, chão)",
                group = NickKindGroup.CORPOS, iconKey = "body.static",
                defaultClasses = listOf("Colisor")
            )
        )
        NickKindRegistry.register(
            NickKind(
                key = "body.rigid", labelEn = "Rigid Body Nick", labelPt = "Nick de Corpo Rígido",
                descEn = "Physics-driven body (mass, gravity, bounce)",
                descPt = "Corpo guiado pela física (massa, gravidade, quique)",
                group = NickKindGroup.CORPOS, iconKey = "body.rigid",
                defaultClasses = listOf("Colisor")
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
                group = NickKindGroup.CORPOS, iconKey = "body.character",
                defaultClasses = listOf("Colisor")
            )
        )
        NickKindRegistry.register(
            NickKind(
                key = "body.player", labelEn = "Player Body Nick", labelPt = "Nick de Corpo de Jogador",
                descEn = "Character + input + follow camera",
                descPt = "Character + input + câmera seguidora",
                group = NickKindGroup.CORPOS, iconKey = "body.player",
                defaultClasses = listOf("Colisor")
            )
        )
        NickKindRegistry.register(
            NickKind(
                key = "body.enemy", labelEn = "Enemy Body Nick", labelPt = "Nick de Corpo Inimigo",
                descEn = "Character + behavior + health and damage",
                descPt = "Character + comportamento + vida e dano",
                group = NickKindGroup.CORPOS, iconKey = "body.enemy",
                status = NickKindStatus.SEM_FUNCAO, visivel = false
            )
        )
        NickKindRegistry.register(
            NickKind(
                key = "body.npc", labelEn = "NPC Body Nick", labelPt = "Nick de Corpo NPC",
                descEn = "Character + dialogue + routines",
                descPt = "Character + diálogo + rotinas",
                group = NickKindGroup.CORPOS, iconKey = "body.npc",
                status = NickKindStatus.SEM_FUNCAO, visivel = false
            )
        )
        // ---------------- ÁREAS E SINAIS (ZONA) ----------------
        NickKindRegistry.register(
            NickKind(
                key = "area.volume", labelEn = "Area Nick", labelPt = "Nick de Área",
                descEn = "3D detection volume (on_enter/exit/stay — Parada 2)",
                descPt = "Volume 3D de detecção (on_enter/exit/stay — Parada 2)",
                group = NickKindGroup.AREAS, iconKey = "area.volume",
                // P3 (v0.1.25): PARCIAL — o gatilho on_trigger 1× existe, mas a
                // regra de NICK COMPLETO exige on_enter/on_exit/on_stay,
                // area.bodies()/contains() e respawn — Parada 2 (C2).
                status = NickKindStatus.PARCIAL
            )
        )
        NickKindRegistry.register(
            NickKind(
                key = "area.signal", labelEn = "Signal Nick", labelPt = "Nick de Sinal",
                descEn = "Emits and receives named events (wires into LINKERF)",
                descPt = "Emite e recebe eventos nomeados (liga no LINKERF)",
                group = NickKindGroup.AREAS, iconKey = "area.signal",
                status = NickKindStatus.SEM_FUNCAO, visivel = false
            )
        )
        NickKindRegistry.register(
            NickKind(
                key = "area.spawn", labelEn = "Spawn Point Nick", labelPt = "Nick de Ponto de Surgimento",
                descEn = "Where players and objects spawn (respawn/checkpoint — Parada 2)",
                descPt = "Onde jogadores e objetos surgem (respawn/checkpoint — Parada 2)",
                group = NickKindGroup.AREAS, iconKey = "area.spawn",
                // P3 (v0.1.25): PARCIAL — o nascimento funciona, mas o contrato
                // completo (respawn() do jogador + checkpoint + queda abaixo do
                // limite) entra na Parada 2 (C2.3).
                status = NickKindStatus.PARCIAL
            )
        )
    }
}
