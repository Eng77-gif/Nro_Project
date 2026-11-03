package nro.models.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 89:
 * - Sự kiện "Quán phở Anh 2" (id 16, bật / tắt trên panel): đánh quái nhặt Bột gạo, Thịt tươi, Cục xương, mang tới Chi Chi nấu
 * Tô phở tái nhiều hành / tái bò viên / nạm 2 quẩy (vật phẩm có sẵn: ăn vào đổi chiêu 2 / 3 / 4 cho đệ tử), mỗi tô được điểm đua top.
 * - Sao Pha Lê 10 (id 18) và Đua top tặng bộ ngọc rồng cho Quy Lão (id 17) thành sự kiện riêng: tự chạy theo lịch cũ,
 * admin bật / tắt / tự động trên web panel như các sự kiện khác.
 *
 * @author Việt
 */
public class Dot89Migration {

    public static final int EV_PHO = 16, EV_QUY_LAO = 17, EV_SPL10 = 18;
    public static final int TOP_PHO = 1601;
    private static final int BOT_GAO = 1547, THIT = 1549, XUONG = 460;
    private static final int PHO_TAI = 1936, PHO_BO_VIEN = 1939, PHO_NAM = 1938;

    public static void run(Connection con) {
        int changed = 0;
        try {
            // 3 vùng Ngũ Hành Sơn: đặt tên 1 / 2 / 3 cho map và cổng dịch chuyển (đi bộ 122 <-> 124 <-> 123)
            changed += update(con, "update map_template set name = 'Ngũ Hành Sơn 1' where id = 122 and name = 'Ngũ Hành Sơn'");
            changed += update(con, "update map_template set name = 'Ngũ Hành Sơn 2' where id = 124 and name = 'Ngũ Hành Sơn'");
            changed += update(con, "update map_template set name = 'Ngũ Hành Sơn 3' where id = 123 and name = 'Ngũ Hành Sơn'");
            changed += update(con, "update map_template set waypoints = replace(waypoints, '\"Ngũ Hành Sơn\",0,263', '\"Ngũ Hành Sơn 2\",0,263') where id = 122");
            changed += update(con, "update map_template set waypoints = replace(waypoints, '\"Ngũ Hành Sơn\",1512', '\"Ngũ Hành Sơn 2\",1512') where id = 123");
            changed += update(con, "update map_template set waypoints = replace(replace(waypoints, '\"Ngũ Hành Sơn\",0,336', '\"Ngũ Hành Sơn 3\",0,336'), '\"Ngũ Hành Sơn\",1224', '\"Ngũ Hành Sơn 1\",1224') where id = 124");
            if (!exists(con, "select 1 from server_migration where name = 'dot89'")) {
                changed += event(con, EV_PHO, "Quán phở Anh 2", 0, 1, 1, 0, 0, 0,
                        "Quán phở Anh 2 mở cửa! Đánh quái nhặt Bột gạo, Thịt tươi, Cục xương rồi mang tới Chi Chi nấu phở: "
                        + "Tô phở tái nhiều hành (đổi chiêu 2 đệ tử), Tô phở tái bò viên (đổi chiêu 3), Tô phở nạm 2 quẩy (đổi chiêu 4). "
                        + "Mỗi tô nấu được cộng điểm đua top Quán phở (1 / 2 / 3 điểm).");
                changed += drop(con, BOT_GAO, 600, 1, 2, 120);
                changed += drop(con, THIT, 450, 1, 1, 80);
                changed += drop(con, XUONG, 300, 1, 1, 50);
                String g = "Quán\nphở";
                changed += exchange(con, g, "Tô phở\ntái nhiều hành", BOT_GAO + ":10," + THIT + ":5," + XUONG + ":3", 5_000_000, 0, PHO_TAI, 1);
                changed += exchange(con, g, "Tô phở\ntái bò viên", BOT_GAO + ":15," + THIT + ":10," + XUONG + ":5", 10_000_000, 0, PHO_BO_VIEN, 2);
                changed += exchange(con, g, "Tô phở\nnạm 2 quẩy", BOT_GAO + ":20," + THIT + ":15," + XUONG + ":10", 0, 10, PHO_NAM, 3);
                changed += update(con, "delete from season_event_top_reward where top_id = " + TOP_PHO);
                changed += update(con, "delete from season_event_top where id = " + TOP_PHO);
                changed += update(con, "insert into season_event_top (id, event_id, name) values (" + TOP_PHO + ", " + EV_PHO + ", 'Đua top Quán phở Anh 2')");
                changed += topReward(con, 1, 1, PHO_NAM, 20);
                changed += topReward(con, 2, 3, PHO_NAM, 10);
                changed += topReward(con, 4, 10, PHO_BO_VIEN, 10);
                changed += topReward(con, 11, 100, PHO_TAI, 5);
                // đua top Quy Lão: cùng lịch Trung Thu (âm lịch 15/8, trước 35 ngày, sau 23 ngày)
                changed += event(con, EV_QUY_LAO, "Đua top tặng ngọc rồng Quy Lão", 1, 8, 15, 35, 23, 1,
                        "Tặng Quy Lão Kame 1 bộ ngọc rồng 1 - 7 sao được 1 điểm đua top. Top 1 - 100 nhận Ván Rồng 7 Sao và nhiều phần quà, top 1 - 3 nhận thêm hào quang. "
                        + "Admin chốt top bằng lệnh chottopql.");
                // Sao Pha Lê 10: 31/10 - hết 20/11 (như lịch cũ)
                changed += event(con, EV_SPL10, "Nâng cấp Sao Pha Lê 10", 0, 10, 31, 0, 20, 1,
                        "Trang bị đập lên 10 sao trong thời gian sự kiện được dòng Hào quang chân Sao Pha Lê 10 (+6% SĐ, HP, KI, +2% sát thương cuối) "
                        + "và tính vào nhiệm vụ mốc ở Bà Hạt Mít (hộp ngọc rồng xanh, Capsule Vỡ, Hộp Set kích hoạt, hào quang chân vĩnh viễn).");
                changed += update(con, "insert into server_migration (name) values ('dot89')");
            }
        } catch (Exception e) {
            Logger.error("Đợt 89: không cập nhật được database: " + e.getMessage() + "\n");
        }
        if (changed > 0) {
            Logger.success("Đợt 89: sự kiện Quán phở Anh 2, bật / tắt Sao Pha Lê 10 và đua top Quy Lão trên panel (" + changed + " dòng)\n");
        }
    }

