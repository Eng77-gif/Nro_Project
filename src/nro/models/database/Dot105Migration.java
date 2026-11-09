package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 105: Phòng thí nghiệm Myuu (map 166) dùng bản của v10 (bản cũ thiếu ô nền đất của bộ ô gạch 23 nên không có chỗ đứng):
 * ô gạch data/map/tile_map_data/166 lấy từ v10, bộ ô gạch 33, nền 20. Chuỗi Bardock thêm bước hạ boss Chilled.
 *
 * @author Việt
 */
public class Dot105Migration {

    // Chỉ số tạm lấy từ v10 (cửa hàng v10, cùng mã + cùng tên vật phẩm, mã chỉ số đối chiếu theo tên): {mã vật phẩm, chỉ số, giá trị, ...}
    private static final int[][] V10 = {
        {1223, 50, 6, 77, 6, 103, 6}, {1231, 50, 7, 77, 7, 103, 7}, {1254, 50, 8, 77, 8, 103, 8}, {1301, 50, 9, 77, 9, 103, 9},
        {1344, 50, 10, 77, 10, 103, 10}, {1358, 50, 11, 77, 11, 103, 11}, {1386, 50, 13, 77, 13, 103, 13}, {1456, 50, 13, 77, 13, 103, 13},
        {1479, 50, 15, 77, 15, 103, 15}, {1519, 50, 18, 77, 18, 103, 18}, {1534, 50, 5, 77, 5, 103, 5}, {1554, 50, 7, 77, 7, 103, 7},
        {1555, 50, 8, 77, 8, 103, 8}, {1625, 50, 15, 77, 15, 103, 15}, {1676, 50, 17, 77, 17, 103, 17}, {1677, 50, 18, 77, 18, 103, 18},
        {1678, 50, 20, 77, 20, 103, 20}, {1685, 5, 30, 50, 50, 77, 50, 103, 50, 33, 1, 18, 6}};
    // Sao pha lê (loại 30): ép vào đồ lấy dòng đầu của vật phẩm, nên mỗi viên cần 1 dòng chỉ số (theo bảng của Item.getOptionDaPhaLe)
    private static final int[][] SPL = {
        {441, 95, 5}, {442, 96, 5}, {443, 97, 5}, {444, 98, 5}, {445, 99, 5}, {446, 100, 5}, {447, 101, 5},
        {1416, 95, 5}, {1417, 96, 5}, {1418, 97, 5}, {1419, 98, 5}, {1420, 99, 5}, {1421, 100, 5}, {1422, 101, 5},
        {1426, 95, 5}, {1427, 96, 5}, {1428, 97, 5}, {1429, 98, 5}, {1430, 99, 5}, {1431, 100, 5}, {1432, 101, 5},
        {1433, 153, 5}, {1434, 160, 5}};
    /** Các vật phẩm đang không có chỉ số nào (không có chỉ số mặc định, không có chỉ số ở cửa hàng) thỏa điều kiện. */
    private static java.util.List<Integer> noStats(Connection con, String where) throws Exception {
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        try (java.sql.Statement st = con.createStatement(); java.sql.ResultSet rs = st.executeQuery("select t.id from item_template t where (" + where + ")"
                + " and t.id not in (select template_id from item_template_option)"
                + " and t.id not in (select s.temp_id from item_shop s join item_shop_option o on o.item_shop_id = s.id)")) {
            while (rs.next()) {
                ids.add(rs.getInt(1));
            }
        }
        return ids;
    }

