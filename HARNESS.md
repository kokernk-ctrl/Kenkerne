# HARNESS — regras de trabalho para IA e humanos

Este arquivo é a fonte única das regras de colaboração do Kenkern. Qualquer IA
(implementador ou arquiteto) lê este arquivo INTEIRO antes de agir. O que está
aqui vale mais do que qualquer hábito de outra sessão.

## 1. Papéis

| Papel | Quem | Faz | Não faz |
| --- | --- | --- | --- |
| Usuário | Dono do projeto e do aparelho | Decide prioridades, testa no aparelho, valida | — |
| Implementador | GLM ou outra IA de código | Escreve código e testes, roda o CI, publica Release, preenche `REPORT.md` | Não aprova o próprio trabalho como VALIDADO |
| Arquiteto | Claude, Qwen ou outra IA de projeto | Escreve prompts, revisa código e relatórios, decide arquitetura | Não escreve o projeto |

O arquiteto não escreve o projeto: escreve prompts e revisa. O implementador
não muda o escopo sozinho: executa o prompt e registra no `REPORT.md` o que
ficou de fora e por quê.

## 2. Linguagem

Proibido em qualquer documento, commit, relatório ou mensagem:

- "talvez"
- "acho"
- "provavelmente"
- "possivelmente"
- "parece que"
- "sei lá"
- "deve funcionar"

Afirme fato com prova (teste, número, arquivo:linha) ou escreva `NÃO VERIFICADO`
e diga COMO verificar. Exemplos:

- Errado: "talvez o colisor não esteja sendo criado".
- Certo: "`CreateNickCommand.execute` (SceneCommands.kt:88) não adiciona a
  classe Colisor; o teste `CorpoSemColisorTest` falha na linha 42."

O CI varre `CHANGELOG.md`, `PROGRESS.md`, `TESTES_PENDENTES.md`,
`DECISIONS.md` e `REPORT.md` e falha o build com arquivo e linha.

## 3. Definição de PRONTO

Um item está PRONTO quando TODOS os itens abaixo são verdadeiros:

1. CI verde nos dois portões (seção 3.1 e 3.2).
2. O teste que cobre o bug NASCEU VERMELHO: rodou contra o código antigo,
   falhou, e depois passou com a correção.
3. A checagem correspondente do Diagnóstico existe e roda (não fica PENDENTE).
4. Strings nos dois idiomas (`values/strings.xml` e `values-pt/strings.xml`) e
   `scripts/check_i18n.sh` verde.

VALIDADO é um estado MAIOR que PRONTO: só o aparelho do usuário valida. Nenhum
item vira VALIDADO por teste de JVM, emulador ou relatório do implementador.

### 3.1 Portão 1 (rápido, bloqueia sempre)

Compilação, testes de JVM, lint sem erros novos, i18n, trava de ratchet
(`gates/baseline.json`), regra "reproduzir antes de corrigir", detector de
palavras proibidas e detector de arquivos obrigatórios.

### 3.2 Portão 2 (emulador, bloqueia a Release)

Instrumentação que abre o app de verdade, na `MainActivity`, e percorre o
caminho da interface: criar projeto, criar scripts, criar Nicks, Play, salvar e
reabrir, Diagnóstico. Capturas de tela vão como artifact. Job tem teto de
25 minutos.

### 3.3 Release

Release manual é proibida. Só o workflow publica, em tag `vX.Y.Z`, depois dos
dois portões e do `REPORT.md` verdes, com a tag igual ao `versionName`. Falha
em qualquer portão = nenhum APK publicado.

## 4. Modelo de relatório e de prompt

### 4.1 Relatório do implementador (`REPORT.md`)

Seções obrigatórias, nesta ordem:

```markdown
# REPORT — vX.Y.Z

## Itens feitos
- **ID** — o que foi feito — commit `<hash curto>`
  - Causa raiz: `arquivo.kt:linha` + uma frase.
  - Teste que falhou antes: `NomeDoTeste` (como falhava).
  - Teste que passou depois: `NomeDoTeste`.
  - Não validado: o que só o aparelho pode confirmar.

## Itens NÃO feitos
- **ID** — motivo objetivo (bloqueado, fora de escopo, dependência).

## Números reais
- Testes JVM: N (antes M) · checagens do Diagnóstico: N · APK: X,Y MB ·
  tempo médio do Portão 1: N min N s · Portão 2: emulador/Robolectric.
```

O CI rejeita o relatório se faltar seção, se um item "feito" não tiver commit
ou se houver palavra proibida.

### 4.2 Prompt para o implementador

Todo prompt de tarefa tem esta forma, sem omitir blocos:

1. **Contexto** — versão atual, o que já existe, arquivo e linha do defeito.
2. **Escopo** — lista numerada de itens; o que está explicitamente fora.
3. **Regras** — reproduzir antes de corrigir, edições pontuais, CI verde por
   commit, sem `runCatching` silencioso, strings en/pt, cores em `Theme.kt`.
4. **Critérios de aceite** — teste, checagem e número que provam cada item.
5. **Entrega** — bump, CHANGELOG, `TESTES_PENDENTES.md` (≤8 itens), uma
   Release de pré-lançamento, relatório de até 20 linhas.
6. **Parada** — onde parar e o que NÃO fazer além dali.

## 5. Erros conhecidos da IA e como evitá-los

| Erro | Como evitar |
| --- | --- |
| Declarar concluído sem prova | Citar commit, teste e número; sem prova, é `NÃO VERIFICADO`. |
| Reescrever arquivos inteiros | Edições pontuais; diff mínimo; nunca regenerar um arquivo estável. |
| Testar com cena montada à mão em vez do fluxo real da UI | Todo teste de comportamento usa `EditorViewModel.addNickOfKind` e os mesmos Commandos da interface. |
| Relatar "corrigido" com teste que já passava | Rodar o teste novo contra o código do commit pai e registrar a falha. |
| Publicar Release com itens faltando | Conferir `REPORT.md`, `TESTES_PENDENTES.md` e o CI-RESUMO antes da tag. |
| Enfraquecer teste | Mudou asserção, mudou o bug que o teste cobre; registrar no relatório. |
| Esquecer que propriedades vêm antes do `init` em Kotlin | Declarar todas as propriedades antes do bloco `init`. |
| Engolir exceção com `runCatching` | Tratar o erro explicitamente; `runCatching` só com ramo de erro visível. |
| Repetir a mesma correção por 30 minutos | Orçamento de 5 CIs vermelhos (seção 6). |

## 6. Orçamento de esforço

Se um bug resiste a **5 CIs vermelhos**, ele vira **BLOQUEADO**: o
implementador registra a causa no `REPORT.md`, lista o que tentou (com os
números) e segue para a próxima tarefa. Nunca fica 30 minutos repetindo a
mesma correção. Um item BLOQUEADO não bloqueia a parada: entra em
`TESTES_PENDENTES.md` com o motivo.

## 7. Onde este arquivo é citado

- `ROADMAP.md` — topo.
- `PROGRESS.md` — topo.
- `README.md` — topo.
- `REPORT.md` — seção de regras.
- `DECISIONS.md` — decisões de CI e portões.
