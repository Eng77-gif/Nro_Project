package nro.models.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 85:
 * - Dọn NPC không hợp bản đồ: bỏ Bò Mộng ở 3 nhà (Gôhan / Moori / Broly; vẫn còn ở Rừng Karin, Siêu Thị); sự kiện không
 * thả thêm Chi Chi, Santa, Bà Hạt Mít ra các làng nữa (đã có sẵn ở Đảo Kamê, Đảo Guru, Vách núi đen / vách núi).
 * - Sự kiện mới: Ngũ Hành Sơn (tự chạy 1/5 - 26/5, boss Tề Thiên Đại Thánh), và 3 sự kiện chỉ bật tay:
 * Mưa vàng, Lễ hội Ngọc Rồng, Đá Ngũ Sắc.
 *
 * @author Việt
 */
public class Dot85Migration {

    public static final int EV_NGU_HANH_SON = 12, EV_MUA_VANG = 13, EV_NGOC_RONG = 14, EV_DA_NGU_SAC = 15;
    public static final int TOP_NGU_HANH_SON = 1201;

    public static void run(Connection con) {
        if (exists0(con)) {
            return;
        }
        int changed = 0;
        try {
            changed += cleanNpcs(con);
            changed += events(con);
            changed += update(con, "insert into server_migration (name) values ('dot85')");
        } catch (Exception e) {
            Logger.error("Đợt 85: không cập nhật được database: " + e.getMessage() + "\n");
        }
        if (changed > 0) {
            Logger.success("Đợt 85: dọn NPC thừa, thêm sự kiện Ngũ Hành Sơn / Mưa vàng / Lễ hội Ngọc Rồng / Đá Ngũ Sắc (" + changed + " dòng)\n");
        }
    }

    private static boolean exists0(Connection con) {
        try {
            return exists(con, "select 1 from server_migration where name = 'dot85'");
        } catch (Exception e) {
            return false;
        }
    }

