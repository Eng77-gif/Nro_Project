package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 114:
 * - Lấp mã vật phẩm bị xoá (2124 - 2127) bằng vật phẩm giữ chỗ: server / client tra theo vị trí nên mọi món phía sau bị lệch.
 * - Con dấu VIP (2131) cho đổi sách Tuyệt Kỹ: thành công 100%, sách tối thiểu 2 dòng (10% ra 3, 5% ra 4).
 * - Dòng chỉ số 88 của cờ đổi tên như v10 "Cộng #% exp khi đánh quái" (bảng đổi cờ).
 * - Mô tả chiêu thức (skill_template.description, lấy từ v10): trước đây server gửi cứng chữ "null" nên bảng kỹ năng hiện "null".
 * - Quái Cađíc M (Sa mạc hoang vu) đi lại như v10 thay vì đứng im.
 * - Gộp chung 1 ô mọi vật phẩm trừ đồ mặc được, sách tuyệt kỹ / kỹ năng, bông tai Porata, capsule VIP.
 * - Chỉ số trong cửa hàng của Phiếu sao vàng may mắn (Không giao dịch, thời gian 1440 phút) và Năng lượng E10 (KI +10%, SĐ +5%,
 *   hiệu lực 10 phút, không giao dịch).
 *
 * @author Việt
 */
public class Dot114Migration {

    private static final String[][] SKILL_DESC = {
        {"0", "0", "Tấn công cận chiến"},
        {"0", "1", "Đánh xa nhờ năng lượng"},
        {"0", "6", "Làm chói mắt đối phương"},
        {"0", "9", "Tấn công cận chiến, mức sát thương lớn"},
        {"0", "10", "Quả cầu năng lượng có mức sát thương cực lớn"},
        {"0", "19", "Khiên năng lượng"},
        {"0", "20", "Dịch chuyển tức thời"},
        {"0", "22", "Thôi miên"},
        {"0", "24", "Đánh xa nhờ năng lượng"},
        {"0", "27", "Tiến hóa nhờ năng lượng"},
        {"0", "28", "Đánh 2 đòn liên tiếp nhờ phân tách"},
        {"1", "2", "Tấn công cận chiến"},
        {"1", "3", "Đánh xa nhờ năng lượng"},
        {"1", "7", "Phục hồi HP KI cho đồng đội"},
        {"1", "11", "Chưởng tia lazer xoắn ốc"},
        {"1", "12", "Đẻ trứng tạo quái vật"},
        {"1", "17", "Liên hoàn"},
        {"1", "18", "Biến socola"},
        {"1", "19", "Khiên năng lượng"},
        {"1", "26", "Nhốt đối thủ vào bình chứa"},
        {"1", "27", "Tiến hóa nhờ năng lượng"},
        {"1", "28", "Đánh 2 đòn liên tiếp nhờ phân tách"},
        {"2", "4", "Tấn công cận chiến"},
        {"2", "5", "Đánh xa nhờ năng lượng"},
        {"2", "8", "Tái tạo lại HP và MP đang có"},
        {"2", "13", "Biến hình thành khỉ"},
        {"2", "14", "Tự phát nổ"},
        {"2", "19", "Khiên năng lượng"},
        {"2", "21", "Huýt sáo"},
        {"2", "23", "Trói"},
        {"2", "25", "Đánh xa nhờ năng lượng"},
        {"2", "27", "Tiến hóa nhờ năng lượng"},
        {"2", "28", "Đánh 2 đòn liên tiếp nhờ phân tách"}
    };

    public static void run(Connection con) {
        try {
            // server và client tra vật phẩm theo vị trí trong danh sách: mã bị xoá (2124 - 2127) làm mọi món phía sau lệch vị trí
            // (3 cải trang đệ tử Ngũ Hành Sơn 2128 - 2130 tra ra null). Lấp chỗ trống bằng vật phẩm giữ chỗ để mã luôn trùng vị trí.
            int filled = 0;
            try (java.sql.Statement st = con.createStatement();
                    java.sql.ResultSet rs = st.executeQuery("select id from item_template order by id")) {
                java.util.List<Integer> missing = new java.util.ArrayList<>();
                int expect = 0;
                while (rs.next()) {
                    int id = rs.getInt(1);
                    for (; expect < id; expect++) {
                        missing.add(expect);
                    }
                    expect = id + 1;
                }
                for (int id : missing) {
                    filled += update(con, "insert into item_template (id, TYPE, gender, NAME, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg) "
                            + "values (" + id + ", 27, 3, 'Vật phẩm đã xoá', '', 0, 0, -1, 0, 0, 0, 0, -1, -1, -1)");
                }
            }
            if (filled > 0) {
                Logger.success("Đợt 114: lấp " + filled + " mã vật phẩm trống để mã trùng vị trí\n");
            }
            if (!exists(con, "select 1 from item_template where id = 2131")) {
                update(con, "insert into item_template (id, TYPE, gender, NAME, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg) "
                        + "select 2131, TYPE, gender, 'Con dấu VIP', 'Đổi sách Tuyệt Kỹ 100%, tối thiểu 2 dòng', level, icon_id, part, 1, "
                        + "power_require, gold, gem, head, body, leg from item_template where id = 1851");
                Logger.success("Đợt 114: thêm Con dấu VIP (2131)\n");
            }
            if (!exists(con, "select 1 from server_migration where name = 'dot114_stack'")) {
                // gộp mọi vật phẩm vào 1 ô, trừ đồ mặc được (áo, quần, găng, giày, rada / nhẫn, cải trang, đeo lưng, thú cưỡi, pet,
                // danh hiệu, giáp tập luyện, cờ), sách tuyệt kỹ / sách kỹ năng; bông tai và capsule VIP không gộp (anh dặn)
                int n = update(con, "update item_template set is_up_to_up = 1 where is_up_to_up = 0 and TYPE not in (0, 1, 2, 3, 4, 5, 11, 23, 24, 25, 28, 32, 36, 37) "
                        + "and not (TYPE = 27 and head >= 0)");
                update(con, "update item_template set is_up_to_up = 0 where id in (454, 921, 1884, 1136, 1928, 1947)");
                update(con, "insert into server_migration (name) values ('dot114_stack')");
                Logger.success("Đợt 114: " + n + " vật phẩm chuyển sang gộp chung 1 ô\n");
            }
            update(con, "update item_option_template set NAME = 'Cộng #% exp khi đánh quái' where id = 88");
            if (!exists(con, "select 1 from information_schema.columns where table_schema = database() and table_name = 'skill_template' and column_name = 'description'")) {
                update(con, "alter table skill_template add column description varchar(255) not null default ''");
            }
            for (String[] d : SKILL_DESC) {
                update(con, "update skill_template set description = '" + d[2].replace("'", "''") + "' where nclass_id = " + d[0] + " and id = " + d[1] + " and description = ''");
            }
            int[][] shopOpts = {{2021, 30, 0}, {2021, 1, 1440}, {2035, 103, 10}, {2035, 50, 5}, {2035, 9, 10}, {2035, 30, 0}};
            for (int[] o : shopOpts) {
                update(con, "insert into item_shop_option (item_shop_id, option_id, param) select s.id, " + o[1] + ", " + o[2] + " from item_shop s where s.temp_id = " + o[0]
                        + " and not exists (select 1 from item_shop_option x where x.item_shop_id = s.id and x.option_id = " + o[1] + ")");
            }
        } catch (Exception e) {
            Logger.error("Đợt 114: " + e.getMessage() + "\n");
        }
    }
}
