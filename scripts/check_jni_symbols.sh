#!/usr/bin/env bash
# =============================================================================
# KENKERN — CHECAGEM DE SÍMBOLOS JNI DA libkenkern.so (E1, v0.1.18)
#
# REGRA: TODA `external fun` declarada em Kotlin precisa do símbolo
# Java_<pacote>_<Objeto>_<nome> DEFINIDO na libkenkern.so. O bug E1 foi
# exatamente isso: instalarHandlerSinaisNativo/handlerInstaladoNativo não
# tinham símbolo (o C++ exportava …_instalarHandlerSinais/…_handlerInstalado)
# e o JNI devolvia "No implementation found" no aparelho — com a .so carregada.
#
# Uso:
#   scripts/check_jni_symbols.sh <libkenkern.so> [ferramenta-nm]
#
# A ferramenta padrão é `nm` (host). No CI usa o llvm-nm do NDK sobre a .so
# arm64-v8a EXTRAÍDA do APK release (mesma conferência, binário de verdade).
# Falha com código 1 se QUALQUER external fun não tiver símbolo.
# =============================================================================
set -euo pipefail

SO="${1:?uso: check_jni_symbols.sh <libkenkern.so> [nm]}"
NM="${2:-nm}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIR_KT="$ROOT/app/src/main/java/com/kenkern/engine/nativecore"

[ -f "$SO" ] || { echo "ERRO: '$SO' não existe"; exit 1; }

# Nome JNI: pacote com.kenkern.engine.nativecore → com_kenkern_engine_nativecore;
# método: '_' vira '_1' (regra JNI §Resolving Native Method Names).
jni_nome() {
    local metodo="$1"
    local esc="${metodo//_/_1}"
    echo "Java_com_kenkern_engine_nativecore_${Objeto}_${esc}"
}

echo "== Símbolos definidos na .so ($("$NM" --version | head -1 | sed 's/^ *//') =="
SIMBOLOS="$("$NM" -D --defined-only "$SO" | awk '{print $NF}' | sort -u)"
echo "$SIMBOLOS" | grep -c '^Java_' | sed 's/^/   Java_* definidos: /'

FALTANDO=0
TESTADAS=0
while IFS= read -r arquivo; do
    Objeto="$(basename "$arquivo" .kt)"
    while IFS= read -r linha; do
        metodo="$(echo "$linha" | sed -n 's/.*external fun \([A-Za-z_][A-Za-z0-9_]*\).*/\1/p')"
        [ -z "$metodo" ] && continue
        esperado="$(jni_nome "$metodo")"
        TESTADAS=$((TESTADAS + 1))
        if echo "$SIMBOLOS" | grep -qx "$esperado"; then
            echo "   ✓ $Objeto.$metodo → $esperado"
        else
            echo "   ✗ $Objeto.$metodo → $esperado  FALTA NA .so"
            FALTANDO=$((FALTANDO + 1))
        fi
    done < <(grep -n 'external fun' "$arquivo")
done < <(find "$DIR_KT" -name '*.kt' | sort)

echo "== Conferidas $TESTADAS external fun contra $SO =="
if [ "$FALTANDO" -gt 0 ]; then
    echo "ERRO: $FALTANDO external fun sem símbolo JNI na .so"
    exit 1
fi
echo "OK: toda external fun tem símbolo JNI definido na libkenkern.so"
