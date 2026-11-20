# KENKERN — BACKLOG

Tarefa pequena = 1 commit. Status: `TODO` · `DOING` · `COMPILADO` ·
`VALIDADO-NO-APARELHO` · `BLOQUEADO`.

## Fase 2 — Projeto & Diagnóstico (EM CURSO)

| ID | Tarefa | Depende de | Status |
|----|--------|-----------|--------|
| F2.1 | Teste JVM: ida e volta do .ck (cena com hierarquia, classes, LINKERF, escapes, arquivo parcial/corrompido) | — | DOING |
| F2.2 | Teste JVM: ida e volta do .KERN (quantização, octaédrica, LODs, clusters; erro máximo de vértice documentado) | — | TODO |
| F2.3 | Tela Diagnóstico no Hub: checagens on-device (.ck, .KERN, picking, shaders, caps GL, buffers, memória) com APROVADO/FALHOU+motivo | F2.1, F2.2 | TODO |
| F2.4 | Pastas `ui/` e `worlds/` na criação/migração de projeto + ENGINE_VERSION de fonte única (BuildConfig) | — | TODO |
| F2.5 | "Duplicar projeto" no Hub (Salvar como) | — | TODO |
| F2.6 | SAF: criar/abrir projeto em pasta escolhida pelo usuário (ACTION_OPEN_DOCUMENT_TREE + permissão persistente) | F2.4 | TODO |

## Fase 3 — LUA (detalhar no início da fase)

Sketch: interface `ScriptRuntime` → `LuaJScriptRuntime`; hooks start/update/eventos;
API `nick.*`, `scene.*`; editor de script no app; `LUA_API.md` oficial; scripts de
exemplo no Diagnóstico; teste JVM da API (mock + LuaJ puro).

## Fases 4–13

Detalhar o backlog no início de cada fase (ver ROADMAP.md).
