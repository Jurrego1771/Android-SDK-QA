# qa-knowledge — Convenciones de estructura

> Fuente **única** de verdad del conocimiento por-feature del SDK (riesgos, tests, historias,
> defectos, reglas). Todo lo demás (`docs/ai-context/`, índices, bundles de contexto de agentes)
> es una **proyección generada** de aquí, nunca una copia editada a mano.
>
> Regla de oro: **un hecho, una casa.** Si el dato ya existe en el código o en el `.aar`, aquí se
> *referencia* o se *deriva* — no se copia. (La deriva de "v11" nació de violar esto.)

---

## 1. Slugs de carpeta — estables, sin números

Cada feature vive en `qa-knowledge/<slug>/`. El `<slug>` es **kebab-case semántico y estable**:
`vod`, `drm`, `ads-ima`, `pip`, `reels`. 

**Prohibido el prefijo numérico** (`04-video-vod`). El orden y la agrupación viven en
`INDEX.yaml` (`order:`, `group:`), no en el nombre de la carpeta — así insertar una feature no
obliga a renumerar nada y los links no se rompen.

## 2. Prefijo de ID — uno por feature, ya existente

Cada feature tiene un **prefijo de ID en MAYÚSCULAS, único** (ya establecido en los YAML actuales):

| slug | id_prefix | · | slug | id_prefix |
|---|---|---|---|---|
| vod | `VOD` | · | chromecast | `CC` |
| live | `LIVE` | · | pip | `PIP` |
| episode | `EP` | · | android-tv | `ATV` |
| drm | `DRM` | · | android-auto | `AUTO` |
| dvr | `DVR` | · | subtitles | `SUB` |
| audio | `AUD` | · | download | `DL` |
| ads-ima | `ADS` | · | notifications | `NOTIF` |
| initialization | `INIT` | · | ui-customization | `UI` |
| player-config | `CONFIG` | · | services | `SVC` |
| callbacks | `CB` | · | reels | `REELS` |
| analytics-comscore | `COM` | · | core-player | `CORE` |
| analytics-youbora | `YB` | · | pip-v10-upcoming | `V10PIP` |

Todos los IDs siguen `<PREFIX>-<TIPO>-<NNN>` con `TIPO ∈ {US, AC, TC, DEF, RISK}`:
`VOD-TC-001`, `DRM-RISK-003`, `PIP-DEF-001`. **Los IDs nunca se reusan ni se renumeran** (son la
ancla de los cross-links; renumerar rompe el grafo).

## 3. Schema mínimo — 4 archivos por feature (3 curados + 1 derivado)

Schema recortado al **set esencial no redundante**: cada archivo responde **una** de las cuatro
preguntas ortogonales que un agente necesita para testear con calidad. Sin solापe.

```
<slug>/
  rules.md       # ¿Qué es correcto aquí?      (curado) — éxito/fallo por comportamiento
  risks.yaml     # ¿Dónde el peligro/prioridad? (curado) — <PREFIX>-RISK-NNN, test_priority, → defect_ref
  defects.yaml   # ¿Qué ya está roto?           (curado) — <PREFIX>-DEF-NNN, base de los @Ignore
  tests.yaml     # ¿Qué tests hay / faltan?     (DERIVADO) — <PREFIX>-TC-NNN + gaps computados
```

`rules.md` lleva un encabezado de 3 líneas (propósito/alcance) en vez de un `overview.md` aparte.
Las reglas pueden anclar IDs `<PREFIX>-AC-NNN` para que los tests las referencien (`ac_ref`).

### Qué se plegó (y dónde) — NO recrear como archivos sueltos
| Antiguo | Destino |
|---|---|
| `overview.md` | encabezado de `rules.md` + campo `summary` en INDEX |
| `acceptance.yaml` | criterios dentro de `rules.md` (IDs `-AC-`) |
| `user-stories.yaml` | lo testeable se absorbe en `rules.md`; el framing de rol se descarta |
| `dependencies.yaml` | derivable de `build.gradle`/código; lo relevante a riesgo → `risks.yaml` |
| `learnings.yaml` (per-feature) | rutear: comportamiento→`rules.md`, bug→`defects.yaml`, gotcha de infra→**un** learnings global (o CLAUDE.md) |

## 4. Derivado vs curado

| Familia | Archivos | Fuente real | Mantenimiento |
|---|---|---|---|
| **Derivable** | `tests.yaml` (TC reales + gaps), los `@Ignore` de `defects.yaml` | los `@Test` reales, `build.gradle.kts` | **autogenerar/verificar desde el código — nunca a mano**. Gaps = riesgo `test_priority: MUST` sin TC que lo cubra. |
| **Curado** | `rules.md`, `risks.yaml`, `defects.yaml` | juicio del QA | escribir a mano (aquí está el valor) |

## 5. Cross-links — verificables por máquina

Los `ac_ref`, `defect_ref`, `test_priority`, y `file:"X.kt:NN"` son **contratos**, no texto libre.
El linter de knowledge (pendiente, `scripts/lint-knowledge.*`) debe fallar si:
- un `file:"X.kt:NN"` no existe o el método ya no está ahí,
- un `*_ref` apunta a un ID inexistente,
- un test con `status: ignored` ya no tiene `@Ignore` en el código (o viceversa),
- se cita una versión del SDK distinta a la de `app/build.gradle.kts` (única casa de la versión).

## 6. El índice manda

`INDEX.yaml` es la **puerta única** de los agentes: feature → slug, qué cubre, deeplink, conteos,
estado de migración. Es **generado** (no se edita a mano) y reemplaza la tabla de `docs/README.md`
y `docs/ai-context/feature-test-matrix.md`, que quedan deprecados.
