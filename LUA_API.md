# KENKERN — LUA_API

**RASCUNHO** — a API oficial nasce na Fase 3. Este arquivo documenta a API
exposta aos scripts do jogo (runtime LuaJ atrás de `ScriptRuntime`). Nada aqui
está implementado ainda; a seção abaixo é o contrato planejado, que será
congelado na Fase 3 com testes JVM.

## Contrato planejado (Fase 3)

```lua
-- Ciclo de vida
function on_start() end      -- Nick entra em Play
function on_update(dt) end   -- todo tick (dt em segundos)

-- Nick dono do script (self)
nick.position = Vec3.new(0, 1, 0)   -- posição local
nick.rotation = Quat.new(0, 0, 0, 1)
nick.scale    = Vec3.new(1, 1, 1)
nick.name     = "Cubo"
nick.active   = true

-- Cena
local outro = scene.find("Cubo 2")
scene.log("mensagem")               -- Logcat tag Kenkern/Lua

-- Tempo
engine.time                          -- segundos desde o Play
engine.dt                            -- delta do tick

-- Campos de classes do Nick (dados .ck)
local spin = nick.class("Spin")
if spin then spin.speed = 90.0 end
```

Regras: script nunca derruba o app (erro de runtime vira log + desativa o
script do Nick); orçamento de tempo por tick; zero alocação na ponte de
chamada frequente.

## Subconjunto IMPLEMENTADO (M1.1, v0.1.21)

O M1.1 entregou a API MÍNIMA ligada aos Nicks (o contrato completo acima
continua planejado até a Fase 5):

- Script = CLASSE do Nick ("Script"; campo `fonte`); os demais campos da
  classe são VARIÁVEIS PÚBLICAS (`params.<nome>`; editáveis no Inspetor).
- `nick`: set_position(x,y,z) · position() → {x,y,z} · set_scale(x,y,z) ·
  set_active(bool) · name() · destroy()
- `scene`: find(nome) → handle (mesmas funções) · destroy(handle) ·
  spawn(x,y,z) → cubo de 1 m (prefab simples) · log(msg) · emit(evento,valor) ·
  on(evento,fn)
- `engine.time()` (segundos desde o início do Play) · on_update(dt)
- Eventos do M1.2: on_collision(nomeDoCorpo) · on_trigger(nomeDaArea)
- Limites: orçamento de tempo (4 ms/tick) + LIMITE DE INSTRUÇÕES
  (8 M/chamada, hook do LuaJ) — estourou = script desativado, jogo continua.
