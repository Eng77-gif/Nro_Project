package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 115:
 * - Ván bay dùng nhầm hình: Chổi bay phù thủy (1922) dùng hình chổi (mount_4), Máy bay giấy / Bảng đen / Ván khủng long Noel dùng hình
 *   lấy từ v10 (mount_81 - 83); Thú cưỡi chim đất (2041, 2058) đang hiện quả bóng tuyết (mount_25): dùng hình chim đất sét của
 *   Đại bàng đất sét nổ (mount_74), 2 món là 1.
 * - Đeo lưng thiếu khung hình (cờ 73 - 76: Như Ý Kim Cô Bổng, Cửu Xỉ Đinh Ba, Nguyệt Nha Sản, Cửu Hoàn Thiên Tích Trượng) lấy từ v10.
 * - Giáp tập luyện cấp 5: mô tả đúng 50% (trước đây không có tác dụng).
 * - Dòng "Đi cùng ... tăng ..." không còn là dòng chỉ số riêng: chuyển sang kích hoạt ẩn (NPoint.applyHiddenCombos).
 *
 * @author Việt
 */
public class Dot115Migration {

    public static void run(Connection con) {
        try {
            for (int p = 81; p <= 83; p++) {
                for (int s = 0; s < 2; s++) {
                    if (!exists(con, "select 1 from img_by_name where NAME = 'mount_" + p + "_" + s + "'")) {
                        update(con, "insert into img_by_name (NAME, n_frame) values ('mount_" + p + "_" + s + "', 4)");
                    }
                }
            }
            if (!exists(con, "select 1 from server_migration where name = 'dot115_mounts'")) {
                update(con, "update item_template set part = 4 where id = 1922");
                update(con, "update item_template set part = 81 where id = 1942");
                update(con, "update item_template set part = 82 where id = 1943");
                update(con, "update item_template set part = 83 where id = 1950");
                update(con, "update item_template set part = 74 where id in (2041, 2058)");
                update(con, "insert into server_migration (name) values ('dot115_mounts')");
                Logger.success("Đợt 115: sửa hình 6 ván bay\n");
            }
            String[][] flags = {
                {"73", "9652, 9653, 9654, 9655, 9656, 9657, 9658, 9659, 9660, 9661, 9662, 9663"},
                {"74", "9678, 9679, 9680, 9681, 9682, 9683, 9684, 9685, 9686, 9687"},
                {"75", "9690, 9691, 9692, 9693, 9694, 9695, 9696, 9697, 9698, 9699"},
                {"76", "9666, 9667, 9668, 9669, 9670, 9671, 9672, 9673, 9674, 9675"}};
            for (String[] f : flags) {
                update(con, "update flag_bag set icon_data = '" + f[1] + "' where id = " + f[0] + " and trim(icon_data) in ('-1', '')");
            }
            update(con, "update item_template set description = 'Mặc giảm 50% sức đánh, tháo ra tăng 50% sức đánh' where id = 2006");
            int n = update(con, "delete from item_template_option where option_id in (39, 42, 91, 92, 169, 172, 255, 264)");
            if (n > 0) {
                Logger.success("Đợt 115: " + n + " dòng \"đi cùng\" chuyển sang kích hoạt ẩn\n");
            }
        } catch (Exception e) {
            Logger.error("Đợt 115: " + e.getMessage() + "\n");
        }
    }
}