    private static int event(Connection con, int id, String name, int lunar, int month, int day, int before, int after, int enabled, String desc) throws Exception {
        if (exists(con, "select 1 from season_event where id = " + id)) {
            return 0;
        }
        try (PreparedStatement ps = con.prepareStatement("insert into season_event (id, name, lunar, month, day, days_before, days_after, enabled, bosses, description) values (?, ?, ?, ?, ?, ?, ?, ?, '', ?)")) {
            ps.setInt(1, id);
            ps.setString(2, name);
            ps.setInt(3, lunar);
            ps.setInt(4, month);
            ps.setInt(5, day);
            ps.setInt(6, before);
            ps.setInt(7, after);
            ps.setInt(8, enabled);
            ps.setString(9, desc);
            return ps.executeUpdate();
        }
    }

    private static int drop(Connection con, int item, int rate, int min, int max, int daily) throws Exception {
        return update(con, "insert into season_event_drop (event_id, item_id, rate, qty_min, qty_max, source, daily_max, item_to, need_item, mobs, maps) values ("
                + EV_PHO + ", " + item + ", " + rate + ", " + min + ", " + max + ", 'mob', " + daily + ", 0, 0, '', '')");
    }

    private static int exchange(Connection con, String grp, String name, String need, long gold, int gem, int item, int topPoint) throws Exception {
        try (PreparedStatement ps = con.prepareStatement("insert into season_event_exchange (event_id, grp, name, need, gold, gem, ruby, cost_point, reward_item, reward_qty, reward_qty_max, reward_days, perm_rate, bonus_item, bonus_rate, open_now, event_point, top_id, top_point, npc)"
                + " values (?, ?, ?, ?, ?, ?, 0, 0, ?, 1, 1, '', 0, 0, 0, 0, ?, ?, ?, '81')")) {
            ps.setInt(1, EV_PHO);
            ps.setString(2, grp);
            ps.setString(3, name);
            ps.setString(4, need);
            ps.setLong(5, gold);
            ps.setInt(6, gem);
            ps.setInt(7, item);
            ps.setInt(8, topPoint);
            ps.setInt(9, TOP_PHO);
            ps.setInt(10, topPoint);
            return ps.executeUpdate();
        }
    }

    private static int topReward(Connection con, int from, int to, int item, int qty) throws Exception {
        return update(con, "insert into season_event_top_reward (top_id, rank_from, rank_to, item_id, qty, hsd_days, min_point) values ("
                + TOP_PHO + ", " + from + ", " + to + ", " + item + ", " + qty + ", '', 0)");
    }
}
