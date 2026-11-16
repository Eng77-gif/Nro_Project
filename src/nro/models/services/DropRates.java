package nro.models.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tỉ lệ rơi đồ từ quái theo từng vật phẩm, chỉnh trên panel > Tỉ lệ rơi (bảng drop_rate, % trên mỗi lần quái chết).
 * Mob.getItemMobReward gọi roll(key) thay cho số cứng; mặc định = đúng tỉ lệ cũ trong code.
 */
public final class DropRates {

    /** {key, id vật phẩm hiển thị, tên, nơi rơi, điều kiện, tỉ lệ mặc định %} */
    public static final Object[][] DEF = {
        {"nr_all", 17, "Ngọc rồng 4 - 7 sao", "Mọi bản đồ", "", 1.0},
        {"nr67", 19, "Ngọc rồng 6 - 7 sao", "3 hành tinh, Nappa, Tương lai, Cold", "", 14.2857},
        {"spl", 441, "Sao pha lê", "Mọi bản đồ", "Mặc cải trang có dòng Dò pha lê", 10.0},
        {"thanlinh", 555, "Đồ thần linh", "Cold, Ngục tù, Hành tinh thực vật", "", 0.002},
        {"tl_kgvang", 555, "Đồ thần linh: dòng Ký gửi vàng", "Khi đồ thần linh rơi (quái + boss)", "", 20.0},
        {"tl_kgngoc", 555, "Đồ thần linh: dòng Ký gửi ngọc", "Khi đồ thần linh rơi (quái + boss)", "", 10.0},
        {"tl_hiemquai", 555, "Đồ thần linh: dòng Vật phẩm hiếm rơi từ quái +1-15%", "Khi đồ thần linh rơi (quái + boss)", "", 30.0},
        {"tl_hiemboss", 555, "Đồ thần linh: dòng Vật phẩm hiếm rơi từ boss +1-15%", "Khi đồ thần linh rơi (quái + boss)", "", 20.0},
        {"skh_ngoai", 1634, "Đồ kích hoạt (SKH)", "Map úp SKH", "x may mắn / cỏ bốn lá (tối đa x3)", 0.01},
        {"skh_riengtu", 1634, "Đồ kích hoạt (SKH)", "Khu riêng tư", "x may mắn / cỏ bốn lá (tối đa x3)", 0.06},
        {"capsule_skh", 1634, "Cápsule Vỡ", "Map úp SKH", "", 0.01},
        {"capsule_riengtu", 1634, "Cápsule Vỡ", "Khu riêng tư", "", 0.005},
        {"dosao_3map", 0, "Đồ sao (3 map đầu)", "Map úp SKH", "Sức mạnh càng cao càng ít, 50% có sao", 0.05},
        {"dosao_vaitho", 0, "Đồ sao khác vải thô", "Map úp SKH", "50% có sao", 0.000000625},
        {"manh_ts", 1066, "Mảnh áo / quần / giày / nhẫn / găng", "Ngục tù", "Mặc đủ 5 món hủy diệt, x may mắn", 10.0},
        {"bikip", 1229, "Bí kíp tuyệt kỹ", "Mọi bản đồ", "Sức mạnh từ 40 tỉ, x may mắn (đủ set hủy diệt thêm 20%)", 10.0},
        {"pudding", 663, "Thức ăn (Bánh Pudding...)", "Mọi bản đồ", "Mặc đủ set thần linh", 0.9009},
        {"daluc_cold", 220, "Đá nâng cấp (Đá lục bảo...)", "Cold", "", 20.0},
        {"davun_dt", 225, "Mảnh đá vụn", "Doanh trại", "", 10.0},
        {"davun_3hanhtinh", 225, "Mảnh đá vụn", "3 hành tinh", "", 10.0},
        {"chagio_dt", 1836, "Cuốn chả giò", "Doanh trại", "", 20.0},
        {"hongdao", 541, "Quả hồng đào", "Ngũ Hành Sơn", "", 10.0},
        {"porata_manhvo", 933, "Mảnh vỡ bông tai", "Map up bông tai", "", 10.0},
        {"porata_manhhon", 934, "Mảnh hồn bông tai", "Map up bông tai", "Khi không rơi mảnh vỡ", 5.0},
        {"porata_daxanh", 935, "Đá xanh lam", "Map up bông tai", "Khi không rơi 2 món trên", 0.2},
        {"maydo_capsule", 380, "Viên Capsule kì bí", "Mọi bản đồ", "Đang dùng Máy dò capsule", 20.0},
        {"vang_3hanhtinh", 189, "Vàng", "3 hành tinh", "", 5.0},
        {"vang_nappa", 189, "Vàng", "Nappa", "", 1.0},
        {"vang_tuonglai", 189, "Vàng", "Tương lai", "", 15.0},
        {"vang_cold", 190, "Vàng", "Cold", "", 30.0},
        {"vang_phoban", 190, "Vàng", "Phó bản", "", 1.0},
        {"vang_nguctu_mm", 190, "Vàng", "Ngục tù may mắn", "x may mắn", 50.0}};

