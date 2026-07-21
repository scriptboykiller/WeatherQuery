param(
    [Parameter(Mandatory = $true)][string]$BuiltJar,
    [Parameter(Mandatory = $true)][string]$Version,
    [Parameter(Mandatory = $true)][string]$TemplateDirectory,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [string]$ApprovedBaseline = ""
)

$ErrorActionPreference = "Stop"

$jarPath = (Resolve-Path -LiteralPath $BuiltJar).Path
$templatePath = (Resolve-Path -LiteralPath $TemplateDirectory).Path
$outputPath = [System.IO.Path]::GetFullPath($OutputDirectory)
$releaseName = "sql-postgres-validator-release"
$releaseRoot = Join-Path $outputPath $releaseName
$zipPath = Join-Path $outputPath "$releaseName-$Version.zip"

if (Test-Path -LiteralPath $releaseRoot) {
    Remove-Item -LiteralPath $releaseRoot -Recurse -Force
}
if (Test-Path -LiteralPath $zipPath) {
    Remove-Item -LiteralPath $zipPath -Force
}

New-Item -ItemType Directory -Force -Path $outputPath | Out-Null
Copy-Item -LiteralPath $templatePath -Destination $releaseRoot -Recurse

$releaseJarName = "sql-postgres-validator-$Version.jar"
Copy-Item -LiteralPath $jarPath -Destination (Join-Path $releaseRoot $releaseJarName)

if (-not [string]::IsNullOrWhiteSpace($ApprovedBaseline)) {
    $baselinePath = (Resolve-Path -LiteralPath $ApprovedBaseline).Path
    Copy-Item -LiteralPath $baselinePath `
        -Destination (Join-Path $releaseRoot "baseline\sql-select-baseline.csv")
}

$versionText = @"
Product: SQL PostgreSQL Validator
Version: $Version
Java: 17
Build Time UTC: $([DateTime]::UtcNow.ToString("yyyy-MM-ddTHH:mm:ssZ"))
Cross DB Normalization Version: v1
Performance Request Version: v2
Performance Result Version: v2
Performance Comparator: OPTIONAL - NOT INCLUDED
"@
Set-Content -LiteralPath (Join-Path $releaseRoot "VERSION.txt") `
    -Value $versionText -Encoding UTF8

$targets = @(
    $releaseJarName,
    "config\application.yml",
    "config\performance-tool.yml",
    "docs\USER_GUIDE.md",
    "docs\TROUBLESHOOTING.md",
    "VERSION.txt"
)

$checksumLines = foreach ($relative in $targets) {
    $full = Join-Path $releaseRoot $relative
    if (Test-Path -LiteralPath $full -PathType Leaf) {
        $hash = (Get-FileHash -LiteralPath $full -Algorithm SHA256).Hash.ToLowerInvariant()
        "$hash *$relative"
    }
}
Set-Content -LiteralPath (Join-Path $releaseRoot "SHA256SUMS.txt") `
    -Value $checksumLines -Encoding UTF8

$forbidden = Get-ChildItem -LiteralPath $releaseRoot -Recurse -Force | Where-Object {
    $_.Name -eq ".git" -or
    $_.Extension -eq ".java" -or
    $_.FullName -match "[\\/](test|tests|test-classes|target[\\/]classes)([\\/]|$)"
}
if ($forbidden) {
    $forbidden | ForEach-Object { Write-Host "FORBIDDEN: $($_.FullName)" }
    throw "Release contains forbidden source or build content."
}

Compress-Archive -LiteralPath $releaseRoot -DestinationPath $zipPath
Write-Host "Release directory: $releaseRoot"
Write-Host "Release ZIP:       $zipPath"
