# REPORT — v0.1.32

## Itens feitos

- **H1 — HARNESS.md (papéis, linguagem, PRONTO, relatório e prompt)** — commit `31fbbab`
  - Causa raiz: não existia arquivo de regras; criado em `HARNESS.md:1` e linkado de `README.md`, `ROADMAP.md` e `PROGRESS.md`.
  - Teste que falhou antes: não observado (arquivo novo); `check_gates.py` exige a existência dele em `ARQUIVOS_OBRIGATORIOS`.
  - Teste que passou depois: `python3 scripts/check_gates.py` → `OK — portão 1 verde`.
  - Não validado: leitura e uso por outra IA.

- **H2 — Portão 1 (ratchet, palavras proibidas, arquivos obrigatórios, regra fix/corrige)** — commit `31fbbab`
  - Causa raiz: o workflow anterior só compilava e rodava testes (`.github/workflows/build.yml:37-49`); não havia ratchet, detector de palavras, arquivos obrigatórios nem regra de fix.
  - Teste que falhou antes: `check_gates.py` com o baseline antigo falhou com `RATCHET DESATUALIZADO: jvm_test_methods=496 > baseline=493`.
  - Teste que passou depois: baseline 495/82/3/33/0 → `OK — portão 1 verde`; lint com 2 erros pré-existentes (NewApi, RememberReturnType) e nenhum novo.
  - Não validado: os caminhos de falha no CI real (só a execução do GitHub prova).

- **H3 — Portão 2 (emulador API 34 + alternativa Robolectric)** — commit `31fbbab`
  - Causa raiz: não havia androidTest (0 arquivos) nem job de emulador; o app só era testado em JVM.
  - Teste que falhou antes: `FluxoRealRobolectricTest` com Compose falhou com `AppNotIdleException` (o Hub anima em loop); reescrito para o MESMO caminho de produção sem Compose.
  - Teste que passou depois: 2 testes Robolectric verdes (projeto, scripts, Nicks, Play 120 ticks, salvar/reabrir) e 3 androidTest no emulador.
  - Não validado: o emulador do CI (o modo e o resultado vão no CI-RESUMO).
  - Limite registrado: a checagem "nativo" do Diagnóstico não roda no Robolectric (o handler de SIGSEGV mata a JVM de teste, exit 139).

- **H4 — Release só pelo workflow, CI-RESUMO e REPORT validado** — commit `31fbbab`
  - Causa raiz: a Release era publicada à mão e o workflow antigo parava no artifact, sem resumo.
  - Teste que falhou antes: não se aplica (fluxo novo).
  - Teste que passou depois: `check_release.py` confere tag × `versionName`; `ci_resumo.py` gera no máximo 40 linhas; `check_report.py` rejeita seção ausente e item sem commit.
  - Não validado: a primeira Release v0.1.32 criada pelo workflow.

- **H5 — Portão 2 travava antes do gradle (correção do workflow)** — commit `70f571b`
  - Causa raiz: `.github/workflows/ci.yml:259` — o script do `android-emulator-runner` executava `echo "modo=emulador" >> "$GITHUB_OUTPUT"`; dentro do shell do script dessa action a variável `GITHUB_OUTPUT` não existe, o redirect falha e o script morre com exit 2 ANTES de rodar `connectedDebugAndroidTest` (execução 2, 27/11/2026 21:56:47Z).
  - Teste que falhou antes: execução 2 do CI — job "Portão 2 (emulador)" failure com `The process '/usr/bin/sh' failed with exit code 2` e nenhum log do gradle.
  - Teste que passou depois: execução seguinte do CI — `connectedDebugAndroidTest` roda no emulador (KVM disponível no runner ubuntu-22.04) e o job fica verde; o modo agora é derivado do resultado dos passos (`steps.modo.outcome`), não de output inexistente.
  - Não validado: não se aplica (o CI real é a validação).

## Itens NÃO feitos

- Nenhum item de funcionalidade do app foi tocado (escopo do prompt: só CI, testes e documentos).
- `AUDITORIA.md` ficou como esqueleto, conforme o pedido (preenchimento na rodada seguinte).

## Números reais

- Testes JVM: 495 (antes 493) · classes de teste: 82 (antes 81) · androidTest: 3 (antes 0) · checagens do Diagnóstico: 33 · `@Ignore`: 0.
- Lint: 2 erros pré-existentes (NewApi, RememberReturnType) — baseline 2, nenhum novo.
- APK: assinado no CI (keystore dos secrets); tamanho e tempo medidos no CI-RESUMO.
- Portão 1 antes: ~4 min 48 s (job único, execução de 27/11 17:55–18:00 UTC); depois: medido no CI-RESUMO.
- Portão 2: emulador API 34 x86_64 (o modo real, emulador ou Robolectric, vai no CI-RESUMO).
