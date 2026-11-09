package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 109: Máy đo sức mạnh (quái 117) là loại 1 "đi lại" với vùng di chuyển 1 nên client tự cho máy đi qua đi lại 1 điểm, quay đầu
 * liên tục. Đổi thành loại 0 đứng yên như Mộc nhân (vùng di chuyển 0).
 * Thiệp chúc VIP (1718) mất hết quà khi đổi mã vật phẩm (mã cũ 1946 nay là Cải trang Thiên sư phụ): khôi phục 13 món theo danh sách
 * gốc. Thêm vào Hộp bánh Trung Thu VIP 2026 các món trước đây chưa có trong database: Cờ ngọc rồng 4 sao, Cờ ngọc rồng Super,
 * Bóng sinh nhật NRO, Hộp nâng cấp đồ thiên sứ. Hộp quà thiếu nhi / tháng 9 mã cũ chưa có quà dùng chung quà với bản mới.
 *
 * @author Việt
 */
public class Dot109Migration {

    public static void run(Connection con) {
        try {
            int n = update(con, "update mob_template set TYPE = 0, range_move = 0 where id = 117 and (TYPE <> 0 or range_move <> 0)");
            if (n > 0) {
                Logger.success("Đợt 109: Máy đo sức mạnh đứng yên (loại 0)\n");
            }
        } catch (Exception e) {
            Logger.error("Đợt 109: " + e.getMessage() + "\n");
        }
        String ins = "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate) values ";
        try {
            if (!exists(con, "select 1 from item_box_reward where box_id = 1718")) {
                int n = update(con, ins + "(1718, 1504, 1, 1, 3, '', 0), (1718, 1503, 1, 1, 3, '', 0), (1718, 1512, 1, 1, 3, '', 0),"
                        + " (1718, 1705, 1, 1, 8, '15,30,45', 10), (1718, 1416, 1, 3, 5, '', 0), (1718, 1417, 1, 3, 5, '', 0),"
                        + " (1718, 1418, 1, 3, 5, '', 0), (1718, 1419, 1, 3, 5, '', 0), (1718, 1420, 1, 3, 5, '', 0), (1718, 1421, 1, 3, 5, '', 0),"
                        + " (1718, 1422, 1, 3, 5, '', 0), (1718, 1520, 1, 1, 10, '7,15', 10), (1718, 1036, 1, 1, 15, '5,15,30,60', 0)");
                Logger.success("Đợt 109: Thiệp chúc VIP có lại " + n + " món quà\n");
            }
            // hộp cùng tên mã cũ chưa có quà: dùng chung danh sách với bản đang có quà {hộp trống, hộp mẫu}
            int[][] copy = {{1606, 1605}, {1607, 1605}, {1608, 1605}, {1695, 1902}, {1696, 1903}};
            for (int[] c : copy) {
                if (!exists(con, "select 1 from item_box_reward where box_id = " + c[0]) && exists(con, "select 1 from item_box_reward where box_id = " + c[1])) {
                    update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate) select " + c[0]
                            + ", item_id, qty_min, qty_max, weight, hsd_days, perm_rate from item_box_reward where box_id = " + c[1]);
                }
            }
            int[][] vip = {{1582, 4}, {1586, 2}, {1769, 4}, {2090, 3}}; // {vật phẩm, tỉ trọng}
            for (int[] v : vip) {
                if (!exists(con, "select 1 from item_box_reward where box_id = 2079 and item_id = " + v[0])) {
                    update(con, ins + "(2079, " + v[0] + ", 1, 1, " + v[1] + ", " + (v[0] == 2090 ? "''" : "'7,15,30'") + ", " + (v[0] == 2090 ? 0 : 5) + ")");
                }
            }
        } catch (Exception e) {
            Logger.error("Đợt 109: không bổ sung được hộp quà: " + e.getMessage() + "\n");
        }
    }
}
