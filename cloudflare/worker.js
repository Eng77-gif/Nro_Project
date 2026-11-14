// Cloudflare Worker: trang chủ người chơi (data/web/index.html) + chuyển tiếp /api/*, /icon/* về server game (lib/proxy.js).
// Triển khai: bash deploy_worker.sh (xem docs/HUONG_DAN_WEB_CLOUDFLARE_SEPAY.md). Biến: BACKEND_URL, PROXY_KEY.
import { proxy } from './proxy.js';
import html from './index.html';
// ảnh giao diện (data/web/assets) đóng gói kèm worker, không phụ thuộc server game
import A_android_png from './assets/android.png';
import A_event_halloween_png from './assets/event-halloween.png';
import A_event_october_png from './assets/event-october.png';
import A_hero_png from './assets/hero.png';
import A_jar_png from './assets/jar.png';
import A_logo_png from './assets/logo.png';
import A_namek_gif from './assets/namek.gif';
import A_napngoc_png from './assets/napngoc.png';
import A_pc_png from './assets/pc.png';
import A_traidat_gif from './assets/traidat.gif';
import A_xayda_gif from './assets/xayda.gif';

const ASSETS = {
  'android.png': A_android_png,
  'event-halloween.png': A_event_halloween_png,
  'event-october.png': A_event_october_png,
  'hero.png': A_hero_png,
  'jar.png': A_jar_png,
  'logo.png': A_logo_png,
  'namek.gif': A_namek_gif,
  'napngoc.png': A_napngoc_png,
  'pc.png': A_pc_png,
  'traidat.gif': A_traidat_gif,
  'xayda.gif': A_xayda_gif,
};
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
    if (path.startsWith('/api/') || path.startsWith('/icon/')) return proxy({ request, env });
    if (path.startsWith('/assets/')) {
      const name = path.slice(8), data = Object.hasOwn(ASSETS, name) ? ASSETS[name] : null;
      if (!data) return new Response('Not found', { status: 404 });
      return new Response(data, { headers: { 'content-type': TYPES[name.split('.').pop()] || 'application/octet-stream',
        'cache-control': 'public, max-age=86400', 'x-content-type-options': 'nosniff' } });
    }
    return new Response(html, { headers: HEADERS });
  },
};
