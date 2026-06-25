# Estrategia de gatillado de QA por tipo de cambio

> Cómo el workflow decide **qué probar** según el tipo de cambio del SDK (feature / fix / versión),
> de dónde saca el **binario** a probar, y cómo selecciona el **alcance de tests** usando el grafo
> de conocimiento (`qa-knowledge/INDEX.yaml` + `kb-resolve`).
>
> Estado: **diseño aprobado** (2026-06-25). Implementación por fases (ver §6).

---

## 1. Principio

La **rama del cambio** del SDK es la fuente de verdad. El workflow ya no mira una rama fija
(`SDK_BRANCH=10.0.8`): **descubre la rama del cambio, la clasifica de forma autónoma, y ramifica
el flujo**.

> **Decisión de diseño (camino B, 2026-06-25): clasificación autónoma, sin depender del SDK team.**
> Las ramas de versión SÍ traen `CHANGELOG.md` (formato `### New Features`/`### Bug Fixes`), pero
> las ramas de **trabajo (fix/feature) NO lo mantienen** (verificado: `bug/adControls` no tiene
> changelog). Por eso el clasificador NO depende del changelog: usa **nombre de rama** (tipo) +
> **`git diff <rama>..master`** (qué archivos tocó → features vía INDEX). El changelog, *si existe*,
> solo **enriquece** el contexto del agente — nunca es requisito. Así no se le impone proceso nuevo
> al equipo SDK y el flujo no se bloquea por disciplina ajena.

## 2. Descubrimiento de la rama  (decisión: input manual + cron de versiones)

| Disparador | Para qué | Cómo |
|---|---|---|
| `workflow_dispatch` con input `sdk_branch` | feature/fix a demanda | el QA/SDK team indica la rama (ej. `SGAI`, `bug/adControls`) |
| cron (existente) | ramas de **versión** | `watch-sdk.sh` sigue vigilando la línea de versión en Maven, automático |

> El cron NO descubre ramas de feature/fix solo (evita disparar QA sobre trabajo que el team no
> quería probar aún). Esas van por input explícito.

## 3. Clasificación autónoma (camino B)

Tres señales, en orden de fiabilidad. Ninguna requiere acción del equipo SDK.

**a) Tipo ← nombre de rama** (siempre disponible):

| Patrón de rama | `change_type` |
|---|---|
| `bug/*`, `bugfix/*`, `*-fix`, `*fix*` | `FIX` |
| `NN.NN.NN` (semver puro, sin sufijo) | `RELEASE` |
| `NN.NN.NN-alphaNN` | `VERSION` (en progreso) |
| otro (`SGAI`, `cast`, `PlayerNotification`, `android-auto-v2`) | `FEATURE` |
| `develop*`, `dev-*`, `master`, `HEAD` | `IGNORE` |

**b) Features tocadas ← `git diff <rama>..master`** (siempre disponible): los archivos del SDK
modificados se mapean a slugs del INDEX. El mapeo se deriva de los `affected_files` ya presentes en
`qa-knowledge/<slug>/risks.yaml` y `defects.yaml` (que apuntan a clases como `MediastreamPlayer.kt`,
`MediastreamPlayerConfig.kt`). Si un archivo no mapea a ninguna feature → fallback a smoke + revisión.

**c) Enriquecimiento ← `CHANGELOG.md@<rama>`** (opcional): si la rama trae changelog, su sección se
pasa al agente como contexto del *intent*. Si no existe (caso típico en fix/feature), se omite sin error.

Salida del clasificador (contrato nuevo, p.ej. `ai-output/change-meta.txt`):
```
change_type=FIX
sdk_branch=bug/adControls
features=ads-ima          # slugs del INDEX, vía git diff → affected_files
binary_source=local-build # ver §5
changelog=absent          # present|absent (solo enriquece)
```

> **Pieza nueva a construir:** un índice inverso `affected_file → slug` derivado de los YAML de
> `qa-knowledge` (extensión natural de `build-knowledge-index.cjs`). Es lo que traduce el git diff
> a features. Mientras no exista, fix/feature caen a smoke + revisión manual de alcance.

