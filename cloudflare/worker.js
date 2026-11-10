// Cloudflare Worker: trang chủ người chơi (data/web/index.html) + chuyển tiếp /api/*, /icon/* về server game (lib/proxy.js).
// Triển khai: bash deploy_worker.sh (xem docs/HUONG_DAN_WEB_CLOUDFLARE_SEPAY.md). Biến: BACKEND_URL, PROXY_KEY.
import { proxy } from './proxy.js';
import html from './index.html';

const HEADERS = {
  'content-type': 'text/html; charset=utf-8',
  'cache-control': 'no-cache',
  'x-content-type-options': 'nosniff',
  'x-frame-options': 'DENY',
  'referrer-policy': 'strict-origin-when-cross-origin',
};

export default {
  async fetch(request, env) {
    const path = new URL(request.url).pathname;
    if (path.startsWith('/api/') || path.startsWith('/icon/')) return proxy({ request, env });
    return new Response(html, { headers: HEADERS });
  },
};
