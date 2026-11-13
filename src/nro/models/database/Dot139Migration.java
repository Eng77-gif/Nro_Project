package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 139: Santa tab Cửa hàng (17) xếp đúng theo ảnh bản chính thức (chạy mỗi lần khởi động, ShopDAO xếp create_time giảm dần).
 * "Đổi đệ tử" chỉ hiện với người có đệ tử 1, "Đổi đệ tử 2" chỉ hiện với người có đệ tử 2 (ShopService.hiddenPetItem).
 * Tab Hỗ trợ (34) xếp ở Dot133Migration.HO_TRO.
 *
 * @author Việt
 */
public class Dot139Migration {

    /** {temp_id, giá, type_sell (1 ngọc, 0 vàng), dòng chỉ số option:param...} theo thứ tự từ trên xuống. */
    static final int[][] CUA_HANG = {
        {933, 49, 1, 31, 10}, // Mảnh vỡ bông tai, số lượng 10
        {935, 149, 1, 30, 0}, // Đá xanh lam
        {759, 500, 1}, {404, 200, 1}, {403, 150, 1}, {402, 100, 1}, // Nâng kỹ năng 4..1 đệ tử
        {401, 100, 1}, // Đổi đệ tử
        {400, 20, 1}, // Đặt tên đệ tử
        {1890, 999, 1}, // Đổi đệ tử 2
        {457, 37_000_000, 0, 86, 0}, // Thỏi vàng, ký gửi vàng
        {1532, 2, 1}, // Rađa kho báu
        {962, 150_000_000, 0}, {963, 99, 1}, // Cap thời trang 5 / 7 ngày
        {1029, 50, 1}, // Lọ nước phép
        {1113, 1999, 1, 50, 12}, // Cá Chà Bá
        {1282, 10_000_000, 0}, // Bìa sách
        {1285, 20, 1, 30, 0}, // Kìm bấm giấy
        {1284, 20, 1, 30, 0}, // Bùa giám định
        {1541, 2499, 1, 77, 7, 103, 7, 84, 0, 210, 1}, // Môtô Bun ma
        {1561, 2, 1, 30, 0}, // Chìa khóa vàng
        {1932, 5999, 1, 210, 2, 267, 0, 30, 0}, // Cánh thiên thần 3 (Đệ)
        {764, 2, 1, 19, 10}, // Khẩu trang
    };

    /** Tài khoản admin không lên bảng xếp hạng nào: xoá điểm đua top sự kiện và chỗ trong bảng Siêu hạng (các hạng sau dồn lên). */
    static void removeAdminFromRanks(Connection con) throws Exception {
        String admins = "select p.id from player p join account a on a.id = p.account_id where a.is_admin = 1";
        update(con, "delete from season_event_point where top_id > 0 and player_id in (" + admins + ")");
        java.util.List<Integer> ranks = new java.util.ArrayList<>();
        try (java.sql.PreparedStatement ps = con.prepareStatement("select rank from super_rank where rank > 0 and player_id in (" + admins + ") order by rank desc");
                java.sql.ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ranks.add(rs.getInt(1));
            }
        }
        for (int r : ranks) { // từ hạng thấp lên để dồn hạng không lệch
            update(con, "delete from super_rank where rank = " + r);
            update(con, "update super_rank set rank = rank - 1 where rank > " + r);
        }
    }

    public static void run(Connection con) {
        try {
            removeAdminFromRanks(con);
            // hình Uub (part 946-948) bị ghi đè bằng hình khác: trả lại như v10, ảnh đại diện 8617
            update(con, "update part set data = '[[8586,4,7],[8587,2,5],[2955,0,0]]' where id = 946");
            update(con, "update part set data = '[[8588,2,6],[8589,1,4],[8590,-1,2],[8591,1,2],[8592,1,1],[8593,2,2],[8594,1,2],[8595,3,4],[8596,3,7],[8597,1,8],[8598,1,3],[8599,1,3],[8600,2,6],[8601,2,3],[8602,2,5],[8603,0,2],[2955,0,0]]' where id = 947");
            update(con, "update part set data = '[[8604,9,9],[8605,1,4],[8606,0,3],[8607,1,2],[8608,0,2],[8609,2,2],[8610,0,4],[8611,3,9],[8612,0,7],[8613,1,3],[8614,1,5],[8615,0,8],[8616,0,7],[2955,0,0]]' where id = 948");
            update(con, "update head_avatar set avatar_id = 8617 where head_id = 946");
            boolean first = !exists(con, "select 1 from server_migration where name = 'dot139c_cua_hang'");
            StringBuilder keep = new StringBuilder();
            for (int i = 0; i < CUA_HANG.length; i++) {
                int[] r = CUA_HANG[i];
                keep.append(i == 0 ? "" : ",").append(r[0]);
                String where = " where tab_id = 17 and temp_id = " + r[0];
                if (!exists(con, "select 1 from item_shop" + where)) {
                    update(con, "insert into item_shop (tab_id, temp_id, is_new, is_sell, type_sell, cost) values (17, " + r[0] + ", 0, 1, " + r[2] + ", " + r[1] + ")");
                    first = true;
                }
                update(con, "update item_shop set is_sell = 1, cost = " + r[1] + ", type_sell = " + r[2]
                        + ", create_time = timestamp('2030-01-01') - interval " + i + " minute" + where);
                if (first) { // dòng chỉ số: đặt 1 lần, admin sửa sau đó thì giữ
                    update(con, "delete from item_shop_option where item_shop_id in (select id from item_shop" + where + ")");
                    for (int k = 3; k + 1 < r.length; k += 2) {
                        update(con, "insert into item_shop_option (item_shop_id, option_id, param) select id, " + r[k] + ", " + r[k + 1] + " from item_shop" + where);
                    }
                }
            }
            // món không có trong ảnh (Trang sách cũ...) thì không bán ở tab này
            update(con, "update item_shop set is_sell = 0 where tab_id = 17 and temp_id not in (" + keep + ")");
            if (first) {
                update(con, "insert ignore into server_migration (name) values ('dot139c_cua_hang')");
                Logger.success("Đợt 139: xếp lại Santa Cửa hàng theo ảnh\n");
            }
        } catch (Exception e) {
            Logger.error("Đợt 139: " + e.getMessage() + "\n");
        }
    }
}
