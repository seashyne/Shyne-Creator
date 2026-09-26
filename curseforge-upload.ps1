<#
.SYNOPSIS
    Upload Shyne Creator mod to CurseForge via the Upload API.

.DESCRIPTION
    Uploads Fabric and NeoForge JARs for the current mod version to CurseForge project 1084611.
    - Fetches game version IDs automatically from the CurseForge API.
    - Supports building before upload with -Build switch.
    - Reads changelog from CHANGELOG.md or inline via -Changelog parameter.

.EXAMPLE
    # Upload current version (auto-detect JARs from build output)
    .\curseforge-upload.ps1

    # Build first, then upload
    .\curseforge-upload.ps1 -Build

    # Upload with custom changelog
    .\curseforge-upload.ps1 -Changelog "Fixed crash on startup"

    # Dry-run (show what would be uploaded without actually uploading)
    .\curseforge-upload.ps1 -DryRun
#>

[CmdletBinding()]
param(
    # CurseForge project ID
    [int]$ProjectId = 1608411,

    # CurseForge API token
    [string]$ApiToken = "65df2957-4715-48ff-a7ee-17f52a9eabba",

    # Release type: alpha, beta, or release (default: release)
    [ValidateSet("alpha", "beta", "release")]
    [string]$ReleaseType = "release",

    # Changelog text. If not provided, looks for CHANGELOG.md in the project root.
    [string]$Changelog = "",

    # Changelog format: text, html, or markdown
    [ValidateSet("text", "html", "markdown")]
    [string]$ChangelogType = "markdown",

    # Run gradle build before uploading
    [switch]$Build,

    # Show what would happen without actually uploading
    [switch]$DryRun,

    # Minecraft version override (e.g., "1.21.4"). Auto-detected from gradle.properties if not set.
    [string]$MinecraftVersion = "",

    # Skip the NeoForge JAR upload (upload Fabric only)
    [switch]$FabricOnly,

    # Skip the Fabric JAR upload (upload NeoForge only)
    [switch]$NeoForgeOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# --- Paths ----------------------------------------------------------------
$ScriptDir = $PSScriptRoot
$GradleProps = Join-Path $ScriptDir "gradle.properties"
$BaseUrl = "https://minecraft.curseforge.com"

# --- Helpers --------------------------------------------------------------

function Write-Step([string]$msg)  { Write-Host "[>] $msg" -ForegroundColor Cyan }
function Write-Ok([string]$msg)    { Write-Host "[OK] $msg" -ForegroundColor Green }
function Write-Warn([string]$msg)  { Write-Host "[!] $msg" -ForegroundColor Yellow }
function Write-Err([string]$msg)   { Write-Host "[X] $msg" -ForegroundColor Red }

function Read-GradleProp([string]$key) {
    $line = Get-Content $GradleProps | Where-Object { $_ -match "^\s*$key\s*=" }
    if ($line) {
        return ($line -split "=", 2)[1].Trim()
    }
    return $null
}

# --- Read Project Properties ----------------------------------------------

Write-Step "Reading gradle.properties..."

$ModVersion = Read-GradleProp "mod_version"
$ModName    = Read-GradleProp "mod_name"
$ModId      = Read-GradleProp "mod_id"

if (-not $ModVersion) {
    Write-Err "Could not read mod_version from gradle.properties"
    exit 1
}

Write-Ok "Mod: $ModName v$ModVersion (id: $ModId)"

# Auto-detect release type from version string if not specified
if (-not $ReleaseType) {
    if ($ModVersion -match "alpha") {
        $ReleaseType = "alpha"
    } elseif ($ModVersion -match "beta") {
        $ReleaseType = "beta"
    } else {
        $ReleaseType = "release"
    }
    Write-Ok "Auto-detected release type: $ReleaseType"
}

# --- Resolve Minecraft Version --------------------------------------------

if (-not $MinecraftVersion) {
    $mcVersionRaw = Read-GradleProp "minecraft_version"
    if ($mcVersionRaw) {
        $MinecraftVersion = $mcVersionRaw
    } else {
        Write-Err "Could not determine Minecraft version. Use -MinecraftVersion parameter."
        exit 1
    }
}

Write-Ok "Minecraft version: $MinecraftVersion"

# --- Build (optional) -----------------------------------------------------

if ($Build) {
    Write-Step "Building mod (gradle releaseBundle)..."
    $gradlew = Join-Path $ScriptDir "gradlew.bat"
    & $gradlew releaseBundle -x test
    if ($LASTEXITCODE -ne 0) {
        Write-Err "Build failed with exit code $LASTEXITCODE"
        exit 1
    }
    Write-Ok "Build completed successfully"
}

# --- Locate JAR Files -----------------------------------------------------

Write-Step "Locating JAR files for v$ModVersion..."

$FabricLibs   = Join-Path $ScriptDir "fabric\build\libs"
$NeoForgeLibs = Join-Path $ScriptDir "neoforge\build\libs"

$FabricJar = $null
$NeoForgeJar = $null

if (-not $NeoForgeOnly) {
    $FabricJar = Get-ChildItem $FabricLibs -Filter "*-fabric-${ModVersion}.jar" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike "*-sources*" } |
        Select-Object -First 1

    if (-not $FabricJar) {
        $releasesDir = Join-Path $ScriptDir "build\releases"
        if (Test-Path $releasesDir) {
            $FabricJar = Get-ChildItem $releasesDir -Filter "*-fabric-${ModVersion}.jar" -ErrorAction SilentlyContinue |
                Select-Object -First 1
        }
    }
}

