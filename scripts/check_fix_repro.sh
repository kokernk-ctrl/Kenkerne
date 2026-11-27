#!/usr/bin/env bash
# Portão 1 — regra "reproduzir antes de corrigir" (parte 2: prova real).
#
# Para cada commit fix/corrige com arquivo de teste, monta uma árvore de
# trabalho no PAI, copia SÓ os arquivos de teste tocados e roda essas classes.
# Pelo menos um teste tem que FALHAR no código antigo. Se todos passam, o
# teste não reproduz o bug e o CI falha com a mensagem exigida.
#
# Limite documentado (HARNESS.md §5): se o teste novo não compilar no pai
# (usa API que não existia), a compilação falha e conta como reprodução —
# a prova de compilação é mais fraca que uma asserção vermelha.
set -uo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
JSON="${1:-$RAIZ/gates/fix_commits.json}"
[ -f "$JSON" ] || { echo "sem fix_commits.json — nada a provar"; exit 0; }

COMITS="$(python3 - "$JSON" <<'PY'
import json, sys
d = json.load(open(sys.argv[1], encoding="utf-8"))
print("\n".join(
    f"{c['sha']}|{c['parent']}|" + ",".join(c["testes"])
    for c in d["fix_commits"] if c["testes"] and c["parent"]
))
PY
)"

[ -n "$COMITS" ] || { echo "nenhum commit fix com teste — nada a provar"; exit 0; }

FALHOU=0
while IFS= read -r linha; do
  [ -z "$linha" ] && continue
  SHA="${linha%%|*}"; RESTO="${linha#*|}"; PAI="${RESTO%%|*}"; ARQS="${RESTO#*|}"
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
    rel="${arq#app/src/test/java/}"; rel="${rel#app/src/androidTest/java/}"
    cls="${rel%.kt}"; cls="${cls////.}"
    TESTES="$TESTES --tests $cls"
  done
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
