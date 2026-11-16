package nro.models.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import nro.models.utils.Logger;

/**
 * Dồn số bản đồ cho liền mạch: 183-190 -> 167-174 (167-182 chưa từng có map).
 * 183 Thành cổ 1 -> 167, 184 Thành cổ 2 -> 168, 185 Đấu trường thành cổ -> 169, 186 Rừng nguyên sinh (Bản đồ truyền thuyết) -> 170,
 * 187 Ngục tù may mắn -> 171, 188 Map Mùa hè -> 172, 189 Thung lũng Maima riêng tư -> 173, 190 Rừng nguyên sinh riêng tư -> 174.
 * Chạy 1 lần, trước mọi migration khác (các migration tạo map đã đổi sang số mới).
 */
public final class MapRenumMigration {

    private MapRenumMigration() {
    }

    public static void run(Connection con) {
        try {
            if (exists(con, "select 1 from server_migration where name = 'map_renum_167'")) {
                return;
            }
            if (exists(con, "select 1 from map_template where id between 167 and 174")) {
                Logger.error("Dồn số map: 167-174 đã có map, bỏ qua\n");
                return;
            }
            update(con, "update map_template set id = id - 16 where id between 183 and 190");
            // vị trí nhân vật [mapId, x, y]
            update(con, "update player set data_location = concat('[', cast(json_extract(data_location, '$[0]') as signed) - 16, substring(data_location, locate(',', data_location))) "
                    + "where json_valid(data_location) and json_extract(data_location, '$[0]') between 183 and 190");
            for (String[] tc : new String[][]{{"season_event_npc", "map_id"}, {"season_event_mob", "map_id"}, {"task_sub_template", "map"},
                {"bot_map_learn", "map_id"}, {"bot_map_stat", "map_id"}}) {
                try {
                    update(con, "update " + tc[0] + " set " + tc[1] + " = " + tc[1] + " - 16 where " + tc[1] + " between 183 and 190");
                } catch (Exception ignored) {
                    // bảng / khoá trùng: bỏ qua bảng đó
                }
            }
            for (int old = 183; old <= 190; old++) { // danh sách map dạng "a,b,c"
                update(con, "update season_event_drop set maps = trim(both ',' from replace(concat(',', maps, ','), '," + old + ",', '," + (old - 16) + ",')) "
                        + "where concat(',', maps, ',') like '%," + old + ",%'");
            }
            update(con, "insert into server_migration (name) values ('map_renum_167')");
            Logger.success("Dồn số map 183-190 -> 167-174\n");
        } catch (Exception e) {
            Logger.error("Dồn số map: " + e.getMessage() + "\n");
        }
    }

    private static boolean exists(Connection con, String sql) throws Exception {
        try (PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            return rs.next();
        }
    }

    private static int update(Connection con, String sql) throws Exception {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            return ps.executeUpdate();
        }
    }
}
