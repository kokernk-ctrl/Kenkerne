# Auditoria independente — cena, engine e projetos (KENKERN 1.0)

**Data:** 23/11/2026
**Branch/HEAD:** `dev/1.0-estabilizacao` / `1ac8474` (`feat: adiciona Nicks jogáveis ao primeiro recorte do M1`)
**Escopo lido:** `core` (Scene/Nick/Transform/CK2/references/Linkerf/script), `engine`/runtime de jogo, `commands`, estado e cálculos do `editor`, autosave e `projects` (incl. Kernpack/SAF), testes de gameplay e docs de roadmap/backlog.
**Integridade:** working tree estava limpa no início e permanece sem alterações. Auditoria read-only: não alterei arquivos do repo nem rodei build/testes; reproduções abaixo são especificações JVM não destrutivas a executar após autorização.

## Achados confirmados por inspeção

### A1 — Apagar um Nick destrói sua subárvore e o undo não a recupera — **P1 / perda de dados**

- `core/Scene.kt:57–73`: `removeNick` chama `unregisterSubtree`; este zera `parent` dos filhos e executa `nick.children.clear()` recursivamente.
- `commands/SceneCommands.kt:54–76`: `DeleteNickCommand` conserva somente o objeto raiz e, no undo, chama `attachSubtree`. A árvore já foi destruída; o undo restaura apenas o Nick apagado, sem filhos.
- **Reprodução JVM:** montar `raiz > pai > filho > neto`; executar `DeleteNickCommand(scene, pai.id).execute()` e depois `.undo()`; esperar que `pai.children[0]` seja `filho`, que `neto` continue descendente e que IDs resolvam. Falha atual: descendentes foram removidos/limpos.
- **Correção testável:** separar detach de destruição definitiva. Ao retirar a raiz, preservar relações internas da subárvore; remover referências sem limpar `children`; reanexar e registrar toda a subárvore. Adicionar teste delete/undo/redo de 3 níveis, ordem entre irmãos e `ReferenceSystem.resolve`.

### A2 — Erro no undo de importação remove o Nick e perde o próprio comando — **P1 / estado inconsistente**

- `commands/Command.kt:29–40`: `undo()` remove o comando da pilha antes de executar `cmd.undo()`; se este lança, o comando não chega à pilha redo nem volta à undo. `redo()` tem o mesmo padrão.
- `commands/ImportModelCommand.kt:81–104`: o undo remove primeiro o Nick da cena, depois cria/move arquivos sequencialmente; falha em mkdir/movimento pode acontecer após mutações parciais. O teste existente `ImportModelCommandTest` verifica mensagem de erro chamando `cmd.undo()` diretamente, não testa o estado do histórico nem rollback da cena.
- **Reprodução JVM:** reutilizar setup de `ImportModelCommandTest` que bloqueia `assets/.lixeira-import` como arquivo; fazer `history.push(cmd)` e `history.undo()`. O undo lança, o Nick já saiu da cena e `history.canUndo` passa a falso (comando perdido). Isso é falha operacional de I/O, não uma reprodução de exploração.
- **Correção testável:** histórico só transfere/remove comando após sucesso; e undo/redo da importação precisam ser transacionais (rollback das movimentações parciais e manter Nick/arquivos coerentes). Testar falha antes do primeiro arquivo e após o primeiro de vários, afirmando pilhas, cena e bytes finais.

### A3 — `removeNick` apaga dados LINKERF que o undo não guarda — **P2 / perda de grafo**

- `core/Scene.kt:66–72` remove portas e conexões por proprietário em cada nó da subárvore (`linkerf.removeOwner`). `core/linkerf/LinkerfModel.kt:109–119` elimina as portas e todo link incidente.
- `DeleteNickCommand` guarda subárvore/posição, não portas nem links; `undo()` não repõe o grafo. Portanto apagar e desfazer não é round-trip para cenas CK2 que já contenham Linkerf, apesar da serialização de `CkFormat` preservar esse grafo.
- **Reprodução JVM:** criar dois Nicks, portas compatíveis e link; apagar um proprietário e desfazer; comparar `allPorts()`/`allLinks()` antes e depois. Estado atual: porta/link ausentes.
- **Correção testável:** snapshot/restauração de portas dos owners removidos e de todos os links incidentes, ou detach reversível do grafo; teste com link interno e link entre subárvore apagada e Nick externo.

### A4 — A gravação chamada “atômica” apaga o arquivo anterior antes de substituí-lo — **P1 / risco de corrupção**

