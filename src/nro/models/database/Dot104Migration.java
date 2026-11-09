package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 104: xoá bảng xếp hạng Siêu hạng cũ (super_rank chứa hơn 1.300 dòng của nhân vật mẫu không còn tồn tại, hiện thành
 * "Top 100 Cao Thủ" hạng 692, 1321...). Chạy 1 lần; người chơi vào Siêu hạng sẽ được xếp hạng lại từ đầu.
 *
 * @author Việt
 */
public class Dot104Migration {

    public static void run(Connection con) {
        try {
            if (exists(con, "select 1 from server_migration where name = 'dot104_superrank'")) {
                return;
            }
            int n = update(con, "delete from super_rank");
            update(con, "insert into server_migration (name) values ('dot104_superrank')");
            Logger.success("Đợt 104: đã xoá " + n + " dòng bảng xếp hạng Siêu hạng cũ\n");
        } catch (Exception e) {
            Logger.error("Đợt 104: không xoá được bảng xếp hạng Siêu hạng: " + e.getMessage() + "\n");
        }
    }
}
