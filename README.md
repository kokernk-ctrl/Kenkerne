# Kenkern

Engine de jogos 3D para Android (Kotlin + Compose + OpenGL ES 3.x), feita para
rodar em celular na medida certa: editor, Nicks, Lua e Diagnóstico no próprio
aparelho.

- **Comece por [HARNESS.md](HARNESS.md)** — regras de trabalho, papéis,
  definição de PRONTO e erros conhecidos da IA. Toda IA novata lê antes de agir.
- [ROADMAP.md](ROADMAP.md) — fases e status.
- [PROGRESS.md](PROGRESS.md) — retome daqui.
- [NICKS.md](NICKS.md) — catálogo de Nicks.
- [LUA_API.md](LUA_API.md) — API Lua da engine.
- [TESTES_PENDENTES.md](TESTES_PENDENTES.md) — o que só o aparelho valida.
- [AUDITORIA.md](AUDITORIA.md) — uma linha por funcionalidade declarada.

## CI e portões

O repositório tem dois portões (HARNESS.md §3):

1. **Portão 1** (rápido, bloqueia sempre): compilação, testes JVM, lint sem
   erros novos, i18n, ratchet `gates/baseline.json`, regra
   "reproduzir antes de corrigir", palavras proibidas e arquivos obrigatórios.
2. **Portão 2** (emulador API 34 ou Robolectric): abre o app de verdade na
   `MainActivity` e percorre a interface — criar projeto, scripts, Nicks, Play,
   salvar/reabrir e Diagnóstico.

A Release de pré-lançamento nasce só do workflow, em tag `vX.Y.Z`, com os dois
portões e o `REPORT.md` verdes. Release manual é proibida.

## Build local

```bash
./gradlew testReleaseUnitTest      # Portão 1 (testes)
./gradlew assembleRelease          # APK release (assinatura via KEYSTORE_*)
./gradlew connectedDebugAndroidTest  # Portão 2 (emulador/KVM)
```
