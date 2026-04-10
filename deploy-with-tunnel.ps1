param(
    [string]$BackendUrl = "http://localhost:9090",
    [switch]$AutoUpdatePages
)

$ErrorActionPreference = "Stop"

function Normalize-EnvValue {
    param([string]$Value)
    if ($null -eq $Value) { return "" }
    return $Value.Replace("`r", "").Replace("`n", "").Trim()
}

function Update-CloudflarePagesApiOrigin {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ApiOrigin
    )

    $token = Normalize-EnvValue $env:CLOUDFLARE_API_TOKEN
    $accountId = Normalize-EnvValue $env:CLOUDFLARE_ACCOUNT_ID
    $projectName = Normalize-EnvValue $env:CLOUDFLARE_PAGES_PROJECT
    $deployHookUrl = Normalize-EnvValue $env:CLOUDFLARE_PAGES_DEPLOY_HOOK_URL

    if ([string]::IsNullOrWhiteSpace($token) -or [string]::IsNullOrWhiteSpace($accountId) -or [string]::IsNullOrWhiteSpace($projectName)) {
        Write-Warning "No se actualizara Cloudflare Pages automaticamente. Configure: CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID, CLOUDFLARE_PAGES_PROJECT"
        return
    }

    $headers = @{
        Authorization = "Bearer $token"
    }

    $uri = "https://api.cloudflare.com/client/v4/accounts/$accountId/pages/projects/$projectName"
    $body = @{
        deployment_configs = @{
            production = @{
                env_vars = @{
                    VITE_API_ORIGIN = @{
                        type = "plain_text"
                        value = $ApiOrigin
                    }
                }
            }
            preview = @{
                env_vars = @{
                    VITE_API_ORIGIN = @{
                        type = "plain_text"
                        value = $ApiOrigin
                    }
                }
            }
        }
    } | ConvertTo-Json -Depth 8

    Write-Host "[API] Actualizando VITE_API_ORIGIN en Cloudflare Pages..."
    $response = Invoke-RestMethod -Method Patch -Uri $uri -Headers $headers -ContentType "application/json" -Body $body
    if (-not $response.success) {
        throw "Cloudflare API no confirmo actualizacion de Pages."
    }

    Write-Host "[OK] VITE_API_ORIGIN actualizado a: $ApiOrigin"

    if (-not [string]::IsNullOrWhiteSpace($deployHookUrl)) {
        Write-Host "[API] Disparando redeploy via deploy hook..."
        Invoke-RestMethod -Method Post -Uri $deployHookUrl | Out-Null
        Write-Host "[OK] Redeploy solicitado."
    } else {
        Write-Host "[INFO] No hay CLOUDFLARE_PAGES_DEPLOY_HOOK_URL. Haga redeploy manual en Pages."
    }
}

Write-Host "[1/3] Verificando prerequisitos..."
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker no esta instalado o no esta en PATH."
}
if (-not (Get-Command cloudflared -ErrorAction SilentlyContinue)) {
    throw "cloudflared no esta instalado o no esta en PATH."
}

Write-Host "[2/3] Levantando backend y servicios..."
docker compose up -d --build
if ($LASTEXITCODE -ne 0) {
    throw "Docker compose fallo. Verifique que Docker Desktop este iniciado y operativo."
}

Write-Host "[3/3] Iniciando tunnel temporal..."
Write-Host "Mantenga esta terminal abierta para que el tunnel siga activo."

$logPath = Join-Path $PSScriptRoot "cloudflared.log"
$errorLogPath = Join-Path $PSScriptRoot "cloudflared.err.log"
if (Test-Path $logPath) {
    Remove-Item $logPath -Force
}
if (Test-Path $errorLogPath) {
    Remove-Item $errorLogPath -Force
}

$process = Start-Process -FilePath "cloudflared" -ArgumentList @("tunnel", "--url", $BackendUrl) -PassThru -RedirectStandardOutput $logPath -RedirectStandardError $errorLogPath

$tunnelUrl = $null
for ($i = 0; $i -lt 60; $i++) {
    if ((Test-Path $logPath) -or (Test-Path $errorLogPath)) {
        $outContent = if (Test-Path $logPath) { Get-Content $logPath -Raw -ErrorAction SilentlyContinue } else { "" }
        $errContent = if (Test-Path $errorLogPath) { Get-Content $errorLogPath -Raw -ErrorAction SilentlyContinue } else { "" }
        $content = "$outContent`n$errContent"
        if ($content -match "https://[a-z0-9-]+\.trycloudflare\.com") {
            $tunnelUrl = $Matches[0]
            break
        }
    }
    Start-Sleep -Seconds 1
}

if ($null -eq $tunnelUrl) {
    Write-Warning "No se detecto URL trycloudflare en 60s. Revise cloudflared.log y cloudflared.err.log"
} else {
    Write-Host "[OK] URL tunnel detectada: $tunnelUrl"
    if ($AutoUpdatePages) {
        Update-CloudflarePagesApiOrigin -ApiOrigin $tunnelUrl
    } else {
        Write-Host "[INFO] AutoUpdatePages desactivado. Actualice manualmente VITE_API_ORIGIN con esa URL."
    }
}

Write-Host "[INFO] Mostrando logs del tunnel. Ctrl+C para salir."
Get-Content $logPath -Wait
