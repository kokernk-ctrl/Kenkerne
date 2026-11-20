# KENKERN — CHANGELOG

## fase-2-ok — Projeto & Diagnóstico (app v0.1.13)

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

Formato do changelog: entrada por versão entregue (APK no CI). Cada fase fecha
com a tag `fase-N-ok` e Release pré-lançamento com este texto.
