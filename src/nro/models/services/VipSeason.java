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

    /** Một món quà: id vật phẩm, số lượng, dòng chỉ số {id, param} (93 = hạn dùng ngày, 30 = không giao dịch). */
    public static final class Reward {

        public final int id, qty;
        public final int[][] opts;
        /** id -11 / -12: ngọc xanh / hồng ngọc ngẫu nhiên từ qty đến max. */
        public int max;

        Reward(int id, int qty, int[]... opts) {
            this.id = id;
            this.qty = qty;
            this.opts = opts;
        }

        public boolean isRandGem() {
            return id == -11 || id == -12;
        }

        /** Phát ngọc ngẫu nhiên, trả về số đã cộng. */
        public int giveGem(Player pl) {
            int n = nro.models.utils.Util.nextInt(qty, Math.max(qty, max));
            if (id == -11) {
                pl.inventory.gem += n;
            } else {
                pl.inventory.ruby += n;
            }
            return n;
        }

        String label() {
            return qty + " - " + Math.max(qty, max) + (id == -11 ? " ngọc xanh" : " hồng ngọc");
        }
    }

    /** Quà 4 gói + có tặng đệ tử thường (khi chưa có) hay không; mặc định = đúng quà cũ trong ToriBot. */
    private static volatile List<List<Reward>> rewards = defaults();
    private static volatile boolean[] givePet = {true, true, true, false};

    private static int[] o(int id, int p) {
        return new int[]{id, p};
    }

    private static List<List<Reward>> defaults() {
        List<List<Reward>> l = new ArrayList<>();
        l.add(List.of(new Reward(457, 200), new Reward(459, 10),
                new Reward(1252, 1, o(50, 10), o(77, 10), o(103, 10), o(93, 30)), new Reward(1248, 1, o(50, 10), o(77, 10), o(103, 10), o(93, 30)),
                new Reward(1256, 1, o(50, 10), o(77, 10), o(103, 10), o(93, 30)), new Reward(987, 5)));
        l.add(List.of(new Reward(457, 500), new Reward(459, 10),
                new Reward(1252, 1, o(50, 12), o(77, 12), o(103, 12), o(93, 30)), new Reward(1248, 1, o(50, 12), o(77, 12), o(103, 12), o(93, 30)),
                new Reward(1254, 1, o(50, 12), o(77, 12), o(103, 12), o(93, 30)), new Reward(987, 10),
                new Reward(584, 1, o(50, 24), o(77, 24), o(117, 15), o(93, 30))));
        l.add(List.of(new Reward(457, 700), new Reward(459, 10),
                new Reward(1252, 1, o(50, 12), o(77, 12), o(103, 12)), new Reward(1248, 1, o(50, 12), o(77, 12), o(103, 12)),
                new Reward(1254, 1, o(50, 12), o(77, 12), o(103, 12)), new Reward(987, 30),
                new Reward(584, 1, o(50, 24), o(77, 24), o(117, 15)), new Reward(1655, 2, o(30, 0)), new Reward(956, 10)));
        l.add(List.of(new Reward(457, 1000), new Reward(459, 10), new Reward(568, 1), new Reward(987, 50),
                new Reward(1554, 1, o(50, 15), o(77, 15), o(103, 15), o(14, 10)),
                new Reward(1929, 1, o(50, 18), o(77, 18), o(5, 18), o(14, 10), o(236, 15)),
                new Reward(1806, 1, o(50, 15), o(77, 15), o(103, 15), o(236, 15)),
                new Reward(1557, 1, o(50, 25), o(77, 25), o(117, 25), o(236, 25)),
                new Reward(1655, 5, o(30, 0)), new Reward(1204, 20)));
        return l;
    }

    public static List<Reward> rewards(int level) {
        load();
        return rewards.get(level - 1);
    }

    public static boolean givePet(int level) {
        load();
        return givePet[level - 1];
    }

    /** Dòng mô tả quà hiện ở Tori Bot. */
    public static String describe(int level) {
        StringBuilder b = new StringBuilder("Nâng cấp VIP " + level + " bạn sẽ nhận được\nX3 Kinh nghiệm 30 ngày");
        if (givePet(level)) {
            b.append("\nTặng 1 đệ tử (nếu chưa có)");
        }
        for (Reward r : rewards(level)) {
            if (r.isRandGem()) {
                b.append('\n').append(r.label());
                continue;
            }
            var t = ItemService.gI().getTemplate(r.id);
            b.append('\n').append(r.qty > 1 ? r.qty + " " : "").append(t != null ? t.name : "#" + r.id);
            for (int[] op : r.opts) {
                if (op[0] == 93) {
                    b.append(" hsd ").append(op[1]).append(" ngày");
                }
            }
        }
        return b.toString();
    }
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
        List<List<Reward>> nr = rewards;
        boolean[] npet = givePet;
        if (b.get("rewards") instanceof List<?> rl) {
            if (rl.size() != 4) {
                throw new IllegalArgumentException("Cần quà đủ 4 gói VIP");
            }
            nr = new ArrayList<>();
            npet = new boolean[4];
            for (int lv = 0; lv < 4; lv++) {
                Map<?, ?> g = (Map<?, ?>) rl.get(lv);
                npet[lv] = Boolean.parseBoolean(String.valueOf(g.get("pet")));
                List<Reward> items = new ArrayList<>();
                for (Object io : (List<?>) g.get("items")) {
                    Map<?, ?> it = (Map<?, ?>) io;
                    int id = num(it.get("id")), qty = num(it.get("qty"));
                    boolean rand = id == -11 || id == -12;
                    if (save && !rand && ItemService.gI().getTemplate(id) == null) {
                        throw new IllegalArgumentException("VIP " + (lv + 1) + ": không có vật phẩm #" + id);
                    }
                    if (qty < 1) {
                        throw new IllegalArgumentException("VIP " + (lv + 1) + ": số lượng #" + id + " phải từ 1");
                    }
                    List<int[]> ops = new ArrayList<>();
                    if (it.get("opts") instanceof List<?> ol) {
                        for (Object op : ol) {
                            List<?> pr = (List<?>) op;
                            ops.add(new int[]{num(pr.get(0)), num(pr.get(1))});
                        }
                    }
                    Reward rw = new Reward(id, qty, ops.toArray(new int[0][]));
                    if (rand) {
                        rw.max = it.get("max") == null ? qty : num(it.get("max"));
                        if (rw.max < qty) {
                            throw new IllegalArgumentException("VIP " + (lv + 1) + ": ngọc nhiều nhất phải >= ít nhất");
                        }
                    }
                    items.add(rw);
                }
                nr.add(items);
            }
        }
        if (save) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("start", s.toString());
            m.put("end", e.toString());
            m.put("prices", np);
            m.put("max", mx);
            m.put("rewards", rewardsJson(nr, npet, false));
            try (Connection con = nro.models.data.LocalManager.getConnection(); PreparedStatement ps = con.prepareStatement("replace into vip_season (id, data) values (1, ?)")) {
                ps.setString(1, GSON.toJson(m));
                ps.executeUpdate();
            }
        }
        start = s;
        end = e;
        prices = np;
        maxBuy = mx;
        rewards = nr;
        givePet = npet;
    }

    private static List<Map<String, Object>> rewardsJson(List<List<Reward>> rw, boolean[] pet, boolean withNames) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int lv = 0; lv < 4; lv++) {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("pet", pet[lv]);
            List<Map<String, Object>> items = new ArrayList<>();
            for (Reward r : rw.get(lv)) {
                Map<String, Object> it = new LinkedHashMap<>();
                it.put("id", r.id);
                it.put("qty", r.qty);
                List<int[]> ops = new ArrayList<>(List.of(r.opts));
                it.put("opts", ops);
                if (r.isRandGem()) {
                    it.put("max", Math.max(r.qty, r.max));
                } else if (withNames) {
                    var t = ItemService.gI().getTemplate(r.id);
                    it.put("name", t != null ? t.name : "?");
                    it.put("icon", t != null ? t.iconID : -1);
                }
                items.add(it);
            }
            g.put("items", items);
            out.add(g);
        }
        return out;
    }

    public static Map<String, Object> get() {
        load();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("start", start.toString());
        m.put("end", end.toString());
        m.put("prices", prices);
        m.put("max", maxBuy);
        m.put("rewards", rewardsJson(rewards, givePet, true));
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
