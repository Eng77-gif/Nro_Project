package nro.models.data;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import nro.models.network.MySession;
import nro.models.utils.Logger;

/**
 * Chống kéo res: phiên bị nghi là tool kéo tài nguyên vẫn nhận đủ file như bình thường (tool tưởng tải thành công) nhưng
 * mọi ảnh là ảnh cùng kích thước ghi "Good, but not today.".
 * Bị coi là tool khi:
 * - xin ảnh (icon, ảnh theo tên, nền vật phẩm, quái) khi chưa đăng nhập tài khoản;
 * - xin quá nhiều ảnh trong thời gian ngắn (client thật chỉ xin ảnh đang thấy);
 * - xin icon theo dãy số liên tiếp tăng dần (quét toàn bộ mã);
 * - một IP tải gói res của từ 2 cỡ màn hình (x1 - x4) trở lên trong 6 giờ (client thật chỉ dùng 1 cỡ), hoặc quá 8 lần / ngày;
 * - xin quá 200 ảnh khi chưa vào nhân vật, xin quá 60 mã ảnh không tồn tại (dò mã);
 * - tài khoản xin quá 6.000 ảnh / ngày, IP quá 12.000 ảnh / ngày.
 * Tài khoản bị đánh dấu được lưu vào bảng res_guard_flag và tiếp tục nhận ảnh giả 7 ngày (kể cả đăng nhập lại).
 * Admin không bị giới hạn.
 *
 * @author Việt
 */
public final class ResGuard {

    public static final String TEXT = "Good, but not today.";
    private static final int BURST_LIMIT = 900;          // số lần xin ảnh trong BURST_WINDOW
    private static final long BURST_WINDOW = 120_000;
    private static final int SEQ_LIMIT = 150;            // số icon liên tiếp tăng dần (bộ đồ có thể ~30 icon liền nhau)
    private static final long RES_ZOOM_WINDOW = 6 * 3600_000L;
    private static final int PRE_CHAR_LIMIT = 200;        // ảnh xin được trước khi vào nhân vật (màn tạo nhân vật)
    private static final int MISS_LIMIT = 60;             // số lần xin mã ảnh không tồn tại
    private static final int DAILY_ACCOUNT_LIMIT = 6000;  // ảnh / tài khoản / ngày (client lưu cache nên chỉ xin 1 lần)
    private static final int DAILY_IP_LIMIT = 12000;      // ảnh / IP / ngày
    private static final int RES_PACK_DAILY = 8;          // số lần tải gói res / IP / ngày
    private static final int FLAG_DAYS = 7;               // tài khoản bị đánh dấu giữ 7 ngày

    private static final Map<String, int[]> dailyByAccount = new ConcurrentHashMap<>();
    private static final Map<String, int[]> dailyByIp = new ConcurrentHashMap<>();
    private static final Map<String, int[]> resPackByIp = new ConcurrentHashMap<>();

