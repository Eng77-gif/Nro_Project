package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 105: Phòng thí nghiệm Myuu (map 166) dùng bản của v10 (bản cũ thiếu ô nền đất của bộ ô gạch 23 nên không có chỗ đứng):
 * ô gạch data/map/tile_map_data/166 lấy từ v10, bộ ô gạch 33, nền 20. Chuỗi Bardock thêm bước hạ boss Chilled.
 *
 * @author Việt
 */
public class Dot105Migration {

    public static void run(Connection con) {
        try {
            int n = update(con, "update map_template set tile_id = 33, bg_id = 20, bg_type = 0, data = '[0,2,0,33,20]' where id = 166 and tile_id <> 33");
            if (n > 0) {
                Logger.success("Đợt 105: Phòng thí nghiệm Myuu dùng bản v10\n");
            }
        } catch (Exception e) {
            Logger.error("Đợt 105: " + e.getMessage() + "\n");
        }
        try {
            // Lọ nước phép (ghép 7 Ngọc Rồng 1 sao thành Ngọc rồng Siêu Cấp ở Bà Hạt Mít) bán cạnh Đá bảo vệ: 10 ngọc
            if (!exists(con, "select 1 from item_shop where temp_id = 1029")) {
                update(con, "insert into item_shop (tab_id, temp_id, is_new, is_sell, type_sell, cost, icon_spec) "
                        + "select tab_id, 1029, 1, 1, 1, 10, 0 from item_shop where temp_id = 987 limit 1");
            }
        } catch (Exception e) {
            Logger.error("Đợt 105: không thêm được Lọ nước phép vào cửa hàng: " + e.getMessage() + "\n");
        }
        try {
            // Chuỗi Bardock (nhiệm vụ 29): thêm bước hạ Chilled ở hành tinh thực vật rồi về báo Bardock (nhận thưởng)
            if (!exists(con, "select 1 from server_migration where name = 'dot105_chilled'")) {
                update(con, "insert into task_sub_template (task_main_id, NAME, max_count, notify, npc_id, map) values "
                        + "(29, 'Hạ Chilled', 1, 'Chilled đang xâm lược hành tinh thực vật', -1, 162),"
                        + " (29, 'Báo tin cho Bardock', 1, '', 70, 160)");
                update(con, "insert into server_migration (name) values ('dot105_chilled')");
                Logger.success("Đợt 105: chuỗi Bardock thêm bước hạ Chilled\n");
            }
        } catch (Exception e) {
            Logger.error("Đợt 105: không thêm được bước hạ Chilled: " + e.getMessage() + "\n");
        }
    }
}
