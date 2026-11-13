package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 139: Santa tab Cửa hàng (17) bán "Đổi đệ tử 2" 999 ngọc, đứng ngay sau "Đặt tên đệ tử" (ShopDAO xếp create_time giảm dần).
 *
 * @author Việt
 */
public class Dot139Migration {

    public static void run(Connection con) {
        try {
            if (exists(con, "select 1 from server_migration where name = 'dot139_doi_de_tu_2'")) {
                return;
            }
            update(con, "update item_shop set create_time = '2022-06-16 08:20:48' where tab_id = 17 and temp_id = 401");
            if (!exists(con, "select 1 from item_shop where tab_id = 17 and temp_id = 1890")) {
                update(con, "insert into item_shop (tab_id, temp_id, is_new, is_sell, type_sell, cost, create_time)"
                        + " values (17, 1890, 0, 1, 1, 999, '2022-06-16 08:20:49')");
            }
            update(con, "insert into server_migration (name) values ('dot139_doi_de_tu_2')");
            Logger.success("Đợt 139: Santa bán Đổi đệ tử 2\n");
        } catch (Exception e) {
            Logger.error("Đợt 139: " + e.getMessage() + "\n");
        }
    }
}
