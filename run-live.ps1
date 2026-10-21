$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $jdkPath = $env:JAVA_HOME
    if (Test-Path -LiteralPath '.tools/jdk21.0.9_10/bin/java.exe') {
        $jdkPath = (Resolve-Path '.tools/jdk21.0.9_10').Path
    }
    if (-not $jdkPath) { throw '请设置 JAVA_HOME 指向 Windows x64 JDK 21。' }
    if (-not (Test-Path -LiteralPath 'target/g1-ocr-0.2.0.jar')) { throw '请先运行 ./build.ps1 -SkipTests。' }
    if (-not (Test-Path -LiteralPath 'output/native/Release/g1capture.dll')) { throw '请先运行 ./build-capture.ps1。' }
    $fxCache = Join-Path $PSScriptRoot 'output/javafx-cache'
    & (Join-Path $jdkPath 'bin/java.exe') "-Djavafx.cachedir=$fxCache" -jar target/g1-ocr-0.2.0.jar --live
    if ($LASTEXITCODE -ne 0) { throw "应用退出代码：$LASTEXITCODE" }
} finally {
    Pop-Location
}
