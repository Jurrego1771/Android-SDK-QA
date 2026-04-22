@echo off
setlocal

echo.
echo ══ GitHub Actions Runner — Check ^& Start ══
echo.

REM Find the service name (pattern: actions.runner.*)
for /f "tokens=*" %%S in ('sc query type= all state= all ^| findstr /i "actions.runner"') do (
    set "SVC_LINE=%%S"
)

REM Extract service name from "SERVICE_NAME: actions.runner.xxx"
set "SVC_NAME="
for /f "tokens=2 delims=: " %%N in ('sc query type= all state= all ^| findstr /i "SERVICE_NAME.*actions.runner"') do (
    set "SVC_NAME=%%N"
)

if "%SVC_NAME%"=="" (
    echo [ERROR] No se encontro ningun servicio actions.runner.*
    echo         Instala el runner como servicio desde C:\actions-runner:
    echo           .\svc.sh install
    echo           .\svc.sh start
    goto :end
)

echo [INFO]  Servicio encontrado: %SVC_NAME%

REM Check current state
for /f "tokens=4" %%T in ('sc query "%SVC_NAME%" ^| findstr "STATE"') do set "SVC_STATE=%%T"

if /i "%SVC_STATE%"=="RUNNING" (
    echo [ OK ]  Runner ACTIVO — listo para recibir jobs.
    goto :end
)

if /i "%SVC_STATE%"=="STOPPED" (
    echo [WARN]  Runner DETENIDO — iniciando...
    sc start "%SVC_NAME%" >nul 2>&1
    timeout /t 3 /nobreak >nul

    for /f "tokens=4" %%T in ('sc query "%SVC_NAME%" ^| findstr "STATE"') do set "SVC_STATE2=%%T"
    if /i "%%T"=="RUNNING" (
        echo [ OK ]  Runner iniciado correctamente.
    ) else (
        sc query "%SVC_NAME%" | findstr "STATE"
        echo [ERROR] No se pudo iniciar. Revisa Event Viewer o corre manualmente:
        echo         cd C:\actions-runner ^&^& .\run.cmd
    )
    goto :end
)

echo [WARN]  Estado inesperado: %SVC_STATE%
sc query "%SVC_NAME%"

:end
echo.
pause
endlocal
