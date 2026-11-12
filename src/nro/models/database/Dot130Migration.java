package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 130:
 * - Xoá toàn bộ bang hội và ký gửi (chạy 1 lần, theo yêu cầu): mọi nhân vật ra khỏi bang.
 * - Map Mùa hè (188) bỏ 2 cổng ra vào: chỉ vào bằng Rađa dò vỏ Xên, hết 3 tiếng tự về nhà.
 *
 * @author Việt
 */
public class Dot130Migration {

    public static void run(Connection con) {
        try {
            update(con, "update map_template set waypoints = '[]' where id = " + Dot129Migration.MAP_MUA_HE);
            if (exists(con, "select 1 from server_migration where name = 'dot130_xoa_bang_kygui'")) {
                return;
            }
            int clans = update(con, "delete from clan");
            update(con, "update player set clan_id = -1");
            int consign = update(con, "delete from shop_ky_gui");
            update(con, "insert into server_migration (name) values ('dot130_xoa_bang_kygui')");
            Logger.success("Đợt 130: xoá " + clans + " bang hội, " + consign + " món ký gửi\n");
        } catch (Exception e) {
            Logger.error("Đợt 130: " + e.getMessage() + "\n");
        }
    }
}
