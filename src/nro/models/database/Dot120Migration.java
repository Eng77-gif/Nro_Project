package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 120: 8/3 và Giỗ tổ Hùng Vương theo bản v10.
 * <ul>
 * <li>8/3: quái rơi Bông hoa 1/50, tối đa 100 / ngày (v10 chỉ có rơi hoa).</li>
 * <li>Hùng Vương: boss Thủy Tinh x10 như v10; Nồi bánh nấu Bánh dầy 1 triệu vàng, Bánh chưng Lang Liêu 5 triệu vàng (cùng nguyên liệu v10).</li>
 * </ul>
 *
 * @author Việt
 */
public class Dot120Migration {

    public static void run(Connection con) {
        try {
            if (exists(con, "select 1 from server_migration where name = 'dot120_83_hv'")) {
                return;
            }
            update(con, "delete from season_event_drop where event_id = 3 and item_id = 610");
            update(con, "insert into season_event_drop (event_id, item_id, rate, qty_min, qty_max, source, daily_max, item_to, need_item, mobs, maps) values "
                    + "(3, 610, 200, 1, 1, 'mob', 100, 0, 0, '', '')");
            update(con, "update season_event set bosses = '-355:10' where id = 4");
            update(con, "update season_event_exchange set gold = 1000000 where event_id = 4 and npc = '66' and reward_item = 1542");
            update(con, "update season_event_exchange set gold = 5000000 where event_id = 4 and npc = '66' and reward_item = 1556");
            update(con, "insert into server_migration (name) values ('dot120_83_hv')");
            Logger.success("Đợt 120: 8/3, Hùng Vương theo bản v10\n");
        } catch (Exception e) {
            Logger.error("Đợt 120: " + e.getMessage() + "\n");
        }
    }
}
