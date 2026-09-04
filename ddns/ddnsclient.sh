#!/usr/bin/env bash
set -u

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
CONFIG=${1:-"${SCRIPT_DIR}/ddnsclient.conf"}

if [[ ! -f "$CONFIG" ]]; then
    printf '配置文件不存在: %s\n' "$CONFIG" >&2
    exit 1
fi

# 配置文件由管理员维护；source 仅适用于可信配置文件。
# shellcheck source=/dev/null
source "$CONFIG"

: "${URL:?缺少 URL}"
: "${CLIENT_ID:?缺少 CLIENT_ID}"
: "${CLIENT_SECRET:?缺少 CLIENT_SECRET}"
: "${INTERVAL:?缺少 INTERVAL}"
: "${TIMEOUT:?缺少 TIMEOUT}"
: "${RETRY:?缺少 RETRY}"
: "${IP_CMD:?缺少 IP_CMD}"

read -r -a IP_CMD_ARGS <<< "$IP_CMD"
if [[ ${#IP_CMD_ARGS[@]} -eq 0 ]]; then
    printf 'IP_CMD 不能为空\n' >&2
    exit 1
fi
IP_SCRIPT="${SCRIPT_DIR}/getipcmd/${IP_CMD_ARGS[0]}"
if [[ ! -x "$IP_SCRIPT" ]]; then
    printf '取 IP 脚本不存在或不可执行: %s\n' "$IP_SCRIPT" >&2
    exit 1
fi

while true; do
    IP=$("$IP_SCRIPT" "${IP_CMD_ARGS[@]:1}" 2>/dev/null | tr -d '[:space:]') || IP=""
    if [[ -z "$IP" ]]; then
        printf '获取 IP 失败\n' >&2
        sleep "$INTERVAL"
        continue
    fi

    TIMESTAMP=$(date +%s)
    SIGNATURE=$(printf '%s\n%s\n%s' "$CLIENT_ID" "$TIMESTAMP" "$IP" |
        openssl dgst -sha256 -hmac "$CLIENT_SECRET" | awk '{print $2}')
    JSON=$(printf '{"client_id":%s,"timestamp":%s,"ip":"%s","signature":"%s"}' \
        "$CLIENT_ID" "$TIMESTAMP" "$IP" "$SIGNATURE")

    success=false
    for ((i = 1; i <= RETRY; i++)); do
        printf 'HTTPS retry %d/%d\n' "$i" "$RETRY"
        if curl --fail --silent --show-error --max-time "$TIMEOUT" \
            -H 'Content-Type: application/json' -d "$JSON" "$URL" >/dev/null; then
            success=true
            break
        fi
    done

    if [[ "$success" != true ]]; then
        printf 'HTTPS 更新失败\n' >&2
    fi
    sleep "$INTERVAL"
done
