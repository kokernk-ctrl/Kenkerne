# KENKERN — TESTES_PENDENTES (só no aparelho; o agente não roda o app)

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
