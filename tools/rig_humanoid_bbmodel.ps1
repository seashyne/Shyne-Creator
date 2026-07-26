param(
    [Parameter(Mandatory = $true)]
    [string] $ModelPath
)

$resolved = [System.IO.Path]::GetFullPath($ModelPath)
$model = Get-Content -Raw -LiteralPath $resolved | ConvertFrom-Json

$parts = @{}
foreach ($element in $model.elements) {
    $parts[$element.name] = $element.uuid
}

$required = @(
    'Head', 'Hat', 'mouth_open',
    'Body', 'Jacket',
    'LeftArm', 'Left Sleeve',
    'RightArm', 'Right Sleeve',
    'LeftLeg', 'Left Pants',
    'RightLeg', 'Right Pants'
)
foreach ($name in $required) {
    if (-not $parts.ContainsKey($name)) {
        throw "Required model part is missing: $name"
    }
}

$definitions = @(
    [pscustomobject]@{
        name = 'Head'; uuid = 'shyne-humanoid-head'; origin = @(0, 24, 0)
        children = @($parts.Head, $parts.Hat, $parts.mouth_open)
    },
    [pscustomobject]@{
        name = 'Body'; uuid = 'shyne-humanoid-body'; origin = @(0, 24, 0)
        children = @($parts.Body, $parts.Jacket)
    },
    [pscustomobject]@{
        name = 'LeftArm'; uuid = 'shyne-humanoid-left-arm'; origin = @(-5, 22, 0)
        children = @($parts.LeftArm, $parts.'Left Sleeve')
    },
    [pscustomobject]@{
        name = 'RightArm'; uuid = 'shyne-humanoid-right-arm'; origin = @(5, 22, 0)
        children = @($parts.RightArm, $parts.'Right Sleeve')
    },
    [pscustomobject]@{
        name = 'LeftLeg'; uuid = 'shyne-humanoid-left-leg'; origin = @(-1.9, 12, 0)
        children = @($parts.LeftLeg, $parts.'Left Pants')
    },
    [pscustomobject]@{
        name = 'RightLeg'; uuid = 'shyne-humanoid-right-leg'; origin = @(1.9, 12, 0)
        children = @($parts.RightLeg, $parts.'Right Pants')
    }
)

$model.groups = @($definitions | ForEach-Object {
    [pscustomobject]@{
        name = $_.name
        uuid = $_.uuid
        origin = $_.origin
        rotation = @(0, 0, 0)
        visibility = $true
        export = $true
        children = @()
    }
})
$model.outliner = @($definitions | ForEach-Object {
    [pscustomobject]@{
        uuid = $_.uuid
        children = $_.children
    }
})

$json = $model | ConvertTo-Json -Depth 100
[System.IO.File]::WriteAllText($resolved, $json, [System.Text.UTF8Encoding]::new($false))
