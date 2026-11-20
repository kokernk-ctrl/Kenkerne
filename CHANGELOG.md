# KENKERN — CHANGELOG

## Realinhamento ao ROADMAP (2026-11-21 — sem APK novo)

- **Tag `fase-2-ok` REMOVIDA** (local, remote e referências): foi criada sem
  cumprir a Fase 2 do roadmap. A Release v0.1.13 no GitHub foi re-titulada
  "v0.1.13 — build da Fase 2 (fase ABERTA; itens de assets pendentes)" — o APK
  fica (o build existe), só a alegação de fase fechada sai. A Fase 2 volta a
  EM CURSO com os pendentes reais: importador glTF/GLB/FBX → .KERN (meta 100 MB
  → ≤ 20 MB + teste de integridade), .KIMG (ASTC/ETC2), cache, streaming
  progressivo anti-trave, navegador de arquivos, pré-visualização, prefabs e
  registro de assets (ids estáveis + referência quebrada).
- **Tag `fase-1-ok` mantida**, mas os itens que dependem do aparelho (seleção
  por toque, Play real, paisagem automática, gizmos) ficam **COMPILADO**, não
  VALIDADO, até o usuário executar o aceite em TESTES_PENDENTES.md.
- **ROADMAP.md renumerado**: LUA = Fase 5 (não 3); Fase 3 = LENTE + LUME +
  colisão + input; importador/.KIMG/cache/streaming/navegador/preview/prefabs/
  registro de assets = Fase 2. BACKLOG.md renumerado 1:1 (F5.1 = LuaJ
  antecipado, congelado; proibido crescer Lua antes da Fase 5).
- **Regras novas de processo** (DECISIONS #16/#17): tag `fase-N-ok` só com
  TODOS os itens de aceite implementados; nenhum `runCatching` engole erro em
  silêncio (Log + Console `KenkernLog` + tela Diagnóstico).
- **Auditoria runCatching**: 62 ocorrências mapeadas (docs/RUNCATCHING_AUDIT.md);
  32 silenciosas nas classes críticas (save silencioso, cena ilegível → vazia,
  OOM de VBO, picking/resize sem rastro…) corrigidas com Console.
- **FIX docs**: KERN_SPEC.md tabela de LODs corrigida (20 → 32 bytes por
  entrada, como no código desde o fix F2.2).
- Testes JVM: KenkernLogTest novo (anel + sem dependência de android.util.Log);
  suíte segue verde.

## v0.1.13 — build da Fase 2 (fase ABERTA; tag `fase-2-ok` foi removida)

- **v0.1.13 (F2.6)** — .KERNPACK: importar/exportar projeto completo via SAF
  (zip com a estrutura de pastas; proteção zip-slip; zero permissões).
  Duplicar projeto no Hub. Pastas `ui/` e `worlds/` (criação + migração de
  projetos antigos). ENGINE_VERSION de fonte única (BuildConfig).
- **v0.1.13 (F2.3)** — Tela **Diagnóstico** no Hub (ícone do inseto): 7
  checagens APROVADO/FALHOU com motivo (.ck, .KERN, picking, shaders, buffers,
  capacidades GL, memória) — contexto EGL pbuffer offscreen próprio.
- **FIXES da Fase 1 desenterrados pelos testes de ida e volta (F2.1/F2.2)**:
  1. `.ck` corrompia strings com aspas/quebras de linha (unescape faltando);
  2. `.KERN` nunca funcionou de verdade: LOD_ENTRY_SIZE errado (20→32) fazia
     o escritor estourar, e o leitor lia o header desalinhado em 14 bytes —
     o torus de teste nunca renderizou (silenciado pelo runCatching).
- Testes JVM novos: CkFormatRoundtripTest (10), KernRoundtripTest (8),
  ProjectManagerDuplicateTest (3), KernpackTest (5).

## fase-1-ok — Fundação (app v0.1.12)

- **v0.1.12** — Olhar horizontal do modo Voo corrigido: a base da câmera lia o
  yaw do ORBITAL no ramo FLIGHT (`flightYawDegrees` nunca era usado). Overlay do
  voo ganha Δ dx/dy em dp do último MOVE. 4 testes JVM novos
  (`FlightLookRegressionTest`), suíte verde.
- **v0.1.11** — Zoom orbital "preso" corrigido (dedo morto reancorado no
  `ACTION_POINTER_UP`); olhar de 2 dedos no Voo (0,25°/dp, clamp ±89°, sem
  roll); overlay da pinça (modo/distância/spans/fator/reset).
- **v0.1.10** — Diagnóstico visível de seleção/gizmo (overlay de toque),
  correções de picking (raio do orbital apontava para trás; hit-test do gizmo
  projetava pelo MODEL em vez do MVP).
- **v0.1.9** — NPE do TransformGizmo corrigido (ordem de inicialização);
  diálogo de crash com stack trace + BootTrace de 6 etapas
  (`filesDir/boot_trace.txt`), layout corrigido em paisagem.
- **v0.1.0–v0.1.8** — Fundação: editor GLES 3.0 (grade infinita, céu, luz
  direcional), Hub de projetos com pastas e `project.kenkern`, `.ck` CK1,
  `.KERN` binário (quantização/LODs/bones), seleção por toque, gizmo de
  transformação, câmera orbital + voo, undo/redo, Play/Stop com timestep fixo,
  classes nativas Spin/Bob, LINKERF (modelo + serialização), autosave.

Formato do changelog: entrada por versão entregue (APK no CI). A tag `fase-N-ok`
só fecha a fase quando TODOS os itens de aceite estiverem implementados
(DECISIONS #16); itens de aparelho só viram VALIDADO após confirmação do usuário.
