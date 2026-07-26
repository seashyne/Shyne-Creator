<#
.SYNOPSIS
แปลง asset จาก Figura เป็น Shyne Avatar Standard 2.0 แบบ Zero-Lua

.DESCRIPTION
ปลายทางต้องเป็น path ใหม่หรือ directory ว่างเท่านั้น เพื่อไม่ให้ script.lua
หรือ asset เก่าค้างอยู่ใน Avatar ที่แปลงแล้ว และห้ามใช้ path เดียวกับต้นทาง
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$Source,

    [Parameter(Mandatory = $true)]
    [string]$Destination,

    [string]$Id = "",
    [string]$Name = "",

    [ValidateSet("Auto", "Accessory", "FullBody", "Custom", "Merling")]
    [string]$Profile = "Auto",

    [ValidateSet("Auto", "Overlay", "Replace")]
    [string]$VanillaMode = "Auto"
)

$ErrorActionPreference = "Stop"

function Test-Property {
    param([object]$Object, [string]$Name)
    return $null -ne $Object -and $null -ne $Object.PSObject.Properties[$Name]
}

function ConvertTo-SafeName {
    param([string]$Value, [string]$Fallback = "file")
    $safe = ([IO.Path]::GetFileNameWithoutExtension($Value) -replace '[^a-zA-Z0-9_.-]', '_').Trim('_')
    if ([string]::IsNullOrWhiteSpace($safe)) { return $Fallback }
    return $safe
}

function ConvertTo-AnimationKey {
    param([string]$Value)
    return ($Value.ToLowerInvariant() -replace '[^a-z0-9]', '')
}

function Get-PngSizeKey {
    param([string]$Path)
    try {
        $bytes = [IO.File]::ReadAllBytes($Path)
        if ($bytes.Length -lt 24) { return $null }
        $signature = @(137, 80, 78, 71, 13, 10, 26, 10)
        for ($index = 0; $index -lt $signature.Count; $index++) {
            if ($bytes[$index] -ne $signature[$index]) { return $null }
        }
        [uint32]$width = ([uint32]$bytes[16] -shl 24) -bor ([uint32]$bytes[17] -shl 16) -bor ([uint32]$bytes[18] -shl 8) -bor [uint32]$bytes[19]
        [uint32]$height = ([uint32]$bytes[20] -shl 24) -bor ([uint32]$bytes[21] -shl 16) -bor ([uint32]$bytes[22] -shl 8) -bor [uint32]$bytes[23]
        if ($width -lt 1 -or $height -lt 1) { return $null }
        return "$($width)x$($height)"
    } catch {
        return $null
    }
}

function Test-LoopAnimation {
    param([object]$Animation)
    if (-not (Test-Property $Animation "loop")) { return $false }
    $value = $Animation.loop
    if ($value -is [bool]) { return [bool]$value }
    return ([string]$value).Trim().ToLowerInvariant() -eq "loop"
}

function Find-TextureSource {
    param(
        [object]$Texture,
        [string]$TextureName,
        [string]$Root,
        [System.IO.FileInfo[]]$Files
    )

    $rootPath = [IO.Path]::GetFullPath($Root).TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
    $rootPrefix = $rootPath + [IO.Path]::DirectorySeparatorChar
    $relativeValues = @()
    if (Test-Property $Texture "relative_path") { $relativeValues += [string]$Texture.relative_path }
    if (-not [string]::IsNullOrWhiteSpace($TextureName)) { $relativeValues += $TextureName }
    if ([string]::IsNullOrWhiteSpace([IO.Path]::GetExtension($TextureName))) { $relativeValues += "$TextureName.png" }

    foreach ($relative in $relativeValues) {
        if ([string]::IsNullOrWhiteSpace($relative)) { continue }
        try {
            $candidate = [IO.Path]::GetFullPath((Join-Path $Root ($relative -replace '/', [IO.Path]::DirectorySeparatorChar)))
            if ($candidate.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase) -and (Test-Path -LiteralPath $candidate -PathType Leaf)) {
                return $candidate
            }
        } catch {
            # Blockbench often stores stale absolute editor paths. Only files
            # inside the Avatar source are accepted.
        }
    }

    $leafNames = @([IO.Path]::GetFileName($TextureName))
    if ([string]::IsNullOrWhiteSpace([IO.Path]::GetExtension($TextureName))) {
        $leafNames += ([IO.Path]::GetFileName($TextureName) + ".png")
    }
    foreach ($leafName in $leafNames) {
        if ([string]::IsNullOrWhiteSpace($leafName)) { continue }
        $matches = @($Files | Where-Object { $_.Name -ieq $leafName })
        if ($matches.Count -eq 1) { return $matches[0].FullName }
    }
    return $null
}

