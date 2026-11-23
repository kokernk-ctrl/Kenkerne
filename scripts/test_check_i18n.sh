#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHECK_SCRIPT="$SCRIPT_DIR/check_i18n.sh"
TEMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/kenkern-i18n-test.XXXXXX")"
trap 'rm -rf "$TEMP_ROOT"' EXIT
FIXTURE="$TEMP_ROOT/project"
RES="$FIXTURE/app/src/main/res"
UI="$FIXTURE/app/src/main/java/com/kenkern/engine/ui"
mkdir -p "$RES/values" "$RES/values-pt" "$UI"

cat > "$UI/Screen.kt" <<'EOF'
fun Sample() {
    Text(stringResource(R.string.app_name))
}
EOF
cat > "$RES/values/strings.xml" <<'EOF'
<resources>
    <string name="app_name">Example</string>
    <plurals name="editor_items">
        <item quantity="one">%d item</item>
        <item quantity="other">%d items</item>
    </plurals>
</resources>
EOF
cat > "$RES/values/hub.xml" <<'EOF'
<resources>
    <string name="hub_title">Project Hub</string>
    <string-array name="hub_actions">
        <item>Model</item>
        <item>Browse</item>
    </string-array>
</resources>
EOF
cat > "$RES/values-pt/strings.xml" <<'EOF'
<resources>
    <string name="app_name">Exemplo</string>
    <plurals name="editor_items">
        <item quantity="one">%d item</item>
        <item quantity="other">%d itens</item>
    </plurals>
</resources>
EOF
cat > "$RES/values-pt/hub_browser.xml" <<'EOF'
<resources>
    <string name="hub_title">Hub de projetos</string>
    <string-array name="hub_actions">
        <item>Modelar</item>
        <item>Navegar</item>
    </string-array>
</resources>
EOF

# Styles/themes are deliberately different and are not localized string keys.
cat > "$RES/values/themes.xml" <<'EOF'
<resources><style name="Theme.EnglishOnly"><item name="android:windowNoTitle">true</item></style></resources>
EOF
cat > "$RES/values-pt/themes.xml" <<'EOF'
<resources><style name="Tema.ApenasPortugues"><item name="android:windowNoTitle">true</item></style></resources>
EOF

# Two simultaneous runs must not share fixed temporary key files.
python3 - "$CHECK_SCRIPT" "$FIXTURE" <<'PY'
import os
import subprocess
import sys

env = os.environ.copy()
env["KENKERN_ROOT"] = sys.argv[2]
runs = [
    subprocess.Popen(
        ["bash", sys.argv[1]],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        env=env,
    )
    for _ in range(2)
]
for run in runs:
    output, _ = run.communicate()
    if run.returncode != 0 or "4 recursos string/string-array/plurals" not in output:
        raise SystemExit(f"falha na execução paralela:\n{output}")
print("OK: parity en/pt em múltiplos XMLs; themes ignorados; 2 execuções paralelas.")
PY

# Missing translated keys and extra Portuguese-only keys must both fail.
cat > "$RES/values-pt/hub_browser.xml" <<'EOF'
<resources>
    <string name="legacy_only">Legado</string>
</resources>
EOF
if KENKERN_ROOT="$FIXTURE" bash "$CHECK_SCRIPT" > "$TEMP_ROOT/mismatch.log" 2>&1; then
    echo "ERRO teste: paridade deveria falhar para chave ausente/sobrando."
    exit 1
fi
grep -q 'string/hub_title' "$TEMP_ROOT/mismatch.log"
grep -q 'string-array/hub_actions' "$TEMP_ROOT/mismatch.log"
grep -q 'string/legacy_only' "$TEMP_ROOT/mismatch.log"
echo "OK: paridade acusa recursos ausentes e extras."
