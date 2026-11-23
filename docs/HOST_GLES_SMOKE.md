# Host GLES shader smoke test

`scripts/host-gles-smoke.py` extracts the GLSL literals from
`GlesBackend.kt`, builds a small EGL/GLES3 harness, then compiles and links all
eight shader pairs. It also renders a triangle with the real flat shaders and
checks the center pixel. Paths are resolved from the checkout; no generated
files are written into the repository.

## Requirements (Ubuntu/Debian)

```sh
sudo apt-get update
sudo apt-get install -y build-essential pkg-config python3 \
  libegl1-mesa-dev libgles2-mesa-dev
```

## Run

From the repository root:

```sh
./scripts/host-gles-smoke.py
```

The script uses EGL surfaceless mode and forces Mesa software rendering
(`llvmpipe`) for a repeatable host check. It creates and removes a temporary
build/extraction directory automatically. To retain that directory for
inspection:

```sh
./scripts/host-gles-smoke.py --keep-temp
```

An alternate backend source can be specified with `--source PATH`. `CC` may
select a different C compiler.

## Scope

This checks the shader source and a basic GLES draw/readback on the host Mesa
implementation. It is **not Android validation** and does not test Android
drivers, `GLSurfaceView`, Kotlin integration, or device behavior. It does not
run Gradle.
