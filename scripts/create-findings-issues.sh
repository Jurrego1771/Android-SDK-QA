#!/usr/bin/env bash
# ============================================================================
# create-findings-issues.sh — Convierte los hallazgos de la exploración en GitHub issues.
#
# Lee ai-output/findings.json (lo produce el agente `explorer`: bugs del SDK + oportunidades de
# mejora observadas en device) y abre un issue por hallazgo con `gh issue create`, con la evidencia
# (escenario, logcat, session-state, recomendación). IDEMPOTENTE: no duplica si ya existe un issue
# abierto con el mismo título.
#
# Labels: `sdk-bug` (type=sdk-bug) · `improvement` (type=improvement). Se crean si no existen.
#
# Uso:  create-findings-issues.sh [findings.json] [--dry-run]
# Env:  GH_REPO (opcional, default = repo del cwd). gh debe estar autenticado.
# Exit: 0 ok (incl. sin hallazgos) · 1 error de gh.
# ============================================================================
set -uo pipefail

FINDINGS="${1:-ai-output/findings.json}"
DRY_RUN=0; [[ "${1:-}" == "--dry-run" || "${2:-}" == "--dry-run" ]] && DRY_RUN=1
[[ "$FINDINGS" == "--dry-run" ]] && FINDINGS="ai-output/findings.json"

[[ -f "$FINDINGS" ]] || { echo "findings: no existe $FINDINGS — nada que crear"; exit 0; }
command -v gh >/dev/null || { echo "✗ gh no disponible" >&2; exit 1; }

# Nº de hallazgos
COUNT=$(node -e 'try{const d=JSON.parse(require("fs").readFileSync(process.argv[1],"utf8"));console.log((d.findings||[]).length)}catch{console.log(0)}' "$FINDINGS")
[[ "$COUNT" -eq 0 ]] && { echo "findings: 0 hallazgos — nada que crear"; exit 0; }
echo "findings: ${COUNT} hallazgo(s)$([[ $DRY_RUN -eq 1 ]] && echo ' (DRY-RUN)')…"

# Asegurar labels (best-effort; no falla si ya existen)
if [[ $DRY_RUN -eq 0 ]]; then
  gh label create sdk-bug    --color d73a4a --description "Bug del SDK hallado en exploración" 2>/dev/null || true
  gh label create improvement --color a2eeef --description "Oportunidad de mejora hallada en exploración" 2>/dev/null || true
fi

# Títulos de issues ABIERTOS (para idempotencia)
EXISTING="$(gh issue list --state open --limit 200 --json title --jq '.[].title' 2>/dev/null || echo "")"

# Emitir cada hallazgo como TITLE\x1fBODY\x1fLABEL (separadores no imprimibles para evitar choques)
node -e '
  const d=JSON.parse(require("fs").readFileSync(process.argv[1],"utf8"));
  const tag = process.argv[2]; // prefijo de título
  for (const f of (d.findings||[])) {
    const label = f.type==="improvement" ? "improvement" : "sdk-bug";
    const title = `[${label==="improvement"?"mejora":"bug"}] ${f.title||"(sin título)"}`;
    const ev = f.evidence||{};
    const body = [
      `**Severidad:** ${f.severity||"?"}  ·  **Escenario:** \`${f.scenario||"?"}\``,
      ``,
      `### Observado`, f.summary||"(sin descripción)",
      ``,
      `### Evidencia`,
      ev.session_state?`- session_state: \`${ev.session_state}\``:"",
      ev.logcat?"- logcat:\n```\n"+ev.logcat+"\n```":"",
      ev.screenshot?`- screenshot: ${ev.screenshot}`:"",
      ``,
      `### Recomendación`, f.recommendation||"(ninguna)",
      ``,
      `---`,
      `_Hallado automáticamente por el agente \`explorer\` durante la exploración en device (${tag})._`,
    ].filter(x=>x!==null).join("\n");
    process.stdout.write(title+"\x1f"+body+"\x1f"+label+"\x1e");
  }
' "$FINDINGS" "QA $(date -u +%Y-%m-%d)" | while IFS=$'\x1f' read -r -d $'\x1e' title body label; do
  if grep -qxF "$title" <<< "$EXISTING"; then
    echo "  ↺ ya existe (omito): $title"
    continue
  fi
  if [[ $DRY_RUN -eq 1 ]]; then
    echo "  + [DRY] crearía: $title  [label: $label]"
  else
    url=$(gh issue create --title "$title" --body "$body" --label "$label" 2>/dev/null) \
      && echo "  ✓ creado: $url" \
      || echo "  ✗ falló al crear: $title"
  fi
done

echo "findings: listo."
