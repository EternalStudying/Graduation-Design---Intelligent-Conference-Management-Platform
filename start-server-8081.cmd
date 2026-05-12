@echo off
setlocal

cd /d "%~dp0"

call mvn -N -DskipTests install
if errorlevel 1 exit /b %errorlevel%

call mvn -pl meeting-room-common clean install -DskipTests "-Dspring-boot.repackage.skip=true"
if errorlevel 1 exit /b %errorlevel%

call mvn -pl meeting-room-server spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"

endlocal
