package com.kenkern.engine.engine

/**
 * L1/L2: Catálogo OFFLINE da API Lua da Kenkern + modelos de novos scripts.
 * Espelha integralmente a documentação em LUA_API.md para autocompletar no
 * editor Lua e checagem por testes de JVM.
 */
object LuaApiCatalog {

    data class ApiEntry(
        val symbol: String,
        val insertText: String,
        val signature: String,
        val category: String,
        val descPt: String,
        val descEn: String
    )

    data class ScriptTemplate(
        val id: String,
        val titlePt: String,
        val titleEn: String,
        val defaultFileName: String,
        val content: String
    )

    /** Palavras-chave reservadas da linguagem Lua 5.2 (realce e autocompletar). */
    val KEYWORDS: Set<String> = setOf(
        "and", "break", "do", "else", "elseif", "end",
        "false", "for", "function", "goto", "if", "in",
        "local", "nil", "not", "or", "repeat", "return",
        "then", "true", "until", "while"
    )

    /** Callbacks de ciclo de vida / eventos reconhecidos pelo motor. */
    val LIFECYCLE_CALLBACKS: List<String> = listOf(
        "on_start", "on_update", "on_enter", "on_exit", "on_stay", "on_trigger", "on_collision"
    )

    val ENTRIES: List<ApiEntry> = listOf(
        // Ciclo de vida e callbacks
        ApiEntry("on_start", "function on_start()\n  \nend", "on_start()", "callback", "Chamado quando o Play inicia.", "Called when Play starts."),
        ApiEntry("on_update", "function on_update(dt)\n  \nend", "on_update(dt)", "callback", "Chamado todo frame com dt em segundos.", "Called every tick with dt in seconds."),
        ApiEntry("on_enter", "function on_enter(outro)\n  \nend", "on_enter(outro)", "callback", "Chamado ao entrar em uma área.", "Called when entering an area."),
        ApiEntry("on_exit", "function on_exit(outro)\n  \nend", "on_exit(outro)", "callback", "Chamado ao sair de uma área.", "Called when exiting an area."),
        ApiEntry("on_stay", "function on_stay(outro, dt)\n  \nend", "on_stay(outro, dt)", "callback", "Chamado enquanto permanece dentro da área.", "Called while staying inside an area."),
        ApiEntry("on_collision", "function on_collision(outro)\n  \nend", "on_collision(outro)", "callback", "Chamado ao colidir com um obstáculo.", "Called on collision with an obstacle."),
        ApiEntry("on_trigger", "function on_trigger(nome)", "on_trigger(nome)", "callback", "Gatilho legado de entrada em área.", "Legacy area entry trigger."),

        // Nick (self / handles)
        ApiEntry("nick.name", "nick.name()", "nick.name() -> string", "nick", "Retorna o nome visível do Nick.", "Returns the Nick display name."),
        ApiEntry("nick.set_name", "nick.set_name(\"\")", "nick.set_name(nome)", "nick", "Define o nome do Nick.", "Sets the Nick name."),
        ApiEntry("nick.kind", "nick.kind()", "nick.kind() -> string", "nick", "Retorna o kind do Nick (ex: body.player).", "Returns the Nick kind."),
        ApiEntry("nick.id", "nick.id()", "nick.id() -> string", "nick", "Retorna o ReferenceId estável do Nick.", "Returns the stable ReferenceId."),
        ApiEntry("nick.position", "nick.position()", "nick.position() -> {x, y, z}", "nick", "Lê a posição local {x, y, z}.", "Reads local position {x, y, z}."),
        ApiEntry("nick.set_position", "nick.set_position(0, 0, 0)", "nick.set_position(x, y, z)", "nick", "Define a posição local.", "Sets local position."),
        ApiEntry("nick.rotation", "nick.rotation()", "nick.rotation() -> {x, y, z, w}", "nick", "Lê a rotação quaternion.", "Reads quaternion rotation."),
        ApiEntry("nick.set_rotation", "nick.set_rotation(0, 0, 0, 1)", "nick.set_rotation(qx, qy, qz, qw)", "nick", "Define a rotação quaternion.", "Sets quaternion rotation."),
        ApiEntry("nick.scale", "nick.scale()", "nick.scale() -> {x, y, z}", "nick", "Lê a escala local.", "Reads local scale."),
        ApiEntry("nick.set_scale", "nick.set_scale(1, 1, 1)", "nick.set_scale(x, y, z)", "nick", "Define a escala local.", "Sets local scale."),
        ApiEntry("nick.active", "nick.active()", "nick.active() -> boolean", "nick", "Retorna se o Nick está ativo.", "Returns whether Nick is active."),
        ApiEntry("nick.set_active", "nick.set_active(true)", "nick.set_active(ativo)", "nick", "Ativa ou desativa o Nick.", "Enables or disables the Nick."),
        ApiEntry("nick.has_tag", "nick.has_tag(\"\")", "nick.has_tag(tag) -> boolean", "nick", "Verifica se o Nick possui a tag.", "Checks if the Nick has the tag."),
        ApiEntry("nick.add_tag", "nick.add_tag(\"\")", "nick.add_tag(tag) -> boolean", "nick", "Adiciona uma tag ao Nick.", "Adds a tag to the Nick."),
        ApiEntry("nick.remove_tag", "nick.remove_tag(\"\")", "nick.remove_tag(tag) -> boolean", "nick", "Remove uma tag do Nick.", "Removes a tag from the Nick."),
        ApiEntry("nick.tags", "nick.tags()", "nick.tags() -> table", "nick", "Lista todas as tags do Nick.", "Lists all tags on the Nick."),
        ApiEntry("nick.get", "nick.get(\"Classe\", \"campo\")", "nick.get(classe, campo) -> valor", "nick", "Lê propriedade de uma classe do Nick.", "Reads a property from a Nick class."),
        ApiEntry("nick.set", "nick.set(\"Classe\", \"campo\", 0)", "nick.set(classe, campo, valor) -> boolean", "nick", "Escreve propriedade em uma classe do Nick.", "Writes a property to a Nick class."),
        ApiEntry("nick.class_data", "nick.class_data(\"Classe\")", "nick.class_data(classe) -> table", "nick", "Retorna tabela com os campos da classe.", "Returns a table with all class fields."),
        ApiEntry("nick.destroy", "nick.destroy()", "nick.destroy()", "nick", "Remove o Nick da cena em Play.", "Destroys the Nick from the Play scene."),

        // Cena / Eventos / Prefabs / Tags
        ApiEntry("scene.find", "scene.find(\"\")", "scene.find(nome) -> handle|nil", "scene", "Busca o primeiro Nick pelo nome.", "Finds the first Nick by name."),
        ApiEntry("scene.find_by_tag", "scene.find_by_tag(\"\")", "scene.find_by_tag(tag) -> handle|nil", "scene", "Busca o primeiro Nick com a tag.", "Finds the first Nick with a tag."),
        ApiEntry("scene.find_all_by_tag", "scene.find_all_by_tag(\"\")", "scene.find_all_by_tag(tag) -> {handle...}", "scene", "Busca todos os Nicks com a tag.", "Finds all Nicks with a tag."),
        ApiEntry("scene.spawn", "scene.spawn(0, 1, 0)", "scene.spawn(x, y, z) -> handle", "scene", "Cria um cubo simples na cena.", "Spawns a default mesh cube."),
        ApiEntry("scene.spawn_prefab", "scene.spawn_prefab(\"\", 0, 1, 0)", "scene.spawn_prefab(nome, x, y, z) -> handle|nil", "scene", "Instancia um prefab em (x, y, z).", "Spawns a prefab at (x, y, z)."),
        ApiEntry("scene.destroy", "scene.destroy()", "scene.destroy(handle)", "scene", "Destrói o Nick informado.", "Destroys the target Nick."),
        ApiEntry("scene.emit", "scene.emit(\"evento\", {})", "scene.emit(evento, dados)", "scene", "Emite um evento global com dados.", "Emits a global event with data."),
        ApiEntry("scene.on", "scene.on(\"evento\", function(dados)\n  \nend)", "scene.on(evento, fn)", "scene", "Escuta um evento emitido por scene.emit.", "Listens for an event from scene.emit."),
        ApiEntry("scene.off", "scene.off(\"evento\")", "scene.off(evento, [fn])", "scene", "Remove ouvintes de um evento.", "Unsubscribes from an event."),
        ApiEntry("scene.log", "scene.log(\"\")", "scene.log(mensagem)", "scene", "Escreve no Console da engine.", "Writes a message to engine Console."),

        // Timers
        ApiEntry("timer.after", "timer.after(1.0, function()\n  \nend)", "timer.after(segundos, fn) -> id", "timer", "Executa uma função após X segundos.", "Runs a function once after X seconds."),
        ApiEntry("timer.every", "timer.every(1.0, function()\n  \nend)", "timer.every(segundos, fn) -> id", "timer", "Executa repetidamente a cada X segundos.", "Repeats every X seconds."),
        ApiEntry("timer.cancel", "timer.cancel(id)", "timer.cancel(id) -> boolean", "timer", "Cancela um timer pelo id.", "Cancels a timer by id."),

        // Engine & Input
        ApiEntry("engine.time", "engine.time()", "engine.time() -> number", "engine", "Tempo total de Play em segundos.", "Total Play elapsed time in seconds."),
        ApiEntry("engine.dt", "engine.dt()", "engine.dt() -> number", "engine", "Delta de tempo do último tick.", "Last tick delta time in seconds."),
        ApiEntry("engine.log", "engine.log(\"\")", "engine.log(mensagem)", "engine", "Escreve no Console da engine.", "Writes to engine Console."),
        ApiEntry("input.joystick", "input.joystick()", "input.joystick() -> {x, y, ativo}", "input", "Estado atual do joystick de tela.", "Current on-screen joystick state."),
        ApiEntry("input.action", "input.action(\"jump\")", "input.action(nome) -> boolean", "input", "Verifica se a ação de input está ativa.", "Checks if an input action is pressed."),
        ApiEntry("input.acao", "input.acao(\"pular\")", "input.acao(nome) -> boolean", "input", "Alias em português para input.action.", "Portuguese alias for input.action."),

        // UI
        ApiEntry("ui.text", "ui.text(\"status\", \"\")", "ui.text(id, texto)", "ui", "Exibe texto na UI de Play.", "Shows text in the Play HUD."),
        ApiEntry("ui.texto", "ui.texto(\"status\", \"\")", "ui.texto(id, texto)", "ui", "Alias em português para ui.text.", "Portuguese alias for ui.text."),
        ApiEntry("ui.counter", "ui.counter(\"pontos\", 0)", "ui.counter(id, valor)", "ui", "Define um valor numérico na UI.", "Sets a numeric display value on UI."),
        ApiEntry("ui.count", "ui.count(\"moedas\", 1)", "ui.count(id, delta)", "ui", "Soma delta no contador global da UI.", "Adds delta to a global UI counter."),
        ApiEntry("ui.get_count", "ui.get_count(\"moedas\")", "ui.get_count(id) -> int", "ui", "Lê o contador global atual.", "Reads the current global counter value."),
        ApiEntry("ui.clear", "ui.clear()", "ui.clear()", "ui", "Limpa todos os textos e contadores da UI.", "Clears all UI texts and counters."),

        // Persistência (storage)
        ApiEntry("storage.save", "storage.save(\"chave\", 1)", "storage.save(chave, valor) -> boolean", "storage", "Salva dado persistente do jogo.", "Saves persistent game data."),
        ApiEntry("storage.load", "storage.load(\"chave\", 0)", "storage.load(chave, padrao) -> valor", "storage", "Carrega dado salvo ou retorna padrão.", "Loads saved data or returns default."),
        ApiEntry("storage.has", "storage.has(\"chave\")", "storage.has(chave) -> boolean", "storage", "Verifica se a chave existe.", "Checks if a saved key exists."),
        ApiEntry("storage.delete", "storage.delete(\"chave\")", "storage.delete(chave) -> boolean", "storage", "Apaga uma chave salva.", "Deletes a saved key."),
        ApiEntry("storage.clear", "storage.clear()", "storage.clear()", "storage", "Apaga todos os dados salvos.", "Clears all saved game data.")
    )

