#!/usr/bin/env python3
"""Portão 1 — ratchet, arquivos obrigatórios e palavras proibidas.

Uso:
  python3 scripts/check_gates.py [--base-ref <ref>] [--json gates/resultado.json]

Regras (HARNESS.md §3.1):
  * nunca diminuir: classes de teste, métodos @Test, checagens do Diagnóstico;
  * nunca aumentar @Ignore;
  * contador maior que o baseline também falha: o baseline é a prova do
    número atual e precisa ser aumentado junto com o teste novo;
  * palavras proibidas em CHANGELOG/PROGRESS/TESTES_PENDENTES/DECISIONS/REPORT;
  * arquivos obrigatórios citados em ROADMAP.md existem no repositório.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

ARQUIVOS_OBRIGATORIOS = [
    "ROADMAP.md",
    "PROGRESS.md",
    "NICKS.md",
    "HARNESS.md",
    "LUA_API.md",
    "TESTES_PENDENTES.md",
    "AUDITORIA.md",
]

ARQUIVOS_VARRIDOS = [
    "CHANGELOG.md",
    "PROGRESS.md",
    "TESTES_PENDENTES.md",
    "DECISIONS.md",
    "REPORT.md",
]

# HARNESS.md §2 — proibidas com contorno de palavra (acho ≠ achado).
PALAVRAS_PROIBIDAS = [
    r"talvez",
    r"acho",
    r"provavelmente",
    r"possivelmente",
    r"parece\s+que",
    r"sei\s+lá",
    r"deve\s+funcionar",
]

CAMINHOS_PORTAO = (".github/", "gates/", "HARNESS.md")


def ler(caminho: str) -> str:
    with open(os.path.join(RAIZ, caminho), encoding="utf-8") as f:
        return f.read()


def contar_testes() -> tuple[int, int, int]:
    """(classes de teste JVM, métodos @Test JVM, métodos @Test androidTest)."""
    classes = 0
    metodos_jvm = 0
    metodos_android = 0
    for base, eh_android in (("app/src/test", False), ("app/src/androidTest", True)):
        raiz = os.path.join(RAIZ, base)
        for dirpath, _, nomes in os.walk(raiz):
            for nome in nomes:
                if not nome.endswith(".kt"):
                    continue
                texto = ler(os.path.relpath(os.path.join(dirpath, nome), RAIZ))
                n = len(re.findall(r"@Test\b", texto))
                if n == 0:
                    continue
                if not eh_android:
                    classes += 1
                    metodos_jvm += n
                else:
                    metodos_android += n
    return classes, metodos_jvm, metodos_android


def contar_ignorados() -> int:
    total = 0
    for base in ("app/src/main", "app/src/test", "app/src/androidTest"):
        raiz = os.path.join(RAIZ, base)
        if not os.path.isdir(raiz):
            continue
        for dirpath, _, nomes in os.walk(raiz):
            for nome in nomes:
                if nome.endswith(".kt"):
                    total += len(re.findall(r"@Ignore\b", ler(os.path.relpath(os.path.join(dirpath, nome), RAIZ))))
    return total


def contar_checagens() -> int:
    diag = ler("app/src/main/java/com/kenkern/engine/diag/Diagnostico.kt")
    return len(re.findall(r'Checagem\("', diag))


def palavras_proibidas() -> list[tuple[str, int, str]]:
    achados: list[tuple[str, int, str]] = []
    padrao = re.compile("|".join(PALAVRAS_PROIBIDAS), re.IGNORECASE)
    for caminho in ARQUIVOS_VARRIDOS:
        if not os.path.exists(os.path.join(RAIZ, caminho)):
            continue
        for numero, linha in enumerate(ler(caminho).splitlines(), 1):
            m = padrao.search(linha)
            if m:
                achados.append((caminho, numero, m.group(0)))
    return achados


def arquivos_faltando() -> list[str]:
    return [a for a in ARQUIVOS_OBRIGATORIOS if not os.path.exists(os.path.join(RAIZ, a))]


def arquivos_alterados(base_ref: str | None) -> list[str]:
    if not base_ref:
        return []
    try:
        out = subprocess.run(
            ["git", "diff", "--name-only", f"{base_ref}...HEAD"],
            cwd=RAIZ, capture_output=True, text=True, check=True,
        ).stdout
    except (subprocess.CalledProcessError, FileNotFoundError):
        return []
    return [l.strip() for l in out.splitlines() if l.strip().startswith(CAMINHOS_PORTAO)]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--base-ref", default=None)
    ap.add_argument("--json", default=None)
    args = ap.parse_args()

    baseline = json.loads(ler("gates/baseline.json"))
    classes, metodos, android = contar_testes()
    ignorados = contar_ignorados()
    checagens = contar_checagens()

    atual = {
        "jvm_test_classes": classes,
        "jvm_test_methods": metodos,
        "android_test_methods": android,
        "diag_checks": checagens,
        "ignored": ignorados,
    }

    erros: list[str] = []
    # Ratchet: cada contador tem que ser EXATAMENTE o baseline (para cima ou
    # para baixo) — subir obriga a atualizar o baseline no mesmo commit.
    for chave in ("jvm_test_classes", "jvm_test_methods", "android_test_methods", "diag_checks"):
        base = int(baseline.get(chave, 0))
        valor = atual[chave]
        if valor < base:
            erros.append(f"RATCHET DIMINUIU: {chave}={valor} < baseline={base}")
        elif valor > base:
            erros.append(
                f"RATCHET DESATUALIZADO: {chave}={valor} > baseline={base} "
                f"— aumente gates/baseline.json junto com o teste novo"
            )
    if ignorados > int(baseline.get("ignored", 0)):
        erros.append(f"RATCHET DIMINUIU: @Ignore={ignorados} > baseline={baseline.get('ignored', 0)}")

    for caminho, linha, palavra in palavras_proibidas():
        erros.append(f"PALAVRA PROIBIDA: {caminho}:{linha} — '{palavra}' (HARNESS.md §2)")

    for caminho in arquivos_faltando():
        erros.append(f"ARQUIVO OBRIGATÓRIO AUSENTE: {caminho}")

    alterados = arquivos_alterados(args.base_ref)
    if alterados:
        with open(os.path.join(RAIZ, "gates", "alterados.txt"), "w", encoding="utf-8") as f:
            f.write("\n".join(alterados) + "\n")

    resultado = {
        "atual": atual,
        "baseline": {k: baseline.get(k, 0) for k in atual},
        "gates_alterados": alterados,
        "erros": erros,
        "ok": not erros,
    }
    if args.json:
        os.makedirs(os.path.dirname(os.path.join(RAIZ, args.json)), exist_ok=True)
        with open(os.path.join(RAIZ, args.json), "w", encoding="utf-8") as f:
            json.dump(resultado, f, ensure_ascii=False, indent=2)

    print("== PORTÃO 1 — ratchet ==")
    for chave in atual:
        print(f"{chave}: {atual[chave]} (baseline {baseline.get(chave, 0)})")
    print(f"gates alterados: {alterados if alterados else 'nenhum'}")
    if alterados:
        print("GATES ALTERADOS")
    if erros:
        for e in erros:
            print("ERRO: " + e)
        return 1
    print("OK — portão 1 verde")
    return 0


if __name__ == "__main__":
    sys.exit(main())
