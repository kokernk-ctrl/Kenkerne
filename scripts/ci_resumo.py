#!/usr/bin/env python3
"""Gera CI-RESUMO.md (máximo 40 linhas) para a execução e para o step summary.

Entrada (arquivos baixados dos artifacts):
  logs/gate1.log, logs/gate2.log, logs/report.log, gates/resultado.json
Variáveis:
  GATE1_RESULT, GATE2_RESULT, REPORT_RESULT (success|failure|skipped|cancelled)
  GATES_ALTERADOS (1 = o commit tocou .github/, gates/ ou HARNESS.md)
  CI_RESUMO_PATH (padrão CI-RESUMO.md)
"""
from __future__ import annotations

import json
import os
import re
import subprocess

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAX_LINHAS = 40


def git(*args: str) -> str:
    try:
        return subprocess.run(["git", *args], cwd=RAIZ, capture_output=True, text=True, check=True).stdout.strip()
    except subprocess.CalledProcessError:
        return "?"


def versao() -> str:
    try:
        texto = open(os.path.join(RAIZ, "app/build.gradle.kts"), encoding="utf-8").read()
        return re.search(r'versionName = "([^"]+)"', texto).group(1)
    except (OSError, AttributeError):
        return "?"


def resultado(nome: str) -> str:
    valor = os.environ.get(nome, "skipped").lower()
    return {"success": "VERDE", "failure": "VERMELHO", "cancelled": "VERMELHO"}.get(valor, "PULADO")


def erros_do_log(caminho: str, max_linhas: int = 10) -> list[str]:
    p = os.path.join(RAIZ, caminho)
    if not os.path.exists(p):
        return []
    linhas = open(p, encoding="utf-8", errors="replace").read().splitlines()
    achadas: list[str] = []
    padrao = re.compile(r"(ERRO:|FAILURE|FAILED|error:|Exception|BUILD FAILED|teste não reproduz)", re.IGNORECASE)
    for l in linhas:
        if padrao.search(l):
            achadas.append(l.strip())
            if len(achadas) >= max_linhas:
                break
    return achadas


def main() -> int:
    saida = os.environ.get("CI_RESUMO_PATH", "CI-RESUMO.md")
    resultado_json = {}
    p = os.path.join(RAIZ, "gates/resultado.json")
    if os.path.exists(p):
        resultado_json = json.load(open(p, encoding="utf-8"))
    atual = resultado_json.get("atual", {})
    base = resultado_json.get("baseline", {})
    alterados = os.environ.get("GATES_ALTERADOS", "0") == "1"

    g1, g2, rep = resultado("GATE1_RESULT"), resultado("GATE2_RESULT"), resultado("REPORT_RESULT")
    # O relatório é validado DENTRO do Portão 1; se o log do relatório tem
    # erro, o veredito dele é vermelho mesmo que o job ainda não tenha caído.
    p_rep = os.path.join(RAIZ, "logs/report.log")
    if os.path.exists(p_rep) and "ERRO:" in open(p_rep, encoding="utf-8", errors="replace").read():
        rep = "VERMELHO"
    hash_curto = git("rev-parse", "--short", "HEAD") or "?"
    modo_g2 = os.environ.get("GATE2_MODO") or "não rodou"
    tempo = os.environ.get("CI_TEMPO", "?")

    L: list[str] = []
    L.append("# CI-RESUMO")
    L.append("")
    L.append(f"- Versão: {versao()} · commit `{hash_curto}` · tempo total: {tempo}")
    L.append(f"- Portão 1 (rápido): **{g1}**")
    L.append(f"- Portão 2 ({modo_g2}): **{g2}**")
    L.append(f"- Verificação do REPORT.md: **{rep}**")
    L.append(
        "- Ratchet: testes JVM {tm}/{tb} · classes {tc}/{tcb} · androidTest {am}/{amb} · "
        "checagens {dc}/{dcb} · @Ignore {ig}/{igb}".format(
            tm=atual.get("jvm_test_methods", "?"), tb=base.get("jvm_test_methods", "?"),
            tc=atual.get("jvm_test_classes", "?"), tcb=base.get("jvm_test_classes", "?"),
            am=atual.get("android_test_methods", "?"), amb=base.get("android_test_methods", "?"),
            dc=atual.get("diag_checks", "?"), dcb=base.get("diag_checks", "?"),
            ig=atual.get("ignored", "?"), igb=base.get("ignored", "?"),
        )
    )
    if alterados:
        L.append("")
        L.append("## GATES ALTERADOS")
        L.append("")
        L.append("```diff")
        L.append("- GATES ALTERADOS: este commit tocou .github/, gates/ ou HARNESS.md")
        L.append("```")
    L.append("")
    if g1 == "VERMELHO" or g2 == "VERMELHO" or rep == "VERMELHO":
        L.append("## Erros")
        L.append("")
        for nome, log in (("Portão 1", "logs/gate1.log"), ("Portão 2", "logs/gate2.log"), ("REPORT", "logs/report.log")):
            erros = erros_do_log(log)
            if erros:
                L.append(f"**{nome}**")
                for e in erros:
                    L.append(f"- `{e[:300]}`")
                L.append("")
    else:
        L.append("Nenhum erro nos três portões.")
    L.append("")

    # Corte duro: nunca mais de 40 linhas.
    L = L[:MAX_LINHAS]
    texto = "\n".join(L) + "\n"
    with open(os.path.join(RAIZ, saida), "w", encoding="utf-8") as f:
        f.write(texto)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write(texto)
    print(texto)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