- `projects/ProjectManager.kt:272–290`: grava `.tmp`, executa `target.delete()` e só depois `tmp.renameTo(target)`; se rename falha, grava diretamente em `target`. Uma interrupção entre delete e rename deixa sem versão anterior; uma interrupção durante fallback pode deixar arquivo truncado. Isso contradiz o comentário de atomicidade e afeta cena principal/autosave (`:294–298`).
- **Reprodução/teste futuro:** introduzir seam de falha no replace/rename e testar com arquivo-alvo pré-existente; exigir que, se substituição não concluir, o conteúdo anterior permaneça integral e que nunca se observe destino parcialmente escrito. Não executei fault injection.
- **Correção testável:** escrever e fechar temporário no mesmo diretório; substituir sem apagar antecipadamente usando operação de replace atômico suportada pelo filesystem; tratar fallback sem destruir o original (ex. backup/restore) e propagar `false` em falha. Testar sucesso, falha de replace e recuperação de temporário.

### A5 — Criação de projeto ignora o resultado da gravação da cena padrão — **P2 / projeto abre vazio**

- `projects/ProjectManager.kt:123–126`: `writeFileAtomic(...scenes/cena.ck...)` retorna `Boolean`, mas o resultado é ignorado; a função retorna `ProjectHandle` mesmo se a gravação falhar. O projeto então abre com cena vazia via fallback de `loadScene`.
- **Teste futuro:** seam que falhe especificamente a escrita de `cena.ck`; criação deve retornar falha, expor erro e limpar/identificar diretório parcial, nunca anunciar projeto inicializado sem cena.

## Cobertura e leitura de qualidade

- O código tem testes valiosos para runtime M1 (spawn, colisão AABB, pai transformado e áreas), CK1→CK2/round-trip, import/undo de arquivos, layouts e Kernpack. Contudo, não encontrei testes de `Scene.removeNick`/`DeleteNickCommand`, `CommandHistory` em exceções, persistência/autosave, falhas de substituição atômica ou integridade undo de Linkerf.
- `NickPlayRuntimeTest` cobre o núcleo JVM, mas não a sessão integrada de Play/Stop/câmera/input no renderer; `TESTES_PENDENTES.md` mantém esse aceite no aparelho. Não afirmar validação de gameplay real com base apenas nos testes JVM.
- `AutoSaveManager` não tem teste dedicado no escopo. Recomenda-se acrescentar testes de debounce/troca de projeto e concorrência de gravação após corrigir a persistência; não tratei comportamento de thread como defeito confirmado nesta auditoria.

## Roadmap/gaps relevantes para estabilidade 1.0

Conforme `ROADMAP.md`, `BACKLOG.md`, `PROGRESS.md`, `NICKS.md` e `TESTES_PENDENTES.md` (estado documental, não revalidado por build):

1. **M1 é um recorte jogável, não física completa:** runtime existente cobre jogador, spawn, gravidade, colisão AABB e câmera básica/seguidora; permanecem fora/por implementar corpos rígidos/cinemáticos, formas de collider além de caixa, pulo/rampas, joystick Nick configurável e input/API integrada. O roteiro M1 está em `TESTES_PENDENTES.md`, ainda depende de aceite no aparelho.
2. **Fase 2 continua aberta:** F2.11 streaming sem bloquear GL, F2.12 navegador de assets, F2.13 preview, F2.14 prefabs e F2.15 registry com IDs/referências quebradas constam como TODO. T0.4 PBR integral e T0.5 Hub novo também estão pendentes no backlog; a resolução Original/512 parcial foi documentada como aceita.
3. **Lua é antecipado e congelado (F5.1):** manter a restrição documental; API, componente/script de cena e editor pertencem à Fase 5, não a correção de M1.
4. **Gates de release:** distinguir `COMPILADO` de `VALIDADO-NO-APARELHO`; preservar a regra de não fechar/taguear Fase 2 com itens pendentes. Os três achados de integridade acima devem entrar como regressões obrigatórias antes de declarar estável 1.0.

## Ordem prática recomendada após sinal do pai

1. **Primeiro:** corrigir detach/reattach de subárvore e adicionar regressão JVM (A1) — pequena superfície, bloqueia perda de conteúdo em apagar/desfazer.
2. **Em seguida:** tornar CommandHistory e undo/redo da importação transacionais (A2), com testes de falha de I/O.
3. **Depois:** corrigir replace realmente atômico + checagem de resultado da criação do projeto (A4–A5), cobrindo cena e autosave.
4. Incluir snapshot de Linkerf no undo de exclusão (A3), preferivelmente junto à API reversível de remoção.

**Não implementei os fixes:** aguardo o sinal do pai após confirmação do baseline/inspeção.
