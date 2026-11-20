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
| F2.3 | Tela Diagnóstico no Hub (7 checagens APROVADO/FALHOU) | F2.1, F2.2 | COMPILADO+CI ✓ (pendente aparelho) |
| F2.4 | Pastas ui/worlds + ENGINE_VERSION de fonte única (BuildConfig) | — | COMPILADO+CI ✓ |
| F2.5 | Duplicar projeto (+ 3 testes JVM) | — | COMPILADO+CI ✓ (pendente aparelho) |
| F2.6 | .KERNPACK import/export via SAF (+ 5 testes JVM, zip-slip) | F2.4 | COMPILADO+CI ✓ (pendente aparelho) |
| F2.7 | **Importador glTF/GLB → .KERN**: parser GLB (chunks JSON+BIN) e .gltf (data URI), POSITION/NORMAL/TEXCOORD_0/indices, transform dos nodes (TRS/matriz), merge de primitivas em 1 KernMesh, meta de compressão (relatório bytes origem → .KERN, meta do roadmap 100 MB → ≤ 20 MB), teste de integridade (AABB, contagem de triângulos, erro máx pós-quantização, índices no range) + checagem nova no Diagnóstico | F2.2 | DOING — plano commitado antes do código (10 linhas, ver histórico do commit) |
| F2.8 | Importador FBX → .KERN (depois do glTF/GLB — DECISIONS #11) | F2.7 | TODO |
| F2.9 | .KIMG: texturas ASTC/ETC2 com mipmaps | F2.7 | TODO |
| F2.10 | Cache de assets em disco (LRU) | F2.9 | TODO |
| F2.11 | Streaming progressivo anti-trave (carregamento fora do frame, sem bloquear a thread GL) | F2.10 | TODO |
| F2.12 | Navegador de arquivos do projeto (painel no editor) | F2.7 | TODO |
| F2.13 | Pré-visualização de assets no navegador (malha/textura) | F2.12 | TODO |
| F2.14 | Prefabs (criar/inserir/instância) | F2.12 | TODO |
| F2.15 | Registro de assets com ids estáveis + detecção de referência quebrada | F2.7 | TODO |

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
