param(
    [Parameter(Mandatory = $true)][string]$ReleaseRoot,
    [Parameter(Mandatory = $true)][string]$ChecksumFile
)

$ErrorActionPreference = "Stop"
$failed = $false

Get-Content -LiteralPath $ChecksumFile | ForEach-Object {
    $line = $_.Trim()
    if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith("#")) {
        return
    }

    $parts = $line -split "\s+\*", 2
    if ($parts.Count -ne 2) {
        Write-Host "INVALID CHECKSUM LINE: $line"
        $failed = $true
        return
    }

    $expected = $parts[0].Trim().ToLowerInvariant()
    $relative = $parts[1].Trim()
    $file = Join-Path $ReleaseRoot $relative

    if (-not (Test-Path -LiteralPath $file -PathType Leaf)) {
        Write-Host "MISSING: $relative"
        $failed = $true
        return
    }

    $actual = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $expected) {
        Write-Host "FAILED: $relative"
        $failed = $true
    } else {
        Write-Host "OK: $relative"
    }
}

if ($failed) { exit 1 }
Write-Host "All release checksums are valid."
exit 0
