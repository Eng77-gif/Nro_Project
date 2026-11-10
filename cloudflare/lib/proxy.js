// Cloudflare Pages Function: chuyển tiếp /api/* và /icon/* sang web server trong server game (BACKEND_URL, thường là
// hostname của Cloudflare Tunnel), kèm IP thật của người chơi. Trình duyệt chỉ thấy 1 tên miền nên không cần CORS.
// Biến môi trường (Pages > Settings > Variables): BACKEND_URL (vd. https://api.ten-mien.com), PROXY_KEY (bí mật, trùng web.proxy.key).
export async function proxy({ request, env }) {
  const backend = String(env.BACKEND_URL || '').replace(/\/+$/, '');
  if (!backend) return json(503, 'Trang web chưa cấu hình BACKEND_URL');
  const url = new URL(request.url);
  const headers = new Headers();
  for (const h of ['content-type', 'x-token']) {
    const v = request.headers.get(h);
    if (v) headers.set(h, v);
  }
  // server chỉ tin IP này khi kèm đúng PROXY_KEY (xem WebServer.clientIp)
  headers.set('x-nro-client-ip', request.headers.get('cf-connecting-ip') || '');
  if (env.PROXY_KEY) headers.set('x-nro-proxy-key', env.PROXY_KEY);
  const init = { method: request.method, headers, redirect: 'manual' };
  if (request.method !== 'GET' && request.method !== 'HEAD') init.body = await request.arrayBuffer();
  try {
    const res = await fetch(backend + url.pathname + url.search, init);
    const type = res.headers.get('content-type') || '';
    if (url.pathname.startsWith('/api/') && !type.includes('json')) {
      // không phải web server của game trả lời (server cũ chưa có web, cổng 8080 bị chương trình khác chiếm...)
      return json(502, `Máy chủ game chưa bật trang web (HTTP ${res.status}). Cập nhật server bản mới rồi khởi động lại.`);
    }
    const out = new Headers({ 'x-content-type-options': 'nosniff' });
    for (const h of ['content-type', 'cache-control']) {
      const v = res.headers.get(h);
      if (v) out.set(h, v);
    }
    return new Response(res.body, { status: res.status, headers: out });
  } catch (e) {
    return json(502, 'Máy chủ game đang bảo trì hoặc không kết nối được, thử lại sau');
  }
}

function json(status, error) {
  return new Response(JSON.stringify({ error }), { status, headers: { 'content-type': 'application/json; charset=utf-8' } });
}
