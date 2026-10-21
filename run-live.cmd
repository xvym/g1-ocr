@echo off
setlocal
title G1 OCR
pushd "%~dp0"
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-live.ps1"
set "launchExit=%errorlevel%"
popd
if not "%launchExit%"=="0" (
    echo.
    echo [ERROR] G1 OCR could not run. See the error above.
    pause
)
exit /b %launchExit%