    /** Bỏ NPC npcId khỏi danh sách npcs của bản đồ (giữ nguyên kiểu ghi [[...]] hoặc ["[...]"]). */
    private static int removeNpc(Connection con, int mapId, int npcId) throws Exception {
        String npcs;
        try (PreparedStatement ps = con.prepareStatement("select npcs from map_template where id = ?")) {
            ps.setInt(1, mapId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return 0;
                }
                npcs = rs.getString(1);
            }
        }
        org.json.simple.JSONArray arr = (org.json.simple.JSONArray) org.json.simple.JSONValue.parse(npcs);
        if (arr == null) {
            return 0;
        }
        org.json.simple.JSONArray out = new org.json.simple.JSONArray();
        boolean removed = false;
        for (Object o : arr) {
            Object v = o instanceof String str ? org.json.simple.JSONValue.parse(str) : o;
            if (v instanceof org.json.simple.JSONArray a && !a.isEmpty() && String.valueOf(a.get(0)).equals(String.valueOf(npcId))) {
                removed = true;
                continue;
            }
            out.add(o);
        }
        if (!removed) {
            return 0;
        }
        try (PreparedStatement ps = con.prepareStatement("update map_template set npcs = ? where id = ?")) {
            ps.setString(1, out.toJSONString());
            ps.setInt(2, mapId);
            return ps.executeUpdate();
        }
    }

    private static int cleanNpcs(Connection con) throws Exception {
        int n = 0;
        for (int m : new int[]{21, 22, 23}) {
            n += removeNpc(con, m, 17); // Bò Mộng trong nhà
        }
        // NPC sự kiện thả thêm ra các làng / đảo: Chi Chi (81), Santa (39), Bà Hạt Mít (21) đã có chỗ cố định
        n += update(con, "delete from season_event_npc where npc_id in (21, 39, 81)");
        return n;
    }

    private static int event(Connection con, int id, String name, int month, int day, int after, int enabled, String bosses, String desc) throws Exception {
        if (exists(con, "select 1 from season_event where id = " + id)) {
            return 0;
        }
        try (PreparedStatement ps = con.prepareStatement("insert into season_event (id, name, lunar, month, day, days_before, days_after, enabled, bosses, description) values (?, ?, 0, ?, ?, 0, ?, ?, ?, ?)")) {
            ps.setInt(1, id);
            ps.setString(2, name);
            ps.setInt(3, month);
            ps.setInt(4, day);
            ps.setInt(5, after);
            ps.setInt(6, enabled);
            ps.setString(7, bosses);
            ps.setString(8, desc);
            return ps.executeUpdate();
        }
    }

    /** {item, rate / 10000, min, max, nguồn, giới hạn ngày, mobs} */
    private static int drop(Connection con, int ev, int item, int rate, int min, int max, String source, int daily, String mobs) throws Exception {
        try (PreparedStatement ps = con.prepareStatement("insert into season_event_drop (event_id, item_id, rate, qty_min, qty_max, source, daily_max, item_to, need_item, mobs, maps) values (?, ?, ?, ?, ?, ?, ?, 0, 0, ?, '')")) {
            ps.setInt(1, ev);
            ps.setInt(2, item);
            ps.setInt(3, rate);
            ps.setInt(4, min);
            ps.setInt(5, max);
            ps.setString(6, source);
            ps.setInt(7, daily);
            ps.setString(8, mobs);
            return ps.executeUpdate();
        }
    }

    private static int exchange(Connection con, int ev, String grp, String name, String need, long gold, int gem, int item, String days, int perm, int evPoint, int topPoint, String npc) throws Exception {
        try (PreparedStatement ps = con.prepareStatement("insert into season_event_exchange (event_id, grp, name, need, gold, gem, ruby, cost_point, reward_item, reward_qty, reward_qty_max, reward_days, perm_rate, bonus_item, bonus_rate, open_now, event_point, top_id, top_point, npc)"
                + " values (?, ?, ?, ?, ?, ?, 0, 0, ?, 1, 1, ?, ?, 0, 0, 0, ?, ?, ?, ?)")) {
            ps.setInt(1, ev);
            ps.setString(2, grp);
            ps.setString(3, name);
            ps.setString(4, need);
            ps.setLong(5, gold);
            ps.setInt(6, gem);
            ps.setInt(7, item);
            ps.setString(8, days);
            ps.setInt(9, perm);
            ps.setInt(10, evPoint);
            ps.setInt(11, topPoint > 0 ? TOP_NGU_HANH_SON : 0);
            ps.setInt(12, topPoint);
            ps.setString(13, npc);
            return ps.executeUpdate();
        }
    }

    private static int topReward(Connection con, int from, int to, int item, int qty, String days) throws Exception {
        return update(con, "insert into season_event_top_reward (top_id, rank_from, rank_to, item_id, qty, hsd_days, min_point) values ("
                + TOP_NGU_HANH_SON + ", " + from + ", " + to + ", " + item + ", " + qty + ", '" + days + "', 0)");
    }

    private static int events(Connection con) throws Exception {
        int n = 0;
        // ---------------- Ngũ Hành Sơn (Tây Du): 1/5 - 26/5 hằng năm
        if (event(con, EV_NGU_HANH_SON, "Ngũ Hành Sơn", 5, 1, 25, 1, "", // nội dung làm lại ở Đợt 86 (theo bản chính thức, không boss)
                "Tề Thiên Đại Thánh thoát khỏi núi Ngũ Hành! Đánh quái nhặt Quả Hồng Đào (150/ngày), hạ Tề Thiên Đại Thánh lấy Vòng Kim Cô và Quả Hồng Đào Chín. "
                + "Mang đến Chi Chi đổi binh khí Tây Du (Như Ý Kim Cô Bổng, Cửu Xỉ Đinh Ba, Nguyệt Nha Sản, Cửu Hoàn Thiên Tích Trượng), CT Urôn Trư Bát Giới, Bồ cào 9 răng; mỗi lần đổi được điểm đua top.") > 0) {
            n++;
            n += drop(con, EV_NGU_HANH_SON, 541, 500, 1, 1, "mob", 150, "");          // Quả Hồng Đào
            n += drop(con, EV_NGU_HANH_SON, 543, 10000, 2, 4, "boss", 0, "-378");      // Vòng Kim Cô
            n += drop(con, EV_NGU_HANH_SON, 542, 10000, 3, 6, "boss", 0, "-378");      // Quả Hồng Đào Chín
            String g = "Tây\nDu";
            n += exchange(con, EV_NGU_HANH_SON, g, "Như Ý\nKim Cô Bổng", "541:99,543:3", 10_000_000, 0, 1024, "7,15", 3, 3, 1, "81");
            n += exchange(con, EV_NGU_HANH_SON, g, "Cửu Xỉ\nĐinh Ba", "541:99,543:3", 10_000_000, 0, 1025, "7,15", 3, 3, 1, "81");
            n += exchange(con, EV_NGU_HANH_SON, g, "Nguyệt\nNha Sản", "541:99,543:3", 10_000_000, 0, 1026, "7,15", 3, 3, 1, "81");
            n += exchange(con, EV_NGU_HANH_SON, g, "Cửu Hoàn\nThiên Tích Trượng", "541:99,543:3", 10_000_000, 0, 1027, "7,15", 3, 3, 1, "81");
            n += exchange(con, EV_NGU_HANH_SON, g, "CT Urôn\nTrư Bát Giới", "542:10,543:5", 0, 30, 1698, "15,30", 2, 5, 3, "81");
            n += exchange(con, EV_NGU_HANH_SON, g, "Bồ cào\n9 răng", "542:5,543:3", 0, 10, 1699, "15,30", 2, 3, 2, "81");
            n += exchange(con, EV_NGU_HANH_SON, g, "Quạt\nba tiêu", "541:50", 5_000_000, 0, 1023, "7", 0, 1, 1, "81");
            n += update(con, "insert into season_event_top (id, event_id, name) values (" + TOP_NGU_HANH_SON + ", " + EV_NGU_HANH_SON + ", 'Đua top Ngũ Hành Sơn')");
            n += topReward(con, 1, 1, 1698, 1, "");      // top 1: CT Urôn Trư Bát Giới vĩnh viễn
            n += topReward(con, 1, 1, 1024, 1, "");      //        + Như Ý Kim Cô Bổng vĩnh viễn
            n += topReward(con, 2, 3, 1698, 1, "30");
            n += topReward(con, 2, 3, 1024, 1, "30");
            n += topReward(con, 4, 10, 1699, 1, "30");
            n += topReward(con, 11, 100, 542, 20, "");
        }
        // ---------------- chỉ bật tay (enabled = 0): admin bật khi muốn, ví dụ cuối tuần
        if (event(con, EV_MUA_VANG, "Mưa vàng", 1, 1, 0, 0, "", "Bật tay: đánh quái có thể rơi Thỏi vàng (tối đa 20 thỏi / ngày).") > 0) {
            n++;
            n += drop(con, EV_MUA_VANG, 457, 300, 1, 1, "mob", 20, "");
        }
        if (event(con, EV_NGOC_RONG, "Lễ hội Ngọc Rồng", 1, 1, 0, 0, "", "Bật tay: đánh quái có thể rơi Ngọc Rồng 4 - 7 sao (mỗi loại tối đa 3 viên / ngày).") > 0) {
            n++;
            for (int it = 17; it <= 20; it++) {
                n += drop(con, EV_NGOC_RONG, it, 60, 1, 1, "mob", 3, "");
            }
        }
        if (event(con, EV_DA_NGU_SAC, "Đá Ngũ Sắc", 1, 1, 0, 0, "", "Bật tay: đánh quái rơi đá nâng cấp (lục bảo, saphia, ruby, titan, thạch anh tím; mỗi loại tối đa 30 viên / ngày).") > 0) {
            n++;
            for (int it = 220; it <= 224; it++) {
                n += drop(con, EV_DA_NGU_SAC, it, 150, 1, 2, "mob", 30, "");
            }
        }
        return n;
    }
}
