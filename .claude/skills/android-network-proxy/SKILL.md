---
name: android-network-proxy
description: >-
  Inspeccionar el tráfico de red HTTPS de la app Android de QA del SDK de
  Mediastream con mitmproxy, incluidas las llamadas de ads de Google IMA
  (VAST/VMAP + beacons) que el Network Inspector de Android Studio NO puede
  ver. Úsala cuando el usuario quiera capturar, interceptar o depurar peticiones
  de red, ads, beacons de tracking, manifiestos HLS/DASH, o tráfico del SDK en
  un dispositivo físico o emulador. En Windows + Python.
---

# Android Network Proxy (mitmproxy)

Flujo para interceptar el tráfico HTTPS de la app `com.example.sdk_qa` con
mitmproxy en Windows. Resuelve el caso que el **Network Inspector de Android
Studio no cubre**: las llamadas de **Google IMA** (ads), que usan su propio
stack de red y un WebView, no OkHttp/HttpURLConnection.

## Cuándo usar qué herramienta

| Quieres ver… | Herramienta | Certificado |
|--------------|-------------|-------------|
| Llamadas del SDK Mediastream (OkHttp) | Android Studio → Network Inspector | No hace falta |
| **Ads IMA (VAST/VMAP, beacons), streaming HLS/DASH** | **mitmproxy (esta skill)** | Sí (CA de usuario) |
| Tráfico con SSL pinning de Google | Frida + bypass | Sí + bypass |

## Requisitos clave (no saltar)

1. **mitmproxy** instalado vía pip. Los ejecutables quedan en
   `C:\Users\<user>\AppData\Roaming\Python\Python3XX\Scripts\` (NO está en PATH
   por defecto — invocar con ruta completa).
2. **`network_security_config.xml`** en la app con `<debug-overrides>` que
   confíe en certificados `user`. Sin esto, una app con `targetSdk 24+` IGNORA
   el cert de mitmproxy y la interceptación HTTPS falla. Ya está configurado en
   `app/src/main/res/xml/network_security_config.xml` y referenciado en el
   manifest. Si no existe, créalo (ver más abajo).
3. **Firewall**: el puerto 8080 inbound debe estar permitido. Si la red está en
   perfil *Public*, Windows bloquea la conexión del teléfono. Requiere admin.

## Paso 1 — Instalar mitmproxy (solo la primera vez)

```powershell
python -c "import mitmproxy" 2>$null; if ($LASTEXITCODE -ne 0) { pip install mitmproxy }
```

Localiza el ejecutable (la versión de Python puede variar):

```powershell
$mitm = Get-ChildItem "$env:APPDATA\Python\Python*\Scripts\mitmweb.exe" | Select-Object -First 1 -ExpandProperty FullName
```

## Paso 2 — Levantar mitmweb capturando el TOKEN

mitmweb 11+ protege la Web UI con un token que imprime al arrancar. Python
bufferiza stdout al redirigir a archivo, así que **hay que forzar
`PYTHONUNBUFFERED=1`** o el log sale vacío y el token nunca aparece.

```powershell
$env:PYTHONUNBUFFERED = "1"
$mitm = Get-ChildItem "$env:APPDATA\Python\Python*\Scripts\mitmweb.exe" | Select-Object -First 1 -ExpandProperty FullName
$log  = "D:\proxy\mitmweb.log"
Remove-Item $log,'D:\proxy\mitmweb.err.log' -Force -ErrorAction SilentlyContinue
Start-Process -FilePath $mitm `
  -ArgumentList '--listen-port','8080','--web-port','8081','--web-host','127.0.0.1','--no-web-open-browser' `
  -RedirectStandardOutput $log `
  -RedirectStandardError 'D:\proxy\mitmweb.err.log' `
  -WindowStyle Hidden
