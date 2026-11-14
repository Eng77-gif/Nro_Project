// Cloudflare Worker: trang chủ người chơi (data/web/index.html) + chuyển tiếp /api/*, /icon/* về server game (lib/proxy.js).
// Triển khai: bash deploy_worker.sh (xem docs/HUONG_DAN_WEB_CLOUDFLARE_SEPAY.md). Biến: BACKEND_URL, PROXY_KEY.
import { proxy } from './proxy.js';
import html from './index.html';
// ảnh giao diện (data/web/assets) đóng gói kèm worker; assets_index.js do deploy_worker.sh tự tạo theo thư mục ảnh
import ASSETS from './assets_index.js';
const TYPES = { png: 'image/png', gif: 'image/gif', jpg: 'image/jpeg', webp: 'image/webp' };

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
    if (path.startsWith('/api/') || path.startsWith('/icon/') || path.startsWith('/callback/')) return proxy({ request, env }); // callback: webhook SePay, cổng thẻ cào
    if (path.startsWith('/assets/')) {
      const name = path.slice(8), data = Object.hasOwn(ASSETS, name) ? ASSETS[name] : null;
      if (!data) return new Response('Not found', { status: 404 });
      return new Response(data, { headers: { 'content-type': TYPES[name.split('.').pop()] || 'application/octet-stream',
        'cache-control': 'public, max-age=86400', 'x-content-type-options': 'nosniff' } });
    }
    return new Response(html, { headers: HEADERS });
  },
};
