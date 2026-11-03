package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 92: quà cho các hộp / capsule / rương trước đây bấm dùng không mở ra gì (file sql/dot92_boxes.sql, mỗi dòng
 * "BOX id|câu lệnh"; chỉ thêm khi hộp đó chưa có quà trong item_box_reward, sửa tiếp trên panel).
 *
 * @author Việt
 */
public class Dot92Migration {

    public static void run(Connection con) {
        int changed = 0;
        try {
            // dòng "Dành cho đệ tử 2" (client 2.4.x không nhận mô tả item nên phải hiện bằng dòng chỉ số)
            if (!exists(con, "select 1 from item_option_template where id = 267")) {
                changed += update(con, "insert into item_option_template (id, NAME) values (267, 'Dành cho đệ tử 2')");
            }
            for (int id : new int[]{1930, 1932, 1981}) {
                if (!exists(con, "select 1 from item_template_option where template_id = " + id + " and option_id = 267")) {
                    changed += update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) values (" + id + ", 267, 0, 0, 100, 0)");
                }
            }
        } catch (Exception e) {
            Logger.error("Đợt 92: không thêm được dòng Dành cho đệ tử 2: " + e.getMessage() + "\n");
        }
        try {
            java.io.File f = new java.io.File("sql/dot92_boxes.sql");
            if (!f.exists()) {
                return;
            }
            for (String line : java.nio.file.Files.readAllLines(f.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                if (!line.startsWith("BOX ")) {
                    continue;
                }
                int bar = line.indexOf('|');
                int box = Integer.parseInt(line.substring(4, bar).trim());
                if (!exists(con, "select 1 from item_box_reward where box_id = " + box)) {
                    changed += update(con, line.substring(bar + 1));
                }
            }
        } catch (Exception e) {
            Logger.error("Đợt 92: không thêm được quà hộp: " + e.getMessage() + "\n");
        }
        try {
            // Hộp Ngọc Rồng Bí dùng trùng icon Hộp ngọc rồng xanh: đổi sang icon hộp bí ẩn
            changed += update(con, "update item_template set icon_id = 16895 where id = 2118 and icon_id = 17442");
            // Rương Rồng Thần / VIP có thêm pet hình rồng (thường có hạn, ít khi vĩnh viễn)
            int[] pets = {1482, 1497, 1712, 1729, 1814, 1815, 1816, 1817, 1818, 1819};
            for (int box : new int[]{1900, 1901}) {
                if (!exists(con, "select 1 from item_box_reward where box_id = " + box + " and item_id = 1729")) {
                    for (int pet : pets) {
                        changed += update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) values ("
                                + box + ", " + pet + ", 1, 1, " + (box == 1900 ? 4 : 6) + ", '7,15,30', " + (box == 1900 ? 2 : 10) + ", 0)");
                    }
                }
            }
            // ... và ván bay / thú cưỡi hình rồng (Ván Rồng 7 sao hiếm hơn)
            int[] mounts = {1468, 1487, 1812, 1828, 1896, 1897, 1898, 1899, 2100};
            for (int box : new int[]{1900, 1901}) {
                if (!exists(con, "select 1 from item_box_reward where box_id = " + box + " and item_id = 1897")) {
                    for (int m : mounts) {
                        int w = m == 2100 ? 1 : (box == 1900 ? 3 : 5);
                        changed += update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) values ("
                                + box + ", " + m + ", 1, 1, " + w + ", '7,15,30', " + (box == 1900 ? 2 : 10) + ", 0)");
                    }
                }
            }
        } catch (Exception e) {
            Logger.error("Đợt 92: không cập nhật được Hộp Ngọc Rồng Bí / Rương Rồng Thần: " + e.getMessage() + "\n");
        }
        try {
            // Đổi điểm Quy Lão như bản chính thức: trang bị / pet / ván bay / cải trang đổi bằng điểm sự kiện là "Hạn sử dụng hoặc vĩnh viễn"
            // (hạn 30 ngày, 10% ra vĩnh viễn) thay vì luôn vĩnh viễn
            if (!exists(con, "select 1 from server_migration where name = 'dot92_point_hsd'")) {
                changed += update(con, "update season_event_exchange e join item_template t on t.id = e.reward_item set e.reward_days = '30', e.perm_rate = 10"
                        + " where e.cost_point > 0 and e.reward_days = '' and t.is_up_to_up = 0");
                changed += update(con, "insert into server_migration (name) values ('dot92_point_hsd')");
            }
        } catch (Exception e) {
            Logger.error("Đợt 92: không cập nhật được hạn đổi điểm: " + e.getMessage() + "\n");
        }
        if (changed > 0) {
            Logger.success("Đợt 92: thêm quà cho hộp / capsule / rương chưa có quà (" + changed + " dòng)\n");
        }
    }
}
