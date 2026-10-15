param(
    [Parameter(Mandatory=$true, Position=0)][string]$Action,
    [Parameter(ValueFromRemainingArguments=$true)][string[]]$ToolArguments
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$gsonPath = Join-Path $projectRoot '.cache/m2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar'
$classesPath = Join-Path $projectRoot 'verification/target/classes'
if (!(Test-Path -LiteralPath $gsonPath) -or !(Test-Path -LiteralPath $classesPath)) {
    throw '先执行 mvn -Dmaven.repo.local=.cache/m2 test'
}
Push-Location $projectRoot
try {
    & java '-Dfile.encoding=UTF-8' '-cp' "$classesPath;$gsonPath" 'io.github.g1ocr.verification.VideoTool' $Action @ToolArguments
    exit $LASTEXITCODE
} finally { Pop-Location }
