[CmdletBinding()]
param(
    [switch]$SkipPlatform,
    [switch]$WhatIf
)

$ErrorActionPreference = "Stop"
$agentWebRoot = Split-Path -Parent $PSScriptRoot
$repositoryRoot = Split-Path -Parent $agentWebRoot

function Get-ListeningProcessId([int]$Port) {
    $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
    return $listener.OwningProcess
}

function Start-ServiceWindow([string]$Name, [string]$WorkingDirectory, [string]$Command) {
    $escapedDirectory = $WorkingDirectory.Replace("'", "''")
    $shellCommand = "Set-Location -LiteralPath '$escapedDirectory'; $Command"
    if ($WhatIf) {
        Write-Host "Would start $Name in $WorkingDirectory with: $Command"
        return
    }

    Write-Host "Starting $Name..."
    Start-Process -FilePath "powershell.exe" -ArgumentList @(
        "-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $shellCommand
    ) | Out-Null
}

function Test-AgentBackendHealth {
    try {
        $response = Invoke-RestMethod -Uri "http://127.0.0.1:3100/health/live" -TimeoutSec 2
        return $response.status -eq "UP"
    } catch {
        return $false
    }
}

function Wait-AgentBackend([int]$TimeoutSeconds = 60) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-AgentBackendHealth) {
            Write-Host "Agent backend health check passed."
            return
        }
        Start-Sleep -Milliseconds 250
    }

    throw "Agent backend did not become healthy within $TimeoutSeconds seconds. The frontend was not started. Check the Agent backend PowerShell window for the startup error."
}

if (-not (Test-Path (Join-Path $agentWebRoot "package.json"))) {
    throw "Cannot find agent-web/package.json below $repositoryRoot."
}

$contractsBuildCommand = "npm.cmd run build -w @flowmind/agent-contracts"
if ($WhatIf) {
    Write-Host "Would build shared contracts in $agentWebRoot with: $contractsBuildCommand"
} else {
    Write-Host "Building shared contracts..."
    Push-Location $agentWebRoot
    try {
        & npm.cmd run build -w @flowmind/agent-contracts
        if ($LASTEXITCODE -ne 0) {
            throw "Shared contracts build failed with exit code $LASTEXITCODE. No services were started."
        }
    } finally {
        Pop-Location
    }
    Write-Host "Shared contracts build completed."
}

$platform = @{ Name = "Flow Mind platform"; Port = 8080; WorkingDirectory = $repositoryRoot; Command = "mvn --% -Dmaven.repo.local=.m2-agent org.springframework.boot:spring-boot-maven-plugin:2.7.18:run -pl platform/platform-core" }
if ($SkipPlatform) {
    Write-Host "Skipping Flow Mind platform by request."
} else {
    $existingProcessId = Get-ListeningProcessId $platform.Port
    if ($existingProcessId) {
        Write-Host "$($platform.Name) is already listening on port $($platform.Port) (PID $existingProcessId); leaving it running."
    } else {
        Start-ServiceWindow $platform.Name $platform.WorkingDirectory $platform.Command
    }
}

$backend = @{ Name = "Agent backend"; Port = 3100; WorkingDirectory = $agentWebRoot; Command = "npm run dev:backend" }
$backendProcessId = Get-ListeningProcessId $backend.Port
if ($backendProcessId) {
    if ($WhatIf) {
        Write-Host "Would verify that the service on port $($backend.Port) (PID $backendProcessId) is a healthy Agent backend."
    } elseif (-not (Test-AgentBackendHealth)) {
        throw "Port $($backend.Port) is already in use by PID $backendProcessId, but http://127.0.0.1:3100/health/live is not a healthy Agent backend. Stop the conflicting process and retry."
    } else {
        Write-Host "$($backend.Name) is already healthy on port $($backend.Port) (PID $backendProcessId); leaving it running."
    }
} else {
    Start-ServiceWindow $backend.Name $backend.WorkingDirectory $backend.Command
    if ($WhatIf) {
        Write-Host "Would wait up to 60 seconds for http://127.0.0.1:3100/health/live before starting the frontend."
    } else {
        Wait-AgentBackend -TimeoutSeconds 60
    }
}

$frontend = @{ Name = "Agent frontend"; Port = 5173; WorkingDirectory = $agentWebRoot; Command = "npm run dev:frontend" }
$frontendProcessId = Get-ListeningProcessId $frontend.Port
if ($frontendProcessId) {
    Write-Host "$($frontend.Name) is already listening on port $($frontend.Port) (PID $frontendProcessId); leaving it running."
} else {
    Start-ServiceWindow $frontend.Name $frontend.WorkingDirectory $frontend.Command
}

if ($WhatIf) {
    Write-Host "WhatIf complete: no services were started."
} else {
    Write-Host "Startup commands were opened in separate PowerShell windows, and the Agent backend is healthy."
    Write-Host "Open http://127.0.0.1:5173 after the frontend window reports ready."
}
