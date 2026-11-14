#!/usr/bin/env bash
# Gom trang web tĩnh vào public/ cho Cloudflare Pages (nguồn duy nhất: data/web/index.html, server game cũng dùng file này).
set -euo pipefail
cd "$(dirname "$0")"
rm -rf public
mkdir -p public
cp ../data/web/index.html public/index.html
cp -r ../data/web/assets public/assets
cp static/_headers public/_headers
echo "Đã gom trang web vào cloudflare/public"
