#!/usr/bin/env python3
"""Portão de publicação — a tag vX.Y.Z precisa coincidir com o versionName.

Uso: python3 scripts/check_release.py v0.1.32
Falha (exit 1) se a versão da tag não é a do app/build.gradle.kts.
"""
from __future__ import annotations

import os
import re
import sys

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def main() -> int:
    if len(sys.argv) != 2:
        print("uso: check_release.py vX.Y.Z")
        return 2
    tag = sys.argv[1].strip()
    m = re.fullmatch(r"v(\d+\.\d+\.\d+)", tag)
    if not m:
        print(f"ERRO: tag inválida: {tag} (esperado vX.Y.Z)")
        return 1
    esperado = m.group(1)
    texto = open(os.path.join(RAIZ, "app/build.gradle.kts"), encoding="utf-8").read()
    m2 = re.search(r'versionName = "([^"]+)"', texto)
    if not m2:
        print("ERRO: versionName não encontrado em app/build.gradle.kts")
        return 1
    atual = m2.group(1)
    if atual != esperado:
        print(f"ERRO: tag {tag} não coincide com versionName {atual}")
        return 1
    print(f"OK — tag {tag} = versionName {atual}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
