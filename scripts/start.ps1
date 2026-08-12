[CmdletBinding()]
param(
    [Alias("Fast")]
    [switch]$SkipMavenUpdate,
    [switch]$WhatIf
)

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$endScript = Join-Path $PSScriptRoot "end.ps1"
$agentWebStartScript = Join-Path $repositoryRoot "agent-web\scripts\Start-AgentWeb.ps1"
$businessFrontendRoot = Join-Path $repositoryRoot "business-base\frontend"

function Assert-Command([string]$Name) {
    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if (-not $command) {
        throw "Required command '$Name' was not found in PATH."
    }
    return $command.Source
}

function Wait-ListeningPort([string]$Name, [int]$Port, [int]$TimeoutSeconds = 60) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($listener) {
            Write-Host "$Name is listening on port $Port."
            return
        }
        Start-Sleep -Milliseconds 500
    }

    throw "$Name did not start listening on port $Port within $TimeoutSeconds seconds. Check its PowerShell window for the startup error."
}

function Start-ServiceWindow([string]$Name, [string]$WorkingDirectory, [string]$Command) {
    $escapedDirectory = $WorkingDirectory.Replace("'", "''")
    $shellCommand = "Set-Location -LiteralPath '$escapedDirectory'; $Command"
    Write-Host "Starting $Name..."
    Start-Process -FilePath "powershell.exe" -ArgumentList @(
        "-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $shellCommand
    ) | Out-Null
}

if (-not (Test-Path -LiteralPath $endScript)) {
    throw "Cannot find $endScript."
}
if (-not (Test-Path -LiteralPath $agentWebStartScript)) {
    throw "Cannot find $agentWebStartScript."
}
if (-not (Test-Path -LiteralPath (Join-Path $repositoryRoot "pom.xml"))) {
    throw "Cannot find the root pom.xml below $repositoryRoot."
}
if (-not (Test-Path -LiteralPath (Join-Path $businessFrontendRoot "package.json"))) {
    throw "Cannot find business-base/frontend/package.json below $repositoryRoot."
}

$maven = Assert-Command "mvn.cmd"
Assert-Command "node.exe" | Out-Null
Assert-Command "npm.cmd" | Out-Null
Assert-Command "powershell.exe" | Out-Null

if ($WhatIf) {
    Write-Host "Would stop existing Flow Mind services on ports 8081, 3100, 5173, and 5174."
    if ($SkipMavenUpdate) {
        Write-Host "Would skip the root Maven package update by request."
    } else {
        Write-Host "Would update Maven packages from $repositoryRoot with: mvn.cmd -U --no-transfer-progress -DskipTests clean install"
    }
    & $agentWebStartScript -WhatIf
    Write-Host "Would start Business Base frontend in $businessFrontendRoot with: npm.cmd run dev"
    Write-Host "WhatIf complete: no process was stopped, built, or started."
    return
}

Write-Host "Stopping any existing Flow Mind development services so the rebuilt Maven packages are loaded..."
& $endScript -Force

if ($SkipMavenUpdate) {
    Write-Host "Skipping the root Maven package update. Existing packages in .m2/repository will be used."
} else {
    Write-Host "Updating and installing Maven packages from the root reactor..."
    Push-Location $repositoryRoot
    try {
        $mavenArguments = @("-U", "--no-transfer-progress", "-DskipTests", "clean", "install")
        & $maven @mavenArguments
        if ($LASTEXITCODE -ne 0) {
            throw "Maven package update failed with exit code $LASTEXITCODE. No service was started."
        }
    } finally {
        Pop-Location
    }
    Write-Host "Maven packages were rebuilt and installed successfully."
}

& $agentWebStartScript

Start-ServiceWindow "Business Base frontend" $businessFrontendRoot "npm.cmd run dev"
Wait-ListeningPort "Agent frontend" 5173
Wait-ListeningPort "Business Base frontend" 5174

Write-Host "Flow Mind is ready."
Write-Host "Agent Web:     http://127.0.0.1:5173"
Write-Host "Business Base: http://127.0.0.1:5174"
