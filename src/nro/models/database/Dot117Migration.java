package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 117: sự kiện Nhà giáo Việt Nam 20/11 viết lại theo bản v10 (mã vật phẩm đổi sang mã của server này theo tên).
 * <ul>
 * <li>Quái rơi: Bông hoa 1/50 (tối đa 100 / ngày), Lá trà tươi 1/300 ở Đông Karin, Thung lũng Namếc, Thung lũng Nappa, Vực cấm,
 * Núi Appule; Nia tre ở phó bản (doanh trại, bản đồ kho báu, con đường rắn độc, khí gas); boss rơi Hoa cúc 1 - 3. Nhiệm vụ hằng ngày
 * thưởng Que tre 1 - 4 (TaskService). v10 không có nguồn Mảnh giấy / Keo dán / Lời chúc: cho rơi từ quái để làm được thiệp.</li>
 * <li>Bà Hạt Mít: làm Túi trà khô, Hộp trà, Hộp trà hoa cúc, Thiệp chúc thường, Thiệp chúc đặc biệt (mỗi món 100 triệu vàng).</li>
 * <li>Quy Lão: tặng Hộp trà / Hộp trà hoa cúc / 10 Hộp trà hoa cúc nhận cải trang ngẫu nhiên (hạn 7 / 10 / 15 ngày), tính điểm top
 * "Tặng hộp trà"; đổi điểm sự kiện theo cửa hàng v10.</li>
 * <li>Chi Chi: cửa hàng bằng Thỏi vàng như v10.</li>
 * </ul>
 *
 * @author Việt
 */
public class Dot117Migration {

    private static final int EV = 8;
    private static final String GOLD100M = "100000000";

