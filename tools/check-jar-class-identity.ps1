param([Parameter(Mandatory=$true)][string]$Jar,[Parameter(Mandatory=$true)][string]$Classes,[Parameter(Mandatory=$true)][string]$Output)
$ErrorActionPreference='Stop'
if(Test-Path -LiteralPath $Output){throw 'Output already exists'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive=[System.IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $Jar).Path)
$records=@()
try {
    foreach($entry in $archive.Entries) {
        if(!$entry.FullName.EndsWith('.class')){continue}
        $stream=$entry.Open()
        $hasher=[System.Security.Cryptography.SHA256]::Create()
        try {$digest=([System.BitConverter]::ToString($hasher.ComputeHash($stream))).Replace('-','').ToLowerInvariant()}
        finally {$stream.Dispose();$hasher.Dispose()}
        $classPath=Join-Path $Classes $entry.FullName
        $classHash=(Get-FileHash -LiteralPath $classPath -Algorithm SHA256).Hash.ToLowerInvariant()
        if($digest -ne $classHash){throw "Package class mismatch: $($entry.FullName)"}
        $records+=@{entry=$entry.FullName;sha256=$digest}
    }
} finally {$archive.Dispose()}
$result=@{jar=$Jar;sha256=(Get-FileHash -LiteralPath $Jar -Algorithm SHA256).Hash.ToLowerInvariant();classes=$records;count=$records.Count;meaning='Every packaged class equals current compiled class; not video acceptance'}
$result|ConvertTo-Json -Depth 8|Set-Content -LiteralPath $Output -Encoding utf8
Write-Output "Verified $($records.Count) packaged classes."
