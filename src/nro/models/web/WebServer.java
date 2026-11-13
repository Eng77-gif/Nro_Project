package nro.models.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import nro.models.panel.PanelApi;
import nro.models.panel.PanelServer;
import nro.models.player.Player;
import nro.models.server.Client;
import nro.models.services.SeasonEventService;
import nro.models.utils.Logger;

/**
 * Trang web cho người chơi (cổng web.port, giao diện data/web/index.html): trang chủ, giới thiệu, diễn đàn, tố cáo, báo lỗi,
 * góp ý, nạp ngọc. Đăng nhập bằng tài khoản game. Nạp: chuyển khoản QR (admin duyệt trên panel) hoặc thẻ cào qua cổng gạch thẻ
 * chuẩn "chargingws/v2" (thesieure, cardvip, doithe1s...), tiền nạp cộng vào số dư account.vnd + tongnap (đổi ra ngọc trong game).
 */
public class WebServer {

    private static WebServer instance;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    /** Loại bài: diễn đàn công khai, còn lại là phiếu riêng (chỉ người gửi và admin xem). */
    public static final List<String> KINDS = List.of("forum", "report", "bug", "idea");

    private HttpServer http;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, long[]> limits = new ConcurrentHashMap<>(); // khoá -> {số lần, mốc thời gian}
    private final Properties cfg = new Properties();
    private String serverName = "NRO";
    private volatile Map<String, Object> statusCache;
    private volatile long statusAt;

    private record Session(int accountId, String username, long expire) {
    }

    public static WebServer gI() {
        if (instance == null) {
            instance = new WebServer();
        }
        return instance;
    }

    public void start() {
        try {
            try (InputStream in = new FileInputStream("Config.properties")) {
                cfg.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            }
            if ("false".equalsIgnoreCase(cfg.getProperty("web.enable", "true").trim())) {
                Logger.log(Logger.YELLOW, "Web người chơi đang tắt (web.enable=false)\n");
                return;
            }
            serverName = cfg.getProperty("server.name", "NRO").trim();
            int port = Integer.parseInt(cfg.getProperty("web.port", "8080").trim());
            http = HttpServer.create(new InetSocketAddress(cfg.getProperty("web.host", "0.0.0.0").trim(), port), 0);
            http.setExecutor(Executors.newFixedThreadPool(6, r -> {
                Thread t = new Thread(r, "Web nguoi choi");
                t.setDaemon(true);
                return t;
            }));
            http.createContext("/", this::handle);
            http.start();
            Logger.success("Web người chơi: http://<ip máy chủ>:" + port + "/\n");
        } catch (Exception e) {
            Logger.error("Không mở được web người chơi: " + e.getMessage() + "\n");
        }
    }

    private String conf(String k) {
        return cfg.getProperty(k, "").trim();
    }

    // ------------------------------------------------------------------ định tuyến