    public static void run(Connection con) {
        try {
            if (exists(con, "select 1 from server_migration where name = 'dot117_teachers'")) {
                return;
            }
            update(con, "delete from season_event_drop where event_id = " + EV);
            update(con, "delete from season_event_exchange where event_id = " + EV);
            update(con, "delete from item_box_reward where box_id between 98201 and 98203");
            String phoBan = "53,54,55,56,57,58,59,60,61,62,135,136,137,138,141,142,143,144,147,148,149,151,152";
            // {item, rate (phần vạn), min, max, source, daily_max, maps}
            Object[][] drops = {
                {610, 200, 1, 1, "mob", 100, ""},
                {1364, 33, 1, 1, "mob", 999, "6,10,68,69,70"},
                {1365, 2000, 1, 1, "mob", 999, phoBan},
                {1367, 5000, 1, 3, "boss", 999, ""},
                {1373, 200, 1, 1, "mob", 200, ""},
                {1374, 20, 1, 1, "mob", 20, ""},
                {1375, 10, 1, 1, "mob", 10, ""}};
            for (Object[] d : drops) {
                update(con, "insert into season_event_drop (event_id, item_id, rate, qty_min, qty_max, source, daily_max, item_to, need_item, mobs, maps) values ("
                        + EV + ", " + d[0] + ", " + d[1] + ", " + d[2] + ", " + d[3] + ", '" + d[4] + "', " + d[5] + ", 0, 0, '', '" + d[6] + "')");
            }
            // Bà Hạt Mít (21): công thức như v10
            String g = "'Sự kiện\\n20/11'";
            ex(con, g, "Túi trà khô", "1364:99", GOLD100M, 0, 0, 1368, 0, 0, "21");
            ex(con, g, "Hộp trà", "1368:1,1366:1,1365:1", GOLD100M, 0, 0, 1369, 0, 0, "21");
            ex(con, g, "Hộp trà hoa cúc", "1369:1,1368:5,1366:1,1365:1,1367:1", GOLD100M, 0, 0, 1370, 0, 0, "21");
            ex(con, g, "Thiệp chúc thường", "1373:99,1372:1,1374:1", GOLD100M, 0, 0, 1376, 0, 0, "21");
            ex(con, g, "Thiệp chúc đặc biệt", "1373:99,1372:1,1374:1,1375:1", GOLD100M, 0, 0, 1377, 0, 0, "21");
            // Quy Lão (13): tặng hộp trà nhận cải trang ngẫu nhiên (mở ngay), +1 điểm top
            int top = 801;
            update(con, "update season_event_top set name = 'Tặng hộp trà' where id = " + top);
            String t = "'Tặng\\nquà'";
            update(con, "insert into season_event_exchange (event_id, grp, name, need, reward_item, open_now, top_id, top_point, npc) values (" + EV + ", " + t
                    + ", 'Tặng Hộp trà', '1369:1', 98201, 1, " + top + ", 1, '13')");
            update(con, "insert into season_event_exchange (event_id, grp, name, need, reward_item, open_now, top_id, top_point, npc) values (" + EV + ", " + t
                    + ", 'Tặng Hộp trà hoa cúc', '1370:1', 98202, 1, " + top + ", 1, '13')");
            update(con, "insert into season_event_exchange (event_id, grp, name, need, reward_item, open_now, top_id, top_point, npc) values (" + EV + ", " + t
                    + ", 'Tặng 10 Hộp trà hoa cúc', '1370:10', 98203, 1, " + top + ", 1, '13')");
            box(con, 98201, "7", 680, 819, 860, 914, 977);
            box(con, 98202, "10", 1041, 1042, 1208, 1209, 1210, 1235);
            box(con, 98203, "15", 1476, 1557, 1567, 1602, 1797, 1921);
            // Quy Lão: đổi điểm sự kiện (cửa hàng v10, giá điểm; hộp quà hạn 30 ngày như v10, cải trang / pet vĩnh viễn)
            int[][] shop = {{1840, 99, 30}, {1682, 450, 0}, {1735, 750, 0}, {1731, 999, 0}, {1629, 999, 0}, {1757, 99, 30}, {1928, 99, 30}, {1821, 99, 30},
                {1900, 99, 30}, {1698, 499, 0}, {1713, 499, 0}, {1630, 999, 0}, {1435, 749, 0}, {1611, 599, 0}, {1786, 999, 0}};
            for (int[] s : shop) {
                update(con, "insert into season_event_exchange (event_id, grp, name, cost_point, reward_item, reward_qty, reward_days, npc) select " + EV
                        + ", 'Đổi\\nđiểm', NAME, " + s[1] + ", id, 1, '" + (s[2] > 0 ? s[2] : "") + "', '13' from item_template where id = " + s[0]);
            }
            // Chi Chi (81): cửa hàng bằng Thỏi vàng (457) như v10
            int[][] chichi = {{1739, 10}, {1367, 1}, {1372, 1}, {1382, 2}, {1387, 1}, {710, 999}, {711, 1999}, {1710, 2999}, {1540, 999}};
            for (int[] c : chichi) {
                update(con, "insert into season_event_exchange (event_id, grp, name, need, reward_item, reward_qty, npc) select " + EV
                        + ", 'Cửa\\nhàng', NAME, '457:" + c[1] + "', id, 1, '81' from item_template where id = " + c[0]);
            }
            // giữ cách dùng Bông hoa hồng (Hoa Hồng tỉa bằng kéo) đổi Hộp quà 20-11 ở Chi Chi
            ex(con, "'Đổi\\nquà'", "Hộp quà 20-11", "1530:20", "0", 0, 0, 1739, top, 1, "81");
            update(con, "update season_event set description = 'Đánh quái nhặt Bông hoa, Lá trà tươi (Đông Karin, Thung lũng Namếc, Nappa, Vực cấm, Núi Appule), "
                    + "Nia tre (phó bản), boss rơi Hoa cúc, nhiệm vụ hằng ngày thưởng Que tre. Bà Hạt Mít làm hộp trà, thiệp; tặng Quy Lão nhận cải trang' where id = " + EV);
            update(con, "insert into server_migration (name) values ('dot117_teachers')");
            Logger.success("Đợt 117: sự kiện 20/11 theo bản v10\n");
        } catch (Exception e) {
            Logger.error("Đợt 117: " + e.getMessage() + "\n");
        }
    }

    private static void ex(Connection con, String grp, String name, String need, String gold, int gem, int point, int reward, int top, int topPoint, String npc) throws Exception {
        update(con, "insert into season_event_exchange (event_id, grp, name, need, gold, gem, cost_point, reward_item, reward_qty, top_id, top_point, npc) values ("
                + EV + ", " + grp + ", '" + name + "', '" + need + "', " + gold + ", " + gem + ", " + point + ", " + reward + ", 1, " + top + ", " + topPoint + ", '" + npc + "')");
    }

    private static void box(Connection con, int boxId, String days, int... items) throws Exception {
        for (int it : items) {
            update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) values ("
                    + boxId + ", " + it + ", 1, 1, 10, '" + days + "', 0, 0)");
        }
    }
}
