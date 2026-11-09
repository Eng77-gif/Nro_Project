package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 108: 7 ván bay / thú cưỡi chỉ có 1 trong 2 lớp hình (mount_{part}_0 / _1) nên client lỗi hành trang khi vẽ nhân vật cưỡi:
 * Ván bay Cây Thông, Máy bay 41, Chổi bay phù thủy, Máy bay giấy, Bảng đen, Ván khủng long Noel, Máy bay NRO. Thêm lớp còn thiếu
 * là ảnh trống (như các thú cưỡi khác), cùng số khung với lớp có hình.
 *
 * @author Việt
 */
public class Dot108Migration {

    private static final Object[][] LAYERS = {{"mount_48_1", 4}, {"mount_52_0", 4}, {"mount_64_0", 8}, {"mount_65_0", 8},
        {"mount_67_0", 8}, {"mount_68_0", 8}, {"mount_69_0", 8}};

    public static void run(Connection con) {
        try {
            int n = 0;
            for (Object[] l : LAYERS) {
                if (!exists(con, "select 1 from img_by_name where NAME = '" + l[0] + "'")) {
                    n += update(con, "insert into img_by_name (NAME, n_frame) values ('" + l[0] + "', " + l[1] + ")");
                }
            }
            if (n > 0) {
                Logger.success("Đợt 108: thêm " + n + " lớp hình thú cưỡi còn thiếu\n");
            }
        } catch (Exception e) {
            Logger.error("Đợt 108: " + e.getMessage() + "\n");
        }
    }
}
