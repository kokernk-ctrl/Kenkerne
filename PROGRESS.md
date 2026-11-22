# KENKERN — PROGRESS

**Retome daqui.** Se uma conversa nova começar com o Prompt Mestre v3, leia este
arquivo, BACKLOG.md, ARCHITECTURE.md, DECISIONS.md e REGRESSION.md, e siga o loop
de tarefas do BACKLOG sem refazer o que está pronto.

## Estado agora

- **Ordem de execução (adendo v4, 2026-11-22)**: v0.1.17 FEITA (Release
  publicada, aguardando aceite no aparelho) → **BLOCO 0**: T0.1 ✓COMPILADO
  (idiomas en/pt + seletor no Hub + checagem de CI) → T0.2 ✓COMPILADO
  (NickKindRegistry + CK2 + migração CK1, 17 tipos IMPLEMENTADOS no NICKS.md) →
  T0.3 ✓COMPILADO (janela Adicionar Nick + cabeçalho de tipo + seletor de forma
  do collider com wireframe) — **PARADA 1: aguardando teste do USUÁRIO**
  (passo a passo no TESTES_PENDENTES.md) → T0.4 fidelidade de importação →
  T0.5 Hub novo (**PARADA 2**) → M1 (marco jogável, M1.3 = input de
  tela/joystick, DECISIONS #31) → resto da Fase 2 → Fase 3… (ROADMAP.md).
- **Fase**: 2 (Projeto & Assets) — **EM CURSO (ABERTA)**. Faltam F2.11
  streaming, F2.12 navegador, F2.13 pré-visualização, F2.14 prefabs e F2.15
  registro de assets. A tag `fase-2-ok` permanece REMOVIDA (não criar até
  TODOS os itens + "aprovado" do usuário). Itens que dependem do aparelho
  estão **COMPILADO**, não VALIDADO (TESTES_PENDENTES.md).
- **Tarefa concluída (v0.1.17)**: BUGS A/B do aparelho + núcleo nativo C++.
  **A**: regra "cena = tela" (MalhaNick.desenhavel no render/picking/foco),
  torus fora do viewport (só no Diagnóstico offscreen), StatusBar "N nicks ·
  M desenhados", checagem "cena = tela". **B1**: CronometroImport com tabela
  de ms e % por etapa no ImportReport/Console. **B2**: limite de textura
  1024 padrão (512/1024/2048 no diálogo), ETC2 rápido padrão (T/H só alta),
  blocos+texturas paralelos, cancelamento < 1 s, progresso por textura; +
  correção da ordem de blocos do RGBA. **B3**: NDK/CMake fixados (27.3.13750724
  / 3.22.1), NativeCore + handler de sinais (native_crash.txt no diálogo de
  crash), checagem "nativo", R8 keeps. **B4**: etcpak (BSD-3) vendorizado,
  JNI ByteBuffer direto multithread, fallback Kotlin, checagem "etc2 nativo"
  (host: nativo 17 ms × Kotlin 5.508 ms em 1024²). **B6**: meshoptimizer
  (MIT) vendorizado — dedup/cache/LOD com erro reportado, checagem "malha
  nativa". **20 checagens no Diagnóstico**; third_party/ com LICENSES.md.
- **Suíte JVM**: 184 testes, verde (testes nativo×Kotlin rodam com a MESMA
  fonte C++ compilada para o host pela task compilarNativoHost).
- **Lua congelada**: o LuaJ existente é o F5.1, trabalho ANTECIPADO —
  proibido crescer código Lua antes da Fase 5. Fase 3 = LENTE/LUME/colliders/input.
- **Regras** (ROADMAP.md, Regras de fase): (1) tag `fase-N-ok` só com TODOS os
  itens de aceite implementados; (2) nenhum `runCatching` engole erro em
  silêncio — Log + Console (`KenkernLog`) + tela Diagnóstico; auditoria em
  `docs/RUNCATCHING_AUDIT.md`.
- **App**: v0.1.17 (versionCode 18) — release `v0.1.17-fase2-teste` com APK
  assinado (2.094.813 B; libkenkern.so 463 KB arm64-v8a).

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

1. F2.7: importador glTF/GLB → .KERN — **FEITO** (COMPILADO+CI ✓).
2. F2.8: importador FBX → .KERN — **FEITO** (COMPILADO+CI ✓).
3. F2.9: .KIMG (ETC2/mipmaps) + cache por hash + GPU — **FEITO** (COMPILADO+CI ✓).
4. F2.16: botão Importar modelo — **FEITO** (COMPILADO+CI ✓).
5. F2.17: correção dos importadores (FBX ASCII, materiais, dedup, LODs) —
   **FEITO** (COMPILADO+CI ✓).
6. **F2.11**: streaming progressivo anti-trave — TODO.
7. **F2.12**: navegador de arquivos do projeto — TODO.
8. **F2.13**: pré-visualização de assets — TODO.
9. **F2.14**: prefabs — TODO.
10. **F2.15**: registro de assets com ids estáveis + ref quebrada — TODO.
11. FIM DE FASE só quando F2.1–F2.15 estiverem implementados (regra do
   ROADMAP) → teste de aceite no aparelho → VALIDADO-NO-APARELHO → aí sim
   tag `fase-2-ok`.

## Auditoria v0.1.15 (V1–V4; detalhes no CHANGELOG)

- V1: tamanho do APK provado com dexdump/apkanalyzer/mapping.txt; checagem
  `release` nova no Diagnóstico (guarda anti-R8); nenhuma regra -keep nova.
- V2: razões REAIS em 4 modelos Khronos (0,62×–4,25× sem/com camada); meta
  5× NÃO atingida — alavancas listadas; Deflate em disco do .KIMG adotado
  (37–61% menos disco, carga +8–16 ms por textura 2048²); GPU ETC2 vs RGBA8
  = 87% de economia (é aí que a compressão realmente melhora).
- V3: Fase 2 ABERTA (5 itens pendentes); ASTC = só DETECÇÃO no Diagnóstico
  (encoder ASTC NÃO existe — pendência no BACKLOG); limitações dos
  importadores avisadas no ImportReport.

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
