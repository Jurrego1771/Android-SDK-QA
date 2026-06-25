# SDK Android QA — Documentación

Repositorio de QA caja-negra del **Mediastream Platform SDK Android**.
La versión exacta bajo test la fija `app/build.gradle.kts` (no se duplica aquí — ver
`qa-knowledge/CONVENTIONS.md §1`).

---

## Punto de entrada del conocimiento

El catálogo por-feature (qué cubre cada una, tests, riesgos, defectos, deeplinks) ya **no** vive en
una tabla a mano en este README — derivaba y mentía. La puerta única es:

- **`qa-knowledge/INDEX.yaml`** — índice generado (feature → slug, cobertura, deeplinks).
  Regenerar: `node scripts/build-knowledge-index.cjs`.
- **`qa-knowledge/CONVENTIONS.md`** — el schema (3 curados + tests derivado) y las reglas.
- Resolver una query como lo hace un agente: `node scripts/kb-resolve.cjs <feature|deeplink|keyword>`.
- Validar consistencia (cross-links, `file:line`, versión del SDK): `node scripts/lint-knowledge.cjs`.

> El conocimiento por-feature está migrando de `docs/features/<NN-...>/` (legado, schema de 6)
> hacia `qa-knowledge/<slug>/` (casa única, schema mínimo de 4). El `INDEX.yaml` lleva el avance
> en el campo `migrated:` de cada feature.

---

## Contexto que leen los agentes

- [`ai-context/sdk-api-contract.md`](ai-context/sdk-api-contract.md) — firmas reales de la API (línea 10.0.x)
- [`ai-context/business-rules.md`](ai-context/business-rules.md) — qué es éxito/fallo
- [`ai-context/test-patterns.md`](ai-context/test-patterns.md) — patrones y templates de test

## Testing

- [Estrategia de Testing](testing/test-strategy.md) · [Guía de Fixtures](testing/fixtures-guide.md)
- [Workflow con IA](testing/ai-workflow.md) · [QA autónomo](testing/sdk-qa-autonomous.md)
- [Bitácora de sesiones](testing/SESSION_LOG.md)

## Risk Map

- [RISK_MAP.md](../risk-map/RISK_MAP.md) · [COVERAGE_TRACKER.md](../risk-map/COVERAGE_TRACKER.md)
