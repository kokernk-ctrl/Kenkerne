# KENKERN — AUDITORIA `runCatching` (documento vivo)

Regra (DECISIONS #17): **nenhum `runCatching` engole erro em silêncio**. Todo
`runCatching` em caminho de **render**, **formato** ou **carregamento** registra
o erro em 3 destinos: logcat (`Log.e/w`) + Console (`KenkernLog`, anel em
memória exibido na tela Diagnóstico e no overlay do editor) + quando aplicável
estado visível (StatusBar/Snackbar).

Varredura inicial: 2026-11-21, 62 ocorrências. Legenda: SILENCIOSO = descartada
sem registro · PARCIAL = só logcat · VISÍVEL = logcat + estado visível ·
INFRA-DIAG = própria infra de diagnóstico.

## Estado ANTES do realinhamento (varredura)

Resumo: 8 visíveis, 1 visível-sem-log, 12 parciais, 1 infra com tudo, 8
infra-diag, **32 silenciosas** (16 delas em caminhos críticos).

| Arquivo | Linhas | Classificação | O que protegia / risco |
|---|---|---|---|
| BootTrace.kt | 52, 64 | INFRA-DIAG silenciosa | delete/append do rastro de boot sem Log — etapa some do rastro |
| BootTrace.kt | 78 | INFRA-DIAG ok | readLines → CrashReport mostra "(não registrada)" |
| Diagnostico.kt | 113 | INFRA-DIAG ok (sem logcat) | checagens já viram FALHOU com motivo na tela; faltava logcat |
| EglDiag.kt | 30 | INFRA-DIAG silenciosa | leak de contexto EGL offscreen |
| EglDiag.kt | 43 | INFRA-DIAG ok | onErro vira FALHOU na tela |
| HomeScreen.kt | 333 | SILENCIOSA | parse de data cosmético (cão no default) |
| EditorScreen.kt | 425 | SILENCIOSA | setFixedSize da resolução recusado sem rastro |
| EditorScreen.kt | 542 | SILENCIOSA CRÍTICA | saveNow no dispose — perda de save na saída |
| CrashReportDialog.kt | 62, 66, 67 | INFRA-DIAG silenciosa | diálogo de crash pode não aparecer / relatório volta |
| CrashReportDialog.kt | 72, 82 | INFRA-DIAG ok | degradação visível no relatório |
| EditorViewModel.kt | 135 | VISÍVEL | Log.e + Snackbar "Falha ao abrir projeto" |
| EditorViewModel.kt | 217, 240 | VISÍVEL-sem-log | Snackbar export/import, faltava Log.e |
| EditorViewModel.kt | 304 | SILENCIOSA CRÍTICA | saveNow (autosave) engolido — perda silenciosa |
| PanelStateStore.kt | 63, 89 | SILENCIOSA | prefs de layout do painel (menor) |
| KenkernApplication.kt | 31, 55 | INFRA-DIAG ok | handler de crash repropaga; appVersion cosmético |
| EngineRenderer.kt | 141–144 | PARCIAL | cubo/gizmo/tGizmo/kern somem com Log.w — sem Console |
| EngineRenderer.kt | 270 | PARCIAL | beginFrame emergencial falha de novo, sem registro |
| GlesBackend.kt | 410, 424, 441, 450, 458 | PARCIAL | céu/grade/flat/line/tGizmo somem com Log.w |
| GlesBackend.kt | 592, 604 | SILENCIOSA CRÍTICA | OOM na alocação de VBO → handle 0 sem Log |
| ProjectManager.kt | 72, 134, 143, 183, 204, 297 | VISÍVEL | reportError (Log.e + lastError; depende do chamador mostrar) |
| ProjectManager.kt | 121, 227 | PARCIAL | lastError setado mas não consumido pela UI |
| ProjectManager.kt | 162, 173 | SILENCIOSA | limpeza pós-erro sem Log |
| ProjectManager.kt | 220 | PARCIAL | mkdirs Log.w |
| ProjectManager.kt | 233 | SILENCIOSA CRÍTICA | cena ilegível → Scene vazia sem aviso |
| ProjectManager.kt | 242 | SILENCIOSA | hasAutosave falso sem Log |
| ProjectManager.kt | 275 | SILENCIOSA | metadata ilegível → nome da pasta |
| KenkernSurfaceView.kt | 259 | VISÍVEL | gizmo vazio: Log.e + flag → StatusBar + overlay |
| KenkernSurfaceView.kt | 408 | SILENCIOSA | deviceSupportsGles3 getOrDefault(true) |
| KenkernSurfaceView.kt | 423 | PARCIAL | Log.e do onTouch (fix dedo morto) |
| KenkernSurfaceView.kt | 431 | SILENCIOSA | isGizmoCapturedNow |
| KenkernSurfaceView.kt | 860 | SILENCIOSA | eglDestroyContext (leak menor) |
| KenkernSurfaceView.kt | 895 | PARCIAL | escolha EGL por candidato (por design; final lança) |
| KenkernSurfaceView.kt | 936 | SILENCIOSA | destroy do backend anterior |
| KenkernSurfaceView.kt | 939 | VISÍVEL | modo seguro: Log.e + StatusBar |
| KenkernSurfaceView.kt | 950 | SILENCIOSA CRÍTICA | resize falha → viewport errado sem rastro |
| KenkernSurfaceView.kt | 978 | SILENCIOSA CRÍTICA | picking falha → tap "morre" sem rastro |
| KenkernSurfaceView.kt | 988 | SILENCIOSA | applyFocus falha sem aviso |
| KenkernSurfaceView.kt | 996 | SILENCIOSA | overlay para de atualizar |
| KenkernSurfaceView.kt | 1008 | PARCIAL | frame emergencial (catch externo Log.e) |
| LuaJScriptRuntime.kt | 66, 85 | SILENCIOSA (visível só por estado) | script DESATIVA com motivo, mas sem logcat/console e sem consumidor |
| CkFormat.kt | 144 | PARCIAL | Log.e, mas chamador devolve cena vazia sem aviso |
| CkFormat.kt | 161 | SILENCIOSA CRÍTICA | linha corrupta descartada sem Log |
| CkFormat.kt | 214, 217 | SILENCIOSA | enum desconhecido → default muda semântica |
| CkFormat.kt | 241 | SILENCIOSA CRÍTICA | magic CK1 ausente → false sem Log (não é exceção) |

## Estado DEPOIS do realinhamento (mesmo commit da regra)

- `KenkernLog` (diag/KenkernLog.kt) criado: `Log` + anel de 200 entradas com
  nível/tag/mensagem/exceção; exibido na tela Diagnóstico (seção CONSOLE) e no
  overlay do editor (contagem + últimas linhas). JVM-safe (log android
  desligável nos testes unitários).
- Todas as SILENCIOSAS CRÍTICAS acima corrigidas (save/carga/cena/picking/
  resize/OOM VBO/ck corrupto/magic ausente). PARCIAL/INFRA-DIAG críticas: agora
  também vão para o Console. Cosméticas (prefs, data relativa, cleanup) recebem
  `KenkernLog.w` também.
- Regra de manutenção: toda NOVA ocorrência de `runCatching` entra nesta tabela
  com classificação; runCatching novo em render/formato/carregamento só pode
  existir com registro (ver REGRESSION.md, checklist "Sempre").

## Adições da ETAPA A (botão "Importar modelo" — 2026-11-21)

| Arquivo | Linha | Classificação | O que protegia |
|---|---|---|---|
| AssetMeshLibrary.kt | carga de malha pendente (processarPendentes) | VISÍVEL | falha de leitura/decodificação/upload vira KenkernLog.e UMA vez por path (não reloga por frame) + Nick segue sem malha |
| EngineRenderer.kt | processarPendentes no frame | VISÍVEL | falha inesperada da fila não derruba o frame — KenkernLog.e |
| KenkernSurfaceView.kt | onSurfaceCreated (limpar+destroy) | INFRA-DIAG | destroy do contexto antigo; já registrava |
| Diagnostico.kt | rodarUma (novo caso "importar") | INFRA-DIAG ok | checagem vira FALHOU com motivo |
| ImportModelCommand.kt | rename→copy fallback | VISÍVEL | KenkernLog.w com o arquivo que não apagou (nunca silêncio) |

Regra mantida: nenhum runCatching novo sem registro; a fila de malhas usa
falha-1×-por-path para não inundar o Console por frame.
