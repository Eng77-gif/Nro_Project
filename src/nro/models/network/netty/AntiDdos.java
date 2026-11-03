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
    public static int MAX_CONN_PER_IP = 8;            // kết nối cùng lúc mỗi IP
    public static int MAX_NEW_CONN_10S = 20;          // kết nối mới mỗi IP trong 10 giây
    public static int MAX_MSG_PER_SEC = 120;          // gói tin / giây mỗi kết nối (client thật < 40)
    public static int MAX_FRAME = 16 * 1024;          // gói client gửi lên lớn nhất (client thật < 2 KB)
    public static int HANDSHAKE_SECONDS = 10;         // phải gửi gói bắt tay (-27) trong thời gian này
    public static int LOGIN_IDLE_SECONDS = 120;       // chưa vào nhân vật mà im lặng quá lâu thì ngắt
    public static long MAX_PENDING_WRITE = 32L * 1024 * 1024; // byte chờ gửi tối đa / kết nối

    private static final AtomicInteger total = new AtomicInteger();
    private static final Map<String, AtomicInteger> perIp = new ConcurrentHashMap<>();
    private static final Map<String, long[]> rate = new ConcurrentHashMap<>();   // {bắt đầu cửa sổ, số kết nối}
    private static final Map<String, long[]> banned = new ConcurrentHashMap<>(); // {hết hạn, số lần bị chặn}
    private static final AtomicInteger dropped = new AtomicInteger();
    private static volatile long lastLog;

    private AntiDdos() {
    }

    public static void loadConfig(java.util.Properties p) {
        MAX_CONN_TOTAL = intOf(p, "server.ddos.maxconn", MAX_CONN_TOTAL);
        MAX_CONN_PER_IP = intOf(p, "server.ddos.maxperip", Math.max(MAX_CONN_PER_IP, nro.models.server.Manager.MAX_PER_IP));
        MAX_NEW_CONN_10S = intOf(p, "server.ddos.newconn10s", MAX_NEW_CONN_10S);
        MAX_MSG_PER_SEC = intOf(p, "server.ddos.msgpersec", MAX_MSG_PER_SEC);
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
        if (isLocal(ip)) {
            total.incrementAndGet();
            perIp.computeIfAbsent(ip, k -> new AtomicInteger()).incrementAndGet();
            return true;
        }
        long[] r = rate.compute(ip, (k, v) -> v == null || now - v[0] > 10_000 ? new long[]{now, 1} : new long[]{v[0], v[1] + 1});
        if (r[1] > MAX_NEW_CONN_10S) {
            ban(ip, "mở " + r[1] + " kết nối trong 10 giây");
            return false;
        }
        if (total.get() >= MAX_CONN_TOTAL) {
            dropped.incrementAndGet();
            return false;
        }
        AtomicInteger c = perIp.computeIfAbsent(ip, k -> new AtomicInteger());
        if (c.get() >= MAX_CONN_PER_IP) {
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
        if (isLocal(ip)) {
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
    }

    public static boolean isLocal(String ip) {
        return ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1");
    }

    /** Dọn bảng định kỳ (gọi mỗi phút). */
    public static void cleanup() {
        long now = System.currentTimeMillis();
        banned.values().removeIf(v -> v[0] < now - 6 * 3600_000L); // nhớ số lần tái phạm thêm 6 giờ
        rate.values().removeIf(v -> now - v[0] > 60_000);
    }

    public static String stats() {
        long now = System.currentTimeMillis();
        long ban = banned.values().stream().filter(v -> v[0] > now).count();
        return "Kết nối: " + total.get() + ", IP đang bị chặn: " + ban + ", đã chặn: " + dropped.get();
    }
}
