package nro.models.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 122:
 * - Gia hạn mầm ở Santa (tab Hỗ trợ, đứng đầu): 1717 = 15 ngày 4.999 ngọc, 1703 = 7 ngày 2.999 ngọc; mua là tự dùng (ShopService), hạn mầm
 *   lưu ở account.mam_until.
 * - Món sự kiện bán / nấu bằng Thỏi vàng đổi sang vàng: mỗi Thỏi vàng = 37 triệu vàng.
 *
 * @author Việt
 */
public class Dot122Migration {

    private static final long THOI_VANG = 37_000_000L;

    public static void run(Connection con) {
        try {
            if (!exists(con, "select 1 from information_schema.columns where table_schema = database() and table_name = 'account' and column_name = 'mam_until'")) {
                update(con, "alter table account add column mam_until bigint not null default 0");
            }
            if (exists(con, "select 1 from server_migration where name = 'dot122_mam_vang'")) {
                return;
            }
            int[][] mam = {{1717, 4999, 15, 2}, {1703, 2999, 7, 1}};
            for (int[] m : mam) {
                update(con, "delete o from item_shop_option o join item_shop s on s.id = o.item_shop_id where s.tab_id = 34 and s.temp_id = " + m[0]);
                update(con, "delete from item_shop where tab_id = 34 and temp_id = " + m[0]);
                update(con, "insert into item_shop (tab_id, temp_id, is_new, is_sell, type_sell, cost, create_time) values (34, " + m[0] + ", 1, 1, 1, " + m[1]
                        + ", timestamp('2030-01-01') + interval " + m[3] + " minute)");
                update(con, "insert into item_shop_option (item_shop_id, option_id, param) select id, 30, 0 from item_shop where tab_id = 34 and temp_id = " + m[0]);
                update(con, "insert into item_shop_option (item_shop_id, option_id, param) select id, 93, " + m[2] + " from item_shop where tab_id = 34 and temp_id = " + m[0]);
            }
            int n = 0;
            try (PreparedStatement ps = con.prepareStatement("select id, need, gold from season_event_exchange where need like '%457:%'"); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    StringBuilder rest = new StringBuilder();
                    long gold = rs.getLong("gold");
                    for (String part : rs.getString("need").split(",")) {
                        String[] kv = part.trim().split(":");
                        if (kv.length == 2 && kv[0].trim().equals("457")) {
                            gold += Long.parseLong(kv[1].trim()) * THOI_VANG;
                        } else if (!part.isBlank()) {
                            rest.append(rest.length() > 0 ? "," : "").append(part.trim());
                        }
                    }
                    try (PreparedStatement up = con.prepareStatement("update season_event_exchange set need = ?, gold = ? where id = ?")) {
                        up.setString(1, rest.toString());
                        up.setLong(2, gold);
                        up.setInt(3, rs.getInt("id"));
                        n += up.executeUpdate();
                    }
                }
            }
            update(con, "insert into server_migration (name) values ('dot122_mam_vang')");
            Logger.success("Đợt 122: Gia hạn mầm ở Santa, " + n + " món sự kiện đổi giá Thỏi vàng sang vàng\n");
        } catch (Exception e) {
            Logger.error("Đợt 122: " + e.getMessage() + "\n");
        }
    }
}
