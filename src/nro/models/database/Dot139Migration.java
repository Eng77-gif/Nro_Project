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
            eventItemStats(con);
            // chat thế giới chỉ vẽ phần đầu: hoá Ma trơi (đầu 651) / Dơi (đầu 654) có đầu trong suốt (con vật nằm ở thân) nên ô avatar trống.
            // Thêm đầu 2391 / 2392 dùng hình con vật, ChatGlobalService đổi sang khi người chat đang hoá hình.
            update(con, "insert into part (id, TYPE, DATA) select 2391, 0, '[[6097,-7,-6],[6097,-7,-6],[6097,-7,-6]]' from dual where not exists (select 1 from part where id = 2391)");
            update(con, "insert into part (id, TYPE, DATA) select 2392, 0, '[[6098,-6,2],[6098,-6,2],[6098,-6,2]]' from dual where not exists (select 1 from part where id = 2392)");
            update(con, "insert into head_avatar (head_id, avatar_id) select 2391, 6100 from dual where not exists (select 1 from head_avatar where head_id = 2391)");
            update(con, "insert into head_avatar (head_id, avatar_id) select 2392, 6099 from dual where not exists (select 1 from head_avatar where head_id = 2392)");
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

    /**
     * Chỉ số vật phẩm sự kiện theo ảnh (đợt 16/11): ghi vào item_template_option (đồ tạo mới / quà) và item_shop_option (đồ bán ở shop),
     * thay các dòng chỉ số cũ, giữ dòng không giao dịch / hạn dùng. Món cũ trong túi người chơi đổi theo khi đăng nhập (ItemService.refreshTitle).
     * Chạy 1 lần (server_migration 'event_items_1116').
     */
    static final int NGAU_GIAM = 269; // Ngầu +#% giảm sát thương cho người xung quanh
    static final int[][][] EVENT_ITEMS = {
        {{1991}, {103, 30}, {7, 25000}}, // Thủy Thủ Mặt Trăng
        {{954}, {50, 22}, {77, 15}, {103, 15}}, // Bó Hoa Hồng
        {{1670}, {50, 12}, {103, 12}, {77, 12}, {83, 0}}, // Balo Capybara hồng
        {{1519}, {50, 19}, {77, 19}, {103, 19}, {5, 18}}, // Bong bóng thiên thần
        {{1925}, {50, 15}, {77, 22}, {103, 15}, {108, 15}, {NGAU_GIAM, 2}}, // Bó Hoa Hồng 2 Mới
        {{1914}, {50, 15}, {77, 15}, {103, 5}, {NGAU_GIAM, 2}}, // Pet Gấu Dâu
        {{1515}, {50, 18}, {77, 15}, {103, 15}, {80, 10}}, // Chậu hoa hồng
        {{1344}, {77, 18}, {103, 18}, {5, 10}, {117, 5}}}; // Con Cat hường
    /** Đợt 2 (ảnh 23 cải trang / đeo lưng / pet): dòng nâu (đi chung, hợp thể...) là mô tả vật phẩm, giữ nguyên. */
    static final int[][][] EVENT_ITEMS2 = {
        {{1745}, {50, 25}, {77, 25}, {103, 25}, {108, 6}, {191, 6}, {148, 39}, {5, 13}}, // CT Cađic SSJ3
        {{1200}, {50, 22}, {77, 30}, {80, 20}}, // Nón mèo
        {{1657}, {50, 24}, {77, 24}, {103, 22}, {204, 6}, {95, 15}, {236, 5}}, // CT Gohan đi biển
        {{1767}, {50, 9}, {103, 9}, {94, 2}}, // Ván bay Magikarp (giữ dòng bay 85)
        {{1557}, {50, 25}, {77, 24}, {103, 24}, {80, 15}, {108, 5}, {94, 15}, {148, 50}}, // CT Hắc Mị Nương
        {{1601}, {50, 23}, {77, 23}, {103, 23}, {101, 16}, {199, 5}, {204, 15}, {108, 5}, {29, 0}, {221, 2}}, // CT Mabư nhí
        {{1968}, {50, 28}, {77, 28}, {103, 28}, {268, 20}, {5, 19}}, // Cải trang Goku Tết
        {{1674}, {50, 23}, {77, 23}, {103, 23}, {14, 11}, {108, 10}, {80, 8}, {183, 11}}, // CT Pikkon
        {{1997}, {95, 15}, {268, 30}, {155, 150}}, // Cải trang Chi Chi giỗ tổ
        {{1980}, {50, 17}, {77, 19}, {103, 17}, {47, 15000}}, // Pet Kirby
        {{1778}, {117, 30}, {14, 15}, {108, 15}}, // CT Lý Tiểu Nương Rực Rỡ
        {{1998}, {103, 40}, {7, 36000}, {268, 10}}, // Cánh hào khí
        {{860}, {50, 24}, {117, 20}, {114, 25}, {77, 24}, {154, 0}}, // Cải trang Mị Nương
        {{1785}, {50, 17}, {77, 15}, {103, 15}, {14, 5}, {80, 10}, {148, 20}}, // Karin Kid Lân 2025
        {{1777}, {77, 33}, {94, 20}, {108, 15}, {80, 5}, {148, 75}}, // CT Broly SSJ God
        {{1996}, {103, 35}, {7, 17000}, {108, 21}}, // Cải trang Goku giỗ tổ
        {{1600}, {50, 22}, {77, 22}, {103, 22}, {148, 31}, {81, 4}, {200, 5}, {8, 2}}, // CT Xên nhí
        {{1693}, {50, 26}, {77, 24}, {103, 24}, {101, 30}, {106, 0}}, // CT Cađic SSJ4
        {{1949}, {50, 23}, {77, 25}, {103, 23}, {97, 20}, {270, 5}}, // Cải trang Kirin tuần lộc
        {{731}, {271, 0}, {50, 21}, {77, 18}, {103, 18}}, // Cải trang Sói Lavender
        {{732}, {272, 0}, {50, 22}, {77, 19}, {103, 19}}}; // Cải trang Sói Bergamo
    /** Đợt 3 (ảnh 14 món, Chi Chi giỗ tổ / Kirin tuần lộc đã đúng từ đợt 2). */
    static final int[][][] EVENT_ITEMS3 = {
        {{1708}, {50, 23}, {77, 28}, {103, 20}, {8, 2}, {94, 15}}, // CT Android 21 Thân Thiện
        {{1684}, {50, 25}, {101, 70}, {104, 15}}, // CT Kirin múa lân
        {{1972}, {50, 26}, {77, 26}, {103, 26}, {108, 15}, {94, 12}}, // Cải trang Chi Chi Tết
        {{1590}, {50, 27}, {77, 27}, {103, 27}, {210, 4}, {106, 0}}, // CT Goku SSJ Blue (4 dòng ẩn mở ra khi mặc)
        {{1952}, {50, 25}, {77, 23}, {103, 23}, {14, 11}}, // Cải trang Póc Noel
        {{1520}, {50, 17}, {77, 15}, {103, 15}, {101, 10}, {81, 10}}, // Bong bóng hoa hồng
        {{1358}, {77, 10}, {103, 10}, {50, 10}, {191, 3}, {126, 5}}, // Cờ hồn Mabư
        {{1889}, {50, 23}, {95, 10}, {236, 33}, {268, 10}}, // Cải trang Kirin chú cuội
        {{1803}, {50, 24}, {77, 24}, {103, 24}, {191, 5}, {14, 5}, {108, 9}, {200, 5}}, // CT Goku SSJ 4 Red
        {{1927}, {204, 30}, {95, 15}, {94, 10}}, // Cánh thiên thần sa ngã
        {{1810}, {50, 24}, {77, 24}, {148, 25}}}; // Mị Hoàng Kim
    /** Đợt 4 (ảnh 15 món). Dòng dưới vạch kẻ (phản sát thương 3%, +10% né đòn cho mọi người...) là mô tả, giữ nguyên. */
    static final int[][][] EVENT_ITEMS4 = {
        {{1919}, {50, 24}, {77, 23}, {103, 29}, {226, 21}, {14, 7}}, // Cải trang Chi Chi Áo Cưới Mới
        {{1564}, {50, 11}, {77, 12}, {103, 12}, {94, 5}}, // Pet Po
        {{1705}, {50, 24}, {77, 23}, {103, 23}, {5, 12}, {95, 10}, {226, 10}}, // Cải Trang Chi Chi Võ Đài
        {{1769}, {50, 11}, {77, 11}, {103, 11}, {14, 11}}, // Bóng sinh nhật NRO
        {{1582}, {50, 16}, {77, 16}, {103, 16}, {14, 5}, {5, 5}}, // Cờ ngọc rồng 4 sao
        {{1573}, {50, 13}, {77, 12}, {103, 12}, {14, 5}}, // Xe tăng Santa
        {{1800}, {50, 22}, {77, 18}, {103, 25}, {95, 7}, {83, 0}}, // Cải trang Chan Xư S+
        {{1937}, {50, 25}, {5, 20}, {204, 25}, {117, 20}}, // Cải trang Giáo sư Bunma
        {{1106}, {50, 21}, {77, 21}, {94, 20}, {148, 25}, {108, 15}}, // Biden dracula
        {{1926}, {50, 24}, {77, 24}, {103, 24}, {273, 30}, {43, 30}, {44, 30}}, // Cải trang Ác Ma Pico
        {{1104}, {50, 24}, {77, 22}, {94, 18}, {148, 25}, {108, 14}, {8, 4}}, // Poc satan
        {{1318}, {77, 15}, {103, 15}, {50, 15}, {8, 3}}, // Pet Mông Quỷ
        {{906}, {50, 24}, {14, 3}, {77, 19}, {103, 19}, {94, 19}, {5, 14}}, // Cải trang Mighty Mask
        {{1105}, {50, 22}, {77, 23}, {94, 16}, {148, 25}, {108, 16}}, // Bun ma phù thủy
        {{1888}, {50, 18}, {77, 16}, {103, 16}, {236, 10}, {97, 10}}}; // Lồng đèn 2 lon
    /** Đợt 5 (cải trang Goku Day / Cađic): dòng ẩn (210) mở ngẫu nhiên khi mặc; CT Goku SSJ 4 Red đổi về 4 dòng ẩn như ảnh món chưa mở. */
    static final int[][][] EVENT_ITEMS5 = {
        {{1469}, {77, 26}, {103, 26}, {50, 25}, {101, 25}}, // Avatar mũ rồng
        {{1742}, {50, 21}, {77, 21}, {103, 21}, {210, 1}}, // CT Cađic SSJ
        {{1743}, {50, 22}, {77, 22}, {103, 22}, {210, 2}}, // CT Cađic SSJ2
        {{2020}, {50, 28}, {77, 28}, {103, 28}, {274, 10}, {108, 15}, {210, 6}}, // Cải trang Goku cận vô cực
        {{1593}, {50, 25}, {77, 25}, {103, 25}, {210, 4}, {106, 0}}, // CT Goku SSJ God
        {{1587}, {50, 23}, {77, 23}, {103, 23}, {210, 3}}, // CT Goku SSJ3
        {{1595}, {50, 22}, {77, 22}, {103, 22}, {210, 2}}, // CT Goku SSJ2
        {{1589}, {50, 21}, {77, 21}, {103, 21}, {210, 1}}, // CT Goku SSJ
        {{1588}, {50, 18}, {77, 18}, {103, 18}, {210, 1}}, // CT Goku
        {{1803}, {50, 24}, {77, 24}, {103, 24}, {210, 4}}, // CT Goku SSJ 4 Red
        {{1811}, {50, 26}, {77, 23}, {103, 23}, {95, 10}, {97, 10}}}; // Vua Vegeta (giảm 50% sát thương từ quái: Mob.attackPlayer)
    /** Đợt 6 (video sinh nhật 12 năm): Máy bay NRO. */
    static final int[][][] EVENT_ITEMS6 = {
        {{1954}, {50, 7}, {77, 7}, {103, 14}, {236, 5}, {253, 12}}}; // Máy bay NRO (+12% TN, SM cho đệ tử khi sư phụ mặc)
    /** Mọi dòng chỉ số 2 đợt dùng (xoá rồi ghi lại); dòng khác như không giao dịch 30, bay 85 giữ nguyên. Dùng chung với ItemService.refreshTitle. */
    public static final java.util.Set<Integer> STAT_SET = java.util.Set.of(5, 7, 8, 14, 29, 47, 50, 77, 80, 81, 83, 94, 95, 97, 100, 101, 103, 104, 106, 108,
            114, 117, 126, 43, 44, 273, 274, 226, 253, 148, 154, 155, 183, 191, 199, 200, 204, 210, 221, 236, 268, 269, 270, 271, 272);
    private static final String STAT_IDS = STAT_SET.toString().replace("[", "").replace("]", "");

    private static void eventItemStats(Connection con) throws Exception {
        update(con, "insert into item_option_template (id, NAME) select " + NGAU_GIAM + ", 'Ngầu +#% giảm sát thương cho người xung quanh' from dual where not exists (select 1 from item_option_template where id = " + NGAU_GIAM + ")");
        update(con, "insert into item_option_template (id, NAME) select 270, 'Wow: Tăng #% Chí mạng cho người xung quanh' from dual where not exists (select 1 from item_option_template where id = 270)");
        update(con, "insert into item_option_template (id, NAME) select 271, 'Cơ hội hạ độc đối thủ' from dual where not exists (select 1 from item_option_template where id = 271)");
        update(con, "insert into item_option_template (id, NAME) select 272, 'Hấp thụ sức mạnh rồi bộc phá' from dual where not exists (select 1 from item_option_template where id = 272)");
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1116'")) {
            writeStats(con, EVENT_ITEMS);
            update(con, "insert into server_migration (name) values ('event_items_1116')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1116b'")) {
            writeStats(con, EVENT_ITEMS2);
            update(con, "insert into server_migration (name) values ('event_items_1116b')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1116c'")) {
            writeStats(con, EVENT_ITEMS3);
            update(con, "insert into server_migration (name) values ('event_items_1116c')");
        }
        // Pet Po biến người xung quanh thành bánh bao: thân 2393 = khung thân Bí Ngô (761) thay hình bằng Bánh dầy 12848
        update(con, "insert into part (id, TYPE, DATA) select 2393, 1, '[[12848,-4,1],[12848,-3,-3],[12848,-3,-6],[12848,-1,-9],[12848,-3,-12],[12848,-3,-7],[12848,-3,-6],[12848,1,-3],[12848,3,-5],[12848,-1,-1],[12848,-2,-7],[12848,-2,-7],[12848,0,-7],[12848,0,-5],[12848,-2,-4],[12848,-2,-7],[2955,0,0]]' from dual where not exists (select 1 from part where id = 2393)");
        // cải trang bán ở shop không còn hạn sử dụng (chỉ đồ mở từ hộp mới có, xem InventoryService.stripCostumeHsd)
        update(con, "delete so from item_shop_option so join item_shop s on s.id = so.item_shop_id join item_template t on t.id = s.temp_id where so.option_id = 93 and t.type = 5");
        update(con, "insert into item_option_template (id, NAME) select 273, 'Tấn công+#% lên quái bay' from dual where not exists (select 1 from item_option_template where id = 273)");
        // Áo Thần Xayda bán ở shop (item_shop 537) không có dòng chỉ số nào: hiện giáp gốc + yêu cầu sức mạnh như đồ rơi
        update(con, "insert into item_shop_option (item_shop_id, option_id, param) select s.id, 47, 900 from item_shop s where s.temp_id between 555 and 567 and s.temp_id = 559 and not exists (select 1 from item_shop_option o where o.item_shop_id = s.id)");
        update(con, "insert into item_shop_option (item_shop_id, option_id, param) select s.id, 21, 15 from item_shop s where s.temp_id = 559 and not exists (select 1 from item_shop_option o where o.item_shop_id = s.id and o.option_id = 21)");
        // Máy bay NRO (1954): hình thú cưỡi mount_84 theo bộ ảnh phi thuyền vàng NRO 12th anh gửi (6 khung, chữ gợn sóng + vệt sáng);
        // 0 = cả phi thuyền sau nhân vật, 1 = kính mờ + mép thân dưới buồng lái trước nhân vật
        for (String name : new String[]{"mount_84_0", "mount_84_1"}) {
            update(con, "insert into img_by_name (NAME, n_frame) select '" + name + "', 6 from dual where not exists (select 1 from img_by_name where NAME = '" + name + "')");
            update(con, "update img_by_name set n_frame = 6 where NAME = '" + name + "' and n_frame <> 6");
        }
        update(con, "update item_template set part = 84 where id = 1954 and part <> 84");
        // gỡ Đại bàng đất sét nổ (VIP): giữ dòng item_template (mã = vị trí), bỏ chỉ số và mọi nơi bán / phát
        update(con, "update item_template set NAME = 'Vật phẩm đã gỡ', description = 'Vật phẩm đã gỡ', TYPE = 27, part = -1 where id in (2121, 2122) and NAME <> 'Vật phẩm đã gỡ'");
        update(con, "delete from item_template_option where template_id in (2121, 2122)");
        update(con, "delete from item_shop_option where item_shop_id in (select id from item_shop where temp_id in (2121, 2122))");
        update(con, "delete from item_shop where temp_id in (2121, 2122)");
        update(con, "delete from item_box_reward where item_id in (2121, 2122)");
        update(con, "delete from shop_ky_gui where item_id in (2121, 2122)");
        update(con, "insert into item_option_template (id, NAME) select 274, '#% cơ hội ra Chống lạnh' from dual where not exists (select 1 from item_option_template where id = 274)");
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1117a'")) {
            writeStats(con, EVENT_ITEMS6);
            update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) select 1954, 30, 0, 0, 100, 0 from dual where not exists (select 1 from item_template_option where template_id = 1954 and option_id = 30)");
            update(con, "insert into server_migration (name) values ('event_items_1117a')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1116e'")) {
            writeStats(con, EVENT_ITEMS5);
            update(con, "insert into server_migration (name) values ('event_items_1116e')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1116d'")) {
            writeStats(con, EVENT_ITEMS4);
            update(con, "insert into server_migration (name) values ('event_items_1116d')");
        }
    }

    private static void writeStats(Connection con, int[][][] items) throws Exception {
        for (int[][] it : items) {
            int id = it[0][0];
            update(con, "delete from item_template_option where template_id = " + id + " and option_id in (" + STAT_IDS + ")");
            update(con, "delete from item_shop_option where option_id in (" + STAT_IDS + ") and item_shop_id in (select id from item_shop where temp_id = " + id + ")");
            for (int k = 1; k < it.length; k++) {
                update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) values (" + id + ", " + it[k][0] + ", " + it[k][1] + ", " + it[k][1] + ", 100, 0)");
                update(con, "insert into item_shop_option (item_shop_id, option_id, param) select id, " + it[k][0] + ", " + it[k][1] + " from item_shop where temp_id = " + id);
            }
        }
    }
}
