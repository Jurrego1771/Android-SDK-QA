---
name: i18n-schema
description: Esquema exacto de los 9 archivos qa-knowledge (campos y anidamiento) a replicar por módulo
metadata:
  type: reference
---
Plantilla: `D:\repos\jurrego1771\lightning-player-qa\qa-knowledge\modules\i18n\`. Todos los YAML empiezan con `version: "1.0"`, `module: {slug}`, `last_updated: "YYYY-MM-DD"`.

- **overview.md** — Markdown narrativo: Qué hace, Archivos clave (tabla), API pública, Flujo de datos (bloque code), Comportamiento, Interacciones con otros sistemas.
- **business-rules.md** — Markdown. Reglas `BR-{MODULE}-00x` en negrita seguidas de descripción. Agrupadas por secciones (##).
- **acceptance.yaml** — `acceptance_criteria:` lista. Cada item: `id` ({MODULE}-AC-00x), `priority` (MUST/SHOULD/COULD), `scenario`, `given`, `when`, `then` (lista). Agrupados por comentarios MUST/SHOULD/COULD.
- **user-stories.yaml** — `user_stories:` lista. Cada item: `id` (US-{MODULE}-00x), `persona`, `narrative` {as, want, so_that}, `business_value` (HIGH/MEDIUM/LOW), `risk_area` (lista), `related_features` (lista), `acceptance` (lista de AC ids), `notes`.
- **tests.yaml** — `existing_tests:` (puede ser []) + `coverage_gaps:` lista. Gap: `id` (GAP-{MODULE}-00x), `priority`, `ac_ref` (id o lista), opcional `risk_ref`/`defect_ref`, `title`, `suggested_spec`, `notes`. (Para core-player añadí campos a existing_tests: name, file, type, ac_ref, status, defect_ref, notes — extensión válida.)
- **defects.yaml** — `defects:` lista. Item: `id` ({MODULE}-DEF-00x), `status` (open/known_limitation), `severity` (low/medium/high), `title`, `description`, `affected_files` (lista), `trigger`, `workaround`, `regression_risk`, `related_ac`. (Añadí `source` en core-player.)
- **risks.yaml** — `risks:` lista. Item: `id` ({MODULE}-RISK-00x), `severity`, `title`, `description`, `affected_files` (lista), `trigger`, `mitigation`, `test_priority` (MUST/SHOULD/COULD).
- **dependencies.yaml** — `internal:` y `external:` listas. internal: `id`, `reason`, `coupling` (high/medium/low), `files` (lista). external: `id`, `package`, `version` (o `function`), `reason`, `risk`, opcional `note`.
- **learnings.yaml** — `learnings:` lista. Item: `id` ({MODULE}-LEARN-00x), `title`, `discovery` (file:line), `detail`, `impact_on_tests`.
