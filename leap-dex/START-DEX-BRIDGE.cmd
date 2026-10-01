@echo off
setlocal
cd /d "%~dp0"
where adb >nul 2>nul
if errorlevel 1 (
  echo ERROR: adb no esta disponible en PATH.
  echo Deja abierto el ADB que ya usas para el Samsung y vuelve a intentar.
  pause
  exit /b 1
)
adb start-server >nul
for /f "skip=1 tokens=1,2" %%A in ('adb devices') do if "%%B"=="device" set DEVICE=%%A
if not defined DEVICE (
  echo ERROR: no hay Samsung conectado por ADB.
  echo Activa la conexion ADB inalambrica habitual y vuelve a ejecutar este archivo.
  pause
  exit /b 2
)
echo DeX Bridge: usando %DEVICE%
echo Deskflow debe estar iniciado como servidor con la pantalla Android a la derecha.
node esm/index.js 127.0.0.1:24800 Android
if errorlevel 1 pause