    private static int add(Connection con, int id, int opt, int min, int max) throws Exception {
        return update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) values ("
                + id + ", " + opt + ", " + min + ", " + max + ", 100, 0)");
    }

    /**
     * Chỉ số tạm cho cải trang / đeo lưng / pet / thú cưỡi, ván bay / sao pha lê đang không có chỉ số nào (chưa có chỉ số thật):
     * ưu tiên chỉ số của v10, sao pha lê theo bảng trong code, còn lại mức chung theo loại. Chỉ chạy 1 lần, không đụng món đã có chỉ số;
     * sửa lại trên panel (Vật phẩm, chỉ số mặc định) khi có chỉ số thật.
     */
    private static void tempStats(Connection con) throws Exception {
        if (exists(con, "select 1 from server_migration where name = 'dot105_tempstats'")) {
            return;
        }
        int n = 0;
        for (int[] r : V10) {
            if (!noStats(con, "t.id = " + r[0]).isEmpty()) {
                for (int i = 1; i + 1 < r.length; i += 2) {
                    n += add(con, r[0], r[i], r[i + 1], r[i + 1]);
                }
            }
        }
        for (int[] r : SPL) {
            if (!noStats(con, "t.id = " + r[0]).isEmpty()) {
                n += add(con, r[0], r[1], r[2], r[2]);
            }
        }
        if (!noStats(con, "t.id = 2074").isEmpty()) {
            n += add(con, 2074, 100, 10, 25); // Sao pha lê ánh kim: vàng rơi 10% - 25%
        }
        // mức chung (sức đánh, HP, KI): cải trang 15%, pet 10%, đeo lưng 8%, thú cưỡi / ván bay 5%
        Object[][] generic = {{"t.TYPE = 5", 15}, {"t.TYPE = 27 and t.head >= 0", 10}, {"t.TYPE = 11", 8}, {"t.TYPE in (23, 24)", 5}};
        for (Object[] g : generic) {
            int v = (Integer) g[1];
            for (int id : noStats(con, (String) g[0])) {
                n += add(con, id, 50, v, v) + add(con, id, 77, v, v) + add(con, id, 103, v, v);
            }
        }
        update(con, "insert into server_migration (name) values ('dot105_tempstats')");
        Logger.success("Đợt 105: thêm " + n + " dòng chỉ số tạm (v10 / sao pha lê / mức chung)\n");
    }

    public static void run(Connection con) {
        try {
            // số lần ước +1% chí mạng gốc ở Rồng thần siêu cấp (tối đa 5 / nhân vật)
            if (!exists(con, "select 1 from information_schema.columns where table_schema = database() and table_name = 'player_extra' and column_name = 'wish_crit'")) {
                update(con, "alter table player_extra add column wish_crit int not null default 0");
            }
        } catch (Exception e) {
            Logger.error("Đợt 105: không thêm được cột wish_crit: " + e.getMessage() + "\n");
        }
        try {
            int n = update(con, "update map_template set tile_id = 33, bg_id = 20, bg_type = 0, data = '[0,2,0,33,20]' where id = 166 and tile_id <> 33");
            if (n > 0) {
                Logger.success("Đợt 105: Phòng thí nghiệm Myuu dùng bản v10\n");
            }
        } catch (Exception e) {
            Logger.error("Đợt 105: " + e.getMessage() + "\n");
        }
        try {
            // Lọ nước phép (ghép 7 Ngọc Rồng 1 sao thành Ngọc rồng Siêu Cấp ở Bà Hạt Mít) bán cạnh Đá bảo vệ: 10 ngọc
            if (!exists(con, "select 1 from item_shop where temp_id = 1029")) {
                update(con, "insert into item_shop (tab_id, temp_id, is_new, is_sell, type_sell, cost, icon_spec) "
                        + "select tab_id, 1029, 1, 1, 1, 10, 0 from item_shop where temp_id = 987 limit 1");
            }
        } catch (Exception e) {
            Logger.error("Đợt 105: không thêm được Lọ nước phép vào cửa hàng: " + e.getMessage() + "\n");
        }
        try {
            tempStats(con);
        } catch (Exception e) {
            Logger.error("Đợt 105: không thêm được chỉ số tạm: " + e.getMessage() + "\n");
        }
        try {
            // Chuỗi Bardock (nhiệm vụ 29): thêm bước hạ Chilled ở hành tinh thực vật rồi về báo Bardock (nhận thưởng)
            if (!exists(con, "select 1 from server_migration where name = 'dot105_chilled'")) {
                update(con, "insert into task_sub_template (task_main_id, NAME, max_count, notify, npc_id, map) values "
                        + "(29, 'Hạ Chilled', 1, 'Chilled đang xâm lược hành tinh thực vật', -1, 162),"
                        + " (29, 'Báo tin cho Bardock', 1, '', 70, 160)");
                update(con, "insert into server_migration (name) values ('dot105_chilled')");
                Logger.success("Đợt 105: chuỗi Bardock thêm bước hạ Chilled\n");
            }
        } catch (Exception e) {
            Logger.error("Đợt 105: không thêm được bước hạ Chilled: " + e.getMessage() + "\n");
        }
    }
}
