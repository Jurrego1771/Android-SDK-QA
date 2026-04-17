# 19 — Notificaciones de Media

## Descripcion

El SDK muestra una notificacion de medios persistente durante la reproduccion. Permite controlar el player desde la barra de notificaciones y la pantalla de bloqueo.

## Configuracion

```kotlin
val config = MediastreamPlayerConfig()
config.notificationTitle = "Titulo del contenido"
config.notificationDescription = "Descripcion o nombre del canal"
config.notificationIconUrl = "https://example.com/artwork.jpg"
config.notificationHasNext = true  // muestra boton "siguiente" en la notificacion
```

## Permisos requeridos (Android 13+)

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
```

El cliente debe solicitar este permiso en runtime en Android 13+.

## Controles en la notificacion

- Play / Pause
- Siguiente (si `notificationHasNext = true`)
- Cerrar/detener

## Comportamiento

- La notificacion aparece automaticamente al iniciar reproduccion
- Se actualiza con metadata del contenido (titulo, artwork)
- Al pausar, la notificacion permanece (permite reanudar)
- Al cerrar el player, la notificacion se elimina
- Integrada con Media Session para soporte en pantalla de bloqueo

## Componentes del SDK

| Clase | Descripcion |
|-------|-------------|
| `NotificationReceiver` | Recibe intents de los botones de la notificacion |
| `MediastreamPlayerService` | Service que mantiene la notificacion activa |

## Testing — Escenarios a cubrir

- [ ] Notificacion aparece al iniciar reproduccion
- [ ] El titulo y descripcion se muestran correctamente
- [ ] El artwork se carga desde `notificationIconUrl`
- [ ] Boton pause/play en la notificacion funciona
- [ ] Boton siguiente en la notificacion (con `notificationHasNext=true`) funciona
- [ ] Notificacion desaparece al cerrar el player
- [ ] En Android 13+: sin permiso `POST_NOTIFICATIONS`, no lanza excepcion (degrada graciosamente)

---

*Feature: 19-notifications | SDK v9.9.0 | 2026-04-16*
