@echo off
echo Stopping any existing Angular dev server...
taskkill /F /IM node.exe /FI "WINDOWTITLE eq ng serve*" 2>nul
timeout /t 2 /nobreak >nul

echo.
echo Starting Angular Development Server...
echo Frontend will be available at: http://localhost:4200
echo.
cd /d %~dp0
call npx ng serve --open --port 4200
pause