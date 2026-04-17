#!/bin/bash
# generate-diff.sh — Genera el diff de la rama actual contra la rama base
#
# Uso:
#   ./scripts/generate-diff.sh                   # diff contra main
#   ./scripts/generate-diff.sh develop            # diff contra develop
#   ./scripts/generate-diff.sh main feature/xyz   # diff entre dos ramas específicas
#
# Output: ai-output/diff.txt + ai-output/diff-meta.txt

set -e

BASE_BRANCH="${1:-main}"
COMPARE_BRANCH="${2:-HEAD}"
OUTPUT_DIR="ai-output"
DIFF_FILE="$OUTPUT_DIR/diff.txt"
META_FILE="$OUTPUT_DIR/diff-meta.txt"

mkdir -p "$OUTPUT_DIR"

echo "Generando diff: $BASE_BRANCH...$COMPARE_BRANCH"

# Metadata del diff
cat > "$META_FILE" <<EOF
fecha: $(date -u +"%Y-%m-%dT%H:%M:%SZ")
base_branch: $BASE_BRANCH
compare_branch: $COMPARE_BRANCH
repo: $(git remote get-url origin 2>/dev/null || echo "local")
current_commit: $(git rev-parse HEAD)
base_commit: $(git rev-parse "$BASE_BRANCH" 2>/dev/null || echo "unknown")

--- Archivos cambiados ---
$(git diff "$BASE_BRANCH"..."$COMPARE_BRANCH" --name-status 2>/dev/null || git diff --name-status)

--- Commits en la rama ---
$(git log "$BASE_BRANCH".."$COMPARE_BRANCH" --oneline 2>/dev/null || git log --oneline -10)
EOF

# Diff completo (excluye binarios, limit razonable)
git diff "$BASE_BRANCH"..."$COMPARE_BRANCH" \
    --no-color \
    --diff-filter=ACMRT \
    -- '*.kt' '*.java' '*.gradle' '*.gradle.kts' '*.xml' '*.json' '*.md' \
    2>/dev/null > "$DIFF_FILE" || \
git diff --no-color -- '*.kt' '*.java' '*.gradle' '*.gradle.kts' > "$DIFF_FILE"

DIFF_SIZE=$(wc -l < "$DIFF_FILE")
echo "✓ Diff generado: $DIFF_FILE ($DIFF_SIZE líneas)"
echo "✓ Metadata: $META_FILE"
echo ""
echo "Siguiente paso: ejecutar /diff-analyzer en Claude Code"
