param(
    [string]$BackendUrl = "http://localhost:9090"
)

$ErrorActionPreference = "Stop"

Write-Host "[1/3] Verificando prerequisitos..."
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker no esta instalado o no esta en PATH."
}
if (-not (Get-Command cloudflared -ErrorAction SilentlyContinue)) {
    throw "cloudflared no esta instalado o no esta en PATH."
}

Write-Host "[2/3] Levantando backend y servicios..."
docker compose up -d --build

Write-Host "[3/3] Iniciando tunnel temporal..."
Write-Host "Mantenga esta terminal abierta para que el tunnel siga activo."
cloudflared tunnel --url $BackendUrl
