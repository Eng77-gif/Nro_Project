package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 111: Hộp Kẹo Ma Quỷ (sự kiện Halloween) làm ra có hạn sử dụng 35 ngày như bản chính thức. Hộp nhận thêm gộp vào ô hộp đang
 * có và lấy hạn của hộp trước (InventoryService.addItemList).
 *
 * @author Việt
 */
public class Dot111Migration {

    public static void run(Connection con) {
        try {
            if (update(con, "update season_event_exchange set reward_days = '35' where event_id = 7 and reward_item = 1356 and (reward_days is null or reward_days = '')") > 0) {
                Logger.success("Đợt 111: Hộp Kẹo Ma Quỷ hạn sử dụng 35 ngày\n");
            }
        } catch (Exception e) {
            Logger.error("Đợt 111: " + e.getMessage() + "\n");
        }
    }
}
