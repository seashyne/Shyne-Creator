<#
.SYNOPSIS
Safely installs a locally built Shyne Creator JAR into one Minecraft instance.

.DESCRIPTION
The target must be the exact game directory and already contain a mods folder.
The script refuses to update the instance while its java/javaw process is
running. It validates and stages the JAR before replacing any installed Shyne
JAR, and it never stops Minecraft itself.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("fabric", "neoforge")]
    [string]$Loader,

    [Parameter(Mandatory = $true)]
    [string]$GameDir,

    [string]$Jar = ""
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
. (Join-Path $PSScriptRoot "lib\install_safety.ps1")

function Assert-ShyneJarValid {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][ValidateSet("fabric", "neoforge")][string]$ExpectedLoader
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Shyne JAR does not exist: $Path"
    }
    if ((Get-Item -LiteralPath $Path).Length -le 0) {
        throw "Shyne JAR is empty: $Path"
    }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = $null
    try {
        $archive = [IO.Compression.ZipFile]::OpenRead($Path)
        $entryNames = @($archive.Entries | ForEach-Object { $_.FullName })
        $requiredMetadata = if ($ExpectedLoader -eq "fabric") {
            "fabric.mod.json"
        } else {
            "META-INF/neoforge.mods.toml"
        }
        if ($requiredMetadata -notin $entryNames) {
            throw "JAR is not a Shyne $ExpectedLoader build: missing $requiredMetadata"
        }

        $buffer = [byte[]]::new(81920)
        foreach ($entry in $archive.Entries) {
            if ($entry.FullName.EndsWith("/")) {
                continue
            }
            $stream = $null
            try {
                $stream = $entry.Open()
                while ($stream.Read($buffer, 0, $buffer.Length) -gt 0) {
                    # Reading every entry detects truncated or invalid local ZIP records.
                }
            } finally {
                if ($null -ne $stream) {
                    $stream.Dispose()
                }
            }
        }
    } catch {
        throw "Invalid Shyne JAR '$Path'. $($_.Exception.Message)"
    } finally {
        if ($null -ne $archive) {
            $archive.Dispose()
        }
    }
}

function Resolve-ShyneSourceJar {
    param(
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$RequestedJar,
        [Parameter(Mandatory = $true)][ValidateSet("fabric", "neoforge")][string]$ExpectedLoader
    )

    if ([string]::IsNullOrWhiteSpace($RequestedJar)) {
        $version = (Get-Content -LiteralPath (Join-Path $projectRoot "VERSION.txt") -Raw).Trim()
        $RequestedJar = Join-Path $projectRoot (
            "$ExpectedLoader\build\libs\shyne-creator-$ExpectedLoader-$version.jar"
        )
    }

    try {
        $resolved = (Resolve-Path -LiteralPath $RequestedJar -ErrorAction Stop).Path
    } catch {
        throw "Built Shyne JAR was not found: $RequestedJar. Build it first, or pass -Jar."
    }

    $expectedName = "^shyne-creator-$([regex]::Escape($ExpectedLoader))-.+[.]jar$"
    $fileName = [IO.Path]::GetFileName($resolved)
    if ($fileName -notmatch $expectedName -or $fileName -match "-sources[.]jar$") {
        throw "Expected a non-sources Shyne $ExpectedLoader JAR, got: $fileName"
    }
    return $resolved
}

try {
    $resolvedGameDir = (Resolve-Path -LiteralPath $GameDir -ErrorAction Stop).Path
} catch {
    throw "GameDir does not exist. Pass the exact Minecraft instance root: $GameDir"
}
if (-not (Test-Path -LiteralPath $resolvedGameDir -PathType Container)) {
    throw "GameDir is not a directory: $resolvedGameDir"
}

$modsDirectory = Join-Path $resolvedGameDir "mods"
if (-not (Test-Path -LiteralPath $modsDirectory -PathType Container)) {
    throw (
        "GameDir must be the exact Minecraft instance root and must already contain " +
        "a mods folder: $resolvedGameDir"
    )
}

