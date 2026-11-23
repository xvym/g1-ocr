@echo off
setlocal
set "JAVA_HOME=D:\Code\Tool\openjdk-21+35_windows-x64_bin\jdk-21"
if not exist "%JAVA_HOME%\bin\javaw.exe" (
    echo [ERROR] Java 21 was not found at "%JAVA_HOME%".
    pause
    exit /b 1
)
call "%~dp0run-live.cmd"
exit /b %errorlevel%
