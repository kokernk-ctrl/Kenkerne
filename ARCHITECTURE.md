# KENKERN — ARCHITECTURE

## Mapa (pacote `com.kenkern.engine`)

```
MainActivity (sensorLandscape)
 └─ KenkernAppRoot (Compose)
     ├─ HomeScreen ............ Hub: lista/cria/renomeia/exclui projetos
     ├─ EditorScreen .......... TopBar + viewport + painéis + FlightControls
     │   └─ KenkernSurfaceView (AndroidView/GLSurfaceView)
     │       ├─ TouchController ... gestos → câmera/gizmo/tap (zero alocação)
     │       ├─ EngineRenderer .... frame: céu, malhas, grade, gizmo
     │       ├─ GlesBackend ....... RenderBackend GLES3 (shaders, buffers)
     │       ├─ OrbitCamera ...... Orbital + Voo (base/matrizes/raio de tela)
     │       └─ TransformGizmo ... alças, hit-test 28dp, projeção por MVP
     └─ CrashReportDialog ..... crash_logs/last_crash.txt + BootTrace
```

## Camadas

| Pacote | Papel |
|--------|-------|
| `core` | `Nick`, `Scene`, `Transform` (quaternion), `NickClass`/`NickValue`/`NickClassRegistry`, `NickFunction` (Spin/Bob), `ReferenceId` |
| `core.linkerf` | `LinkerfModel`: portas (EVENT_OUT/DATA_IN…) e links, serializado no .ck |
| `core.serialization` | `CkFormat`: .ck texto CK2, migração de CK1; o editor exige uma cópia completa antes de ativar a cena |
| `core.math` | `Vec3`, `Mat4`, `Quaternion` |
| `assets.import`, `assets.image` | glTF/GLB e FBX → KERN + KIMG, resolução/perfil, ETC2; `nativecore` encapsula JNI com fallback Kotlin |
| `assets.mesh` | `.KERN` binário: `KernFormat` (quantização u16 + octaédrica 8:8), `KernReader`/`KernWriter`, `MeshData` (LODs, bones, clusters) |
| `render` | GL: backend, renderer, câmera, gizmo, toque |
| `engine` | `FixedStepLoop` (timestep fixo, PlayState), `PlayScene` e `NickPlayRuntime` (spawn, áreas, movimento e caixas) |
| `projects` | `ProjectManager`: pastas, `project.kenkern` (format=1), recuperação; `Kernpack`: transporte portátil com SAF |
| `editor` | `EditorViewModel` (estado, undo/redo por `commands.Command`), `PanelStateStore` |
| `autosave` | `AutoSaveManager` (debounce 1,5 s) |
| `diag` | `BootTrace` (6 etapas: application → primeiro frame) |
| `ui` | screens/panels/theme (Material 3, paisagem) |

## Threads

- **UI/Compose**: telas, painéis, ViewModel; toques chegam aqui.
- **GL (própria do GLSurfaceView)**: `onDrawFrame` → `FixedStepLoop.tick` →
  `scene.updateFunctions` + `updateWorldTransforms` → renderFrame → publicação
  do debug (só com overlay ON).
- Comunicação UI→GL: flags `@Volatile` (pickPending/focusPending). GL→UI:
  `mainHandler.post` + campos `@Volatile` do `ViewportDebugState`.
- **Cena do editor**: publicada via `sceneRef` volátil. A thread principal é a
  única que altera a árvore, classes e TRS local; por isso o Compose pode ler
  esses dados na própria thread. Comandos (incluindo undo/redo), arrasto do
  gizmo e frames de edição usam o mesmo `Scene.withEditorAccess`: o GL só
  escreve matrizes derivadas e mantém o monitor até terminar render, picking,
  foco e diagnóstico do frame. Leituras da UI dessas matrizes também usam
  o monitor. Não se deve suspender ou esperar outra thread nesse bloco.
- **Play**: o GL captura a cena de edição sob o mesmo monitor uma vez por
  sessão; parse, simulação e render de Play trabalham em uma cena privada.
  Não há cópias da cena a cada frame ou movimento do gizmo. O trade-off é que
  um frame de edição demorado pode atrasar um comando ou toque concorrente.

## Fluxos críticos

- **Seleção**: tap no UP (< 250 ms, < 12dp) → `pickPending` → GL roda
  `pickNickAt` (raio × AABB das malhas, tolerância 24dp) → `onNickPicked`.
- **Gizmo**: DOWN sobre alça (hit-test 28dp) captura o gesto até o UP; drags
  viram UM Command de undo no fim (`onGizmoDragEnd` com snapshot inicial).
- **Persistência**: autosave com debounce; o editor testa primeiro a cópia mais nova e recorre à outra se estiver incompleta. Sem nenhuma cópia utilizável, não ativa a edição. A gravação sincroniza o temporário e o publica na mesma pasta sem apagar antecipadamente o original.

## Invariantes

- Objetivo de evitar alocação por frame (arrays pré-alocados, pools; primitivos @Volatile).
- Contenção de exceções Kotlin no caminho de GL, não garantia contra falhas nativas do driver (try/catch nos 3 callbacks + frame de clear).
- Formatos versionados com migração por defaults (CK1/CK2, KERN v1/v2, project format=1).
