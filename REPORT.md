# REPORT — v0.1.31

## Itens feitos

- **X3 — editor de código invisível/mudando com o cursor** — commit `d03545c`
  - Causa raiz: `EditorLuaScreen.kt:128` mapeava cor de API para `KenkernSurfaceHigh` (#2D2D30 — 1,21:1, cor de SUPERFÍCIE como cor de TEXTO) e comentário para `KenkernTextLow` (3,27:1); a linha com o cursor era um BasicTextField PLANO sem spans (`EditorLuaScreen.kt:735`) — os tokens mudavam de cor ao mover o cursor.
  - Teste que falhou antes: `X3ContrasteEditorLuaTest` (1,21:1 e 3,27:1 < 4,5) e `X3RealceLinhaTest` (a linha do cursor não tinha spans).
  - Teste que passou depois: os mesmos, com as 5 cores de token em `Theme.kt` (8,73/5,86/6,15/5,65/12,53:1) e a MESMA função pura `realceLinhaLua` no Text e no VisualTransformation.
  - Não validado: leitura real no aparelho (item 8 do TESTES_PENDENTES).

- **F8 — identidade da build** — commit `8488a63`
  - Causa raiz: não existia registro de hash/data — build sem identidade não podia ser distribuída.
  - Teste que falhou antes: `F8IdentidadeBuildTest` (BuildConfig sem BUILD_HASH/BUILD_DATE).
  - Teste que passou depois: hash injetado no `build.gradle.kts` (git rev-parse, exigido); Hub (`HomeScreen.kt`), cabeçalho do Diagnóstico e "Copiar relatório" (`DiagRelatorioTexto.montar`) mostram versão · vc · hash · data.
  - Não validado: aparelho (item 8 do TESTES_PENDENTES).

- **X1 — ui.* nunca filhos de Nicks 3D** — commit `7059ae7`
  - Causa raiz: `EditorViewModel.addNickOfKind` (`EditorViewModel.kt:642-646`) aceitava QUALQUER pai, inclusive para `ui.*` — o joystick ia para a cena 3D.
  - Teste que falhou antes: `x1_uiJoystickComLuzSelecionada_nuncaAlteraHierarquia3D` (o joystick ALTERAVA a Hierarquia).
  - Teste que passou depois: rota própria `adicionarElementoUi` (elemento de UI no .kui); helper `criarUmNickDeCadaKind` do Portão 2 ajustado para PROVAR o X1 (cena intacta + elemento no .kui).
  - Não validado: aparelho (item 6 do TESTES_PENDENTES).

- **X2 — Inspetor do joystick (TELAR) com efeito ao vivo** — commit `7059ae7`
  - Causa raiz: o Inspetor do TELAR (`TelarScreen.kt:393-498`) não expunha os campos joystick (o `ComandoPropriedade` já os aceitava desde a U1.4 — `TelarModelo.kt:243-249`), e o Play só lia o Nick antigo (`JoystickUiConfig.de(scene)`).
  - Teste que falhou antes: `x2_inspetorJoystick_editaOs7CamposComEfeitoAoVivo` (JoystickUiConfig não lia o .kui).
  - Teste que passou depois: 7 campos editáveis no Inspetor do TELAR + `JoystickUiConfig.de(scene, docUi)` (Nick antigo com prioridade; .kui depois).
  - Não validado: aparelho (item 7 do TESTES_PENDENTES).

- **F3 — painel "Projeto" (explorador acoplável + aba Arquivos)** — commit `77087b1`
  - Causa raiz: só existia o núcleo paginado puro (`ExploradorArquivos`, 37 linhas) — nenhuma UI, nenhuma operação de escrita, nenhum undo de arquivo.
  - Teste que falhou antes: `OperacoesArquivosTest` (as operações não existiam — compilação no pai) e `ExploradorCacheTest`.
  - Teste que passou depois: criar/renomear/duplicar/mover/apagar→LIXEIRA/un­do com Commands reais no histórico do editor; painel acoplado + 4ª aba "Arquivos"; breadcrumb/busca/ordenação/paginação/miniaturas; abrir por tipo (.lua/.kui/.ck/.glb/…/imagem).
  - Não validado: aparelho (itens 2-3 do TESTES_PENDENTES).

- **F4 — importar de qualquer pasta** — commit `77087b1`
  - Causa raiz: SAF de 1 arquivo só para modelos; `GuardasCaminho.pastaDe` definido SEM chamador; navegador do aparelho inexistente.
  - Teste que falhou antes: `ImportadorArquivosTest` (roteamento por extensão, árvore recursiva com contagem/cancelamento, resolvedor local com zip-slip bloqueado).
  - Teste que passou depois: SAF múltiplo ("Importar vários arquivos…"), navegador do aparelho com seleção múltipla e pasta inteira, glTF local com .bin/texturas (ResolvedorArquivos).
  - Não validado: aparelho (item 4 do TESTES_PENDENTES).

- **F5 — referências estáveis (assets.index)** — commit `77087b1`
  - Causa raiz: `ReferenceSystem.registerAsset/resolveAsset` sem chamador; mover/renomear quebrava Malha.path silenciosamente.
  - Teste que falhou antes: `ReferenciasF5Test` (índice não existia; mover quebrava a referência).
  - Teste que passou depois: id ESTÁVEL no `assets.index` (mover/renomear atualizam índice + repontam Nicks), painel "Problemas" com Corrigir…/Remover referência, parser tolerante (linha+motivo), reconstrução preservando ids.
  - Não validado: aparelho (item 5 do TESTES_PENDENTES).

- **F7 — Diagnóstico 36** — commit `77087b1`
  - Causa raiz: faltavam as checagens "arquivos", "importar-pasta" e "referencias".
  - Teste que falhou antes: `DiagAgendaTest` esperava 36 com o catálogo em 33.
  - Teste que passou depois: catálogo/agenda/when/títulos/strings en-pt completos; baseline `diag_checks` 33→36.
  - Não validado: aparelho (item 1 do TESTES_PENDENTES).

- **Portão 2 do CI — 3 causas raiz (execuções 2/3/4)** — commits `70f571b`, `3584d45`, `813daf4`
  - Causa raiz 1 (execução 2): `ci.yml:259` escrevia em `$GITHUB_OUTPUT` dentro do script do android-emulator-runner (a variável não existe no shell da action — exit 2 antes do gradle).
  - Causa raiz 2 (execução 3): o script da action roda em dash, que rejeita `set -o pipefail` — o gradle passou a rodar sob bash explícito.
  - Causa raiz 3 (execução 4): link x86_64 da libkenkern.so quebrado — o subconjunto etcpak OMITIU `Dither.cpp` (ProcessRGB.cpp chama `Dither()` no caminho `__SSE4_1__`, só compilado para x86_64); vendorizado intacto do commit upstream 3d716e1, com teste de contrato de LINK SSE4.1 real (`EtcpakVendorTest`).
  - Teste que falhou antes: os jobs de emulador das execuções 2/3/4 do GitHub Actions (prova nos logs).
  - Teste que passou depois: `connectedDebugAndroidTest` roda no emulador (KVM disponível no ubuntu-22.04) e o contrato etcpak linka com SSE4.1.
  - Não validado: a execução 5 do CI (em andamento quando este relatório foi escrito).

## Itens NÃO feitos

- Pré-visualização de imagem em TELA (o toque em imagem mostra a miniatura; o zoom/leitor fica para a rodada de TELAR/visual).
- Conflito de renomear via arrastar no explorador (a tarefa U2/Hierarquia de arrasto é da rodada de correção crítica) — o mover atual usa "Mover para…".
- `Testes do PORTÃO 2`: a física esperada-falhar (rígido caindo) continua com ID `FIS-1` e prazo v0.1.33 (a rodada de física).

## Números reais

- Testes JVM: 528 (antes 495) · classes de teste: 90 (antes 82) · androidTest: 3 · checagens do Diagnóstico: 36 (antes 33) · `@Ignore`: 0.
- Lint: 2 erros pré-existentes (NewApi, RememberReturnType) — baseline 2, nenhum novo.
- Contraste medido (WCAG) das 5 cores de token contra #1E1E1E: palavra 8,73 · string 5,86 · comentário 6,15 · número 5,65 · API 12,53 (antes: API 1,21 · comentário 3,27).
- Explorador: 5.000 arquivos, 25 páginas × 200 com CACHE de varredura única (a checagem "explorador" mede ≤ 2.000 ms).
- APK: assinado no CI (keystore dos secrets); tamanho/tempo medidos no CI-RESUMO.
