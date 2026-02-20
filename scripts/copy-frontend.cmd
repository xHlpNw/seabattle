@echo off
REM Copy Angular production build into Spring Boot static resources.
REM Run after: cd frontend && npm run build
set ROOT=%~dp0..
set SRC=%ROOT%\frontend\dist\frontend-app
set DST=%ROOT%\src\main\resources\static

if not exist "%SRC%" (
    echo Build not found. Run: cd frontend ^&^& npm run build
    exit /b 1
)

if not exist "%DST%" mkdir "%DST%"
del /q "%DST%\*" 2>nul
xcopy /e /y "%SRC%\*" "%DST%\"
echo Copied frontend build to src/main/resources/static
echo Start the app and open http://localhost:8080
