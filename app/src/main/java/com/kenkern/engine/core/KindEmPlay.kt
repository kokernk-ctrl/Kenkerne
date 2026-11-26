package com.kenkern.engine.core

/**
 * S4 (v0.1.24) — TABELA DE TODO KIND IMPLEMENTADO: o que cada tipo de Nick é
 * no VIEWPORT, suas CLASSES PADRÃO e o que ele FAZ EM PLAY. Fonte ÚNICA
 * testável (NickKindTabelaTest cria um Nick de CADA kind e confere) — a
 * tabela humana em NICKS.md espelha estas chaves.
 *
 * Classificação de viewport (3 grupos, [MalhaNick]):
 *  - "malha-cubo"    — desenha geometria (malha importada ou cubo placeholder);
 *  - "auxiliar"      — luz/câmera/área/surgimento/vazio: ícone de EDITOR 3D
 *                      (nunca em Play, nunca no jogo exportado);
 *  - "elemento-2d"   — ui.*: NUNCA desenha malha 3D nem cubo nem auxiliar —
 *                      no editor aparece como MOLDURA 2D de tela; em Play
 *                      vira controle real (joystick/botões) sobre a tela.
 *  - "container"     — nick.group: organiza filhos, não desenha.
 */
object KindEmPlay {

    /** Representação no viewport (chave estável; NICKS.md espelha). */
    fun representacaoViewport(kind: String): String = when {
        MalhaNick.ehKindElemento2D(kind) -> "elemento-2d"
        MalhaNick.ehKindAuxiliar(kind) -> "auxiliar"
        kind == NickKindRegistry.NICK_GROUP -> "container"
        else -> "malha-cubo"
    }

    /** Classes padrão do kind (preset registrado no NickKindRegistry). */
    fun classesPadrao(kind: String): List<String> =
        NickKindRegistry.find(kind)?.defaultClasses ?: emptyList()

    /** O que o Nick do tipo FAZ EM PLAY (chave estável; NICKS.md espelha). */
    fun comportamentoEmPlay(kind: String): String = when (kind) {
        "nick.empty" -> "nada — só transform, filhos e classes"
        "nick.group" -> "nada — só estrutura (não desenha)"
        "nick.mesh" -> "desenha a malha (importada ou cubo)"
        "body.player" -> "SIMULA: gravidade + input (mover/pular) + colisão; nasce no spawn; a câmera seguidora segue"
        "body.rigid" -> "SIMULA: gravidade + colisão (corpo dinâmico)"
        "body.character" -> "SIMULA: gravidade + colisão (sem input próprio)"
        "body.collider" -> "SIMULA: gravidade + colisão — e BLOQUEIA os outros corpos (volume do Colisor)"
        "body.static" -> "BLOQUEIA: corpo estático (não cai; volume do Colisor ou cubo × escala)"
        "body.enemy" -> "NÃO simulado (comportamento/IA pendentes — Fase 8+)"
        "body.npc" -> "NÃO simulado (diálogo/rotinas pendentes — Fase 8+)"
        "area.volume" -> "ÁREA (C2): on_enter/on_exit/on_stay + on_trigger legado; filtro por tag, uma vez só e ligado (classe Área); area.bodies()/contains()"
        "area.spawn" -> "SPAWN: teleporta o jogador para cá ao INICIAR o Play (e no respawn sem checkpoint)"
        "area.signal" -> "SINAL (C2): signal.emit(nome, dados)/signal.on(nome, fn) no barramento da cena (LINKERF na Parada 4)"
        "area.checkpoint" -> "CHECKPOINT (C2): o jogador dentro do volume marca o REAPARECIMENTO; queda abaixo do limite volta aqui"
        "camera.basic" -> "CÂMERA de Play: renderiza pela Lente do Nick (fov/near/far/orto; principal vence)"
        "camera.follow" -> "CÂMERA de Play: segue o jogador (alvo/distância/altura, clamp do chão)"
        "light.directional" -> "ILUMINA: substitui o sol fixo (editor e Play)"
        "light.point" -> "ILUMINA: até 4 pontuais/spot por frame por contribuição (editor e Play)"
        "light.spot" -> "ILUMINA: cone interno/externo + alcance reais (editor e Play)"
        "ui.joystick" -> "INPUT: joystick de tela em Play (tipo/tamanho/zona morta/8 direções; Controla)"
        else -> "—"
    }

    /** Kinds que ENTRAM na simulação de física em Play (NickPlayRuntime). */
    val corposDinamicos: Set<String> =
        setOf("body.player", "body.rigid", "body.character", "body.collider")

    /** Kinds que BLOQUEIAM corpos (estáticos). */
    val bloqueadores: Set<String> = setOf("body.static", "body.collider")
}
