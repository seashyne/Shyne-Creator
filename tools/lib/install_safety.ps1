Set-StrictMode -Version Latest

function ConvertTo-ShyneComparablePath {
    param(
        [Parameter(Mandatory = $true)][string]$Path
    )

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $null
    }

    try {
        $expanded = [Environment]::ExpandEnvironmentVariables($Path.Trim())
        if (-not [IO.Path]::IsPathFullyQualified($expanded)) {
            return $null
        }
        return [IO.Path]::GetFullPath($expanded).TrimEnd(
            [IO.Path]::DirectorySeparatorChar,
            [IO.Path]::AltDirectorySeparatorChar
        )
    } catch {
        return $null
    }
}
function Get-ShyneGameDirArgument {
    param(
        [AllowNull()][string]$CommandLine
    )

    if ([string]::IsNullOrWhiteSpace($CommandLine)) {
        return $null
    }

    $pattern = '(?i)(?:^|\s)--gameDir(?:\s*=\s*|\s+)(?:"(?<double>[^"]+)"|''(?<single>[^'']+)''|(?<bare>\S+))'
    $match = [regex]::Match($CommandLine, $pattern)
    if (-not $match.Success) {
        return $null
    }

    foreach ($groupName in @("double", "single", "bare")) {
        if ($match.Groups[$groupName].Success) {
            return $match.Groups[$groupName].Value
        }
    }
    return $null
}

function Get-ShyneProcessProperty {
    param(
        [Parameter(Mandatory = $true)][object]$Process,
        [Parameter(Mandatory = $true)][string]$Name
    )

    $property = $Process.PSObject.Properties[$Name]
    if ($null -eq $property) {
        return $null
    }
    return $property.Value
}

function Test-ShyneProcessTargetsGameDir {
    param(
        [Parameter(Mandatory = $true)][object]$Process,
        [Parameter(Mandatory = $true)][string]$GameDir
    )

    $processName = [string](Get-ShyneProcessProperty -Process $Process -Name "Name")
    if ($processName -notin @("java.exe", "javaw.exe", "java", "javaw")) {
        return $false
    }

    $commandLine = [string](Get-ShyneProcessProperty -Process $Process -Name "CommandLine")
    $processGameDir = Get-ShyneGameDirArgument -CommandLine $commandLine
    if ([string]::IsNullOrWhiteSpace($processGameDir)) {
        return $false
    }

    $expected = ConvertTo-ShyneComparablePath -Path $GameDir
    $actual = ConvertTo-ShyneComparablePath -Path $processGameDir
    if ($null -eq $expected -or $null -eq $actual) {
        return $false
    }

    return [string]::Equals($expected, $actual, [StringComparison]::OrdinalIgnoreCase)
}

function Get-ShyneJavaProcessSnapshot {
    try {
        return @(Get-CimInstance `
            -ClassName Win32_Process `
            -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" `
            -ErrorAction Stop |
            Select-Object Name, ProcessId, CommandLine)
    } catch {
        throw "Could not inspect running Java processes, so the installed JAR cannot be updated safely. No files were changed. $($_.Exception.Message)"
    }
}

function Assert-ShyneInstallNotRunning {
    param(
        [Parameter(Mandatory = $true)][string]$GameDir,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][object[]]$Processes
    )

    $matching = @($Processes | Where-Object {
        Test-ShyneProcessTargetsGameDir -Process $_ -GameDir $GameDir
    })
    if ($matching.Count -eq 0) {
        return
    }

    $processLabels = @($matching | ForEach-Object {
        $name = [string](Get-ShyneProcessProperty -Process $_ -Name "Name")
        $processId = Get-ShyneProcessProperty -Process $_ -Name "ProcessId"
        if ($null -eq $processId) { $name } else { "$name PID $processId" }
    })
    throw (
        "Minecraft is running for target instance '$GameDir' ({0}). " +
        "Close Minecraft completely, then run the installer again. " +
        "The installed Shyne JAR was not changed."
    ) -f ($processLabels -join ", ")
}
