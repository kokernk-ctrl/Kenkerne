# KENKERN — BACKLOG

Tarefa pequena = 1 commit. Status: `TODO` · `DOING` · `COMPILADO` (CI verde, sem
validação no aparelho) · `VALIDADO-NO-APARELHO` · `BLOQUEADO`.

Regras: os IDs aqui coincidem 1:1 com as fases do ROADMAP.md (`F<N>.<k>`).
Tag `fase-N-ok` só com TODOS os itens da fase implementados (ROADMAP.md, Regras
de fase). Nenhum `runCatching` engole erro em silêncio (auditoria:
`docs/RUNCATCHING_AUDIT.md`).

## Fase 1 — Fundação (tag `fase-1-ok` mantida; validação de aparelho parcial)

| ID | Tarefa | Depende de | Status |
|----|--------|-----------|--------|
| F1.1 | Editor GLES3: viewport, grade infinita, céu, luz direcional | — | FEITA (CI ✓; aparelho ver F1.6+) |
| F1.2 | `.ck` CK1 (texto versionável) + autosave atômico | — | FEITA (roundtrip JVM ✓) |
| F1.3 | `.KERN` binário (quantização u16, octaédrica 8:8, LODs, bones/clusters reservados) | — | FEITA (roundtrip JVM ✓; fixes F2.1/F2.2) |
| F1.4 | Hub de projetos (criar/listar/renomear/excluir + `project.kenkern`) | — | FEITA (usado diariamente pelo usuário) |
| F1.5 | LINKERF (modelo de portas/links + serialização), undo/redo | — | FEITA (CI ✓) |
| F1.6 | Seleção por toque (picking por raio) | F1.1 | **COMPILADO** (aguarda aparelho) |
| F1.7 | Gizmo de transformação (mover/rotar/escalar por alça) | F1.6 | **COMPILADO** (aguarda aparelho) |
| F1.8 | Play real (timestep fixo + classes Spin/Bob) | F1.1 | **COMPILADO** (aguarda aparelho) |
| F1.9 | Paisagem automática (sensorLandscape + insets) | — | **COMPILADO** (aguarda aparelho) |
| F1.10 | Crash diag (diálogo + stack trace) + BootTrace + debug overlay | — | FEITA (exercitado em v0.1.9–v0.1.12) |
| F1.11 | Câmeras Orbital + Voo (pinça, joystick, olhar 1/2 dedos) | F1.1 | FEITA (regressões v0.1.10–v0.1.12 corrigidas após reporte do usuário) |

Aceite completo da Fase 1 (passo a passo) em TESTES_PENDENTES.md — itens F1.6,
F1.7, F1.8 e F1.9 só viram VALIDADO-NO-APARELHO depois da execução pelo usuário.

## Fase 2 — Projeto & Assets (EM CURSO — ABERTA; tag `fase-2-ok` foi removida)

