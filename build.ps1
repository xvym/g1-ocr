param(
    [ValidateSet('all', 'development', 'holdout')][string]$Group = 'all',
    [switch]$SkipTests
)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    # No machine-wide JDK or Maven configuration changes.
    if (Test-Path -LiteralPath '.tools/jdk21.0.9_10/bin/java.exe') {
        $env:JAVA_HOME = (Resolve-Path '.tools/jdk21.0.9_10').Path
    }
    $buildArgs = @('-B', '-s', 'tools/maven-settings.xml', '-Dmaven.repo.local=.tools/m2', "-Docr.group=$Group")
    if ($SkipTests) { $buildArgs += '-DskipTests' }
    & mvn @buildArgs verify
    if ($LASTEXITCODE -ne 0) { throw "Maven failed: $LASTEXITCODE" }
} finally {
    Pop-Location
}
