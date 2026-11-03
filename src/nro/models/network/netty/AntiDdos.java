package nro.models.network.netty;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import nro.models.utils.Logger;

/**
 * Chống DDoS ở tầng ứng dụng (cổng game): giới hạn kết nối / IP, tốc độ mở kết nối, tốc độ gói tin, kích thước gói,
 * bắt tay trong 10 giây; IP vi phạm bị chặn tạm thời (chặn lâu dần nếu tái phạm).
 * Lưu ý: tấn công băng thông lớn (UDP flood, SYN flood vài Gbps) phải chặn ở nhà mạng / VPS (Cloudflare Spectrum,
 * tường lửa nhà cung cấp) - phần mềm trên máy chủ không chặn được lưu lượng chưa tới được máy.
 *
 * @author Việt
 */
public final class AntiDdos {

    // ---- giới hạn (đọc thêm từ Config.properties: server.ddos.*)
    public static int MAX_CONN_TOTAL = 3000;          // tổng kết nối cùng lúc
    public static int MAX_CONN_PER_IP = 20;            // kết nối cùng lúc mỗi IP
    public static int MAX_NEW_CONN_10S = 60;          // kết nối mới mỗi IP trong 10 giây
    public static int MAX_MSG_PER_SEC = 300;          // gói tin / giây mỗi kết nối (client thật < 40, mạng lag dồn gói có thể vọt cao)
    public static int MAX_FRAME = 64 * 1024;          // gói client gửi lên lớn nhất (client thật < 2 KB)
    public static int HANDSHAKE_SECONDS = 10;         // phải gửi gói bắt tay (-27) trong thời gian này
    public static int LOGIN_IDLE_SECONDS = 120;       // chưa vào nhân vật mà im lặng quá lâu thì ngắt
    public static long MAX_PENDING_WRITE = 32L * 1024 * 1024; // byte chờ gửi tối đa / kết nối

    private static final AtomicInteger total = new AtomicInteger();
    private static final Map<String, AtomicInteger> perIp = new ConcurrentHashMap<>();
    private static final Map<String, long[]> rate = new ConcurrentHashMap<>();   // {bắt đầu cửa sổ, số kết nối}
    private static final Map<String, long[]> banned = new ConcurrentHashMap<>(); // {hết hạn, số lần bị chặn}
    private static final Map<String, Long> trusted = new ConcurrentHashMap<>();  // IP đã đăng nhập thành công -> hết hạn tin cậy
    private static final Map<String, long[]> strikes = new ConcurrentHashMap<>(); // {bắt đầu cửa sổ, số lỗi nhẹ}
    private static final java.util.Set<String> whitelist = ConcurrentHashMap.newKeySet();
    public static int STRIKE_LIMIT = 30;
    public static boolean BAN_ENABLED = true;          // server.ddos.ban=false: không bao giờ chặn cả IP, chỉ ngắt kết nối vi phạm              // số kết nối không bắt tay / IP / phút mới bị chặn
    private static final long TRUST_MS = 24 * 3600_000L;
    private static final AtomicInteger dropped = new AtomicInteger();
    private static volatile long lastLog;

    private AntiDdos() {
    }

    public static void loadConfig(java.util.Properties p) {
        MAX_CONN_TOTAL = intOf(p, "server.ddos.maxconn", MAX_CONN_TOTAL);
        MAX_CONN_PER_IP = intOf(p, "server.ddos.maxperip", Math.max(MAX_CONN_PER_IP, nro.models.server.Manager.MAX_PER_IP));
        MAX_NEW_CONN_10S = intOf(p, "server.ddos.newconn10s", MAX_NEW_CONN_10S);
        MAX_MSG_PER_SEC = intOf(p, "server.ddos.msgpersec", MAX_MSG_PER_SEC);
        STRIKE_LIMIT = intOf(p, "server.ddos.strikes", STRIKE_LIMIT);
        BAN_ENABLED = !"false".equalsIgnoreCase(p.getProperty("server.ddos.ban", "true").trim());
        whitelist.clear();
        String w = p.getProperty("server.ddos.whitelist", "");
        for (String ip : w.split("[,; ]+")) {
            if (!ip.isBlank()) {
                whitelist.add(ip.trim());
            }
        }
    }

    /** Đăng nhập thành công: IP được tin cậy 24 giờ (không bị chặn cả IP, giới hạn kết nối nới gấp 4). */
    public static void markTrusted(String ip) {
        if (ip != null) {
            trusted.put(ip, System.currentTimeMillis() + TRUST_MS);
            banned.remove(ip);
        }
    }

    public static boolean isTrusted(String ip) {
        if (ip == null) {
            return false;
        }
        if (isLocal(ip) || whitelist.contains(ip)) {
            return true;
        }
        Long t = trusted.get(ip);
        return t != null && t > System.currentTimeMillis();
    }

