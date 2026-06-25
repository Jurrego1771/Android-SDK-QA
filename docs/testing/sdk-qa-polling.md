# QA automático del SDK por polling (sin tocar el repo del SDK)

Tu PC vigila la rama de release del SDK con `gh` y, cuando avanza (merge o push), corre sola el
pipeline de QA caja-negra por **diff real del código**: analiza qué cambió, lo explora en device con
MCP, genera/ajusta tests, los corre y abre un PR de QA. **No mergea nada** — todo para revisión humana.

Sin PAT cross-repo, sin workflow en el repo del SDK, sin coordinar con su equipo. Solo `gh` y una
tarea programada.

## Cómo funciona

```
Task Scheduler (cada N h) → scripts/poll-sdk.sh
   └─ gh api repos/<SDK>/commits/<rama> → HEAD actual
   └─ ¿avanzó vs .sdk-poll-state?  no → no-op
                                   sí → run-sdk-pipeline.sh (compare prev..nuevo)
                                          └─ fetch-sdk-diff → /diff-analyzer → /test-strategist
                                          └─ /changelog-explorer (MCP+device) → /test-generator
                                          └─ run-tests (device) → /test-analyzer → version-comparator
                                          └─ rama + PR de QA   (gh del runner)
   └─ marca .sdk-poll-state = nuevo sha  (solo si el pipeline terminó ok)
```

## Configuración

| Variable | Default | Qué es |
|---|---|---|
| `SDK_REPO` | `mediastream/MediastreamPlatformSDKAndroid` | repo del SDK |
| `SDK_BRANCH` | `10.0.8` | rama de release a vigilar (cambiar al subir de versión) |

Estado local: `.sdk-poll-state` (gitignored) — el último sha procesado. Idempotente.

## Requisitos
- **`gh` autenticado** como el usuario que corre la tarea (en esta PC: `Neo`). Ya está (scope `repo`).
- **`ANTHROPIC_API_KEY`** en el entorno (para los agentes `claude -p`). Ponerla en `scripts/.env` o
  como variable de usuario.
- **Device conectado** (Motorola/A53) cuando se dispare; si no, el pipeline sale con exit 2 y el
  polling reintenta en la próxima pasada (no marca procesado).

## Programar la tarea (Windows Task Scheduler)

Cada 4 horas, como el usuario `Neo` (para heredar el login de `gh`):

```powershell
schtasks /Create /TN "SDK-QA-Poll" /SC HOURLY /MO 4 /RU Neo /F /TR ^
  "\"C:\Program Files\Git\bin\bash.exe\" -lc \"cd /d/repos/jurrego1771/SDK-Android-Qa && bash scripts/poll-sdk.sh >> sdk-poll.log 2>&1\""
```

- `/MO 4` = cada 4 horas (ajustar). `/SC DAILY /ST 09:00` para una vez al día.
- Ver/forzar: `schtasks /Run /TN "SDK-QA-Poll"` · borrar: `schtasks /Delete /TN "SDK-QA-Poll" /F`.
- El log queda en `sdk-poll.log` (gitignored vía `*.log`? — si no, añadir).

> La primera corrida hace **bootstrap**: guarda el sha actual y NO dispara (para no reprocesar todo
> el historial). A partir de ahí, solo dispara con cambios nuevos.

## Disparo manual (sin esperar el polling)
Validar un cambio puntual a mano:

```bash
# por rango de commits (push/merge):
SDK_EVENT=push SDK_REPO=mediastream/MediastreamPlatformSDKAndroid \
  SDK_BASE_SHA=<prev> SDK_HEAD_SHA=<nuevo> bash scripts/run-sdk-pipeline.sh

# por PR del SDK:
SDK_EVENT=pr SDK_REPO=mediastream/MediastreamPlatformSDKAndroid \
  SDK_PR_NUMBER=<n> bash scripts/run-sdk-pipeline.sh
```

## Nota de alcance
El polling vigila el **HEAD de la rama release** → detecta lo que **entra** a esa rama (merges y
pushes). No valida PRs aún abiertos (no mergeados). Si se quiere eso, se puede ampliar `poll-sdk.sh`
para listar PRs abiertos con `gh pr list`, pero añade complejidad; el caso "código que entra a
release" queda cubierto.
