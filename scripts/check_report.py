#!/usr/bin/env python3
"""Portão 1 — valida o formato do REPORT.md (HARNESS.md §4.1).

Falha se:
  * faltar uma das seções obrigatórias;
  * um item "feito" não tiver commit;
  * houver palavra proibida (HARNESS.md §2).
"""
from __future__ import annotations

import os
import re
import sys

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SECOES = ["## Itens feitos", "## Itens NÃO feitos", "## Números reais"]
PADRAO_COMMIT = re.compile(r"\b[0-9a-f]{7,40}\b")
PALAVRAS = re.compile(
    r"\b(talvez|acho|provavelmente|possivelmente)\b|parece\s+que|sei\s+lá|deve\s+funcionar",
    re.IGNORECASE,
)


def main() -> int:
    caminho = os.path.join(RAIZ, "REPORT.md")
    if not os.path.exists(caminho):
        print("ERRO: REPORT.md ausente")
        return 1
    texto = open(caminho, encoding="utf-8").read()
    erros: list[str] = []

    for secao in SECOES:
        if secao not in texto:
            erros.append(f"seção ausente: '{secao}'")

    # Itens "feito": linhas de bullet iniciadas por **ID** até o próximo bullet.
    dentro = False
    item_atual: list[str] = []
    itens: list[list[str]] = []
    for linha in texto.splitlines():
        if linha.startswith("## "):
            dentro = linha.strip() == SECOES[0]
            if item_atual:
                itens.append(item_atual)
                item_atual = []
            continue
        if not dentro:
            continue
        if re.match(r"^\s*-\s+\*\*", linha):
            if item_atual:
                itens.append(item_atual)
            item_atual = [linha]
        elif item_atual:
            item_atual.append(linha)
    if item_atual:
        itens.append(item_atual)

    for item in itens:
        bloco = "\n".join(item)
        titulo = item[0].strip()[:80]
        if not PADRAO_COMMIT.search(bloco):
            erros.append(f"item feito sem commit: {titulo}")
        if "Causa raiz" not in bloco:
            erros.append(f"item feito sem 'Causa raiz': {titulo}")

    for numero, linha in enumerate(texto.splitlines(), 1):
        m = PALAVRAS.search(linha)
        if m:
            erros.append(f"PALAVRA PROIBIDA: REPORT.md:{numero} — '{m.group(0)}'")

    print("== PORTÃO 1 — REPORT.md ==")
    print(f"itens feitos: {len(itens)}")
    if erros:
        for e in erros:
            print("ERRO: " + e)
        return 1
    print("OK — seções, commits e linguagem do relatório")
    return 0


if __name__ == "__main__":
    sys.exit(main())
