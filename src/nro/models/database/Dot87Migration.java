package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 88: Ngũ Hành Sơn theo luật mới, dùng vật phẩm có sẵn trong game:
 * - tặng Quả Hồng Đào cho Ngộ Không nhận ngẫu nhiên Chữ Giải / Khai / Phong / Ấn (537 - 540);
 * - đủ 10 chữ mỗi loại đổi Cải trang Tôn Ngộ Không (544 - 546 theo hành tinh) ở Đường Tăng;
 * - Chi Chi bán Vòng Kim Cô (543): mặc CT Tôn Ngộ Không + có Vòng Kim Cô đánh quái Ngũ Hành Sơn nhận công đức.
 * Chỉ số (khoảng ngẫu nhiên) của cải trang sự kiện ghi vào item_template_option để panel / shop hiển thị.
 * Bảng res_guard_flag: tài khoản bị chống kéo res đánh dấu (giữ qua các lần đăng nhập).
 *
 * @author Việt
 */
public class Dot87Migration {

    /** {option, min, max} - giống NguHanhSonService.createEventCt */
    private static final int[][] CT_OPTS = {{50, 15, 20}, {77, 50, 100}, {103, 50, 100}, {101, 30, 100}, {100, 70, 130}, {148, 50, 120}};
    private static final int[] CT_IDS = {544, 545, 546, 547, 548, 2128, 2129, 2130};

    public static void run(Connection con) {
        int changed = 0;
        try {
            if (!exists(con, "select 1 from information_schema.tables where table_schema = database() and table_name = 'res_guard_flag'")) {
                changed += 1 + update(con, "create table res_guard_flag (account_id int not null primary key, reason varchar(255) not null default '',"
                        + " ip varchar(64) not null default '', created_at timestamp not null default current_timestamp) engine=InnoDB default charset=utf8mb4");
            }
            if (!exists(con, "select 1 from server_migration where name = 'dot88'")) {
                int ev = 12;
                for (int id : CT_IDS) {
                    changed += update(con, "delete from item_template_option where template_id = " + id);
                    for (int[] o : CT_OPTS) {
                        changed += update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) values ("
                                + id + ", " + o[0] + ", " + o[1] + ", " + o[2] + ", 100, 0)");
                    }
                }
                changed += update(con, "update item_template set description = 'Đủ 10 chữ mỗi loại Giải Khai Phong Ấn đổi CT Tôn Ngộ Không ở Đường Tăng' where id between 537 and 540");
                changed += update(con, "update item_template set description = 'Mặc CT Tôn Ngộ Không, mang Vòng Kim Cô đánh quái Ngũ Hành Sơn nhận công đức' where id = 543");
                changed += update(con, "update season_event set description = 'Đường Tăng xuất hiện ở Làng Aru, Làng Mori, Làng Kakarot đưa người chơi vào Ngũ Hành Sơn. "
                        + "Tặng Quả Hồng Đào cho Ngộ Không nhận ngẫu nhiên Chữ Giải / Khai / Phong / Ấn (Hồng Đào Chín: 3 chữ), đủ 10 chữ mỗi loại đổi Cải trang Tôn Ngộ Không ở Đường Tăng. "
                        + "Mặc CT Tôn Ngộ Không, mua Vòng Kim Cô ở Chi Chi rồi đánh quái Ngũ Hành Sơn hoặc làm nhiệm vụ hộ tống Đường Tăng để nhận công đức đổi quà. "
                        + "Cải trang sự kiện có hạn 5 - 15 ngày và không mặc được trong Ngọc Rồng Sao Đen.' where id = " + ev);
                changed += update(con, "delete from season_event_exchange where event_id = " + ev + " and reward_item = 543");
                changed += update(con, "insert into season_event_exchange (event_id, grp, name, need, gold, gem, ruby, cost_point, reward_item, reward_qty, reward_qty_max, reward_days, perm_rate, bonus_item, bonus_rate, open_now, event_point, top_id, top_point, npc)"
                        + " values (" + ev + ", 'Cửa\\nhàng', 'Vòng Kim Cô', '', 0, 10, 0, 0, 543, 1, 1, '', 0, 0, 0, 0, 0, 0, 0, '81')");
                changed += update(con, "insert into server_migration (name) values ('dot88')");
            }
        } catch (Exception e) {
            Logger.error("Đợt 88: không cập nhật được database: " + e.getMessage() + "\n");
        }
        if (changed > 0) {
            Logger.success("Đợt 88: Ngũ Hành Sơn - chữ Giải Khai Phong Ấn, CT Tôn Ngộ Không, Vòng Kim Cô (" + changed + " dòng)\n");
        }
    }
}
