# QA autónomo del SDK (cron + changelog + Maven)

Tu máquina corre, sola y periódicamente, QA caja-negra del SDK **cuando hay una versión nueva
publicada en Maven** — sin que el equipo del SDK toque nada (sin workflow ni PAT en su repo).

## Por qué este diseño

El harness prueba un **binario publicado** (el `.aar` de Maven). Por eso el disparador correcto NO es
un push de código (el binario puede no existir aún), sino **que aparezca una versión nueva en Maven**.
El CHANGELOG.md del SDK da la **línea** (`10.0.8`) y el contexto; Maven da el **artefacto exacto**
(`10.0.8-alpha08`) y confirma que el binario existe.

## Flujo

```
Cron (cada 30 min, self-hosted)  →  scripts/watch-sdk.sh
  1. gh: lee CHANGELOG.md de <SDK_REPO>@<SDK_BRANCH> → línea de versión (10.0.8)
  2. resolve-sdk-version.sh → último alpha mainline en Maven (10.0.8-alpha08)
        └─ si la línea aún no tiene artefacto en Maven → no-op (reintenta al próximo cron)
  3. ¿nueva vs .last-tested-sdk?   no → no-op (no toca el device)
  4. sí → (núcleo qa-core.sh) change-analyzer → strategist → explore(MCP+device) → bump → generate
          → run-tests(device) → analyze → diff-sesiones → version-comparator
          → rama auto/sdk-<versión> + PR de QA   (gh del runner)
          → marca .last-tested-sdk = <versión>   (solo si abrió el PR)
```

Como el cron reintenta, **resuelve también la espera de Maven**: si el alpha aún no está publicado, la
próxima pasada lo encontrará. No hay que adivinar el delay de publicación.

## Config (GitHub → repo QA → Settings)

| Tipo | Nombre | Default | Para qué |
|---|---|---|---|
| Secret | `ANTHROPIC_API_KEY` | — | agentes `claude -p` (o en `scripts/.env` del runner) |
| Secret | `SLACK_WEBHOOK_URL` | — | (opcional) avisos |
| Variable | `SDK_REPO` | `mediastream/MediastreamPlatformSDKAndroid` | repo del SDK |
| Variable | `SDK_BRANCH` | `10.0.8` | rama de versión a vigilar (cambiar al subir de línea) |

**Sin tokens de GitHub.** El runner corre como tu usuario con `gh` autenticado (scope `repo`): el
mismo login lee el SDK y crea el PR de QA. El workflow a propósito NO setea `GH_TOKEN`.

## Requisitos del runner
- `gh` autenticado (ya está), `claude` en PATH con `ANTHROPIC_API_KEY`, y el **device conectado**.
- Si el device no está, el pipeline sale exit 2 y **no marca la versión como probada** → reintenta.
- Si la PC está apagada, el cron encola/omite; al encender, la próxima pasada revisa.

## Probar / operar
```bash
# correr el watcher a mano (igual que el cron):
SDK_BRANCH=10.0.8 bash scripts/watch-sdk.sh

# forzar reproceso de una versión ya probada: borrar/editar el estado
rm -f .last-tested-sdk            # o: echo "10.0.8-alpha07" > .last-tested-sdk

# resolver a mano qué versión saldría:
bash scripts/resolve-sdk-version.sh 10.0.8 --plain      # → 10.0.8-alpha08
ANY_VARIANT=1 bash scripts/resolve-sdk-version.sh 10.0.8 --plain   # incluye eu/hotfix
```

En GitHub: repo QA → Actions → **SDK Maven Watch** → *Run workflow* para dispararlo a demanda.

## Alcance
Vigila la **progresión mainline** (`-alphaNN`) de la línea configurada. Las variantes EU/hotfix
(`-alpha-eu-*`, `-eu-hotfix-*`) se excluyen por defecto (requieren env EU/accessToken) — se prueban a
mano con `ANY_VARIANT=1`. Para vigilar varias líneas a la vez habría que extender el watcher.