function Add-UniqueDestinationName {
    param([string]$Directory, [string]$FileName)
    $base = ConvertTo-SafeName $FileName "outfit"
    $extension = [IO.Path]::GetExtension($FileName)
    if ([string]::IsNullOrWhiteSpace($extension)) { $extension = ".png" }
    $candidate = $base + $extension.ToLowerInvariant()
    $counter = 2
    while (Test-Path -LiteralPath (Join-Path $Directory $candidate)) {
        $candidate = "${base}_$counter$($extension.ToLowerInvariant())"
        $counter++
    }
    return $candidate
}

function Test-LikelyAuxiliaryPng {
    param([System.IO.FileInfo]$File)

    $stem = [IO.Path]::GetFileNameWithoutExtension($File.Name)
    $tokenPattern = '(?i)(^|[._\-\s])(normal|normals|nrm|n|emissive|emission|emiss|e|specular|spec|s|roughness|rough|metallic|metalness|ambient[_\-\s]?occlusion|ao|mask|masks|reference|references|ref|guide|preview|thumbnail|thumb)([._\-\s]|$)'
    if ($stem -match $tokenPattern) { return $true }

    # Also catch compact names such as TailNormal.png and TailSpecular.png.
    return $stem -match '(?i)(normal|normals|nrm|emissive|emission|emiss|specular|roughness|metallic|metalness|mask|reference|preview|thumbnail)$'
}

function ConvertTo-HumanoidPartKey {
    param([string]$Value)
    $normalized = ($Value.ToLowerInvariant() -replace '[^a-z0-9]', '')
    $result = switch ($normalized) {
        "head" { "Head" }
        "hat" { "Head" }
        "mouthopen" { "Head" }
        "body" { "Body" }
        "jacket" { "Body" }
        "leftarm" { "LeftArm" }
        "leftsleeve" { "LeftArm" }
        "rightarm" { "RightArm" }
        "rightsleeve" { "RightArm" }
        "leftleg" { "LeftLeg" }
        "leftpants" { "LeftLeg" }
        "rightleg" { "RightLeg" }
        "rightpants" { "RightLeg" }
        default { "" }
    }
    return $result
}

function Test-StandardHumanoidOrigin {
    param([string]$Part, [object]$Origin)
    if ($null -eq $Origin -or @($Origin).Count -lt 3) { return $false }
    try {
        $x = [Math]::Abs([double]$Origin[0])
        $y = [double]$Origin[1]
        $z = [double]$Origin[2]
    } catch {
        return $false
    }
    $expectedX = switch ($Part) {
        { $_ -in @("LeftArm", "RightArm") } { 5.0; break }
        { $_ -in @("LeftLeg", "RightLeg") } { 1.9; break }
        default { 0.0 }
    }
    $expectedY = switch ($Part) {
        { $_ -in @("Head", "Body") } { 24.0; break }
        { $_ -in @("LeftArm", "RightArm") } { 22.0; break }
        { $_ -in @("LeftLeg", "RightLeg") } { 12.0; break }
        default { 0.0 }
    }
    return [Math]::Abs($x - $expectedX) -le 0.25 `
        -and [Math]::Abs($y - $expectedY) -le 0.25 `
        -and [Math]::Abs($z) -le 0.25
}

