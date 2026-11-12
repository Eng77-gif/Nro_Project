package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 123: sự kiện Pokémon (tháng 9) theo bản v10. v10 làm dở (đổi quà ở Chi Chi chỉ báo "đã nhận" mà không có quà, Vỏ Xên không có nguồn),
 * nên giữ phần chạy được và bổ sung cho dùng được:
 * <ul>
 * <li>Boss Meowth (Đội Hỏa Tiễn) x20: rơi Pet Meowth (30%, hạn 1 - 5 ngày), Pet rồng Pikachu thanh long hoặc vật phẩm như v10.</li>
 * <li>Quái rơi Vỏ Xên bọ hung 1/50 (tối đa 200 / ngày).</li>
 * <li>Chi Chi: đổi quà thường (99 Vỏ Xên) = Hộp quà tháng 9, quà VIP (99 Vỏ Xên + 2 Túi đựng) = Hộp quà tháng 9 VIP, tính top "Bóng Master";
 * cửa hàng bằng vàng như v10.</li>
 * </ul>
 *
 * @author Việt
 */
public class Dot123Migration {

    private static final int EV = 19, TOP = 1901;

    public static void run(Connection con) {
        try {
            if (exists(con, "select 1 from server_migration where name = 'dot123_pokemon'")) {
                return;
            }
            update(con, "delete from season_event where id = " + EV);
            update(con, "insert into season_event (id, name, lunar, month, day, days_before, days_after, enabled, bosses, description) values (" + EV
                    + ", 'Pokémon tháng 9', 0, 9, 1, 0, 29, 1, '-377:20', 'Đánh quái nhặt Vỏ Xên bọ hung, săn Meowth (Đội Hỏa Tiễn) có Pet Meowth, "
                    + "Pikachu. Gặp Chi Chi đổi quà, đua top Bóng Master')");
            update(con, "delete from season_event_top where id = " + TOP);
            update(con, "insert into season_event_top (id, event_id, name) values (" + TOP + ", " + EV + ", 'Bóng Master')");
            update(con, "delete from season_event_top_reward where top_id = " + TOP);
            update(con, "insert into season_event_top_reward (top_id, rank_from, rank_to, item_id, qty, hsd_days, min_point) "
                    + "select " + TOP + ", rank_from, rank_to, item_id, qty, hsd_days, min_point from season_event_top_reward where top_id = 801");
            update(con, "delete from season_event_drop where event_id = " + EV);
            update(con, "insert into season_event_drop (event_id, item_id, rate, qty_min, qty_max, source, daily_max, item_to, need_item, mobs, maps) values "
                    + "(" + EV + ", 1664, 200, 1, 1, 'mob', 200, 0, 0, '', ''),"
                    + "(" + EV + ", 1665, 3000, 1, 1, 'boss', 999, 0, 0, '-377', '')");
            update(con, "delete from season_event_exchange where event_id = " + EV);
            update(con, "insert into season_event_exchange (event_id, grp, name, need, reward_item, reward_qty, top_id, top_point, npc) values "
                    + "(" + EV + ", 'Đổi\\nthưởng', 'Đổi quà thường', '1664:99', 1695, 1, " + TOP + ", 1, '81'),"
                    + "(" + EV + ", 'Đổi\\nthưởng', 'Đổi quà VIP', '1664:99,1665:2', 1696, 1, " + TOP + ", 3, '81')");
            long[][] shop = {{1695, 200_000_000}, {1696, 200_000_000}, {1631, 2_000_000_000}, {1591, 500_000_000}, {1757, 500_000_000}, {1665, 200_000_000}};
            for (long[] s : shop) {
                update(con, "insert into season_event_exchange (event_id, grp, name, gold, reward_item, reward_qty, npc) select " + EV
                        + ", 'Cửa\\nhàng', NAME, " + s[1] + ", id, 1, '81' from item_template where id = " + s[0]);
            }
            update(con, "insert into server_migration (name) values ('dot123_pokemon')");
            Logger.success("Đợt 123: sự kiện Pokémon tháng 9\n");
        } catch (Exception e) {
            Logger.error("Đợt 123: " + e.getMessage() + "\n");
        }
    }
}
