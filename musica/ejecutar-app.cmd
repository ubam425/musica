@echo off
setlocal

cd /d "%~dp0"

if not defined JAVA_HOME (
  set "JAVA_HOME=C:\Users\perez\.jdks\openjdk-26"
)

set "PATH=%JAVA_HOME%\bin;%PATH%"

echo Iniciando MusicUbam en http://localhost:8081/
echo Para detener la aplicacion, cierra esta ventana o presiona Ctrl+C.
echo.

call .\mvnw.cmd spring-boot:run

pause
