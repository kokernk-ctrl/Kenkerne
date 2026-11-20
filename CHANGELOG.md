# KENKERN — CHANGELOG

## ETAPA A — Botão "Importar modelo" na TopBar (fechamento da Fase 2)

- **Importar modelo (SAF)**: botão novo na TopBar (ícone Importar, 40dp,
  contentDescription) com menu "Arquivo (.glb, .gltf, .fbx, .zip)" e
  "Pasta (glTF com .bin e texturas)" (ACTION_OPEN_DOCUMENT_TREE). Importação
  RODA EM BACKGROUND (Dispatchers.Default/IO) com progresso por etapas
  ("lendo", "triangulando", "gravando .KERN", "validando") e botão CANCELAR —
  nunca trava a UI nem a thread GL.
- **Pipeline**: origem copiada em STREAMING (nunca 2× na memória; limite duro
  500 MB) para `assets/models/origem/`; .KERN gravado em `assets/models/`
  (nome único — colisão nunca sobrescreve); Nick criado com a classe "Malha"
  (path + AABB + escalaFit), filho do selecionado ou da raiz do Mundo; opção
  "Ajustar ao tamanho (~2 m)" aplica a escala NO Nick (o .KERN fica fiel).
- **UM Command no Undo/Redo** (`ImportModelCommand`): desfazer remove o Nick e
  recolhe os assets para `assets/.lixeira-import/`; refazer devolve tudo.
- **Relatório** (diálogo + "Copiar relatório"): tamanho antes/depois e razão,
  vértices/triângulos/LODs, materiais, avisos (skipping de esqueleto),
  tempo, pico de memória e resultado do KernIntegrity. Arquivo > 200 MB pede
  confirmação; erro sempre CLARO (nome + causa + o que fazer) na tela e no Console.
- **Render da malha importada**: `AssetMeshLibrary` carrega os .KERN usados
  pela cena FORA do flush (fila com orçamento de 6 ms por frame — anti-trave);
  picking e Focar passam a usar a AABB REAL da malha (classe "Malha"), não só
  o cubo de 1 m.
- **glTF externo**: .bin e texturas resolvidos contra a pasta SAF ou o conteúdo
  do .zip (resolver com bloqueio de zip-slip e caminhos com ".." recusados com
  erro claro).
- **Diagnóstico**: checagem nova "importar" (pipeline completa com modelo
  embutido + nome único + undo/redo do Command + glTF externo em .zip).
- Testes JVM: 15 novos (ModelImporterTest) — total da suíte: 96, verde.

## F2.8 — Importador FBX → .KERN (sem APK novo ainda)