    /** Lỗi nhẹ (kết nối không bắt tay kịp - mạng chậm cũng gặp): chỉ chặn IP khi lặp lại nhiều trong 1 phút. */
    public static void strike(String ip, String why) {
        if (isTrusted(ip)) {
            return;
        }
        long now = System.currentTimeMillis();
        long[] r = strikes.compute(ip, (k, v) -> v == null || now - v[0] > 60_000 ? new long[]{now, 1} : new long[]{v[0], v[1] + 1});
        if (r[1] >= STRIKE_LIMIT) {
            strikes.remove(ip);
            ban(ip, r[1] + " lần " + why + " trong 1 phút");
        }
    }

    private static int intOf(java.util.Properties p, String k, int def) {
        try {
            return p.getProperty(k) == null ? def : Integer.parseInt(p.getProperty(k).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Kết nối mới: true = cho vào. */
    public static boolean accept(String ip) {
        long now = System.currentTimeMillis();
        long[] b = banned.get(ip);
        if (b != null) {
            if (b[0] > now) {
                dropped.incrementAndGet();
                return false;
            }
        }
        if (isLocal(ip) || whitelist.contains(ip)) {
            total.incrementAndGet();
            perIp.computeIfAbsent(ip, k -> new AtomicInteger()).incrementAndGet();
            return true;
        }
        long[] r = rate.compute(ip, (k, v) -> v == null || now - v[0] > 10_000 ? new long[]{now, 1} : new long[]{v[0], v[1] + 1});
        boolean trust = isTrusted(ip);
        if (r[1] > MAX_NEW_CONN_10S * (trust ? 4 : 1)) {
            if (trust) {
                dropped.incrementAndGet();
                return false;
            }
            ban(ip, "mở " + r[1] + " kết nối trong 10 giây");
            return false;
        }
        if (total.get() >= MAX_CONN_TOTAL) {
            dropped.incrementAndGet();
            return false;
        }
        AtomicInteger c = perIp.computeIfAbsent(ip, k -> new AtomicInteger());
        if (c.get() >= MAX_CONN_PER_IP * (trust ? 4 : 1)) {
            dropped.incrementAndGet();
            return false;
        }
        c.incrementAndGet();
        total.incrementAndGet();
        return true;
    }

    public static void release(String ip) {
        total.decrementAndGet();
        AtomicInteger c = perIp.get(ip);
        if (c != null && c.decrementAndGet() <= 0) {
            perIp.remove(ip);
        }
    }

    /** Chặn IP: 2 phút, tái phạm gấp đôi, tối đa 1 ngày. */
    public static void ban(String ip, String why) {
        if (!BAN_ENABLED || isTrusted(ip)) { // IP người chơi thật: chỉ ngắt kết nối vi phạm (nơi gọi tự đóng), không chặn cả IP
            return;
        }
        long now = System.currentTimeMillis();
        long[] b = banned.compute(ip, (k, v) -> {
            int n = v == null ? 1 : (int) v[1] + 1;
            long dur = Math.min(24L * 3600_000, 120_000L << Math.min(10, n - 1));
            return new long[]{now + dur, n};
        });
        dropped.incrementAndGet();
        if (now - lastLog > 2000) {
            lastLog = now;
            Logger.warning("[CHỐNG DDOS] chặn " + ip + " " + ((b[0] - now) / 60_000) + " phút: " + why + "\n");
        }
        try (java.io.FileWriter fw = new java.io.FileWriter("logs/ddos.log", true)) { // lịch sử chặn để kiểm tra chặn nhầm
            fw.write(java.time.LocalDateTime.now().withNano(0) + " chặn " + ip + " " + ((b[0] - now) / 60_000) + " phút: " + why + "\n");
        } catch (Exception ignored) {
        }
    }

    public static boolean isLocal(String ip) {
        if (ip == null) {
            return false;
        }
        if (ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1") || ip.startsWith("10.") || ip.startsWith("192.168.")) {
            return true; // máy chủ / mạng LAN
        }
        if (ip.startsWith("172.")) {
            String[] p = ip.split("\\.");
            try {
                int b = Integer.parseInt(p[1]);
                return b >= 16 && b <= 31;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    /** Admin: gỡ chặn mọi IP, trả về số IP được gỡ. */
    public static int unbanAll() {
        int n = banned.size();
        banned.clear();
        strikes.clear();
        rate.clear();
        return n;
    }

    /** Dọn bảng định kỳ (gọi mỗi phút). */
    public static void cleanup() {
        long now = System.currentTimeMillis();
        banned.values().removeIf(v -> v[0] < now - 6 * 3600_000L); // nhớ số lần tái phạm thêm 6 giờ
        rate.values().removeIf(v -> now - v[0] > 60_000);
        strikes.values().removeIf(v -> now - v[0] > 60_000);
        trusted.values().removeIf(t -> t < now);
    }

    public static String stats() {
        long now = System.currentTimeMillis();
        long ban = banned.values().stream().filter(v -> v[0] > now).count();
        return "Kết nối: " + total.get() + ", IP đang bị chặn: " + ban + ", IP tin cậy: " + trusted.size() + ", đã chặn: " + dropped.get();
    }
}