if (-not $FabricOnly) {
    $NeoForgeJar = Get-ChildItem $NeoForgeLibs -Filter "*-neoforge-${ModVersion}.jar" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike "*-sources*" } |
        Select-Object -First 1

    if (-not $NeoForgeJar) {
        $releasesDir = Join-Path $ScriptDir "build\releases"
        if (Test-Path $releasesDir) {
            $NeoForgeJar = Get-ChildItem $releasesDir -Filter "*-neoforge-${ModVersion}.jar" -ErrorAction SilentlyContinue |
                Select-Object -First 1
        }
    }
}

$jarsToUpload = @()

if ($FabricJar -and -not $NeoForgeOnly) {
    $jarsToUpload += @{ Loader = "Fabric"; File = $FabricJar; GameVersionName = "Fabric" }
    Write-Ok "Fabric JAR:   $($FabricJar.Name) ($([math]::Round($FabricJar.Length / 1MB, 2)) MB)"
}

if ($NeoForgeJar -and -not $FabricOnly) {
    $jarsToUpload += @{ Loader = "NeoForge"; File = $NeoForgeJar; GameVersionName = "NeoForge" }
    Write-Ok "NeoForge JAR: $($NeoForgeJar.Name) ($([math]::Round($NeoForgeJar.Length / 1MB, 2)) MB)"
}

if ($jarsToUpload.Count -eq 0) {
    Write-Err "No JAR files found for version $ModVersion!"
    Write-Err "Run with -Build to build first, or check that the JARs exist in fabric/build/libs and neoforge/build/libs"
    exit 1
}

# --- Resolve Changelog ----------------------------------------------------

if (-not $Changelog) {
    $changelogFile = Join-Path $ScriptDir "CHANGELOG.md"
    if (Test-Path $changelogFile) {
        $Changelog = Get-Content $changelogFile -Raw -Encoding UTF8
        Write-Ok "Loaded changelog from CHANGELOG.md"
    } else {
        $Changelog = "$ModName v$ModVersion"
        $ChangelogType = "text"
        Write-Warn "No CHANGELOG.md found, using default changelog: '$Changelog'"
    }
}

# --- Fetch Game Versions from CurseForge API ------------------------------

Write-Step "Fetching game versions from CurseForge API..."

$headers = @{
    "X-Api-Token" = $ApiToken
}

try {
    $gameVersions = Invoke-RestMethod -Uri "$BaseUrl/api/game/versions" -Headers $headers -Method Get
} catch {
    Write-Err "Failed to fetch game versions: $_"
    Write-Host "  Make sure your API token is valid."
    Write-Host "  Generate one at: https://authors-old.curseforge.com/account/api-tokens"
    exit 1
}

