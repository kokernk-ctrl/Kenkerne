# KENKERN — PROGRESS

**Retome daqui.** Se uma conversa nova começar com o Prompt Mestre v3, leia este
arquivo, BACKLOG.md, ARCHITECTURE.md, DECISIONS.md e REGRESSION.md, e siga o loop
de tarefas do BACKLOG sem refazer o que está pronto.

## Estado agora

- **Fase**: 2 (Projeto & Assets) — **EM CURSO (ABERTA)**. A tag `fase-2-ok` foi
  REMOVIDA em 2026-11-21 (foi criada sem cumprir a Fase 2 do roadmap; a Release
  v0.1.13 foi re-titulada "fase ABERTA"). A tag `fase-1-ok` fica, mas os itens que
  dependem do aparelho (seleção por toque, Play real, paisagem automática, gizmos)
  estão **COMPILADO**, não VALIDADO, até o usuário confirmar (TESTES_PENDENTES.md).
- **Tarefa atual**: fechamento testável da Fase 2 por ETAPAS (A → B → C → D,
  cada uma com CI verde + commit): **ETAPA A (botão Importar modelo) FEITA
  (F2.16 COMPILADO+CI ✓)**; a seguir **ETAPA B (correção dos importadores:
  FBX ASCII, materiais, dedup, LODs, gizmo/Spin/Bob)**, depois **ETAPA C
  (F2.9 .KIMG ETC2/ASTC)** e **ETAPA D (Release v0.1.14-fase2-teste)**. F2.7
  (glTF/GLB, 11 testes) e F2.8 (FBX, 15 testes) COMPILADO+CI ✓.
- **Lua congelada**: o LuaJ existente é o F5.1 (antes F3.1), trabalho ANTECIPADO —
  proibido crescer código Lua antes da Fase 5. Fase 3 = LENTE/LUME/colliders/input.
- **Regras novas** (ROADMAP.md, Regras de fase): (1) tag `fase-N-ok` só com TODOS os
  itens de aceite implementados; (2) nenhum `runCatching` engole erro em silêncio —
  Log + Console (`KenkernLog`) + tela Diagnóstico; auditoria em
  `docs/RUNCATCHING_AUDIT.md`.
- **App**: v0.1.13 (`versionCode 14`) + F2.7/F2.8; suíte JVM 82 testes, verde.

## O que existe (Fases 1–2, validado por CI)

- Tudo da Fase 1 (ver CHANGELOG) MAIS:
- Tela Diagnóstico no Hub (ícone do inseto): checagens APROVADO/FALHOU
  (.ck, .KERN, gltf, fbx, picking, shaders, buffers, glcaps, memória) — EglDiag
  cria contexto pbuffer offscreen na thread de fundo; toda fase adiciona checagens.
- Duplicar projeto; Importar/Exportar .kernpack (SAF); pastas ui/ e worlds/
  (com migração de projetos antigos); ENGINE_VERSION = BuildConfig.VERSION_NAME.
- Fixes da Fase 1 achados pelos testes F2: escapes do .ck; LOD_ENTRY_SIZE
  20→32 + header desalinhado no KernReader (o torus .KERN nunca tinha
  renderizado — agora renderiza).
- Console de erros `KenkernLog` (Log + anel em memória) exibido no Diagnóstico e
  no overlay do editor — nenhum runCatching crítico engole erro em silêncio.
- Importador glTF/GLB → .KERN (F2.7): parser GLB/.gltf + meta de compressão +
  teste de integridade + checagem `gltf` no Diagnóstico.
- Importador FBX binário → .KERN (F2.8): parser 7.1–7.7 (u32/u64, zlib),
  triangulação em leque de polígonos, vértices expandidos por canto, normais/UV
  por camada (Direct/IndexToDirect; UV v'=1−v, DECISIONS #20), hierarquia de
  Models (Lcl/Geometric TRS em graus), merge de instâncias + checagem `fbx` no
  Diagnóstico. FBX ASCII/6.x = erro claro.

## O que falta na Fase 2 (ordem)

1. F2.7: importador glTF/GLB → .KERN — **FEITO** (COMPILADO+CI ✓, 11 testes
   JVM, checagem `gltf` no Diagnóstico; gotchas do GLB documentados no
   cabeçalho do GltfImporter.kt).
2. F2.8: importador FBX → .KERN — **FEITO** (COMPILADO+CI ✓, 15 testes JVM,
   checagem `fbx` no Diagnóstico; gotchas do FBX no cabeçalho do FbxImporter.kt
   e no DECISIONS #20).
3. F2.9: **.KIMG (ASTC/ETC2 com mipmaps)** — TAREFA ATUAL (próxima).
4. F2.10–F2.15: cache LRU, streaming anti-trave, navegador, pré-visualização,
   prefabs, registro de assets (ids estáveis + ref quebrada).
5. FIM DE FASE só quando F2.1–F2.15 estiverem implementados (regra nova) →
   teste de aceite no aparelho → VALIDADO-NO-APARELHO → aí sim tag `fase-2-ok`.

## Armadilhas descobertas (não repetir)

1–8. (Ver git log v0.1.8–v0.1.12: yaw por modo no computeBasis, dedo morto
   no POINTER_UP, ordem de propriedades Kotlin, AlertDialog material3 1.3,
   px de view vs framebuffer, limiares em dp, CI bloqueia APK se teste falha.)
9. Método de teste JVM não pode ter `.` no nome (caractere ilegal p/ JVM) —
   usar vírgulas/palavras ("0,25" ok, "2e-3" ok, ">= " NÃO).
10. Assinaturas EGL14 android-35: eglInitialize(display, major[], off, minor[],
    off) e eglChooseConfig(8 args) — conferir javap antes de escrever.
11. .KERN: toda malha tem ≥1 LOD (default no Builder.build()) — writer sem
    LOD não existe; testes de erro devem partir disso.
12. Diagnóstico NÃO chama GlesBackend.initialize() (marcaria BootTrace).
13. Tag `fase-N-ok` sem todos os itens de aceite = erro de processo (aconteceu
    com `fase-2-ok` e foi revertida em 2026-11-21; regra nova no ROADMAP).
14. `runCatching` silencioso em caminho de render/formato/carregamento é bug de
    processo — usar `KenkernLog` (Log + Console + Diagnóstico), ver
    docs/RUNCATCHING_AUDIT.md.

## Como compilar/testar (ambiente do agente)

```bash
cd /home/z/my-project/Kenkerne
./gradlew -Dorg.gradle.java.home=/home/z/jdk/jdk-21.0.13.1+2 :app:testDebugUnitTest
```
SDK em `/home/z/android-sdk` (`local.properties` aponta). Se o ambiente foi
reciclado: reinstalar cmdline-tools (layout `cmdline-tools/latest`!) +
platforms;android-35 + build-tools;35.0.0 + JDK Temurin 21 em /home/z/jdk
(script /home/z/my-project/scripts/setup_env.sh do agente faz isso).
LuaJ (Fase 5): adicionar em gradle/libs.versions.toml (mirror se rate-limit).
