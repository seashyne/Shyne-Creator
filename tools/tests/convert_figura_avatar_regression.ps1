param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw "Regression check failed: $Message" }
}

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..")).Path
$converter = (Resolve-Path -LiteralPath (Join-Path $repoRoot "tools\convert_figura_avatar.ps1")).Path
$testRoot = Join-Path $repoRoot ("build\tool-tests\converter-" + [Guid]::NewGuid().ToString("N"))
$source = Join-Path $testRoot "source"
$output = Join-Path $testRoot "output"
$emptyOutput = Join-Path $testRoot "empty-output"
$occupiedOutput = Join-Path $testRoot "occupied-output"
$rigSource = Join-Path $testRoot "rig-source"
$rigOutput = Join-Path $testRoot "rig-output"

[IO.Directory]::CreateDirectory($source) | Out-Null
[IO.Directory]::CreateDirectory($emptyOutput) | Out-Null
[IO.Directory]::CreateDirectory($occupiedOutput) | Out-Null
[IO.Directory]::CreateDirectory($rigSource) | Out-Null

$pngBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScL3WQAAAABJRU5ErkJggg=="
$pngBytes = [Convert]::FromBase64String($pngBase64)

$manifest = [ordered]@{
    name = "Converter Regression"
    description = "Synthetic semantic-converter fixture"
    authors = @("Shyne")
}
$model = [ordered]@{
    meta = [ordered]@{ format_version = "4.10"; model_format = "free" }
    resolution = [ordered]@{ width = 1; height = 1 }
    textures = @([ordered]@{
        name = "Tail.png"
        relative_path = "Tail.png"
        source = "data:image/png;base64,$pngBase64"
    })
    elements = @()
    outliner = @()
    animations = @([ordered]@{
        name = "Idle"
        loop = "loop"
        length = 1
        animators = [ordered]@{}
    })
}

