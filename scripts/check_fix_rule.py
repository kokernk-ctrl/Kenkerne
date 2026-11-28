#!/usr/bin/env python3
"""Portão 1 — regra "reproduzir antes de corrigir" (parte 1: arquivo de teste).

Um commit cujo assunto começa com "fix" ou "corrige" precisa alterar ou criar
ao menos um arquivo de TESTE: app/src/test, app/src/androidTest ou
app/src/testShared (código de teste compartilhado pelos dois runners, ligado
por android.sourceSets no app/build.gradle.kts). Sem isso o CI falha. O
commit que só documenta (chore/docs) não é afetado.

Saída: gates/fix_commits.json com os commits fix, os arquivos de teste
tocados e os arquivos de PRODUÇÃO (app/src/main) tocados — o campo
"producao" decide se a prova de reprodução do pai é aplicável (HARNESS.md §5
e DECISIONS.md "Prova de reprodução").

Uso:
  python3 scripts/check_fix_rule.py --base-ref <ref> [--json gates/fix_commits.json]
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PADRAO_FIX = re.compile(r"^(fix|corrige)\b", re.IGNORECASE)
PREFIXOS_TESTE = ("app/src/test/", "app/src/androidTest/", "app/src/testShared/")
PREFIXO_PRODUCAO = "app/src/main/"


def git(*args: str) -> str:
    return subprocess.run(
        ["git", *args], cwd=RAIZ, capture_output=True, text=True, check=True
    ).stdout


def commits_entre(base: str | None) -> list[str]:
    if base:
        try:
            return git("rev-list", f"{base}..HEAD").split()
        except subprocess.CalledProcessError:
            return []
    # Sem base (tag/dispatch): avalia só o commit pontuado.
    return [git("rev-parse", "HEAD").strip()]


def arquivos_do_commit(sha: str) -> list[str]:
    try:
        out = git("diff-tree", "--no-commit-id", "--name-only", "-r", sha)
    except subprocess.CalledProcessError:
        return []
    return [l.strip() for l in out.splitlines() if l.strip()]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--base-ref", default=os.environ.get("BASE_REF"))
    ap.add_argument("--json", default="gates/fix_commits.json")
    args = ap.parse_args()

    erros: list[str] = []
    fix_commits: list[dict] = []

    for sha in commits_entre(args.base_ref):
        assunto = git("log", "-1", "--format=%s", sha).strip()
        if not PADRAO_FIX.match(assunto):
            continue
        arquivos = arquivos_do_commit(sha)
        testes = [a for a in arquivos if a.startswith(PREFIXOS_TESTE)]
        producao = [a for a in arquivos if a.startswith(PREFIXO_PRODUCAO)]
        # Commit de merge não é avaliado (o diff contra o pai é o merge).
        pais = git("rev-list", "--parents", "-n", "1", sha).split()[1:]
        if len(pais) > 1:
            continue
        fix_commits.append({
            "sha": sha[:7],
            "parent": pais[0][:7] if pais else "",
            "assunto": assunto,
            "testes": testes,
            "producao": producao,
        })
        if not testes:
            erros.append(
                f"FIX SEM TESTE: {sha[:7]} '{assunto}' não toca app/src/test, app/src/androidTest "
                "nem app/src/testShared (HARNESS.md §3: reproduzir antes de corrigir)"
            )

    os.makedirs(os.path.join(RAIZ, "gates"), exist_ok=True)
    with open(os.path.join(RAIZ, args.json), "w", encoding="utf-8") as f:
        json.dump({"fix_commits": fix_commits, "erros": erros}, f, ensure_ascii=False, indent=2)

    print("== PORTÃO 1 — regra fix/corrige ==")
    if not fix_commits:
        print("nenhum commit fix/corrige no intervalo")
    for c in fix_commits:
        print(f"{c['sha']}: {len(c['testes'])} arquivo(s) de teste, "
              f"{len(c['producao'])} de produção — {c['assunto'][:70]}")
    if erros:
        for e in erros:
            print("ERRO: " + e)
        return 1
    print("OK — todo commit fix/corrige toca arquivo de teste")
    return 0


if __name__ == "__main__":
    sys.exit(main())
