package nro.models.services;

import com.google.gson.Gson;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nro.models.player.Player;

/**
 * Mùa VIP Tori Bot (tích nạp): thời gian, giá 4 gói, số lần mua tối đa. Chỉnh ở panel > Mùa VIP Tori Bot (bảng vip_season).
 * Mỗi mùa có mã = ngày bắt đầu; người chơi sang mùa mới thì đếm lại số lần mua.
 */
public final class VipSeason {

    private static final Gson GSON = new Gson();
    private static volatile LocalDateTime start = LocalDateTime.of(LocalDate.now().getYear(), Month.AUGUST, 16, 0, 0, 0);
    private static volatile LocalDateTime end = LocalDateTime.of(LocalDate.now().getYear(), Month.SEPTEMBER, 16, 23, 59, 59);
    private static volatile int[] prices = {50000, 100000, 150000, 200000};
    private static volatile int maxBuy = 4;
    private static volatile boolean loaded;

    private VipSeason() {
    }

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try (Connection con = nro.models.data.LocalManager.getConnection(); Statement st = con.createStatement()) {
            st.executeUpdate("create table if not exists vip_season (id int primary key, data text not null)");
            try (ResultSet rs = st.executeQuery("select data from vip_season where id=1")) {
                if (rs.next()) {
                    apply(GSON.fromJson(rs.getString(1), Map.class), false);
                }
            }
        } catch (Exception e) {
            nro.models.utils.Logger.error("Mùa VIP: không đọc được vip_season, dùng mặc định 16/08 - 16/09: " + e.getMessage() + "\n");
        }
    }

    public static LocalDateTime start() {
        load();
        return start;
    }

    public static LocalDateTime end() {
        load();
        return end;
    }

    public static int price(int level) {
        load();
        return prices[level - 1];
    }

    public static int maxBuy() {
        load();
        return maxBuy;
    }

    public static boolean isOpen() {
        LocalDateTime now = LocalDateTime.now();
        return !now.isBefore(start()) && !now.isAfter(end());
    }

    /** Mã mùa = ngày giờ bắt đầu. */
    public static String id() {
        return start().toString();
    }

    /** Sang mùa mới: đếm lại số lần mua (VIP đang có vẫn dùng tới hết hạn timevip). */
    public static void check(Player pl) {
        String id = id();
        if (!id.equals(pl.vipSeason)) {
            pl.vipSeason = id;
            pl.vipPurchaseCount = 0;
        }
    }

    private static int num(Object o) {
        return (int) Math.round(Double.parseDouble(String.valueOf(o)));
    }

    public static synchronized void apply(Map<?, ?> b, boolean save) throws Exception {
        LocalDateTime s = LocalDateTime.parse(String.valueOf(b.get("start")));
        LocalDateTime e = LocalDateTime.parse(String.valueOf(b.get("end")));
        if (!e.isAfter(s)) {
            throw new IllegalArgumentException("Ngày kết thúc phải sau ngày bắt đầu");
        }
        List<?> p = (List<?>) b.get("prices");
        if (p == null || p.size() != 4) {
            throw new IllegalArgumentException("Cần đủ giá 4 gói VIP");
        }
        int[] np = new int[4];
        for (int i = 0; i < 4; i++) {
            np[i] = num(p.get(i));
            if (np[i] < 0) {
                throw new IllegalArgumentException("Giá VIP " + (i + 1) + " không hợp lệ");
            }
        }
        int mx = num(b.get("max"));
        if (mx < 1 || mx > 100) {
            throw new IllegalArgumentException("Số lần mua tối đa 1 - 100");
        }
        if (save) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("start", s.toString());
            m.put("end", e.toString());
            m.put("prices", np);
            m.put("max", mx);
            try (Connection con = nro.models.data.LocalManager.getConnection(); PreparedStatement ps = con.prepareStatement("replace into vip_season (id, data) values (1, ?)")) {
                ps.setString(1, GSON.toJson(m));
                ps.executeUpdate();
            }
        }
        start = s;
        end = e;
        prices = np;
        maxBuy = mx;
    }

    public static Map<String, Object> get() {
        load();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("start", start.toString());
        m.put("end", end.toString());
        m.put("prices", prices);
        m.put("max", maxBuy);
        m.put("open", isOpen());
        m.put("now", LocalDateTime.now().withNano(0).toString());
        return m;
    }

    /** Người đã mua VIP: online lấy trong bộ nhớ, offline đọc data_vip. */
    public static List<Map<String, Object>> buyers() throws Exception {
        Map<Long, Map<String, Object>> by = new LinkedHashMap<>();
        String id = id();
        try (Connection con = nro.models.data.LocalManager.getConnection(); PreparedStatement ps = con.prepareStatement("select id, name, data_vip from player where data_vip is not null and data_vip <> ''");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    org.json.simple.JSONArray a = (org.json.simple.JSONArray) org.json.simple.JSONValue.parse(rs.getString(3));
                    if (a == null || a.size() <= 7) {
                        continue;
                    }
                    int vip = Integer.parseInt(String.valueOf(a.get(5)));
                    long tv = Long.parseLong(String.valueOf(a.get(6)));
                    int cnt = Integer.parseInt(String.valueOf(a.get(7)));
                    String season = a.size() > 8 ? String.valueOf(a.get(8)) : "";
                    if (!id.equals(season)) {
                        cnt = 0;
                    }
                    if (vip > 0 || cnt > 0 || tv > System.currentTimeMillis()) {
                        by.put(rs.getLong(1), row(rs.getLong(1), rs.getString(2), false, vip, tv, cnt));
                    }
                } catch (Exception ignored) {
                }
            }
        }
        for (Player p : new ArrayList<>(nro.models.server.Client.gI().getPlayers())) {
            if (p != null && p.isPl()) {
                check(p);
                if (p.vip > 0 || p.vipPurchaseCount > 0 || p.timevip > System.currentTimeMillis()) {
                    by.put(p.id, row(p.id, p.name, true, p.vip, p.timevip, p.vipPurchaseCount));
                } else {
                    by.remove(p.id);
                }
            }
        }
        List<Map<String, Object>> l = new ArrayList<>(by.values());
        l.sort((x, y) -> Long.compare((long) y.get("timevip"), (long) x.get("timevip")));
        return l;
    }

    private static Map<String, Object> row(long id, String name, boolean online, int vip, long timevip, int cnt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("online", online);
        m.put("vip", vip);
        m.put("timevip", timevip);
        m.put("active", timevip > System.currentTimeMillis());
        m.put("count", cnt);
        return m;
    }
}
