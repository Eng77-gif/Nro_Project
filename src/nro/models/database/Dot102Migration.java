package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 102:
 * - Mỗi map 15 khu (trước 10 khu: khu 0 - 9), riêng Đảo Kamê (map 5) 30 khu.
 * - Shop Santa (tab Hỗ trợ): Vé vàng (1876, không dùng được) thay bằng Phiếu sao vàng may mắn (2021: vào Ngục tù may mắn, vàng rơi
 *   tới 500.000 / cục), giá giữ 19.999 ngọc; thêm Năng lượng E10 (2035) giá 10 ngọc.
 * - Boss Mặt Trời chỉ xuất hiện trong Sự kiện Hè (season_event 5), 20 con.
 *
 * @author Việt
 */
public class Dot102Migration {

    public static void run(Connection con) {
        try {
            int n = update(con, "update map_template set zones = 15 where zones = 10 and id <> 5")
                    + update(con, "update map_template set zones = 30 where id = 5 and zones < 30")
                    + update(con, "update item_shop set temp_id = 2021 where temp_id = 1876 and tab_id = 34");
            if (!Dot77Migration.exists(con, "select 1 from item_shop where temp_id = 2035 and tab_id = 34")) {
                n += update(con, "insert into item_shop (tab_id, temp_id, is_new, is_sell, type_sell, cost, icon_spec) values (34, 2035, 1, 1, 1, 10, 0)");
            }
            n += update(con, "update season_event set bosses = if(bosses is null or bosses = '', '-799:20', concat(bosses, ',-799:20')) where id = 5 and (bosses is null or bosses not like '%-799:%')");
            if (n > 0) {
                Logger.success("Đợt 102: 15 khu / map (Đảo Kamê 30), Phiếu sao vàng thay vé vàng, Năng lượng E10 ở Santa, boss Mặt Trời vào Sự kiện Hè\n");
            }
        } catch (Exception e) {
            Logger.error("Đợt 102: không cập nhật được: " + e.getMessage() + "\n");
        }
    }
}