# Find the matching Minecraft version ID
$mcVersionEntry = $gameVersions | Where-Object { $_.name -eq $MinecraftVersion } | Select-Object -First 1

if (-not $mcVersionEntry) {
    $mcVersionEntry = $gameVersions | Where-Object { $_.name -like "*$MinecraftVersion*" } | Select-Object -First 1
}

if (-not $mcVersionEntry) {
    Write-Err "Could not find Minecraft version '$MinecraftVersion' in CurseForge game versions."
    Write-Host ""
    Write-Host "Available versions (last 30):" -ForegroundColor Yellow
    $gameVersions | Sort-Object -Property id -Descending | Select-Object -First 30 | ForEach-Object {
        Write-Host "  ID: $($_.id)  Name: $($_.name)  Slug: $($_.slug)"
    }
    exit 1
}

$mcVersionId = $mcVersionEntry.id
Write-Ok "Minecraft version ID: $mcVersionId ($($mcVersionEntry.name))"

# Try to find loader-specific version IDs (Fabric, NeoForge)
$fabricVersionEntry   = $gameVersions | Where-Object { $_.name -eq "Fabric" }
$neoForgeVersionEntry = $gameVersions | Where-Object { $_.name -eq "NeoForge" }

# Also look for Java version
$javaVersion = Read-GradleProp "java_version"
$javaVersionEntry = $null
if ($javaVersion) {
    $javaVersionEntry = $gameVersions | Where-Object { $_.name -eq "Java $javaVersion" }
}

# Environment versions (CurseForge requires at least one: Client and/or Server)
$clientVersionEntry = $gameVersions | Where-Object { $_.name -eq "Client" }
$serverVersionEntry = $gameVersions | Where-Object { $_.name -eq "Server" }

# --- Upload Function ------------------------------------------------------

function Upload-ToCurseForge {
    param(
        [string]$LoaderName,
        [System.IO.FileInfo]$JarFile,
        [string]$GameVersionName,
        [int]$ParentFileId = 0
    )

    # Build game version IDs list
    $versionIds = @($mcVersionId)

    if ($LoaderName -eq "Fabric" -and $fabricVersionEntry) {
        $versionIds += $fabricVersionEntry.id
    } elseif ($LoaderName -eq "NeoForge" -and $neoForgeVersionEntry) {
        $versionIds += $neoForgeVersionEntry.id
    }

    if ($javaVersionEntry) {
        $versionIds += $javaVersionEntry.id
    }

    if ($clientVersionEntry) {
        $versionIds += $clientVersionEntry.id
    }

    if ($serverVersionEntry) {
        $versionIds += $serverVersionEntry.id
    }

    # Build metadata
    $metadata = @{
        changelog     = $Changelog
        changelogType = $ChangelogType
        displayName   = "$ModName v$ModVersion ($LoaderName)"
        releaseType   = $ReleaseType
        gameVersions  = $versionIds
        relations     = @{
            projects = @(
                @{
                    slug = "simple-voice-chat"
                    type = "optionalDependency"
                }
            )
        }
    }

    if ($ParentFileId -gt 0) {
        $metadata.Remove("gameVersions")
        $metadata["parentFileID"] = $ParentFileId
    }

    $metadataJson = $metadata | ConvertTo-Json -Depth 5 -Compress

    Write-Step "Uploading $LoaderName JAR: $($JarFile.Name)..."
    Write-Host "  Project ID:    $ProjectId"
    Write-Host "  Release Type:  $ReleaseType"
    Write-Host "  Game Versions: $($versionIds -join ', ')"
    Write-Host "  Display Name:  $($metadata.displayName)"

    if ($DryRun) {
        Write-Warn "[DRY RUN] Would upload $($JarFile.Name)"
        Write-Host "  Metadata: $metadataJson"
        return 0
    }

    $uploadUrl = "$BaseUrl/api/projects/$ProjectId/upload-file"

    try {
        # Build multipart form data
        $boundary = [System.Guid]::NewGuid().ToString()
        $LF = "`r`n"

        $bodyLines = @(
            "--$boundary",
            "Content-Disposition: form-data; name=`"metadata`"$LF",
            $metadataJson,
            "--$boundary",
            "Content-Disposition: form-data; name=`"file`"; filename=`"$($JarFile.Name)`"",
            "Content-Type: application/java-archive$LF"
        )

        $bodyStart = ($bodyLines -join $LF) + $LF
        $bodyEnd = "$LF--$boundary--$LF"

        $startBytes = [System.Text.Encoding]::UTF8.GetBytes($bodyStart)
        $fileBytes  = [System.IO.File]::ReadAllBytes($JarFile.FullName)
        $endBytes   = [System.Text.Encoding]::UTF8.GetBytes($bodyEnd)

        $bodyStream = New-Object System.IO.MemoryStream
        $bodyStream.Write($startBytes, 0, $startBytes.Length)
        $bodyStream.Write($fileBytes, 0, $fileBytes.Length)
        $bodyStream.Write($endBytes, 0, $endBytes.Length)

        $bodyArray = $bodyStream.ToArray()
        $bodyStream.Dispose()

        $response = Invoke-RestMethod -Uri $uploadUrl `
            -Method Post `
            -Headers @{ "X-Api-Token" = $ApiToken } `
            -ContentType "multipart/form-data; boundary=$boundary" `
            -Body $bodyArray

        $fileId = $response.id
        Write-Ok "$LoaderName upload successful! File ID: $fileId"
        return $fileId

    } catch {
        Write-Err "$LoaderName upload failed: $_"
        if ($_.Exception -and $_.Exception.PSObject.Properties['Response'] -and $_.Exception.Response) {
            try {
                $statusCode = $_.Exception.Response.StatusCode.value__
                Write-Host "  Status:   $statusCode" -ForegroundColor Red
            } catch {}
            try {
                $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $errorBody = $reader.ReadToEnd()
                $reader.Dispose()
                Write-Host "  Response: $errorBody" -ForegroundColor Red
            } catch {}
        }
        Write-Host ""
        return 0

        if ($statusCode -eq 403) {
            Write-Warn "403 Forbidden -- check that your API token is valid and you have upload permissions for project $ProjectId"
        } elseif ($statusCode -eq 404) {
            Write-Warn "404 Not Found -- check that Project ID $ProjectId is correct"
        }

        return -1
    }
}

