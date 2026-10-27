param([switch]$DebugMode)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $jdkPath = $env:JAVA_HOME
    if (Test-Path -LiteralPath '.tools/jdk21.0.9_10/bin/java.exe') {
        $jdkPath = (Resolve-Path '.tools/jdk21.0.9_10').Path
    }
    if (-not $jdkPath) { throw '请设置 JAVA_HOME 指向 Windows x64 JDK 21。' }
    if (-not (Test-Path -LiteralPath 'target/g1-ocr-0.3.0.jar')) { throw '请先运行 ./build.ps1 -SkipTests。' }
    if (-not (Test-Path -LiteralPath 'output/native/Release/g1capture.dll')) { throw '请先运行 ./build-capture.ps1。' }
    $fxCache = Join-Path $PSScriptRoot 'output/javafx-cache'
    $launchArguments = '"-Djavafx.cachedir={0}" -jar target/g1-ocr-0.3.0.jar --live' -f $fxCache
    if ($DebugMode) {
        Write-Host 'Debug 模式：日志写入 output/live-stdout.log 和 output/live-stderr.log；关闭应用后返回。'
        $appProcess = Start-Process -FilePath (Join-Path $jdkPath 'bin/javaw.exe') -ArgumentList $launchArguments `
            -WorkingDirectory $PSScriptRoot -WindowStyle Normal -Wait -PassThru `
            -RedirectStandardOutput (Join-Path $PSScriptRoot 'output/live-stdout.log') `
            -RedirectStandardError (Join-Path $PSScriptRoot 'output/live-stderr.log')
        if ($appProcess.ExitCode -ne 0) { throw "应用退出代码：$($appProcess.ExitCode)，请查看 output/live-stderr.log。" }
    } else {
        Start-Process -FilePath (Join-Path $jdkPath 'bin/javaw.exe') -ArgumentList $launchArguments `
            -WorkingDirectory $PSScriptRoot -WindowStyle Normal
    }
} finally {
    Pop-Location
}
