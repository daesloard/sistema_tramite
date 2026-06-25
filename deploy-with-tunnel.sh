#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_URL="${BACKEND_URL:-http://localhost:8088}"
AUTO_UPDATE_PAGES=false

normalize_env_value() {
    local value="${1-}"
    value="${value//$'\r'/}"
    value="${value//$'\n'/}"
    printf '%s' "${value}" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//'
}

usage() {
    cat <<'EOF'
Uso: ./deploy-with-tunnel.sh [--backend-url URL] [--auto-update-pages]

Opciones:
  --backend-url URL      URL local que publicara cloudflared.
                         Default: http://localhost:8088
  --auto-update-pages    Actualiza VITE_API_ORIGIN en Cloudflare Pages.
  -h, --help             Muestra esta ayuda.
EOF
}

update_cloudflare_pages_api_origin() {
    local api_origin="$1"
    local token account_id project_name deploy_hook_url body_file response_file http_code success

    token="$(normalize_env_value "${CLOUDFLARE_API_TOKEN:-}")"
    account_id="$(normalize_env_value "${CLOUDFLARE_ACCOUNT_ID:-}")"
    project_name="$(normalize_env_value "${CLOUDFLARE_PAGES_PROJECT:-}")"
    deploy_hook_url="$(normalize_env_value "${CLOUDFLARE_PAGES_DEPLOY_HOOK_URL:-}")"

    if [[ -z "$token" || -z "$account_id" || -z "$project_name" ]]; then
        echo "[WARN] No se actualizara Cloudflare Pages automaticamente. Configure: CLOUDFLARE_API_TOKEN, CLOUDFLARE_ACCOUNT_ID, CLOUDFLARE_PAGES_PROJECT" >&2
        return 0
    fi

    body_file="$(mktemp)"
    response_file="$(mktemp)"
    trap 'rm -f "$body_file" "$response_file"' RETURN

    cat >"$body_file" <<EOF
{
  "deployment_configs": {
    "production": {
      "env_vars": {
        "VITE_API_ORIGIN": {
          "type": "plain_text",
          "value": "$api_origin"
        }
      }
    },
    "preview": {
      "env_vars": {
        "VITE_API_ORIGIN": {
          "type": "plain_text",
          "value": "$api_origin"
        }
      }
    }
  }
}
EOF

    echo "[API] Actualizando VITE_API_ORIGIN en Cloudflare Pages..."
    http_code="$(curl -sS -o "$response_file" -w '%{http_code}' \
        -X PATCH \
        -H "Authorization: Bearer $token" \
        -H "Content-Type: application/json" \
        --data @"$body_file" \
        "https://api.cloudflare.com/client/v4/accounts/$account_id/pages/projects/$project_name")"

    if [[ "$http_code" != 2* ]]; then
        echo "[ERROR] Cloudflare API respondio con HTTP $http_code" >&2
        cat "$response_file" >&2
        return 1
    fi

    success="$(grep -o '"success":[[:space:]]*[^,]*' "$response_file" | head -n1 | cut -d: -f2 | tr -d '[:space:]')"
    if [[ "$success" != "true" ]]; then
        echo "[ERROR] Cloudflare API no confirmo actualizacion de Pages." >&2
        cat "$response_file" >&2
        return 1
    fi

    echo "[OK] VITE_API_ORIGIN actualizado a: $api_origin"

    if [[ -n "$deploy_hook_url" ]]; then
        echo "[API] Disparando redeploy via deploy hook..."
        curl -sS -X POST "$deploy_hook_url" >/dev/null
        echo "[OK] Redeploy solicitado."
    else
        echo "[INFO] No hay CLOUDFLARE_PAGES_DEPLOY_HOOK_URL. Haga redeploy manual en Pages."
    fi
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --backend-url)
            if [[ $# -lt 2 ]]; then
                echo "Falta valor para --backend-url" >&2
                exit 1
            fi
            BACKEND_URL="$2"
            shift 2
            ;;
        --auto-update-pages)
            AUTO_UPDATE_PAGES=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Argumento no reconocido: $1" >&2
            usage >&2
            exit 1
            ;;
    esac
done

echo "[1/3] Verificando prerequisitos..."
if ! command -v docker >/dev/null 2>&1; then
    echo "Docker no esta instalado o no esta en PATH." >&2
    exit 1
fi
if ! command -v cloudflared >/dev/null 2>&1; then
    echo "cloudflared no esta instalado o no esta en PATH." >&2
    exit 1
fi

echo "[2/3] Levantando backend y servicios..."
(
    cd "$SCRIPT_DIR"
    docker compose up -d --build
)

echo "[3/3] Iniciando tunnel temporal..."
echo "Mantenga esta terminal abierta para que el tunnel siga activo."

log_path="$SCRIPT_DIR/cloudflared.log"
error_log_path="$SCRIPT_DIR/cloudflared.err.log"
rm -f "$log_path" "$error_log_path"

cloudflared tunnel --url "$BACKEND_URL" >"$log_path" 2>"$error_log_path" &
cloudflared_pid=$!

cleanup() {
    if [[ -n "${cloudflared_pid:-}" ]] && kill -0 "$cloudflared_pid" >/dev/null 2>&1; then
        kill "$cloudflared_pid" >/dev/null 2>&1 || true
    fi
}

trap cleanup EXIT INT TERM

tunnel_url=""
for _ in $(seq 1 60); do
    combined_content="$(cat "$log_path" "$error_log_path" 2>/dev/null || true)"
    tunnel_url="$(printf '%s\n' "$combined_content" | grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' | head -n1 || true)"
    if [[ -n "$tunnel_url" ]]; then
        break
    fi
    sleep 1
done

if [[ -z "$tunnel_url" ]]; then
    echo "[WARN] No se detecto URL trycloudflare en 60s. Revise cloudflared.log y cloudflared.err.log" >&2
else
    echo "[OK] URL tunnel detectada: $tunnel_url"
    if [[ "$AUTO_UPDATE_PAGES" == true ]]; then
        update_cloudflare_pages_api_origin "$tunnel_url"
    else
        echo "[INFO] AutoUpdatePages desactivado. Actualice manualmente VITE_API_ORIGIN con esa URL."
    fi
fi

echo "[INFO] Mostrando logs del tunnel. Ctrl+C para salir."
touch "$log_path"
tail -n +1 -f "$log_path"