function Add-StandardHumanoidRig {
    param([object]$Model, [string]$NativeProfile)

    if ($NativeProfile -ne "full_body" -or @($Model.animations).Count -gt 0) { return $false }
    if ((Test-Property $Model "groups") -and @($Model.groups).Count -gt 0) { return $false }

    $elements = @($Model.elements)
    if ($elements.Count -lt 6) { return $false }
    $outliner = @($Model.outliner)
    if ($outliner.Count -ne $elements.Count -or @($outliner | Where-Object { $_ -isnot [string] }).Count -gt 0) {
        return $false
    }

    $outlinerIds = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($entry in $outliner) { [void]$outlinerIds.Add([string]$entry) }

    $children = [ordered]@{
        Head = @(); Body = @(); LeftArm = @(); RightArm = @(); LeftLeg = @(); RightLeg = @()
    }
    $baseElements = @{}
    foreach ($element in $elements) {
        if ($null -eq $element -or ((Test-Property $element "type") -and [string]$element.type -ne "cube")) { return $false }
        $uuid = [string]$element.uuid
        $name = [string]$element.name
        $part = ConvertTo-HumanoidPartKey $name
        if ([string]::IsNullOrWhiteSpace($uuid) -or [string]::IsNullOrWhiteSpace($part) -or -not $outlinerIds.Contains($uuid)) {
            return $false
        }
        $children[$part] += $uuid
        $normalized = ($name.ToLowerInvariant() -replace '[^a-z0-9]', '')
        if ($normalized -in @("head", "body", "leftarm", "rightarm", "leftleg", "rightleg")) {
            $baseElements[$part] = $element
        }
    }
    foreach ($part in $children.Keys) {
        if (-not $baseElements.ContainsKey($part) -or -not (Test-StandardHumanoidOrigin $part $baseElements[$part].origin)) {
            return $false
        }
    }

    $groupDefinitions = @()
    $groupOutliner = @()
    $v5Groups = Test-Property $Model "groups"
    foreach ($part in $children.Keys) {
        $uuid = [Guid]::NewGuid().ToString()
        $origin = @($baseElements[$part].origin | ForEach-Object { [double]$_ })
        $definition = [ordered]@{
            name = $part
            uuid = $uuid
            origin = $origin
            rotation = @(0, 0, 0)
            visibility = $true
            export = $true
            children = @()
        }
        if ($v5Groups) {
            $groupDefinitions += [pscustomobject]$definition
            $groupOutliner += [pscustomobject][ordered]@{
                uuid = $uuid
                children = @($children[$part])
            }
        } else {
            $definition.children = @($children[$part])
            $groupOutliner += [pscustomobject]$definition
        }
    }
    if ($v5Groups) { $Model.groups = @($groupDefinitions) }
    $Model.outliner = @($groupOutliner)
    return $true
}

$sourceRoot = (Resolve-Path -LiteralPath $Source).Path
$destinationRoot = [IO.Path]::GetFullPath($Destination)
if (Test-Path -LiteralPath $destinationRoot) {
    if (-not (Test-Path -LiteralPath $destinationRoot -PathType Container)) {
        throw "Destination must be a new path or an empty directory: $destinationRoot"
    }
    $destinationRoot = (Resolve-Path -LiteralPath $destinationRoot).Path
}

