$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$checks = @()
foreach ($entry in @(@(6258,336,352,352,368),@(6291,368,304,384,336),@(6292,368,304,384,336),@(6293,288,352,304,368))) {
    $frameNumber = $entry[0]
    $file = "data/frames/video-01/authorized-correction-intro-creatures-pass2-v1/frame-$('{0:D6}' -f $frameNumber).png"
    $bitmap = [System.Drawing.Bitmap]::new([string](Resolve-Path -LiteralPath $file).Path)
    try {
        $values = @()
        for ($y=$entry[2]; $y -lt $entry[4]; $y++) {
            for ($x=$entry[1]; $x -lt $entry[3]; $x++) {
                $color = $bitmap.GetPixel($x,$y)
                $values += ($color.R+2*$color.G+$color.B)/4
            }
        }
        $m = $values | Measure-Object -Minimum -Maximum -Average
        $checks += [pscustomobject]@{frame=$frameNumber; png=$file; sha256=(Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLower(); rect=$entry[1..4]; pixels=$values.Count; minimum=$m.Minimum; maximum=$m.Maximum; average=$m.Average; constantLuminance=($m.Minimum -eq $m.Maximum)}
    } finally { $bitmap.Dispose() }
}
$out = Join-Path $PSScriptRoot 'absent-cell-pixel-checks-v2.json'
if (Test-Path -LiteralPath $out) { throw 'Use new output' }
$checks | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $out -Encoding utf8
$checks | Select-Object frame,pixels,minimum,maximum,constantLuminance | Format-Table
