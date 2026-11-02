package nro.models.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import nro.models.utils.Logger;

/**
 * Đợt 77:
 * - Đeo lưng cánh (Cánh thiên thần sa ngã, bản đệ tử, Cánh hào khí) trước đây trỏ vào hình Ngọc Rồng Super 5 - 7 sao
 * (flag_bag 154 - 156): tạo flag_bag riêng theo đúng khung hình cánh.
 * - Diều rồng băng dùng nhầm hình Rìu Sơn Tinh (148), Cá Koi zombie dùng nhầm hình rồng hồng (145).
 *
 * @author Việt
 */
public class Dot77Migration {

    /** {flag_bag id, khung hình, item, part cũ} */
    private static final Object[][] FLAGS = {
        {176, "16175, 16176, 16177, 16178, 16179, 16180", 1927, 156}, // Cánh thiên thần sa ngã
        {177, "16217, 16218, 16219, 16220, 16221, 16222", 1930, 155}, // Cánh thiên thần sa ngã (Đệ)
        {178, "16831, 16832, 16833, 16834", 1998, 154}, // Cánh hào khí
        {179, "14512", 1751, 148}, // Diều rồng băng
    };

    public static void run(Connection con) {
        int changed = 0;
        try {
            for (Object[] f : FLAGS) {
                if (!exists(con, "select 1 from flag_bag where id = " + f[0])) {
                    changed += update(con, "insert into flag_bag (id, icon_data, NAME, gold, gem, icon_id) values (" + f[0] + ", '" + f[1] + "', 'flag_bag', -1, -1, 0)");
                }
                changed += update(con, "update item_template set part = " + f[0] + " where id = " + f[2] + " and part = " + f[3]);
            }
            changed += update(con, "update flag_bag set icon_data = '14262' where id = 145 and icon_data like '14243%'");
        } catch (Exception e) {
            Logger.error("Đợt 77: không cập nhật được đeo lưng: " + e.getMessage() + "\n");
        }
        if (changed > 0) {
            Logger.success("Đợt 77: sửa hình đeo lưng cánh / diều / cá koi (" + changed + " dòng)\n");
        }
    }

    static boolean exists(Connection con, String sql) throws Exception {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next();
        }
    }

    static int update(Connection con, String sql) throws Exception {
        try (Statement st = con.createStatement()) {
            return st.executeUpdate(sql);
        }
    }
}