Start-Sleep -Seconds 4
Get-Content $log -Raw   # muestra: Web server listening at http://127.0.0.1:8081/?token=XXXX
```

Dale al usuario la **URL completa con `?token=...`** — si abre solo
`http://127.0.0.1:8081` se la vuelve a pedir. Tras entrar una vez, la cookie
queda guardada mientras el proceso siga vivo.

## Paso 3 — Abrir el puerto en el firewall (requiere admin)

```powershell
New-NetFirewallRule -DisplayName "mitmproxy 8080" -Direction Inbound -LocalPort 8080 -Protocol TCP -Action Allow -Profile Any
```

Desde una sesión sin admin falla con "Acceso denegado". Pasarle al usuario para
que lo corra con `!` y elevación:

```
! Start-Process powershell -Verb RunAs -ArgumentList '-Command','New-NetFirewallRule -DisplayName \"mitmproxy 8080\" -Direction Inbound -LocalPort 8080 -Protocol TCP -Action Allow -Profile Any'
```

## Paso 4 — Config de red en la app (si falta)

Crear `app/src/main/res/xml/network_security_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <debug-overrides>
        <trust-anchors>
            <certificates src="system" />
            <certificates src="user" />
        </trust-anchors>
    </debug-overrides>
</network-security-config>
```

Y en `AndroidManifest.xml`, en `<application>`:
`android:networkSecurityConfig="@xml/network_security_config"`.

**Recompilar e instalar tras este cambio** (obligatorio):

```powershell
cd D:\repos\jurrego1771\SDK-Android-Qa
.\gradlew installDebug
```

## Paso 5 — Conectar el dispositivo

Obtener la IP LAN del PC (ignorar 127.* / 169.* / 100.64.* de Tailscale/CGNAT):

```powershell
Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -like '192.168.*' -or $_.IPAddress -like '10.*' } | Select-Object -ExpandProperty IPAddress
```

- **Dispositivo físico** (misma WiFi): `Ajustes WiFi > red > Proxy: Manual`,
  Host = IP LAN del PC, Puerto = `8080`.
- **Emulador**: arrancar con `emulator -avd <avd> -http-proxy http://10.0.2.2:8080`
  (`10.0.2.2` = host visto desde el emulador).

## Paso 6 — Instalar el certificado CA en el dispositivo

Con el proxy ya configurado, en el navegador del dispositivo: `http://mitm.it`
→ descargar el de **Android** → instalar en
`Ajustes > Seguridad > Instalar certificado > Certificado CA`.

⚠️ Android API 29+ no confía en certs de usuario por defecto a nivel sistema;
por eso es imprescindible el `network_security_config` del Paso 4 (que sí hace
que ESTA app confíe en el cert de usuario en debug).

## Paso 7 — Filtrar los ads en la Web UI

En el buscador de mitmweb:

```
~d doubleclick.net | ~d imasdk.googleapis.com | ~d googlesyndication.com
```

Probar con el escenario **[06] Ads** (`VideoAdsScenarioActivity`). Verás el
request del tag VAST/VMAP (`adURL`), los wrappers en cadena y los beacons
(impression, start, quartiles, complete).

## Teardown

```powershell
Get-Process mitmweb,mitmproxy,mitmdump -ErrorAction SilentlyContinue | Stop-Process -Force
```

Recordar al usuario que quite el proxy manual de la WiFi del teléfono al
terminar.

## Troubleshooting

- **Log vacío / sin token** → falta `PYTHONUNBUFFERED=1` (ver Paso 2).
- **El teléfono no conecta al proxy** → firewall (Paso 3) o red en perfil
  *Public*; verificar que PC y teléfono están en la misma WiFi.
- **HTTPS de la app da error de certificado** → falta el `network_security_config`
  o no se recompiló con `installDebug` (Paso 4).
- **Algún beacon de Google no se descifra** → SSL pinning de IMA; el `adURL`
  VAST/VMAP normalmente sí se ve. Para los pinneados haría falta Frida.
- **`mitmweb.exe` no encontrado** → no está en PATH; usar la ruta completa en
  `%APPDATA%\Python\Python3XX\Scripts\`.
