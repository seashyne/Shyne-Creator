param(
    [Parameter(Mandatory = $true)]
    [string]$Source,

    [Parameter(Mandatory = $true)]
    [string]$Destination,

    [string]$Id = "",
    [string]$Name = "",

    [ValidateSet("Auto", "Generic", "Merling")]
    [string]$Profile = "Auto"
)

$ErrorActionPreference = "Stop"
$sourceRoot = (Resolve-Path -LiteralPath $Source).Path
$modelPath = Join-Path $sourceRoot "model.bbmodel"
$figuraManifestPath = Join-Path $sourceRoot "avatar.json"

if (-not (Test-Path -LiteralPath $modelPath -PathType Leaf)) {
    $modelCandidates = @(Get-ChildItem -LiteralPath $sourceRoot -Filter "*.bbmodel" -File)
    if ($modelCandidates.Count -ne 1) {
        throw "Expected model.bbmodel or exactly one .bbmodel file in $sourceRoot"
    }
    $modelPath = $modelCandidates[0].FullName
}

$folderName = Split-Path -Leaf $Destination
if ([string]::IsNullOrWhiteSpace($Id)) {
    $Id = ($folderName.ToLowerInvariant() -replace '[^a-z0-9_.-]', '_').Trim('_')
}
if ($Id -notmatch '^[a-z0-9][a-z0-9_.-]{0,63}$') {
    throw "Avatar id '$Id' is invalid"
}

$figuraManifest = $null
if (Test-Path -LiteralPath $figuraManifestPath -PathType Leaf) {
    $figuraManifest = Get-Content -LiteralPath $figuraManifestPath -Raw | ConvertFrom-Json
}
if ([string]::IsNullOrWhiteSpace($Name)) {
    $Name = if ($figuraManifest -and $figuraManifest.name) { [string]$figuraManifest.name } else { $folderName }
}

$destinationRoot = [IO.Path]::GetFullPath($Destination)
$textureRoot = Join-Path $destinationRoot "textures"
[IO.Directory]::CreateDirectory($textureRoot) | Out-Null

$model = Get-Content -LiteralPath $modelPath -Raw | ConvertFrom-Json
$animationNames = @($model.animations | ForEach-Object { [string]$_.name })
$modelText = $model | ConvertTo-Json -Depth 100 -Compress
$looksLikeMerling = @("swim", "stand", "small", "smallSwim", "Tail1", "Tail2", "Tail3", "Tail4") |
    ForEach-Object { $modelText -match ('"' + [Regex]::Escape($_) + '"') } |
    Where-Object { -not $_ } |
    Measure-Object |
    Select-Object -ExpandProperty Count
$looksLikeMerling = $looksLikeMerling -eq 0
$useMerlingProfile = $Profile -eq "Merling" -or ($Profile -eq "Auto" -and $looksLikeMerling)
$copiedTextures = @()
foreach ($texture in @($model.textures)) {
    $textureName = [IO.Path]::GetFileName([string]$texture.name)
    if ([string]::IsNullOrWhiteSpace($textureName)) {
        $textureName = "texture_$($texture.id).png"
    }

    $candidate = Join-Path $sourceRoot $textureName
    if ($texture.relative_path) {
        $relativeCandidate = Join-Path $sourceRoot ([string]$texture.relative_path -replace '/', [IO.Path]::DirectorySeparatorChar)
        if (Test-Path -LiteralPath $relativeCandidate -PathType Leaf) { $candidate = $relativeCandidate }
    }
    if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
        $matches = @(Get-ChildItem -LiteralPath $sourceRoot -Recurse -File | Where-Object Name -EQ $textureName)
        if ($matches.Count -eq 1) { $candidate = $matches[0].FullName }
    }
    if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
        throw "Texture '$textureName' was not found inside the Figura avatar"
    }

    $textureId = if ($null -ne $texture.id) { [string]$texture.id } else { [string]$copiedTextures.Count }
    $safeName = (([IO.Path]::GetFileNameWithoutExtension($textureName) -replace '[^a-zA-Z0-9_.-]', '_') + "_" + $textureId + [IO.Path]::GetExtension($textureName))
    Copy-Item -LiteralPath $candidate -Destination (Join-Path $textureRoot $safeName) -Force
    $texture.relative_path = "textures/$safeName"
    $texture.PSObject.Properties.Remove("source")
    $copiedTextures += "textures/$safeName"
}

# Blockbench reference images can contain megabytes of unrelated embedded data.
# They are editor-only and are not consumed by Shyne's renderer.
$model.PSObject.Properties.Remove("reference_images")
$model.PSObject.Properties.Remove("backgrounds")
$model.PSObject.Properties.Remove("history")

