package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 127: Sự kiện hè 2025 theo 3 bài diễn đàn, chia 3 đợt (mỗi lúc chạy 1 đợt):
 * <ul>
 * <li>Hè 1 - Ẩm thực mùa hè (07/06 - 07/07, sự kiện 5): boss Mặt trời mùa hè, làm nước mía (đua top), ăn Kem trái cây (đua top, Que kem mở quà),
 * mở Hộp quà thiếu nhi 2025 (đua top), thức ăn cho thần đổi Phiếu thức ăn ở Whis (Thần hủy diệt) lấy Rada / Pet Baby Shark / Rương CT hải tặc /
 * Cờ ẩm thực / Hộp thần linh, Quy Lão đổi điểm.</li>
 * <li>Hè 2 - Vi vu bãi biển (12/07 - 11/08, sự kiện 21): câu cá (Cá nóc, Cá bảy màu -> Cá diêu hồng -> Xô cá; đua top Xô cá xanh), tìm vỏ ốc
 * (Ván bay té nước 50% vĩnh viễn, Cờ NR 7 sao 10% vĩnh viễn), đổi Trứng vàng ở Quy Lão (đua top), Quy Lão đổi điểm.</li>
 * <li>Hè 3 - Săn thú cưng (16/08 - 16/09, sự kiện 19 gộp Pokémon): dọn rác (999 Rác = Thùng rác vĩnh viễn), giải cứu Pikachu (Đội Hỏa Tiễn),
 * vỏ Xên bọ hung từ Xên con map Tương Lai / boss Xên (boss Xên rơi set kích hoạt Gohan), cửa hàng Chi Chi, đua top Quả cầu Pokemon Master.</li>
 * </ul>
 *
 * @author Việt
 */
public class Dot127Migration {

    public static final int SUMMER_1 = 5, SUMMER_2 = 21, SUMMER_3 = 19;
    public static final int TOP_MIA = 503, TOP_KEM = 504, TOP_THIEU_NHI = 505, TOP_TRUNG = 2101, TOP_XO = 2102, TOP_POKE = 1901;
    private static final String CHI_CHI = "81", QUY_LAO = "13", WHIS = "56";

    public static void run(Connection con) {
        try {
            if (exists(con, "select 1 from server_migration where name = 'dot127_he2025'")) {
                return;
            }
            summer1(con);
            summer2(con);
            summer3(con);
            update(con, "insert into server_migration (name) values ('dot127_he2025')");
            Logger.success("Đợt 127: sự kiện hè 2025 (3 đợt)\n");
        } catch (Exception e) {
            Logger.error("Đợt 127: " + e.getMessage() + "\n");
        }
    }

