[CmdletBinding()]
param(
    [switch]$Force
)

$ErrorActionPreference = "Stop"
# These are the four fixed local development ports used by Flow Mind.
$ports = 8081, 3100, 5173, 5174
$listeners = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
    Where-Object { $_.LocalPort -in $ports } |
    Sort-Object LocalPort

if (-not $listeners) {
    Write-Host "No Flow Mind development service is listening on ports 8081, 3100, 5173, or 5174."
    return
}

$listeners | Select-Object LocalAddress, LocalPort, OwningProcess | Format-Table -AutoSize

if (-not $Force) {
    $answer = Read-Host "Stop the listed process trees? [y/N]"
    if ($answer -notmatch "^(y|yes)$") {
        Write-Host "No process was stopped."
        return
    }
}

$processById = @{}
Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | ForEach-Object {
    $processById[[int]$_.ProcessId] = $_
}

function Get-ServiceTreeRootId([int]$ListenerProcessId) {
    $currentId = $ListenerProcessId
    $serviceProcessNames = @("cmd.exe", "java.exe", "node.exe")

    while ($processById.ContainsKey($currentId)) {
        $parentId = [int]$processById[$currentId].ParentProcessId
        if (-not $processById.ContainsKey($parentId)) {
            break
        }

        $parent = $processById[$parentId]
        if ($parent.Name -notin $serviceProcessNames) {
            break
        }

        $currentId = $parentId
    }

    return $currentId
}

$serviceTreeRootIds = @()
foreach ($listenerProcessId in ($listeners.OwningProcess | Sort-Object -Unique)) {
    $serviceTreeRootIds += Get-ServiceTreeRootId $listenerProcessId
}

foreach ($processId in ($serviceTreeRootIds | Sort-Object -Unique)) {
    Write-Host "Stopping process tree rooted at PID $processId..."
    & taskkill.exe /PID $processId /T /F
    if ($LASTEXITCODE -ne 0) {
        throw "taskkill failed for PID $processId with exit code $LASTEXITCODE."
    }
}

Write-Host "Flow Mind development services stopped."
