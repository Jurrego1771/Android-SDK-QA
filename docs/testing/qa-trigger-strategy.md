# Estrategia de gatillado de QA por tipo de cambio

> Cómo el workflow decide **qué probar** según el tipo de cambio del SDK (feature / fix / versión),
> de dónde saca el **binario** a probar, y cómo selecciona el **alcance de tests** usando el grafo
> de conocimiento (`qa-knowledge/INDEX.yaml` + `kb-resolve`).
>
> Estado: **diseño aprobado** (2026-06-25). Implementación por fases (ver §6).

---

## 1. Principio

El CHANGELOG del SDK vive **en la rama del cambio**, no en `master`. La rama —su **nombre** y su
**changelog**— es la fuente de verdad del tipo de cambio y de qué features toca. El workflow ya no
mira una rama fija (`SDK_BRANCH=10.0.8`): **descubre la rama del cambio, la clasifica, y ramifica
el flujo**.

## 2. Descubrimiento de la rama  (decisión: input manual + cron de versiones)

| Disparador | Para qué | Cómo |
|---|---|---|
| `workflow_dispatch` con input `sdk_branch` | feature/fix a demanda | el QA/SDK team indica la rama (ej. `SGAI`, `bug/adControls`) |
| cron (existente) | ramas de **versión** | `watch-sdk.sh` sigue vigilando la línea de versión en Maven, automático |

> El cron NO descubre ramas de feature/fix solo (evita disparar QA sobre trabajo que el team no
> quería probar aún). Esas van por input explícito.

## 3. Clasificación  (nombre de rama + changelog de la rama)

El nombre da el tipo tentativo; el changelog de la rama (`gh api .../CHANGELOG.md?ref=<rama>`, ya
implementado en `fetch-sdk-changelog.sh`) lo confirma y lista las features tocadas.

| Patrón de rama | `change_type` | Confirmación en changelog |
|---|---|---|
| `bug/*`, `bugfix/*`, `*-fix`, `*fix*` | `FIX` | sección "Bug Fixes" |
| `NN.NN.NN` (semver puro, sin sufijo) | `RELEASE` | tag estable + artefacto en Maven |
| `NN.NN.NN-alphaNN` | `VERSION` (en progreso) | alpha en Maven |
| otro (`SGAI`, `cast`, `PlayerNotification`, `android-auto-v2`) | `FEATURE` | sección "New Features" |
| `develop*`, `dev-*`, `master`, `HEAD` | `IGNORE` | — |

Salida del clasificador (contrato nuevo, p.ej. `ai-output/change-meta.txt`):
```
change_type=FIX
sdk_branch=bug/adControls
features=ads-ima          # slugs del INDEX (vía mapeo del changelog → kb-resolve)
binary_source=local-build # ver §5
```

## 4. Los tres flujos  (alcance de tests vía INDEX + kb-resolve)

```
descubrir rama → clasificar → change_type + features[]
  ├─ FEATURE  → curar rules.md + risks.yaml (US/AC) → /test-generator (tests nuevos)
  │            → run: tests nuevos + REGRESIÓN de features acopladas (INDEX merge/coupling) + smoke
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

**Fix (decidido: GitHub Pages por run):**
1. Workflow `deploy-pages` que publica `ai-output/report/` en `/runs/<run_number>/` → URL navegable.
2. `run-tests.sh`: pasar esa URL como `RUN_URL` (4º arg de `notify-slack.sh`).
3. **Prerrequisito:** habilitar GitHub Pages en el repo. Si el repo es privado, Pages requiere plan
   GitHub Team/Enterprise — confirmar antes de implementar; si no, fallback al link del run de Actions.

## 7. Fases de implementación

1. **Slack/Pages** (independiente, valor inmediato): deploy-pages + pasar `RUN_URL`. Desbloquea el link.
2. **Clasificador de rama**: `classify-branch.sh` (nombre + changelog) → `change-meta.txt`. Extiende
   `fetch-sdk-changelog.sh` para aceptar cualquier `sdk_branch`.
3. **Router en el orquestador**: ramificar `watch-sdk.sh` por `change_type` (alcance de `run-tests.sh`).
4. **Build local → mavenLocal** para feature/fix.
5. **workflow_dispatch con `sdk_branch`** + cron de versiones (descubrimiento).

Fase 1 no depende de las demás. Las fases 2–3 dependen del grafo de conocimiento (INDEX + kb-resolve),
ya en su sitio.
