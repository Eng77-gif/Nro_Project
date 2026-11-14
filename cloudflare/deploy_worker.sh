#!/usr/bin/env bash
# Đưa trang chủ lên Cloudflare Workers bằng API (không cần cài wrangler).
# Cần: CF_API_TOKEN (quyền Edit Cloudflare Workers), CF_ACCOUNT_ID; lần đầu thêm BACKEND_URL, PROXY_KEY (bỏ trống = giữ giá trị cũ). Tuỳ chọn: WORKER_NAME (mặc định nro-web).
set -euo pipefail
cd "$(dirname "$0")"
NAME="${WORKER_NAME:-nro-web}"
API="https://api.cloudflare.com/client/v4/accounts/$CF_ACCOUNT_ID/workers/scripts/$NAME"
if [ -n "${PROXY_KEY:-}" ]; then
  META=$(printf '{"main_module":"worker.js","compatibility_date":"2024-09-23","bindings":[{"type":"plain_text","name":"BACKEND_URL","text":"%s"},{"type":"secret_text","name":"PROXY_KEY","text":"%s"}]}' "$BACKEND_URL" "$PROXY_KEY")
else # cập nhật giao diện: giữ BACKEND_URL / PROXY_KEY đã đặt trên Cloudflare
  META='{"main_module":"worker.js","compatibility_date":"2024-09-23","keep_bindings":["plain_text","secret_text"]}'
fi
ASSET_ARGS=()
IDX=$(mktemp)
echo "// tự tạo bởi deploy_worker.sh" > "$IDX"
ENTRIES=""
n=0
for f in ../data/web/assets/*; do
  b=$(basename "$f")
  case "$b" in *[!A-Za-z0-9._-]*) continue;; esac # chỉ tên file an toàn
  ASSET_ARGS+=(-F "assets/$b=@$f;filename=assets/$b;type=application/octet-stream")
  echo "import a$n from './assets/$b';" >> "$IDX"
  ENTRIES="$ENTRIES'$b': a$n, "
  n=$((n+1))
done
echo "export default { $ENTRIES};" >> "$IDX"
ASSET_ARGS+=(-F "assets_index.js=@$IDX;filename=assets_index.js;type=application/javascript+module")
curl -sS -f -X PUT "$API" -H "Authorization: Bearer $CF_API_TOKEN" \
  -F "metadata=$META;type=application/json" \
  -F "worker.js=@worker.js;filename=worker.js;type=application/javascript+module" \
  -F "proxy.js=@lib/proxy.js;filename=proxy.js;type=application/javascript+module" \
  -F "index.html=@../data/web/index.html;filename=index.html;type=text/plain" "${ASSET_ARGS[@]}" > /dev/null
rm -f "$IDX"
curl -sS -f -X POST "$API/subdomain" -H "Authorization: Bearer $CF_API_TOKEN" -H "Content-Type: application/json" -d '{"enabled":true}' > /dev/null
SUB=$(curl -sS -f "https://api.cloudflare.com/client/v4/accounts/$CF_ACCOUNT_ID/workers/subdomain" -H "Authorization: Bearer $CF_API_TOKEN" | python3 -c "import sys,json;print(json.load(sys.stdin)['result']['subdomain'])")
echo "Đã đưa lên: https://$NAME.$SUB.workers.dev"
