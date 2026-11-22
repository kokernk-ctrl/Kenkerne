#!/usr/bin/env bash
# =============================================================================
# KENKERN — CHECAGEM DE I18N (T0.1, adendo v4 — DECISIONS #27)
#
# Regra: NENHUMA tela (código em ui/) pode ter texto literal em Text("...")
# ou contentDescription = "..." — toda string de interface vem de recursos
# (res/values/strings.xml = inglês + res/values-pt/strings.xml = português).
#
# LISTA DE EXCEÇÕES (arquivo:motivo — manter CURTA e justificada):
#   (nenhuma hoje — dados técnicos do overlay de debug usam lines.add(...)
#    fora de Text(), e formatos numéricos usam recursos com placeholder)
#
# Falha com código 1 se encontrar literal; o CI bloqueia o APK.
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UI_DIR="$ROOT/app/src/main/java/com/kenkern/engine/ui"

# Padrões proibidos: Text(" ... e contentDescription = " ...
PADRAO_TEXT='Text("'
PADRAO_CD='contentDescription = "'

FALHAS=0
while IFS= read -r arquivo; do
    while IFS=: read -r linha _; do
        [ -z "${linha:-}" ] && continue
        echo "ERRO i18n: literal em Text() — $arquivo:$linha"
        sed -n "${linha}p" "$arquivo" | sed 's/^/    /'
        FALHAS=$((FALHAS + 1))
    done < <(grep -Fn "$PADRAO_TEXT" "$arquivo" || true)
    while IFS=: read -r linha _; do
        [ -z "${linha:-}" ] && continue
        echo "ERRO i18n: contentDescription literal — $arquivo:$linha"
        sed -n "${linha}p" "$arquivo" | sed 's/^/    /'
        FALHAS=$((FALHAS + 1))
    done < <(grep -Fn "$PADRAO_CD" "$arquivo" || true)
done < <(find "$UI_DIR" -name '*.kt' -type f | sort)

# Paridade en/pt: toda chave de values/strings.xml existe em values-pt/strings.xml
EN="$ROOT/app/src/main/res/values/strings.xml"
PT="$ROOT/app/src/main/res/values-pt/strings.xml"
if [ -f "$EN" ] && [ -f "$PT" ]; then
    chaves() { grep -o 'name="[^"]*"' "$1" | sort; }
    chaves "$EN" > /tmp/kenkern_i18n_en.txt
    chaves "$PT" > /tmp/kenkern_i18n_pt.txt
    FALTANDO=$(comm -23 /tmp/kenkern_i18n_en.txt /tmp/kenkern_i18n_pt.txt || true)
    SOBRANDO=$(comm -13 /tmp/kenkern_i18n_en.txt /tmp/kenkern_i18n_pt.txt || true)
    if [ -n "$FALTANDO" ]; then
        echo "ERRO i18n: chaves sem tradução em values-pt/strings.xml:"
        echo "$FALTANDO" | sed 's/^/    /'
        FALHAS=$((FALHAS + 1))
    fi
    if [ -n "$SOBRANDO" ]; then
        echo "ERRO i18n: chaves em values-pt/ SEM correspondente em values/strings.xml:"
        echo "$SOBRANDO" | sed 's/^/    /'
        FALHAS=$((FALHAS + 1))
    fi
else
    echo "ERRO i18n: strings.xml ou values-pt/strings.xml não encontrado"
    FALHAS=$((FALHAS + 1))
fi

if [ "$FALHAS" -gt 0 ]; then
    echo ""
    echo "CHECAGEM DE I18N: $FALHAS problema(s) — toda string de tela vem de recursos (DECISIONS #27)."
    exit 1
fi

echo "CHECAGEM DE I18N: OK (sem literais em telas; en/pt com paridade de chaves)."
