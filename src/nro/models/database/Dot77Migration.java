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
 * Đợt 78: gán hình sau lưng cho các đeo lưng chưa có (part -1) và Lưỡi hái (cờ 300 không tồn tại).
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
        // Đợt 78: đeo lưng chưa có hình sau lưng (part -1) hoặc trỏ cờ không tồn tại
        {180, "14001, 14002, 14003, 14004, 14005, 14006, 14007, 14008, 14009", 1706, 300}, // Lưỡi hái
        {181, "17570, 17571, 17572, 17573, 17574, 17575", 2083, -1}, // Lồng đèn Thỏ trắng
        {183, "16532, 16533, 16534, 16535, 16536, 16537, 16538, 16539", 1963, -1}, // Đeo lưng lồng đèn Tết
        {184, "16686, 16687, 16688, 16689, 16690, 16691, 16692, 16693, 16694, 16695, 16696, 16697, 16698, 16699", 1979, -1}, // Bao lì xì
        {185, "16711, 16712, 16713, 16714", 1981, -1}, // Bư tập tạ
        {186, "16716, 16717, 16718, 16719", 1983, -1}, // Cờ đeo lưng cớt hồng
        {186, "16716, 16717, 16718, 16719", 1993, -1}, // Cờ đeo lưng cớt hồng VIP
        {187, "16754, 16755, 16756, 16757", 1988, -1}, // Yêu quái núi lãng lãng
        {188, "14528, 14529, 14530, 14531", 1760, -1}, // Đeo lưng Kẹo Noel
        {189, "16982, 16983, 16984, 16985, 16986, 16987", 2024, -1}, // Cờ đeo lưng sao may mắn
        {190, "14635, 14636, 14637, 14638", 1769, -1}, // Bóng sinh nhật NRO
        {191, "14652, 14653, 14654, 14655", 1772, -1}, // Đuôi rắn xanh
        {192, "14647, 14648, 14649, 14650", 1773, -1}, // Đuôi rắn rực lửa
        {164, null, 1841, -1}, // Bạch tuộc Xanh cáu kỉnh: dùng hình bạch tuộc xanh
        {111, null, 1932, -1}, // Cánh thiên thần 3 (Đệ): cùng hình bản thường
    };

    public static void run(Connection con) {
        int changed = 0;
        try {
            for (Object[] f : FLAGS) {
                if (f[1] != null && !exists(con, "select 1 from flag_bag where id = " + f[0])) {
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