    // ------------------------------------------------------------------ Hè 1
    private static void summer1(Connection con) throws Exception {
        int ev = SUMMER_1;
        update(con, "update season_event set name = 'Hè 1 - Ẩm thực mùa hè', lunar = 0, month = 6, day = 7, days_before = 0, days_after = 30, "
                + "bosses = '-799:20', description = 'Boss Mặt trời mùa hè, làm nước mía, ăn kem trái cây, mở hộp quà thiếu nhi, thức ăn cho thần "
                + "đổi quà ở Whis, Quy Lão đổi điểm' where id = " + ev);
        // câu cá / vỏ ốc chuyển sang Hè 2
        update(con, "update season_event_exchange set event_id = " + SUMMER_2 + " where event_id = " + ev + " and (grp like 'Câu%' or grp like 'Vỏ%')");
        update(con, "update season_event_drop set event_id = " + SUMMER_2 + " where event_id = " + ev + " and item_id in (1002, 1003, 695, 696, 697, 698)");
        // làm nước mía: đua top
        top(con, TOP_MIA, ev, "Làm nước mía", new int[][]{{1, 1, 1809, 100}, {1, 1, 987, 60}, {1, 1, 1559, 3}, {1, 1, 2111, 1},
            {2, 2, 1809, 50}, {2, 2, 987, 40}, {2, 2, 1559, 2}, {3, 3, 1809, 30}, {3, 3, 987, 30}, {3, 3, 1559, 1},
            {4, 10, 1809, 20}, {4, 10, 987, 20}, {4, 10, 1690, 50}, {11, 50, 1809, 10}, {11, 50, 987, 10}, {11, 50, 1690, 20},
            {51, 100, 1809, 5}, {51, 100, 987, 5}, {51, 100, 1690, 10}}, null);
        update(con, "update season_event_exchange set top_id = " + TOP_MIA + ", top_point = 1 where event_id = " + ev + " and grp like 'Nước%'");
        // kem trái cây (ăn kem = 1 điểm, UseItem) và hộp quà thiếu nhi (mua = 1 điểm)
        top(con, TOP_KEM, ev, "Ăn kem trái cây", new int[][]{{1, 1, 1809, 100}, {1, 1, 987, 60}, {1, 1, 1559, 3}, {1, 1, 1771, 20}, {1, 1, 2111, 1},
            {2, 2, 1809, 50}, {2, 2, 987, 40}, {2, 2, 1559, 2}, {2, 2, 1771, 10}, {3, 3, 1809, 30}, {3, 3, 987, 30}, {3, 3, 1559, 1}, {3, 3, 1771, 5},
            {4, 10, 1809, 20}, {4, 10, 987, 20}, {4, 10, 1690, 50}, {4, 10, 1771, 2}, {11, 50, 1809, 10}, {11, 50, 987, 10}, {11, 50, 1690, 20},
            {51, 100, 1809, 5}, {51, 100, 987, 5}, {51, 100, 1690, 10}}, null);
        top(con, TOP_THIEU_NHI, ev, "Mở hộp quà thiếu nhi 2025", new int[][]{{1, 1, 1853, 1}, {1, 1, 1283, 70}, {1, 1, 1559, 3}, {1, 1, 1851, 2},
            {1, 1, 2111, 1}, {2, 2, 1853, 1}, {2, 2, 1283, 60}, {2, 2, 1559, 2}, {2, 2, 1851, 1}, {3, 3, 1853, 1}, {3, 3, 1283, 50}, {3, 3, 1559, 1},
            {4, 10, 1853, 1}, {4, 10, 1283, 20}, {4, 10, 1690, 50}, {11, 50, 1853, 1}, {11, 50, 1283, 10}, {11, 50, 1690, 20},
            {51, 100, 1853, 1}, {51, 100, 1283, 5}, {51, 100, 1690, 10}}, new int[][]{{4, 10, 180}, {11, 50, 60}, {51, 100, 30}});
        shop(con, ev, CHI_CHI, 1609, 0, 50, 0, 0);
        shop(con, ev, CHI_CHI, 1856, 0, 20, TOP_THIEU_NHI, 1);
        // Que kem: mở ra quà, có cơ hội Cải trang Bun ma đi biển
        box(con, 1610, new int[][]{{457, 1, 1, 200}, {987, 1, 2, 120}, {1690, 1, 3, 120}, {1283, 1, 2, 100}, {1771, 1, 1, 50}, {1559, 1, 1, 5}});
        update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) values (1610, 1854, 1, 1, 8, '7,15,30', 10, 0)");
        // nguyên liệu nước mía + thức ăn cho thần
        String cold = "105,106,107,108,109,110,152";
        drop(con, ev, 1613, 300, 1, 1, "mob", 99, "", cold); // Nước đá: quái map Cold
        drop(con, ev, 1613, 5000, 1, 3, "boss", 0, "-29", ""); // Nước đá: boss Cooler
        drop(con, ev, 1612, 300, 1, 1, "mob", 99, "", ""); // Khúc mía: quái thường
        drop(con, ev, 1857, 300, 1, 1, "mob", 99, "", ""); // Tayaki
        drop(con, ev, 1859, 300, 1, 1, "mob", 50, "", cold); // Kem que đôi: quái map Cold
        drop(con, ev, 1860, 5000, 1, 1, "boss", 7, "-365,-78,-79", ""); // Mochi: Ăn trộm, Ở dơ, Virút
        drop(con, ev, 1861, 5000, 1, 1, "boss", 5, "-6", ""); // Râu bạch tuộc: boss Bản đồ kho báu
        drop(con, ev, 1862, 3000, 1, 1, "boss", 10, "-925,-203,-29,-203999", ""); // Ramen: Baby, Black Goku, Cooler, Cumber
        update(con, "delete from season_event_npc where event_id = " + ev + " and npc_id = " + WHIS);
        update(con, "insert into season_event_npc (event_id, npc_id, map_id, x, y) values (" + ev + ", " + WHIS + ", 5, 0, 0), (" + ev + ", " + WHIS
                + ", 13, 0, 0), (" + ev + ", " + WHIS + ", 20, 0, 0)");
        int[][] food = {{1857, 1}, {1859, 2}, {1860, 5}, {1861, 5}, {1862, 5}, {1858, 10}};
        for (int[] f : food) {
            update(con, "insert into season_event_exchange (event_id, grp, name, need, reward_item, reward_qty, npc) select " + ev
                    + ", 'Thức\\năn', concat(NAME, ' -> ', " + f[1] + ", ' phiếu'), '" + f[0] + ":1', 2106, " + f[1] + ", '" + WHIS + "' from item_template where id = " + f[0]);
        }
        int[][] ticket = {{1808, 199, 0}, {1620, 1999, 20}, {1864, 99, 0}, {1855, 999, 20}, {2103, 9999, 0}};
        for (int[] t : ticket) {
            update(con, "insert into season_event_exchange (event_id, grp, name, need, reward_item, reward_qty, reward_days, perm_rate, npc) select " + ev
                    + ", 'Phiếu\\nthức ăn', NAME, '2106:" + t[1] + "', id, 1, '" + (t[2] > 0 ? "7,15,30" : "") + "', " + t[2] + ", '" + WHIS
                    + "' from item_template where id = " + t[0]);
        }
    }

    // ------------------------------------------------------------------ Hè 2
    private static void summer2(Connection con) throws Exception {
        int ev = SUMMER_2;
        update(con, "delete from season_event where id = " + ev);
        update(con, "insert into season_event (id, name, lunar, month, day, days_before, days_after, enabled, bosses, description) values (" + ev
                + ", 'Hè 2 - Vi vu bãi biển', 0, 7, 12, 0, 30, 1, '', 'Câu cá đổi Xô cá, tìm vỏ ốc đổi Ván bay té nước / Cờ NR 7 sao, "
                + "đổi Trứng vàng ở Quy Lão, đua top Xô cá xanh và Trứng vàng')");
        // vỏ ốc: Ván bay té nước 50% vĩnh viễn, Cờ NR 7 sao 10% vĩnh viễn
        update(con, "update season_event_exchange set reward_days = '7,15,30', perm_rate = 50 where event_id = " + ev + " and reward_item = 1563");
        update(con, "update season_event_exchange set reward_days = '7,15,30', perm_rate = 10 where event_id = " + ev + " and reward_item = 1585");
        top(con, TOP_XO, ev, "Xô cá xanh", new int[][]{{1, 1, 1882, 1}, {1, 1, 1283, 70}, {1, 1, 1559, 3}, {1, 1, 2111, 1}, {1, 1, 1204, 10},
            {2, 2, 1882, 1}, {2, 2, 1283, 60}, {2, 2, 1559, 2}, {2, 2, 1204, 5}, {3, 3, 1882, 1}, {3, 3, 1283, 50}, {3, 3, 1559, 1}, {3, 3, 1204, 2},
            {4, 10, 1882, 1}, {4, 10, 1283, 20}, {4, 10, 1690, 50}, {11, 50, 1882, 1}, {11, 50, 1283, 10}, {11, 50, 1690, 20},
            {51, 100, 1882, 1}, {51, 100, 1283, 5}, {51, 100, 1690, 10}}, null);
        update(con, "update season_event_exchange set top_id = " + TOP_XO + ", top_point = 1 where event_id = " + ev + " and reward_item = 1005");
        top(con, TOP_TRUNG, ev, "Đổi trứng vàng", new int[][]{{1, 1, 1878, 1}, {1, 1, 1283, 70}, {1, 1, 1559, 3}, {1, 1, 1851, 2}, {1, 1, 2111, 1},
            {2, 2, 1847, 1}, {2, 2, 1283, 60}, {2, 2, 1559, 2}, {2, 2, 1851, 1}, {3, 3, 1847, 1}, {3, 3, 1283, 50}, {3, 3, 1559, 1},
            {4, 10, 1847, 1}, {4, 10, 1283, 20}, {4, 10, 1690, 50}, {11, 50, 1847, 1}, {11, 50, 1283, 10}, {11, 50, 1690, 20},
            {51, 100, 1847, 1}, {51, 100, 1283, 5}, {51, 100, 1690, 10}}, new int[][]{{4, 10, 180}, {11, 50, 60}, {51, 100, 30}});
        // Quy Lão đổi điểm: như Hè 1, Trứng vàng tính top
        update(con, "delete from season_event_exchange where event_id = " + ev + " and grp like 'Đổi%điểm'");
        update(con, "insert into season_event_exchange (event_id, grp, name, need, gold, gem, ruby, cost_point, reward_item, reward_qty, reward_qty_max, "
                + "reward_days, perm_rate, bonus_item, bonus_rate, open_now, event_point, top_id, top_point, npc) select " + ev + ", grp, name, need, gold, gem, "
                + "ruby, cost_point, reward_item, reward_qty, reward_qty_max, reward_days, perm_rate, bonus_item, bonus_rate, open_now, event_point, 0, 0, npc "
                + "from season_event_exchange where event_id = " + SUMMER_1 + " and grp like 'Đổi%điểm'");
        update(con, "insert into season_event_exchange (event_id, grp, name, cost_point, reward_item, reward_qty, reward_days, top_id, top_point, npc) select "
                + ev + ", 'Đổi\\nđiểm', NAME, 99, id, 1, '30', " + TOP_TRUNG + ", 1, '" + QUY_LAO + "' from item_template where id = 1821");
    }

    // ------------------------------------------------------------------ Hè 3
    private static void summer3(Connection con) throws Exception {
        int ev = SUMMER_3;
        update(con, "update season_event set name = 'Hè 3 - Săn thú cưng', lunar = 0, month = 8, day = 16, days_before = 0, days_after = 31, "
                + "bosses = '-377:20,-376:10,-375:10', description = 'Dọn rác nhận Thùng rác, giải cứu Pikachu khỏi Đội Hỏa Tiễn, tìm vỏ Xên bọ hung, "
                + "boss Xên rơi set kích hoạt Gohan, cửa hàng Chi Chi, đua top Quả cầu Pokemon Master' where id = " + ev);
        update(con, "update season_event_top set name = 'Quả cầu Pokemon Master' where id = " + TOP_POKE);
        update(con, "delete from season_event_top_reward where top_id = " + TOP_POKE);
        top(con, TOP_POKE, ev, null, new int[][]{{1, 1, 1874, 1}, {1, 1, 1283, 70}, {1, 1, 1559, 3}, {1, 1, 1851, 2}, {1, 1, 2111, 1},
            {2, 2, 1874, 1}, {2, 2, 1283, 60}, {2, 2, 1559, 2}, {2, 2, 1851, 1}, {3, 3, 1874, 1}, {3, 3, 1283, 50}, {3, 3, 1559, 1},
            {4, 10, 1874, 1}, {4, 10, 1283, 20}, {4, 10, 1690, 50}, {11, 50, 1874, 1}, {11, 50, 1283, 10}, {11, 50, 1690, 20},
            {51, 100, 1874, 1}, {51, 100, 1283, 5}, {51, 100, 1690, 10}}, new int[][]{{4, 10, 180}, {11, 50, 60}, {51, 100, 30}});
        // dọn rác
        drop(con, ev, 1895, 300, 1, 1, "mob", 99, "", "");
        update(con, "insert into season_event_exchange (event_id, grp, name, need, reward_item, reward_qty, npc) values (" + ev
                + ", 'Dọn\\nrác', 'Thùng rác (vĩnh viễn)', '1895:999', 1891, 1, '" + CHI_CHI + "')");
        // vỏ Xên: Xên con map Tương Lai (99 / ngày) + boss Xên (200 / ngày)
        update(con, "update season_event_drop set rate = 500, daily_max = 99, mobs = '58,59,60,61,62,63,64,65' where event_id = " + ev + " and item_id = 1664 and source = 'mob'");
        drop(con, ev, 1664, 8000, 5, 10, "boss", 200, "-100,-101,-102,-103,-104,-105,-106,-107,-108", "");
        // cửa hàng Chi Chi
        update(con, "update season_event_exchange set gold = 1500000000 where event_id = " + ev + " and reward_item = 1631");
        update(con, "update season_event_exchange set gold = 0, gem = 20 where event_id = " + ev + " and reward_item in (1591, 1757) and grp like 'Cửa%'");
        shop(con, ev, CHI_CHI, 1893, 0, 99, 0, 0); // Bóng Ultra
        shop(con, ev, CHI_CHI, 1892, 20_000_000, 0, 0, 0); // Bóng Poke
        update(con, "insert into season_event_exchange (event_id, grp, name, gem, reward_item, reward_qty, reward_days, npc) select " + ev
                + ", 'Cửa\\nhàng', NAME, 2, id, 1, '2', '" + CHI_CHI + "' from item_template where id = 1666"); // Rađa dò vỏ Xên 2 ngày
        // mở bóng: pet Pokémon hạn dùng hoặc vĩnh viễn
        update(con, "delete from item_box_reward where box_id in (1892, 1893)");
        for (int pet = 1870; pet <= 1873; pet++) {
            update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) values "
                    + "(1892, " + pet + ", 1, 1, 25, '1,3,7', 2, 0), (1893, " + pet + ", 1, 1, 22, '3,7,15', 10, 0)");
        }
        update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) values (1893, 1874, 1, 1, 6, '3,7', 5, 0)");
        // quà VIP: thêm Cờ Quả Cầu Pokemon
        update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) values (1696, 1875, 1, 1, 10, '7,15,30', 5, 0)");
    }

    // ------------------------------------------------------------------ tiện ích
    /** rows: {hạng từ, hạng đến, vật phẩm, số lượng}; hsd: {hạng từ, hạng đến, ngày} cho món đầu tiên của nhóm hạng (vd cải trang 180 ngày). */
    private static void top(Connection con, int id, int ev, String name, int[][] rows, int[][] hsd) throws Exception {
        if (name != null) {
            update(con, "delete from season_event_top where id = " + id);
            update(con, "insert into season_event_top (id, event_id, name) values (" + id + ", " + ev + ", '" + name + "')");
        }
        update(con, "delete from season_event_top_reward where top_id = " + id);
        for (int[] r : rows) {
            String days = "";
            if (hsd != null && r[3] == 1) {
                for (int[] h : hsd) {
                    if (r[0] == h[0] && r[1] == h[1]) {
                        days = String.valueOf(h[2]);
                    }
                }
            }
            update(con, "insert into season_event_top_reward (top_id, rank_from, rank_to, item_id, qty, hsd_days, min_point) values (" + id + ", "
                    + r[0] + ", " + r[1] + ", " + r[2] + ", " + r[3] + ", '" + days + "', 10)");
        }
    }

    private static void shop(Connection con, int ev, String npc, int item, long gold, int gem, int topId, int topPoint) throws Exception {
        update(con, "insert into season_event_exchange (event_id, grp, name, gold, gem, reward_item, reward_qty, top_id, top_point, npc) select " + ev
                + ", 'Cửa\\nhàng', NAME, " + gold + ", " + gem + ", id, 1, " + topId + ", " + topPoint + ", '" + npc + "' from item_template where id = " + item);
    }

    private static void drop(Connection con, int ev, int item, int rate, int qMin, int qMax, String src, int daily, String mobs, String maps) throws Exception {
        update(con, "insert into season_event_drop (event_id, item_id, rate, qty_min, qty_max, source, daily_max, item_to, need_item, mobs, maps) values ("
                + ev + ", " + item + ", " + rate + ", " + qMin + ", " + qMax + ", '" + src + "', " + daily + ", 0, 0, '" + mobs + "', '" + maps + "')");
    }

    /** rows: {vật phẩm, số lượng từ, đến, trọng số} */
    private static void box(Connection con, int boxId, int[][] rows) throws Exception {
        update(con, "delete from item_box_reward where box_id = " + boxId);
        for (int[] r : rows) {
            update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) values (" + boxId + ", "
                    + r[0] + ", " + r[1] + ", " + r[2] + ", " + r[3] + ", '', 0, 0)");
        }
    }
}
