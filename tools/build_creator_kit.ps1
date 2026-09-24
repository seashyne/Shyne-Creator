<#
.SYNOPSIS
Builds the public Shyne Creator Kit from an explicit allow-list.

.DESCRIPTION
The destination must be new or empty. Mod JARs, source trees, caches,
developer fixtures, and third-party converted assets are intentionally excluded.
#>
param(
    [string]$Destination = "",
    [switch]$Refresh
)

$ErrorActionPreference = "Stop"
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$projectParent = Split-Path -Parent $projectRoot
$version = (Get-Content -LiteralPath (Join-Path $projectRoot "VERSION.txt") -Raw).Trim()
if ([string]::IsNullOrWhiteSpace($version)) { throw "VERSION.txt is empty" }
$minecraftVersion = [regex]::Match($version, '\d+\.\d+$').Value
if ([string]::IsNullOrWhiteSpace($minecraftVersion)) { throw "Could not read Minecraft version from VERSION.txt: $version" }

if ([string]::IsNullOrWhiteSpace($Destination)) {
    $Destination = Join-Path $projectParent "Shyne-Creator-Kit-$version"
}
$destinationRoot = [IO.Path]::GetFullPath($Destination)
$sourcePrefix = $projectRoot.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
$destinationPrefix = $destinationRoot.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar

if ($destinationRoot.Equals($projectRoot, [StringComparison]::OrdinalIgnoreCase) -or
    $destinationRoot.StartsWith($sourcePrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Creator Kit must be built outside the source project: $destinationRoot"
}
if (Test-Path -LiteralPath $destinationRoot) {
    if (-not (Test-Path -LiteralPath $destinationRoot -PathType Container)) {
        throw "Destination is not a directory: $destinationRoot"
    }
    if ($null -ne (Get-ChildItem -LiteralPath $destinationRoot -Force | Select-Object -First 1)) {
        if (-not $Refresh) { throw "Destination must be new or empty (or pass -Refresh): $destinationRoot" }
        $kitMarker = Join-Path $destinationRoot "KIT_INFO.txt"
        if (-not (Test-Path -LiteralPath $kitMarker -PathType Leaf) -or
            -not ((Get-Content -LiteralPath $kitMarker -TotalCount 1) -eq "Shyne Creator Kit")) {
            throw "Refusing to refresh a folder without a Shyne Creator Kit marker: $destinationRoot"
        }
    }
} else {
    [IO.Directory]::CreateDirectory($destinationRoot) | Out-Null
}

function Copy-KitFile {
    param(
        [Parameter(Mandatory = $true)][string]$SourceRelative,
        [string]$DestinationRelative = $SourceRelative
    )

    $source = [IO.Path]::GetFullPath((Join-Path $projectRoot $SourceRelative))
    if (-not $source.StartsWith($sourcePrefix, [StringComparison]::OrdinalIgnoreCase) -or
        -not (Test-Path -LiteralPath $source -PathType Leaf)) {
        throw "Kit source file is missing or outside the project: $SourceRelative"
    }
    $target = [IO.Path]::GetFullPath((Join-Path $destinationRoot $DestinationRelative))
    if (-not $target.StartsWith($destinationPrefix, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Kit destination escapes the output folder: $DestinationRelative"
    }
    $parent = Split-Path -Parent $target
    [IO.Directory]::CreateDirectory($parent) | Out-Null
    Copy-Item -LiteralPath $source -Destination $target -Force
}

function Copy-KitDirectory {
    param(
        [Parameter(Mandatory = $true)][string]$SourceRelative,
        [Parameter(Mandatory = $true)][string]$DestinationRelative
    )

    $sourceDirectory = [IO.Path]::GetFullPath((Join-Path $projectRoot $SourceRelative))
    if (-not (Test-Path -LiteralPath $sourceDirectory -PathType Container)) {
        throw "Kit source directory is missing: $SourceRelative"
    }
    foreach ($file in Get-ChildItem -LiteralPath $sourceDirectory -Recurse -File) {
        $relative = $file.FullName.Substring($sourceDirectory.Length).TrimStart([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
        Copy-KitFile (Join-Path $SourceRelative $relative) (Join-Path $DestinationRelative $relative)
    }
}

$rootFiles = @(
    "LICENSE",
    "TRADEMARKS.md",
    "THIRD_PARTY_NOTICES.md",
    "VERSION.txt",
    "PLAYER_QUICKSTART_TH.md",
    "SHYNE_STANDARD_2_TH.md",
    "CREATOR_QUICKSTART_TH.md",
    "AVATAR_SYSTEM.md",
    "BLOCKBENCH_ANIMATION_STANDARD.md",
    "CREATOR_SDK_TH.md",
    "SHYNE_LUA_API_TH.md",
    "CUSTOM_RENDER_API_TH.md",
    "RIG_API_TH.md"
)
foreach ($file in $rootFiles) { Copy-KitFile $file }

Copy-KitFile "tools\kit\README_INSTALL_TH.md" "README_INSTALL_TH.md"
Copy-KitFile "tools\blockbench\shyne_standard_2.js"
Copy-KitFile "tools\blockbench\README_TH.md"
Copy-KitFile "tools\creator\shyne_creator.py"
Copy-KitFile "tools\creator\shyne-creator.ps1"
Copy-KitFile "tools\convert_figura_avatar.ps1"
Copy-KitFile "common\src\main\resources\shyne_sdk\schemas\avatar.schema.json" "schema\avatar.schema.json"
Copy-KitFile "common\src\main\resources\shyne_sdk\schemas\avatar_synced.schema.json" "schema\avatar_synced.schema.json"
Copy-KitDirectory "tools\examples\zero-lua-avatar" "examples\zero-lua-avatar"

$kitInfo = @(
    "Shyne Creator Kit",
    "Version: $version",
    "Minecraft: $minecraftVersion",
    "Avatar Standard: 2.0",
    "Runtime: Shyne native; no Figura runtime",
    "Mod JARs are distributed separately."
)
[IO.File]::WriteAllLines((Join-Path $destinationRoot "KIT_INFO.txt"), $kitInfo, [Text.UTF8Encoding]::new($false))

$forbidden = @(Get-ChildItem -LiteralPath $destinationRoot -Recurse -Force | Where-Object {
    $_.Name -eq "__pycache__" -or $_.Extension -in @(".pyc", ".class", ".jar") -or $_.FullName -match "lua-api-1[.]1-avatar"
})
if ($forbidden.Count -gt 0) {
    throw "Forbidden generated, binary, or legacy files entered the Kit: $($forbidden.FullName -join ', ')"
}

$hashLines = @(Get-ChildItem -LiteralPath $destinationRoot -Recurse -File |
    Where-Object { $_.Name -ne "SHA256SUMS.txt" } |
    Sort-Object FullName |
    ForEach-Object {
        $relative = $_.FullName.Substring($destinationRoot.Length + 1).Replace([IO.Path]::DirectorySeparatorChar, "/")
        "{0}  {1}" -f (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant(), $relative
    })
[IO.File]::WriteAllLines((Join-Path $destinationRoot "SHA256SUMS.txt"), $hashLines, [Text.UTF8Encoding]::new($false))

Write-Host "Created Shyne Creator Kit $version"
Write-Host "Destination: $destinationRoot"
Write-Host "Files: $((Get-ChildItem -LiteralPath $destinationRoot -Recurse -File).Count)"
