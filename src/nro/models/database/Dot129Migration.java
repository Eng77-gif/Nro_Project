package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 129:
 * - Bóng Poke / Bóng Ultra / Bóng Master mở ra pet Pokémon, tỉ lệ vĩnh viễn 1% / 3% / 5% (Master có thêm MewTwo).
 * - Gia hạn mầm xếp ngay sau Phiếu sao vàng may mắn ở cửa hàng Santa.
 * - Sự kiện hè 3: map Mùa hè (188, chép Ngọn núi phía bắc - map Xên con cấp 6), vào bằng Rađa dò vỏ Xên; boss Xên hoàn thiện 1 tiếng / lần,
 *   rơi vỏ Xên như boss Xên.
 * - Sa mạc hoang vu (165) trả lại nền / ô gạch như cũ.
 *
 * @author Việt
 */
public class Dot129Migration {

    public static final int MAP_MUA_HE = 188;

    public static void run(Connection con) {
        try {
            update(con, "update map_template set tile_id = 12, bg_id = 11, bg_type = 2 where id = 165");
            if (exists(con, "select 1 from server_migration where name = 'dot129'")) {
                return;
            }
            // bóng Pokémon
            update(con, "delete from item_box_reward where box_id in (1892, 1893, 1894)");
            int[][] balls = {{1892, 1}, {1893, 3}, {1894, 5}};
            for (int[] b : balls) {
                for (int pet = 1870; pet <= 1873; pet++) {
                    update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) values ("
                            + b[0] + ", " + pet + ", 1, 1, 25, '1,3,7', " + b[1] + ", 0)");
                }
            }
            update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) values "
                    + "(1893, 1874, 1, 1, 4, '1,3,7', 3, 0), (1894, 1874, 1, 1, 15, '1,3,7', 5, 0)");
            // gia hạn mầm ngay sau Phiếu sao vàng (cửa hàng xếp theo create_time giảm dần)
            update(con, "update item_shop set create_time = '2029-12-31 23:58:40' where tab_id = 34 and temp_id = 1703");
            update(con, "update item_shop set create_time = '2029-12-31 23:58:30' where tab_id = 34 and temp_id = 1717");
            // map Mùa hè
            update(con, "delete from map_template where id = " + MAP_MUA_HE);
            update(con, "insert into map_template (id, NAME, zones, max_player, data, type, planet_id, bg_type, tile_id, bg_id, waypoints, mobs, npcs, is_map_double) "
                    + "select " + MAP_MUA_HE + ", 'Map Mùa hè', zones, max_player, data, type, planet_id, bg_type, tile_id, bg_id, waypoints, mobs, npcs, is_map_double "
                    + "from map_template where id = 98");
            update(con, "update season_event set bosses = concat(bosses, ',-378:1') where id = " + Dot127Migration.SUMMER_3 + " and bosses not like '%-378%'");
            update(con, "update season_event_drop set mobs = concat(mobs, ',-378') where event_id = " + Dot127Migration.SUMMER_3
                    + " and item_id = 1664 and source = 'boss' and mobs not like '%-378%'");
            update(con, "insert into server_migration (name) values ('dot129')");
            Logger.success("Đợt 129: bóng Pokémon, gia hạn mầm, map Mùa hè + Xên hoàn thiện\n");
        } catch (Exception e) {
            Logger.error("Đợt 129: " + e.getMessage() + "\n");
        }
    }
}
