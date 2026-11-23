#!/usr/bin/env python3
"""Compile and exercise GlesBackend.kt shader pairs in host Mesa GLES."""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
import textwrap


SHADER_NAMES = (
    "vertexShader",
    "fragmentShader",
    "fallbackVertexShader",
    "fallbackFragmentShader",
    "skyVertexShader",
    "skyFragmentShader",
    "gridVertexShader",
    "gridFragmentShader",
    "gridFallbackFragmentShader",
    "flatVertexShader",
    "flatFragmentShader",
    "lineVertexShader",
    "lineFragmentShader",
    "tGizmoVertexShader",
    "tGizmoFragmentShader",
)


def extract_shaders(source_path: Path, output_dir: Path) -> None:
    source = source_path.read_text(encoding="utf-8")
    output_dir.mkdir(parents=True, exist_ok=True)
    for name in SHADER_NAMES:
        match = re.search(
            rf"\b{re.escape(name)}\s*=\s*\"\"\"(.*?)\"\"\"\.trimIndent\(\)",
            source,
            flags=re.DOTALL,
        )
        if not match:
            raise RuntimeError(f"não encontrei o shader Kotlin {name} em {source_path}")
        # Kotlin's trimIndent removes common indentation and edge blank lines.
        shader = textwrap.dedent(match.group(1)).strip() + "\n"
        (output_dir / f"{name}.glsl").write_text(shader, encoding="utf-8")


def run(command: list[str], *, env: dict[str, str] | None = None) -> None:
    result = subprocess.run(command, env=env, check=False)
    if result.returncode:
        raise subprocess.CalledProcessError(result.returncode, command)


def main() -> int:
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(
        description="Build and run the GlesBackend host GLES3 smoke test (Mesa/llvmpipe)."
    )
    parser.add_argument(
        "--source",
        type=Path,
        default=root / "app/src/main/java/com/kenkern/engine/render/GlesBackend.kt",
        help="Kotlin source to extract (default: this checkout's GlesBackend.kt).",
    )
    parser.add_argument(
        "--keep-temp",
        action="store_true",
        help="keep extracted GLSL and the compiled runner; print their directory.",
    )
    args = parser.parse_args()
    source_path = args.source.resolve()
    if not source_path.is_file():
        parser.error(f"fonte não encontrado: {source_path}")

    if not shutil.which("pkg-config"):
        parser.error(
            "pkg-config ausente; instale pkg-config e as dependências EGL/GLES "
            "(docs/HOST_GLES_SMOKE.md)."
        )
    compiler = shlex.split(os.environ.get("CC", "cc"))
    if not compiler or not shutil.which(compiler[0]):
        parser.error(
            f"compilador C {compiler[0] if compiler else '(vazio)'} ausente; "
            "instale build-essential (docs/HOST_GLES_SMOKE.md)."
        )
    pkg = subprocess.run(
        ["pkg-config", "--cflags", "--libs", "egl", "glesv2"],
        text=True,
        capture_output=True,
        check=False,
    )
    if pkg.returncode:
        parser.error(
            "pkg-config não encontrou egl/glesv2; instale os pacotes de "
            "desenvolvimento (docs/HOST_GLES_SMOKE.md)."
        )

    temp = Path(tempfile.mkdtemp(prefix="kenkern-gles-smoke-"))
    try:
        shaders = temp / "shaders"
        extract_shaders(source_path, shaders)
        runner = temp / "host-gles-smoke"
        command = (
            compiler
            + [
                "-std=c11",
                "-O2",
                "-Wall",
                "-Wextra",
                str(root / "scripts/host-gles-smoke.c"),
                "-o",
                str(runner),
            ]
            + shlex.split(pkg.stdout)
        )
        print(f"Compilando harness C com {' '.join(compiler)}…", flush=True)
        run(command)

        env = os.environ.copy()
        env["LIBGL_ALWAYS_SOFTWARE"] = "1"
        env["EGL_PLATFORM"] = "surfaceless"
        print(f"Fonte: {source_path}", flush=True)
        print(f"Shaders GLSL extraídos: {len(SHADER_NAMES)}", flush=True)
        run([str(runner), str(shaders)], env=env)
        return 0
    except subprocess.CalledProcessError as exc:
        return exc.returncode
    except (OSError, RuntimeError) as exc:
        print(f"host-gles-smoke: {exc}", file=sys.stderr)
        return 1
    finally:
        if args.keep_temp:
            print(f"Arquivos temporários mantidos em: {temp}")
        else:
            shutil.rmtree(temp, ignore_errors=True)


if __name__ == "__main__":
    raise SystemExit(main())
