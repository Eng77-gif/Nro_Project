package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 98: dữ liệu nhiệm vụ.
 * - Nhiệm vụ 11 ghi NPC không đứng ở map đó (Lý Tiểu Nương ở Đảo Kamê cho bước đạt sức mạnh, Tiên Bry ở Vách núi cho bước về nhà):
 *   bước sức mạnh không cần NPC, bước về nhà gặp ông nội (TaskService.checkDoneTaskTalkNpc ONG_GOHAN... TASK_11_3).
 * - Nhiệm vụ 29 (trước là "..") thành chuỗi Bardock của source V10 (nhiệm vụ 31 bên đó), thêm nhiệm vụ 30 "Vui lòng chờ nhiệm vụ mới".
 *
 * @author Việt
 */
public class Dot98Migration {

    public static void run(Connection con) {
        try {
            int n = update(con, "update task_sub_template set npc_id = -1, map = -1 where task_main_id = 11 and npc_id = 54")
                    + update(con, "update task_sub_template set npc_id = -2, map = -2 where task_main_id = 11 and npc_id = 105");
            if (!exists(con, "select 1 from server_migration where name = 'dot98_bardock'")) {
                n += update(con, "update task_main_template set name = 'Chạm trán người ngoài hành tinh', detail = "
                        + "'Bảo vệ hành tinh thực vật, hạ những kẻ xâm lược.\\nThưởng 10 Tr sức mạnh\\nThưởng 10 Tr tiềm năng' where id = 29");
                if (!exists(con, "select 1 from task_main_template where id = 30")) {
                    n += update(con, "insert into task_main_template (id, name, detail) values (30, 'Vui lòng chờ nhiệm vụ mới', 'Vui lòng chờ nhiệm vụ mới')");
                }
                n += update(con, "update task_sub_template set task_main_id = 30 where task_main_id = 29"); // dòng ".." cũ thành nhiệm vụ chờ
                String v = "insert into task_sub_template (task_main_id, NAME, max_count, notify, npc_id, map) values ";
                n += update(con, v + "(29, 'Tìm nhẫn thời không từ Black Goku', 1, 'Hạ Black Goku để lấy Nhẫn thời không sai lệch', -1, -1),"
                        + " (29, 'Sử dụng nhẫn thời không', 1, '', -1, -1),"
                        + " (29, 'Tìm người xayda đang bị thương', 1, '', 70, 160),"
                        + " (29, 'Hạ 5.000 Tobi và Cabira', 5000, '', -1, 160),"
                        + " (29, 'Nói chuyện với Bardock', 1, '', 70, 160),"
                        + " (29, 'Tìm kiếm Berry đi lạc', 1, '', 71, 160),"
                        + " (29, 'Mang Berry về hang cho Bardock', 1, '', 70, 160),"
                        + " (29, 'Tìm 99 thức ăn cho Bardock tại bìa rừng', 99, '', -1, 161),"
                        + " (29, 'Hạ 10.000 Tobi và Cabira', 10000, '', -1, 160),"
                        + " (29, 'Nói chuyện với Bardock', 1, '', 70, 160)");
                n += update(con, "insert into server_migration (name) values ('dot98_bardock')");
            }
            // Vé vàng (vào khu riêng tư up bông tai) bán ở Santa, tab Hỗ trợ: 19.999 ngọc
            if (!exists(con, "select 1 from item_shop where temp_id in (1876, 2021) and tab_id = 34")) { // Đợt 102 đổi vé vàng thành phiếu sao vàng (2021)
                n += update(con, "insert into item_shop (tab_id, temp_id, is_new, is_sell, type_sell, cost, icon_spec) values (34, 1876, 1, 1, 1, 19999, 0)");
            }
            if (n > 0) {
                Logger.success("Đợt 98: nhiệm vụ 11 bỏ NPC sai map, nhiệm vụ 29 chuỗi Bardock, vé vàng ở Santa\n");
            }
        } catch (Exception e) {
            Logger.error("Đợt 98: không cập nhật được nhiệm vụ: " + e.getMessage() + "\n");
        }
    }
}
