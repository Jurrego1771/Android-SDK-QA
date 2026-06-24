# 08 — DVR y Fictitious Timeline

## Descripcion

DVR (Digital Video Recording) permite al usuario hacer seek atras en un stream en vivo. El SDK implementa un "fictitious timeline" — una seekbar personalizada que representa el tiempo real del DVR, no el tiempo del segmento HLS.

## Configuracion

```kotlin
val config = MediastreamPlayerConfig()
config.id = "LIVE_CHANNEL_ID"
config.accountID = "ACCOUNT_ID"
config.type = MediastreamPlayerConfig.VideoTypes.LIVE
config.dvr = true
config.windowDvr = 7200    // ventana DVR de 2 horas en segundos
```

## Como funciona internamente

El DVR en HLS tiene una "ventana deslizante" de segmentos disponibles. El SDK traduce esta ventana a un timeline ficticio donde:

- **Posicion 0** = el inicio de la ventana DVR disponible
- **Posicion max** = el borde del live (ahora mismo)
- El usuario puede hacer seek a cualquier punto dentro de la ventana

### Componentes clave del SDK

- `DvrTimeBar` — seekbar personalizada que muestra el timeline DVR
- `FakeTimelineView` — representacion visual del timeline ficticio
- `MediastreamDAIController` — integra ads con el timeline DVR

## Diferencias con Live sin DVR

| Aspecto | Live sin DVR | Live con DVR |
|---------|-------------|--------------|
| Seekbar | Oculta | Visible (DvrTimeBar) |
| Seek | No permitido | Hasta `windowDvr` segundos atras |
| Posicion inicial | Live edge | Live edge |
| `onEnd` | No se dispara | Se dispara si el usuario alcanza el edge y el stream termina |

## Ventana DVR

- `windowDvr` define cuantos segundos hacia atras puede ir el usuario
- Si el servidor tiene menos historia que `windowDvr`, se muestra lo disponible
- Al llegar al borde de la ventana (inicio), el usuario no puede ir mas atras

## Advertencias

- La logica de offset del fictitious timeline es COMPLEJA — alto riesgo de bugs
- Los seeks en DVR deben convertir entre "tiempo ficticio" y "tiempo real del segmento HLS"
- Con ads (DAI) + DVR, la complejidad se multiplica

## Testing — Escenarios a cubrir

- [ ] DVR habilitado muestra la seekbar DvrTimeBar
- [ ] Seek hacia atras lleva al tiempo correcto
- [ ] Seek al inicio de la ventana DVR funciona
- [ ] Seek al live edge funciona (posicion maxima)
- [ ] Posicion actual se actualiza correctamente durante reproduccion
- [ ] Con `windowDvr=3600`, no se puede ir mas de 1 hora atras
- [ ] Reanudar desde posicion DVR (no live edge) mantiene posicion
- [ ] Volver al live edge desde posicion DVR
- [ ] DVR + ads: el timeline incluye correctamente el tiempo de anuncios
- [ ] DVR disabled: no muestra seekbar de DVR

---

*Feature: 08-dvr | SDK v9.9.0 | 2026-04-16*
