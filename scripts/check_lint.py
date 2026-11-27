#!/usr/bin/env python3
"""Portão 1 — lint sem ERROS NOVOS.

Conta <issue severity="Error"> (e Fatal) do relatório do lint e compara com
gates/baseline.json ("lint_errors"). Se subiu, o CI falha; se caiu, o baseline
pode ser reduzido no mesmo commit.
"""
from __future__ import annotations

import json
import os
import re
import sys

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RELATORIOS = [
    "app/build/reports/lint-results-release.xml",
    "app/build/reports/lint-results-debug.xml",
]


def main() -> int:
    baseline = json.load(open(os.path.join(RAIZ, "gates/baseline.json"), encoding="utf-8"))
    limite = int(baseline.get("lint_errors", 0))
    caminho = next((p for p in RELATORIOS if os.path.exists(os.path.join(RAIZ, p))), None)
    if not caminho:
        print("ERRO: relatório do lint ausente — rode ./gradlew lintRelease antes")
        return 1
    texto = open(os.path.join(RAIZ, caminho), encoding="utf-8").read()
    erros = re.findall(r'<issue[^>]*severity="(?:Error|Fatal)"[^>]*>', texto)
    ids = re.findall(r'<issue[^>]*id="([^"]+)"[^>]*severity="(?:Error|Fatal)"', texto)
    print("== PORTÃO 1 — lint ==")
    print(f"erros de lint: {len(erros)} (baseline {limite})")
    for i in ids:
        print("  - " + i)
    if len(erros) > limite:
        print(f"ERRO: lint subiu para {len(erros)} erros (baseline {limite})")
        return 1
    print("OK — nenhum erro novo")
    return 0


if __name__ == "__main__":
    sys.exit(main())
