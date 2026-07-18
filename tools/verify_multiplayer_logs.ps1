param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$logs = @{
    Server = Join-Path $Root 'run/server/logs/latest.log'
    ClientA = Join-Path $Root 'run/client-a/logs/latest.log'
    ClientB = Join-Path $Root 'run/client-b/logs/latest.log'
}

$failures = [System.Collections.Generic.List[string]]::new()
foreach ($entry in $logs.GetEnumerator()) {
    if (-not (Test-Path -LiteralPath $entry.Value -PathType Leaf)) {
        $failures.Add("Missing $($entry.Key) log: $($entry.Value)")
    }
}
if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Error $_ }
    exit 1
}

$server = Get-Content -Raw -LiteralPath $logs.Server
$clientA = Get-Content -Raw -LiteralPath $logs.ClientA
$clientB = Get-Content -Raw -LiteralPath $logs.ClientB

$checks = @(
    @{ Name = 'Server accepted Client A'; Pass = $server -match 'Protocol ready for ShyneTesterA' },
    @{ Name = 'Server accepted Client B'; Pass = $server -match 'Protocol ready for ShyneTesterB' },
    @{ Name = 'Client A received capabilities'; Pass = $clientA -match 'gameplay\.server_authoritative' -and $clientA -match 'avatar\.peer_snapshot_v2' },
    @{ Name = 'Client B received capabilities'; Pass = $clientB -match 'gameplay\.server_authoritative' -and $clientB -match 'avatar\.peer_snapshot_v2' },
    @{ Name = 'Server had no Shyne exception'; Pass = $server -notmatch '(?i)Shyne.*(Exception|ERROR)|(?:Exception|ERROR).*Shyne' },
    @{ Name = 'Client A had no Shyne exception'; Pass = $clientA -notmatch '(?i)Shyne.*(Exception|ERROR)|(?:Exception|ERROR).*Shyne' },
    @{ Name = 'Client B had no Shyne exception'; Pass = $clientB -notmatch '(?i)Shyne.*(Exception|ERROR)|(?:Exception|ERROR).*Shyne' }
)

foreach ($check in $checks) {
    $mark = if ($check.Pass) { '[PASS]' } else { '[FAIL]' }
    Write-Host "$mark $($check.Name)"
    if (-not $check.Pass) { $failures.Add($check.Name) }
}

if ($failures.Count -gt 0) { exit 1 }
Write-Host '[PASS] Multiplayer log verification complete'
