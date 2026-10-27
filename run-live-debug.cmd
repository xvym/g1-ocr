@echo off
setlocal
title G1 OCR - Debug
pushd "%~dp0"
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-live.ps1" -DebugMode
set "launchExit=%errorlevel%"
popd
echo.
echo [DEBUG] G1 OCR exited with code %launchExit%.
echo [DEBUG] Logs: "%~dp0output\live-stdout.log" and "%~dp0output\live-stderr.log"
pause
exit /b %launchExit%