    private static final Map<String, Double> RATE = new ConcurrentHashMap<>();
    private static volatile boolean loaded;

    private DropRates() {
    }

    private static Object[] def(String key) {
        for (Object[] d : DEF) {
            if (d[0].equals(key)) {
                return d;
            }
        }
        throw new IllegalArgumentException("Không có tỉ lệ " + key);
    }

    private static void load() {
        if (loaded) {
            return;
        }
        synchronized (RATE) {
            if (loaded) {
                return;
            }
            try (Connection con = nro.models.data.LocalManager.getConnection(); PreparedStatement ps = con.prepareStatement("select k, rate from drop_rate"); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RATE.put(rs.getString(1), rs.getDouble(2));
                }
            } catch (Exception ignored) {
                // chưa có bảng: dùng mặc định
            }
            loaded = true;
        }
    }

    /** Tỉ lệ đã chỉnh trên panel, chưa chỉnh thì def (quà riêng của boss: BossDrops). */
    public static double rateOr(String key, double def) {
        load();
        Double r = RATE.get(key);
        return r != null ? r : def;
    }

    /** Tỉ lệ % hiện tại. */
    public static double rate(String key) {
        load();
        Double r = RATE.get(key);
        return r != null ? r : (Double) def(key)[5];
    }

    /** Tung 1 lần với tỉ lệ (%) x hệ số (may mắn, cỏ bốn lá...). */
    public static boolean roll(String key, double mult) {
        return ThreadLocalRandom.current().nextDouble(100) < rate(key) * mult;
    }

    public static boolean roll(String key) {
        return roll(key, 1);
    }

    public static void set(String key, double rate) throws Exception {
        if (!key.startsWith("boss:")) {
            def(key);
        } else if (java.util.Arrays.stream(BossDrops.DEF).noneMatch(d -> key.equals("boss:" + d[0]))) {
            throw new IllegalArgumentException("Không có tỉ lệ " + key);
        }
        double r = Math.max(0, Math.min(100, rate));
        try (Connection con = nro.models.data.LocalManager.getConnection(); PreparedStatement ps = con.prepareStatement(
                "insert into drop_rate (k, rate) values (?, ?) on duplicate key update rate = values(rate)")) {
            ps.setString(1, key);
            ps.setDouble(2, r);
            ps.executeUpdate();
        }
        RATE.put(key, r);
    }

    public static List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] d : DEF) {
            Map<String, Object> m = new LinkedHashMap<>();
            var t = (int) d[1] > 0 ? ItemService.gI().getTemplate(((Integer) d[1]).shortValue()) : null;
            m.put("key", d[0]);
            m.put("item", d[1]);
            m.put("icon", t != null ? t.iconID : -1);
            m.put("name", d[2]);
            m.put("where", d[3]);
            m.put("cond", d[4]);
            m.put("def", d[5]);
            m.put("rate", rate((String) d[0]));
            out.add(m);
        }
        return out;
    }
}
