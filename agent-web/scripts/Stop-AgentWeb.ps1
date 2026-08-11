[CmdletBinding()]
param(
    [switch]$Force
)

$ErrorActionPreference = "Stop"
# Stops only the three local development ports documented by Agent Web.
$ports = 8081, 3100, 5173
$listeners = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue |
    Where-Object { $_.LocalPort -in $ports } |
    Sort-Object LocalPort

if (-not $listeners) {
    Write-Host "No Flow Mind development service is listening on ports 8081, 3100, or 5173."
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

foreach ($processId in ($listeners.OwningProcess | Sort-Object -Unique)) {
    Write-Host "Stopping process tree rooted at PID $processId..."
    & taskkill.exe /PID $processId /T /F
    if ($LASTEXITCODE -ne 0) {
        throw "taskkill failed for PID $processId with exit code $LASTEXITCODE."
    }
}

Write-Host "Flow Mind development services stopped."
