# KENKERN — BACKLOG

Tarefa pequena = 1 commit. Status: `TODO` · `DOING` · `COMPILADO` ·
`VALIDADO-NO-APARELHO` · `BLOQUEADO`.

## Fase 2 — Projeto & Diagnóstico (FEITA)

| ID | Tarefa | Depende de | Status |
|----|--------|-----------|--------|
| F2.1 | Teste JVM: ida e volta do .ck (+ FIX: escapes voltavam corrompidos) | — | COMPILADO+CI ✓ (pendente aparelho via Diagnóstico) |
| F2.2 | Teste JVM: ida e volta do .KERN (+ FIX: LOD_ENTRY_SIZE e header desalinhado — torus nunca renderizou) | — | COMPILADO+CI ✓ (pendente aparelho) |
| F2.3 | Tela Diagnóstico no Hub (7 checagens APROVADO/FALHOU) | F2.1, F2.2 | COMPILADO+CI ✓ (pendente aparelho) |
| F2.4 | Pastas ui/worlds + ENGINE_VERSION de fonte única (BuildConfig) | — | COMPILADO+CI ✓ |
| F2.5 | Duplicar projeto (+ 3 testes JVM) | — | COMPILADO+CI ✓ (pendente aparelho) |
| F2.6 | .KERNPACK import/export via SAF (+ 5 testes JVM, zip-slip) | F2.4 | COMPILADO+CI ✓ (pendente aparelho) |

## Fase 3 — LUA (EM CURSO — detalhar no início da fase)

Sketch: interface `ScriptRuntime` → `LuaJScriptRuntime`; hooks start/update/eventos;
API `nick.*`, `scene.*`; editor de script no app; `LUA_API.md` oficial; scripts de
exemplo no Diagnóstico; teste JVM da API (mock + LuaJ puro).

## Fases 4–13

Detalhar o backlog no início de cada fase (ver ROADMAP.md).
