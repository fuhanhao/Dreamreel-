#Requires -Version 5.1
<#
.SYNOPSIS
  Stop frontend (7050) and API (7051). Docker DB stays up.
#>
$ErrorActionPreference = "Continue"

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
        Write-Host "Stopped port $Port PIDs: $($killed -join ', ')" -ForegroundColor Green
    } else {
        Write-Host "Port $Port idle" -ForegroundColor DarkGray
    }
}

Write-Host "Stopping frontend :7050 ..."
Stop-Port 7050
Write-Host "Stopping API :7051 ..."
Stop-Port 7051

Write-Host ""
Write-Host "Docker (Postgres/Redis) kept running. To stop:"
Write-Host "  docker compose down"
Write-Host ""
Write-Host "Done."
