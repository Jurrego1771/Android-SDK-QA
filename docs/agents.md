# Agentes de QA — schema, set y proceso (fuente de verdad)

> Este documento es la **única fuente de verdad** del sistema de agentes de QA. Todo agente en
> `.claude/commands/*.md` y `.claude/agents/*.md` sigue el schema de §2. El linter
> (`scripts/lint-knowledge.cjs`) valida que cada agente lo cumpla.
>
> **Principio rector:** los agentes son **QA seniors con superpoderes** — recorren el grafo de
> conocimiento al instante, exploran en device, y recuerdan cada test/AC/riesgo. No son funciones
> tontas: **observan y verifican antes de afirmar**; nunca inventan APIs ni resultados.

---

## 1. El proceso de QA profesional (la columna vertebral)

Ante un cambio (versión nueva del SDK, un diff de PR, o un issue), el sistema sigue el proceso
estándar de la industria: **risk-based testing + Regression Test Selection (RTS) + exploratory-first**.
No se corre "la batería completa y se espera" — se **selecciona** la regresión por impacto.

| # | Etapa | Agente (rol QA real) | Produce |
|---|---|---|---|
| 1 | **Análisis de impacto** | `change-analyzer` (QA Lead) | clasifica el cambio; calcula el **blast radius**: features **directas** (git-diff → `affected_file→slug`) + **acopladas** (links de riesgo/dependencia del grafo) → `analysis.md` |
| 2 | **Planificación + selección** | `test-strategist` (QA Lead) | de la matriz de trazabilidad (el grafo): tests existentes del scope = **regresión a correr**; comportamiento nuevo + `coverage_gaps` + ACs = **casos nuevos a diseñar**. SELECCIONA regresión (smoke + tocada + acopladas) → `strategy.md` + `regression-set.txt` (+ `scenarios-to-create.txt` si falta una Activity) |
| 3 | **Exploratorio de lo NUEVO primero** | `explorer` (QA exploratorio) | session-based testing en device de los casos nuevos: observa comportamiento/selectores reales, halla bugs temprano → `exploration.md` + `findings.json` |
| 4 | **Diseño / automatización** | `test-generator` (Automation) | escribe los tests nuevos **desde lo observado** + AC; clasifica `smoke\|integration\|regression`; **auto-verifica** (compila) → tests `.kt` + `generated-tests-report.md` |
| 5 | **Ejecución en 2 fases** | `scripts/run-tests.sh` (Ejecución) | **A) Verificación del cambio**: corre los tests NUEVOS · **B) Regresión**: corre `regression-set`. **Ambas siempre** (aunque A falle). Con **auto-retry** de fallidos (evidencia de flaky) |
| 6 | **Triage + reporte** | `test-analyzer` (QA triage) | clasifica fallos con evidencia (real/flaky/entorno/test-defect) → `test-analysis-report.md`; abre **GH issues** (bugs+mejoras de `findings.json`); Slack con **veredicto del cambio** + **veredicto de regresión** |

> **Batería COMPLETA** (no seleccionada, todos los tests): solo cuando `change_type=RELEASE`.
> **`version-comparator`** corre como paso 6b cuando hay baseline de sesiones (diff entre versiones).

---

## 2. Schema uniforme de agente

Todo agente declara frontmatter y estas secciones, en este orden:

```markdown
---
name: <slug>                 # = nombre del archivo, sin .md
description: <una línea>     # cuándo se usa (lo lee el orquestador/humano)
model: haiku | sonnet | opus # SIEMPRE explícito (ver §4)
---

# <Nombre>
## Rol            — 1-2 líneas: qué hace y qué NO hace.
## Entrada        — "Contexto que DEBES leer, EN ORDEN". Cada path debe EXISTIR (el linter lo valida).
## Proceso        — pasos numerados.
## Salida         — el archivo EXACTO que produce + su plantilla. Un agente = un archivo (sin colisión).
## Reglas         — qué no violar. Incluye SIEMPRE: "no inventes — verifica/observa; reporta solo con evidencia".
## Modo fix       — (solo agentes que se auto-reparan) leer compile-gate*.txt, corregir, RECOMPILAR con gradle.
```

---

## 3. Contrato de archivos (`ai-output/`)

Cada archivo tiene **un único productor** — esto mata las colisiones que tenía el diseño viejo
(`analysis.md` y `strategy.md` los escribían dos agentes cada uno).

