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
            update(con, "alter table web_topup add column if not exists gem_pending int not null default 0"); // ngọc nạp lúc offline, cộng khi đăng nhập
            removeAdminFromRanks(con);
            update(con, "alter table player_extra add column if not exists skh_menu tinyint not null default 0"); // Bà Hạt Mít: đã từng có Cápsule Vỡ
            // Danh hiệu theo ảnh: Đón Xuân 1978 (+50% TNSM, +25% TNSM đệ, +5% vàng rơi, SĐ +11%, chí mạng +5%, 30 ngày);
            // Tay nhanh hơn não 1673 (HP +5%, may mắn +20%, TNSM +10%, 7 ngày). Chỉ số thật: data_badges; hiện ở shop: item_shop_option
            int[][][] titles = {{{1978}, {101, 50}, {160, 25}, {268, 5}, {50, 11}, {14, 5}, {93, 30}}, {{1673}, {77, 5}, {236, 20}, {101, 10}, {93, 7}}};
            for (int[][] t : titles) {
                StringBuilder js = new StringBuilder("[");
                for (int i = 1; i < t.length; i++) {
                    js.append(i > 1 ? "," : "").append("{\"param\":").append(t[i][1]).append(",\"id\":").append(t[i][0]).append("}");
                }
                update(con, "update data_badges set Options = '" + js + "]' where idItem = " + t[0][0]);
                update(con, "delete from item_template_option where template_id = " + t[0][0]);
                update(con, "delete o from item_shop_option o join item_shop s on s.id = o.item_shop_id where s.tab_id = 44 and s.temp_id = " + t[0][0]);
                for (int i = 1; i < t.length; i++) {
                    update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) values (" + t[0][0] + ", " + t[i][0] + ", " + t[i][1] + ", " + t[i][1] + ", 100, 0)");
                    update(con, "insert into item_shop_option (item_shop_id, option_id, param) select id, " + t[i][0] + ", " + t[i][1] + " from item_shop where tab_id = 44 and temp_id = " + t[0][0]);
                }
            }
            // Vé riêng tư: thêm 2 map bản sao (Thung lũng Maima 11 -> 189, Rừng nguyên sinh 17 -> 190), không cổng / NPC; 3 map riêng tư mỗi map 50 khu
            for (int[] c : new int[][]{{173, 11}, {174, 17}}) { // trước 189 / 190 (dồn số map)
                if (!exists(con, "select 1 from map_template where id = " + c[0])) {
                    update(con, "insert into map_template (id, NAME, zones, max_player, data, type, planet_id, bg_type, tile_id, bg_id, waypoints, mobs, npcs, is_map_double) "
                            + "select " + c[0] + ", concat(NAME, ' (riêng tư)'), 50, max_player, data, type, planet_id, bg_type, tile_id, bg_id, '[]', mobs, '[]', is_map_double from map_template where id = " + c[1]);
                }
            }
            // Thành cổ: đi bằng cổng giữa 3 map; ra về / lên sàn nhờ Hùng Vương ở Đấu trường (vào cổ thành nhờ Hùng Vương ở làng)
            setWps(con, 167, "[]", wps(new Object[]{"Thành cổ 2", 1008, 480, 168, 60, 624}));
            setWps(con, 168, "[]", wps(new Object[]{"Thành cổ 1", 0, 624, 167, 948, 480}, new Object[]{"Đấu trường thành cổ", 1008, 624, 169, 60, 672}));
            setWps(con, 169, "[[52,921,672]]", wps(new Object[]{"Thành cổ 2", 0, 672, 168, 948, 624})); // Hùng Vương: lên sàn đấu / đưa về làng
            update(con, "update map_template set zones = 50, max_player = 1 where id in (164, 173, 174)"); // mỗi khu 1 người (đệ tử không tính)
            // Siêu thị (84): NPC đứng đúng toạ độ anh gửi (Bò Mộng, Bunma, Bà Hạt Mít, Uron, Appule, Dende, Cửa hàng ký gửi, King Furry, Dr. Brief)
            update(con, "update map_template set npcs = '[[17,103,336],[7,204,336],[21,298,336],[16,391,336],[9,506,336],[8,600,336],[28,696,336],[68,796,336],[10,889,336]]' where id = 84");
            update(con, "create table if not exists drop_rate (k varchar(64) primary key, rate double not null)"); // panel > Tỉ lệ rơi
            // mô tả đúng nguyên văn bản chính thức (ảnh shop Santa); tác dụng thật: Mob.skhLuckBoost / Mob.getItemMobReward / NPoint
            update(con, "update item_template set description = 'Trong vòng tối đa 30 phút +100% cơ hội rơi vật phẩm hiếm, +50% vàng từ quái' where id = 1635");
            update(con, "update item_template set description = 'Trong vòng tối đa 30 phút x2 sức mạnh và tiềm năng của đệ tử' where id = 1628");
            update(con, "update item_template set description = 'Sức mạnh từ 40 tỉ rơi ở mọi map. Gặp Whis tại hành tinh Bill' where id = 1229");
            if (!exists(con, "select 1 from server_migration where name = 'dot141_only_halloween'")) {
                // chỉ giữ sự kiện Halloween: tắt lịch tự chạy và bỏ bật tay của mọi sự kiện khác (dữ liệu giữ nguyên để mở lại sau)
                update(con, "update season_event set enabled = 0 where id <> 7");
                update(con, "delete from season_event_force where event_id <> 7");
                update(con, "insert ignore into server_migration (name) values ('dot141_only_halloween')");
            }
            if (!exists(con, "select 1 from server_migration where name = 'dot139n_halloween'")) {
                // Bí ngô 1-7 sao từ cả 3 boss Ma trơi / Dơi / Bí ma (cần Giỏ đựng ngọc bí, giỏ mất khi nhặt), 50%; Máy dò NR sự kiện x2
                update(con, "update season_event_drop set mobs = '-350,-349,-351', rate = 5000 where event_id = 7 and item_id = 702 and need_item = 1348");
                update(con, "update item_template set description = '30 phút: x2 tỉ lệ rơi Bí ngô 1-7 sao từ boss sự kiện (cộng dồn)' where id = 1115");
                update(con, "insert ignore into server_migration (name) values ('dot139n_halloween')");
            }
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

    private static void setWps(Connection con, int mapId, String npcs, String wps) throws Exception {
        try (java.sql.PreparedStatement ps = con.prepareStatement("update map_template set npcs = ?, waypoints = ? where id = ?")) {
            ps.setString(1, npcs);
            ps.setString(2, wps);
            ps.setInt(3, mapId);
            ps.executeUpdate();
        }
    }

    /** Cổng mép map: {tên, x mép (0 = trái, rộng map = phải), mặt đất y, map đến, x đến, y đến} -> chuỗi waypoints của map_template. */
    private static String wps(Object[]... w) {
        StringBuilder b = new StringBuilder("[");
        for (Object[] p : w) {
            int x = (int) p[1], y = (int) p[2], x0 = x == 0 ? 0 : x - 24;
            if (b.length() > 1) {
                b.append(',');
            }
            b.append("\"[\"").append(p[0]).append("\",").append(x0).append(',').append(y - 24).append(',').append(x0 + 24).append(',').append(y)
                    .append(",0,0,").append(p[3]).append(',').append(p[4]).append(',').append(p[5]).append("]\"");
        }
        return b.append(']').toString();
    }
}