[IO.File]::WriteAllText((Join-Path $source "avatar.json"), ($manifest | ConvertTo-Json -Depth 10), [Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $source "model.bbmodel"), ($model | ConvertTo-Json -Depth 20), [Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $source "script.lua"), "error('must not be copied')", [Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllBytes((Join-Path $source "avatar.png"), $pngBytes)
[IO.File]::WriteAllBytes((Join-Path $source "Tail Alt.png"), $pngBytes)
foreach ($name in @("Tail_normal.png", "Tail_emissive.png", "Tail_specular.png", "Tail_reference.png", "Tail_mask.png", "Tail_n.png")) {
    [IO.File]::WriteAllBytes((Join-Path $source $name), $pngBytes)
}

& $converter -Source $source -Destination $output -Id "converter.regression" | Out-Null
$outputManifest = Get-Content -LiteralPath (Join-Path $output "avatar.json") -Raw | ConvertFrom-Json
$outfits = @(Get-ChildItem -LiteralPath (Join-Path $output "outfit") -File)
Assert-True ($outputManifest.standard -eq "2.0") "fresh output must use Standard 2.0"
Assert-True ($null -eq $outputManifest.PSObject.Properties["main"]) "fresh output must not declare a Lua main"
Assert-True (@(Get-ChildItem -LiteralPath $output -Recurse -Filter "*.lua" -File).Count -eq 0) "fresh output must contain no Lua files"
Assert-True ($outfits.Count -eq 1 -and $outfits[0].Name -eq "Tail_Alt.replace.png") "the real alternate texture should become an explicit replacement outfit"

& $converter -Source $source -Destination $emptyOutput -Id "converter.empty" | Out-Null
Assert-True (Test-Path -LiteralPath (Join-Path $emptyOutput "avatar.json") -PathType Leaf) "an existing empty destination must remain supported"

[IO.File]::WriteAllText((Join-Path $occupiedOutput "script.lua"), "stale", [Text.UTF8Encoding]::new($false))
$occupiedRejected = $false
try {
    & $converter -Source $source -Destination $occupiedOutput -Id "converter.occupied" | Out-Null
} catch {
    $occupiedRejected = $_.Exception.Message -match "Destination must be empty"
}
Assert-True $occupiedRejected "a non-empty destination must be rejected"
Assert-True (-not (Test-Path -LiteralPath (Join-Path $occupiedOutput "avatar.json"))) "rejection must happen before conversion writes"

$sourceHashBefore = (Get-FileHash -LiteralPath (Join-Path $source "avatar.json") -Algorithm SHA256).Hash
$samePathRejected = $false
try {
    & $converter -Source $source -Destination $source -Id "converter.same" | Out-Null
} catch {
    $samePathRejected = $_.Exception.Message -match "must be different"
}
$sourceHashAfter = (Get-FileHash -LiteralPath (Join-Path $source "avatar.json") -Algorithm SHA256).Hash
Assert-True $samePathRejected "Source and Destination must not be the same directory"
Assert-True ($sourceHashBefore -eq $sourceHashAfter) "same-path rejection must not modify source files"

$rigParts = @(
    @("Head", "head", @(0, 24, 0)),
    @("Hat", "hat", @(0, 24, 0)),
    @("Body", "body", @(0, 24, 0)),
    @("Jacket", "jacket", @(0, 24, 0)),
    @("LeftArm", "left-arm", @(-5, 22, 0)),
    @("Left Sleeve", "left-sleeve", @(-5, 22, 0)),
    @("RightArm", "right-arm", @(5, 22, 0)),
    @("Right Sleeve", "right-sleeve", @(5, 22, 0)),
    @("LeftLeg", "left-leg", @(-1.9, 12, 0)),
    @("Left Pants", "left-pants", @(-1.9, 12, 0)),
    @("RightLeg", "right-leg", @(1.9, 12, 0)),
    @("Right Pants", "right-pants", @(1.9, 12, 0))
)
$rigElements = @($rigParts | ForEach-Object {
    [ordered]@{
        type = "cube"
        name = $_[0]
        uuid = $_[1]
        origin = $_[2]
        from = @(0, 0, 0)
        to = @(1, 1, 1)
        faces = [ordered]@{}
    }
})
$rigModel = [ordered]@{
    meta = [ordered]@{ format_version = "5.0"; model_format = "free" }
    resolution = [ordered]@{ width = 64; height = 64 }
    textures = @()
    elements = $rigElements
    groups = @()
    outliner = @($rigElements | ForEach-Object { $_.uuid })
    animations = @()
}
[IO.File]::WriteAllText((Join-Path $rigSource "avatar.json"), '{"name":"Rig Fixture","replace_vanilla":true}', [Text.UTF8Encoding]::new($false))
[IO.File]::WriteAllText((Join-Path $rigSource "model.bbmodel"), ($rigModel | ConvertTo-Json -Depth 30), [Text.UTF8Encoding]::new($false))

& $converter -Source $rigSource -Destination $rigOutput -Id "converter.rig" | Out-Null
$rigManifest = Get-Content -LiteralPath (Join-Path $rigOutput "avatar.json") -Raw | ConvertFrom-Json
$convertedRig = Get-Content -LiteralPath (Join-Path $rigOutput "model.bbmodel") -Raw | ConvertFrom-Json
$rigNames = @($convertedRig.groups | ForEach-Object { [string]$_.name })
Assert-True ($rigManifest.profile -eq "full_body") "replace_vanilla source must become full_body"
Assert-True (@($convertedRig.groups).Count -eq 6) "recognized ungrouped skin cubes must receive six humanoid roots"
foreach ($name in @("Head", "Body", "LeftArm", "RightArm", "LeftLeg", "RightLeg")) {
    Assert-True ($rigNames -contains $name) "generated humanoid rig is missing $name"
}
Assert-True (@($convertedRig.groups | Where-Object { $null -ne $_.PSObject.Properties["parent_type"] }).Count -eq 0) "full-body pose roots must not use parent_type"
$body = $convertedRig.groups | Where-Object { $_.name -eq "Body" } | Select-Object -First 1
Assert-True ($body.origin[0] -eq 0 -and $body.origin[1] -eq 24 -and $body.origin[2] -eq 0) "Body pivot must be 0,24,0"
Assert-True (@($convertedRig.animations).Count -eq 0) "auto-rig must not invent authored animations"

Write-Output "PASS convert_figura_avatar regression checks"
Write-Output "Artifacts: $testRoot"
