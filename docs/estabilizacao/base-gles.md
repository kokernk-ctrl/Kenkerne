# Auditoria gráfica GLES — `GlesBackend.kt`

**Data:** 2026-11-23
**Escopo:** validação gráfica nativa no host Linux, sem alterar o repositório.

## Ambiente e resultado

- Ubuntu 24.04; EGL 1.5; Mesa 25.2.8; `GL_VENDOR=Mesa`.
- Contexto EGL surfaceless/pbuffer, GLES 3.2, renderizador **llvmpipe** (software, LLVM 20.1.2).
- Extraí diretamente os literais GLSL de `app/src/main/java/com/kenkern/engine/render/GlesBackend.kt` (15 shaders) e compilei/linkei os 8 pares: malha, fallback, céu, grade, fallback da grade, flat, linhas e gizmo de transformação.
- **8/8 pares compilaram e linkaram.**
- Desenhei um triângulo real com os shaders flat, matriz MVP identidade e cor uniforme; pixel central lido: **RGBA (64, 128, 191, 255)** para a cor solicitada (0.25, 0.5, 0.75, 1.0). `glGetError() = GL_NO_ERROR`.

## Reprodução

Dependências instaladas no host (não no repositório):

```sh
sudo apt-get update
sudo apt-get install -y pkg-config libegl1-mesa-dev libgles2-mesa-dev
```

Scripts e shaders extraídos ficam em `/tmp/ken-gles/`. A partir da raiz do repositório:

```sh
python3 /tmp/ken-gles/extract.py \
  app/src/main/java/com/kenkern/engine/render/GlesBackend.kt \
  /tmp/ken-gles/shaders
cc -O2 -Wall -Wextra /tmp/ken-gles/runner.c -o /tmp/ken-gles/runner \
  $(pkg-config --cflags --libs egl glesv2)
LIBGL_ALWAYS_SOFTWARE=1 EGL_PLATFORM=surfaceless \
  /tmp/ken-gles/runner /tmp/ken-gles/shaders
```

## Limites

Isto valida os fontes GLSL reais em Mesa/llvmpipe no host e um caminho de desenho/leitura de pixel flat. **Não é validação em Android, não substitui os drivers/GPU de aparelhos, nem executa o app, GLSurfaceView ou a integração Kotlin/Android.** Nenhum Gradle/Android foi executado e nenhum arquivo do repositório foi modificado.
