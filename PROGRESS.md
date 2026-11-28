# KENKERN — PROGRESS


> **Antes de agir:** leia [HARNESS.md](HARNESS.md) — papéis, linguagem, definição de PRONTO e portões de CI.
**Retome daqui.** Se uma conversa nova começar com o Prompt Mestre v3, leia este
arquivo, BACKLOG.md, ARCHITECTURE.md, DECISIONS.md e REGRESSION.md, e siga o loop
de tarefas do BACKLOG sem refazer o que está pronto.

## Estado agora

- **v0.1.31 — FECHAR ARQUIVOS (F3/F4/F5/F7 + F8/X1/X2/X3)**: painel "Projeto"
  acoplável + 4ª aba "Arquivos" (breadcrumb, busca, ordenação, paginação com
  cache de varredura única, miniaturas, lixeira `.kenkern/lixeira` com undo
  por Commands, abrir por tipo); importar de QUALQUER pasta (SAF múltiplo,
  navegador do aparelho, pasta inteira recursiva, glTF local com .bin);
  `assets.index` com id ESTÁVEL + painel "Problemas" (Corrigir…/Remover);
  Diagnóstico 36 (arquivos/importar-pasta/referencias); identidade da build
  (BUILD_HASH/BUILD_DATE) no Hub/Diagnóstico/relatório; ui.* NUNCA na
  Hierarquia 3D (vão para o TELAR); Inspetor do joystick com efeito ao vivo;
  editor de código legível (cores de token ≥ 4,5:1 + mesma função no cursor).
  Portão 2 corrigido em 3 causas raiz (GITHUB_OUTPUT no script da action,
  dash sem pipefail, Dither.cpp omitido na vendorização do etcpak).
  90 classes / 528 testes JVM / 36 checagens.
- **v0.1.28 — LUZ DO LUME + 30/30 + ONDA 0 COMPILADO**: L1 causa raiz da luz
  (fragment shader usava `vTangent` sem declaração → não compilava →
  fallback de cor sólida → pixel (154,154,154) desde a v0.1.20; corrigido +
  checagem "luz" instrumentada com uniforms no Console e aceite R>200/
  G<100/B<100; ShaderGlslFonteTest valida GLSL na JVM); L2 agenda do
  Diagnóstico 26→30 (play-sair/area/editor/ui RODAM — DiagAgendaTest nasceu
  vermelho "agenda tem 26 de 30"; RoteadorToqueJogo.acao aplicado no
  teste); Onda 0: P1/P2 botões do editor fora da janela de jogo POR ESTADO
  + área segura (insets) + overlay de toque por 3 s com ordem z; P3 abas
  Cena/UI/Código na TopBar (editor de código a 2 toques do Hub); P4 TopBar
  ≤8 botões + menu "mais" (inventário provado na JVM); P5 as 8 fontes OFL
  EMBUTIDAS no APK (assets/fonts, FontesKenkern, sem download — LICENSES.md)
  + linha "FONTES: 8/8" na checagem "ui"; P6 aviso de skinning no texto do
  usuário ("esqueleto/skinning ainda não suportado — Onda 3"). 456 testes
  JVM verdes. **Release `v0.1.28` publicada — aguardando aceite no
  aparelho** (6 itens no TESTES_PENDENTES.md).
- **v0.1.27 — PARADA 3 (U1) COMPILADO**: TELAR base — aba "UI" com canvas
  1920x1080 (zoom/pan/régua/snap 8px), 8 elementos, âncoras 9+esticar com
  área segura nas 3 proporções, camadas (Z/ocultar/travar), Inspetor de UI,
  undo/redo POR COMMAND, .kui (KUI1) com salvar automático, migração do
  Joystick Nick sem perder dados, Play desenha a UI sobre o jogo com toque
  sem conflito, template "Coletar moedas" com tela de UI, checagem "ui".
  431 testes JVM verdes. **Release `v0.1.27-fase2-teste` publicada —
  aguardando aceite no aparelho** (5 itens no TESTES_PENDENTES.md). Pendente
  registrado: TTFs OFL embutidas (meta 24 fontes) — os 8 NOMES já no modelo.
- **v0.1.26 — PARADA 2 (C1/C2) COMPILADO**: C1 editor de código Lua em
  RETRATO (modelo puro LuaCodeModel: tokenizador/undo-redo/indentação por
  balanço/busca/substituir; teclas de apoio; autocompletar offline do
  LuaApiCatalog; Verificar no LuaJ com linha; LazyColumn virtualizada; salvar
  automático; 4 modelos de script; checagem "editor") e C2 Nicks de Área
  funcionais (classe ÁREA filtro/umaVezSo/ligado; on_enter/exit/stay;
  bodies()/contains(); Signal emit/on; checkpoint/respawn na queda; template
  Coletar moedas com classe ÁREA; checagem "area"). 415 testes JVM verdes.
  **Release `v0.1.26-fase2-teste` publicada — aguardando aceite no aparelho**
  (6 itens no TESTES_PENDENTES.md).
