#!/usr/bin/env bash
# Portão 1 — regra "reproduzir antes de corrigir" (parte 2: prova real).
#
# Para cada commit fix/corrige com arquivo de teste, monta uma árvore de
# trabalho no PAI, copia SÓ os arquivos de teste tocados e roda essas classes.
# Pelo menos um teste tem que FALHAR no código antigo. Se todos passam, o
# teste não reproduz o bug e o CI falha com a mensagem exigida.
#
# Limite documentado (HARNESS.md §5 e DECISIONS.md "Prova de reprodução"):
#   * se o teste novo não compilar no pai (usa API que não existia), a
#     compilação falha e conta como reprodução — a prova de compilação é
#     mais fraca que uma asserção vermelha;
#   * commit fix SEM mudança em app/src/main (bug no próprio teste, ex.:
#     navegação do androidTest) NÃO tem prova do pai: a produção é idêntica,
#     então o mesmo teste não pode passar e falhar ao mesmo tempo — a
#     reprodução é a execução VERMELHA no histórico do CI (execuções 6/7/8
#     do Portão 2). O commit é pulado com a marca "PULO (sem produção)".
set -uo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
JSON="${1:-$RAIZ/gates/fix_commits.json}"
[ -f "$JSON" ] || { echo "sem fix_commits.json — nada a provar"; exit 0; }

COMITS="$(python3 - "$JSON" <<'PY'
import json, sys
d = json.load(open(sys.argv[1], encoding="utf-8"))
print("\n".join(
    f"{c['sha']}|{c['parent']}|" + ",".join(c["testes"]) + "|" + ",".join(c.get("producao", []))
    for c in d["fix_commits"] if c["testes"] and c["parent"]
))
PY
)"

[ -n "$COMITS" ] || { echo "nenhum commit fix com teste — nada a provar"; exit 0; }

FALHOU=0
while IFS= read -r linha; do
  [ -z "$linha" ] && continue
  SHA="${linha%%|*}"; RESTO="${linha#*|}"; PAI="${RESTO%%|*}"; RESTO="${RESTO#*|}"; ARQS="${RESTO%%|*}"; PROD="${RESTO#*|}"
  if [ -z "$PROD" ]; then
    echo "PULO (sem produção): $SHA não muda app/src/main — a prova de reprodução é a execução vermelha no histórico do CI (HARNESS §5)"
    continue
  fi
  DIR="/tmp/kenkern-repro-$SHA"
  rm -rf "$DIR"
  git -C "$RAIZ" worktree add --detach "$DIR" "$PAI" >/dev/null 2>&1 || {
    echo "ERRO: não foi possível montar worktree do pai $PAI"; FALHOU=1; continue; }
  # SDK e propriedades locais não entram no git.
  [ -f "$RAIZ/local.properties" ] && cp "$RAIZ/local.properties" "$DIR/local.properties"
  IFS=',' read -ra LISTA <<< "$ARQS"
  TESTES=""
  for arq in "${LISTA[@]}"; do
    mkdir -p "$DIR/$(dirname "$arq")"
    cp "$RAIZ/$arq" "$DIR/$arq"
    # Só classe com @Test entra no filtro (--tests de helper sem teste
    # devolveria "no tests found" e fingiria reprodução).
    grep -q "@Test" "$RAIZ/$arq" || continue
    rel="${arq#app/src/test/java/}"; rel="${rel#app/src/androidTest/java/}"; rel="${rel#app/src/testShared/java/}"
    cls="${rel%.kt}"; cls="${cls////.}"
    TESTES="$TESTES --tests $cls"
  done
  if [ -z "$TESTES" ]; then
    echo "PULO (sem classe executável): $SHA só toca código de teste SEM @Test — reprodução pelo histórico do CI"
    git -C "$RAIZ" worktree remove --force "$DIR" >/dev/null 2>&1 || true
    continue
  fi
  echo "== provando $SHA contra $PAI: $TESTES"
  ( cd "$DIR" && JAVA_HOME="${JAVA_HOME:-}" ./gradlew --no-daemon :app:testReleaseUnitTest $TESTES ) \
    > "/tmp/kenkern-repro-$SHA.log" 2>&1
  CODIGO=$?
  if [ $CODIGO -ne 0 ]; then
    echo "OK: teste(s) de $SHA FALHAM no código do pai (reproduz o bug)"
  else
    echo "ERRO: teste não reproduz o bug — todos passaram no pai $PAI ($SHA)"
    FALHOU=1
  fi
  git -C "$RAIZ" worktree remove --force "$DIR" >/dev/null 2>&1 || true
done <<< "$COMITS"

exit $FALHOU