| ID | Tarefa | Depende de | Status |
|----|--------|-----------|--------|
| F2.1 | Teste JVM: ida e volta do .ck (+ FIX: escapes voltavam corrompidos) | — | COMPILADO+CI ✓ (pendente aparelho via Diagnóstico) |
| F2.2 | Teste JVM: ida e volta do .KERN (+ FIX: LOD_ENTRY_SIZE e header desalinhado — torus nunca renderizou) | — | COMPILADO+CI ✓ (pendente aparelho) |
| F2.3 | Tela Diagnóstico no Hub (9 checagens APROVADO/FALHOU; a 8ª, `gltf`, entrou no F2.7 e a 9ª, `fbx`, no F2.8) | F2.1, F2.2 | COMPILADO+CI ✓ (pendente aparelho) |
| F2.4 | Pastas ui/worlds + ENGINE_VERSION de fonte única (BuildConfig) | — | COMPILADO+CI ✓ |
| F2.5 | Duplicar projeto (+ 3 testes JVM) | — | COMPILADO+CI ✓ (pendente aparelho) |
| F2.6 | .KERNPACK import/export via SAF (+ 5 testes JVM, zip-slip) | F2.4 | COMPILADO+CI ✓ (pendente aparelho) |
| F2.7 | **Importador glTF/GLB → .KERN**: parser GLB (chunks JSON+BIN) e .gltf (data URI), POSITION/NORMAL/TEXCOORD_0/indices (i8/u8/i16/u16/u32/f32), transform dos nodes (matriz column-major OU TRS/quaternion), merge de primitivas em 1 KernMesh, meta de compressão (ImportReport: bytes origem → .KERN, razão, meta do roadmap 100 MB → ≤ 20 MB anotada), teste de integridade (AABB, contagem de triângulos, erro máx pós-quantização em passos, índices no range, LODs) + checagem `gltf` nova no Diagnóstico | F2.2 | COMPILADO+CI ✓ (11 testes JVM; FBX é o F2.8 separado) |
| F2.8 | **Importador FBX → .KERN**: parser binário 7.1–7.7 (offsets u32/u64, arrays zlib), PolygonVertexIndex (polígono = índice negativo), triangulação em leque de quad/ngon, vértices expandidos por canto, normais/UV (ByPolygonVertex/ByVertice/ByPolygon × Direct/IndexToDirect; ausente = acumulada; UV v'=1−v), hierarquia de Models com Lcl/Geometric T-R-S em graus, merge de instâncias (geometria compartilhada), KernIntegrity + ImportReport, checagem `fbx` no Diagnóstico; FBX ASCII/6.x = erro claro | F2.7 | COMPILADO+CI ✓ (15 testes JVM; DECISIONS #19/#20) |
| F2.9 | .KIMG: texturas ASTC/ETC2 com mipmaps | F2.7 | TODO |
| F2.10 | Cache de assets em disco (LRU) | F2.9 | TODO |
| F2.11 | Streaming progressivo anti-trave (carregamento fora do frame, sem bloquear a thread GL) | F2.10 | TODO |
| F2.12 | Navegador de arquivos do projeto (painel no editor) | F2.7 | TODO |
| F2.13 | Pré-visualização de assets no navegador (malha/textura) | F2.12 | TODO |
| F2.14 | Prefabs (criar/inserir/instância) | F2.12 | TODO |
| F2.15 | Registro de assets com ids estáveis + detecção de referência quebrada | F2.7 | TODO |
| F2.16 | **Botão "Importar modelo"** na TopBar (ETAPA A do fechamento da Fase 2): SAF arquivo (.glb/.gltf/.fbx/.zip) e Pasta (OpenDocumentTree); importação em background (Dispatchers.Default/IO) com progresso por etapas (lendo/triangulando/gravando/validando) e cancelar; origem copiada em streaming para assets/models/origem/ + .KERN em assets/models/; UM Command no Undo/Redo (ImportModelCommand — undo remove Nick e recolhe assets para a lixeira, redo devolve); classe "Malha" no Nick com path + AABB (picking/foco pela malha real); render da malha importada via AssetMeshLibrary (carga fora do flush, orçamento 6 ms); ImportReport com tamanho antes/depois, triângulos, tempo, pico de memória e KernIntegrity + "Copiar relatório"; > 200 MB pede confirmação; resolução de glTF externo (.bin/texturas) via pasta SAF e .zip com bloqueio de zip-slip; checagem "importar" no Diagnóstico (modelo embutido) | F2.7, F2.8 | COMPILADO+CI ✓ (pendente aparelho; 15 testes JVM novos)
| F2.17 | **Correção dos importadores** (ETAPA B do fechamento da Fase 2): **FBX ASCII** (B.2) parser de texto com a MESMA saída do binário (Vertices/PolygonVertexIndex/camadas/TRS/Connections; nomes "Tipo::Nome" limpos; versão não suportada = erro CLARO com a versão encontrada); **materiais** (B.3) glTF (baseColorFactor/texture, metallic/roughness, normalTexture, doubleSided) e FBX (DiffuseColor/Opacity, Video com Content embutido) gravados no .KERN **v2** como tabela de materiais + grupos por submalha (offset/count/índice), skinning reservado intacto (aviso "contém esqueleto"); **dedup** (B.4) de vértices (pos+normal+UV exatos, 2 passadas sem mutação) e materiais iguais, erro ≤ 1 passo de quantização; **LODs** (B.5) 3 níveis por agrupamento de vértices em grade com tabela e AABB próprias; **remoção do que não é usado** (B.6): tangentes só com normal map (ou opção manter), UV2/cores nunca lidas; **regressão gizmo/Spin/Bob** (B.7): quaternion e Mat4.setTRS preservam comprimento em 0/30/90/180/270° + composição pai×filho + euler ida e volta | F2.16 | COMPILADO+CI ✓ (18 testes JVM novos — 111 verdes)

Meta de compressão da Fase 2 (100 MB → ≤ 20 MB) é da PIPELINE de assets completa:
F2.7 já emite o relatório (meta); deduplicação, LODs gerados e compressão de
índices aprofundam a razão nos itens seguintes.

## Fase 3 — LENTE + LUME + colisão + input (PLANEJADA — detalhar no início)

Sketch: LENTA (câmera de jogo como Nick) · LUME (luzes como Nicks:
direcional/ponto/spot + sombras) · colisão física (AABB/esfera/raio) · input de
jogo. IDs F3.1… na abertura da fase.

## Fase 4 — TRAMA (PLANEJADA — detalhar no início)

Materiais (albedo/normal/rough/metal) e pintura básica sobre o .KIMG (F2.9).

## Fase 5 — LUA (PLANEJADA; F5.1 foi ANTECIPADO)

**Regra: proibido crescer código Lua antes da Fase 5.** O que existe de LuaJ é
trabalho antecipado, congelado.

| ID | Tarefa | Depende de | Status |
|----|--------|-----------|--------|
| F5.1 | (ANTECIPADO, ex-F3.1) LuaJ 3.0.1 + `ScriptRuntime` + `LuaJScriptRuntime` (compila sem rodar; orçamento de tempo por tick; sandbox sem os/io/require) + 8 testes JVM | — | COMPILADO+CI ✓ — CONGELADO (não crescer) |
| F5.2 | Ponte de API (nick.position/rotation/scale/name/active, scene.find, engine.time/dt, nick.class) com erro que DESATIVA o script + testes JVM | F5.1 | TODO — detalhar na abertura da Fase 5 |
| F5.3 | Componente de script no Nick (campo no .ck — CK1 cresce com SCRIPT path=…) + execução no Play | F5.2 | TODO |
| F5.4 | Editor simples de script no app (painel) + scripts de exemplo | F5.2 | TODO |
| F5.5 | Checagem Lua no Diagnóstico + LUA_API.md oficial congelado | F5.4 | TODO |

GOTCHA LuaJ 3.0.1 (do trabalho antecipado): registrar MathLib precisa de
`package.loaded` como mesa — plantar mesa vazia, NÃO instalar PackageLib.

## Fases 6–13 (PLANEJADAS — detalhar no início de cada fase)

- **6 CÉU** — dia/noite, nuvens/estrelas/clima.
- **7 Natureza** — TERRA (terreno), VERDE (vegetação instanciada), MARÉ (água), VENTO.
- **8 OSSATURA + LINHA** — skeleton, rig, skinning, timeline de animação, animações glTF.
- **9 ZONA** — campos/áreas de detecção.
- **10 KENA + KELU** — geometria virtualizada (GPU-driven/compute), iluminação global.
- **11 TELAR + FALA + MENTE** — editor de UI estilo Canva, diálogos, IA de NPCs
  (KAI BLOQUEADO até chave de API própria do usuário).
- **12 Orçamento mobile + MUNDOS + RODANTE** — classes de aparelho, térmico,
  resolução dinâmica, multi-mundos, veículos, exportação via GitHub Actions.
- **13 Vulkan** — `RenderBackend` Vulkan via NDK/JNI com paridade GLES e fallback.

Detalhar o backlog no início de cada fase (ver ROADMAP.md).
