# KENKERN — TESTES_PENDENTES (só no aparelho; o agente não roda o app)

## v0.1.31 — FECHAR ARQUIVOS: 8 itens

1. **Diagnóstico 36/36** no aparelho (as 3 checagens novas F7: arquivos,
   importar-pasta, referencias) com os números reais (ms, contagens).
2. **Painel "Projeto" acoplado + aba "Arquivos"**: criar pasta, criar
   script, renomear, duplicar, mover e apagar — conferir a LIXEIRA em
   `.kenkern/lixeira` e o desfazer da TopBar voltando TUDO.
3. **Abrir por tipo**: .lua abre no editor de código; .kui abre no TELAR
   com O ARQUIVO clicado; .ck carrega a cena; .glb importa (com .bin/-
   texturas externas junto, se existirem); imagem mostra miniatura.
4. **Importar vários arquivos (SAF múltiplo)** do menu ⋮ e pelo
   **Navegador do aparelho** (com a permissão do F1): conferir o destino
   por extensão (assets/models, textures, audio, fonts, video, scripts).
5. **F5 no aparelho**: mover/renomear um .KERN REFERENCIADO por um Nick —
   o Nick não pode perder a malha; apagar e conferir o painel Problemas
   com Corrigir…/Remover referência.
6. **X1**: criar ui.joystick com uma LUZ selecionada — o joystick NÃO pode
   aparecer na Hierarquia 3D e deve aparecer no TELAR (aba UI).
7. **X2 no Play**: mudar tamanho/opacidade/zona morta do joystick no
   Inspetor do TELAR e conferir o efeito AO VIVO na janela de jogo.
8. **X3 no editor de código**: "set_position"/"math.cos" legíveis em TODAS
   as linhas e o realce NÃO muda ao mover o cursor; identidade da build
   (F8) no Hub e no cabeçalho do relatório copiado.

## v0.1.32 — HARNESS + PORTÕES DE CI: 6 itens

> Sem funcionalidade nova no app. A Release v0.1.32 só existe se o Portão 1,
> o Portão 2 (emulador ou Robolectric) e o `REPORT.md` passarem no CI.

1. **Instalar o APK assinado** da Release v0.1.32 e abrir o app: sem crash e
   com a versão 0.1.32 no Hub.
2. **Criar um projeto pelo Hub** (nome digitado) e conferir no gerenciador de
   arquivos a árvore completa: `assets/{models,textures,materials,audio,fonts,
   video}`, `scenes`, `nicks`, `scripts`, `animations`, `shaders`, `ui`,
   `worlds`, `dialogues`, `build`, `.kenkern` e `project.kenkern`.
3. **Diagnóstico 33/33** no aparelho, com os números reais (ms, permissão e
   espaço livre) — as checagens novas de armazenamento/pastas/explorador.
4. **Play do template Coletar moedas por 120 ticks**: sem crash, jogador no
   chão, contador de moedas ≥ 1 e Stop restaurando a cena (a contraprova do
   Portão 2 é a checagem "jogo").
5. **Abrir o `CI-RESUMO.md` anexado à Release**: Portão 1 e Portão 2 VERDES,
   números do ratchet (495/82/3/33/0) e ausência de GATES ALTERADOS.
6. **Conferir que a Release foi criada pelo workflow** (não manual): tag
   `v0.1.32`, APK assinado e o resumo anexado.