- **v0.1.25 — PARADA 1 (P1/P2/P3) COMPILADO**: P1 botões Pausar/Sair
  respondem em Play (causa raiz KenkernSurfaceView.kt:541-542 — onTouchEvent
  consumia TODO toque em Play; correção: fonte única RoteadorToqueJogo +
  regiões reservadas + zIndex + 48dp + BACK sempre sai + watchdog 3 s com
  "Forçar saída" + overlay de toque + checagem "play-sair"); P2 visual
  "Singularidade" (tokens do shader do Hub no Theme.kt — preto quente/cobre/
  âmbar — e componentes únicos KButton/KIconButton/KChip/KToggle/KPanel em
  Hub/TopBar/painéis/diálogos/Diagnóstico/jogo; painéis OPAQUES; contraste
  ≥4,5:1 provado por WCAG); P3 regra de NICK COMPLETO (5 exigências; tabela
  de TODO kind no NICKS.md com a fase em que fica completo; PARCIAIS com
  selo "Em breve" desabilitados + guard com KenkernLog). 388 testes JVM
  verdes. **Release `v0.1.25-fase2-teste` publicada — aguardando aceite no
  aparelho** (6 itens no TESTES_PENDENTES.md).
- **v0.1.24 — ESTABILIZAÇÃO (S1–S8) COMPILADO**: nenhum recurso novo; cada
  correção nasceu de um teste VERMELHO (reprodução antes de corrigir). S1
  Diagnóstico (títulos trocados DiagnosticoScreen.kt:68-92; checagem
  "materiais" cobrava a amostra sem materiais; Copiar relatório com falhas
  no topo); S2 TODO corpo dinâmico cai (NickPlayRuntime simulava só o
  body.player e ignorava o Colisor do estático; overlay de debug de Play);
  S3 Inspetor ao vivo em Play (EspelhoNick na cópia renderizada); S4 tabela
  de TODO kind (KindEmPlay) + Joystick ui.* NUNCA desenha 3D (moldura 2D);
  S5 limite de 15 s com causa visível em toda operação do Hub
  (OperacaoComLimite); S6 Janela de Jogo em tela cheia com Pausar/Sair; S7
  checagem "jogo" (template real, 300 ticks, input sintético — a falha diz
  o passo); S8 fechamento. 353 testes JVM verdes.
  **Release `v0.1.24-fase2-teste` publicada — aguardando aceite no aparelho**
  (8 itens no TESTES_PENDENTES.md).
- **v0.1.20 — PARADA A (H1–H3 + T0.4) COMPILADO**: H1 auxiliares voltam depois
  do Stop (causa raiz KenkernSurfaceView.kt:784 — condição de picking INVERTIDA
  do E5; fix em AuxiliaresPreferencia + 5 testes); H2 luz Nick ilumina a cena
  (LumeCena — direcional substitui o sol, ≤4 pontuais/spot por contribuição,
  atenuação suave, auxiliares com alcance/ângulo REAIS, checagem "luz");
  H3 câmera Nick (Lente: fov/near/far/orto, "Câmera principal" só uma por cena,
  "Ver pela câmera" com moldura, follow com Alvo/distância/altura, aviso único
  sem câmera); T0.4 material PBR COMPLETO como asset no .KERN v3 (5 mapas +
  fatores, shader único usa o que existe, tangentes na GPU, skinning com causa
  clara no ImportReport/Console, checagem "materiais"). 277 testes JVM verdes.
  **Release `v0.1.20-fase2-teste` publicada — aguardando aceite no aparelho**
  (16 itens no TESTES_PENDENTES.md).
- **v0.1.21 — PARADA B (T0.5 + M1.1 + M1.2) COMPILADO**: T0.5 Hub novo
  (fundo 2.5D com paralaxe + buraco negro sutil a 30 FPS, pausa quando
  invisível, "reduzir animações", atalhos Modelar/Animar/Texturizar →
  projeto "Sandbox"); M1.1 Lua ligado aos Nicks (classe Script + variáveis
  públicas params + nick/scene/engine + eventos + LIMITE DE INSTRUÇÕES
  provado no "release"); M1.2 Colliders (todas as formas param pela AABB da
  forma — sem solver completo), gatilhos on_trigger na borda e
  on_collision; gravidade básica. 297 testes JVM verdes. **Release
  `v0.1.21-fase2-teste` publicada — aguardando aceite no aparelho.**
- **v0.1.22 — PARADA C (M1.3–M1.6) COMPILADO**: M1.3 input de jogo
  (Joystick Nick editável + botões de ação + ações nomeadas + pulo +
  checagem "joystick"); M1.4 câmera 3ª pessoa que não atravessa o chão;
  M1.5 UI de jogo mínima por Lua (ui.texto/contador/limpar + overlay);
  M1.6 Play/Stop reais com snapshot completo PROVADO na JVM. 310 testes
  JVM verdes. **Release `v0.1.22-fase2-teste` publicada.**