    private void handle(HttpExchange ex) {
        try {
            String path = ex.getRequestURI().getPath();
            if (path.startsWith("/api/")) {
                api(ex, path.substring(5));
            } else if (path.startsWith("/icon/")) {
                PanelServer.serveIcon(ex, path.substring(6));
            } else if (path.startsWith("/assets/")) { // ảnh giao diện (data/web/assets), chỉ tên file chữ / số
                String name = path.substring(8);
                File f = new File("data/web/assets", name);
                if (!name.matches("[A-Za-z0-9_-]+\\.(png|gif|jpg|webp)") || !f.isFile()) {
                    PanelServer.sendText(ex, 404, "text/plain; charset=utf-8", "Không có".getBytes(StandardCharsets.UTF_8));
                    return;
                }
                String type = name.endsWith(".gif") ? "image/gif" : name.endsWith(".jpg") ? "image/jpeg" : name.endsWith(".webp") ? "image/webp" : "image/png";
                ex.getResponseHeaders().add("Cache-Control", "public, max-age=86400");
                PanelServer.sendText(ex, 200, type, Files.readAllBytes(f.toPath()));
            } else if (path.equals("/callback/card")) {
                cardCallback(ex);
            } else if (path.equals("/callback/sepay")) {
                sepayCallback(ex);
            } else {
                File f = new File("data/web/index.html");
                if (!f.exists()) {
                    PanelServer.sendText(ex, 404, "text/plain; charset=utf-8", "Thiếu data/web/index.html".getBytes(StandardCharsets.UTF_8));
                    return;
                }
                ex.getResponseHeaders().add("Cache-Control", "no-cache");
                PanelServer.sendText(ex, 200, "text/html; charset=utf-8", Files.readAllBytes(f.toPath()));
            }
        } catch (ApiError e) {
            send(ex, e.code, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            Logger.error("Web người chơi: " + e + "\n");
            send(ex, 500, Map.of("error", "Lỗi máy chủ, thử lại sau"));
        } finally {
            ex.close();
        }
    }

    private static void send(HttpExchange ex, int code, Object o) {
        try {
            PanelServer.sendJson(ex, code, o);
        } catch (Exception ignored) {
        }
    }

    private static class ApiError extends RuntimeException {

        final int code;

        ApiError(int code, String msg) {
            super(msg);
            this.code = code;
        }
    }

    private void api(HttpExchange ex, String route) throws Exception {
        Map<String, String> q = PanelServer.query(ex);
        Map<String, Object> b = new HashMap<>();
        boolean post = "POST".equals(ex.getRequestMethod());
        if (post) {
            byte[] raw = ex.getRequestBody().readNBytes(64_000);
            Map<String, Object> m = raw.length == 0 ? null : GSON.fromJson(new String(raw, StandardCharsets.UTF_8), new TypeToken<Map<String, Object>>() {
            }.getType());
            if (m != null) {
                b = m;
            }
        }
        String ip = clientIp(ex);
        Session s = session(ex);
        Object res = switch (route) {
            case "status" -> status();
            case "login" -> login(ip, b);
            case "logout" -> {
                String t = ex.getRequestHeaders().getFirst("X-Token");
                if (t != null) {
                    sessions.remove(t);
                }
                yield Map.of("ok", true);
            }
            case "me" -> me(need(s));
            case "posts" -> posts(s, q);
            case "post" -> post(s, intv(q.get("id")));
            case "post/create" -> createPost(need(s), b);
            case "post/reply" -> reply(need(s), b);
            case "topup/info" -> topupInfo();
            case "topup/bank" -> topupBank(need(s), b);
            case "topup/card" -> topupCard(need(s), b);
            case "topup/history" -> PanelApi.rows("select id, method, amount, telco, concat(left(serial, 4), '****') serial, status, note, credited, "
                    + "date_format(created_at, '%d/%m/%Y %H:%i') created_at from web_topup where account_id = ? order by id desc limit 50", need(s).accountId);
            default -> throw new ApiError(404, "Không có chức năng này");
        };
        send(ex, 200, res);
    }

    // ------------------------------------------------------------------ Cloudflare

    /** Dải IP của Cloudflare (https://www.cloudflare.com/ips/). */
    private static final String[] CF_RANGES = {"173.245.48.0/20", "103.21.244.0/22", "103.22.200.0/22", "103.31.4.0/22", "141.101.64.0/18",
        "108.162.192.0/18", "190.93.240.0/20", "188.114.96.0/20", "197.234.240.0/22", "198.41.128.0/17", "162.158.0.0/15", "104.16.0.0/13",
        "104.24.0.0/14", "172.64.0.0/13", "131.0.72.0/22", "2400:cb00::/32", "2606:4700::/32", "2803:f800::/32", "2405:b500::/32",
        "2405:8100::/32", "2a06:98c0::/29", "2c0f:f248::/32"};

    /**
     * IP thật của người chơi. Khi web.cloudflare=true và yêu cầu đến từ Cloudflare (proxy DNS) hoặc từ chính máy chủ (Cloudflare
     * Tunnel / cloudflared chạy trên VPS) thì lấy theo header CF-Connecting-IP; nguồn khác không được tin header này (chống giả IP).
     */
    String clientIp(HttpExchange ex) {
        java.net.InetAddress remote = ex.getRemoteAddress().getAddress();
        // trang chủ chạy riêng trên Cloudflare Pages (thư mục cloudflare/): Pages Function gửi kèm IP người chơi và khoá web.proxy.key
        String key = conf("web.proxy.key"), given = ex.getRequestHeaders().getFirst("X-NRO-Proxy-Key"), pip = ex.getRequestHeaders().getFirst("X-NRO-Client-IP");
        if (!key.isEmpty() && given != null && pip != null && !pip.isBlank()
                && MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8))) {
            return pip.trim();
        }
        String cf = ex.getRequestHeaders().getFirst("CF-Connecting-IP");
        if (cf != null && "true".equalsIgnoreCase(conf("web.cloudflare")) && (remote.isLoopbackAddress() || fromCloudflare(remote))) {
            return cf.trim();
        }
        return remote.getHostAddress();
    }

    static boolean fromCloudflare(java.net.InetAddress a) {
        byte[] ip = a.getAddress();
        for (String c : CF_RANGES) {
            try {
                String[] p = c.split("/");
                byte[] net = java.net.InetAddress.getByName(p[0]).getAddress();
                if (net.length != ip.length) {
                    continue;
                }
                int bits = Integer.parseInt(p[1]), i = 0;
                for (; bits >= 8; bits -= 8, i++) {
                    if (ip[i] != net[i]) {
                        break;
                    }
                }
                if (bits >= 8) {
                    continue; // khác ở byte nguyên
                }
                int mask = bits == 0 ? 0 : (0xFF << (8 - bits)) & 0xFF;
                if (i >= ip.length || (ip[i] & mask) == (net[i] & mask)) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private Session session(HttpExchange ex) {
        String t = ex.getRequestHeaders().getFirst("X-Token");
        Session s = t == null ? null : sessions.get(t);
        if (s != null && s.expire < System.currentTimeMillis()) {
            sessions.remove(t);
            return null;
        }
        return s;
    }

    private static Session need(Session s) {
        if (s == null) {
            throw new ApiError(401, "Vui lòng đăng nhập");
        }
        return s;
    }

    /** Giới hạn số lần trong một khoảng thời gian (chống spam). */
    private void limit(String key, int max, long windowMs, String msg) {
        long now = System.currentTimeMillis();
        long[] v = limits.compute(key, (k, o) -> o == null || now - o[1] > windowMs ? new long[]{1, now} : new long[]{o[0] + 1, o[1]});
        if (v[0] > max) {
            throw new ApiError(429, msg);
        }
    }

    // ------------------------------------------------------------------ trang chủ

    private Map<String, Object> status() throws Exception {
        if (statusCache != null && System.currentTimeMillis() - statusAt < 60_000) {
            Map<String, Object> m = new LinkedHashMap<>(statusCache);
            m.put("online", Client.gI().getPlayers().size());
            return m;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", serverName);
        m.put("online", Client.gI().getPlayers().size());
        m.put("accounts", PanelApi.rows("select count(*) n from account").get(0).get("n"));
        m.put("download", Map.of("apk", conf("web.download.apk"), "pc", conf("web.download.pc"), "ios", conf("web.download.ios")));
        List<Map<String, Object>> events = new ArrayList<>();
        for (SeasonEventService.Season e : SeasonEventService.gI().activeSeasons()) {
            events.add(Map.of("name", e.name, "description", e.description == null ? "" : e.description, "end", e.end == null ? "" : e.end.toString()));
        }
        m.put("events", events);
        m.put("top", topPower());
        m.put("news", PanelApi.rows("select id, kind, title, author, replies, date_format(created_at, '%d/%m/%Y') created_at from web_post "
                + "where kind = 'forum' order by pinned desc, id desc limit 6"));
        statusCache = m;
        statusAt = System.currentTimeMillis();
        return m;
    }

    private List<Map<String, Object>> topPower() throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : PanelApi.rows("select p.name, p.gender, p.data_point from player p where p." + nro.models.server.Manager.NOT_ADMIN + " and p.account_id not in "
                + "(select pl.account_id from bot_account b join player pl on pl.id = b.player_id)")) {
            try {
                @SuppressWarnings("deprecation") JsonArray pt = new JsonParser().parse(String.valueOf(r.get("data_point"))).getAsJsonArray();
                out.add(new LinkedHashMap<>(Map.of("name", r.get("name"), "gender", r.get("gender"), "power", pt.size() > 1 ? pt.get(1).getAsLong() : 0L)));
            } catch (Exception ignored) {
            }
        }
        out.sort((x, y) -> Long.compare((Long) y.get("power"), (Long) x.get("power")));
        return new ArrayList<>(out.subList(0, Math.min(10, out.size())));
    }

    // ------------------------------------------------------------------ tài khoản

    private Map<String, Object> login(String ip, Map<String, Object> b) throws Exception {
        limit("login:" + ip, 8, 5 * 60_000L, "Đăng nhập sai quá nhiều lần, thử lại sau 5 phút");
        String u = str(b.get("user")).trim(), pw = str(b.get("pass"));
        if (u.isEmpty() || u.length() > 20 || pw.isEmpty() || pw.length() > 100) {
            throw new ApiError(400, "Nhập tài khoản và mật khẩu");
        }
        List<Map<String, Object>> r = PanelApi.rows("select id, username, password, ban from account where username = ?", u);
        // so khớp mật khẩu như khi đăng nhập game (account.password)
        if (r.isEmpty() || !MessageDigest.isEqual(str(r.get(0).get("password")).getBytes(StandardCharsets.UTF_8), pw.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiError(401, "Sai tài khoản hoặc mật khẩu");
        }
        int id = ((Number) r.get(0).get("id")).intValue();
        if (intv(r.get(0).get("ban")) == 1) {
            throw new ApiError(403, "Tài khoản đang bị khoá");
        }
        if (nro.models.Bot.BotWorld.gI().isBotAccount(id)) {
            throw new ApiError(403, "Đây là tài khoản bot của máy chủ");
        }
        limits.remove("login:" + ip);
        byte[] raw = new byte[24];
        random.nextBytes(raw);
        StringBuilder t = new StringBuilder();
        for (byte x : raw) {
            t.append(String.format("%02x", x));
        }
        sessions.put(t.toString(), new Session(id, str(r.get(0).get("username")), System.currentTimeMillis() + 7 * 24 * 3600_000L));
        return Map.of("ok", true, "token", t.toString());
    }

    private Map<String, Object> me(Session s) throws Exception {
        Map<String, Object> m = new LinkedHashMap<>(PanelApi.rows("select username, vnd, tongnap from account where id = ?", s.accountId).get(0));
        m.put("characters", PanelApi.rows("select name, gender from player where account_id = ?", s.accountId));
        return m;
    }

    /** Tên hiển thị: tên nhân vật của tài khoản, chưa có nhân vật thì tên tài khoản. */
    private static String author(Session s) throws Exception {
        List<Map<String, Object>> r = PanelApi.rows("select name from player where account_id = ? limit 1", s.accountId);
        return r.isEmpty() ? s.username : str(r.get(0).get("name"));
    }

    // ------------------------------------------------------------------ diễn đàn, tố cáo, báo lỗi, góp ý

    private Map<String, Object> posts(Session s, Map<String, String> q) throws Exception {
        String kind = str(q.get("kind"));
        if (!KINDS.contains(kind)) {
            throw new ApiError(400, "Sai mục");
        }
        if (!kind.equals("forum") && s == null) {
            throw new ApiError(401, "Vui lòng đăng nhập để xem phiếu của bạn");
        }
        int size = 15, page = Math.max(1, intv(q.getOrDefault("page", "1")));
        String where = kind.equals("forum") ? "kind = 'forum'" : "kind = ? and account_id = " + s.accountId;
        Object[] args = kind.equals("forum") ? new Object[0] : new Object[]{kind};
        long total = ((Number) PanelApi.rows("select count(*) n from web_post where " + where, args).get(0).get("n")).longValue();
        List<Object> a = new ArrayList<>(List.of(args));
        a.add(size);
        a.add((page - 1) * size);
        List<Map<String, Object>> rows = PanelApi.rows("select id, title, author, target, status, pinned, replies, date_format(created_at, '%d/%m/%Y %H:%i') created_at, "
                + "date_format(updated_at, '%d/%m/%Y %H:%i') updated_at from web_post where " + where + " order by pinned desc, updated_at desc limit ? offset ?", a.toArray());
        return Map.of("rows", rows, "page", page, "pages", Math.max(1, (total + size - 1) / size), "total", total);
    }

    private Map<String, Object> post(Session s, int id) throws Exception {
        List<Map<String, Object>> r = PanelApi.rows("select id, kind, account_id, title, author, target, content, status, pinned, replies, "
                + "date_format(created_at, '%d/%m/%Y %H:%i') created_at from web_post where id = ?", id);
        if (r.isEmpty()) {
            throw new ApiError(404, "Bài viết không tồn tại");
        }
        Map<String, Object> p = new LinkedHashMap<>(r.get(0));
        boolean mine = s != null && intv(p.get("account_id")) == s.accountId;
        if (!"forum".equals(p.get("kind")) && !mine) {
            throw new ApiError(403, "Bạn không xem được phiếu này");
        }
        p.remove("account_id");
        p.put("mine", mine);
        p.put("replyList", PanelApi.rows("select author, is_admin, content, date_format(created_at, '%d/%m/%Y %H:%i') created_at from web_reply where post_id = ? order by id", id));
        return p;
    }

    private Map<String, Object> createPost(Session s, Map<String, Object> b) throws Exception {
        String kind = str(b.get("kind")), title = str(b.get("title")).trim(), content = str(b.get("content")).trim(), target = str(b.get("target")).trim();
        if (!KINDS.contains(kind)) {
            throw new ApiError(400, "Sai mục");
        }
        if (title.length() < 4 || title.length() > 150) {
            throw new ApiError(400, "Tiêu đề từ 4 đến 150 ký tự");
        }
        if (content.length() < 10 || content.length() > 5000) {
            throw new ApiError(400, "Nội dung từ 10 đến 5.000 ký tự");
        }
        if (kind.equals("report") && (target.isEmpty() || target.length() > 64)) {
            throw new ApiError(400, "Nhập tên nhân vật bị tố cáo");
        }
        limit("post:" + kind + ":" + s.accountId, 1, 30_000L, "Bạn đăng quá nhanh, đợi 30 giây");
        PanelApi.update("insert into web_post (kind, account_id, author, title, target, content) values (?, ?, ?, ?, ?, ?)",
                kind, s.accountId, author(s), title, kind.equals("report") ? target : null, content);
        statusCache = null;
        return Map.of("ok", true, "id", PanelApi.rows("select max(id) id from web_post where account_id = ?", s.accountId).get(0).get("id"));
    }

    private Map<String, Object> reply(Session s, Map<String, Object> b) throws Exception {
        int id = intv(b.get("id"));
        String content = str(b.get("content")).trim();
        if (content.length() < 2 || content.length() > 2000) {
            throw new ApiError(400, "Bình luận từ 2 đến 2.000 ký tự");
        }
        List<Map<String, Object>> r = PanelApi.rows("select kind, account_id from web_post where id = ?", id);
        if (r.isEmpty()) {
            throw new ApiError(404, "Bài viết không tồn tại");
        }
        if (!"forum".equals(r.get(0).get("kind")) && intv(r.get(0).get("account_id")) != s.accountId) {
            throw new ApiError(403, "Bạn không trả lời được phiếu này");
        }
        limit("reply:" + s.accountId, 1, 10_000L, "Bạn bình luận quá nhanh, đợi 10 giây");
        PanelApi.update("insert into web_reply (post_id, account_id, author, content) values (?, ?, ?, ?)", id, s.accountId, author(s), content);
        PanelApi.update("update web_post set replies = replies + 1, updated_at = now() where id = ?", id);
        return Map.of("ok", true);
    }

    // ------------------------------------------------------------------ nạp ngọc

    private Map<String, Object> topupInfo() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bank", conf("web.bank.code").isEmpty() || conf("web.bank.account").isEmpty() ? null
                : Map.of("code", conf("web.bank.code"), "account", conf("web.bank.account"), "owner", conf("web.bank.owner")));
        m.put("card", !conf("web.card.partner_id").isEmpty() && !conf("web.card.partner_key").isEmpty());
        m.put("cardRate", cardRate());
        m.put("amounts", List.of(10000, 20000, 50000, 100000, 200000, 500000));
        m.put("telcos", List.of("VIETTEL", "VINAPHONE", "MOBIFONE", "VNMOBI", "ZING", "GATE"));
        return m;
    }

    private int cardRate() {
        try {
            return Math.max(1, Math.min(100, Integer.parseInt(conf("web.card.rate").isEmpty() ? "100" : conf("web.card.rate"))));
        } catch (NumberFormatException e) {
            return 100;
        }
    }

    private Map<String, Object> topupBank(Session s, Map<String, Object> b) throws Exception {
        if (conf("web.bank.code").isEmpty() || conf("web.bank.account").isEmpty()) {
            throw new ApiError(400, "Máy chủ chưa mở nạp chuyển khoản");
        }
        int amount = intv(b.get("amount"));
        if (amount < 10_000 || amount > 50_000_000) {
            throw new ApiError(400, "Số tiền từ 10.000đ đến 50.000.000đ");
        }
        limit("topup:" + s.accountId, 3, 60_000L, "Tạo yêu cầu quá nhanh, đợi 1 phút");
        PanelApi.update("insert into web_topup (account_id, username, method, amount) values (?, ?, 'bank', ?)", s.accountId, s.username, amount);
        int id = intv(PanelApi.rows("select max(id) id from web_topup where account_id = ?", s.accountId).get(0).get("id"));
        String memo = "NAP" + id + " " + s.username;
        PanelApi.update("update web_topup set note = ? where id = ?", memo, id);
        return Map.of("ok", true, "id", id, "memo", memo, "amount", amount, "bank", conf("web.bank.code"), "account", conf("web.bank.account"), "owner", conf("web.bank.owner"));
    }

    private Map<String, Object> topupCard(Session s, Map<String, Object> b) throws Exception {
        String telco = str(b.get("telco")).toUpperCase(), serial = str(b.get("serial")).replaceAll("\\s", ""), code = str(b.get("code")).replaceAll("\\s", "");
        int amount = intv(b.get("amount"));
        if (!((List<?>) topupInfo().get("telcos")).contains(telco) || !((List<?>) topupInfo().get("amounts")).contains(amount)) {
            throw new ApiError(400, "Chọn nhà mạng và mệnh giá");
        }
        if (!serial.matches("[0-9A-Za-z]{6,20}") || !code.matches("[0-9A-Za-z]{6,20}")) {
            throw new ApiError(400, "Số seri và mã thẻ chỉ gồm chữ số / chữ cái, 6 - 20 ký tự");
        }
        if (!PanelApi.rows("select 1 from web_topup where method = 'card' and serial = ? and code = ? and status <> 2", serial, code).isEmpty()) {
            throw new ApiError(400, "Thẻ này đã được gửi");
        }
        limit("topup:" + s.accountId, 3, 60_000L, "Nạp quá nhanh, đợi 1 phút");
        PanelApi.update("insert into web_topup (account_id, username, method, amount, telco, serial, code) values (?, ?, 'card', ?, ?, ?, ?)",
                s.accountId, s.username, amount, telco, serial, code);
        int id = intv(PanelApi.rows("select max(id) id from web_topup where account_id = ? and method = 'card'", s.accountId).get(0).get("id"));
        if (!conf("web.card.partner_id").isEmpty() && !conf("web.card.partner_key").isEmpty()) {
            sendCard(id, telco, serial, code, amount);
        } else {
            PanelApi.update("update web_topup set note = 'Chờ admin kiểm tra thẻ' where id = ?", id);
        }
        return Map.of("ok", true, "id", id);
    }

    /** Gửi thẻ sang cổng gạch thẻ (giao thức chargingws/v2): sign = md5(partner_key + code + serial). */
    private void sendCard(int id, String telco, String serial, String code, int amount) {
        try {
            String url = conf("web.card.url").isEmpty() ? "https://thesieure.com/chargingws/v2" : conf("web.card.url");
            Map<String, String> f = new LinkedHashMap<>();
            f.put("telco", telco);
            f.put("code", code);
            f.put("serial", serial);
            f.put("amount", String.valueOf(amount));
            f.put("request_id", String.valueOf(id));
            f.put("partner_id", conf("web.card.partner_id"));
            f.put("sign", md5(conf("web.card.partner_key") + code + serial));
            f.put("command", "charging");
            StringBuilder body = new StringBuilder();
            for (Map.Entry<String, String> e : f.entrySet()) {
                body.append(body.length() > 0 ? "&" : "").append(e.getKey()).append('=').append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
            }
            HttpResponse<String> res = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build().send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(), HttpResponse.BodyHandlers.ofString());
            Map<String, Object> r = GSON.fromJson(res.body(), new TypeToken<Map<String, Object>>() {
            }.getType());
            int st = r == null ? -1 : intv(r.get("status"));
            String msg = r == null ? "Cổng thẻ không phản hồi" : str(r.get("message"));
            if (st == 99) {
                PanelApi.update("update web_topup set note = 'Đang chờ cổng thẻ xử lý' where id = ?", id);
            } else if (st == 1 || st == 2) {
                finishCard(id, st, intv(r.get("value")), msg);
            } else {
                PanelApi.update("update web_topup set status = 2, note = ?, updated_at = now() where id = ? and status = 0", "Thẻ lỗi: " + msg, id);
            }
        } catch (Exception e) {
            Logger.error("Gửi thẻ cào #" + id + ": " + e.getMessage() + "\n");
            try {
                PanelApi.update("update web_topup set note = 'Chưa gửi được sang cổng thẻ, admin sẽ kiểm tra' where id = ?", id);
            } catch (Exception ignored) {
            }
        }
    }

    /** Cổng thẻ gọi về khi xử lý xong: kiểm tra callback_sign = md5(partner_key + code + serial). */
    private void cardCallback(HttpExchange ex) throws Exception {
        Map<String, String> q = new HashMap<>(PanelServer.query(ex));
        if ("POST".equals(ex.getRequestMethod())) {
            String raw = new String(ex.getRequestBody().readNBytes(16_000), StandardCharsets.UTF_8).trim();
            if (raw.startsWith("{")) {
                Map<String, Object> m = GSON.fromJson(raw, new TypeToken<Map<String, Object>>() {
                }.getType());
                if (m != null) {
                    m.forEach((k, v) -> q.put(k, v instanceof Double d && d == Math.floor(d) ? String.valueOf(d.longValue()) : str(v)));
                }
            } else {
                for (String kv : raw.split("&")) {
                    String[] a = kv.split("=", 2);
                    if (a.length == 2) {
                        q.put(java.net.URLDecoder.decode(a[0], StandardCharsets.UTF_8), java.net.URLDecoder.decode(a[1], StandardCharsets.UTF_8));
                    }
                }
            }
        }
        int id = intv(q.get("request_id"));
        List<Map<String, Object>> r = PanelApi.rows("select code, serial from web_topup where id = ? and method = 'card'", id);
        String key = conf("web.card.partner_key");
        if (r.isEmpty() || key.isEmpty() || !MessageDigest.isEqual(md5(key + r.get(0).get("code") + r.get(0).get("serial")).getBytes(StandardCharsets.UTF_8),
                str(q.get("callback_sign")).toLowerCase().getBytes(StandardCharsets.UTF_8))) {
            send(ex, 403, Map.of("error", "sai chữ ký"));
            return;
        }
        int st = intv(q.get("status"));
        if (st == 1 || st == 2) {
            finishCard(id, st, intv(q.get("value")), str(q.get("message")));
        } else if (st != 99) {
            PanelApi.update("update web_topup set status = 2, note = ?, updated_at = now() where id = ? and status = 0", "Thẻ lỗi: " + str(q.get("message")), id);
        }
        send(ex, 200, Map.of("ok", true));
    }

    private static final java.util.regex.Pattern NAP_CODE = java.util.regex.Pattern.compile("NAP\\s*(\\d{1,9})", java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * Webhook SePay (web.sepay.apikey; ở SePay chọn chứng thực "API Key", gửi header "Authorization: Apikey <key>"): mỗi giao dịch
     * tiền vào có nội dung "NAP<mã>" thì tự duyệt yêu cầu nạp chuyển khoản đó theo số tiền thật nhận được. Mọi giao dịch được lưu ở
     * web_bank_txn theo mã giao dịch SePay để gửi lại nhiều lần cũng chỉ cộng 1 lần; giao dịch không khớp để admin xem trên panel.
     */
    private void sepayCallback(HttpExchange ex) throws Exception {
        String key = conf("web.sepay.apikey"), auth = Objects.toString(ex.getRequestHeaders().getFirst("Authorization"), "").trim();
        String given = auth.toLowerCase().startsWith("apikey ") ? auth.substring(7).trim() : auth;
        if (key.isEmpty() || !"POST".equals(ex.getRequestMethod())
                || !MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8))) {
            send(ex, 401, Map.of("success", false, "message", "sai API key"));
            return;
        }
        Map<String, Object> t = GSON.fromJson(new String(ex.getRequestBody().readNBytes(32_000), StandardCharsets.UTF_8), new TypeToken<Map<String, Object>>() {
        }.getType());
        if (t == null || t.get("id") == null) {
            send(ex, 400, Map.of("success", false, "message", "thiếu dữ liệu"));
            return;
        }
        long txId = (long) Double.parseDouble(str(t.get("id")));
        String content = (str(t.get("content")) + " " + str(t.get("code")) + " " + str(t.get("description"))).trim();
        long amount = (long) Double.parseDouble(str(t.get("transferAmount")).isEmpty() ? "0" : str(t.get("transferAmount")));
        boolean in = !"out".equalsIgnoreCase(str(t.get("transferType")));
        try {
            PanelApi.update("insert into web_bank_txn (sepay_id, gateway, account_number, amount, content, transfer_type, reference_code, txn_time) values (?, ?, ?, ?, ?, ?, ?, ?)",
                    txId, str(t.get("gateway")), str(t.get("accountNumber")), amount, content.length() > 500 ? content.substring(0, 500) : content,
                    str(t.get("transferType")), str(t.get("referenceCode")), str(t.get("transactionDate")));
        } catch (java.sql.SQLException dup) {
            if (dup.getErrorCode() != 1062 && !(dup instanceof java.sql.SQLIntegrityConstraintViolationException)) {
                throw dup;
            }
            send(ex, 200, Map.of("success", true, "message", "đã xử lý trước đó")); // SePay gửi lại giao dịch đã nhận
            return;
        }
        String result = "Không có mã NAP trong nội dung";
        java.util.regex.Matcher m = NAP_CODE.matcher(content);
        if (in && amount > 0 && m.find()) {
            int id = Integer.parseInt(m.group(1));
            List<Map<String, Object>> r = PanelApi.rows("select amount, status, method from web_topup where id = ?", id);
            if (r.isEmpty() || !"bank".equals(r.get(0).get("method"))) {
                result = "Không có yêu cầu nạp NAP" + id;
            } else if (intv(r.get(0).get("status")) != 0) {
                result = "Yêu cầu NAP" + id + " đã được xử lý trước đó";
            } else if (approve(id, (int) Math.min(amount, Integer.MAX_VALUE), "SePay tự duyệt (giao dịch " + txId + ")"
                    + (amount != intv(r.get(0).get("amount")) ? ", chuyển " + amount + "đ khác số tiền yêu cầu" : ""))) {
                result = "OK";
                PanelApi.update("update web_bank_txn set topup_id = ? where sepay_id = ?", id, txId);
            }
        } else if (!in) {
            result = "Giao dịch tiền ra";
        }
        PanelApi.update("update web_bank_txn set result = ? where sepay_id = ?", result, txId);
        send(ex, 200, Map.of("success", true, "message", result));
    }

    /** Thẻ đúng (1) hoặc sai mệnh giá (2): cộng theo mệnh giá thật nhân tỉ lệ web.card.rate %. */
    private void finishCard(int id, int st, int value, String msg) throws Exception {
        int credit = (int) ((long) Math.max(0, value) * cardRate() / 100);
        if (credit <= 0) {
            PanelApi.update("update web_topup set status = 2, note = ?, updated_at = now() where id = ? and status = 0", "Thẻ lỗi: " + msg, id);
            return;
        }
        approve(id, credit, st == 2 ? "Sai mệnh giá, thẻ thật " + value + "đ" : "Thẻ đúng");
    }

    /**
     * Duyệt 1 yêu cầu nạp (dùng cho cả panel): đánh dấu thành công đúng 1 lần rồi cộng tiền vào account.vnd + tongnap, cập nhật luôn
     * người đang online. Trả về false nếu yêu cầu đã được xử lý trước đó.
     */
    public static boolean approve(int id, int credit, String note) throws Exception {
        if (credit <= 0) {
            return false;
        }
        int gem = nro.models.services_func.Input.napGem(credit);
        int rest = credit - nro.models.services_func.Input.napGemUsed(credit); // phần lẻ dưới 10.000đ để lại số dư
        List<Map<String, Object>> r = PanelApi.rows("select account_id from web_topup where id = ?", id);
        if (r.isEmpty()) {
            return false;
        }
        int acc = intv(r.get(0).get("account_id"));
        Player on = Client.gI().getPlayerByUser(acc);
        // offline: ngọc chờ ở gem_pending, cộng + báo khi đăng nhập (claimPendingGem)
        if (PanelApi.update("update web_topup set status = 1, credited = ?, gem_pending = ?, note = ?, updated_at = now() where id = ? and status = 0",
                credit, on == null ? gem : 0, note + (gem > 0 ? ", " + gem + " ngọc" : ""), id) <= 0) {
            return false;
        }
        PanelApi.update("update account set vnd = vnd + ?, tongnap = tongnap + ? where id = ?", rest, credit, acc);
        if (on != null) {
            if (on.getSession() != null) {
                on.getSession().vnd += rest;
                on.getSession().tongnap += credit;
            }
            giveGem(on, gem);
        }
        return true;
    }

    /** Gọi khi nhân vật vào game: cộng ngọc của các lần nạp lúc offline rồi báo. */
    public static void claimPendingGem(Player pl) {
        if (pl == null || pl.getSession() == null) {
            return;
        }
        try {
            int total = 0;
            for (Map<String, Object> r : PanelApi.rows("select id, gem_pending from web_topup where account_id = ? and gem_pending > 0", pl.getSession().userId)) {
                if (PanelApi.update("update web_topup set gem_pending = 0 where id = ? and gem_pending > 0", r.get("id")) > 0) {
                    total += intv(r.get("gem_pending"));
                }
            }
            giveGem(pl, total);
        } catch (Exception e) {
            nro.models.utils.Logger.error("Cộng ngọc nạp chờ: " + e.getMessage() + "\n");
        }
    }

    private static void giveGem(Player pl, int gem) {
        if (gem <= 0) {
            return;
        }
        pl.inventory.gem += gem;
        nro.models.services.Service.gI().sendMoney(pl);
        nro.models.services.Service.gI().sendThongBao(pl, "Bạn đã nạp " + nro.models.utils.Util.numberToMoney(gem) + " ngọc vào tài khoản");
    }

    // ------------------------------------------------------------------ tiện ích

    private static String md5(String s) {
        try {
            StringBuilder sb = new StringBuilder();
            for (byte x : MessageDigest.getInstance("MD5").digest(s.getBytes(StandardCharsets.UTF_8))) {
                sb.append(String.format("%02x", x));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    static int intv(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return (int) Double.parseDouble(str(o).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
