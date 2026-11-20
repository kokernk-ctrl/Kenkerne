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
| `core.serialization` | `CkFormat`: .ck texto CK1 (tolerante a linhas corruptas) |
| `core.math` | `Vec3`, `Mat4`, `Quaternion` |
| `assets.mesh` | `.KERN` binário: `KernFormat` (quantização u16 + octaédrica 8:8), `KernReader`/`KernWriter`, `MeshData` (LODs, bones, clusters) |
| `render` | GL: backend, renderer, câmera, gizmo, toque |
| `engine` | `FixedStepLoop` (timestep fixo, PlayState) |
| `projects` | `ProjectManager`: pastas, `project.kenkern` (format=1), autosave, atômico |
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

## Fluxos críticos

- **Seleção**: tap no UP (< 250 ms, < 12dp) → `pickPending` → GL roda
  `pickNickAt` (raio × AABB das malhas, tolerância 24dp) → `onNickPicked`.
- **Gizmo**: DOWN sobre alça (hit-test 28dp) captura o gesto até o UP; drags
  viram UM Command de undo no fim (`onGizmoDragEnd` com snapshot inicial).
- **Persistência**: autosave com debounce; `loadScene` prefere autosave mais
  novo que a cena principal; gravação sempre atômica (.tmp → rename).

## Invariantes

- Zero alocação por frame (arrays pré-alocados, pools; primitivos @Volatile).
- Falha de GL nunca derruba o app (try/catch nos 3 callbacks + frame de clear).
- Formatos versionados com migração por defaults (CK1, KERN v1, project format=1).
