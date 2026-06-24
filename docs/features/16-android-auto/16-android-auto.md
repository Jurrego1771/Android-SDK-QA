# 16 — Android Auto / Automotive

## Descripcion

Soporte para reproduccion de audio en Android Auto (proyeccion en pantalla del vehiculo) y Android Automotive (sistema operativo del vehiculo). El SDK expone tabs de contenido (live, podcast) para la interfaz de Auto.

## Arquitectura

Android Auto no ejecuta la app del cliente en el vehiculo — proyecta una interfaz simplificada. El SDK expone el contenido via `MediastreamPlayerServiceWithSync` que implementa `MediaSessionService`.

```
App Android (telefono)
        │
        │ MediaSessionService
        ▼
Android Auto (pantalla del vehiculo)
        │
        │ Muestra tabs de contenido
        ▼
Usuario selecciona → reproduce en el telefono
```

## Componentes del SDK para Auto

| Clase | Descripcion |
|-------|-------------|
| `MediastreamPlayerService` | Servicio de reproduccion en background |
| `MediastreamPlayerServiceWithSync` | Extiende MediaSessionService para Auto |
| `AndroidAutoData` | Modelo de datos para tabs de Auto |
| `AutoTabDetailsResponse` | Respuesta de la API para tabs |
| `PodCastTabDetailsResponse` | Datos de tabs de podcast |
| `GetShowResponse` | Lista de shows para Auto |
| `GetEpisodesResponse` | Lista de episodios para Auto |

## Configuracion del Manifest (cliente)

```xml
<service android:name="am.mediastre...MediastreamPlayerServiceWithSync"
    android:exported="true"
    android:foregroundServiceType="mediaPlayback">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaSessionService"/>
    </intent-filter>
</service>

<meta-data
    android:name="com.google.android.gms.car.application"
    android:resource="@xml/automotive_app_desc"/>
```

## Endpoints de API para Auto

El SDK llama a endpoints especificos para obtener el contenido de Auto:
- `getAutoDataLive()` — tabs de live para Auto
- `getAutoDataPodcast()` — tabs de podcast para Auto
- `getShowInfo()` — detalles de un show
- `getEpisodesInfo()` — lista de episodios

## Testing — Escenarios a cubrir

- [ ] `MediastreamPlayerServiceWithSync` se conecta correctamente
- [ ] Tabs de live aparecen en Android Auto
- [ ] Tabs de podcast aparecen en Android Auto
- [ ] Reproduccion desde Auto controla correctamente el player
- [ ] Notificacion de media se actualiza con metadata del contenido actual

**Nota:** Los tests de Auto requieren el emulador de Android Auto del SDK de Android (Desktop Head Unit).

---

*Feature: 16-android-auto | SDK v9.9.0 | 2026-04-16*
