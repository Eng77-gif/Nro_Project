package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 132: sự kiện hè 3 làm lại theo video review.
 * - Quy Lão "Đổi thường" (điểm sự kiện): Hộp quà Cađíc VIP 99, Hộp quà Goku Day VIP 2025 99 (30 ngày), Bóng Master 99 (30 ngày),
 *   CT Chi Chi Võ Đài 449, CT Urôn Trư Bát Giới 499, Pet Hải Ly 499, Khủng Long Thơ Mộng 499, Cân Đẩu Vân Thơ Mộng 750,
 *   CT Black Goku Rose 999, Vé nhiệm vụ tháng VIP 999 (40 ngày).
 * - Cửa hàng Chi Chi: Pet Zịt vàng bối rối 1,5 tỉ vàng, Bóng Ultra 99 ngọc, Bóng Poke 20 triệu vàng, Hộp quà Goku Day 10 ngọc,
 *   Hộp quà Cađíc 20 triệu vàng, Rađa dò vỏ Xên 2 ngọc, Túi đựng vỏ Xên bọ hung 2 ngọc.
 * - Rác rơi ở quái (tối đa 99 / ngày) và Musashi / Kochiro / Meowth (5 - 10), bấm Sử dụng: 99 Rác đổi 1 Thùng rác (UseItem); bỏ mục "Dọn rác" 999 Rác.
 *
 * @author Việt
 */
public class Dot132Migration {

    static final int SUMMER_3 = 19, QUY_LAO = 13, CHI_CHI = 81;

    public static void run(Connection con) {
        try {
            // 99 Rác đổi 1 Thùng rác: quái rơi Rác lại (tối đa 99 / ngày), boss Đội Hỏa Tiễn rơi thêm 5 - 10
            if (!exists(con, "select 1 from season_event_drop where event_id = " + SUMMER_3 + " and item_id = 1895 and source = 'mob'")) {
                update(con, "insert into season_event_drop (event_id, item_id, rate, qty_min, qty_max, source, daily_max, item_to, need_item, mobs, maps) values ("
                        + SUMMER_3 + ", 1895, 300, 1, 1, 'mob', 99, 0, 0, '', '')");
            }
            if (exists(con, "select 1 from server_migration where name = 'dot132_he3_video'")) {
                return;
            }
            int ev = SUMMER_3;
            update(con, "delete from season_event_exchange where event_id = " + ev + " and npc = '" + QUY_LAO + "'");
            String q = "insert into season_event_exchange (event_id, grp, name, cost_point, reward_item, reward_qty, reward_days, npc) values ";
            Object[][] ql = {{"Hộp quà Cađíc VIP", 99, 1757, ""}, {"Hộp quà Goku Day VIP 2025", 99, 1840, "30"}, {"Bóng Master", 99, 1894, "30"},
                {"Cải Trang Chi Chi Võ Đài", 449, 1705, ""}, {"CT Urôn Trư Bát Giới", 499, 1698, ""}, {"Pet Hải Ly", 499, 1682, ""},
                {"Khủng Long Thơ Mộng", 499, 1713, ""}, {"Cân Đẩu Vân Thơ Mộng", 750, 1711, ""}, {"CT Black Goku Rose", 999, 1731, ""},
                {"Vé nhiệm vụ tháng vip", 999, 1835, "40"}};
            for (Object[] r : ql) {
                update(con, q + "(" + ev + ", 'Đổi\\nthường', '" + r[0] + "', " + r[1] + ", " + r[2] + ", 1, '" + r[3] + "', '" + QUY_LAO + "')");
            }
            update(con, "delete from season_event_exchange where event_id = " + ev + " and npc = '" + CHI_CHI + "' and grp in ('Cửa\\nhàng', 'Dọn\\nrác')");
            String c = "insert into season_event_exchange (event_id, grp, name, gold, gem, reward_item, reward_qty, reward_days, npc) values ";
            Object[][] cc = {{"Pet Zịt vàng bối rối", 1_500_000_000L, 0, 1631, ""}, {"Bóng Ultra", 0L, 99, 1893, ""}, {"Bóng Poke", 20_000_000L, 0, 1892, ""},
                {"Hộp quà Goku Day", 0L, 10, 1591, ""}, {"Hộp quà Cađíc", 20_000_000L, 0, 1755, ""}, {"Rađa dò vỏ Xên", 0L, 2, 1666, "2"},
                {"Túi đựng vỏ Xên bọ hung", 0L, 2, 1665, ""}};
            for (Object[] r : cc) {
                update(con, c + "(" + ev + ", 'Cửa\\nhàng', '" + r[0] + "', " + r[1] + ", " + r[2] + ", " + r[3] + ", 1, '" + r[4] + "', '" + CHI_CHI + "')");
            }
            update(con, "insert into server_migration (name) values ('dot132_he3_video')");
            Logger.success("Đợt 132: sự kiện hè 3 theo video (Quy Lão Đổi thường, cửa hàng Chi Chi, Rác đổi Thùng rác)\n");
        } catch (Exception e) {
            Logger.error("Đợt 132: " + e.getMessage() + "\n");
        }
    }
}
