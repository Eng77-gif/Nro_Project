package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 121: Trung Thu theo bản v10: boss Nguyệt thần x20, Thỏ đại ca x20, Meowth x20; làm bánh đúng công thức v10 (99 Bột mì, 5 Đậu xanh,
 * 2 Trứng vịt muối, 1 Gà quay / Hạt sen): Gà Quay 20 triệu vàng, Gà Quay hảo hạng 1.000 hồng ngọc (30% thêm Bánh thập cẩm),
 * Hạt Sen 1.000 hồng ngọc.
 *
 * @author Việt
 */
public class Dot121Migration {

    public static void run(Connection con) {
        try {
            if (exists(con, "select 1 from server_migration where name = 'dot121_trungthu'")) {
                return;
            }
            update(con, "update season_event set bosses = '-345:20,-374:20,-377:20' where id = 6");
            update(con, "update season_event_exchange set need = '888:99,889:5,886:2,887:1', gold = 20000000, ruby = 0 where event_id = 6 and name = 'Bánh\\nGà Quay'");
            update(con, "update season_event_exchange set need = '888:99,889:5,886:2,887:1', gold = 0, ruby = 1000, bonus_item = 891, bonus_rate = 30 "
                    + "where event_id = 6 and name = 'Gà Quay\\nhảo hạng'");
            update(con, "update season_event_exchange set need = '888:99,889:5,886:2,1312:1', gold = 0, ruby = 1000 where event_id = 6 and name = 'Bánh\\nHạt Sen'");
            update(con, "insert into server_migration (name) values ('dot121_trungthu')");
            Logger.success("Đợt 121: Trung Thu theo bản v10\n");
        } catch (Exception e) {
            Logger.error("Đợt 121: " + e.getMessage() + "\n");
        }
    }
}
