param([Parameter(Mandatory=$true)][string]$Work,[int]$Shard=0,[int]$Shards=2)
$ErrorActionPreference='Stop'
$probeClasspath="$Work/probe-classes;app/target/classes;verification/target/classes;.cache/m2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar"
$probeItems=Get-Content -LiteralPath "$Work/joint-all-inputs/index.json" -Raw | ConvertFrom-Json
for($probeIndex=0;$probeIndex -lt $probeItems.Count;$probeIndex++){
    if(($probeIndex % $Shards) -ne $Shard){continue}
    $probeItem=$probeItems[$probeIndex]
    $probeOutput="$Work/joint-all/$($probeItem.clip).json"
    if(Test-Path -LiteralPath $probeOutput){throw "Existing output: $probeOutput"}
    & java '-Dfile.encoding=UTF-8' -cp $probeClasspath io.github.g1ocr.verification.JointGridProbe $probeItem.input $probeOutput
    if($LASTEXITCODE -ne 0){throw "Probe failed: $($probeItem.clip)"}
    Write-Output "Closed continuous pixel experiment: $($probeItem.clip)"
}
