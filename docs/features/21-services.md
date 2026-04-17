# 21 — Services: MediastreamPlayerService y MediastreamPlayerServiceWithSync

## Descripcion

El SDK expone dos servicios de Android para mantener la reproduccion en background y soportar integraciones del sistema (notificaciones, Android Auto, pantalla de bloqueo).

---

## MediastreamPlayerService

**Proposito:** Reproduccion de media en background con notificacion persistente y MediaSession.
**Uso tipico:** Audio en background, notificacion con controles de media.

### Que hace

- Mantiene el player vivo cuando la app va a background
- Crea y gestiona la notificacion con `PlayerNotificationManager`
- Expone `MediaSession` para controles externos (Bluetooth, wearables, pantalla de bloqueo)
- Maneja los botones de media: **Next**, **Previous** (via `ACTION_NEXT` / `ACTION_PREVIOUS`)
- Forwardea callbacks del player al cliente: `nextEpisodeIncoming`, `onConfigChange`

### Comunicacion con el player

El service y el player se comunican via **EventBus** (libreria `org.greenrobot:eventbus`). El player publica eventos y el service los consume — esto permite que sean instancias separadas sin referencia directa.

```
MediastreamPlayer  →[EventBus]→  MediastreamPlayerService
                                         │
                                    Notificacion
                                    MediaSession
                                    Callbacks → Cliente
```

### Declaracion en Manifest (cliente)

```xml
<service
    android:name="am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerService"
    android:foregroundServiceType="mediaPlayback"
    android:exported="false">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaSessionService"/>
    </intent-filter>
</service>
```

### Callbacks que forwardea al cliente

| Callback | Cuando |
|----------|--------|
| `nextEpisodeIncoming(nextEpisodeId)` | Player detecta que se aproxima el siguiente episodio |
| `onConfigChange(miniPlayerConfig)` | Cambio en la configuracion del mini player |

### Botones de notificacion

El service escucha `ACTION_NEXT` y `ACTION_PREVIOUS`:
```kotlin
// Internamente en el service
ACTION_NEXT → msPlayerCallback?.onNext()
ACTION_PREVIOUS → msPlayerCallback?.onPrevious()
```

---

## MediastreamPlayerServiceWithSync

**Proposito:** Extiende `MediaLibraryService` (Media3) para soporte de Android Auto y Android Automotive.
**Uso tipico:** Apps que requieren Android Auto + reproduccion en background.
**Tamaño:** ~1891 lineas — es el service mas complejo del SDK.

### Que hace ademas de MediastreamPlayerService

- Implementa `MediaLibraryService` (protocolo de Android Auto)
- Expone el browser de contenido para Android Auto: tabs de Live y Podcast
- Maneja la navegacion jerarquica de contenido en Auto: `LIVE → Channels`, `PODCAST → Shows → Episodes`
- Soporta modos de menu: `LIVE`, `PODCAST`, `SEASON`, `EPISODE`
- Llama a la API para obtener contenido del auto (`getAutoDataLive`, `getAutoDataPodcast`, `getEpisodesInfo`, `getShowInfo`)
- Forwardea `nextEpisodeIncoming` en dos lugares: nivel de service y nivel de listener del player

### Arquitectura de contenido para Android Auto

```
Root
├── LIVE (tab)
│   └── Lista de canales en vivo
└── PODCAST (tab)
    └── Lista de shows
        └── Lista de episodios (SEASON/EPISODE)
```

### Declaracion en Manifest (cliente)

```xml
<service
    android:name="am.mediastre.mediastreamplatformsdkandroid.MediastreamPlayerServiceWithSync"
    android:exported="true"
    android:foregroundServiceType="mediaPlayback">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaSessionService"/>
        <action android:name="androidx.media3.session.MediaLibraryService"/>
    </intent-filter>
</service>

<meta-data
    android:name="com.google.android.gms.car.application"
    android:resource="@xml/automotive_app_desc"/>
```

### Modos de inicializacion

El service puede operar en dos modos controlados por el flag `isFromSyncService`:

```kotlin
// Constructor del player con isFromSyncService=true (desde el service)
MediastreamPlayer(
    context = this,
    config = config,
    container = null,
    playerContainer = null,
    msMiniPlayerConfig = null,
    isFromSyncService = true   // indica que se inicializa desde un Service
)
```

---

## Cual service usar

| Caso de uso | Service recomendado |
|-------------|-------------------|
| Solo audio en background | `MediastreamPlayerService` |
| Audio + Android Auto | `MediastreamPlayerServiceWithSync` |
| Video (sin background necesario) | Ninguno (player en Activity) |
| Video + mini player flotante | `MediastreamPlayerService` |

---

## EventBus — comunicacion interna

Tanto el player como los services usan EventBus para comunicarse sin acoplamiento directo. Si el cliente usa EventBus en su propia app, debe tener cuidado de **no interceptar los eventos del SDK**.

---

## Permisos requeridos

```xml
<!-- Para los servicios de media en background -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"/>

<!-- Para recibir la accion de inicio en boot (si se requiere) -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
```

El SDK declara estos permisos en su propio Manifest y se fusionan automaticamente (manifest merger).

---

## NotificationReceiver

El SDK incluye un `BroadcastReceiver` para recibir acciones de los botones de la notificacion:

```xml
<!-- En el Manifest del SDK -->
<receiver android:name="am.mediastre...NotificationReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="android.intent.action.MEDIA_BUTTON"/>
    </intent-filter>
</receiver>
```

---

## Testing — Escenarios a cubrir

### MediastreamPlayerService
- [ ] El service arranca cuando el player inicia reproduccion de audio
- [ ] La notificacion aparece con titulo y artwork correctos
- [ ] Boton "siguiente" en notificacion dispara `onNext()` callback
- [ ] Boton "anterior" en notificacion dispara `onPrevious()` callback
- [ ] `nextEpisodeIncoming` llega al cliente cuando se origina desde el service
- [ ] El service se destruye cuando el player se cierra
- [ ] La notificacion desaparece cuando el service se destruye

### MediastreamPlayerServiceWithSync
- [ ] El service se conecta correctamente al head unit de Android Auto
- [ ] Tab de LIVE aparece con la lista de canales desde la API
- [ ] Tab de PODCAST aparece con la lista de shows desde la API
- [ ] Navegacion PODCAST → Show → Episodios funciona correctamente
- [ ] Seleccionar un item en Auto inicia la reproduccion en el telefono
- [ ] `nextEpisodeIncoming` llega al cliente cuando se origina desde este service
- [ ] El player inicializado con `isFromSyncService=true` funciona sin Activity

### Comunicacion EventBus
- [ ] Los eventos del player llegan correctamente al service via EventBus
- [ ] Si el cliente tiene EventBus propio, los eventos del SDK no interfieren

---

*Feature: 21-services | SDK v9.9.0 | 2026-04-16*