$sourceJar = Resolve-ShyneSourceJar -RequestedJar $Jar -ExpectedLoader $Loader
$sourceParent = [IO.Path]::GetDirectoryName($sourceJar)
if ([string]::Equals(
    $sourceParent.TrimEnd("\", "/"),
    $modsDirectory.TrimEnd("\", "/"),
    [StringComparison]::OrdinalIgnoreCase
)) {
    throw "Source JAR must be outside the target instance mods folder: $sourceJar"
}

Assert-ShyneJarValid -Path $sourceJar -ExpectedLoader $Loader
Assert-ShyneInstallNotRunning `
    -GameDir $resolvedGameDir `
    -Processes @(Get-ShyneJavaProcessSnapshot)

$oppositeLoader = if ($Loader -eq "fabric") { "neoforge" } else { "fabric" }
$oppositeJars = @(Get-ChildItem -LiteralPath $modsDirectory -File |
    Where-Object { $_.Name -match "^shyne-creator-$oppositeLoader-.+[.]jar$" })
if ($oppositeJars.Count -gt 0) {
    throw (
        "The target instance contains a Shyne $oppositeLoader JAR: {0}. " +
        "Remove the wrong-loader JAR before installing the $Loader build."
    ) -f ($oppositeJars.Name -join ", ")
}

$installedJars = @(Get-ChildItem -LiteralPath $modsDirectory -File |
    Where-Object {
        $_.Name -match "^shyne-creator-$Loader-.+[.]jar$" -and
        $_.Name -notmatch "-sources[.]jar$"
    })
$destinationJar = Join-Path $modsDirectory ([IO.Path]::GetFileName($sourceJar))
$transactionId = [Guid]::NewGuid().ToString("N")
$stagedJar = Join-Path $modsDirectory (
    ".$([IO.Path]::GetFileName($sourceJar)).$transactionId.installing"
)
$renamedJars = [Collections.Generic.List[object]]::new()
$committed = $false

try {
    Copy-Item -LiteralPath $sourceJar -Destination $stagedJar
    $sourceHash = (Get-FileHash -LiteralPath $sourceJar -Algorithm SHA256).Hash
    $stagedHash = (Get-FileHash -LiteralPath $stagedJar -Algorithm SHA256).Hash
    if ($sourceHash -ne $stagedHash) {
        throw "Staged JAR hash does not match the build output."
    }
    Assert-ShyneJarValid -Path $stagedJar -ExpectedLoader $Loader

    # Check again immediately before changing any installed JAR.
    Assert-ShyneInstallNotRunning `
        -GameDir $resolvedGameDir `
        -Processes @(Get-ShyneJavaProcessSnapshot)

    foreach ($installedJar in $installedJars) {
        $backupPath = Join-Path $modsDirectory (
            ".$($installedJar.Name).$transactionId.backup"
        )
        Move-Item -LiteralPath $installedJar.FullName -Destination $backupPath
        $renamedJars.Add([pscustomobject]@{
            Original = $installedJar.FullName
            Backup = $backupPath
        })
    }

    Move-Item -LiteralPath $stagedJar -Destination $destinationJar
    $committed = $true
} catch {
    $installError = $_
    if (-not $committed) {
        for ($index = $renamedJars.Count - 1; $index -ge 0; $index--) {
            $renamed = $renamedJars[$index]
            if ((Test-Path -LiteralPath $renamed.Backup) -and
                -not (Test-Path -LiteralPath $renamed.Original)) {
                Move-Item -LiteralPath $renamed.Backup -Destination $renamed.Original
            }
        }
    }
    throw (
        "Local Shyne install stopped safely. The previous installed JAR was preserved. {0}" -f
        $installError.Exception.Message
    )
} finally {
    if (Test-Path -LiteralPath $stagedJar) {
        Remove-Item -LiteralPath $stagedJar -Force
    }
}

foreach ($renamed in $renamedJars) {
    if (Test-Path -LiteralPath $renamed.Backup) {
        try {
            Remove-Item -LiteralPath $renamed.Backup -Force
        } catch {
            Write-Warning "Installed successfully, but could not remove backup: $($renamed.Backup)"
        }
    }
}

Write-Output "Installed Shyne Creator ($Loader): $destinationJar"