| Archivo | Productor único | Consumidores |
|---|---|---|
| `source.md` + `source-meta.txt` | adaptador (`fetch-sdk-changelog.sh` / `ingest-issue.sh`) | change-analyzer |
| `analysis.md` | **change-analyzer** | test-strategist, explorer, version-comparator |
| `strategy.md` + `regression-set.txt` + `scenarios-to-create.txt` | **test-strategist** | explorer, test-generator, activity-creator, qa-core |
| `exploration.md` + `findings.json` | **explorer** | test-generator, create-findings-issues |
| `generated-tests-report.md` + tests `.kt` | **test-generator** | run-tests |
| `compile-gate.txt` / `compile-gate-tests.txt` | qa-core (gradle) | test-strategist, test-generator (modo fix), activity-creator (modo fix) |
| `test-results.json` (+ XML) | run-tests | test-analyzer, report |
| `test-analysis-report.md` | **test-analyzer** | PR, Slack |
| `session-diff.md` | `diff-sessions.cjs` | version-comparator |
| `version-comparison-report.md` | **version-comparator** | PR |

`source-meta.txt` lleva: `source_type=changelog|diff|issue`, `sdk_version`, `sdk_branch`, `change_type=FEATURE|FIX|RELEASE`.

---

## 4. El set (8 agentes)

| Agente | Modelo | Por qué ese modelo |
|---|---|---|
| `change-analyzer` | sonnet | razonamiento de impacto (git-diff + grafo + clasificación) |
| `test-strategist` | sonnet | selección de regresión + diseño de casos (juicio) |
| `explorer` | sonnet | exploración en device + diagnóstico (usa MCP, interpreta comportamiento) |
| `test-generator` | sonnet | genera código correcto + auto-repara |
| `test-analyzer` | sonnet | triage con evidencia (distinguir real/flaky/entorno) |
| `version-comparator` | haiku | clasifica un diff ya computado (tarea acotada) |
| `activity-creator` | sonnet | escribe código Android correcto contra el contrato |
| `sdk-knowledge-builder` | opus | construye documentación estructurada desde el SDK source (alto contexto) |

**Borrados** (fusionados en los de arriba): `changelog-analyzer` + `diff-analyzer` → `change-analyzer`;
`feature-strategist` → `test-strategist`. `changelog-explorer` → `explorer` (generalizado).

---

## 5. Adaptadores de entrada + núcleo compartido

```
ENTRADA            ADAPTADOR                       → contrato único →  NÚCLEO (qa-core.sh)
versión nueva      fetch-sdk-changelog.sh / build-sdk-local.sh   source.md   change-analyzer → test-strategist
diff de PR         fetch-pr-diff.js                              source-meta  → explorer → test-generator
issue / cambio     ingest-issue.sh "<desc|#issue>"               (source_type) → run-tests (2 fases) → test-analyzer
                                                                              → version-comparator → issues + Slack + PR
```

El núcleo (`scripts/qa-core.sh`) es **idéntico** sea cual sea la entrada. `watch-sdk.sh` (versión) e
`ingest-issue.sh` (issue) lo invocan — esto elimina la divergencia que había entre `watch-sdk.sh` y
`run-changelog-pipeline.sh`.

---

## 6. Clasificación de tests (smoke / integration / regression)

Hoy NO hay carpetas `smoke/`/`regression/` — la clasificación es por **anotación**, y así se mantiene
(no se mueven archivos). Convención:

- **smoke** — `@LargeTest` + tag/nombre `Smoke*` (camino feliz mínimo, CDN real). Siempre en regresión.
- **integration** — `@MediumTest` en `integration/` (flujo principal). El grueso.
- **regression** — un test que cubre un `defect_ref` / comportamiento ya validado. Se marca con
  `@MediumTest` y se registra `type: regression` en `tests.yaml` (el grafo es el que clasifica, no la carpeta).
- Device: `@MobileOnly` / `@TVOnly` / `@FireTvOnly` / `@ManualOnly` (ya existen).

El `regression-set.txt` que produce el strategist es una lista de `--class`/`--package` que `run-tests.sh`
ejecuta en la Fase B. El `test-generator` registra el `type` de cada test nuevo en `tests.yaml`.

---

## 7. Reglas transversales (todos los agentes)

1. **No inventes.** Reporta solo con evidencia (compile-gate, logcat, session-state, el grafo). Si no
   podés verificar, dilo — no lo asumas (lección del run #9: un agente honesto vale más que uno que finge).
2. **Versión real = `app/build.gradle.kts`**, no el changelog. (Evita la deriva "v11".)
3. **Auto-verificación**: los agentes que editan código (test-generator, activity-creator) corren
   `./gradlew :app:compileDebugAndroidTestKotlin` tras su fix (tienen permiso en `.claude/settings.json`).
4. **Un agente = un archivo de salida.** No escribir el archivo de otro agente.
5. **Sin rutas absolutas hardcodeadas.** SDK source vía `SDK_LOCAL_PATH`; nada de `D:\repos\...`.
6. **No mergear.** El sistema abre PRs/issues; la decisión final es humana.
