package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 101: chợ ký gửi đồ sự kiện tách riêng: NPC King Furry (mẫu 68, trước là Panchy không đứng map nào; hình theo source V10: head 1392, body 1393, leg 1391, avatar 11378) đứng ở Siêu thị (map 84, rộng 960px).
 *
 * @author Việt
 */
public class Dot101Migration {

    public static void run(Connection con) {
        try {
            int n = update(con, "update map_template set npcs = concat(left(npcs, length(npcs) - 1), ',[68,860,336]]') where id = 84 and npcs not like '%[68,%'");
            n += update(con, "update npc_template set name = 'King Furry', head = 1392, body = 1393, leg = 1391, avatar = 11378 where id = 68 and name <> 'King Furry'");
            if (n > 0) {
                Logger.success("Đợt 101: thêm NPC chợ đồ sự kiện ở Siêu thị\n");
            }
        } catch (Exception e) {
            Logger.error("Đợt 101: không thêm được NPC chợ đồ sự kiện: " + e.getMessage() + "\n");
        }
    }
}