$sourceComparable = $sourceRoot.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
$destinationComparable = $destinationRoot.TrimEnd([IO.Path]::DirectorySeparatorChar, [IO.Path]::AltDirectorySeparatorChar)
if ($sourceComparable.Equals($destinationComparable, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Source and Destination must be different directories"
}
if (Test-Path -LiteralPath $destinationRoot -PathType Container) {
    $existingEntry = Get-ChildItem -LiteralPath $destinationRoot -Force | Select-Object -First 1
    if ($null -ne $existingEntry) {
        throw "Destination must be empty so stale Lua scripts or assets cannot survive: $destinationRoot"
    }
}

$sourceFiles = @(Get-ChildItem -LiteralPath $sourceRoot -Recurse -File)
$modelPath = Join-Path $sourceRoot "model.bbmodel"
$figuraManifestPath = Join-Path $sourceRoot "avatar.json"

if (-not (Test-Path -LiteralPath $modelPath -PathType Leaf)) {
    $modelCandidates = @($sourceFiles | Where-Object { $_.Extension -ieq ".bbmodel" })
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
    $Name = if ($figuraManifest -and (Test-Property $figuraManifest "name") -and -not [string]::IsNullOrWhiteSpace([string]$figuraManifest.name)) {
        [string]$figuraManifest.name
    } else {
        $folderName
    }
}

$model = Get-Content -LiteralPath $modelPath -Raw | ConvertFrom-Json
$animations = @($model.animations | Where-Object { $null -ne $_ -and -not [string]::IsNullOrWhiteSpace([string]$_.name) })
$animationNames = @($animations | ForEach-Object { [string]$_.name })
$animationKeys = @{}
foreach ($animation in $animations) {
    $key = ConvertTo-AnimationKey ([string]$animation.name)
    if (-not $animationKeys.ContainsKey($key)) { $animationKeys[$key] = @() }
    $animationKeys[$key] += [string]$animation.name
}

$modelText = $model | ConvertTo-Json -Depth 100 -Compress
$missingTailBones = @("Tail1", "Tail2", "Tail3", "Tail4") |
    Where-Object { $modelText -notmatch ('"' + [Regex]::Escape($_) + '"') } |
    Measure-Object |
    Select-Object -ExpandProperty Count
$aquaticAnimationCount = @("swim", "stand", "small") |
    Where-Object { $animationKeys.ContainsKey($_) } |
    Measure-Object |
    Select-Object -ExpandProperty Count
$looksLikeAquaticCustom = $missingTailBones -eq 0 -and $aquaticAnimationCount -ge 2

$sourceLua = @($sourceFiles | Where-Object { $_.Extension -ieq ".lua" } | ForEach-Object {
    Get-Content -LiteralPath $_.FullName -Raw
}) -join "`n"
$sourceHidesVanillaPlayer = $sourceLua -match '(?is)vanilla_model\s*\.\s*(PLAYER|ALL)\s*:\s*(setVisible|visible)\s*\(\s*false\s*\)'
$manifestReplacesVanilla = $figuraManifest -and (Test-Property $figuraManifest "replace_vanilla") -and [bool]$figuraManifest.replace_vanilla
$sourceLooksFullBody = switch ($VanillaMode) {
    "Replace" { $true }
    "Overlay" { $false }
    default { $sourceHidesVanillaPlayer -or $manifestReplacesVanilla }
}

$nativeProfile = switch ($Profile) {
    "Accessory" { "accessory" }
    "FullBody" { "full_body" }
    "Custom" { "custom" }
    "Merling" { "custom" } # Legacy command-line alias.
    default {
        if ($looksLikeAquaticCustom) { "custom" }
        elseif ($sourceLooksFullBody) { "full_body" }
        else { "accessory" }
    }
}

$textureRoot = Join-Path $destinationRoot "textures"
[IO.Directory]::CreateDirectory($textureRoot) | Out-Null

$copiedTextures = @()
$referencedSourcePaths = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$textureSizeKeys = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$usedTextureNames = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$textureIndex = 0

foreach ($texture in @($model.textures)) {
    if ($null -eq $texture) { continue }
    $textureName = if (Test-Property $texture "name") { [IO.Path]::GetFileName([string]$texture.name) } else { "" }
    if ([string]::IsNullOrWhiteSpace($textureName)) { $textureName = "texture_$textureIndex.png" }

    $extension = [IO.Path]::GetExtension($textureName)
    if ([string]::IsNullOrWhiteSpace($extension)) { $extension = ".png" }
    $safeBase = ConvertTo-SafeName $textureName "texture_$textureIndex"
    $safeName = $safeBase + $extension.ToLowerInvariant()
    if (-not $usedTextureNames.Add($safeName)) {
        $safeName = "${safeBase}_$textureIndex$($extension.ToLowerInvariant())"
        [void]$usedTextureNames.Add($safeName)
    }

    $candidate = Find-TextureSource $texture $textureName $sourceRoot $sourceFiles
    $destinationTexture = Join-Path $textureRoot $safeName
    if ($candidate) {
        Copy-Item -LiteralPath $candidate -Destination $destinationTexture -Force
        [void]$referencedSourcePaths.Add([IO.Path]::GetFullPath($candidate))
    } elseif ((Test-Property $texture "source") -and [string]$texture.source -match '^data:image/[a-zA-Z0-9+.-]+;base64,(.+)$') {
        try {
            [IO.File]::WriteAllBytes($destinationTexture, [Convert]::FromBase64String($Matches[1]))
        } catch {
            throw "Embedded texture '$textureName' contains invalid Base64 data"
        }
    } else {
        throw "Texture '$textureName' was not found inside the Figura avatar and has no embedded Base64 source"
    }

    $relativeTexturePath = "textures/$safeName"
    if (Test-Property $texture "relative_path") { $texture.relative_path = $relativeTexturePath }
    else { $texture | Add-Member -NotePropertyName "relative_path" -NotePropertyValue $relativeTexturePath }
    $texture.PSObject.Properties.Remove("source")
    $copiedTextures += $relativeTexturePath
    $sizeKey = Get-PngSizeKey $destinationTexture
    if ($sizeKey) { [void]$textureSizeKeys.Add($sizeKey) }
    $textureIndex++
}

# Editor-only reference images can contain megabytes of unrelated Base64 data.
foreach ($property in @("reference_images", "backgrounds", "history")) {
    $model.PSObject.Properties.Remove($property)
}
$autoHumanoidRigGenerated = Add-StandardHumanoidRig $model $nativeProfile
$modelJson = $model | ConvertTo-Json -Depth 100 -Compress
[IO.File]::WriteAllText((Join-Path $destinationRoot "model.bbmodel"), $modelJson, [Text.UTF8Encoding]::new($false))

$sourceIcon = Join-Path $sourceRoot "avatar.png"
if (-not (Test-Path -LiteralPath $sourceIcon -PathType Leaf) -and $figuraManifest -and (Test-Property $figuraManifest "icon")) {
    try {
        $iconCandidate = [IO.Path]::GetFullPath((Join-Path $sourceRoot ([string]$figuraManifest.icon -replace '/', [IO.Path]::DirectorySeparatorChar)))
        $sourcePrefix = $sourceRoot.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
        if ($iconCandidate.StartsWith($sourcePrefix, [StringComparison]::OrdinalIgnoreCase) -and (Test-Path -LiteralPath $iconCandidate -PathType Leaf)) {
            $sourceIcon = $iconCandidate
        }
    } catch {}
}
if (Test-Path -LiteralPath $sourceIcon -PathType Leaf) {
    Copy-Item -LiteralPath $sourceIcon -Destination (Join-Path $destinationRoot "avatar.png") -Force
}

# Figura packs commonly keep complete alternate skins beside the primary
# texture. Preserve that replacement behavior explicitly because plain wardrobe
# PNGs are overlays in Shyne Standard 2.0.
$copiedOutfits = @()
if ($textureSizeKeys.Count -gt 0) {
    foreach ($png in @($sourceFiles | Where-Object { $_.Extension -ieq ".png" })) {
        $fullPath = [IO.Path]::GetFullPath($png.FullName)
        if ($png.Name -ieq "avatar.png" -or $referencedSourcePaths.Contains($fullPath)) { continue }
        if (Test-LikelyAuxiliaryPng $png) { continue }
        $sizeKey = Get-PngSizeKey $fullPath
        if (-not $sizeKey -or -not $textureSizeKeys.Contains($sizeKey)) { continue }
        $outfitRoot = Join-Path $destinationRoot "outfit"
        [IO.Directory]::CreateDirectory($outfitRoot) | Out-Null
        $outfitStem = [IO.Path]::GetFileNameWithoutExtension($png.Name)
        $outfitSourceName = if ($outfitStem -match '(?i)[._-](overlay|replace)$') {
            $png.Name
        } else {
            "$outfitStem.replace.png"
        }
        $outfitName = Add-UniqueDestinationName $outfitRoot $outfitSourceName
        Copy-Item -LiteralPath $png.FullName -Destination (Join-Path $outfitRoot $outfitName) -Force
        $copiedOutfits += "outfit/$outfitName"
    }
}

$slotAliases = [ordered]@{
    idle = @("idle", "stand", "standing")
    walk = @("walk", "walking", "move", "forward")
    sprint = @("sprint", "run", "running")
    crouch = @("crawl", "crouch", "crouching", "sneak")
    swim = @("swim", "swimming", "mermaidswim")
    sleep = @("sleep", "sleeping", "sleepy")
    fly = @("elytra", "fly", "flying")
    sit = @("sitanim", "mount", "mountup", "mountdown", "sit")
}
$animationMap = [ordered]@{}
$mappedAnimations = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($slot in $slotAliases.Keys) {
    $matches = @()
    foreach ($alias in $slotAliases[$slot]) {
        if ($animationKeys.ContainsKey($alias)) {
            foreach ($actualName in @($animationKeys[$alias])) {
                if ($matches -notcontains $actualName) { $matches += $actualName }
            }
        }
    }
    if ($matches.Count -eq 1) { $animationMap[$slot] = $matches[0] }
    elseif ($matches.Count -gt 1) { $animationMap[$slot] = @($matches) }
    foreach ($actualName in $matches) { [void]$mappedAnimations.Add($actualName) }
}

$blinkName = $null
foreach ($key in @("blink", "blinking")) {
    if ($animationKeys.ContainsKey($key)) {
        $blinkName = [string]@($animationKeys[$key])[0]
        break
    }
}

$autoplay = @()
foreach ($animation in $animations) {
    $actualName = [string]$animation.name
    if (-not (Test-LoopAnimation $animation) -or $mappedAnimations.Contains($actualName) -or $actualName -ieq $blinkName) { continue }
    $key = ConvertTo-AnimationKey $actualName
    if ($key -match '^(i[0-9]+|ears?|earidle|tailidle|hairidle|wingidle|ambient|breath|breathe|pulse)$') {
        $autoplay += $actualName
    }
}

$behavior = [ordered]@{ preset = "auto" }
if ($autoplay.Count -gt 0) { $behavior.autoplay = @($autoplay) }
if ($animationMap.Count -gt 0) { $behavior.animations = $animationMap }
# Figura starts a directly played animation at full weight. Converted packs use
# zero blend by default for frame-accurate output; creators can opt into a
# cross-fade later from Blockbench or avatar.json.
$behavior.blend_ticks = 0
if ($blinkName) {
    $behavior.blink = [ordered]@{
        animation = $blinkName
        min_ticks = 50
        max_ticks = 110
    }
}

$manifest = [ordered]@{
    standard = "2.0"
    id = $Id
    name = $Name
    version = "1.0.0"
    profile = $nativeProfile
    model = "model.bbmodel"
    behavior = $behavior
}
if ($figuraManifest -and (Test-Property $figuraManifest "description") -and -not [string]::IsNullOrWhiteSpace([string]$figuraManifest.description)) {
    $manifest.description = [string]$figuraManifest.description
}
if ($figuraManifest -and (Test-Property $figuraManifest "authors")) {
    $authors = @($figuraManifest.authors | ForEach-Object { [string]$_ } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($authors.Count -gt 0) { $manifest.authors = @($authors) }
}
if ($figuraManifest -and (Test-Property $figuraManifest "color") -and -not [string]::IsNullOrWhiteSpace([string]$figuraManifest.color)) {
    $manifest.color = [string]$figuraManifest.color
}
if ($copiedTextures.Count -gt 0) { $manifest.textures = @($copiedTextures) }

$manifestJson = $manifest | ConvertTo-Json -Depth 20
[IO.File]::WriteAllText((Join-Path $destinationRoot "avatar.json"), $manifestJson + "`n", [Text.UTF8Encoding]::new($false))

Write-Output "Converted '$Name' as '$Id' to Shyne Standard 2.0"
Write-Output "Destination: $destinationRoot"
Write-Output "Profile: $nativeProfile"
Write-Output "Animations: $($animationNames -join ', ')"
Write-Output "State bindings: $($animationMap.Keys -join ', ')"
Write-Output "Autoplay: $(if ($autoplay.Count -gt 0) { $autoplay -join ', ' } else { 'none' })"
Write-Output "Textures: $($copiedTextures -join ', ')"
Write-Output "Outfits: $(if ($copiedOutfits.Count -gt 0) { $copiedOutfits -join ', ' } else { 'none' })"
Write-Output "Auto Humanoid rig: $(if ($autoHumanoidRigGenerated) { 'generated' } else { 'preserved source hierarchy' })"
Write-Output "No Lua entry point was generated or copied."
