package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 133:
 * - Quái Cađíc M (Sa mạc hoang vu, mẫu 95) là quái loại 1 "đi lại" với vùng di chuyển 0: client tự cho nó đi qua lại tại chỗ nên
 *   quay 2 đầu liên tục. Đổi thành loại 0 đứng yên như Mộc nhân / máy đo sức mạnh (DataGame.vsData 29 để client tải lại).
 * - Mô tả Cỏ bốn lá đúng tác dụng thật (UseItem / Mob.skhLuckBoost).
 *
 * @author Việt
 */
public class Dot133Migration {

    public static void run(Connection con) {
        try {
            // Cađíc M đi lại như v10 (loại 1, vùng 33, tốc độ 1): vùng 0 làm client cho nó quay qua quay lại tại chỗ liên tục
            update(con, "update mob_template set type = 1, range_move = 33, speed = 1 where id = 95 and (type <> 1 or range_move <> 33 or speed <> 1)");
            if (exists(con, "select 1 from server_migration where name = 'dot133_cadic_co4la'")) {
                return;
            }
            // cột description tối đa 75 ký tự; mặc đủ cải trang, pet, đeo lưng, ván bay thì x3 (Mob.skhLuckBoost)
            update(con, "update item_template set description = '30 phút/lá (cộng dồn tối đa 500 phút): +50% rơi đồ kích hoạt từ quái' where id = 1635");
            update(con, "insert into server_migration (name) values ('dot133_cadic_co4la')");
            Logger.success("Đợt 133: mô tả Cỏ bốn lá\n");
        } catch (Exception e) {
            Logger.error("Đợt 133: " + e.getMessage() + "\n");
        }
    }
}
