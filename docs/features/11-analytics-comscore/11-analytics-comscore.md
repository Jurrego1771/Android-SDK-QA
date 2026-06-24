# 11 — Analytics: Comscore

## Descripcion

Integracion con Comscore Streaming Analytics para medir audiencia. Se configura automaticamente desde los datos que devuelve la API (`ConfigMain.comscore`).

## Como funciona

El SDK instancia `ComscoreAnalyticsManager` durante la inicializacion del player si el campo `comscore` esta presente en la respuesta de `ConfigMain`. El cliente no necesita configurar Comscore manualmente.

## Configuracion desde la API (ConfigMain)

El objeto `comscore` en la respuesta de la API contiene:
- `c2` — Publisher ID de Comscore
- `ns_site` — nombre del sitio
- Clasificacion de contenido (short/long/live form)
- Parametros custom

## Eventos trackeados automaticamente

| Evento | Cuando |
|--------|--------|
| Play | Al iniciar reproduccion |
| Pause | Al pausar |
| End | Al terminar el contenido |
| Buffer start/end | Al entrar/salir de buffering |
| Seek | Al hacer seek |

## Testing — Escenarios a cubrir

- [ ] Comscore se inicializa cuando la API devuelve datos de Comscore
- [ ] NO se inicializa cuando la API no devuelve datos de Comscore
- [ ] Evento de play se registra en Comscore al iniciar
- [ ] Evento de pause se registra correctamente
- [ ] Evento de end se registra al finalizar el contenido

**Nota de testing:** Comscore es una libreria externa. En unit/integration tests se debe mockear `ComscoreAnalyticsManager` y verificar que se llama con los parametros correctos. No se deben hacer asserts sobre el SDK de Comscore en si.

---

*Feature: 11-analytics-comscore | SDK v9.9.0 | 2026-04-16*
