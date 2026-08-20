#Requires -Version 5.1
<#
.SYNOPSIS
  Start Docker (Postgres/Redis) + Spring Boot API (7051) + Next.js (7050)
#>
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

function Write-Step([string]$msg) {
    Write-Host ""
    Write-Host "==> $msg" -ForegroundColor Cyan
}

function Test-PortOpen([int]$Port) {
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $iar = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
        $ok = $iar.AsyncWaitHandle.WaitOne(500)
        if ($ok -and $client.Connected) {
            $client.EndConnect($iar)
            $client.Close()
            return $true
        }
        $client.Close()
        return $false
    } catch {
        return $false
    }
}

function Wait-Port([int]$Port, [int]$TimeoutSec = 90, [string]$Name = "service") {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (Test-PortOpen $Port) {
            Write-Host "    $Name ready (:$Port)" -ForegroundColor Green
            return $true
        }
        Start-Sleep -Seconds 2
    }
    Write-Host "    Wait $Name (:$Port) timed out after ${TimeoutSec}s" -ForegroundColor Yellow
    return $false
}

function Assert-Command([string]$Name) {
    $cmd = Get-Command $Name -ErrorAction SilentlyContinue
    if (-not $cmd) {
        throw "Command not found: $Name. Install it and add to PATH."
    }
}

function Stop-Port([int]$Port) {
    $killed = @()
    $lines = netstat -ano | findstr "LISTENING" | findstr ":$Port"
    foreach ($line in $lines) {
        $parts = ($line -split '\s+') | Where-Object { $_ -ne "" }
        $procId = $parts[-1]
        if ($procId -match '^\d+$' -and [int]$procId -gt 0) {
            taskkill /PID $procId /F 2>$null | Out-Null
            if ($LASTEXITCODE -eq 0) {
                $killed += $procId
            }
        }
    }
    if ($killed.Count -gt 0) {
        Write-Host "    Stopped port $Port PIDs: $($killed -join ', ')" -ForegroundColor Yellow
    }
}

Write-Host "Project: $Root" -ForegroundColor DarkGray

Write-Step "Clean stale API/Web processes"
Stop-Port 7050
Stop-Port 7051
Start-Sleep -Seconds 1

Assert-Command "docker"
Assert-Command "node"
Assert-Command "npm"
Assert-Command "mvn"

# --- Docker ---
Write-Step "Start Docker (PostgreSQL :7052 / Redis :7053)"
docker compose up -d
if ($LASTEXITCODE -ne 0) {
    throw "docker compose up failed. Is Docker Desktop running?"
}

Write-Host "    Waiting for DB healthcheck..."
$healthy = $false
for ($i = 0; $i -lt 30; $i++) {
    $status = docker inspect --format='{{.State.Health.Status}}' dreamreel-postgres 2>$null
    if ($status -eq "healthy") {
        $healthy = $true
        break
    }
    Start-Sleep -Seconds 2
}
if ($healthy) {
    Write-Host "    PostgreSQL healthy" -ForegroundColor Green
} else {
    Write-Host "    PostgreSQL not healthy yet; continuing anyway" -ForegroundColor Yellow
}

# --- npm deps ---
if (-not (Test-Path (Join-Path $Root "node_modules"))) {
    Write-Step "npm install"
    npm install
    if ($LASTEXITCODE -ne 0) { throw "npm install failed" }
}

$webEnv = Join-Path $Root "apps\web\.env.local"
$apiEnv = Join-Path $Root "services\api\application-local.yml"
if (-not (Test-Path $webEnv)) {
    Write-Host "    Hint: missing apps/web/.env.local (copy from .env.local.example)" -ForegroundColor Yellow
}
if (-not (Test-Path $apiEnv)) {
    Write-Host "    Hint: missing services/api/application-local.yml (copy from example)" -ForegroundColor Yellow
}

# --- API ---
Write-Step "Start Spring Boot API (:7051)"
$apiCmd = @"
chcp 65001 >nul
cd /d `"$Root\services\api`"
title dreamreel-api
set MAVEN_OPTS=-Xmx1536m -XX:+UseG1GC -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8
echo [API] spring-boot:run profile=local
mvn -q spring-boot:run "-Dspring-boot.run.profiles=local"
pause
"@
$apiBat = Join-Path $env:TEMP "dreamreel-start-api.bat"
Set-Content -Path $apiBat -Value $apiCmd -Encoding ASCII
Start-Process -FilePath "cmd.exe" -ArgumentList "/k", "`"$apiBat`"" -WorkingDirectory (Join-Path $Root "services\api")
Wait-Port -Port 7051 -TimeoutSec 120 -Name "API" | Out-Null

# --- Web ---
Write-Step "Start Next.js (:7050)"
$webCmd = @"
cd /d `"$Root`"
title dreamreel-web
set NODE_OPTIONS=--max-old-space-size=4096
echo [WEB] npm run dev:web
npm run dev:web
pause
"@
$webBat = Join-Path $env:TEMP "dreamreel-start-web.bat"
Set-Content -Path $webBat -Value $webCmd -Encoding ASCII
Start-Process -FilePath "cmd.exe" -ArgumentList "/k", "`"$webBat`"" -WorkingDirectory $Root
Wait-Port -Port 7050 -TimeoutSec 90 -Name "Web" | Out-Null

# --- Browser ---
Write-Step "Open browser"
Start-Process "http://localhost:7050"

Write-Host ""
Write-Host "Started:" -ForegroundColor Green
Write-Host "  Web      http://localhost:7050"
Write-Host "  API      http://localhost:7051"
Write-Host "  DramaForge http://localhost:7050/projects?entry=dramaforge"
Write-Host ""
Write-Host "Stop: run stop.bat, or close the API / Web terminal windows."
