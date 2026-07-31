[CmdletBinding()]
param(
    [string]$PiModel = "openai/gpt-5.6-terra",
    [switch]$SkipPlatform,
    [switch]$WhatIf
)

$ErrorActionPreference = "Stop"
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$agentWebRoot = Join-Path $repositoryRoot "agent-web"

function Get-ListeningProcessId([int]$Port) {
    $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
    return $listener.OwningProcess
}

function Start-ServiceWindow([string]$Name, [string]$WorkingDirectory, [string]$Command) {
    $escapedDirectory = $WorkingDirectory.Replace("'", "''")
    $shellCommand = "Set-Location -LiteralPath '$escapedDirectory'; $Command"
    Write-Host "Starting $Name..."
    if (-not $WhatIf) {
        Start-Process -FilePath "powershell.exe" -ArgumentList @(
            "-NoExit", "-ExecutionPolicy", "Bypass", "-Command", $shellCommand
        ) | Out-Null
    }
}

if (-not (Test-Path (Join-Path $agentWebRoot "package.json"))) {
    throw "Cannot find agent-web/package.json below $repositoryRoot."
}

# PI_MODEL is consumed by the NestJS process. The API key is deliberately read
# only from the caller's environment and is never written to a file or output.
$env:PI_MODEL = $PiModel
if ($PiModel.StartsWith("openai/") -and -not $env:OPENAI_API_KEY) {
    Write-Warning "OPENAI_API_KEY is not set in this PowerShell session. Pi may use ~/.pi/agent/auth.json instead; otherwise /health/ready will fail."
}

$services = @(
    @{ Name = "Flow Mind platform"; Port = 8080; WorkingDirectory = $repositoryRoot; Command = "mvn --% -Dmaven.repo.local=.m2-agent org.springframework.boot:spring-boot-maven-plugin:2.7.18:run -pl platform/platform-core" },
    @{ Name = "Agent backend"; Port = 3100; WorkingDirectory = $agentWebRoot; Command = "npm run dev:backend" },
    @{ Name = "Agent frontend"; Port = 5173; WorkingDirectory = $agentWebRoot; Command = "npm run dev:frontend" }
)

foreach ($service in $services) {
    if ($SkipPlatform -and $service.Port -eq 8080) {
        Write-Host "Skipping Flow Mind platform by request."
        continue
    }

    $existingProcessId = Get-ListeningProcessId $service.Port
    if ($existingProcessId) {
        Write-Host "$($service.Name) is already listening on port $($service.Port) (PID $existingProcessId); leaving it running."
        continue
    }
    Start-ServiceWindow $service.Name $service.WorkingDirectory $service.Command
}

if ($WhatIf) {
    Write-Host "WhatIf complete: no services were started."
} else {
    Write-Host "Startup commands were opened in separate PowerShell windows."
    Write-Host "Open http://127.0.0.1:5173 after the frontend window reports ready."
}
