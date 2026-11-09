package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 107: hộp quà / rương / capsule / túi (và Set kích hoạt Beerus 1 món tự chọn) gộp chung 1 ô trong hành trang thay vì mỗi hộp 1 ô.
 *
 * @author Việt
 */
public class Dot107Migration {

    /** Điều kiện SQL chọn hộp quà (bỏ pet / trang bị: head < 0). Giữ đồng bộ với InventoryService.isGiftBox. */
    public static final String GIFT_BOX = "TYPE in (27, 29) and head < 0 and (NAME regexp '^(Hộp|Rương|Capsule|Cápsule|Túi) ' or NAME like 'Set kích hoạt%tự chọn')";

    public static void run(Connection con) {
        try {
            int n = update(con, "update item_template set is_up_to_up = 1 where is_up_to_up = 0 and " + GIFT_BOX);
            if (n > 0) {
                Logger.success("Đợt 107: " + n + " hộp quà gộp chung 1 ô\n");
            }
        } catch (Exception e) {
            Logger.error("Đợt 107: " + e.getMessage() + "\n");
        }
    }
}
