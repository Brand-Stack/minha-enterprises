@echo off
title Minha Enterprises - Biometric Sync Gateway
cd /d "%~dp0"

echo ===================================================
echo   Starting Minha Biometric Sync Gateway Service
echo ===================================================

where node >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo Error: Node.js is not found in PATH. Please install Node.js.
    pause
    exit /b 1
)

:LOOP
node gateway.js
echo.
echo [GATEWAY] Process exited or restarted. Waiting 5 seconds before restart...
timeout /t 5 /nobreak >nul
goto LOOP
