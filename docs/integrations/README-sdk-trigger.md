# Disparo cross-repo: cambio en el SDK → QA automático

Cuando el equipo del SDK abre un PR o hace push a la rama de release, el repo de QA
(`Android-SDK-QA`) ejecuta **automáticamente** el pipeline caja-negra en un runner self-hosted
(la PC con el device): obtiene el **diff real del código** del SDK, analiza qué features afectó,
explora el comportamiento en device con MCP, genera/ajusta tests, los corre, y abre un PR de QA
con el reporte. **No mergea nada** — deja todo para revisión humana.

> Por qué el diff y no un changelog escrito: el diff del código es la fuente de verdad; capta
> cambios que un changelog redactado a mano puede omitir o describir mal. Un PR además trae su
> título/descripción como narrativa de intención.

## Arquitectura

```
[Repo SDK]  PR o push a rama release
   └─ .github/workflows/QA-trigger.yml  → repository_dispatch (sdk-pr | sdk-push) ──┐
                                            payload: repo, ref, pr_number / base+head │
                                                                                      ▼
[Repo QA]  .github/workflows/sdk-dispatch.yml  (self-hosted, TU PC + device)
   └─ fetch-sdk-diff.js   → ai-output/diff.txt (diff REAL del SDK)
   └─ /diff-analyzer → /test-strategist → /changelog-explorer (MCP+device)
   └─ /test-generator → run-tests.sh (device) → /test-analyzer
   └─ diff-sessions + /version-comparator → rama + PR de QA (revisión humana)
```

## Pasos para el equipo del SDK (una sola vez)

### 1. Crear el token de disparo (PAT)
El repo del SDK necesita permiso para disparar el workflow del repo QA. Crear un **fine-grained PAT**:

1. GitHub → Settings → Developer settings → **Fine-grained tokens** → *Generate new token*.
2. **Resource owner**: la org/usuario dueño del repo QA (`Jurrego1771`).
3. **Repository access** → *Only select repositories* → `Android-SDK-QA`.
4. **Permissions** → Repository permissions → **Contents: Read and write** *o* como mínimo
   **Metadata: Read** + lo necesario para `dispatches`. En la práctica, para `POST /dispatches`
   basta **Contents: Read and write** (la API de dispatch lo exige). 
5. Generar y copiar el token.

> Nota: el `GITHUB_TOKEN` por defecto NO sirve — no puede disparar workflows en *otro* repo.

### 2. Guardar el token como secret en el repo del SDK
Repo del SDK → Settings → Secrets and variables → Actions → **New repository secret**:
- Name: `QA_DISPATCH_TOKEN`
- Value: el PAT del paso 1.

### 3. Colocar el workflow
Copiar `sdk-side-trigger.yml` (en esta carpeta) al repo del SDK como
`.github/workflows/QA-trigger.yml`. **Ajustar las ramas** (`branches:`) a las de release reales
del SDK (hoy puestas como `10.0.x` y `release/**`).

## Pasos en el repo QA (lado nuestro — ya implementado)

Solo falta configurar 2 secrets/vars en `Android-SDK-QA` → Settings → Secrets and variables → Actions:

| Tipo | Nombre | Para qué |
|---|---|---|
| Secret | `ANTHROPIC_API_KEY` | agentes `claude -p` headless |
| Secret | `SDK_READ_TOKEN` | **leer el diff del repo del SDK** (PAT con *Contents: Read* del repo SDK). Si el SDK es **público**, puede omitirse y usar el `gh` del runner. |
| Secret | `SLACK_WEBHOOK_URL` | (opcional) notificaciones |
| Variable | `SDK_REPO` | (opcional) `owner/repo` del SDK por defecto; el payload ya lo manda |

El runner self-hosted debe estar **online con el device conectado**. Si está apagado, el job encola
hasta que vuelva.

## Probarlo sin esperar un push real
En el repo QA → Actions → **SDK Change QA (cross-repo)** → *Run workflow* (workflow_dispatch):
- `event=pr`, `pr=<nº de un PR real del SDK>` — o
- `event=push`, `base=<sha>`, `head=<sha>`.

## Qué produce
Una rama `auto/sdk-pr-<n>` (o `auto/sdk-push-<sha>`) con un PR de QA que incluye, en `ai-output/`:
`diff.txt`, `analysis.md`, `strategy.md`, `exploration.md`, `test-analysis-report.md`,
`session-diff.md`, `version-comparison-report.md` y el reporte HTML. **No se mergea solo.**
