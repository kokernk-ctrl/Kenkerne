# KENKERN — Fase 1 (Fundação)

Game engine 3D mobile-first em **Kotlin + Jetpack Compose (Material 3)**, com render
OpenGL ES 3.0 e arquitetura preparada para Vulkan.

## Como compilar

```bash
./gradlew assembleDebug        # APK de debug
./gradlew assembleRelease      # APK de release (assinado se variáveis presentes)
```

Requisitos: JDK 17, Android SDK (compileSdk 35, build-tools 35).

## Assinatura de release

O `app/build.gradle.kts` lê a assinatura de variáveis de ambiente:

| Variável           | Significado                                   |
|--------------------|-----------------------------------------------|
| `KEYSTORE_PATH`    | Caminho do arquivo .jks                       |
| `KEYSTORE_PASSWORD`| Senha do keystore                             |
| `KEY_ALIAS`        | Alias da chave                                |
| `KEY_PASSWORD`     | Senha da chave                                |

Sem as variáveis, o release cai para a chave debug (builds locais não quebram).

## CI (GitHub Actions)

O workflow `.github/workflows/build.yml` roda a cada push em `main` (e manualmente),
decodifica o keystore de `KEYSTORE_BASE64` e publica o APK assinado como artifact.

Para habilitar, crie os secrets do repositório:

```bash
base64 -w0 kenkern-release.jks > keystore.b64
# GitHub > Settings > Secrets and variables > Actions:
#   KEYSTORE_BASE64  = conteúdo de keystore.b64
#   KEYSTORE_PASSWORD = sua senha
#   KEY_ALIAS         = kenkern
#   KEY_PASSWORD      = sua senha
```

## Estrutura da Fase 1

- **Nicks e cenas**: `app/src/main/java/com/kenkern/engine/core/`
  - `Nick` (unidade básica), `Scene` (árvore), `NickClass`/`NickFunction` (classes e funções)
  - `.ck`: `core/serialization/CkFormat.kt` (texto estruturado, versionável)
  - Referências estáveis: `core/ReferenceId.kt` + `core/references/ReferenceSystem.kt`
  - Linkerf (só modelo de dados): `core/linkerf/`
- **Malhas .KERN**: `docs/KERN_SPEC.md` + `assets/mesh/` (KernWriter/KernReader)
- **Render**: `render/` (RenderBackend, GlesBackend, GeometryPipeline/LightingPipeline,
  OrbitCamera, TouchController, EngineRenderer, KenkernSurfaceView)
- **Editor**: `editor/` (ViewModel, painéis) + `ui/` (telas e painéis Compose)
- **Undo/Redo**: `commands/` (padrão Command)
- **Auto-save**: `autosave/AutoSaveManager.kt` (debounce + gravação atômica + recuperação)
- **Projetos**: `projects/ProjectManager.kt` (pastas + `project.kenkern`)

## Pendências conhecidas (planejadas para próximas fases)

Ver README da release / relatório da Fase 1: UI do Linkerf (Fase 4), VulkanBackend,
KENA (clusters), KELU (iluminação global), skinning/esqueleto animado, importação de
modelos externos (OBJ/glTF -> .KERN), seleção por toque no viewport.
