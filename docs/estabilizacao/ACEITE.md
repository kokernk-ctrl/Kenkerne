# Fundação Android 1.0 — contrato proposto de aceite

**Não é uma release.** A revisão de partida (1ac8474) é de desenvolvimento. O roadmap termina na Fase 13, mas não vincula o número 1.0 a uma fase. Esta proposta distingue uma primeira versão utilizável do editor/runtime de terminar a visão inteira. Não fecha fases, não elimina funcionalidades prometidas nem autoriza tag. Caso 1.0 designe todas as 13 fases, somam-se como bloqueios os itens ainda abertos do inventário do roadmap. PC, rede, loja e multiplayer continuam fora de escopo.

## Produto que seria entregue

No Android arm64 com GLES3, alguém deve conseguir criar/importar um projeto, montar uma pequena cena jogável com os Nicks suportados, testar, desfazer alterações, sair/reabrir sem perda e trocar o projeto com outro aparelho sem reconstruir a cena. Um APK do **editor** não é exportação de um **jogo independente**. Não anunciar essa capacidade enquanto não houver um jogo exportado que arranque sem o editor. Diferenciar os formatos suportados e as limitações reais no catálogo.

## Critérios de passagem (evidência exigida, não intenção)

| Prioridade | Critério objetivo | Evidência esperada |
|---|---|---|
| P0 | Build em checkout limpo: debug/release, testes JVM das duas variantes, verificação de traduções, sem erros lint, símbolos nativos e assinatura | CI e relatório por revisão exata; modelos opcionais ausentes não contam como testes aprovados |
| P0 | Editar um projeto CK1 e CK2, excluir/recuperar hierarquia e links, salvar, simular autosave interrompido, reabrir | Comprovar mesmos IDs/valores/ordem; cópia boa não pode ser substituída por cena vazia; falha de I/O explícita |
| P0 | Instalar versão assinada e atualizar a instalação anterior sem apagar os dados, executar Hub→Editor→Play→Pause→Stop→Hub; bloquear/desbloquear tela e reabrir | Dois Android arm64: um próximo ao mínimo API 26, outro API 35 ou superior; modelo/API/GPU, asset, versão, logs e resultado |
| P0 | Recriar o contexto GL e trocar projeto com caminhos iguais, sem textura/malha antiga, crash ou crescimento contínuo de handles | Teste de regressão + 20 ciclos no aparelho com contagem de recursos e tempo de frame |
| P1 | Importar glTF/GLB/FBX de referência, testar cancelamento, desfazer/refazer e dados persistidos; listar dependências quebradas | Testes integrados e amostras reais; material/textura/escala no aparelho; falhas não deixam o projeto irrecuperável |
| P1 | Jogador surge, responde ao toque, colide, câmera segue e Stop restaura exatamente a cena; pausar/congelar e retomar sem toque preso | Cena repetível + teste JVM + execução gráfica no alvo real |
| P1 | Trocar projeto por KERNPACK sem alterar original e sem perder arquivos; fechar/abrir a aplicação | Round-trip em duas instalações independentes, incluindo SAF e cancelamento |
| P1 | Fluxo do editor não apresenta controles que prometem comportamento inexistente; delimitar os Nicks funcionais, luzes/materiais e limitações de M1 | Conferir catálogo com a execução, roteiros e imagens de referência |
| P2 | Documentação de build/uso/diagnóstico, changelog e notas com limitações; medir travamentos, armazenamento e memória em projetos reais | Relatórios de aparelho e checklist com responsável, revisão e resultado |

P0 e P1 não se tornam verdes por existirem arquivos/classes. Dependências de driver, SAF, thermal, instalação com a chave de distribuição e atualização sobre a release anterior não se concluem num teste JVM. Um release só se marca após evidência dos critérios, sem regressão que cause perda de dados e sem substituir a chave de distribuição por debug.
