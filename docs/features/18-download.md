# 18 — Descarga Offline

## Descripcion

Soporte para descargar contenido para reproduccion offline. Implementado via `DownloadTracker` y `DemoDownloadService`, basado en ExoPlayer DownloadManager.

## Componentes

| Clase | Descripcion |
|-------|-------------|
| `DownloadTracker` | Gestiona el estado de las descargas |
| `DemoDownloadService` | Service para descargas en background |

## Configuracion en el Manifest del cliente

```xml
<service android:name="am.mediastre...DemoDownloadService"
    android:exported="false">
    <intent-filter>
        <action android:name="androidx.media3.exoplayer.downloadService.action.RESTART"/>
    </intent-filter>
</service>
```

## Permisos requeridos

```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
```

## Flujo de descarga

```
1. Usuario selecciona "Descargar"
2. DownloadTracker registra la solicitud
3. DemoDownloadService descarga en background
4. Al completar, el contenido esta disponible offline
5. Reproduccion offline: usar config.src con la ruta local + onLocalSourceAdded callback
```

## Callback relevante

```kotlin
override fun onLocalSourceAdded() {
    // El contenido descargado se ha cargado como fuente local
}
```

## Testing — Escenarios a cubrir

- [ ] Descarga de VOD se completa correctamente
- [ ] Reproduccion offline de contenido descargado
- [ ] `onLocalSourceAdded` se dispara al usar fuente local
- [ ] Descarga cancelada no deja archivos corruptos
- [ ] Descarga con red interrumpida se reanuda al reconectar

**Nota:** Feature marcada como experimental/demo en el SDK. Verificar con el equipo Mediastream si esta feature es soportada en produccion.

---

*Feature: 18-download | SDK v9.9.0 | 2026-04-16*
