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
