package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 118: sự kiện Giáng sinh viết lại theo bản v10 (mã vật phẩm đổi theo tên sang server này).
 * <ul>
 * <li>Boss: Ông già Noel x30, Virut x10, Nguyệt thần x10, Tuần lộc x10. Boss rơi Người tuyết / Người tuyết băng giá 1 - 3.</li>
 * <li>Quái rơi Tất, vớ giáng sinh 1/50 (tối đa 100 / ngày), Hộp Diêm thống nhất 1/50 ở vùng Cold.</li>
 * <li>Chi Chi: quẹt Hộp Diêm (cần Que diêm) nhận quà như v10, tính top "Dùng hộp diêm"; cửa hàng bằng Thỏi vàng như v10.</li>
 * <li>Santa: giao Người tuyết / Người tuyết băng giá nhận quà (v10 giao ở Vùng đất băng giá - map server này chưa có).</li>
 * <li>Quy Lão: đổi điểm sự kiện theo cửa hàng v10.</li>
 * </ul>
 *
 * @author Việt
 */
public class Dot118Migration {

    private static final int EV = 9;

    public static void run(Connection con) {
        try {
            if (exists(con, "select 1 from server_migration where name = 'dot118_christmas'")) {
                return;
            }
            update(con, "delete from season_event_drop where event_id = " + EV);
            update(con, "delete from season_event_exchange where event_id = " + EV);
            update(con, "delete from item_box_reward where box_id between 98301 and 98303");
            update(con, "update season_event set bosses = '-353:30,-79:10,-345:10,-372:10', description = 'Đánh quái nhặt Tất vớ giáng sinh, "
                    + "vùng Cold có Hộp Diêm. Boss (Ông già Noel, Tuần lộc, Virut, Nguyệt thần) rơi Người tuyết. Chi Chi quẹt Hộp Diêm, Santa nhận Người tuyết' where id = " + EV);
            update(con, "insert into season_event_drop (event_id, item_id, rate, qty_min, qty_max, source, daily_max, item_to, need_item, mobs, maps) values "
                    + "(" + EV + ", 649, 200, 1, 1, 'mob', 100, 0, 0, '', ''),"
                    + "(" + EV + ", 1758, 200, 1, 1, 'mob', 999, 0, 0, '', '105,106,107,108,109,110'),"
                    + "(" + EV + ", 1448, 5000, 1, 3, 'boss', 999, 1449, 0, '', '')");
            int top = 901;
            update(con, "update season_event_top set name = 'Dùng hộp diêm' where id = " + top);
            // Chi Chi (81): quẹt hộp diêm như v10 (35% Que diêm bùng cháy, 15% Kiếm Kitetsu, 15% Pet Ông già Noel / Shiba,
            // 15% Rương sao pha lê VIP, 10% Nón Noel, 10% Tuần lộc Machine)
            update(con, "insert into season_event_exchange (event_id, grp, name, need, reward_item, open_now, top_id, top_point, npc) values (" + EV
                    + ", 'Hộp\\ndiêm', 'Quẹt Hộp Diêm', '1758:1,1759:1', 98301, 1, " + top + ", 1, '81')");
            box(con, 98301, new int[][]{{1766, 35}, {1456, 15}, {1452, 8}, {1458, 7}, {1453, 15}, {1451, 10}, {1466, 10}}, "");
            int[][] chichi = {{1941, 2}, {1448, 2}, {1449, 2}, {1757, 3}, {1463, 1}, {1759, 5}, {649, 1}, {386, 49}, {387, 148}, {388, 249}};
            for (int[] c : chichi) {
                update(con, "insert into season_event_exchange (event_id, grp, name, need, reward_item, reward_qty, npc) select " + EV
                        + ", 'Cửa\\nhàng', NAME, '457:" + c[1] + "', id, 1, '81' from item_template where id = " + c[0]);
            }
            // Santa: giao Người tuyết (vàng / ngọc, ngọc rồng, hộp diêm, vật phẩm cấp 2, ngọc rồng băng) / băng giá (pet, ...)
            update(con, "insert into season_event_exchange (event_id, grp, name, need, reward_item, open_now) values (" + EV
                    + ", 'Người\\ntuyết', 'Giao Người tuyết', '1448:1', 98302, 1), (" + EV + ", 'Người\\ntuyết', 'Giao Người tuyết băng giá', '1449:1', 98303, 1)");
            box(con, 98302, new int[][]{{76, 15}, {77, 15}, {17, 5}, {18, 5}, {19, 5}, {20, 5}, {1758, 5}, {1759, 5}, {1150, 5}, {1151, 5}, {1152, 5},
                {1153, 5}, {1154, 5}, {925, 2}, {926, 2}, {927, 2}, {928, 2}, {929, 2}, {930, 2}, {931, 2}}, "");
            update(con, "update item_box_reward set qty_min = 5000000, qty_max = 20000000 where box_id = 98302 and item_id = 76"); // vàng
            update(con, "update item_box_reward set qty_min = 10, qty_max = 50 where box_id = 98302 and item_id = 77"); // ngọc
            box(con, 98303, new int[][]{{1550, 7}, {1551, 7}, {1435, 6}, {1150, 16}, {1151, 16}, {1152, 16}, {1153, 16}, {1154, 16}}, "1,3,7");
            // Quy Lão (13): đổi điểm sự kiện (cửa hàng v10)
            int[][] shop = {{1757, 99, 30}, {1840, 99, 30}, {1749, 999, 0}, {1877, 999, 0}, {1676, 750, 0}, {1735, 750, 0}, {1731, 999, 0}, {1680, 999, 0},
                {1629, 999, 0}, {1630, 999, 0}, {1435, 999, 0}};
            for (int[] s : shop) {
                update(con, "insert into season_event_exchange (event_id, grp, name, cost_point, reward_item, reward_qty, reward_days, npc) select " + EV
                        + ", 'Đổi\\nđiểm', NAME, " + s[1] + ", id, 1, '" + (s[2] > 0 ? s[2] : "") + "', '13' from item_template where id = " + s[0]);
            }
            update(con, "insert into server_migration (name) values ('dot118_christmas')");
            Logger.success("Đợt 118: sự kiện Giáng sinh theo bản v10\n");
        } catch (Exception e) {
            Logger.error("Đợt 118: " + e.getMessage() + "\n");
        }
    }

    /** {itemId, trọng số}; days: các mức hạn dùng (rỗng = vĩnh viễn). */
    static void box(Connection con, int boxId, int[][] items, String days) throws Exception {
        for (int[] it : items) {
            update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) values ("
                    + boxId + ", " + it[0] + ", 1, 1, " + it[1] + ", '" + days + "', 0, 0)");
        }
    }
}
