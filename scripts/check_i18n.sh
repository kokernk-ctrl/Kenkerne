#!/usr/bin/env bash
# =============================================================================
# KENKERN — CHECAGEM DE I18N (T0.1, adendo v4 — DECISIONS #27)
#
# Regra: NENHUMA tela (código em ui/) pode ter texto literal em Text("...")
# ou contentDescription = "..." — toda string de interface vem de recursos
# distribuídos por quaisquer XMLs em res/values/ e res/values-pt/.
#
# LISTA DE EXCEÇÕES (arquivo:motivo — manter CURTA e justificada):
#   (nenhuma hoje — dados técnicos do overlay de debug usam lines.add(...)
#    fora de Text(), e formatos numéricos usam recursos com placeholder)
#
# Falha com código 1 se encontrar literal; o CI bloqueia o APK.
# =============================================================================
set -euo pipefail

ROOT="${KENKERN_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
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

# Paridade de recursos localizados em TODOS os XMLs dos diretórios values.
# Extrair apenas recursos de texto evita comparar styles/colors/themes e elimina
# arquivos temporários globais: chamadas paralelas não compartilham estado.
EN_DIR="$ROOT/app/src/main/res/values"
PT_DIR="$ROOT/app/src/main/res/values-pt"
if ! python3 - "$EN_DIR" "$PT_DIR" <<'PY'
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

STRING_TYPES = {"string", "string-array", "plurals"}

def collect(directory):
    path = Path(directory)
    if not path.is_dir():
        raise ValueError(f"diretório não encontrado: {path}")
    files = sorted(path.glob("*.xml"))
    if not files:
        raise ValueError(f"nenhum XML em {path}")
    resources = {}
    for xml_file in files:
        try:
            root = ET.parse(xml_file).getroot()
        except ET.ParseError as error:
            raise ValueError(f"XML inválido em {xml_file}: {error}") from error
        for element in root.iter():
            tag = element.tag.rsplit("}", 1)[-1]
            resource_type = (
                element.get("type") if tag == "item" else tag
            )
            if resource_type in STRING_TYPES and element.get("name"):
                key = f"{resource_type}/{element.get('name')}"
                resources.setdefault(key, []).append(xml_file.name)
    return resources

try:
    english = collect(sys.argv[1])
    portuguese = collect(sys.argv[2])
except ValueError as error:
    print(f"ERRO i18n: {error}")
    sys.exit(1)

if not english:
    print("ERRO i18n: nenhum recurso string localizado encontrado em values/.")
    sys.exit(1)

missing = sorted(english.keys() - portuguese.keys())
extra = sorted(portuguese.keys() - english.keys())
if missing:
    print("ERRO i18n: recursos sem tradução em values-pt/:")
    for key in missing:
        print(f"    {key} (values/{', '.join(english[key])})")
if extra:
    print("ERRO i18n: recursos em values-pt/ sem chave em values/:")
    for key in extra:
        print(f"    {key} (values-pt/{', '.join(portuguese[key])})")
if missing or extra:
    sys.exit(1)
print(
    "Paridade en/pt: OK "
    f"({len(english)} recursos string/string-array/plurals; styles e themes ignorados)."
)
PY
then
    FALHAS=$((FALHAS + 1))
fi

if [ "$FALHAS" -gt 0 ]; then
    echo ""
    echo "CHECAGEM DE I18N: $FALHAS problema(s) — toda string de tela vem de recursos (DECISIONS #27)."
    exit 1
fi

echo "CHECAGEM DE I18N: OK (sem literais em telas; en/pt com paridade de chaves)."