## 4. Los tres flujos  (alcance de tests vía INDEX + kb-resolve)

```
descubrir rama → clasificar → change_type + features[]
  ├─ FEATURE  → curar rules.md + risks.yaml (US/AC)
  │            → ADAPTACIÓN DE API (compile-gate): bump → compilar androidTest
  │                 · si falla por la interfaz (callbacks nuevos / firmas cambiadas):
  │                   un agente agrega los métodos faltantes a TODAS las clases que implementan
  │                   MediastreamPlayerCallback (stubs que loguean vía CallbackCaptor) y crea las
  │                   Activities/escenarios de la feature nueva (reusa /activity-creator)
  │                 · recompila hasta que el APK de test buildea
  │            → /test-generator (tests nuevos contra los AC) → run: nuevos + REGRESIÓN acoplada + smoke
  ├─ FIX      → kb-resolve(features) → recopilar tests existentes de esa(s) feature(s)
  │            → si el bug NO está cubierto: generar 1 test de regresión
  │            → run: --class/--package dirigido a esas features + smoke
  └─ RELEASE  → run: SUITE COMPLETA (--size all, todos los targets)
```

- **FIX scope (decidido):** feature afectada vía `kb-resolve` + smoke. Dirigido con `--class`/
  `--package` que `run-tests.sh` ya soporta. No corre la suite completa.
- **FEATURE:** primero se curan los 3 archivos del schema mínimo (`rules.md`, `risks.yaml`,
  `defects.yaml`) de la feature nueva — historias/criterios/aceptación viven en `rules.md` (anclas
  `<PREFIX>-AC-NNN`); luego `/test-generator` produce los tests contra esos criterios.
- **RELEASE:** validación amplia, sin generación nueva.

### Adaptación de API (compile-gate) — pieza nueva del flujo FEATURE
Un cambio de SDK puede **romper la compilación del QA** sin ser un fallo: p.ej. `feature/vertical`
(11.0.0-alpha01) agrega `onSwipeToItem`, `onEndReached`, `onEpisodeInfoClick` a la interfaz
`MediastreamPlayerCallback` (sin defaults → todas las clases que la implementan dejan de compilar).
Hoy el pipeline bumpea y corre; si no compila, **falla sin diagnóstico útil**. Debe, en cambio:

1. **Detectar** — el `changelog-analyzer` marca "callbacks/firmas nuevas en la interfaz" como señal
   de adaptación; y un **compile-gate** (`./gradlew :app:compileDebugAndroidTestKotlin`) captura los
   errores reales tras el bump.
2. **Adaptar** (agente, sobre los errores del compilador):
   - agrega los métodos faltantes (stubs con logging vía `CallbackCaptor`) a **todas** las clases que
     implementan `MediastreamPlayerCallback` — Activities de escenario, helpers de test;
   - crea las Activities/escenarios de la feature nueva con **`/activity-creator`** (ya existe) +
     su deeplink en `DeepLinkRouterActivity`.
3. **Recompilar** hasta que el APK de test buildea; recién entonces correr tests.
4. El resultado (qué se agregó, qué rompió) va al PR — **requiere revisión humana** antes de merge.

> Esto es lo que convierte un "feature nueva del SDK" en cobertura real en vez de un build roto.
> El breaking change de la interfaz es además un **hallazgo** a documentar en `qa-knowledge` (riesgo
> de compatibilidad para integradores).

## 5. Origen del binario  (decisión: build local del SDK → mavenLocal)

| `change_type` | Origen del `.aar` |
|---|---|
| `RELEASE` / `VERSION` | Maven (flujo actual: `resolve-sdk-version.sh` + bump en `build.gradle.kts`) |
| `FEATURE` / `FIX` | **build local**: checkout de `<sdk_branch>` en el SDK fuente del runner → `publishToMavenLocal` → el QA app consume ese artefacto |

Para feature/fix el QA app debe resolver desde `mavenLocal()` la versión que publique la rama
(la rama declara su `version` en `mediastreamplatformsdkandroid/build.gradle.kts` — ej. `10.0.7`).
Requisitos: repo SDK clonado en el runner (ya está en `D:\repos\mediastream\…`), y `mavenLocal()`
en los repos de `settings.gradle.kts` del QA app durante esas corridas.

