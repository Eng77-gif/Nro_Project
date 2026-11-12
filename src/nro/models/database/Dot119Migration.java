package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;
import static nro.models.database.Dot118Migration.box;

/**
 * Đợt 119: sự kiện Tết viết lại theo bản v10 (mã vật phẩm theo tên).
 * <ul>
 * <li>Boss: Lân con x10, Bé Na x10; boss rơi Thịt heo / Thúng nếp / Thúng đậu xanh / Lá dong 1 - 3.</li>
 * <li>Quái rơi Thịt heo 1/200 mọi map; phó bản rơi nhiều nguyên liệu bánh.</li>
 * <li>Nồi bánh: nấu 10 Bánh tét (100 mỗi nguyên liệu, 1 Thỏi vàng, 5 triệu vàng) / 10 Bánh chưng (thêm 10 Trứng vịt muối).</li>
 * <li>Santa (v10 ở Cây nêu): trang trí cây nêu (2 Dây pháo, 4 Câu đối, 20 Dây treo bánh, 1 Đèn lồng, 5 triệu vàng) nhận quà.</li>
 * <li>Quy Lão: đổi Phiếu bé ngoan, đổi điểm sự kiện theo cửa hàng v10. Chi Chi: cửa hàng bằng Thỏi vàng.</li>
 * </ul>
 *
 * @author Việt
 */
public class Dot119Migration {

    private static final int EV = 1;

    public static void run(Connection con) {
        try {
            if (exists(con, "select 1 from server_migration where name = 'dot119_tet'")) {
                return;
            }
            update(con, "delete from season_event_drop where event_id = " + EV);
            update(con, "delete from season_event_exchange where event_id = " + EV);
            update(con, "delete from item_box_reward where box_id between 98401 and 98402");
            update(con, "update season_event set bosses = '-371:10,-373:10', description = 'Đánh quái nhặt Thịt heo, phó bản rơi nhiều nguyên liệu bánh, "
                    + "boss Lân con / Bé Na rơi nguyên liệu. Nồi bánh nấu Bánh tét / Bánh chưng, Santa trang trí cây nêu, Quy Lão đổi Phiếu bé ngoan' where id = " + EV);
            String phoBan = "53,54,55,56,57,58,59,60,61,62,141,142,143,144,147,148,149,151,152";
            update(con, "insert into season_event_drop (event_id, item_id, rate, qty_min, qty_max, source, daily_max, item_to, need_item, mobs, maps) values "
                    + "(" + EV + ", 748, 50, 1, 1, 'mob', 999, 0, 0, '', ''),"
                    + "(" + EV + ", 748, 1500, 1, 1, 'mob', 999, 0, 0, '', '" + phoBan + "'),"
                    + "(" + EV + ", 749, 5000, 1, 1, 'mob', 999, 751, 0, '', '" + phoBan + "'),"
                    + "(" + EV + ", 748, 5000, 1, 3, 'boss', 999, 751, 0, '', '')");
            // Nồi bánh (66)
            update(con, "insert into season_event_exchange (event_id, grp, name, need, gold, reward_item, reward_qty, reward_qty_max, npc) values "
                    + "(" + EV + ", 'Nấu\\nbánh', '10 Bánh tét', '748:100,749:100,750:100,751:100,457:1', 5000000, 752, 10, 10, '66'),"
                    + "(" + EV + ", 'Nấu\\nbánh', '10 Bánh chưng', '748:100,749:100,750:100,751:100,886:10,457:1', 5000000, 753, 10, 10, '66')");
            // Santa: trang trí cây nêu nhận quà (bùa, sao pha lê, ngọc, pet, cải trang Tết)
            update(con, "insert into season_event_exchange (event_id, grp, name, need, gold, reward_item, open_now) values (" + EV
                    + ", 'Cây\\nnêu', 'Trang trí cây nêu', '1472:2,1473:4,1474:20,1475:1', 5000000, 98401, 1)");
            box(con, 98401, new int[][]{{381, 6}, {382, 6}, {383, 6}, {384, 6}, {385, 6}, {441, 5}, {442, 5}, {443, 5}, {444, 5}, {445, 5}, {446, 5}, {447, 5},
                {220, 4}, {221, 4}, {222, 4}, {223, 4}, {224, 4}, {1188, 3}, {1197, 3}, {1201, 2}, {1476, 1}}, "3,7");
            // Quy Lão (13): đổi Phiếu bé ngoan (10 phiếu) lấy quà
            update(con, "insert into season_event_exchange (event_id, grp, name, need, reward_item, open_now, npc) values (" + EV
                    + ", 'Phiếu\\nbé ngoan', 'Đổi 10 Phiếu bé ngoan', '1194:10', 98402, 1, '13')");
            box(con, 98402, new int[][]{{717, 30}, {1150, 10}, {1151, 10}, {1152, 10}, {1153, 10}, {1154, 10}, {1188, 8}, {1197, 7}, {1201, 5}}, "7");
            int[][] shop = {{1840, 99, 30}, {717, 99, 0}, {1682, 450, 0}, {1777, 999, 0}, {1731, 999, 0}, {1693, 999, 0}, {1629, 999, 0}, {1757, 99, 30},
                {1928, 99, 30}, {1821, 99, 30}, {1900, 99, 30}, {1698, 499, 0}, {1713, 499, 0}, {1630, 999, 0}, {1435, 749, 0}, {1611, 599, 0}, {1786, 999, 0}};
            for (int[] s : shop) {
                update(con, "insert into season_event_exchange (event_id, grp, name, cost_point, reward_item, reward_qty, reward_days, npc) select " + EV
                        + ", 'Đổi\\nđiểm', NAME, " + s[1] + ", id, 1, '" + (s[2] > 0 ? s[2] : "") + "', '13' from item_template where id = " + s[0]);
            }
            // v10 bán đồ trang trí cây nêu qua bot bằng Thỏi vàng: đưa vào Chi Chi
            int[][] chichi = {{886, 2}, {717, 5}, {1533, 2}, {553, 3}, {543, 5}, {541, 1}, {542, 1}, {1472, 5}, {1473, 3}, {1474, 1}, {1475, 5}};
            for (int[] c : chichi) {
                update(con, "insert into season_event_exchange (event_id, grp, name, need, reward_item, reward_qty, npc) select " + EV
                        + ", 'Cửa\\nhàng', NAME, '457:" + c[1] + "', id, 1, '81' from item_template where id = " + c[0]);
            }
            update(con, "insert into server_migration (name) values ('dot119_tet')");
            Logger.success("Đợt 119: sự kiện Tết theo bản v10\n");
        } catch (Exception e) {
            Logger.error("Đợt 119: " + e.getMessage() + "\n");
        }
    }
}
