# Session baselines (Capa C)

JSON normalizados de sesión (`SessionExporter`) de la **última versión del SDK aceptada como
referencia**. Son la línea base contra la que el pipeline compara las capturas de una versión nueva.

- **Quién los compara:** `scripts/diff-sessions.cjs <este-dir> ai-output/report/sessions` →
  `ai-output/session-diff.md` (Δ estructural + métricas).
- **Quién los interpreta:** el agente `/version-comparator` → `ai-output/version-comparison-report.md`
  (clasifica cada Δ: ESPERADO / REGRESIÓN / RUIDO-DE-RED / HALLAZGO / DATO-INCOMPLETO).

## Cómo se actualiza (promoción de baseline)
NO se auto-promueve. Cuando un PR del pipeline (`auto/changelog-<ver>`) sale **sin regresiones**
(o con Δ explicables por el changelog) y se aprueba, **se reemplazan estos JSON por las capturas de
esa versión nueva** (`ai-output/report/sessions/*.json`) en el mismo PR o en uno de seguimiento. Así
el baseline avanza versión a versión y siempre es un set completo y limpio.

> Baseline semilla actual: **SDK 10.0.7** (7 escenarios). Las métricas numéricas (ttffMs, rebufferMs)
> varían por red — el comparador las marca con tolerancia; lo que diffea limpio es lo estructural
> (orden de callbacks, threading, formato, resolución).
