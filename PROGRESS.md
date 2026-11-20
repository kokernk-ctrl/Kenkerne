# KENKERN — PROGRESS

**Retome daqui.** Se uma conversa nova começar com o Prompt Mestre v3, leia este
arquivo, BACKLOG.md, ARCHITECTURE.md, DECISIONS.md e REGRESSION.md, e siga o loop
de tarefas do BACKLOG sem refazer o que está pronto.

## Estado agora

- **Fase**: 3 (LUA) — EM CURSO. Fase 2 FEITA (tag `fase-2-ok`, Release publicada).
- **Tarefa atual**: F3.2 — ponte de API (nick/scene/engine) sobre
  `LuaJScriptRuntime.instalarTabela()` (a infra já existe e está testada).
- **App**: v0.1.13 (`versionCode 14`), suíte JVM verde (37 testes), CI verde.

## O que existe (Fases 1–2, validado por CI)

- Tudo da Fase 1 (ver CHANGELOG) MAIS:
- Tela Diagnóstico no Hub (ícone do inseto): 7 checagens com APROVADO/FALHOU
  (.ck, .KERN, picking, shaders, buffers, glcaps, memória) — EglDiag cria
  contexto pbuffer offscreen na thread de fundo; toda fase adiciona checagens.
- Duplicar projeto; Importar/Exportar .kernpack (SAF); pastas ui/ e worlds/
  (com migração de projetos antigos); ENGINE_VERSION = BuildConfig.VERSION_NAME.
- Fixes da Fase 1 achados pelos testes F2: escapes do .ck; LOD_ENTRY_SIZE
  20→32 + header desalinhado no KernReader (o torus .KERN nunca tinha
  renderizado — agora renderiza).

## O que falta na Fase 3 (LUA)

1. ~~F3.1~~ FEITO: LuaJ 3.0.1 (luaj-jse) + `ScriptRuntime` + `LuaJScriptRuntime`
   (compila sem rodar; iniciar roda top-level+on_start; atualizar chama
   on_update; erro SEMPRE desativa com motivo; orçamento de tempo por tick;
   sandbox sem os/io/require). 8 testes em LuaRuntimeTest. GOTCHA: LuaJ
   3.0.1 precisa de `package.loaded` como mesa ao registrar MathLib —
   plantar mesa vazia, NÃO instalar PackageLib.
2. F3.2: ponte de API (nick.position/rotation/scale/name/active,
   scene.find, engine.time/dt, nick.class) com orçamento por tick e erro
   que DESATIVA o script (nunca derruba o app) + testes JVM.
3. F3.3: componente de script no Nick (campo no .ck — CK1 cresce com
   SCRIPT path=...) + execução no updateFunctions (Play).
4. F3.4: editor simples de script no app (painel) + scripts de exemplo.
5. F3.5: checagem Lua no Diagnóstico + LUA_API.md oficial congelado.
6. FIM DE FASE: tag `fase-3-ok` + Release + TESTES_PENDENTES.

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

## Como compilar/testar (ambiente do agente)

```bash
cd /home/z/my-project/Kenkerne
./gradlew -Dorg.gradle.java.home=/home/z/jdk/jdk-21.0.13.1+2 :app:testDebugUnitTest
```
SDK em `/home/z/android-sdk` (`local.properties` aponta). Se o ambiente foi
reciclado: reinstalar cmdline-tools (layout `cmdline-tools/latest`!) +
platforms;android-35 + build-tools;35.0.0 + JDK Temurin 21 em /home/z/jdk.
LuaJ (Fase 3): adicionar em gradle/libs.versions.toml (mirror se rate-limit).