> Implicación de la regla R5 del linter (versión única): en corridas de feature/fix el `build.gradle`
> del QA apuntará a la versión que declare la rama del SDK, no a un alpha de Maven. El linter sigue
> validando contra `build.gradle` — coherente.

## 6. Slack + GitHub Pages  (link del informe)

**Problema actual:** `notify-slack.sh` tiene el botón "Ver detalles →" pero `run-tests.sh:708` le
pasa `RUN_URL=""`. Y no existe workflow de Pages — el `report.sh` genera `ai-output/report/index.html`
pero solo se sube como *artifact*.

**Fix (implementado, Fase 1):** GitHub Pages en **URL fija, force-orphan** — NO subcarpeta por run.
Decisión revisada (2026-06-25): acumular por run inflaba el repo sin tope (videos en el historial de
git, irreversible). En su lugar:
1. `scripts/publish-report-pages.sh` publica `ai-output/report/` a `gh-pages` con una **rama huérfana
   fresca** cada vez (1 commit, force-push) → crecimiento **cero**; Pages muestra siempre el ÚLTIMO
   run en `https://<owner>.github.io/<repo>/`. Históricos → artifacts de Actions (retención 30 días).
2. Los 3 workflows de device exportan `REPORT_URL` (fija) → `run-tests.sh` la pasa al botón de Slack
   (4º arg de `notify-slack.sh`, antes `""`), y publican el reporte con un step posterior (`if: always`).
3. **Prerrequisito (manual, una vez):** Settings → Pages → Source = rama `gh-pages`. Repo público
   confirmado → Pages sin plan especial. La rama `gh-pages` se crea en el primer publish.

## 7. Fases de implementación

> **Estado: implementado.** El proceso vive en el núcleo `scripts/qa-core.sh` con 3 adaptadores de
> entrada (versión / changelog local / issue). Detalle del set de agentes y el contrato: `docs/agents.md`.

1. **Slack/Pages** ✅ — `publish-report-pages.sh` + `RUN_URL` en el botón de Slack.
2. **Índice inverso `affected_file → slug`** ✅ — `build-knowledge-index.cjs` emite
   `qa-knowledge/affected-files.json`; lo usa `change-analyzer` para el blast radius (mejora al migrar features).
3. **Clasificación del cambio** ✅ — los adaptadores infieren `change_type` (nombre de rama + semver) y
   lo dejan en `source-meta.txt`; `change-analyzer` calcula directas + acopladas.
4. **Router / selección de regresión** ✅ — `test-strategist` emite `regression-set.txt` (smoke + tocada
   + acopladas; `all` si RELEASE); `qa-core.sh` ejecuta 2 fases + `retry-failed-tests.sh`.
5. **Build local → mavenLocal** ✅ — `build-sdk-local.sh` (assembleRelease + install manual del AAR/POM
   a `~/.m2`, porque el `publishToMavenLocal` del SDK falla por doble sources jar).
6. **workflow_dispatch con `sdk_branch`** + cron de versiones ✅.
7. **Adaptación de API (compile-gate + activity-creator)** ✅ — `qa-core.sh` corre el compile-gate tras
   el bump; si el strategist pide escenarios (`scenarios-to-create.txt`), `activity-creator` los crea
   (con auto-fix), recompila, instala, y el `explorer` los observa.
8. **Shift-left por issue** ✅ — `ingest-issue.sh "<desc>"|<#N>` → núcleo (prueba la versión actual).
9. **Issues de hallazgos** ✅ — `explorer` → `findings.json` → `create-findings-issues.sh` (bugs+mejoras).

Fase 1 no depende de las demás. Las fases 2–4 dependen del grafo de conocimiento (INDEX + kb-resolve),
ya en su sitio; la fase 2 mejora a medida que se migran features. La fase 7 es la que cierra el flujo
FEATURE end-to-end (caso real que lo motiva: `feature/vertical` rompe la compilación del QA).