$modelJson = $model | ConvertTo-Json -Depth 100
[IO.File]::WriteAllText((Join-Path $destinationRoot "model.bbmodel"), $modelJson, [Text.UTF8Encoding]::new($false))

$description = if ($figuraManifest -and $figuraManifest.description) { [string]$figuraManifest.description } else { "" }
$manifest = [ordered]@{
    api_version = 1
    id = $Id
    name = $Name
    version = if ($useMerlingProfile) { "1.2.0-blockbench-engine" } else { "1.0.0-shyne" }
    main = "script.lua"
    model = "model.bbmodel"
    replace_vanilla = $true
    online_sync = $true
    description = $description
    first_person_masking = $true
    local_camera = $true
    texture_sync_mode = "manifest"
    textures = @($copiedTextures)
    permissions = @("camera")
}
$manifestJson = $manifest | ConvertTo-Json -Depth 10
[IO.File]::WriteAllText((Join-Path $destinationRoot "avatar.json"), $manifestJson, [Text.UTF8Encoding]::new($false))

$script = @'
-- Generated native Shyne Avatar API Standard 1.0 entry script.
avatar.hide_vanilla(true)
avatar.camera.configure({ local_only = true, first_person_masking = true, hide_head = true })
avatar.texture.sync("manifest")
avatar.network.online(true)

local current_locomotion = nil

local function first_animation(names)
  for _, name in ipairs(names) do
    if model.animation.exists(name) then return name end
  end
  return nil
end

local function use_locomotion(name)
  if name == current_locomotion then return end
  if current_locomotion then
    model.animation.get(current_locomotion):fade_out(5):stop()
  end
  current_locomotion = name
  if name then
    model.animation.get(name):loop(true):fade_in(5):fade_out(5):priority(0):play()
  end
end

local function play_one_shot(names, priority)
  local name = first_animation(names)
  if name then model.animation.get(name):loop(false):fade_in(2):fade_out(4):priority(priority or 20):play() end
end

events.on("entity_init", function()
  model.root:visible(true)
end)

events.on("tick", function()
  local next_animation
  if minecraft.player.sleeping() then
    next_animation = first_animation({ "sleep", "sleeping" })
  elseif minecraft.player.in_water() or minecraft.player.swimming() then
    next_animation = first_animation({ "swim", "swimming", "mermaid_swim" })
  elseif minecraft.player.crouching() then
    next_animation = first_animation({ "crawl", "crouch", "crouching" })
  else
    local velocity = minecraft.player.velocity()
    local moving = math.abs(velocity.x) + math.abs(velocity.z) > 0.015
    if moving and minecraft.player.sprinting() then
      next_animation = first_animation({ "sprint", "run", "walk" })
    elseif moving then
      next_animation = first_animation({ "walk", "swim", "move" })
    else
      next_animation = first_animation({ "idle", "stand", "standing" })
    end
  end
  use_locomotion(next_animation)
end)

ui.action({ id = "twirl", title = "Twirl", icon = "spark", on_use = function() play_one_shot({ "twirl", "spin" }, 30) end })
ui.action({ id = "sing", title = "Sing", icon = "star", on_use = function() play_one_shot({ "sing", "song" }, 30) end })
input.bind("twirl", { title = "Twirl", key = input.key.r, on_press = function() play_one_shot({ "twirl", "spin" }, 30) end })
'@
if ($useMerlingProfile) {
    # Merling models need form/animation coordination; the generic starter
    # otherwise plays the large-tail stand animation while the legs are visible.
    $merlingTemplate = Join-Path $PSScriptRoot "templates\merling_native.lua"
    $script = Get-Content -LiteralPath $merlingTemplate -Raw
}
[IO.File]::WriteAllText((Join-Path $destinationRoot "script.lua"), $script, [Text.UTF8Encoding]::new($false))

foreach ($scriptFolder in @("lib", "scripts")) {
    $sourceFolder = Join-Path $sourceRoot $scriptFolder
    if (Test-Path -LiteralPath $sourceFolder -PathType Container) {
        Copy-Item -LiteralPath $sourceFolder -Destination $destinationRoot -Recurse -Force
    }
}

Write-Output "Converted '$Name' as '$Id'"
Write-Output "Destination: $destinationRoot"
Write-Output "Textures: $($copiedTextures -join ', ')"
Write-Output "Native profile: $(if ($useMerlingProfile) { 'Merling' } else { 'Generic' })"
Write-Output "A native locomotion, emote, input, and multiplayer starter script was generated."
Write-Warning "Preserved source behavior modules are reference material and are not executed automatically."