    private static final Map<String, Map<Integer, Long>> resZoomByIp = new ConcurrentHashMap<>();
    private static final Map<Long, BufferedImageHolder> cache = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, BufferedImageHolder> e) {
            return size() > 256;
        }
    };

    private record BufferedImageHolder(byte[] png) {
    }

    private ResGuard() {
    }

    /** Ghi nhận 1 lần xin ảnh, trả về true nếu phải gửi ảnh giả. */
    public static boolean check(MySession s, String kind, int id) {
        if (s == null) {
            return false;
        }
        if (s.scraper) {
            return true;
        }
        if (s.userId <= 0) {
            return flag(s, "xin " + kind + " khi chưa đăng nhập");
        }
        if (s.isAdmin) {
            return false;
        }
        if (s.player == null && ++s.resBeforeChar > PRE_CHAR_LIMIT) {
            return flag(s, "xin " + s.resBeforeChar + " ảnh khi chưa vào nhân vật");
        }
        long now = System.currentTimeMillis();
        if (now - s.resWindowStart > BURST_WINDOW) {
            s.resWindowStart = now;
            s.resCount = 0;
        }
        if (++s.resCount > BURST_LIMIT) {
            return flag(s, "xin " + s.resCount + " ảnh trong 2 phút");
        }
        if ("icon".equals(kind)) {
            s.resSeq = id == s.resLastId + 1 ? s.resSeq + 1 : 0;
            s.resLastId = id;
            if (s.resSeq >= SEQ_LIMIT) {
                return flag(s, "quét icon liên tiếp tới mã " + id);
            }
        }
        int day = java.time.LocalDate.now().getDayOfYear();
        int acc = daily(dailyByAccount, String.valueOf(s.userId), day);
        if (acc > DAILY_ACCOUNT_LIMIT) {
            return flag(s, "tài khoản xin " + acc + " ảnh trong ngày");
        }
        if (!nro.models.network.netty.AntiDdos.isLocal(s.getIP())) {
            int ip = daily(dailyByIp, s.getIP(), day);
            if (ip > DAILY_IP_LIMIT) {
                return flag(s, "IP xin " + ip + " ảnh trong ngày");
            }
        }
        return false;
    }

    private static int daily(Map<String, int[]> m, String key, int day) {
        if (m.size() > 200_000) {
            m.clear();
        }
        int[] d = m.compute(key, (k, v) -> v == null || v[0] != day ? new int[]{day, 0} : v);
        synchronized (d) {
            return ++d[1];
        }
    }

    /** Xin mã ảnh không tồn tại: tool dò mã sẽ xin rất nhiều mã trống, client thật gần như không. */
    public static void miss(MySession s, String kind, int id) {
        if (s == null || s.scraper || s.isAdmin) {
            return;
        }
        if (s.userId <= 0) {
            flag(s, "dò " + kind + " " + id + " khi chưa đăng nhập");
        } else if (++s.resMiss > MISS_LIMIT) {
            flag(s, "xin " + s.resMiss + " " + kind + " không tồn tại");
        }
    }

    /** Gói res: true nếu IP này đã tải gói res của cỡ màn hình khác gần đây (tool kéo đủ x1 - x4) hoặc tải quá nhiều lần. */
    public static boolean checkRes(MySession s) {
        if (s == null) {
            return false;
        }
        if (s.scraper) {
            return true;
        }
        if (nro.models.network.netty.AntiDdos.isLocal(s.getIP())) {
            return false;
        }
        long now = System.currentTimeMillis();
        Map<Integer, Long> m = resZoomByIp.computeIfAbsent(s.getIP(), k -> new ConcurrentHashMap<>());
        m.values().removeIf(t -> now - t > RES_ZOOM_WINDOW);
        m.put((int) s.zoomLevel, now);
        if (m.size() >= 2) {
            return flag(s, "tải gói res nhiều cỡ màn hình " + m.keySet());
        }
        int n = daily(resPackByIp, s.getIP(), java.time.LocalDate.now().getDayOfYear());
        if (n > RES_PACK_DAILY) {
            return flag(s, "tải gói res " + n + " lần trong ngày");
        }
        return false;
    }

    /** Khi đăng nhập: tài khoản đã bị đánh dấu trong FLAG_DAYS ngày gần đây thì tiếp tục chỉ nhận ảnh giả. */
    public static void onLogin(MySession s) {
        if (s == null || s.userId <= 0 || s.isAdmin) {
            return;
        }
        try (java.sql.Connection con = LocalManager.getConnection();
                java.sql.PreparedStatement ps = con.prepareStatement("select reason from res_guard_flag where account_id = ? and created_at > now() - interval " + FLAG_DAYS + " day")) {
            ps.setInt(1, s.userId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    s.scraper = true;
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** Bỏ đánh dấu (admin): trả về true nếu có dòng bị xoá. */
    public static boolean unflag(int accountId) {
        try (java.sql.Connection con = LocalManager.getConnection();
                java.sql.PreparedStatement ps = con.prepareStatement("delete from res_guard_flag where account_id = ?")) {
            ps.setInt(1, accountId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean flag(MySession s, String why) {
        if (!s.scraper) {
            s.scraper = true;
            Logger.warning("[CHỐNG KÉO RES] " + s.getIP() + " (tài khoản " + s.userId + ") bị coi là tool kéo res (" + why + "), từ giờ chỉ nhận ảnh giả\n");
            if (s.userId > 0) {
                final int acc = s.userId;
                final String ip = s.getIP();
                nro.models.utils.Util.POOL.submit(() -> {
                    try (java.sql.Connection con = LocalManager.getConnection();
                            java.sql.PreparedStatement ps = con.prepareStatement("replace into res_guard_flag (account_id, reason, ip) values (?, ?, ?)")) {
                        ps.setInt(1, acc);
                        ps.setString(2, why.length() > 250 ? why.substring(0, 250) : why);
                        ps.setString(3, ip);
                        ps.executeUpdate();
                    } catch (Exception ignored) {
                    }
                });
            }
        }
        return true;
    }

    /** Ảnh giả cùng kích thước với ảnh gốc (đọc từ phần đầu file PNG), ghi "Good, but not today.". */
    public static byte[] fake(byte[] original) {
        int w = 64, h = 64;
        if (original != null && original.length > 24 && (original[0] & 0xFF) == 0x89 && original[1] == 'P' && original[2] == 'N' && original[3] == 'G') {
            w = (original[16] & 0xFF) << 24 | (original[17] & 0xFF) << 16 | (original[18] & 0xFF) << 8 | (original[19] & 0xFF);
            h = (original[20] & 0xFF) << 24 | (original[21] & 0xFF) << 16 | (original[22] & 0xFF) << 8 | (original[23] & 0xFF);
        }
        w = Math.max(1, Math.min(4096, w));
        h = Math.max(1, Math.min(8192, h));
        long key = (long) w << 32 | h;
        synchronized (cache) {
            BufferedImageHolder c = cache.get(key);
            if (c != null) {
                return c.png();
            }
        }
        byte[] png = draw(w, h);
        synchronized (cache) {
            cache.put(key, new BufferedImageHolder(png));
        }
        return png;
    }

    private static byte[] draw(int w, int h) {
        try {
            if (w < 64 || h < 32) { // ảnh nhỏ: vẽ to gấp 4 rồi thu nhỏ cho chữ vẫn đủ
                BufferedImage big = render(w * 4, h * 4);
                BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = img.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.drawImage(big, 0, 0, w, h, null);
                g.dispose();
                return png(img);
            }
            return png(render(w, h));
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private static byte[] png(BufferedImage img) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static BufferedImage render(int w, int h) {
        {
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new Color(255, 255, 255, 235));
            g.fillRect(0, 0, w, h);
            g.setColor(new Color(200, 30, 30));
            // thử 1 dòng, 2 dòng, 4 dòng: lấy cách xếp cho chữ to nhất mà vẫn vừa ảnh
            String[][] layouts = {{TEXT}, {"Good, but", "not today."}, {"Good,", "but", "not", "today."}};
            String[] lines = layouts[0];
            Font f = new Font(Font.SANS_SERIF, Font.BOLD, 6);
            FontMetrics fm = g.getFontMetrics(f);
            int best = 0;
            for (String[] lay : layouts) {
                for (int size = Math.max(6, h / lay.length); size >= 5; size--) {
                    Font tf = new Font(Font.SANS_SERIF, Font.BOLD, size);
                    FontMetrics tm = g.getFontMetrics(tf);
                    int widest = 0;
                    for (String l : lay) {
                        widest = Math.max(widest, tm.stringWidth(l));
                    }
                    if (widest <= w - 2 && tm.getHeight() * lay.length <= h) {
                        if (size > best) {
                            best = size;
                            f = tf;
                            fm = tm;
                            lines = lay;
                        }
                        break;
                    }
                }
            }
            g.setFont(f);
            int y = (h - fm.getHeight() * lines.length) / 2 + fm.getAscent();
            for (String l : lines) {
                g.drawString(l, (w - fm.stringWidth(l)) / 2, y);
                y += fm.getHeight();
            }
            g.dispose();
            return img;
        }
    }
}
