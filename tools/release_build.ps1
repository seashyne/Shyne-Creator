[CmdletBinding()]
param(
    [switch]$NoClean
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$propertiesPath = Join-Path $projectRoot 'gradle.properties'
$propertiesText = [IO.File]::ReadAllText($propertiesPath)
$versionMatch = [regex]::Match(
    $propertiesText,
    '(?m)^mod_version=(?<major>\d+)\.(?<minor>\d+)\.(?<patch>\d+)(?<suffix>-(?:alpha|beta)-.+)?$'
)
if (-not $versionMatch.Success) {
    throw 'Could not read mod_version from gradle.properties.'
}

$currentVersion = $versionMatch.Value.Substring('mod_version='.Length)
$nextPatch = [int]$versionMatch.Groups['patch'].Value + 1
$nextVersion = '{0}.{1}.{2}{3}' -f `
    $versionMatch.Groups['major'].Value,
    $versionMatch.Groups['minor'].Value,
    $nextPatch,
    $versionMatch.Groups['suffix'].Value

$versionFiles = @(
    'gradle.properties',
    'VERSION.txt',
    'README.md',
    'docs/README.md',
    'docs/architecture/AVATAR_SYSTEM.md',
    'docs/guides/BLOCKBENCH_ANIMATION_STANDARD.md',
    'docs/standards/CREATOR_SDK_TH.md',
    'docs/api/CUSTOM_RENDER_API_TH.md',
    'docs/api/RIG_API_TH.md',
    'docs/api/SHYNE_LUA_API_TH.md',
    'docs-site/src/content.ts',
    'tools/kit/README_INSTALL_TH.md',
    'fabric/src/main/java/seashyne/shynecore/ShyneCore.java',
    'neoforge/src/main/java/seashyne/shynecore/ShyneCore.java'
)

foreach ($relativePath in $versionFiles) {
    $path = Join-Path $projectRoot $relativePath
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Version file is missing: $relativePath"
    }
    $text = [IO.File]::ReadAllText($path)
    if ($text.Contains($currentVersion)) {
        [IO.File]::WriteAllText($path, $text.Replace($currentVersion, $nextVersion))
    }
}

$kitPath = Join-Path $projectRoot 'tools/kit/README_INSTALL_TH.md'
$kitText = [IO.File]::ReadAllText($kitPath)
$kitText = [regex]::new('Shyne Creator Kit \d+\.\d+\.\d+(?:-(?:alpha|beta)-26\.\d+)?').Replace($kitText, "Shyne Creator Kit $nextVersion", 1)
$kitText = [regex]::new('Shyne Creator \d+\.\d+\.\d+(?:-(?:alpha|beta)-26\.\d+)? ส่ง Public Avatar').Replace($kitText, "Shyne Creator $nextVersion ส่ง Public Avatar", 1)
[IO.File]::WriteAllText($kitPath, $kitText)

Write-Host "Version: $currentVersion -> $nextVersion"
Push-Location $projectRoot
try {
    $arguments = @()
    if (-not $NoClean) {
        $arguments += 'clean'
    }
    $arguments += 'releaseBundle'
    & .\gradlew.bat @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle release build failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}

Write-Host "Release ready: $nextVersion"