- **Importador FBX binário 7.1–7.7** (`assets/import/FbxImporter.kt`): header
  "Kaydara FBX Binary" + versão u32; records de node com offsets u32 (< 7500) ou
  u64 (≥ 7500); propriedades i16/bool/i32/f32/f64/i64, strings (nomes "A::B"
  viram "B") e arrays 'd'/'i'/'f'/'l'/'b' com encoding 0 (raw) e 1 (zlib/Inflater
  com validação de tamanho). Footer ignorado. Limites de profundidade/contagem
  e validação de endOffset/propsLen contra corrupção — erro CLARO, nunca
  silêncio (regra #17; o arquivo não tem UM runCatching).
- **Malha**: Vertices + PolygonVertexIndex (fim de polígono = índice negativo,
  `idx.inv()`); polígonos de N cantos triangulados em LEQUE (quad/ngon → 2+n-3
  triângulos, preservando winding); vértices EXPANDIDOS por canto (hard edges
  corretos nativamente; dedup fica para a pipeline de compressão).
- **Normais/UV**: primeira camada de LayerElementNormal/UV (3 e 2 componentes;
  camadas extras = AVISO no KenkernLog); mappings ByPolygonVertex/ByVertice/
  ByPolygon com Direct e IndexToDirect; normais ausentes = acumulação suave por
  face (mesma matemática do glTF); UV convertida para a convenção glTF
  (v' = 1 − v — DECISIONS #20).
- **Hierarquia/instâncias**: Connections OO monta pais/filhos de Model;
  mundo = pai × TRS local (Lcl Translation/Rotation em GRAUS/Scaling) × TRS
  geométrico (Geometric T/R/S); geometria compartilhada por 2+ Models =
  instâncias fundidas (offset de índices); PreRotation/PostRotation presentes =
  AVISO no KenkernLog (malha estática não usa; rigs são da Fase 8).
- **Saída idêntica ao glTF**: KernWriter.Builder → .KERN (quantização u16 +
  octaédrica 8:8) + KernIntegrity (roundtrip real) + ImportReport reutilizado
  (geometrias×instâncias no lugar de primitivas).
- **Fora de escopo com erro claro**: FBX ASCII/texto (pede binário), FBX 6.x/5.x
  (pede conversão), polígono aberto, índice fora do range.
- **Diagnóstico**: checagem nova `fbx` (quad + cubo compartilhado por 2 Models +
  erro claro) — 9 checagens agora.
- Testes JVM novos: FbxImporterTest (15) + FbxAmostras (mini-escritor de FBX
  binário sintético — quad, ngon, ByVertice, sem normais, zlib, cubo com 2
  Models, v7500, ASCII, 6.x, polígono aberto). Suíte: 67 → 82 testes, verde.
  `assembleRelease` verde.

## F2.7 — Importador glTF/GLB → .KERN (app v0.1.13, sem APK novo ainda)

- **Importador glTF 2.0 completo** (`assets/import`): GLB (chunks JSON+BIN com
  magic/offsets/limites validados) e .gltf (buffers em data URI; buffer externo
  = erro CLARO com orientação). POSITION/NORMAL/TEXCOORD_0/índices nos
  componentTypes i8/u8/i16/u16/u32/f32; só primitivas TRIANGLES (mode≠4 = erro
  claro). Transform dos nodes (matriz column-major OU TRS/quaternion, herança
  de pais) aplicado aos vértices; NORMAL pela matriz 3×3 renormalizada; NORMAL
  ausente = acumulação suave de faces. Todas as primitivas da cena padrão
  fundidas em 1 KernMesh; saída SEMPRE pelo KernWriter (quantização u16 +
  octaédrica 8:8).
- **Meta de compressão**: `ImportReport` (bytes origem → bytes .KERN, razão,
  vértices/triângulos/primitivas/nodes/lods, flag da meta do roadmap 100 MB →
  ≤ 20 MB) — a meta de 5× é da pipeline completa (dedup/LODs/índices, F2.8+);
  mesh denso já sai ~1,3× menor.
- **Teste de integridade** (`KernIntegrity`): roundtrip pelo KernReader REAL —
  contagens, índices no range e idênticos, AABB do header, erro máximo de
  posição pós-dequantização medido em PASSOS de quantização, tabela de LODs.
- **FIX CRÍTICO da Fase 1 achado pelo importador**: rotação de quaternion com
  meia amplitude — `Quaternion.toMatrix` e `Mat4.setTRS` tinham os produtos com
  `w` SEM o fator 2 (90° em Y mapeava (0,0,1) para comprimento 0,5). Todo Nick
  girando (Spin/Bob), o gizmo e o torus entortavam/encolhiam em vez de girar.
  Play nunca tinha sido validado no aparelho — por isso invisível.
  Fix + `QuaternionMatrixRegressionTest` (REGRESSION.md).
- **Diagnóstico**: checagem nova `gltf` (gera GLB na memória → importa → valida
  integridade + meta + erro claro) — 8 checagens agora.
- Testes JVM novos: GltfImporterTest (10), QuaternionMatrixRegressionTest (5),
  KenkernLogTest (5). Suíte: 37 → 67 testes, verde.
- Dependência só de TESTE: `org.json:json` (mesma API nativa do Android; não
  entra no APK).

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
