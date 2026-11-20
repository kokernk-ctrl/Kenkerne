# KENKERN — PROGRESS

**Retome daqui.** Se uma conversa nova começar com o Prompt Mestre v3, leia este
arquivo, BACKLOG.md, ARCHITECTURE.md, DECISIONS.md e REGRESSION.md, e siga o loop
de tarefas do BACKLOG sem refazer o que está pronto.

## Estado agora

- **Fase**: 2 (Projeto & Diagnóstico) — EM CURSO.
- **Tarefa atual**: F2.1 (teste JVM de ida e volta do .ck).
- **App**: v0.1.12 (`versionCode 13`), commit `01ce442`, CI verde, APK assinado
  em `download/Kenkern-v0.1.12-flight-yaw.apk` (ambiente do agente) e na Release
  `fase-1-ok` do GitHub.
- **Fase 1**: FEITA — tag `fase-1-ok` + Release publicada.

## O que existe (Fase 1, validado)

- Editor: viewport GLES3 (`KenkernSurfaceView` + `EngineRenderer`), grade infinita,
  céu, cubo procedural, seleção por toque (picking AABB), gizmo mover/rotacionar/
  escalar, câmera Orbital + Voo (yaw/pitch por toque, pinça exponencial), painéis
  acopláveis (Hierarquia/Inspetor), undo/redo por Commands, Play/Stop com loop de
  timestep fixo, autosave com debounce, crash dialog + BootTrace de 6 etapas,
  overlay de debug de toque.
- Formatos: `.ck` texto CK1 (NICK/TRANSFORM/CLASS/FIELD/LINKERF, tolerante a
  linhas corruptas) e `.KERN` binário (quantização pos u16, normais octaédricas
  8:8, LODs, bones/clusters) — leitor/escritor prontos, SEM importador glTF.
- Núcleo: `Nick` (id/nome/ativo/visível/pai/filhos/transform quaternion/classes),
  `Scene` (hierarquia, `updateWorldTransforms`, `updateFunctions`), classes
  nativas Spin/Bob, LINKERF (modelo de portas/links, serializado).
- Hub: lista/cria/renomeia/exclui projetos no armazenamento interno
  (`filesDir/projects`), estrutura de pastas por projeto, `project.kenkern`
  (format=1), recuperação por autosave.
- Testes JVM (CI): `OrbitCameraRayTest`, `PinchZoomRegressionTest`,
  `FlightLookRegressionTest` (12 testes verdes). CI: `testReleaseUnitTest` +
  `assembleRelease` assinado.

## O que falta na Fase 2 (ver BACKLOG)

Diagnóstico no Hub, testes de serialização, pastas ui/worlds, versão única,
duplicar projeto, SAF.

## Armadilhas descobertas (não repetir)

1. `computeBasis` do OrbitCamera usava yaw do modo errado no voo (fix v0.1.12) —
   qualquer código novo de câmera deve ler o yaw **do modo certo**.
2. `ACTION_POINTER_UP` ainda contém o dedo que subiu (zoom preso v0.1.11).
3. Ordem de inicialização: propriedades Kotlin ANTES de init que as usa
   (NPE do TransformGizmo na v0.1.8, thread principal, sem try/catch GL).
4. material3 1.3.0: `AlertDialog` com containerColor não existe — usar overload
   de botões.
5. Touch em px da view; projeção/overlay em px de framebuffer (escala de
   resolução) — converter com `density`/proporção, nunca misturar.
6. Limiares de toque sempre em dp (12dp/28dp/24dp já usados).
7. `logcat` de release usa `Log.d` do TouchController — ok (android.jar nos
   testes JVM retorna padrão, não lança).
8. CI já roda `testReleaseUnitTest`: teste JVM vermelho bloqueia o APK.

## Como compilar/testar (ambiente do agente)

```bash
cd /home/z/my-project/Kenkerne
./gradlew -Dorg.gradle.java.home=/home/z/jdk/jdk-21.0.13.1+2 :app:testDebugUnitTest
```
SDK em `/home/z/android-sdk` (`local.properties` já aponta). Se o ambiente foi
reciclado, reinstalar cmdline-tools (layout `cmdline-tools/latest`) + platform
35 + build-tools 35 + JDK Temurin 21.
