param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw "Regression check failed: $Message" }
}

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..\..")).Path
$installer = (Resolve-Path -LiteralPath (Join-Path $repoRoot "tools\install_local.ps1")).Path
. (Join-Path $repoRoot "tools\lib\install_safety.ps1")

$testRoot = Join-Path $repoRoot ("build\tool-tests\installer-" + [Guid]::NewGuid().ToString("N"))
$gameDir = Join-Path $testRoot "Instance With Spaces"
$modsDir = Join-Path $gameDir "mods"
$payloadA = Join-Path $testRoot "payload-a"
$payloadB = Join-Path $testRoot "payload-b"
[IO.Directory]::CreateDirectory($modsDir) | Out-Null
[IO.Directory]::CreateDirectory($payloadA) | Out-Null
[IO.Directory]::CreateDirectory($payloadB) | Out-Null

$targetProcess = [pscustomobject]@{
    Name = "javaw.exe"
    ProcessId = 4102
    CommandLine = "javaw.exe -Xmx4G --gameDir `"$gameDir`" --version 26.2"
}
$equalSyntaxProcess = [pscustomobject]@{
    Name = "java.exe"
    ProcessId = 4103
    CommandLine = "java.exe --gameDir=`"$($gameDir.Replace('\', '/'))`" net.minecraft.client.main.Main"
}
$otherProcess = [pscustomobject]@{
    Name = "javaw.exe"
    ProcessId = 4104
    CommandLine = "javaw.exe --gameDir `"$gameDir-other`" --version 26.2"
}

Assert-True (Test-ShyneProcessTargetsGameDir -Process $targetProcess -GameDir $gameDir) `
    "quoted --gameDir must match the exact instance"
Assert-True (Test-ShyneProcessTargetsGameDir -Process $equalSyntaxProcess -GameDir $gameDir) `
    "--gameDir= with forward slashes must match the exact instance"
Assert-True (-not (Test-ShyneProcessTargetsGameDir -Process $otherProcess -GameDir $gameDir)) `
    "a path with the same prefix must not match"

$runningRejected = $false
try {
    Assert-ShyneInstallNotRunning -GameDir $gameDir -Processes @($targetProcess)
} catch {
    $runningRejected = (
        $_.Exception.Message -match "Minecraft is running" -and
        $_.Exception.Message -match "PID 4102" -and
        $_.Exception.Message -match "was not changed"
    )
}
Assert-True $runningRejected "the guard must fail clearly without changing files"

$installerText = Get-Content -LiteralPath $installer -Raw
Assert-True ($installerText -notmatch '(?i)\bStop-Process\b') `
    "the installer must never kill Minecraft"

[IO.File]::WriteAllText(
    (Join-Path $payloadA "fabric.mod.json"),
    '{"schemaVersion":1,"id":"shyne_creator","version":"test-a"}',
    [Text.UTF8Encoding]::new($false)
)
[IO.File]::WriteAllText(
    (Join-Path $payloadA "marker.txt"),
    "first",
    [Text.UTF8Encoding]::new($false)
)
[IO.File]::WriteAllText(
    (Join-Path $payloadB "fabric.mod.json"),
    '{"schemaVersion":1,"id":"shyne_creator","version":"test-b"}',
    [Text.UTF8Encoding]::new($false)
)
[IO.File]::WriteAllText(
    (Join-Path $payloadB "marker.txt"),
    "second",
    [Text.UTF8Encoding]::new($false)
)

$jarA = Join-Path $testRoot "shyne-creator-fabric-test-a.jar"
$jarB = Join-Path $testRoot "shyne-creator-fabric-test-b.jar"
Compress-Archive -LiteralPath @(
    (Join-Path $payloadA "fabric.mod.json"),
    (Join-Path $payloadA "marker.txt")
) -DestinationPath $jarA
Compress-Archive -LiteralPath @(
    (Join-Path $payloadB "fabric.mod.json"),
    (Join-Path $payloadB "marker.txt")
) -DestinationPath $jarB

& $installer -Loader fabric -GameDir $gameDir -Jar $jarA | Out-Null
Assert-True (Test-Path -LiteralPath (Join-Path $modsDir ([IO.Path]::GetFileName($jarA)))) `
    "a valid Fabric JAR must install"

& $installer -Loader fabric -GameDir $gameDir -Jar $jarB | Out-Null
$installed = @(Get-ChildItem -LiteralPath $modsDir -Filter "shyne-creator-fabric-*.jar" -File)
Assert-True ($installed.Count -eq 1 -and $installed[0].Name -eq ([IO.Path]::GetFileName($jarB))) `
    "an update must replace the old version without leaving duplicate mod JARs"

$installedHashBefore = (Get-FileHash -LiteralPath $installed[0].FullName -Algorithm SHA256).Hash
$invalidJar = Join-Path $testRoot "shyne-creator-fabric-invalid.jar"
[IO.File]::WriteAllText($invalidJar, "not a zip", [Text.UTF8Encoding]::new($false))
$invalidRejected = $false
try {
    & $installer -Loader fabric -GameDir $gameDir -Jar $invalidJar | Out-Null
} catch {
    $invalidRejected = $_.Exception.Message -match "Invalid Shyne JAR"
}
$installedHashAfter = (Get-FileHash -LiteralPath $installed[0].FullName -Algorithm SHA256).Hash
Assert-True $invalidRejected "an invalid JAR must be rejected"
Assert-True ($installedHashBefore -eq $installedHashAfter) `
    "invalid input must not change the installed JAR"

$wrongRootRejected = $false
try {
    & $installer -Loader fabric -GameDir $modsDir -Jar $jarB | Out-Null
} catch {
    $wrongRootRejected = $_.Exception.Message -match "exact Minecraft instance root"
}
Assert-True $wrongRootRejected "the mods directory itself must not be accepted as GameDir"

Write-Output "PASS install_local safety regression checks"
Write-Output "Artifacts: $testRoot"