    val API_ROOT_IDENTIFIERS: Set<String> = setOf(
        "nick", "scene", "timer", "engine", "input", "ui", "storage", "params", "log",
        "on_start", "on_update", "on_enter", "on_exit", "on_stay", "on_trigger", "on_collision"
    )

    val TEMPLATES: List<ScriptTemplate> = listOf(
        ScriptTemplate(
            id = "vazio",
            titlePt = "Vazio",
            titleEn = "Empty",
            defaultFileName = "novo_script.lua",
            content = """-- Script Kenkern
function on_start()
  scene.log("Script iniciado: " .. nick.name())
end

function on_update(dt)
end
"""
        ),
        ScriptTemplate(
            id = "movimento",
            titlePt = "Movimento",
            titleEn = "Movement",
            defaultFileName = "movimento.lua",
            content = """-- Movimento usando joystick e ação
local velocidade = params.velocidade or 4.0

function on_update(dt)
  local joy = input.joystick()
  if joy.ativo then
    local p = nick.position()
    nick.set_position(p.x + joy.x * velocidade * dt, p.y, p.z + joy.y * velocidade * dt)
  end
  if input.action("jump") then
    scene.log("Pulo acionado")
  end
end
"""
        ),
        ScriptTemplate(
            id = "coletavel",
            titlePt = "Coletável",
            titleEn = "Collectible",
            defaultFileName = "coletavel.lua",
            content = """-- Coletável: soma contador, salva recorde e emite evento
function on_enter(outro)
  ui.count("moedas", 1)
  local total = ui.get_count("moedas")
  storage.save("moedas", total)
  scene.emit("moeda_coletada", { total = total, quem = outro.name() })
  nick.destroy()
end
"""
        ),
        ScriptTemplate(
            id = "gatilho_area",
            titlePt = "Gatilho de área",
            titleEn = "Area trigger",
            defaultFileName = "gatilho_area.lua",
            content = """-- Gatilho de área: entrada, permanência e saída
function on_enter(outro)
  ui.text("zona", "Entrou: " .. outro.name())
  scene.emit("zona_entrou", outro.name())
end

function on_stay(outro, dt)
  -- executado enquanto o corpo estiver dentro
end

function on_exit(outro)
  ui.text("zona", "")
end
"""
        )
    )

    fun buscarAutocompletar(prefixo: String): List<ApiEntry> {
        val q = prefixo.trim().lowercase()
        if (q.isEmpty()) return ENTRIES.take(12)
        return ENTRIES.filter {
            it.symbol.lowercase().contains(q) || it.category.lowercase().startsWith(q)
        }
    }
}