# --- Execute Uploads ------------------------------------------------------

Write-Host ""
Write-Host "================================================================" -ForegroundColor Magenta
Write-Host "  Shyne Creator -- CurseForge Upload" -ForegroundColor Magenta
Write-Host "  Version: $ModVersion | Release: $ReleaseType | MC: $MinecraftVersion" -ForegroundColor Magenta
Write-Host "================================================================" -ForegroundColor Magenta
Write-Host ""

$results = @()

foreach ($jar in $jarsToUpload) {
    $fileId = Upload-ToCurseForge `
        -LoaderName $jar.Loader `
        -JarFile $jar.File `
        -GameVersionName $jar.GameVersionName `
        -ParentFileId 0

    $results += @{
        Loader = $jar.Loader
        FileId = $fileId
        File   = $jar.File.Name
    }

    Write-Host ""
}

# --- Summary --------------------------------------------------------------

Write-Host ""
Write-Host "================================================================" -ForegroundColor Magenta
Write-Host "  Upload Summary" -ForegroundColor Magenta
Write-Host "================================================================" -ForegroundColor Magenta

$allSuccess = $true
foreach ($r in $results) {
    if ($r.FileId -gt 0) {
        Write-Ok "$($r.Loader): $($r.File) -> File ID $($r.FileId)"
    } elseif ($r.FileId -eq 0 -and $DryRun) {
        Write-Warn "$($r.Loader): $($r.File) -> [DRY RUN - not uploaded]"
    } else {
        Write-Err "$($r.Loader): $($r.File) -> FAILED"
        $allSuccess = $false
    }
}

Write-Host ""

if ($DryRun) {
    Write-Warn "Dry run complete. No files were uploaded."
    Write-Host "  Remove -DryRun to actually upload."
} elseif ($allSuccess) {
    Write-Ok "All uploads completed successfully!"
    Write-Host "  View your project: https://legacy.curseforge.com/minecraft/mc-mods/shyne-creator"
} else {
    Write-Err "Some uploads failed. Check the errors above."
    exit 1
}
