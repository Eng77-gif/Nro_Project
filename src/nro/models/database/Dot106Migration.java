package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 106: Ván Rồng 7 sao (2100) dùng hình thú cưỡi riêng mount_80 (mây Cân Đẩu Vân ngọc rồng 7 sao, 8 khung, ảnh tạm tới khi có
 * hình thật). Trước dùng chung mount_34 với Ván bay té nước / Ván Bay Trung Thu 2026.
 *
 * @author Việt
 */
public class Dot106Migration {

    public static void run(Connection con) {
        try {
            int n = 0;
            for (String name : new String[]{"mount_80_0", "mount_80_1"}) {
                if (!exists(con, "select 1 from img_by_name where NAME = '" + name + "'")) {
                    n += update(con, "insert into img_by_name (NAME, n_frame) values ('" + name + "', 8)");
                }
            }
            n += update(con, "update item_template set part = 80 where id = 2100 and part <> 80");
            if (n > 0) {
                Logger.success("Đợt 106: Ván Rồng 7 sao dùng hình mount_80\n");
            }
        } catch (Exception e) {
            Logger.error("Đợt 106: " + e.getMessage() + "\n");
        }
    }
}
