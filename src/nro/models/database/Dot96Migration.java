package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 96: giá bán thỏi vàng (457) 37 triệu, thỏi vàng NRJ (2032) 150 triệu (giá lấy từ item_template.gold, dùng chung cho cửa hàng,
 * bán nhiều và bot).
 *
 * @author Việt
 */
public class Dot96Migration {

    public static void run(Connection con) {
        try {
            int n = update(con, "update item_template set gold = 37000000 where id = 457 and gold <> 37000000")
                    + update(con, "update item_template set gold = 150000000, description = 'Bán cho cửa hàng được 150 triệu vàng' where id = 2032 and gold <> 150000000");
            if (n > 0) {
                Logger.success("Đợt 96: giá thỏi vàng 37 triệu, thỏi vàng NRJ 150 triệu\n");
            }
        } catch (Exception e) {
            Logger.error("Đợt 96: không đổi được giá thỏi vàng: " + e.getMessage() + "\n");
        }
    }
}
