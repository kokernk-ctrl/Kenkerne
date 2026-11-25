# KENKERN — LUA_API

Documentação oficial da API Lua da engine Kenkern (L2, v0.1.25). Esta mesma especificação é embutida offline no autocompletar do Editor de Lua (`LuaApiCatalog.kt`).

Regras de runtime:
- Erros de sintaxe ou execução são registrados no Console (`KenkernLog` com `arquivo:linha`) e desativam apenas o script defeituoso — o jogo continua.
- Limites: orçamento de 4 ms por tick e teto de instruções por chamada (8 M).
- Fonte do script: classe `Script` (`arquivo` aponta para `scripts/*.lua`; quando vazio, usa o campo `fonte`). Demais campos da classe entram em `params.<nome>`.

---

## Ciclo de vida e callbacks

| Função | Assinatura | Descrição |
| --- | --- | --- |
| `on_start` | `function on_start()` | Executada ao iniciar Play. |
| `on_update` | `function on_update(dt)` | Executada a cada frame com delta `dt` (s). |
| `on_enter` | `function on_enter(outro)` | Corpo/jogador entrou em uma área (`area.volume`). |
| `on_exit` | `function on_exit(outro)` | Corpo/jogador saiu da área. |
| `on_stay` | `function on_stay(outro, dt)` | Chamada todo tick enquanto dentro da área. |
| `on_collision` | `function on_collision(nomeOutro)` | Chamada ao colidir/parar contra corpo bloqueante. |
| `on_trigger` | `function on_trigger(nomeOutro)` | Callback legado de entrada em área. |

---

## `nick` (self e handles retornados pela cena)

- `nick.id() -> string` · `nick.kind() -> string`
- `nick.name() -> string` · `nick.set_name(nome)`
- `nick.position() -> {x, y, z}` · `nick.set_position(x, y, z)`
- `nick.rotation() -> {x, y, z, w}` · `nick.set_rotation(qx, qy, qz, qw)`
- `nick.scale() -> {x, y, z}` · `nick.set_scale(x, y, z)`
- `nick.active() -> boolean` · `nick.set_active(bool)`
- `nick.has_tag(tag) -> boolean` · `nick.add_tag(tag) -> boolean` · `nick.remove_tag(tag) -> boolean` · `nick.tags() -> table`
- `nick.get(classe, campo) -> valor|nil` (lê propriedade de qualquer classe do Nick)
- `nick.set(classe, campo, valor) -> boolean` (escreve propriedade em classe do Nick)
- `nick.class_data(classe) -> table|nil` (alias `nick.class`)
- `nick.destroy()`

---

## `scene` (cena, eventos, tags, prefabs)

- `scene.find(nome) -> handle|nil`
- `scene.find_by_tag(tag) -> handle|nil`
- `scene.find_all_by_tag(tag) -> {handle1, ...}`
- `scene.spawn(x, y, z) -> handle` (cria cubo padrão 1 m)
- `scene.spawn_prefab(nome, x, y, z) -> handle|nil` (instancia `prefabs/<nome>.ck`)
- `scene.destroy(handle)`
- `scene.on(evento, fn)` · `scene.off(evento, [fn])` · `scene.emit(evento, dados)` (suporta múltiplos ouvintes e dados/tabelas)
- `scene.log(mensagem)`

---

## `timer`

- `timer.after(segundos, fn) -> id`
- `timer.every(segundos, fn) -> id`
- `timer.cancel(id) -> boolean`

---

## `engine` e `log`

- `engine.time() -> number` (segundos em Play)
- `engine.dt() -> number` (delta do último tick)
- `engine.log(mensagem)` · `log(mensagem)`

---

## `input`

- `input.joystick() -> {x, y, ativo}`
- `input.action(nome) -> boolean` (alias `input.acao(nome)`)

---

## `ui`

- `ui.text(id, texto)` / `ui.texto(id, texto)`
- `ui.counter(id, valor)` / `ui.contador(id, valor)`
- `ui.count(id, delta)` / `ui.contagem(id, delta)` / `ui.add_count(id, delta)`
- `ui.get_count(id)` / `ui.contagem_atual(id) -> int`
- `ui.clear()` / `ui.limpar()`

---

## `storage` (salvar e carregar dados do jogo)

- `storage.save(chave, valor) -> boolean` (string, número, booleano ou tabela)
- `storage.load(chave, padrao) -> valor`
- `storage.has(chave) -> boolean`
- `storage.delete(chave) -> boolean`
- `storage.clear()`

---

## Exemplo completo (verificado pela checagem `"lua-api"` do Diagnóstico)

```lua
local t_after, t_every = false, 0

function on_start()
  nick.add_tag("heroi")
  local alvo = scene.find_by_tag("alvo")
  if alvo then
    alvo.set("Spin", "speed", 120.0)
  end
  scene.on("ponto", function(dados)
    ui.count("pontos", dados.qtd or 1)
    storage.save("melhor", ui.get_count("pontos"))
  end)
  t_after = timer.after(0.1, function()
    scene.emit("ponto", { qtd = 2 })
  end)
  local rep = timer.every(0.1, function()
    t_every = t_every + 1
  end)
  timer.cancel(rep)
  scene.log("lua-api ok")
end
```