- **v0.1.23 — PARADA D (M1.7 + M1.8) COMPILADO — MARCO M1 FECHADO**: M1.7
  template "Coletar moedas" (projeto nasce jogável: chão/jogador/surgimento/
  câmera/3 moedas-gatilho com script de coleta + ui.contagem compartilhada;
  seletor de template ao criar); M1.8 exportar APK do jogo por botão
  (workflow_dispatch com o projeto; token só em memória; docs/EXPORTAR_JOGO.md).
  316 testes JVM verdes. **Release `v0.1.23-fase2-teste` publicada.**
- **Próximos (ROADMAP)**: resto da Fase 2 (F2.11 streaming, F2.12 navegador,
  F2.13 pré-visualização, F2.14 prefabs, F2.15 registro de assets) → Fase 3
  completa → animação/ossatura → Lua completo (Fase 5) → …
- **T0.4 parcial — resolução de texturas**: opção Original, redução proporcional
  e cache por resolução efetiva e qualidade ETC2. O usuário aprovou no aparelho
  a comparação 512 × Original e a reabertura do projeto. (O PBR completo entrou
  na v0.1.20 — ver CHANGELOG.)
- **v0.1.19 — G1–G3 VALIDADOS NO APARELHO**: o usuário confirmou os
  sete testes de layout em `TESTES_PENDENTES.md` (TopBar, Adicionar Nick e
  controles de voo). O aceite é específico desta versão e não fecha a Fase 2.
- **Ordem de execução (adendo v4, 2026-11-22)**: v0.1.17 FEITA → **T0.1–T0.3
  FEITAS + E1–E6 FEITAS (v0.1.18, campo do usuário)**: PARADA 1 respondida
  com 6 bugs corrigidos (E1 símbolos JNI do handler nativo + prova no CI com
  llvm-nm; E2 Diagnóstico com timeout 30 s/cancelar/tempo por checagem;
  E3 janela Adicionar Nick alcançável até o fim + barreira de toque; E4
  painéis flutuantes movem/redimensionam (era var comum sem recomposição),
  clamp no viewport, 48dp, acoplar/resetar, por orientação; E5 auxiliares de
  editor para Nicks sem malha (seta/esfera/cone/pirâmide com FOV real/caixa/
  losango, cor por grupo, 24dp, somem em Play) + collider sobre o nick (era
  no mini-viewport do canto) + luz NÃO ilumina (entra no LUME — NICKS.md);
  E6 StatusBar "N nicks · M malhas") — Release `v0.1.18-fase2-teste`
  publicada → **aguardando teste do USUÁRIO** (18 itens no
  TESTES_PENDENTES.md) → T0.4 fidelidade de importação → T0.5 Hub novo
  (**PARADA 2**) → M1 (marco jogável, M1.3 = input de tela/joystick,
  DECISIONS #31) → resto da Fase 2 → Fase 3… (ROADMAP.md).
- **Fase**: 2 (Projeto & Assets) — **EM CURSO (ABERTA)**. Faltam F2.11
  streaming, F2.12 navegador, F2.13 pré-visualização, F2.14 prefabs e F2.15
  registro de assets. A tag `fase-2-ok` permanece REMOVIDA (não criar até
  TODOS os itens + "aprovado" do usuário). Itens sem confirmação de teste
  no aparelho permanecem **COMPILADO**, não VALIDADO (TESTES_PENDENTES.md).
- **Tarefa concluída (v0.1.18)**: 6 bugs do aparelho E1–E6 (relatório por
  bug com causa raiz arquivo/linha no CHANGELOG.md). CI: 222 testes JVM,
  assembleRelease, **E1: llvm-nm confere TODA external fun × a .so do APK**,
  i18n en/pt; lint sem erros novos (2 pré-existentes). Correção do próprio
  E1 no CI: teste instalava handlers de sinal na JVM (HotSpot usa SIGSEGV
  para NPE implícito → exit 139); restaurarSinaisNativo devolve as ações
  originais e o teste restaura em finally — no app o handler segue ativo.

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
- **Suíte JVM**: 222 testes, verde (testes nativo×Kotlin rodam com a MESMA
  fonte C++ compilada para o host pela task compilarNativoHost; o teste do
  handler RESTAURA os sinais da JVM no finally — bug do CI, exit 139).
- **Lua congelada**: o LuaJ existente é o F5.1, trabalho ANTECIPADO —
  proibido crescer código Lua antes da Fase 5. Fase 3 = LENTE/LUME/colliders/input.
- **Regras** (ROADMAP.md, Regras de fase): (1) tag `fase-N-ok` só com TODOS os
  itens de aceite implementados; (2) nenhum `runCatching` engole erro em
  silêncio — Log + Console (`KenkernLog`) + tela Diagnóstico; auditoria em
  `docs/RUNCATCHING_AUDIT.md`.
- **App**: v0.1.23 (versionCode 24) — release de pré-lançamento
  `v0.1.23-fase2-teste` com APK assinado (CI: 316 testes + assembleRelease +
  llvm-nm E1 sobre a .so do APK).

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
