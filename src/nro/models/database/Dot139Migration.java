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
            compactTail(con);
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
            // Bí ngô (đầu 760) và bánh bao Pet Po (đầu 760 + thân 2393) cũng có đầu trong suốt: đầu 2394 hình bí ngô, 2395 hình bánh dầy
            update(con, "insert into part (id, TYPE, DATA) select 2394, 0, '[[7060,-4,-4],[7060,-4,-4],[7060,-4,-4]]' from dual where not exists (select 1 from part where id = 2394)");
            update(con, "insert into part (id, TYPE, DATA) select 2395, 0, '[[12848,-4,-2],[12848,-4,-2],[12848,-4,-2]]' from dual where not exists (select 1 from part where id = 2395)");
            update(con, "insert into head_avatar (head_id, avatar_id) select 2394, 7062 from dual where not exists (select 1 from head_avatar where head_id = 2394)");
            update(con, "insert into head_avatar (head_id, avatar_id) select 2395, 12848 from dual where not exists (select 1 from head_avatar where head_id = 2395)");
            // đầu con vật (danh sách tin nhắn vẽ ảnh đầu, cần ảnh nhỏ): Ma trơi / Dơi = ảnh avatar 6100 / 6099 thu nhỏ (17531 / 17532; số 32308 / 32309 vượt ảnh lớn nhất của game, client J2ME vẽ nhầm thành bánh dầy),
            // Bí ngô = bí 7060, bánh bao = bánh 13440; ảnh to (6100 / 6099 / 7062 / 13441) giữ làm avatar popup
            update(con, "update part set DATA = '[[17531,0,0],[17531,0,0],[17531,0,0]]' where id = 2391");
            update(con, "update part set DATA = '[[17532,0,3],[17532,0,3],[17532,0,3]]' where id = 2392");
            update(con, "update part set DATA = '[[7060,2,-1],[7060,2,-1],[7060,2,-1]]' where id = 2394");
            // bánh bao: avatar 13441 (bộ bánh bao đầu 1488) thay ảnh Bánh dầy 12848 tạm trước đây
            update(con, "update part set DATA = '[[13440,2,0],[13440,2,0],[13440,2,0]]' where id = 2395");
            update(con, "update head_avatar set avatar_id = 13441 where head_id = 2395");
            // đầu bánh bao mới cho chat thế giới (client J2ME nhớ avatar cũ 12848 của đầu 2395)
            update(con, "insert into part (id, TYPE, DATA) select 2397, 0, '[[13440,-4,-4],[13440,-4,-4],[13440,-4,-4]]' from dual where not exists (select 1 from part where id = 2397)");
            // căn giữa ô avatar như đầu thường (~17x20 đặt ở dx 4, dy 0): tâm ảnh về (12, 10)
            update(con, "update part set DATA = '[[13440,2,0],[13440,2,0],[13440,2,0]]' where id = 2397");
            update(con, "insert into head_avatar (head_id, avatar_id) select 2397, 13441 from dual where not exists (select 1 from head_avatar where head_id = 2397)");
            // Siêu Thị (84) nới thêm 5 ô về phía cổng sau (tile_map_data/84: 40 -> 45 ô) để NPC không đứng dưới cổng sang Sân sau siêu thị:
            // cổng dời ra mép mới, đi từ Sân sau về thì xuất hiện ở x 1020
            update(con, "update map_template set waypoints = replace(waypoints, '936,312,960,336', '1056,312,1080,336') where id = 84");
            update(con, "update map_template set waypoints = replace(waypoints, ',84,900,336', ',84,1020,336') where id = 104");
            // pet đi theo giờ lấy hình theo item_template: Pet Thỏ mập ghi nhầm chân = thân (1093), chân đúng là 1094
            update(con, "update item_template set leg = 1094 where id = 1040 and leg = 1093");
            // thân 2393 (hình bánh bao tạm cũ = Bánh dầy 12848) không còn dùng; đổi sang bánh bao thật 13440 để không chỗ nào vẽ ra bánh dầy nữa
            update(con, "update part set DATA = replace(DATA, '12848,', '13440,') where id = 2393");
            update(con, "delete from head_avatar where avatar_id = 12848"); // ảnh Bánh dầy không phải avatar của đầu nào
            // thân trong suốt: popup chat thế giới vẽ cả người, thân hoá hình cũng là con vật nên thành 2 con chồng nhau (dơi cõng ma trơi)
            update(con, "insert into part (id, TYPE, DATA) select 2396, 1, '[[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0]]' from dual where not exists (select 1 from part where id = 2396)");
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
    /** Đợt 7 (ảnh anh gửi): CT Yardrat 3 hành tinh, CT Gohan / Videl học đường. Thú cưỡi chim đất VIP 2058 đã đúng ảnh. */
    static final int[][][] EVENT_ITEMS7 = {
        {{592}, {47, 350}, {108, 10}, {33, 0}}, // Cải trang Yardrat: Giáp+350, 10% né đòn, dịch chuyển tức thời
        {{593}, {47, 350}, {108, 10}, {33, 0}},
        {{594}, {47, 350}, {108, 10}, {33, 0}},
        {{1933}, {77, 20}, {103, 20}, {50, 25}, {101, 50}, {10, 10}}, // CT Gohan học đường: sát thương chuẩn 10%
        {{1934}, {77, 25}, {103, 20}, {50, 25}, {101, 50}, {94, 10}}}; // CT Videl học đường: giảm 10% sát thương
    /** Đợt 9 (ảnh anh gửi): Cải trang thành Chi Chi (408, 580) SĐ / HP +20%; Cải trang thành Yarirobe (283) HP+10%. */
    static final int[][][] EVENT_ITEMS9 = {
        {{408}, {50, 20}, {77, 20}}, {{580}, {50, 20}, {77, 20}}, // 724 bán ở Santa đã đúng chỉ số: không đụng
        {{283}, {77, 10}}};
    /** Đợt 10 (ảnh anh gửi): cải trang Hải Tặc Tí Hon (One Piece) +5% sức đánh, +12% tốc độ chạy, tăng khi ở gần CT Hải Tặc khác loại. */
    static final int[][][] EVENT_ITEMS10 = {
        {{618}, {147, 5}, {148, 12}, {149, 0}},
        {{619}, {147, 5}, {148, 12}, {149, 0}},
        {{620}, {147, 5}, {148, 12}, {149, 0}},
        {{621}, {147, 5}, {148, 12}, {149, 0}},
        {{622}, {147, 5}, {148, 12}, {149, 0}},
        {{623}, {147, 5}, {148, 12}, {149, 0}},
        {{624}, {147, 5}, {148, 12}, {149, 0}},
        {{625}, {147, 5}, {148, 12}, {149, 0}},
        {{626}, {147, 5}, {148, 12}, {149, 0}},
    };
    /** Đợt 11 (ảnh anh gửi): nhóm Đôrêmon, kèm dòng set 176 (đủ 5 loại +20% SĐ +50% tốc độ chạy). Xuka 819 đã có ở đợt 8. */
    static final int[][][] EVENT_ITEMS11 = {
        {{863}, {77, 20}, {103, 20}, {176, 0}}, // Cải trang Xekô
        {{864}, {50, 20}, {77, 5}, {176, 0}}, // Cải trang Chaien
        {{806}, {94, 20}, {108, 10}, {176, 0}}, // Cải trang Đôrêmon: giáp 20% (option 94 như ngọc rồng 2 sao), 10% né đòn
        {{862}, {14, 5}, {50, 15}, {176, 0}}, // Cải trang Nobita: chí mạng 5%, sức đánh 15%
        {{1992}, {117, 10}, {80, 6}, {176, 0}}}; // Cải trang Xuka (mã 2) như Xuka 819
    /** Đợt 12: cải trang Trâu (vàng, đen, trắng, nâu, đốm): HP+31%, HP+50%/30s, sức đánh+21%. */
    static final int[][][] EVENT_ITEMS12 = {
        {{843}, {77, 31}, {80, 50}, {50, 21}},
        {{844}, {77, 31}, {80, 50}, {50, 21}},
        {{845}, {77, 31}, {80, 50}, {50, 21}},
        {{853}, {77, 31}, {80, 50}, {50, 21}},
        {{854}, {77, 31}, {80, 50}, {50, 21}},
        {{855}, {77, 31}, {80, 50}, {50, 21}},
        {{856}, {77, 31}, {80, 50}, {50, 21}},
        {{857}, {77, 31}, {80, 50}, {50, 21}},
        {{858}, {77, 31}, {80, 50}, {50, 21}},
    };
    /** Đợt 13: Cải trang Pan VIP: hút 14% HP (biến tấn công thành HP), +100% may mắn, không giao dịch. */
    static final int[][][] EVENT_ITEMS13 = {
        {{1921}, {95, 14}, {236, 100}}};
    /** Đợt 14: Cải trang Fide vàng: sức đánh 22%, KI 19%, HP 19%, giáp 19% (option 94), không giao dịch. */
    static final int[][][] EVENT_ITEMS14 = {
        {{629}, {50, 22}, {103, 19}, {77, 19}, {94, 19}}};
    /** Đợt 15 (ảnh anh gửi): CT Thỏ đại ca, CT Nàng Tiên Cá, CT bộ xương / ma trơi / dơi nhí. */
    static final int[][][] EVENT_ITEMS15 = {
        {{463}, {116, 0}, {114, 25}}, // Thỏ đại ca: kháng TDHS, +25% TĐ chạy (biến cà rốt: NPoint)
        {{681}, {50, 20}, {77, 20}, {103, 20}, {94, 20}}, // Nàng Tiên Cá: giáp 20% (option 94)
        {{642}, {8, 4}, {50, 20}, {77, 17}, {103, 17}}, // CT ma trơi: hút 4% HP, KI xung quanh mỗi 5 giây
        {{643}, {8, 4}, {50, 20}, {77, 17}, {103, 17}}, // CT dơi nhí
        {{644}, {8, 4}, {50, 20}, {77, 17}, {103, 17}}, // CT bộ xương
        {{645}, {8, 4}, {50, 20}, {77, 17}, {103, 17}},
        {{646}, {8, 4}, {50, 20}, {77, 17}, {103, 17}}};
    /** Part gói x4 (Nr_Part.sql thư viện 2085 - 2096): khỉ xanh 2398 - 2400, Broly xanh 2401 - 2403, Broly xanh áo choàng 2404 - 2406, Gohan SSJ 2407 - 2409. {id, type, data} */
    static final String[][] PARTS_X4 = {
            {"2398", "0", "[[17790,3,-11],[2955,0,0],[2955,0,0]]"}, // thư viện 2085
            {"2399", "1", "[[2955,0,0],[17791,-22,-23],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0]]"}, // thư viện 2086
            {"2400", "2", "[[2955,0,0],[17792,-20,-27],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0]]"}, // thư viện 2087
            {"2401", "0", "[[17793,0,-15],[2955,0,0],[2955,0,0]]"}, // thư viện 2088
            {"2402", "1", "[[2955,0,0],[17794,-22,-23],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0]]"}, // thư viện 2089
            {"2403", "2", "[[2955,0,0],[17795,-20,-27],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0]]"}, // thư viện 2090
            {"2404", "0", "[[17796,1,-15],[2955,0,0],[2955,0,0]]"}, // thư viện 2091
            {"2405", "1", "[[2955,0,0],[17797,-22,-23],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0]]"}, // thư viện 2092
            {"2406", "2", "[[2955,0,0],[17795,-20,-27],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0]]"}, // thư viện 2093
            {"2407", "0", "[[17807,2,-2],[17808,1,-2],[2955,0,0]]"}, // thư viện 2094
            {"2408", "1", "[[17809,-7,2],[17810,-2,-1],[17811,-8,-1],[17812,-2,-1],[17813,-8,0],[17814,-5,0],[17815,-4,-2],[17816,-4,0],[17817,0,0],[17818,-3,0],[17819,-6,0],[17820,0,0],[17821,-2,-1],[17822,0,0],[17823,0,0],[17824,-3,0],[2955,0,0]]"}, // thư viện 2095
            {"2409", "2", "[[17825,4,8],[17826,0,1],[17827,0,1],[17828,0,0],[17829,0,0],[17830,0,0],[17831,0,0],[17832,-1,4],[17833,-1,2],[17834,0,0],[17835,0,0],[17836,0,0],[17837,0,0],[2955,0,0]]"}, // thư viện 2096
    };
    /** Đợt 24 (ảnh): Ván Rồng 7 sao, CT Fide nhí, Pet Capybara xì mũi, CT Broly SSJ4 / SSJ God, Ván bay Magikarp, CT Cađíc / Pôcôlô Dragon, Avatar mũ rồng,
     * Cờ dây pháo, CT Thần tài, Cờ bao lì xì, Thỏi vàng bay, Cửu Hoàn Thiên Tích Trượng, Như Ý Kim Cô Bổng, CT / Nón Hổ vàng, Pet Ngựa Thần Tài, Pet ngựa chiến, Đeo lưng Bao lì xì. */
    /** Đợt 26 (ảnh): CT Thầy giáo Goku, Chi Chi Võ Đài, Zenitsu, Máy Bay Xám, Cờ hồn Xên Bọ Hung, Phong Xích Lan, Bí Ngô Cánh Dơi, Đuôi rắn rực lửa, Đeo lưng Kẹo Noel, Kiếm Kitetsu, Avatar Cử nhân. */
    /** Chỉ số theo ảnh 24/11: Xe heo tuần lộc, Pet Mèo Phù Thủy, Quạt ba tiêu, CT Thầy giáo Quy Lão / Cađíc, CT Gohan áo xanh / Búp bê Arale / Póc áo đỏ,
     *  Pet Rồng Xanh / Rồng xương, Thú cưỡi con Cat hường, Quả bóng siêu việt, Bồ cào 9 răng, Cải trang Gohan. */
    static final int[][][] STATS_ANH_1124 = {
        {{1172}, {84, 0}, {148, 25}, {50, 5}, {30, 0}},
        {{1347}, {77, 15}, {103, 15}, {50, 15}},
        {{1023}, {50, 11}, {77, 10}, {103, 10}, {14, 10}},
        {{1385}, {50, 24}, {77, 24}, {103, 24}, {108, 10}, {80, 10}, {5, 10}, {101, 50}},
        {{912}, {50, 23}, {77, 20}, {103, 20}, {276, 17}, {184, 2}, {106, 0}, {154, 0}},
        {{914}, {50, 23}, {77, 20}, {103, 20}, {276, 19}, {184, 2}, {106, 0}, {154, 0}},
        {{913}, {50, 22}, {77, 20}, {103, 20}, {276, 18}, {184, 2}, {106, 0}, {154, 0}},
        {{1729}, {50, 14}, {77, 14}, {103, 14}, {108, 10}, {160, 15}, {30, 0}},
        {{1712}, {50, 17}, {103, 17}, {77, 15}, {8, 5}, {94, 7}, {30, 0}},
        {{1363}, {50, 7}, {77, 7}, {103, 7}, {148, 15}},
        {{1131}, {84, 0}, {148, 25}, {77, 5}},
        {{1699}, {50, 13}, {77, 15}, {103, 13}, {80, 3}, {14, 5}, {30, 0}},
        {{1378}, {50, 20}, {77, 23}, {103, 23}, {94, 5}, {223, 30}},
        {{876}, {50, 22}, {178, 3}, {116, 0}, {154, 0}}};
    static final int[][][] EVENT_ITEMS26 = {
        {{1384}, {50, 23}, {77, 23}, {103, 23}, {94, 7}, {108, 10}, {24, 0}},
        {{1705}, {50, 24}, {77, 23}, {103, 23}, {5, 12}, {95, 10}, {226, 10}, {30, 0}},
        {{1090}, {50, 22}, {77, 21}, {103, 21}, {94, 18}, {108, 13}, {148, 20}, {205, 2}, {154, 0}},
        {{1833}, {50, 9}, {77, 10}, {103, 12}, {94, 4}, {108, 5}, {85, 0}, {30, 0}},
        {{1359}, {77, 10}, {103, 10}, {50, 10}, {191, 3}, {8, 2}, {30, 0}},
        {{1734}, {50, 9}, {77, 10}, {103, 9}, {5, 9}, {160, 30}, {104, 5}, {30, 0}},
        {{1704}, {50, 9}, {77, 12}, {103, 10}, {8, 5}, {5, 10}, {85, 0}},
        {{1773}, {50, 20}, {77, 20}, {103, 20}, {94, 12}, {108, 7}, {30, 0}},
        {{1760}, {50, 16}, {77, 15}, {103, 15}, {204, 20}, {30, 0}},
        {{1456}, {50, 18}, {77, 15}, {103, 15}, {5, 12}, {30, 0}},
        {{1391}, {50, 24}, {77, 23}, {103, 23}, {101, 25}},
    };

    /** Đợt 25 (ảnh): Nón Hổ trắng, Nón Chuột / Nón Trâu may mắn, Cây trúc, CT Hit, Bút chì hồng, CT Gohan SSJ (đệ tử 2). */
    static final int[][][] EVENT_ITEMS25 = {
        {{946}, {50, 21}, {103, 16}, {80, 51}, {154, 0}},
        {{754}, {77, 30}, {80, 50}, {50, 20}},
        {{755}, {77, 30}, {80, 50}, {50, 20}},
        {{756}, {77, 30}, {80, 50}, {50, 20}},
        {{846}, {77, 30}, {80, 50}, {50, 20}},
        {{847}, {77, 30}, {80, 50}, {50, 20}},
        {{848}, {77, 30}, {80, 50}, {50, 20}},
        {{852}, {50, 10}, {77, 10}, {94, 10}, {103, 10}},
        {{884}, {50, 24}, {14, 2}, {103, 19}, {5, 15}, {154, 0}},
        {{1386}, {50, 20}, {77, 17}, {103, 17}, {183, 5}},
        {{2125}, {50, 20, 25}, {0, 5000, 10000}, {267, 0}, {30, 0}},
    };

    static final int[][][] EVENT_ITEMS24 = {
        {{2100}, {50, 12}, {19, 7}, {204, 7}, {5, 7}, {14, 7}, {95, 7}, {268, 7}},
        {{1599}, {50, 21}, {77, 21}, {103, 21}, {106, 0}, {210, 2}},
        {{1630}, {50, 20}, {14, 7}, {5, 10}, {30, 0}},
        {{1775}, {50, 29}, {77, 29}, {103, 29}, {204, 20}, {5, 20}, {10, 5}, {106, 0}, {30, 0}},
        {{1777}, {77, 33}, {94, 20}, {108, 15}, {80, 5}, {148, 75}, {30, 0}},
        {{1767}, {50, 9}, {103, 9}, {94, 2}, {30, 0}},
        {{1500}, {50, 24}, {77, 24}, {103, 24}, {94, 10}, {191, 15}, {80, 10}, {101, 10}, {154, 0}},
        {{1499}, {50, 24}, {77, 24}, {103, 24}, {108, 14}, {81, 15}, {97, 20}, {101, 10}, {223, 30}, {154, 0}},
        {{1469}, {77, 26}, {103, 26}, {50, 25}, {101, 25}},
        {{1470}, {77, 26}, {103, 26}, {50, 25}, {101, 25}},
        {{1471}, {77, 26}, {103, 26}, {50, 25}, {101, 25}},
        {{1479}, {50, 15}, {77, 15}, {103, 15}, {5, 10}},
        {{1484}, {77, 23}, {103, 23}, {50, 22}, {81, 10}, {14, 7}, {101, 25}, {104, 20}, {154, 0}},
        {{1485}, {77, 23}, {103, 23}, {50, 22}, {81, 10}, {14, 7}, {101, 25}, {104, 20}, {154, 0}},
        {{1486}, {77, 23}, {103, 23}, {50, 22}, {81, 10}, {14, 7}, {101, 25}, {104, 20}, {154, 0}},
        {{1478}, {50, 16}, {77, 15}, {103, 15}, {94, 7}},
        {{1477}, {50, 5}, {77, 7}, {103, 5}, {84, 0}},
        {{1027}, {50, 18}, {77, 15}, {103, 15}, {80, 20}},
        {{1024}, {50, 18}, {77, 15}, {103, 15}, {14, 15}},
        {{948}, {77, 22}, {103, 22}, {50, 23}, {94, 10}, {80, 11}, {154, 0}},
        {{941}, {50, 21}, {77, 16}, {80, 50}, {154, 0}},
        {{1964}, {50, 20}, {77, 20}, {10, 20}, {0, 5000}, {204, 20}, {30, 0}},
        {{1962}, {50, 24}, {14, 10}, {30, 0}},
        {{1979}, {77, 21}, {6, 25000}, {108, 9}, {94, 13}, {30, 0}}};
    /** Đợt 23 (ảnh shop 20/11 Quy Lão): CT Chi Chi Hoa Hồng Xanh, Đeo lưng lồng đèn Tết, Bóng mờ NRO, Hiệu ứng hoa Tết, Ba lô ngọc rồng, Bảng đen, CT Quy lão Kamê 710, CT Jacky Chun 711. */
    static final int[][][] EVENT_ITEMS23 = {
        {{1795}, {50, 26}, {77, 24}, {275, 16}, {236, 22}, {30, 0}},
        {{1963}, {77, 10}, {6, 7000}, {267, 0}, {30, 0}},
        {{1971}, {50, 5}, {30, 0}},
        {{1976}, {77, 5}, {6, 7500}, {30, 0}},
        {{1941}, {50, 17}, {77, 20}, {5, 14}, {204, 10}, {30, 0}},
        {{1943}, {50, 8}, {77, 10}, {103, 8}, {14, 9}, {10, 5}, {30, 0}},
        {{710}, {50, 22}, {77, 20}, {103, 20}, {159, 3}, {253, 35}, {154, 0}},
        {{711}, {50, 23}, {77, 21}, {103, 21}, {159, 4}, {253, 50}, {154, 0}}};
    /** Đợt 22 (ảnh): CT Siêu Thần Trái Đất 905 / Namếc 907 giống Siêu Thần Xayda 911; CT Goku GT 1957, Máy bay 41 1798, Phượng hoàng lửa 1144, Gậy phép Frieren 1571, CT Himmel 1632, Pet Bư Noel 1953. */
    static final int[][][] EVENT_ITEMS22 = {
        {{905}, {50, 22}, {47, 450}, {108, 30}, {183, 10}, {154, 0}},
        {{907}, {50, 22}, {47, 450}, {108, 30}, {183, 10}, {154, 0}},
        {{1957}, {77, 29}, {6, 15000}, {47, 15000}, {94, 15}, {30, 0}},
        {{1798}, {50, 10}, {77, 8}, {103, 8}, {253, 25}, {30, 0}},
        {{1144}, {84, 0}, {148, 25}, {50, 5}},
        {{1571}, {50, 14}, {77, 12}, {103, 12}, {30, 0}},
        {{1632}, {50, 25}, {103, 25}, {77, 25}, {14, 10}, {97, 10}, {236, 15}, {5, 15}, {30, 0}},
        {{1953}, {50, 11}, {77, 12}, {103, 20}, {30, 0}}};
    /** Đợt 21 (ảnh): CT Siêu Thần 591 (Siêu Thần Xayda tóc xanh): giáp +450, 30% né đòn, dịch chuyển tức thời. */
    static final int[][][] EVENT_ITEMS21 = {{{591}, {47, 450}, {108, 30}, {33, 0}}};
    /** Đợt 20: CT Píc 633 / King kong 634 giống CT Póc 583. */
    static final int[][][] EVENT_ITEMS20 = {
        {{633}, {50, 8}, {77, 8}, {94, 8}, {150, 0}, {154, 0}},
        {{634}, {50, 8}, {77, 8}, {94, 8}, {150, 0}, {154, 0}}};
    /** Đợt 19 (ảnh): CT Siêu Thần Xayda 911: SĐ 22%, giáp +450, 30% né đòn, giảm 10% thời gian hồi Khiên, không bán lại. */
    static final int[][][] EVENT_ITEMS19 = {{{911}, {50, 22}, {47, 450}, {108, 30}, {183, 10}, {154, 0}}};
    /** Đợt 18 (ảnh): CT Póc Android sát thủ 583, Bum ma 581, Lý Tiểu Nương 582, Urôn 719, Sói Basil 730, Xe tuần lộc 746, Bông tuyết 745. */
    static final int[][][] EVENT_ITEMS18 = {
        {{583}, {50, 8}, {77, 8}, {94, 8}, {150, 0}, {154, 0}},
        {{581}, {50, 21}, {77, 21}, {30, 0}},
        {{582}, {50, 22}, {77, 22}, {30, 0}},
        {{719}, {50, 18}, {77, 18}, {103, 18}, {100, 20}, {161, 2}},
        {{730}, {165, 10}, {50, 20}, {77, 17}, {103, 17}, {154, 0}},
        {{746}, {84, 0}, {14, 1}, {148, 25}},
        {{745}, {106, 0}}};
    /** Đợt 17: nhóm Piláp (Pi láp 635, Mai 636, Su 637): SĐ 10%, +33% TĐ chạy, TN,SM ngẫu nhiên 5 - 15% (param_max), ở gần 1 / 2 CT nhóm Piláp khác loại (151 / 152). */
    static final int[][][] EVENT_ITEMS17 = {
        {{635}, {50, 10}, {114, 33}, {101, 5, 15}, {151, 0}, {152, 0}},
        {{636}, {50, 10}, {114, 33}, {101, 5, 15}, {151, 0}, {152, 0}},
        {{637}, {50, 10}, {114, 33}, {101, 5, 15}, {151, 0}, {152, 0}}};
    /** Đợt 16: Cải trang Sơn Tinh 421 / Thủy Tinh 422: SĐ 12%, giáp 12% (option 94), HP / KI 22%, tăng 20% TNSM khi đánh quái (83). */
    static final int[][][] EVENT_ITEMS16 = {
        {{421}, {50, 12}, {94, 12}, {77, 22}, {103, 22}, {83, 20}},
        {{422}, {50, 12}, {94, 12}, {77, 22}, {103, 22}, {83, 20}}};
    /** Đợt 8 (ảnh anh gửi 19/11): cải trang nhóm Bunma / Thỏ / Poc, cải trang Android 21 / Sarah / Muscular Lady / Caulifla, Xuka, Nón Noel. */
    static final int[][][] EVENT_ITEMS8 = {
        {{676}, {50, 10}, {77, 10}, {103, 10}, {94, 10}}, // Cải trang Sarah
        {{678}, {50, 14}, {77, 14}, {103, 14}, {94, 14}}, // Cải trang Android 21
        {{679}, {50, 16}, {77, 16}, {103, 16}, {94, 16}}, // Cải trang Muscular Lady
        {{680}, {50, 18}, {77, 18}, {103, 18}, {94, 18}}, // Cải trang Caulifla
        {{819}, {117, 10}, {80, 6}, {176, 0}}, // Cải trang Xuka
        {{977}, {50, 22}, {103, 22}, {77, 22}, {94, 17}, {148, 20}, {108, 15}, {193, 2}, {154, 0}}, // Số 3 Poc
        {{1041}, {50, 25}, {77, 23}, {103, 22}, {94, 18}, {148, 20}, {108, 12}, {196, 2}, {154, 0}}, // Thỏ hồng Bun ma
        {{1042}, {50, 25}, {77, 23}, {103, 22}, {94, 18}, {148, 20}, {108, 12}, {196, 2}, {154, 0}}, // Thỏ đỏ Chi Chi
        {{1209}, {50, 22}, {77, 23}, {103, 23}, {94, 10}, {108, 5}, {148, 20}, {154, 0}}, // Bunma tóc nâu băng đô
        {{1210}, {50, 22}, {77, 23}, {103, 23}, {94, 10}, {97, 5}, {148, 20}, {154, 0}}, // Bunma tóc tím thắt bím
        {{1235}, {50, 24}, {77, 24}, {103, 24}, {14, 10}, {108, 15}, {114, 20}, {265, 5}}, // CT Poc Bikini (+ giảm 5% SĐ người xung quanh)
        {{1476}, {50, 24}, {77, 24}, {103, 24}, {94, 10}, {14, 5}, {148, 50}, {154, 0}}, // CT Bunma rực rỡ
        {{1797}, {50, 24}, {77, 23}, {108, 23}, {5, 12}, {95, 10}, {253, 30}}, // CT Bunma Rider
        {{386}, {77, 10}, {106, 0}, {50, 10}},
        {{389}, {77, 10}, {106, 0}, {50, 10}},
        {{392}, {77, 10}, {106, 0}, {50, 10}},
        {{387}, {77, 25}, {106, 0}, {80, 24}, {50, 17}, {103, 15}},
        {{390}, {77, 25}, {106, 0}, {80, 24}, {50, 17}, {103, 15}},
        {{393}, {77, 25}, {106, 0}, {80, 24}, {50, 17}, {103, 15}},
        {{388}, {77, 30}, {106, 0}, {80, 50}, {50, 20}},
        {{391}, {77, 30}, {106, 0}, {80, 50}, {50, 20}},
        {{394}, {77, 30}, {106, 0}, {80, 50}, {50, 20}},
    };
    /** Mọi dòng chỉ số 2 đợt dùng (xoá rồi ghi lại); dòng khác như không giao dịch 30, bay 85 giữ nguyên. Dùng chung với ItemService.refreshTitle. */
    public static final java.util.Set<Integer> STAT_SET = java.util.Set.of(116, 10, 33, 176, 193, 196, 265, 147, 149, 5, 7, 8, 14, 29, 47, 50, 77, 80, 81, 83, 94, 95, 97, 100, 101, 103, 104, 106, 108,
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
        update(con, "insert into item_option_template (id, NAME) select 274, '#% cơ hội ra Chống lạnh' from dual where not exists (select 1 from item_option_template where id = 274)");
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1117a'")) {
            writeStats(con, EVENT_ITEMS6);
            update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) select 1954, 30, 0, 0, 100, 0 from dual where not exists (select 1 from item_template_option where template_id = 1954 and option_id = 30)");
            update(con, "insert into server_migration (name) values ('event_items_1117a')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1119a'")) {
            writeStats(con, EVENT_ITEMS7);
            for (int id : new int[]{1933, 1934}) { // "Không giao dịch" như ảnh
                update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) select " + id + ", 30, 0, 0, 100, 0 from dual where not exists (select 1 from item_template_option where template_id = " + id + " and option_id = 30)");
            }
            update(con, "insert into server_migration (name) values ('event_items_1119a')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1119b'")) {
            writeStats(con, EVENT_ITEMS8);
            update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) select 1797, 30, 0, 0, 100, 0 from dual where not exists (select 1 from item_template_option where template_id = 1797 and option_id = 30)");
            update(con, "insert into server_migration (name) values ('event_items_1119b')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1119c'")) { // Poc Bikini thêm dòng giảm SĐ người xung quanh (265)
            writeStats(con, new int[][][]{EVENT_ITEMS8[10]});
            update(con, "insert into server_migration (name) values ('event_items_1119c')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1119d'")) {
            writeStats(con, EVENT_ITEMS9);
            update(con, "insert into server_migration (name) values ('event_items_1119d')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1119e'")) {
            // trả lại chỉ số gốc CT Chi Chi 724 ở Santa (đợt 9 ghi nhầm SĐ / HP 20%): SĐ+20%, HP+17%, KI+17% (+ dòng 162 giữ nguyên)
            update(con, "delete from item_template_option where template_id = 724");
            update(con, "delete from item_shop_option where option_id in (50, 77, 103) and item_shop_id in (select id from item_shop where temp_id = 724)");
            for (int[] o : new int[][]{{50, 20}, {77, 17}, {103, 17}}) {
                update(con, "insert into item_shop_option (item_shop_id, option_id, param) select id, " + o[0] + ", " + o[1] + " from item_shop where temp_id = 724");
            }
            update(con, "insert into server_migration (name) values ('event_items_1119e')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1119f'")) {
            writeStats(con, EVENT_ITEMS10);
            update(con, "insert into server_migration (name) values ('event_items_1119f')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1119g'")) { // 628 là hộp (mở ra CT One Piece), không có chỉ số
            update(con, "delete from item_template_option where template_id = 628");
            update(con, "insert into server_migration (name) values ('event_items_1119g')");
        }
        // mô tả 628 giữ như data gốc (đợt 1119g từng đổi): "Cải trang Hải Tặc Tí Hon"
        update(con, "update item_template set description = 'Cải trang Hải Tặc Tí Hon' where id = 628 and description <> 'Cải trang Hải Tặc Tí Hon'");
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1119h'")) {
            writeStats(con, EVENT_ITEMS11);
            update(con, "insert into server_migration (name) values ('event_items_1119h')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1119i'")) { // Đôrêmon: giáp dùng option 47
            writeStats(con, new int[][][]{EVENT_ITEMS11[2]});
            update(con, "insert into server_migration (name) values ('event_items_1119i')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1119j'")) { // Đôrêmon: giáp % về option 94
            writeStats(con, new int[][][]{EVENT_ITEMS11[2]});
            update(con, "insert into server_migration (name) values ('event_items_1119j')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'nhs_ct_goc'")) {
            // Ngũ Hành Sơn: bỏ 3 cải trang đệ tử tự tạo 2128 - 2130 (trùng món có sẵn), dùng 548 Bát Giới / 547 Ngộ Không / 1302 Sa Tăng;
            // giữ dòng item_template (mã = vị trí) dạng vật phẩm đã gỡ, món người chơi đang có đổi về món gốc khi tải nhân vật
            update(con, "update item_template set NAME = 'Vật phẩm đã gỡ', description = 'Vật phẩm đã gỡ', TYPE = 27, part = -1, head = -1, body = -1, leg = -1 where id in (2128, 2129, 2130)");
            update(con, "delete from item_template_option where template_id in (2128, 2129, 2130)");
            update(con, "delete from item_shop where temp_id in (2128, 2129, 2130)");
            update(con, "delete from item_box_reward where item_id in (2128, 2129, 2130)");
            update(con, "delete from item_template_option where template_id = 1302");
            for (int[] o : new int[][]{{50, 15, 20}, {77, 50, 100}, {103, 50, 100}, {101, 30, 100}, {100, 70, 130}, {148, 50, 120}}) {
                update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) values (1302, " + o[0] + ", " + o[1] + ", " + o[2] + ", 100, 0)");
            }
            update(con, "insert into server_migration (name) values ('nhs_ct_goc')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'nhs_ct_giam_st'")) { // dòng Tôn Ngộ Không: + giảm 10 - 15% sát thương
            for (int id : new int[]{544, 545, 546, 547, 548, 1302}) {
                update(con, "delete from item_template_option where template_id = " + id + " and option_id = 94");
                update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) values (" + id + ", 94, 10, 15, 100, 0)");
            }
            update(con, "insert into server_migration (name) values ('nhs_ct_giam_st')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'ct_khong_hinh'")) {
            // 8 cải trang chưa từng có hình (ảnh 1x1 đen, không đầu / thân / chân), không nơi nào phát: giữ dòng (mã = vị trí) dạng vật phẩm đã gỡ
            String ids = "1839, 1849, 1905, 1908, 1911, 1912, 2062, 2063";
            update(con, "update item_template set NAME = 'Vật phẩm đã gỡ', description = 'Vật phẩm đã gỡ', TYPE = 27, part = -1, head = -1, body = -1, leg = -1 where id in (" + ids + ")");
            update(con, "delete from item_template_option where template_id in (" + ids + ")");
            update(con, "delete from item_shop where temp_id in (" + ids + ")");
            update(con, "delete from item_box_reward where item_id in (" + ids + ")");
            update(con, "delete from shop_ky_gui where item_id in (" + ids + ")");
            update(con, "insert into server_migration (name) values ('ct_khong_hinh')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1119k'")) {
            writeStats(con, EVENT_ITEMS12);
            update(con, "insert into server_migration (name) values ('event_items_1119k')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1119l'")) {
            writeStats(con, EVENT_ITEMS13);
            update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) select 1921, 30, 0, 0, 100, 0 from dual where not exists (select 1 from item_template_option where template_id = 1921 and option_id = 30)");
            update(con, "insert into server_migration (name) values ('event_items_1119l')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1119m'")) {
            writeStats(con, EVENT_ITEMS14);
            update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) select 629, 30, 0, 0, 100, 0 from dual where not exists (select 1 from item_template_option where template_id = 629 and option_id = 30)");
            update(con, "insert into server_migration (name) values ('event_items_1119m')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1119n'")) {
            writeStats(con, EVENT_ITEMS15);
            update(con, "delete from item_template_option where template_id between 642 and 646 and option_id = 14"); // bỏ chí mạng 3% tạm
            update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) select 681, 30, 0, 0, 100, 0 from dual where not exists (select 1 from item_template_option where template_id = 681 and option_id = 30)");
            update(con, "insert into server_migration (name) values ('event_items_1119n')");
        }
        // Kéo tỉa hoa giá cũ trùng (202 / 203), Hộp quà (hoa hồng) trùng 607 (204): Dot48 tạo lại nếu xoá nên tách khỏi sự kiện (mỗi lần khởi động)
        update(con, "update season_event_exchange set event_id = -8 where id in (202, 203, 204) and event_id = 8"); // 202 - 204 có id cố định (Dot48)
        // các dòng khác của sự kiện 20/11 / Giáng sinh: id mỗi máy chủ khác nhau, không sửa theo id (xem Dot140Migration)
        if (!exists(con, "select 1 from server_migration where name = 'force_cleanup_1123'")) {
            // bật tay cũ của các sự kiện khác (trước đây bị khoá chỉ chạy Halloween / 20-11 nên không có tác dụng): bỏ để không tự chạy lại (Hè 2 hiện top ở Chi Chi)
            update(con, "create table if not exists season_event_force (event_id int primary key, force_on tinyint not null)");
            update(con, "delete from season_event_force where force_on = 1 and event_id not in (7, 8)");
            update(con, "insert into server_migration (name) values ('force_cleanup_1123')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'ev2011_auto'")) { // 20/11 chạy tự động theo lịch
            update(con, "update season_event set enabled = 1 where id = 8");
            update(con, "insert into server_migration (name) values ('ev2011_auto')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1123g'")) { // CT Black Gohan Rose (ảnh)
            update(con, "delete from item_template_option where template_id = 883 and option_id = 154");
            writeStats(con, new int[][][]{{{883}, {50, 24}, {14, 3}, {103, 19}, {80, 10}, {154, 0}}});
            update(con, "insert into server_migration (name) values ('event_items_1123g')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1123f'")) { // CT Black Goku SSJ White, CT Zamasu (ảnh)
            update(con, "delete from item_template_option where template_id in (904, 898) and option_id in (154, 180)");
            writeStats(con, new int[][][]{
                {{904}, {50, 24}, {14, 4}, {94, 10}, {77, 15}, {103, 20}, {108, 5}, {80, 10}, {180, 2}, {154, 0}},
                {{898}, {50, 24}, {14, 3}, {77, 15}, {103, 20}, {5, 16}, {80, 11}, {154, 0}}});
            update(con, "insert into server_migration (name) values ('event_items_1123f')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'whis_hoa10_vv'")) { // quà tặng 10 bông: món có hạn đều có 15% ra vĩnh viễn
            update(con, "update item_box_reward set perm_rate = 15, hsd_days = '7,15' where box_id = 98412 and item_id in (1380, 1381, 1371, 955, 1933, 1934)");
            update(con, "insert into server_migration (name) values ('whis_hoa10_vv')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'hop_tra_2011_qua'")) {
            // Tặng Quy Lão: Hộp trà (98201) / Hộp trà hoa cúc (98213, mở ngay thay cho Sách giáo khoa) theo danh sách anh gửi
            update(con, "delete from item_box_reward where box_id in (98201, 98213)");
            update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) values "
                    + "(98201, -122, 1, 1, 15, '', 0, 0), (98201, 1230, 1, 1, 8, '7,15', 0, 0), (98201, 1205, 1, 1, 8, '7,15', 0, 0), (98201, -123, 1, 1, 15, '', 0, 0), (98201, 381, 1, 1, 3, '', 0, 0), (98201, 382, 1, 1, 3, '', 0, 0), (98201, 383, 1, 1, 3, '', 0, 0), (98201, 384, 1, 1, 3, '', 0, 0), (98201, 385, 1, 1, 3, '', 0, 0), (98201, 1173, 3, 3, 15, '', 0, 0), (98201, 920, 1, 1, 8, '7,15', 0, 0)");
            update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) values "
                    + "(98213, 1371, 1, 1, 6, '7,15', 5, 0), (98213, 1386, 1, 1, 8, '7,15', 0, 0), (98213, 1389, 1, 1, 6, '7,15', 5, 0), (98213, -122, 1, 1, 15, '', 0, 0), (98213, 1230, 1, 1, 8, '7,15', 0, 0), (98213, 1205, 1, 1, 6, '7,15', 5, 0), (98213, -123, 1, 1, 10, '', 0, 0), (98213, -124, 1, 1, 5, '', 0, 0), (98213, 1150, 1, 1, 3, '', 0, 0), (98213, 1151, 1, 1, 3, '', 0, 0), (98213, 1152, 1, 1, 3, '', 0, 0), (98213, 1153, 1, 1, 3, '', 0, 0), (98213, 1154, 1, 1, 3, '', 0, 0), (98213, 1173, 10, 10, 10, '', 0, 0), (98213, 920, 1, 1, 6, '7,15', 5, 0)");
            update(con, "update season_event_exchange set reward_item = 98213, open_now = 1 where event_id = 8 and need in ('1370:1', '1370:10') and reward_item = 1740");
            update(con, "insert into server_migration (name) values ('hop_tra_2011_qua')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'hop_tra_2011_sgk'")) { // tặng Hộp trà hoa cúc: quà + giữ Sách giáo khoa
            update(con, "update season_event_exchange set bonus_item = 1740, bonus_rate = 100 where event_id = 8 and reward_item = 98213");
            update(con, "insert into server_migration (name) values ('hop_tra_2011_sgk')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'sgk_2011_qua'")) {
            // Sách giáo khoa chỉ ra: CT đệ tử Tí / Sửu / Dần, CT Thầy giáo Quy Lão, Cải trang Pan VIP, Bó Hoa Hồng (954), Bó Hoa Hồng 2 Mới (1925)
            update(con, "delete from item_box_reward where box_id = 1740");
            update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) values "
                    + "(1740, 1371, 1, 1, 10, '7,15', 5, 0), (1740, 1380, 1, 1, 10, '7,15', 5, 0), (1740, 1381, 1, 1, 10, '7,15', 5, 0), "
                    + "(1740, 1385, 1, 1, 10, '7,15', 5, 0), (1740, 1921, 1, 1, 10, '7,15', 5, 0), (1740, 954, 1, 1, 10, '7,15', 5, 0), "
                    + "(1740, 1925, 1, 1, 10, '7,15', 5, 0)");
            update(con, "insert into server_migration (name) values ('sgk_2011_qua')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'thiep_2011_qua'")) {
            // Quà Thiệp chúc thường / đặc biệt theo danh sách anh gửi (-121 CT thầy giáo theo hành tinh, -122 bùa 1 giờ / 8 giờ ngẫu nhiên)
            update(con, "delete from item_box_reward where box_id in (1376, 1377)");
            update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) values "
                    + "(1376, -121, 1, 1, 10, '7,15', 0, 0), (1376, 1380, 1, 1, 8, '7,15', 5, 0), (1376, -122, 1, 1, 15, '', 0, 0), (1376, 1074, 1, 1, 5, '', 0, 0), (1376, 1075, 1, 1, 4, '', 0, 0), (1376, 1076, 1, 1, 3, '', 0, 0), (1376, 1079, 1, 1, 5, '', 0, 0), (1376, 1080, 1, 1, 4, '', 0, 0), (1376, 1081, 1, 1, 3, '', 0, 0), (1376, 1205, 1, 1, 6, '7,15', 5, 0), (1376, 2120, 1, 1, 10, '', 0, 0), (1376, 2089, 1, 1, 5, '', 0, 0), (1376, 381, 1, 1, 3, '', 0, 0), (1376, 382, 1, 1, 3, '', 0, 0), (1376, 383, 1, 1, 3, '', 0, 0), (1376, 384, 1, 1, 3, '', 0, 0), (1376, 385, 1, 1, 3, '', 0, 0), (1376, 1173, 10, 10, 10, '', 0, 0), (1376, 920, 1, 1, 6, '7,15', 5, 0)");
            update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) values "
                    + "(1377, 1371, 1, 1, 6, '7,15', 5, 0), (1377, 1381, 1, 1, 6, '7,15', 5, 0), (1377, 1380, 1, 1, 6, '7,15', 5, 0), (1377, -122, 1, 1, 15, '', 0, 0), (1377, 1074, 1, 1, 5, '', 0, 0), (1377, 1075, 1, 1, 4, '', 0, 0), (1377, 1076, 1, 1, 3, '', 0, 0), (1377, 1079, 1, 1, 5, '', 0, 0), (1377, 1080, 1, 1, 4, '', 0, 0), (1377, 1081, 1, 1, 3, '', 0, 0), (1377, 1205, 1, 1, 6, '7,15', 5, 0), (1377, 2120, 1, 1, 10, '', 0, 0), (1377, 2089, 1, 1, 5, '', 0, 0), (1377, 1150, 1, 1, 3, '', 0, 0), (1377, 1151, 1, 1, 3, '', 0, 0), (1377, 1152, 1, 1, 3, '', 0, 0), (1377, 1153, 1, 1, 3, '', 0, 0), (1377, 1154, 1, 1, 3, '', 0, 0), (1377, 1173, 20, 20, 10, '', 0, 0), (1377, 920, 1, 1, 6, '7,15', 5, 0), (1377, 1143, 1, 1, 10, '', 0, 0), (1377, 1668, 1, 1, 3, '', 0, 0)");
            update(con, "insert into server_migration (name) values ('thiep_2011_qua')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'thiep_2011_manh'")) {
            // Mảnh sưu tầm thường / VIP trong thiệp = mảnh thẻ rađa ngẫu nhiên (-123 hạng 0 - 2, -124 hạng 3 - 4), thay Thẻ sưu tầm thường / Mảnh thẻ VIP
            update(con, "update item_box_reward set item_id = -123 where box_id in (1376, 1377) and item_id = 2120");
            update(con, "update item_box_reward set item_id = -124 where box_id in (1376, 1377) and item_id = 2089");
            update(con, "insert into server_migration (name) values ('thiep_2011_manh')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'go_super_234'")) { // gỡ hẳn Super 2 / 3 / 4 sao (giữ ô vì mã = vị trí)
            update(con, "update item_template set NAME = 'Vật phẩm đã gỡ', description = 'Vật phẩm đã gỡ', TYPE = 27, part = -1, head = -1, body = -1, leg = -1 where id in (2107, 2108, 2109)");
            update(con, "delete from item_template_option where template_id in (2107, 2108, 2109)");
            update(con, "insert into server_migration (name) values ('go_super_234')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'hop_but_bay_2109'")) { // Hộp bút bay (data gốc: phương tiện bay, icon 16329, part 66)
            update(con, "update item_template set NAME = 'Hộp bút bay', description = 'Vật phẩm sự kiện', TYPE = 23, gender = 3, icon_id = 16329, part = 66, power_require = 0, head = -1, body = -1, leg = -1 where id = 2109");
            update(con, "insert into img_by_name (NAME, n_frame) select 'mount_66_0', 4 from dual where not exists (select 1 from img_by_name where NAME = 'mount_66_0')");
            update(con, "insert into server_migration (name) values ('hop_but_bay_2109')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'go_ct_khong_hinh'")) {
            // gỡ cải trang không có hình trong data (không đầu / thân / chân): giữ ô vì mã = vị trí, bỏ khỏi hộp quà / shop
            String g = "1145, 1415"; // CT Sư: ct_su_mabu;  Thủy Thủ Mặt Trăng: ct_thuy_thu;  Bill / Champa: ct_part_goc;  1710 / 1785 / 1800 / 1778 / 1996 có part (ct_khoi_phuc_part, ct_khoi_phuc_x4)
            update(con, "update item_template set NAME = 'Vật phẩm đã gỡ', description = 'Vật phẩm đã gỡ', TYPE = 27, part = -1, head = -1, body = -1, leg = -1 where id in (" + g + ")");
            update(con, "delete from item_template_option where template_id in (" + g + ")");
            update(con, "delete from item_box_reward where item_id in (" + g + ")");
            update(con, "delete from item_shop where temp_id in (" + g + ")");
            update(con, "delete from season_event_exchange where reward_item in (" + g + ")");
            update(con, "insert into server_migration (name) values ('go_ct_khong_hinh')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'ct_khoi_phuc_part'")) {
            // khôi phục 3 CT có part: Android 21 Tiến Sĩ (part v10 1964 / 1967 / 1968 -> 2410 - 2412, ảnh 14205 - 14239 có trong data),
            // Karin Kid Lân 2025 (cùng icon Karin Kid Lân: part 1198 - 1200), Chan Xư S+ (cùng icon Chan Xư: part 371 - 373)
            update(con, "delete from part where id in (2410, 2411, 2412)");
            update(con, "insert into part (id, TYPE, DATA) values (2410, 0, '[[14205,-2,-8],[14208,-6,-7],[2955,0,0]]'), "
                    + "(2411, 1, '[[14211,-4,-5],[14212,-3,-9],[14213,-4,-8],[14214,-2,-9],[14215,-1,-8],[14216,0,-7],[14217,0,-7],[14218,1,-7],[14219,1,-3],[14220,-2,-4],[14221,-4,-8],[14222,-4,-6],[14223,1,-5],[14224,3,-12],[14225,-6,-8],[14226,-6,-8],[2954,0,0]]'), "
                    + "(2412, 2, '[[14227,5,3],[14228,-1,-5],[14229,2,-2],[14230,4,-2],[14231,3,-3],[14232,4,-2],[14233,5,0],[14234,2,0],[14235,1,-4],[14236,1,-5],[14237,4,-2],[14238,2,2],[14239,0,-2],[2955,0,0]]')");
            update(con, "insert into head_avatar (head_id, avatar_id) select 2410, 14240 from dual where not exists (select 1 from head_avatar where head_id = 2410)");
            update(con, "update item_template set NAME = 'CT Android 21 Tiến Sĩ', description = 'VPSK', TYPE = 5, part = -1, head = 2410, body = 2411, leg = 2412 where id = 1710");
            update(con, "update item_template set NAME = 'Karin Kid Lân 2025', description = 'Vật phẩm sự kiện', TYPE = 5, part = -1, head = 1198, body = 1199, leg = 1200 where id = 1785");
            update(con, "update item_template set NAME = 'Cải trang Chan Xư S+', description = 'Cải trang sự kiện KOL', TYPE = 5, part = -1, head = 371, body = 372, leg = 373 where id = 1800");
            writeStats(con, new int[][][]{{{1710}, {50, 24}, {77, 22}, {103, 22}, {159, 5}, {253, 60}, {154, 0}},
                {{1785}, {50, 17}, {77, 15}, {103, 15}, {14, 5}, {80, 10}, {148, 20}},
                {{1800}, {50, 22}, {77, 18}, {103, 25}, {95, 7}, {83, 0}}});
            update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) select * from (select 758 b, 1785 i, 1 a, 1 c, 40 w, '3,7,15' h, 0 p, 0 y union all select 1779, 1785, 1, 1, 50, '3', 0, 0 "
                    + "union all select 1781, 1785, 1, 1, 62, '7,15', 5, 0 union all select 1995, 1710, 1, 1, 41, '7,15', 2, 0) t where not exists (select 1 from item_box_reward r where r.box_id = t.b and r.item_id = t.i)");
            update(con, "insert into season_event_exchange (event_id, grp, name, need, gold, gem, ruby, cost_point, reward_item, reward_qty, reward_qty_max, reward_days, perm_rate, bonus_item, bonus_rate, open_now, event_point, top_id, top_point, npc) "
                    + "select 8, 'Cửa\\nhàng', 'CT Android 21 Tiến Sĩ', '', 0, 5999, 0, 0, 1710, 1, 0, '', 0, 0, 0, 0, 0, 0, 0, '81' from dual where not exists (select 1 from season_event_exchange where reward_item = 1710)");
            update(con, "insert into server_migration (name) values ('ct_khoi_phuc_part')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'ct_khoi_phuc_x4'")) {
            // part từ bộ icon x4 gốc: Lý Tiểu Nương Rực Rỡ (ảnh 14738 - 14768, toạ độ theo bộ Lý Tiểu Nương hầu gái 1413 - 1415),
            // Goku giỗ tổ (ảnh 16763 - 16791, toạ độ theo cặp Chi Chi giỗ tổ 1959 - 1961, căn theo kích thước ảnh)
            update(con, "delete from part where id between 2413 and 2418");
            update(con, "insert into part (id, TYPE, DATA) values (2413, 0, '[[14738,-1,-8],[14739,-2,-12],[2955,0,0]]'), (2414, 1, '[[14740,-3,-5],[14741,-1,-8],[14742,1,-8],[14743,-1,-5],[14744,0,-6],[14745,2,-9],[14746,1,-8],[14747,2,-8],[14748,-1,-9],[14749,-2,-10],[14750,-2,-8],[14751,-2,-7],[14752,-4,-13],[14753,-1,-8],[14754,-2,-10],[14755,-2,-5],[2955,0,0]]'), (2415, 2, '[[14756,3,2],[14757,-1,-3],[14758,0,-2],[14759,-2,-4],[14760,-2,-5],[14761,-1,-4],[14762,1,-3],[14763,-1,-3],[14764,-1,-2],[14765,-4,-7],[14766,2,-5],[14767,-5,-1],[14768,0,-5],[2954,0,0]]'), (2416, 0, '[[16763,1,-18],[16764,-3,-19],[2955,0,0]]'), (2417, 1, '[[16765,-7,-9],[16766,-8,-9],[16767,-11,-17],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[16790,-8,-10],[16768,-8,-18],[16769,-15,-5],[16770,-11,-9],[16771,-7,-17],[16772,-8,-14],[16773,-12,-6],[16774,-12,-7],[16775,-20,-13],[2955,0,0]]'), (2418, 2, '[[16776,0,-3],[16791,-1,-10],[16777,0,-7],[16778,-10,-22],[16779,-16,-21],[16780,-8,-23],[16781,-10,-24],[16782,-3,-5],[16783,-6,-13],[16784,-4,-8],[16785,-2,0],[16786,-5,0],[16787,-2,-3],[2955,0,0]]')");
            update(con, "insert into head_avatar (head_id, avatar_id) select 2413, 14770 from dual where not exists (select 1 from head_avatar where head_id = 2413)");
            update(con, "insert into head_avatar (head_id, avatar_id) select 2416, 16789 from dual where not exists (select 1 from head_avatar where head_id = 2416)");
            update(con, "update item_template set NAME = 'CT Lý Tiểu Nương Rực Rỡ', description = 'Vật phẩm sự kiện', TYPE = 5, part = -1, head = 2413, body = 2414, leg = 2415 where id = 1778");
            update(con, "update item_template set NAME = 'Cải trang Goku giỗ tổ', description = 'Vật phẩm sự kiện', TYPE = 5, part = -1, head = 2416, body = 2417, leg = 2418 where id = 1996");
            writeStats(con, new int[][][]{{{1778}, {117, 30}, {14, 15}, {108, 15}}, {{1996}, {103, 35}, {7, 17000}, {108, 21}}});
            update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) select 2013, 1996, 1, 1, 50, '15,30', 2, 0 from dual where not exists (select 1 from item_box_reward where box_id = 2013 and item_id = 1996)");
            update(con, "insert into server_migration (name) values ('ct_khoi_phuc_x4')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'ct_part_goc'")) {
            // part gốc trong Nr_Part.sql (bộ x4): Lý Tiểu Nương Rực Rỡ (1686 - 1688) / Goku giỗ tổ (1968 - 1970) thay toạ độ ghép tạm 2413 - 2418;
            // Cải trang Bill (part 508 - 510, trùng sẵn), Cải trang Champa (511 + 512 / 513 theo bản gốc)
            update(con, "update part set DATA = '[[14738,0,-8],[14739,-2,-12],[2955,0,0]]' where id = 2413");
            update(con, "update part set DATA = '[[14740,-4,-4],[14741,-1,-6],[14742,0,-8],[14743,-1,-7],[14744,-1,-6],[14745,1,-7],[14746,0,-7],[14747,2,-10],[14748,-1,-6],[14749,-2,-6],[14750,-3,-7],[14751,-2,-6],[14752,-2,-8],[14753,-1,-7],[14754,-2,-7],[14755,-5,-4],[2955,0,0]]' where id = 2414");
            update(con, "update part set DATA = '[[14756,3,2],[14757,-2,-3],[14758,0,-2],[14759,-2,-4],[14760,0,-5],[14761,-1,-4],[14762,1,-3],[14763,0,-3],[14764,-1,-2],[14765,-4,-7],[14766,2,-5],[14767,-5,-1],[14768,0,-5],[2955,0,0]]' where id = 2415");
            update(con, "update part set DATA = '[[16763,0,-16],[16764,1,-20],[2955,0,0]]' where id = 2416");
            update(con, "update part set DATA = '[[16765,-12,-7],[16790,-13,-12],[16766,-9,-14],[2955,4,-5],[2955,0,0],[2955,0,0],[2955,0,0],[16767,-5,-14],[16768,-10,-15],[16769,-11,-13],[16770,-9,-13],[16771,-4,-14],[16772,-8,-16],[16773,-7,-13],[16774,-12,-11],[16775,-20,-14],[2955,0,0]]' where id = 2417");
            update(con, "update part set DATA = '[[16776,-1,1],[16791,-3,-7],[16777,0,-5],[16778,-4,-21],[16779,-10,-20],[16780,-4,-21],[16781,-4,-19],[16782,-3,-3],[16783,-3,-6],[16784,-7,-8],[16785,2,-3],[16786,0,-1],[16787,-2,-3],[2955,0,0]]' where id = 2418");
            update(con, "update part set DATA = '[[4881,-1,-5],[4882,-4,-10],[4883,-5,-8],[4884,-2,-7],[4885,-2,-8],[4886,-1,-7],[4887,-4,-7],[4888,-2,-6],[4889,0,-7],[4894,-2,-6],[4890,-1,-5],[4893,-4,-4],[4891,0,-5],[4895,-1,-6],[4892,0,-7],[4907,-2,-7],[3001,0,0]]' where id = 512");
            update(con, "update part set DATA = '[[4878,2,1],[4896,-2,-4],[4898,-15,-2],[4899,-8,-2],[4900,-17,-4],[4901,-9,-2],[4902,-12,-5],[4908,-3,-2],[4897,-5,-2],[4903,-2,-5],[4906,-3,-4],[4905,-3,0],[4904,-4,-3],[3002,0,0]]' where id = 513");
            update(con, "update item_template set NAME = 'Cải trang Bill', description = 'Vật phẩm sự kiện', TYPE = 5, part = -1, head = 508, body = 509, leg = 510 where id = 1906");
            update(con, "update item_template set NAME = 'Cải trang Champa', description = 'Vật phẩm sự kiện', TYPE = 5, part = -1, head = 511, body = 512, leg = 513 where id = 1907");
            update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) select 1728, 1906, 1, 1, 5, '7,15', 1, 0 from dual where not exists (select 1 from item_box_reward where box_id = 1728 and item_id = 1906)");
            update(con, "insert into head_avatar (head_id, avatar_id) select 511, 5068 from dual where not exists (select 1 from head_avatar where head_id = 511)");
            update(con, "insert into server_migration (name) values ('ct_part_goc')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'ct_thuy_thu'")) {
            // Thủy Thủ Mặt Trăng (icon mới 16761) dùng bộ part gốc của Cải trang Thủy Thủ Mặt Trăng cũ (772 - 774, đầu 7102 giống icon)
            update(con, "update item_template set NAME = 'Thủy Thủ Mặt Trăng', description = 'Vật phẩm sự kiện', TYPE = 5, part = -1, head = 772, body = 773, leg = 774 where id = 1991");
            writeStats(con, new int[][][]{{{1991}, {103, 30}, {7, 25000}}});
            update(con, "insert into server_migration (name) values ('ct_thuy_thu')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'ct_su_mabu'")) { // CT Sư (chung icon 13303 với CT Mabư nhí): dùng part Mabư nhí 1482 - 1484
            update(con, "update item_template set NAME = 'CT Sư', description = 'Vật phẩm sự kiện', TYPE = 5, part = -1, head = 1482, body = 1483, leg = 1484 where id = 1604");
            update(con, "insert into server_migration (name) values ('ct_su_mabu')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'gop_capsule_vo'")) {
            // Cápsule Vỡ 2111 trùng mã gốc 1634 (món có tác dụng ở CapsuleVoService): đổi mọi chỗ sang 1634, gỡ 2111
            update(con, "update season_event_top_reward set item_id = 1634 where item_id = 2111");
            update(con, "update season_event_reward_item set item_id = 1634 where item_id = 2111");
            update(con, "update boss_reward set item_id = 1634 where item_id = 2111");
            update(con, "update item_box_reward set item_id = 1634 where item_id = 2111");
            update(con, "update item_shop set temp_id = 1634 where temp_id = 2111");
            update(con, "update season_event_exchange set reward_item = 1634 where reward_item = 2111");
            update(con, "update season_event_drop set item_id = 1634 where item_id = 2111");
            update(con, "update item_template set NAME = 'Vật phẩm đã gỡ', description = 'Vật phẩm đã gỡ', TYPE = 27, part = -1, head = -1, body = -1, leg = -1 where id = 2111");
            update(con, "delete from item_template_option where template_id = 2111");
            update(con, "insert into server_migration (name) values ('gop_capsule_vo')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'bo_item_tu_them'")) {
            // bỏ vật phẩm không có trong data gốc: Mảnh khỉ Oozaru 1 / 2 (2104 / 2105, đồ đang giữ đổi thành Mảnh Khỉ Oozaru 1771),
            // Phiếu thức ăn (2106: thức ăn đổi ở Whis ra điểm sự kiện, quà đổi bằng điểm), Hào quang chân SPL10 (2112: chỉ còn hào quang khi mặc đồ đập 10 sao trong sự kiện)
            update(con, "delete from item_shop where temp_id in (2104, 2105, 2106, 2112)");
            update(con, "delete from item_shop where tab_id = 63"); // shop đổi Phiếu ăn của Bill
            update(con, "update season_event_exchange set reward_item = 0, open_now = 1, event_point = reward_qty, reward_qty = 1, "
                    + "name = replace(name, ' phiếu', ' điểm') where reward_item = 2106");
            update(con, "update season_event_exchange set cost_point = cast(substring(need, 6) as unsigned), need = '', grp = 'Đổi\\nđiểm' where need like '2106:%'");
            update(con, "delete from item_box_reward where item_id in (2104, 2105, 2106, 2112)");
            update(con, "update item_template set NAME = 'Vật phẩm đã gỡ', description = 'Vật phẩm đã gỡ', TYPE = 27, part = -1, head = -1, body = -1, leg = -1 where id in (2104, 2105, 2106, 2112)");
            update(con, "delete from item_template_option where template_id in (2104, 2105, 2106, 2112)");
            update(con, "insert into server_migration (name) values ('bo_item_tu_them')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'bo_kol_ky_lan'")) { // bỏ Vé KOL VIP 2110, Thú cưỡi Kỳ Lân 2121 (không có trong data gốc)
            update(con, "delete from item_shop where temp_id in (2110, 2121)");
            update(con, "delete from item_box_reward where item_id in (2110, 2121)");
            update(con, "delete from season_event_exchange where reward_item in (2110, 2121)");
            update(con, "delete from season_event_top_reward where item_id in (2110, 2121)");
            update(con, "update item_template set NAME = 'Vật phẩm đã gỡ', description = 'Vật phẩm đã gỡ', TYPE = 27, part = -1, head = -1, body = -1, leg = -1 where id in (2110, 2121)");
            update(con, "delete from item_template_option where template_id in (2110, 2121)");
            update(con, "insert into server_migration (name) values ('bo_kol_ky_lan')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'bo_hop_than_linh'")) { // Hộp thần linh 2103 (tự thêm) -> Hộp trang bị Thần 1863 (data gốc, mở ra 1 món đồ Thần Linh)
            update(con, "update season_event_exchange set reward_item = 1863, name = 'Hộp trang bị Thần' where reward_item = 2103");
            update(con, "update item_box_reward set item_id = 1863 where item_id = 2103");
            update(con, "update item_shop set temp_id = 1863 where temp_id = 2103");
            update(con, "update season_event_top_reward set item_id = 1863 where item_id = 2103");
            update(con, "update item_template set NAME = 'Vật phẩm đã gỡ', description = 'Vật phẩm đã gỡ', TYPE = 27, part = -1, head = -1, body = -1, leg = -1 where id = 2103");
            update(con, "delete from item_template_option where template_id = 2103");
            update(con, "insert into server_migration (name) values ('bo_hop_than_linh')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'ct_de_tu_dan_anh'")) { // CT đệ tử Dần theo ảnh: SĐ+24%, HP/KI+25%, Chống lạnh, biến 30% tấn công quái thành HP
            update(con, "insert ignore into item_option_template (id, NAME) values (279, 'Chống lạnh')");
            update(con, "update item_template set description = 'Vật phẩm sự kiện (chỉ dành cho đệ tử)' where id = 1381");
            update(con, "delete from item_template_option where template_id = 1381");
            update(con, "delete from item_shop_option where item_shop_id in (select id from item_shop where temp_id = 1381)");
            writeStats(con, new int[][][]{{{1381}, {50, 24}, {77, 25}, {103, 25}, {279, 0}, {104, 30}}});
            update(con, "insert into server_migration (name) values ('ct_de_tu_dan_anh')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'desc_1380'")) { // mô tả CT đệ tử Sửu giống Dần / Tí
            update(con, "update item_template set description = 'Vật phẩm sự kiện (chỉ dành cho đệ tử)' where id = 1380");
            update(con, "insert into server_migration (name) values ('desc_1380')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'reopen_2011_tops'")) {
            // Đua top 20/11 bị chốt sớm (trước 22/11 lịch sự kiện 20/11 còn ngắn nên sự kiện tự tắt và chốt top). Lịch nay 19/11 - 17/12,
            // top tính đến 2/12: mở lại top, trả điểm từ quà chờ nhận (hạng + điểm lúc chốt), bỏ quà top chưa nhận để không nhận 2 lần
            update(con, "create table if not exists season_event_top_closed (top_id int primary key, closed_at timestamp not null default current_timestamp)");
            String tops = "select id from season_event_top where event_id = 8";
            update(con, "insert into season_event_point (top_id, player_id, name, points) select p.top_id, p.player_id, coalesce((select name from player where id = p.player_id), ''), p.points "
                    + "from season_event_reward_pending p where p.top_id in (" + tops + ") and p.top_id in (select top_id from season_event_top_closed) "
                    + "on duplicate key update points = season_event_point.points + values(points)");
            update(con, "delete from season_event_reward_pending where top_id in (" + tops + ") and top_id in (select top_id from season_event_top_closed)");
            update(con, "delete from season_event_reward_item where top_id in (" + tops + ") and top_id in (select top_id from season_event_top_closed)");
            update(con, "delete from season_event_top_closed where top_id in (" + tops + ")");
            update(con, "insert into server_migration (name) values ('reopen_2011_tops')");
        }
        // Top 100 sự kiện hiện "X giờ trước" (lần cuối được cộng điểm) như bản chính thức
        update(con, "alter table season_event_point add column if not exists updated_at timestamp not null default current_timestamp on update current_timestamp");
        // Hình dáng lần lưu cuối (có cả hợp thể / biến khỉ) để Top 100 hiện đúng người offline
        update(con, "create table if not exists player_look (player_id bigint primary key, head int not null, body int not null, leg int not null)");
        if (!exists(con, "select 1 from server_migration where name = 'icon_gohan_ssj'")) { // CT Gohan SSJ: icon là ảnh đầu nhỏ 17807 (17838 là ảnh avatar to)
            update(con, "update item_template set icon_id = 17807 where id = 2125");
            update(con, "insert into server_migration (name) values ('icon_gohan_ssj')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'hop_tra_kygui'")) { // Hộp trà / Hộp trà hoa cúc: ký gửi vàng, giao dịch được
            update(con, "delete from item_template_option where template_id in (1369, 1370) and option_id in (30, 86, 87)");
            update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) values (1369, 86, 0, 0, 100, 0), (1370, 86, 0, 0, 100, 0)");
            update(con, "insert into server_migration (name) values ('hop_tra_kygui')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'nia_que_kygui'")) { // Nia tre ký gửi vàng, Que tre ký gửi ngọc
            update(con, "delete from item_template_option where template_id in (1365, 1366) and option_id in (86, 87)");
            update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) values (1365, 86, 0, 0, 100, 0), (1366, 87, 0, 0, 100, 0)");
            update(con, "insert into server_migration (name) values ('nia_que_kygui')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'thiep_2011_kygui'")) {
            // Thiệp chúc thầy cô theo bài: dòng Ký gửi vàng (Mảnh giấy, Keo dán, Thiệp thường) / Ký gửi ngọc (Thiệp đặc biệt);
            // thiệp mở ra vật phẩm có hạn hoặc vĩnh viễn (5%), Pet Capybara hồng trong thiệp đặc biệt luôn vĩnh viễn
            update(con, "delete from item_template_option where template_id in (1373, 1374, 1376, 1377) and option_id in (86, 87)");
            update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) values "
                    + "(1373, 86, 0, 0, 100, 0), (1374, 86, 0, 0, 100, 0), (1376, 86, 0, 0, 100, 0), (1377, 87, 0, 0, 100, 0)");
            if (!exists(con, "select 1 from server_migration where name = 'thiep_2011_qua'")) { // danh sách mới (thiep_2011_qua) đã có tỉ lệ riêng
                update(con, "update item_box_reward set perm_rate = 5 where box_id in (1376, 1377) and item_id <> 1668");
            }
            update(con, "insert into server_migration (name) values ('thiep_2011_kygui')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'thien_su_phu_1946'")) {
            // CT Thiên sư phụ theo ảnh: phân thân gây mù CD 300 giây, SĐ+22%, HP+28%, KI+49%, giảm 10% sát thương, né đòn +15%
            update(con, "insert ignore into item_option_template (id, NAME) values (277, 'Tạo ra phân thân gây mù đối phương CD # giây'), (278, 'Né đòn +#%')");
            update(con, "delete from item_template_option where template_id = 1946");
            update(con, "delete from item_shop_option where item_shop_id in (select id from item_shop where temp_id = 1946)");
            writeStats(con, new int[][][]{{{1946}, {277, 300}, {50, 22}, {77, 28}, {103, 49}, {94, 10}, {278, 15}}});
            update(con, "insert into server_migration (name) values ('thien_su_phu_1946')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'stats_anh_1124'")) {
            // Chỉ số theo ảnh anh gửi (tooltip đầy đủ): thay toàn bộ dòng chỉ số mặc định của các món dưới
            update(con, "insert ignore into item_option_template (id, NAME) values (276, 'Giáp #%')");
            for (int[][] it : STATS_ANH_1124) {
                int id = it[0][0];
                update(con, "delete from item_template_option where template_id = " + id + (id == 1699 ? " and option_id <> 236" : ""));
                update(con, "delete from item_shop_option where item_shop_id in (select id from item_shop where temp_id = " + id + ")" + (id == 1699 ? " and option_id <> 236" : ""));
            }
            writeStats(con, STATS_ANH_1124);
            update(con, "insert into server_migration (name) values ('stats_anh_1124')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'super_rank_box_1924'")) {
            // Rương Siêu Hạng theo bài diễn đàn: 50k-200k vàng, Rương SPL VIP, NR 1 sao, CT Black Fide (7-15 ngày, 5% vĩnh viễn)
            update(con, "delete from item_box_reward where box_id = 1924");
            update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) values "
                    + "(1924, -1, 50000, 200000, 400, '', 0, 0), (1924, 1453, 1, 1, 150, '', 0, 0), "
                    + "(1924, 14, 1, 1, 100, '', 0, 0), (1924, 1913, 1, 1, 50, '7,15', 5, 0)");
            update(con, "insert into server_migration (name) values ('super_rank_box_1924')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'whis_hoa10_vv7'")) { // CT đệ tử Mão / Dần / Tí, Bó Hoa Vàng: 7% vĩnh viễn (Videl / Gohan giữ 15%)
            update(con, "update item_box_reward set perm_rate = 7 where box_id = 98412 and item_id in (1380, 1381, 1371, 955)");
            update(con, "insert into server_migration (name) values ('whis_hoa10_vv7')");
        }
        // hoa hồng 20/11 (Bó 1388, Đóa 1395, Bông 1530) chỉ để tặng Whis: không phải hộp quà, không mở ra quà (mỗi lần khởi động)
        update(con, "delete from item_box_reward where box_id in (1388, 1395, 1530)");
        if (!exists(con, "select 1 from server_migration where name = 'whis_hoa10_box2'")) {
            // thêm lại quà cũ: CT đệ tử Mão / Dần / Tí (15 ngày), Bó Hoa Vàng (7-15 ngày), Đá bảo vệ
            update(con, "delete from item_box_reward where box_id = 98412 and item_id in (1380, 1381, 1371, 955, 987)");
            update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) values "
                    + "(98412, 1380, 1, 1, 8, '15', 1, 0), (98412, 1381, 1, 1, 8, '15', 1, 0), (98412, 1371, 1, 1, 8, '15', 1, 0), "
                    + "(98412, 955, 1, 1, 10, '7,15', 1, 0), (98412, 987, 1, 3, 15, '', 0, 0)");
            update(con, "insert into server_migration (name) values ('whis_hoa10_box2')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'whis_hoa10_box'")) {
            // quà tặng 10 bông hoa cho Whis: vàng, ngọc, ngọc rồng 1-3 sao, CT Videl / Gohan học đường 7-15 ngày (15% vĩnh viễn)
            update(con, "delete from item_box_reward where box_id = 98412");
            update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) values "
                    + "(98412, -1, 5000000, 20000000, 25, '', 0, 0), (98412, -2, 5, 20, 20, '', 0, 0), "
                    + "(98412, 14, 1, 1, 5, '', 0, 0), (98412, 15, 1, 1, 8, '', 0, 0), (98412, 16, 1, 1, 12, '', 0, 0), "
                    + "(98412, 1934, 1, 1, 15, '7,15', 15, 0), (98412, 1933, 1, 1, 15, '7,15', 15, 0)");
            update(con, "insert into server_migration (name) values ('whis_hoa10_box')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'whis_hoa10_them'")) {
            // Tặng 10 hoa cho Whis: thêm bùa 1h/8h, đá nâng cấp / đá may mắn cấp 1-3, mảnh sưu tầm thường / VIP, item hỗ trợ cấp 2,
            // Rương mảnh thiên sứ x10, Búa hắc hường (Blackpink) và Cờ Con Cat hường có hạn, Đá bảo vệ khóa
            update(con, "delete from item_box_reward where box_id = 98412 and item_id in (-122, -123, -124, 1074, 1075, 1076, 1079, 1080, 1081, 1150, 1151, 1152, 1153, 1154, 1173, 1254, 1344, 1143)");
            update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) values "
                    + "(98412, -122, 1, 1, 15, '', 0, 0), (98412, 1074, 1, 1, 5, '', 0, 0), (98412, 1075, 1, 1, 4, '', 0, 0), (98412, 1076, 1, 1, 3, '', 0, 0), (98412, 1079, 1, 1, 5, '', 0, 0), (98412, 1080, 1, 1, 4, '', 0, 0), (98412, 1081, 1, 1, 3, '', 0, 0), (98412, -123, 1, 1, 10, '', 0, 0), (98412, -124, 1, 1, 5, '', 0, 0), (98412, 1150, 1, 1, 3, '', 0, 0), (98412, 1151, 1, 1, 3, '', 0, 0), (98412, 1152, 1, 1, 3, '', 0, 0), (98412, 1153, 1, 1, 3, '', 0, 0), (98412, 1154, 1, 1, 3, '', 0, 0), (98412, 1173, 10, 10, 10, '', 0, 0), (98412, 1254, 1, 1, 6, '7,15', 0, 0), (98412, 1344, 1, 1, 6, '7,15', 0, 0), (98412, 1143, 1, 1, 10, '', 0, 0)");
            update(con, "insert into server_migration (name) values ('whis_hoa10_them')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'desc_1371'")) { // mô tả CT đệ tử Tí theo ảnh
            update(con, "update item_template set description = 'Vật phẩm sự kiện (chỉ dành cho đệ tử)' where id = 1371");
            update(con, "insert into server_migration (name) values ('desc_1371')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1123e'")) {
            StringBuilder ids = new StringBuilder();
            for (int[][] it : EVENT_ITEMS26) {
                ids.append(ids.length() > 0 ? "," : "").append(it[0][0]);
            }
            update(con, "delete from item_template_option where template_id in (" + ids + ") and option_id in (30, 154, 85, 24, 160, 0)");
            writeStats(con, EVENT_ITEMS26);
            update(con, "insert into server_migration (name) values ('event_items_1123e')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1123d'")) { // CT Hổ trắng (ảnh)
            update(con, "delete from item_template_option where template_id = 952 and option_id in (30, 154)");
            writeStats(con, new int[][][]{{{952}, {77, 23}, {103, 23}, {50, 23}, {94, 11}, {80, 11}, {154, 0}}});
            update(con, "insert into server_migration (name) values ('event_items_1123d')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1123c'")) {
            StringBuilder ids = new StringBuilder();
            for (int[][] it : EVENT_ITEMS25) {
                ids.append(ids.length() > 0 ? "," : "").append(it[0][0]);
            }
            update(con, "delete from item_template_option where template_id in (" + ids + ") and option_id in (30, 154, 0, 267)");
            writeStats(con, EVENT_ITEMS25);
            update(con, "insert into server_migration (name) values ('event_items_1123c')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1123b'")) {
            StringBuilder ids = new StringBuilder();
            for (int[][] it : EVENT_ITEMS24) {
                ids.append(ids.length() > 0 ? "," : "").append(it[0][0]);
            }
            // dòng không phải chỉ số (không giao dịch, không bán lại, bay, chống lạnh, kích hồi, HP+#, tấn công+#, dòng ẩn) xoá trước để không trùng
            update(con, "delete from item_template_option where template_id in (" + ids + ") and option_id in (30, 154, 84, 106, 223, 6, 0, 210, 19)");
            writeStats(con, EVENT_ITEMS24);
            update(con, "insert into server_migration (name) values ('event_items_1123b')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'ev2011_dates'")) {
            // 20/11 theo diễn đàn: 19/11 - hết 17/12 (đua top tính đến 2/12, 15 ngày trước khi hết)
            update(con, "update season_event set days_before = 1, days_after = 27 where id = 8");
            update(con, "insert into server_migration (name) values ('ev2011_dates')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'condau_vip_desc2'")) {
            update(con, "update item_template set description = 'Vật phẩm sự kiện' where id = 2122");
            update(con, "insert into server_migration (name) values ('condau_vip_desc2')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'broly_lv_2011'")) {
            update(con, "update radar set max = 50 where id = 2126"); // Thẻ Broly: 15 / 50 / 50 mảnh (RadarService.thresholds)
            
            update(con, "insert into server_migration (name) values ('broly_lv_2011')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'quylao_2011_order'")) {
            // cửa hàng đổi điểm Quy Lão 20/11 đúng 25 món, đúng thứ tự như video (thêm theo thứ tự id = thứ tự hiện)
            update(con, "update season_event_exchange set event_id = -8 where event_id = 8 and npc = '13' and cost_point > 0");
            int[][] ql = { // {item, điểm, HSD ngày, top}
                {1757, 99, 30, 0}, {1840, 99, 30, 0}, {1944, 99, 30, 803}, {1821, 99, 30, 0}, {1901, 99, 30, 0},
                {1713, 499, 0, 0}, {1682, 499, 0, 0}, {1632, 499, 0, 0}, {2025, 499, 0, 0},
                {1941, 750, 0, 0}, {1735, 750, 0, 0}, {1711, 750, 0, 0},
                {1943, 999, 0, 0}, {2022, 999, 0, 0}, {1997, 999, 30, 0}, {1731, 999, 0, 0}, {1795, 999, 30, 0}, {1567, 999, 0, 0}, {1476, 999, 0, 0}, {1921, 999, 0, 0},
                {2023, 1999, 0, 0}, {1976, 1999, 0, 0}, {1971, 1999, 0, 0}, {1981, 1999, 0, 0}, {1963, 1999, 0, 0}};
            for (int[] q : ql) {
                update(con, "insert into season_event_exchange (event_id, grp, name, need, gold, gem, ruby, cost_point, reward_item, reward_qty, reward_days, top_id, top_point, npc) "
                        + "select 8, 'Đổi\\nđiểm', NAME, '', 0, 0, 0, " + q[1] + ", " + q[0] + ", 1, '" + (q[2] > 0 ? q[2] : "") + "', " + q[3] + ", " + (q[3] > 0 ? 1 : 0)
                        + ", '13' from item_template where id = " + q[0]);
            }
            update(con, "insert into server_migration (name) values ('quylao_2011_order')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'video_2011'")) {
            // 20/11 theo video "Ơn Thầy Nghĩa Trò"
            // Bà Hạt Mít: nút "Làm Túi trà khô", "Làm Hộp trà"...
            String[][] names = {{"575", "Làm\\nTúi trà khô"}, {"576", "Làm\\nHộp trà"}, {"577", "Làm\\nHộp trà hoa\\ncúc"}, {"578", "Làm\\nThiệp chúc\\nthường"}, {"579", "Làm\\nThiệp chúc\\nđặc biệt"}};
            for (String[] n : names) {
            }
            // tặng hoa: nút riêng ở Whis (Hành tinh Kaio), dùng Bó hoa hồng; bỏ công thức cũ và Hộp quà đổi bằng Bông hoa hồng
            update(con, "delete from item_box_reward where box_id = 98412");
            update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) values "
                    + "(98412, 1380, 1, 1, 20, '15', 1, 0), (98412, 1381, 1, 1, 20, '15', 1, 0), (98412, 1371, 1, 1, 20, '15', 1, 0), "
                    + "(98412, 1934, 1, 1, 8, '7,15', 1, 0), (98412, 955, 1, 1, 15, '7,15', 1, 0), (98412, 987, 1, 3, 17, '', 0, 0)");
            // Keo dán: Ốc mượn hồn / Ốc sên
            update(con, "update season_event_drop set mobs = '13,14' where event_id = 8 and item_id = 1374");
            // danh hiệu Gõ Đầu Trẻ (ảnh): SĐ 8%, HP 10%, KI 10%, chí mạng 10%
            for (int id = 1392; id <= 1394; id++) {
                writeStats(con, new int[][][]{{{id}, {50, 8}, {77, 10}, {103, 10}, {14, 10}}});
            }
            // CT đệ tử Sửu / Dần (ảnh): SĐ 25%, HP / KI 23%, chống lạnh, hút 5% HP KI xung quanh mỗi 5 giây
            for (int id : new int[]{1380, 1381}) {
                writeStats(con, new int[][][]{{{id}, {50, 25}, {77, 23}, {103, 23}, {106, 0}, {8, 5}}});
            }
            // cửa hàng đổi điểm Quy Lão (video): thêm CT Chi Chi giỗ tổ, CT Chi Chi Hoa Hồng Xanh (HSD 30 ngày), CT Frieren, Bunma rực rỡ, Pan VIP; Pet Hải Ly 499, Rương Rồng Thần HSD 30 ngày
            int[][] ql = {{1997, 999, 30}, {1795, 999, 30}, {1567, 999, 0}, {1476, 999, 0}, {1921, 999, 0}};
            for (int[] q : ql) {
                String nm = "(select NAME from item_template where id = " + q[0] + ")";
                update(con, "insert into season_event_exchange (event_id, grp, name, need, gold, gem, ruby, cost_point, reward_item, reward_qty, reward_days, top_id, top_point, npc) "
                        + "select 8, 'Đổi\\nđiểm', " + nm + ", '', 0, 0, 0, " + q[1] + ", " + q[0] + ", 1, '" + (q[2] > 0 ? q[2] : "") + "', 0, 0, '13' from dual "
                        + "where not exists (select 1 from season_event_exchange where event_id = 8 and npc = '13' and reward_item = " + q[0] + ")");
            }
            update(con, "insert into server_migration (name) values ('video_2011')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'radar_broly'")) {
            // Thẻ Broly (radar no.21, ảnh): Mở khóa +1% vàng rơi; Lv.1 tấn công +5% khi đánh quái, +3% vàng rơi; Lv.2 chí mạng +5%, +3% vàng rơi; Lv.3 +5% SĐ chí mạng, +4% vàng rơi
            update(con, "insert into item_template (id, TYPE, gender, NAME, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg) "
                    + "select 2126, 33, 3, 'Mảnh Broly', 'Vật phẩm sự kiện', 0, 17798, -1, 1, 0, 0, 0, -1, -1, -1 from dual where not exists (select 1 from item_template where id = 2126)");
            update(con, "insert into radar (id, iconId, `rank`, max, type, mob_id, body, name, info, options, `require`, require_level, aura_id) select 2126, 17798, 6, 125, 1, -1, "
                    + "'[{\"head\":2398, \"body\":2399, \"leg\":2400, \"bag\":-1}]', 'Thẻ Broly', 'Vật phẩm sự kiện', "
                    + "'[{\"id\": 268, \"param\": 1, \"activeCard\": 0}, {\"id\": 19, \"param\": 5, \"activeCard\": 1}, {\"id\": 268, \"param\": 3, \"activeCard\": 1}, "
                    + "{\"id\": 14, \"param\": 5, \"activeCard\": 2}, {\"id\": 268, \"param\": 3, \"activeCard\": 2}, {\"id\": 5, \"param\": 5, \"activeCard\": 3}, {\"id\": 268, \"param\": 4, \"activeCard\": 3}]', -1, 0, -1 "
                    + "from dual where not exists (select 1 from radar where id = 2126)");
            update(con, "insert into server_migration (name) values ('radar_broly')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'parts_x4_1122'")) {
            for (String[] pt : PARTS_X4) {
                update(con, "delete from part where id = " + pt[0]);
                update(con, "insert into part (id, TYPE, DATA) values (" + pt[0] + ", " + pt[1] + ", '" + pt[2] + "')");
            }
            int[][] av = {{2398, 17790}, {2401, 17793}, {2404, 17796}, {2407, 17838}};
            for (int[] a : av) {
                update(con, "insert into head_avatar (head_id, avatar_id) select " + a[0] + ", " + a[1] + " from dual where not exists (select 1 from head_avatar where head_id = " + a[0] + ")");
            }
            // Cải trang Gohan SSJ (chỉ dành cho đệ tử): SĐ 20 - 25%, tấn công +5.000 - 10.000 (ảnh Hộp tháng 20/11 / Giấy tốt nghiệp)
            update(con, "insert into item_template (id, TYPE, gender, NAME, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg) "
                    + "select 2125, 5, 3, 'Cải trang Gohan SSJ', 'Vật phẩm sự kiện (chỉ dành cho đệ tử)', 0, 17807, -1, 0, 0, 0, 0, 2407, 2408, 2409 from dual where not exists (select 1 from item_template where id = 2125)");
            update(con, "delete from item_template_option where template_id = 2125");
            update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) values (2125, 50, 20, 25, 100, 0), (2125, 0, 5000, 10000, 100, 0)");
            update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) select 1739, 2125, 1, 1, 10, '3', 1, 0 from dual where not exists (select 1 from item_box_reward where box_id = 1739 and item_id = 2125)");
            update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) select 2124, 2125, 1, 1, 5, '15,30,45', 2, 0 from dual where not exists (select 1 from item_box_reward where box_id = 2124 and item_id = 2125)");
            // hình biến hình khỉ xanh / Broly xanh (aura_11 - 13 của gói, đổi số vì 11 - 13 đã là hào quang thẻ radar), 8 khung
            for (String n : new String[]{"aura_123_0", "aura_124_0", "aura_124_1", "aura_125_0", "aura_125_1"}) {
                update(con, "insert into img_by_name (NAME, n_frame) select '" + n + "', 8 from dual where not exists (select 1 from img_by_name where NAME = '" + n + "')");
            }
            update(con, "insert into server_migration (name) values ('parts_x4_1122')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'icon_x4_1119'")) {
            // hình mới gói x4 anh gửi: Giỏ hoa 20/11, Giấy tốt nghiệp, Con dấu VIP (trước mượn hình Con dấu)
            update(con, "update item_template set icon_id = 17842 where id = 2123");
            update(con, "update item_template set icon_id = 17843 where id = 2124");
            update(con, "update item_template set icon_id = 17805 where id = 2122");
            update(con, "insert into server_migration (name) values ('icon_x4_1119')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'restore_98302'")) {
            // bản thử trước ghi nhầm quà tặng hoa Whis vào hộp 98302 (Giao Người tuyết - Giáng sinh): trả lại quà gốc
            if (exists(con, "select 1 from item_box_reward where box_id = 98302 and item_id = 1934")) {
                update(con, "delete from item_box_reward where box_id = 98302");
                Dot118Migration.box(con, 98302, new int[][]{{76, 15}, {77, 15}, {17, 5}, {18, 5}, {19, 5}, {20, 5}, {1758, 5}, {1759, 5}, {1150, 5}, {1151, 5}, {1152, 5},
                    {1153, 5}, {1154, 5}, {925, 2}, {926, 2}, {927, 2}, {928, 2}, {929, 2}, {930, 2}, {931, 2}}, "");
                update(con, "update item_box_reward set qty_min = 5000000, qty_max = 20000000 where box_id = 98302 and item_id = 76");
                update(con, "update item_box_reward set qty_min = 10, qty_max = 50 where box_id = 98302 and item_id = 77");
                update(con, "delete from server_migration where name = 'event_2011_rest'"); // chạy lại phần 20/11 với mã quà mới 98411 / 98412
            }
            update(con, "insert into server_migration (name) values ('restore_98302')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_2011_rest'")) {
            // 20/11 phần còn lại theo bài diễn đàn
            // Mảnh giấy: map rừng (Bamboo, Dương xỉ, Cọ, Đá) tối đa 999/ngày; Keo dán: Ốc sên Trái Đất; Nia tre thêm từ boss; Lời chúc xin ở menu người chơi (bỏ rơi từ quái)
            update(con, "update season_event_drop set maps = '27,28,35,36', daily_max = 999 where event_id = 8 and item_id = 1373");
            update(con, "update season_event_drop set mobs = '14', rate = 50, daily_max = 999 where event_id = 8 and item_id = 1374");
            update(con, "delete from season_event_drop where event_id = 8 and item_id = 1375");
            update(con, "insert into season_event_drop (event_id, item_id, rate, qty_min, qty_max, source, daily_max, item_to, need_item, mobs, maps) "
                    + "select 8, 1365, 5000, 1, 3, 'boss', 999, 0, 0, '', '' from dual where not exists (select 1 from season_event_drop where event_id = 8 and item_id = 1365 and source = 'boss')");
            // Tặng Hộp trà hoa cúc ở Quy Lão ra Sách giáo khoa; mở Sách giáo khoa ra cải trang (thêm CT Gohan học đường)
            update(con, "delete from item_box_reward where box_id in (1740, 1376, 1377, 98412)");
            update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) select 1740, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity from item_box_reward where box_id in (98202, 98203)");
            update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) values (1740, 1933, 1, 1, 10, '7,15', 2, 0)");
            // Thiệp chúc thường / đặc biệt mở ra vật phẩm (đặc biệt có thêm Pet Capybara hồng vĩnh viễn)
            update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) select 1376, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity from item_box_reward where box_id = 98201");
            update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) select 1377, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity from item_box_reward where box_id = 98202");
            update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) values (1377, 1668, 1, 1, 5, '', 0, 0)");
            // Whis (Thần Vũ Trụ): tặng hoa - 1 đóa x2 TNSM sư phụ + đệ tử 30 phút, 10 đóa thêm 1 quà đặc biệt (CT Videl học đường...)
            update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) values "
                    + "(98412, 1934, 1, 1, 10, '7,15', 2, 0), (98412, 1933, 1, 1, 10, '7,15', 2, 0), (98412, 955, 1, 1, 20, '7,15', 2, 0), (98412, 987, 1, 3, 30, '', 0, 0), (98412, 457, 1, 2, 30, '', 0, 0)");
            update(con, "insert into season_event_exchange (event_id, grp, name, need, reward_item, open_now, npc) select 8, 'Tặng\\nhoa', 'Tặng 1 Đóa hoa', '1530:1', 98411, 1, '56' from dual where not exists (select 1 from season_event_exchange where reward_item = 98411)");
            update(con, "insert into season_event_exchange (event_id, grp, name, need, reward_item, open_now, npc) select 8, 'Tặng\\nhoa', 'Tặng 10 Đóa hoa', '1530:10', 98412, 1, '56' from dual where not exists (select 1 from season_event_exchange where reward_item = 98412)");
            update(con, "insert into server_migration (name) values ('event_2011_rest')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'top_2011_spl'")) {
            // top Hộp quà 20-11 (vàng): top 1 Sao pha lê lấp lánh 10% vàng, top 2 9% vàng (chỉ số đặt lúc nhận quà, SeasonEventService.topRewardItem)
            update(con, "insert into season_event_top_reward (top_id, rank_from, rank_to, item_id, qty, hsd_days, min_point) values (801, 1, 1, 1431, 1, '', 0), (801, 2, 2, 1431, 1, '', 0)");
            update(con, "insert into server_migration (name) values ('top_2011_spl')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'box_2011'")) {
            // quà hộp theo ảnh 20/11 (item:qtyMin-qtyMax:weight:hsd:perm%:bảo hiểm); Gohan SSJ (đệ tử) chưa có trong data
            String[][] boxes = {
                {"1944", // Hộp 20/11 VIP
                    "1937:1:5:7,15:0:200", "2116:1:5:7,15:0:200", "2045:1:30::0:0", "1951:1:20:7,15:0:70", "1173:10:25::0:0",
                    "1386:1:20:7,15:3:0", "2090:1:15::0:0", "2075:1:3:7,15:0:500", "1453:1:20::0:0", "1965:1:20:7,15:0:70"},
                {"1739", // Hộp tháng 20/11 (20tr vàng): hạn dùng + 1% vĩnh viễn
                    "1380:1:20:3:1:0", "1381:1:20:3:1:0", "1383:1:20:3:1:0", "1384:1:20:3:1:0", "1389:1:7:3:1:0", "1390:1:7:3:1:0", "1391:1:6:3:1:0",
                    "1371:1:20:3:1:0", "2017:1:5:3:1:300", "2061:1:15:3:1:0", "1998:1:8:3:1:100"},
                {"2124", // Giấy tốt nghiệp (50 ngọc): hạn dùng 15 / 30 / 45 ngày + 2% vĩnh viễn
                    "1919:1:5:15,30,45:2:0", "860:1:5:15,30,45:2:0", "1380:1:10:15,30,45:2:0", "1381:1:10:15,30,45:2:0", "1383:1:10:15,30,45:2:0",
                    "1384:1:10:15,30,45:2:0", "1389:1:4:15,30,45:2:0", "1390:1:3:15,30,45:2:0", "1391:1:3:15,30,45:2:0", "1371:1:10:15,30,45:2:0"}};
            for (String[] b : boxes) {
                update(con, "delete from item_box_reward where box_id = " + b[0]);
                for (int i = 1; i < b.length; i++) {
                    String[] f = b[i].split(":", -1);
                    update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) values (" + b[0] + ", " + f[0] + ", " + f[1] + ", " + f[1]
                            + ", " + f[2] + ", '" + f[3] + "', " + f[4] + ", " + f[5] + ")");
                }
            }
            update(con, "insert into server_migration (name) values ('box_2011')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'tops_2011c'")) {
            // quà 4 top 20/11 theo bài diễn đàn chính thức (item_id:qty:hsd, rank_from-rank_to, min_point)
            String[][] rw = {
                // A. Hộp quà 20/11 VIP (803) - đổi điểm Quy Lão (Ván Bút Chì Bay chưa có trong data)
                {"803", "1-1", "1283:70,1559:3,1634:3,2098:999,2122:2"}, {"803", "2-2", "1283:60,1559:2,1634:2,2098:500,2122:1"},
                {"803", "3-3", "1283:50,1559:1,1634:1,2098:200"}, {"803", "4-10", "1283:20,1690:50,1909:250,2098:20"},
                {"803", "11-50", "1283:10,1690:20,1909:100"}, {"803", "51-100", "1283:5,1690:10,1909:20"},
                {"803", "1-1:25000", "1538:1,2102:1"},
                // B. Giấy tốt nghiệp (805) - top ngọc
                {"805", "1-1", "2085:1,1283:70,1559:3,1634:3,2046:10,2122:2,2006:1"}, {"805", "2-2", "2085:1,1283:60,1559:2,1634:2,2046:5,2122:1"},
                {"805", "3-3", "2085:1,1283:50,1559:1,1634:1,2046:2"}, {"805", "4-10", "2085:1:365,1283:20,1690:50,1909:100"},
                {"805", "11-50", "2085:1:90,1283:10,1690:20,1909:20"}, {"805", "51-100", "2085:1:30,1283:5,1690:10"},
                {"805", "1-1:41250", "2102:1"},
                // C. Hộp quà 20-11 (801) - top vàng (Sao pha lê 10% / 9% vàng chưa có trong data)
                {"801", "1-1", "1809:100,987:60,1559:3,1634:3,1868:10000,2046:10"}, {"801", "2-2", "1809:50,987:40,1559:2,1634:2,1868:5000,2046:5"},
                {"801", "3-3", "1809:30,987:30,1559:1,1634:1,1868:1000,2046:2"}, {"801", "4-10", "1809:20,987:20,1690:50,1909:100"},
                {"801", "11-50", "1809:10,987:10,1690:20,1909:20"}, {"801", "51-100", "1809:5,987:5,1690:10"},
                {"801", "1-1:65000", "1537:1"},
                // D. Tặng bộ ngọc rồng cho Quy Lão (802)
                {"802", "1-1", "2100:1,1283:70,1559:3,1634:3,1868:10000,2046:10,2006:1"}, {"802", "2-2", "2100:1,1283:60,1559:2,1634:2,1868:5000,2046:5"},
                {"802", "3-3", "2100:1,1283:50,1559:1,1634:1,1868:1000,2046:2"}, {"802", "4-10", "2100:1,1283:20,1690:50,1909:100"},
                {"802", "11-50", "2100:1:90,1283:10,1690:20,1909:20"}, {"802", "51-100", "2100:1:60,1283:5,1690:10"}};
            update(con, "delete from season_event_top_reward where top_id in (801, 802, 803, 805)");
            for (String[] r : rw) {
                String[] rk = r[1].split(":");
                String[] ft = rk[0].split("-");
                int minPoint = rk.length > 1 ? Integer.parseInt(rk[1]) : 0;
                for (String it : r[2].split(",")) {
                    String[] f = it.split(":");
                    update(con, "insert into season_event_top_reward (top_id, rank_from, rank_to, item_id, qty, hsd_days, min_point) values (" + r[0] + ", " + ft[0] + ", " + ft[1]
                            + ", " + f[0] + ", " + f[1] + ", '" + (f.length > 2 ? f[2] : "") + "', " + minPoint + ")");
                }
            }
            update(con, "update season_event_top set name = 'Giao 7 viên ngọc rồng' where id = 802");
            // điểm top tính lúc mua như bản chính thức: Hộp quà 20-11 (Chi Chi, vàng) -> 801, Hộp quà 20-11 VIP (Quy Lão, điểm) -> 803
            update(con, "insert into season_event_exchange (event_id, grp, name, need, gold, gem, ruby, cost_point, reward_item, reward_qty, reward_days, top_id, top_point, npc) "
                    + "select 8, 'Đổi\\nđiểm', 'Hộp quà 20-11 VIP', '', 0, 0, 0, 99, 1944, 1, '30', 803, 1, '13' from dual where not exists (select 1 from season_event_exchange where event_id = 8 and reward_item = 1944)");
            // Giỏ hoa 20/11 là vật phẩm đua top (không phải hộp quà)
            update(con, "delete from item_box_reward where box_id = 2123");
            update(con, "update item_template set description = 'Vật phẩm đua top' where id = 2123");
            // CT đệ tử Tí (ảnh): SĐ 24%, HP / KI 22%, chống lạnh, tấn công +30% khi đánh quái
            writeStats(con, new int[][][]{{{1371}, {50, 24}, {77, 22}, {103, 22}, {106, 0}, {19, 30}}});
            update(con, "insert into server_migration (name) values ('tops_2011c')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'shop_2011'")) {
            // Cửa hàng Chi Chi 20/11 và công thức Bà Hạt Mít theo ảnh bản chính thức
            update(con, "insert into item_template (id, TYPE, gender, NAME, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg) "
                    + "select 2123, 27, 3, 'Giỏ hoa 20/11', 'Vật phẩm sự kiện', 0, 10484, -1, 1, 0, 0, 0, -1, -1, -1 from dual where not exists (select 1 from item_template where id = 2123)");
            update(con, "insert into item_template (id, TYPE, gender, NAME, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg) "
                    + "select 2124, 27, 3, 'Giấy tốt nghiệp', 'Vật phẩm đua top', 0, 11804, -1, 1, 0, 0, 0, -1, -1, -1 from dual where not exists (select 1 from item_template where id = 2124)");
            // Giỏ hoa 20/11 mở ra quà như Hộp quà 20-11
            update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) select 2123, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity "
                    + "from item_box_reward where box_id = 1739 and not exists (select 1 from item_box_reward where box_id = 2123)");
            String[] shop = {"598:20000000:0:30", "599:30000000:0:35", "600:0:10:35", "601:0:10:35", "602:40000000:0:35", "603:0:3000:", "604:0:1000:", "605:0:5999:"};
            for (String s : shop) {
                String[] f = s.split(":", -1);
            }
            update(con, "insert into season_event_exchange (event_id, grp, name, need, gold, gem, ruby, cost_point, reward_item, reward_qty, reward_days, top_id, top_point, npc) "
                    + "select 8, 'Cửa\\nhàng', 'Giỏ hoa 20/11', '', 0, 20, 0, 0, 2123, 1, '30', 0, 0, '81' from dual where not exists (select 1 from season_event_exchange where reward_item = 2123)");
            update(con, "insert into season_event_exchange (event_id, grp, name, need, gold, gem, ruby, cost_point, reward_item, reward_qty, reward_days, top_id, top_point, npc) "
                    + "select 8, 'Cửa\\nhàng', 'Giấy tốt nghiệp', '', 0, 50, 0, 0, 2124, 1, '30', 805, 1, '81' from dual where not exists (select 1 from season_event_exchange where reward_item = 2124)");
            // top Giấy tốt nghiệp: mỗi tờ mua 1 điểm, quà như các top 20/11
            update(con, "insert into season_event_top (id, event_id, name) select 805, 8, 'Giấy tốt nghiệp' from dual where not exists (select 1 from season_event_top where id = 805)");
            update(con, "insert into season_event_top_reward (top_id, rank_from, rank_to, item_id, qty, hsd_days, min_point) select 805, rank_from, rank_to, item_id, qty, hsd_days, min_point "
                    + "from season_event_top_reward where top_id = 802 and not exists (select 1 from season_event_top_reward where top_id = 805)");
            // Bà Hạt Mít: Hộp trà 30tr vàng, Hộp trà hoa cúc 5 ngọc, thiệp thường 15 ngọc, thiệp đặc biệt 30 ngọc, Túi trà khô miễn phí
            writeStats(con, new int[][][]{{{1710}, {50, 24}, {77, 22}, {103, 22}, {159, 5}, {253, 60}, {154, 0}}}); // CT Android 21 Tiến Sĩ (ảnh)
            update(con, "delete from item_template_option where template_id = 1710 and option_id = 14");
            update(con, "insert into server_migration (name) values ('shop_2011')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'tops_2011'")) {
            // đua top 20/11 như bản chính thức: Giao 7 viên ngọc rồng (802), Hộp quà 20-11 VIP (803), Hộp quà 20-11 (801, trước là Tặng hộp trà); quà như top cũ
            for (int[] t : new int[][]{{802}, {803}}) {
                String name = t[0] == 802 ? "Giao 7 viên ngọc rồng" : "Hộp quà 20-11 VIP";
                update(con, "insert into season_event_top (id, event_id, name) select " + t[0] + ", 8, '" + name + "' from dual where not exists (select 1 from season_event_top where id = " + t[0] + ")");
                update(con, "insert into season_event_top_reward (top_id, rank_from, rank_to, item_id, qty, hsd_days, min_point) select " + t[0]
                        + ", rank_from, rank_to, item_id, qty, hsd_days, min_point from season_event_top_reward where top_id = 801 and not exists (select 1 from season_event_top_reward where top_id = " + t[0] + ")");
            }
            update(con, "update season_event_exchange set top_id = 0, top_point = 0 where top_id = 801"); // điểm top Hộp quà tính khi mở hộp
            update(con, "update season_event_top set name = 'Hộp quà 20-11' where id = 801");
            update(con, "insert into server_migration (name) values ('tops_2011')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'tops_2011b'")) {
            // bản trước tạo top 804 và xoá 801 (Dot48 lại tạo lại 801): dùng 801 cho Hộp quà 20-11, bỏ 804
            update(con, "update season_event_top set name = 'Hộp quà 20-11' where id = 801");
            update(con, "delete from season_event_top_reward where top_id = 804");
            update(con, "delete from season_event_top where id = 804");
            update(con, "delete from season_event_point where top_id = 804");
            update(con, "insert into server_migration (name) values ('tops_2011b')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1119v'")) {
            update(con, "insert into item_option_template (id, NAME) select 275, 'Cool: +#% sức đánh bản thân và đồng minh' from dual where not exists (select 1 from item_option_template where id = 275)");
            update(con, "delete from item_template_option where template_id in (1795, 1963, 1971, 1976, 1941, 1943, 710, 711) and option_id in (30, 6, 267, 154, 159, 275)");
            writeStats(con, EVENT_ITEMS23);
            update(con, "insert into server_migration (name) values ('event_items_1119v')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_2011_on'")) {
            // bật sự kiện 20/11 (tự chạy 15/11 - 22/11); Halloween hết lịch 19/11 nên bật tay để chạy song song (admin chưa đặt thì mới bật)
            update(con, "update season_event set enabled = 1 where id = 8");
            update(con, "create table if not exists season_event_force (event_id int primary key, force_on tinyint not null)");
            update(con, "insert into season_event_force (event_id, force_on) select 7, 1 from dual where not exists (select 1 from season_event_force where event_id = 7)");
            update(con, "insert into server_migration (name) values ('event_2011_on')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1119u'")) {
            update(con, "delete from item_template_option where template_id in (905, 907, 1957, 1798, 1144, 1571, 1632, 1953) and option_id in (154, 30, 6, 84, 85)");
            writeStats(con, EVENT_ITEMS22);
            update(con, "insert into server_migration (name) values ('event_items_1119u')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1119t'")) {
            writeStats(con, EVENT_ITEMS21);
            // CT Giáo sư Bunma 1937 (ảnh): chỉ số đã đúng, thiếu dòng không giao dịch
            update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) select 1937, 30, 0, 0, 100, 0 from dual where not exists (select 1 from item_template_option where template_id = 1937 and option_id = 30)");
            update(con, "insert into server_migration (name) values ('event_items_1119t')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1119s'")) {
            update(con, "delete from item_template_option where template_id in (633, 634) and option_id in (150, 154)");
            writeStats(con, EVENT_ITEMS20);
            update(con, "insert into server_migration (name) values ('event_items_1119s')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1119r'")) {
            update(con, "delete from item_template_option where template_id = 911 and option_id = 154");
            writeStats(con, EVENT_ITEMS19);
            update(con, "insert into server_migration (name) values ('event_items_1119r')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1119q'")) {
            // dòng không phải chỉ số (150, 154, 30, 161, 165, 84, 85, 106) xoá trước để không trùng
            update(con, "delete from item_template_option where template_id in (583, 581, 582, 719, 730, 746, 745) and option_id in (150, 154, 30, 161, 165, 84, 85, 106)");
            writeStats(con, EVENT_ITEMS18);
            update(con, "insert into server_migration (name) values ('event_items_1119q')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1119p'")) {
            writeStats(con, EVENT_ITEMS17);
            update(con, "delete from item_template_option where template_id between 635 and 637 and option_id in (151, 152) and id not in (select * from (select max(id) from item_template_option where template_id between 635 and 637 and option_id in (151, 152) group by template_id, option_id) t)");
            for (int id = 635; id <= 637; id++) {
                update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) select " + id + ", 30, 0, 0, 100, 0 from dual where not exists (select 1 from item_template_option where template_id = " + id + " and option_id = 30)");
            }
            update(con, "insert into server_migration (name) values ('event_items_1119p')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1119o'")) {
            writeStats(con, EVENT_ITEMS16);
            update(con, "insert into server_migration (name) values ('event_items_1119o')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1116e'")) {
            writeStats(con, EVENT_ITEMS5);
            update(con, "insert into server_migration (name) values ('event_items_1116e')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'event_items_1116d'")) {
            writeStats(con, EVENT_ITEMS4);
            update(con, "insert into server_migration (name) values ('event_items_1116d')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'stats_anh_1124b'")) { // chỉ số theo ảnh bản chính thức (cờ ngọc rồng, Bunma, Bó Hoa Vàng, Goku giỗ tổ / GT Kid, Cánh hào khí)
            writeStats(con, new int[][][]{
                {{955}, {50, 12}, {77, 11}, {103, 11}}, // Bó Hoa Vàng
                {{1208}, {50, 22}, {77, 23}, {103, 23}, {14, 10}, {108, 5}, {148, 20}, {154, 0}}, // Bunma tóc xanh neon
                {{1579}, {50, 19}, {77, 19}, {103, 19}, {14, 10}, {5, 10}}, // Cờ ngọc rồng 1 sao
                {{1580}, {50, 18}, {77, 18}, {103, 18}, {108, 10}, {94, 10}}, // 2 sao
                {{1581}, {50, 17}, {77, 17}, {103, 17}, {108, 5}, {94, 5}}, // 3 sao
                {{1582}, {50, 16}, {77, 16}, {103, 16}, {14, 5}, {5, 5}}, // 4 sao
                {{1583}, {50, 15}, {77, 15}, {103, 15}, {14, 5}, {108, 5}}, // 5 sao
                {{1584}, {50, 13}, {77, 13}, {103, 13}, {108, 5}}, // 6 sao
                {{1585}, {50, 12}, {77, 12}, {103, 12}, {14, 5}}, // 7 sao
                {{1586}, {50, 22}, {77, 22}, {103, 22}, {108, 10}, {94, 12}, {14, 10}, {5, 15}}, // Cờ ngọc rồng Super
                {{1996}, {103, 35}, {7, 10000, 35000}, {108, 21}}, // Cải trang Goku giỗ tổ: KI +10k - 35k ngẫu nhiên
                {{1955}, {77, 35}, {191, 22}, {6, 10000, 35000}}, // Cải trang Goku GT Kid: HP +10k - 35k ngẫu nhiên
                {{1998}, {103, 40}, {7, 20000, 40000}, {268, 10}}}); // Cánh hào khí (đổi 5k - 40k ở canh_hao_khi_5k)
            update(con, "insert into server_migration (name) values ('stats_anh_1124b')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'canh_hao_khi_5k'")) { // Cánh hào khí: KI +5k - 40k ngẫu nhiên
            update(con, "update item_template_option set param = 5000, param_max = 40000 where template_id = 1998 and option_id = 7");
            update(con, "insert into server_migration (name) values ('canh_hao_khi_5k')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'stats_anh_1124c'")) { // pet Baby (Rabbit / Secret / Panda / Dino), Ván bay Rồng Thiêng VIP, Pet Capybara hồng, Pet Shimo, Goku bay theo ảnh
            writeStats(con, new int[][][]{
                {{1792}, {77, 18}, {94, 5}, {108, 7}}, // Pet Rabbit
                {{1791}, {77, 22}, {50, 22}, {94, 8}, {5, 11}, {14, 8}}, // Pet Secret
                {{1794}, {50, 16}, {77, 16}, {103, 16}, {236, 20}}, // Pet Panda
                {{1793}, {77, 18}, {5, 7}, {14, 5}}, // Pet Dino
                {{1828}, {50, 14}, {77, 10}, {103, 10}, {5, 8}, {14, 5}, {94, 8}}, // Ván bay Rồng Thiêng VIP
                {{1668}, {50, 15}, {103, 17}, {77, 15}, {83, 0}}, // Pet Capybara hồng
                {{1568}, {50, 16}, {77, 18}, {103, 18}, {94, 7}, {14, 5}}, // Pet Shimo
                {{1572}, {50, 15}, {77, 15}, {103, 15}}}); // Goku bay
            update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) select 1828, 85, 0, 0, 100, 0 from dual where not exists (select 1 from item_template_option where template_id = 1828 and option_id = 85)");
            update(con, "insert into server_migration (name) values ('stats_anh_1124c')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'phao_hoa_nro_kgd'")) { // Pháo hoa NRO (cờ): bỏ chí mạng / SĐ chí mạng, thay bằng không giao dịch
            update(con, "delete from item_template_option where template_id = 1965 and option_id in (5, 14, 30)");
            update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) values (1965, 30, 0, 0, 100, 0)");
            update(con, "insert into server_migration (name) values ('phao_hoa_nro_kgd')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'co_bong_tuyet_codinh'")) { // Cờ bông tuyết: cố định giảm 11% sát thương, 21% né đòn
            update(con, "update item_template_option set param = 11, param_max = 11 where template_id = 1951 and option_id = 94");
            update(con, "update item_template_option set param = 21, param_max = 21 where template_id = 1951 and option_id = 108");
            update(con, "insert into server_migration (name) values ('co_bong_tuyet_codinh')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'stats_khac_nhau'")) {
            variedStats(con);
            update(con, "insert into server_migration (name) values ('stats_khac_nhau')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'stats_khac_nhau2'")) { // dòng phụ đủ loại: chí mạng, SĐ chí mạng, né, giáp...
            regenVaried(con);
            update(con, "insert into server_migration (name) values ('stats_khac_nhau2')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'stats_khac_nhau_deo_lung'")) { // đeo lưng đang trùng chỉ số tạm
            update(con, "create table if not exists item_stat_varied (template_id int primary key)");
            java.util.Set<Integer> ids = new java.util.TreeSet<>();
            String q = "select i.id, (select group_concat(concat(t.option_id, '=', t.param, if(t.param_max <> t.param, concat('..', t.param_max), '')) order by t.option_id)"
                    + " from item_template_option t where t.template_id = i.id and t.option_id in (" + STAT_IDS + ")) sig from item_template i where i.TYPE = 11 and i.NAME <> 'Vật phẩm đã gỡ'";
            java.util.Set<String> temp = java.util.Set.of("50=8,77=8,103=8", "50=6..9,77=6..9,103=6..9", "50=8..12,77=8..12,103=8..12");
            try (java.sql.PreparedStatement ps = con.prepareStatement(q); java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (temp.contains(String.valueOf(rs.getString(2)))) {
                        ids.add(rs.getInt(1));
                    }
                }
            }
            for (int id : ids) {
                update(con, "insert ignore into item_stat_varied (template_id) values (" + id + ")");
            }
            regenVaried(con, ids);
            update(con, "insert into server_migration (name) values ('stats_khac_nhau_deo_lung')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'stats_khac_nhau3'")) { // bỏ Tấn công+# / HP+# / KI+#, 2 - 4 dòng phụ
            regenVaried(con);
            update(con, "insert into server_migration (name) values ('stats_khac_nhau3')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'doi_qua_han_hoac_vv'")) {
            // đổi quà / cửa hàng sự kiện: mọi cải trang / pet / ván bay / đeo lưng đều "Hạn sử dụng hoặc vĩnh viễn" (30 ngày, 10% vĩnh viễn)
            String rare = " and x.reward_item in (select id from item_template where TYPE in (5, 23, 24) or (TYPE in (11, 27) and part >= 0))";
            update(con, "update season_event_exchange x set x.reward_days = '30', x.perm_rate = 10 where (x.reward_days is null or trim(x.reward_days) = '' or x.reward_days = '0')" + rare);
            update(con, "update season_event_exchange x set x.perm_rate = 10 where x.reward_days = '30' and x.perm_rate = 0" + rare);
            update(con, "insert into server_migration (name) values ('doi_qua_han_hoac_vv')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'doi_diem_quy_lao_han'")) { // đổi điểm Quy Lão: CT / pet / ván / đeo lưng 7 - 15 - 30 - 45 ngày, 2% vĩnh viễn
            update(con, "update season_event_exchange x set x.reward_days = '7,15,30,45', x.perm_rate = 2 where x.npc = '13' and replace(x.grp, '\\n', ' ') like 'Đổi%điểm'"
                    + " and x.reward_item in (select id from item_template where TYPE in (5, 23, 24) or (TYPE in (11, 27) and part >= 0))");
            update(con, "insert into server_migration (name) values ('doi_diem_quy_lao_han')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'doi_diem_quy_lao_han2'")) {
            // lần trước lọc nhóm bằng SQL LIKE 'Đổi%điểm': dữ liệu VPS có thể lưu chữ tiếng Việt / xuống dòng khác nên không khớp -> so trong Java sau khi chuẩn hoá
            java.util.List<Integer> ids = new java.util.ArrayList<>();
            try (java.sql.PreparedStatement ps = con.prepareStatement("select x.id, x.npc, x.grp from season_event_exchange x where x.reward_item in"
                    + " (select id from item_template where TYPE in (5, 23, 24) or (TYPE in (11, 27) and part >= 0))"); java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String grp = java.text.Normalizer.normalize(String.valueOf(rs.getString(3)), java.text.Normalizer.Form.NFC).replaceAll("\\s+", "").toLowerCase();
                    if ("13".equals(String.valueOf(rs.getString(2)).trim()) && grp.equals("đổiđiểm")) {
                        ids.add(rs.getInt(1));
                    }
                }
            }
            for (int id : ids) {
                update(con, "update season_event_exchange set reward_days = '7,15,30,45', perm_rate = 2 where id = " + id);
            }
            nro.models.utils.Logger.success("Đổi điểm Quy Lão: " + ids.size() + " món hạn 7 - 15 - 30 - 45 ngày, 2% vĩnh viễn\n");
            update(con, "insert into server_migration (name) values ('doi_diem_quy_lao_han2')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'vip_mua_1_ngay_2511'")) {
            // Mùa VIP mới chỉ trong ngày 25/11/2026; VIP 4 theo ảnh, VIP 1 - 3 nhỏ hơn VIP 4 (panel cho tự đặt, không hơn VIP 4)
            update(con, "create table if not exists vip_season (id int primary key, data text not null)");
            com.google.gson.Gson gson = new com.google.gson.Gson();
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            try (java.sql.PreparedStatement ps = con.prepareStatement("select data from vip_season where id = 1"); java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    m.putAll(gson.fromJson(rs.getString(1), java.util.Map.class));
                }
            }
            m.putIfAbsent("prices", new int[]{50000, 100000, 150000, 200000});
            m.putIfAbsent("max", 4);
            m.put("start", "2026-11-25T00:00");
            m.put("end", "2026-11-25T23:59:59");
            int[] da = {1075, 1076, 1077, 1078, 1079, 1080, 1081, 1082, 1083}; // 1074 + 9 = đá nâng cấp / đá may mắn cấp 1 - 5
            // {thỏi vàng, rương mảnh thiên sứ, mảnh khỉ Oozaru, đá ngẫu nhiên, đá bảo vệ khoá, sách cũ, capsule 1 món kích hoạt,
            //  CT Cađíc SSJ Blue, balo Capybara hồng, pet Capybara hồng, rađa ngọc rồng VIP, CT Goku SSJ Blue, phiếu đổi capsule}
            int[] ids = {457, 1173, 1771, 1074, 1143, 1283, 1559, 1746, 1670, 1668, 1809, 1590, 1690};
            int[][] qty = {
                {20, 200, 2, 3, 10, 10, 0, 0, 0, 0, 4, 0, 50},
                {40, 400, 4, 6, 20, 20, 1, 0, 0, 0, 8, 0, 100},
                {70, 700, 7, 10, 35, 35, 1, 0, 1, 1, 14, 0, 175},
                {99, 1000, 10, 15, 50, 50, 1, 1, 1, 1, 20, 1, 250}};
            java.util.List<Object> rewards = new java.util.ArrayList<>();
            for (int[] q : qty) {
                java.util.List<Object> items = new java.util.ArrayList<>();
                for (int i = 0; i < ids.length; i++) {
                    if (q[i] <= 0) {
                        continue;
                    }
                    java.util.Map<String, Object> it = new java.util.LinkedHashMap<>();
                    it.put("id", ids[i]);
                    it.put("qty", q[i]);
                    it.put("opts", new int[0][]);
                    if (ids[i] == 1074) {
                        it.put("alt", da);
                        it.put("label", "Ngẫu nhiên đá may mắn hoặc đá nâng cấp");
                    }
                    items.add(it);
                }
                rewards.add(java.util.Map.of("pet", true, "items", items));
            }
            m.put("rewards", rewards);
            try (java.sql.PreparedStatement ps = con.prepareStatement("replace into vip_season (id, data) values (1, ?)")) {
                ps.setString(1, gson.toJson(m));
                ps.executeUpdate();
            }
            update(con, "insert into server_migration (name) values ('vip_mua_1_ngay_2511')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'do_mac_khong_gop'")) {
            // CT Cađíc (1741-1746), Diều rồng băng, Ván bay Cây Thông, pet... bị bật gộp (is_up_to_up = 1) nên nhận 2 cái thành 1 ô x2
            update(con, "update item_template set is_up_to_up = 0 where is_up_to_up = 1 and (TYPE in (5, 11, 23, 24) or (TYPE = 27 and head >= 0))");
            update(con, "insert into server_migration (name) values ('do_mac_khong_gop')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'cadic_ssj_blue_nhu_goku'")) {
            // CT Cađíc SSJ Blue (1746) chưa có chỉ số (ra chỉ số tạm SĐ / HP 30%): giống CT Goku SSJ Blue (1590)
            writeStats(con, new int[][][]{{{1746}, {50, 27}, {77, 27}, {103, 27}, {210, 4}, {106, 0}}});
            update(con, "insert into server_migration (name) values ('cadic_ssj_blue_nhu_goku')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'ct_ssj_blue_chong_lanh_20'")) {
            // CT Goku SSJ Blue / SSJ God, CT Cađíc SSJ Blue / SSJ4: dòng "Không ảnh hưởng bởi cái lạnh" chỉ 20% (trước 100%)
            update(con, "update item_template_option set chance = 20 where option_id = 106 and template_id in (1590, 1746)");
            update(con, "insert into server_migration (name) values ('ct_ssj_blue_chong_lanh_20')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'ct_god_chong_lanh_100'")) {
            // CT Goku SSJ God (1593) luôn có Chống lạnh (chỉ Goku / Cađíc SSJ Blue 20%), shop hiện lại dòng
            update(con, "update item_template_option set chance = 100 where template_id = 1593 and option_id = 106");
            update(con, "insert into item_shop_option (item_shop_id, option_id, param) select s.id, 106, 0 from item_shop s where s.temp_id = 1593 "
                    + "and not exists (select 1 from item_shop_option x where x.item_shop_id = s.id and x.option_id = 106)");
            update(con, "insert into server_migration (name) values ('ct_god_chong_lanh_100')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'ct_ssj4_chong_lanh_theo_anh'")) {
            // theo ảnh: CT Cađíc SSJ4 luôn có Chống lạnh (không theo 20% của dòng SSJ Blue), shop hiện lại dòng
            update(con, "update item_template_option set chance = 100 where template_id = 1693 and option_id = 106");
            update(con, "insert into item_shop_option (item_shop_id, option_id, param) select s.id, 106, 0 from item_shop s where s.temp_id = 1693 "
                    + "and not exists (select 1 from item_shop_option x where x.item_shop_id = s.id and x.option_id = 106)");
            // CT Goku SSJ4 (1553) chưa có chỉ số mặc định (quà / hộp ra trống): SĐ 20, HP 17, KI 17, +10% SĐ chí mạng, Chống lạnh
            if (!exists(con, "select 1 from item_template_option where template_id = 1553")) {
                for (int[] o : new int[][]{{50, 20}, {77, 17}, {103, 17}, {5, 10}, {106, 0}}) {
                    update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) values (1553, " + o[0] + ", " + o[1] + ", " + o[1] + ", 100, 0)");
                }
            }
            update(con, "insert into server_migration (name) values ('ct_ssj4_chong_lanh_theo_anh')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'ct_ssj_blue_shop_an_chong_lanh'")) {
            // shop không hiện dòng chống lạnh ở xem trước (có hiện là chắc chắn ra); mua xong tung 20%
            update(con, "delete from item_shop_option where option_id = 106 and item_shop_id in (select id from item_shop where temp_id in (1590, 1746))");
            update(con, "insert into server_migration (name) values ('ct_ssj_blue_shop_an_chong_lanh')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'nap_bu_diem_su_kien'")
                && exists(con, "select 1 from information_schema.tables where table_schema = database() and table_name = 'web_topup'")) {
            // nạp web: 1 ngọc = 1 điểm sự kiện. ep_given = 0: lệnh cũ chưa cộng điểm, bù khi nhân vật đăng nhập (WebServer.claimPendingGem).
            // Lệnh duyệt sau khi có tính năng (từ 25/11/2026 11:50) đã được cộng điểm lúc duyệt.
            if (!exists(con, "select 1 from information_schema.columns where table_schema = database() and table_name = 'web_topup' and column_name = 'ep_given'")) {
                update(con, "alter table web_topup add ep_given tinyint not null default 0");
            }
            update(con, "update web_topup set ep_given = 1 where status <> 1 or updated_at >= '2026-11-25 11:50:00'");
            update(con, "insert into server_migration (name) values ('nap_bu_diem_su_kien')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'vip_gia_diem_su_kien'")) {
            // VIP mua bằng điểm sự kiện (chung điểm Quy Lão): VIP 1 - 4 = 500 / 800 / 2000 / 8000 điểm
            com.google.gson.Gson gson = new com.google.gson.Gson();
            java.util.Map<String, Object> m = null;
            try (java.sql.PreparedStatement ps = con.prepareStatement("select data from vip_season where id = 1"); java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    m = new java.util.LinkedHashMap<>(gson.fromJson(rs.getString(1), java.util.Map.class));
                }
            }
            if (m != null) {
                m.put("prices", new int[]{500, 800, 2000, 8000});
                try (java.sql.PreparedStatement ps = con.prepareStatement("update vip_season set data = ? where id = 1")) {
                    ps.setString(1, gson.toJson(m));
                    ps.executeUpdate();
                }
            }
            update(con, "insert into server_migration (name) values ('vip_gia_diem_su_kien')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'trung_ve_sau_xen'")) {
            // Trứng ve sầu (2045) mở ra Pet Xên con / hoàn thiện / MAX (chỉ số ngẫu nhiên theo item_template_option),
            // hạn 7 - 15 - 30 ngày, 1% vĩnh viễn; ra 119 con có hạn cùng loại thì con thứ 120 vĩnh viễn (perm_pity)
            if (!exists(con, "select 1 from information_schema.columns where table_schema = database() and table_name = 'item_box_reward' and column_name = 'perm_pity'")) {
                update(con, "alter table item_box_reward add perm_pity int not null default 0");
            }
            update(con, "create table if not exists box_perm_pity (player_id bigint not null, box_id int not null, item_id int not null, cnt int not null default 0, primary key (player_id, box_id, item_id))");
            update(con, "delete from item_box_reward where box_id = 2045");
            for (int pet : new int[]{2037, 2036, 2038}) {
                update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity, perm_pity) values (2045, " + pet + ", 1, 1, 100, '7,15,30', 1, 0, 120)");
            }
            update(con, "insert into server_migration (name) values ('trung_ve_sau_xen')");
        }
        if (!exists(con, "select 1 from server_migration where name = 'nappa_aura_vv_01'")) {
            // % ra vĩnh viễn của hộp quà nhận số lẻ (0,1%); CT Nappa Aura (2075) ở các hộp có hạn: 0,1% vĩnh viễn
            update(con, "alter table item_box_reward modify perm_rate decimal(6,2) not null default 0");
            update(con, "update item_box_reward set perm_rate = 0.1 where item_id = 2075 and hsd_days <> ''");
            update(con, "insert into server_migration (name) values ('nappa_aura_vv_01')");
        }
    }

    /**
     * Xoá hẳn các mã vật phẩm cuối bảng không còn dùng (2121 / 2122 Đại bàng đất sét nổ, 2124 - 2127 thú cưỡi đã xoá, 2128 - 2130 cải trang
     * đệ tử tự tạo): danh sách vật phẩm tra theo vị trí nên dồn 2 món còn dùng lên: Thú cưỡi Kỳ Lân 2123 -> 2121, Con dấu VIP 2131 -> 2122.
     * Đổi mã trong mọi bảng (cửa hàng, hộp quà, sự kiện, ký gửi) và trong đồ của người chơi (trang bị, hành trang, rương, đệ tử);
     * đồ Đại bàng cũ thành ô trống, cải trang đệ tử 2128 / 2129 / 2130 thành 548 / 547 / 1302. Chạy 1 lần.
     */
    private static void compactTail(Connection con) throws Exception {
        if (exists(con, "select 1 from server_migration where name = 'compact_2122'")) {
            return;
        }
        java.util.Map<Integer, Integer> map = new java.util.LinkedHashMap<>();
        map.put(2121, -1);
        map.put(2122, -1);
        for (int i = 2124; i <= 2127; i++) {
            map.put(i, -1);
        }
        map.put(2128, 548);
        map.put(2129, 547);
        map.put(2130, 1302);
        map.put(2123, 2121);
        map.put(2131, 2122);
        // bảng vật phẩm
        update(con, "delete from item_template_option where template_id in (2121, 2122, 2124, 2125, 2126, 2127, 2128, 2129, 2130)");
        update(con, "delete from item_template where id in (2121, 2122)");
        update(con, "update item_template set id = 2121 where id = 2123");
        update(con, "update item_template_option set template_id = 2121 where template_id = 2123");
        update(con, "update item_template set id = 2122 where id = 2131");
        update(con, "update item_template_option set template_id = 2122 where template_id = 2131");
        update(con, "delete from item_template where id > 2122");
        // các bảng tham chiếu mã vật phẩm: món đã bỏ thì xoá dòng, món dồn lên thì đổi mã
        String[][] refs = {{"item_shop", "temp_id"}, {"item_box_reward", "item_id"}, {"item_box_reward", "box_id"}, {"season_event_exchange", "reward_item"},
            {"season_event_exchange", "bonus_item"}, {"season_event_drop", "item_id"}, {"season_event_drop", "item_to"}, {"season_event_drop", "need_item"},
            {"season_event_reward_item", "item_id"}, {"boss_reward", "item_id"}, {"shop_ky_gui", "item_id"}};
        for (String[] r : refs) {
            if (!exists(con, "select 1 from information_schema.columns where table_schema = database() and table_name = '" + r[0] + "' and column_name = '" + r[1] + "'")) {
                continue;
            }
            for (java.util.Map.Entry<Integer, Integer> e : map.entrySet()) {
                int to = e.getValue() == -1 ? -1 : e.getValue();
                if (to == -1 || (e.getKey() >= 2128 && e.getKey() <= 2130)) {
                    if (r[1].equals("item_id") || r[1].equals("temp_id") || r[1].equals("reward_item")) {
                        update(con, "delete from " + r[0] + " where " + r[1] + " = " + e.getKey());
                    } else {
                        update(con, "update " + r[0] + " set " + r[1] + " = 0 where " + r[1] + " = " + e.getKey());
                    }
                } else {
                    update(con, "update " + r[0] + " set " + r[1] + " = " + to + " where " + r[1] + " = " + e.getKey());
                }
            }
        }
        // đồ người chơi: mỗi món lưu dạng "[mã,số lượng,...]" (trong chuỗi JSON, có thể lồng trong dữ liệu đệ tử)
        java.util.regex.Pattern pat = java.util.regex.Pattern.compile("(?<=[\"\\\\])\\[(21(?:2[1-9]|3[01])),");
        String[] cols = {"items_body", "items_bag", "items_box", "items_box_lucky_round", "items_daban", "pet"};
        int changed = 0;
        try (java.sql.Statement st = con.createStatement(java.sql.ResultSet.TYPE_FORWARD_ONLY, java.sql.ResultSet.CONCUR_READ_ONLY);
                java.sql.ResultSet rs = st.executeQuery("select id, " + String.join(", ", cols) + " from player")) {
            while (rs.next()) {
                StringBuilder set = new StringBuilder();
                java.util.List<String> vals = new java.util.ArrayList<>();
                for (String c : cols) {
                    String v = rs.getString(c);
                    if (v == null) {
                        continue;
                    }
                    java.util.regex.Matcher m = pat.matcher(v);
                    StringBuilder out = new StringBuilder();
                    boolean any = false;
                    while (m.find()) {
                        any = true;
                        m.appendReplacement(out, "[" + map.getOrDefault(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(1))) + ",");
                    }
                    if (any) {
                        m.appendTail(out);
                        set.append(set.length() > 0 ? ", " : "").append(c).append(" = ?");
                        vals.add(out.toString());
                    }
                }
                if (!vals.isEmpty()) {
                    try (java.sql.PreparedStatement ps = con.prepareStatement("update player set " + set + " where id = ?")) {
                        for (int i = 0; i < vals.size(); i++) {
                            ps.setString(i + 1, vals.get(i));
                        }
                        ps.setLong(vals.size() + 1, rs.getLong("id"));
                        changed += ps.executeUpdate();
                    }
                }
            }
        }
        update(con, "insert into server_migration (name) values ('compact_2122')");
        nro.models.utils.Logger.success("Dồn mã vật phẩm cuối bảng: xoá 2123 - 2131, Kỳ Lân -> 2121, Con dấu VIP -> 2122 (" + changed + " nhân vật đổi mã đồ)\n");
    }

    /**
     * Cải trang / pet / ván bay đang dùng chung chỉ số tạm (CT 15/15/15, CT 12-18 + chí mạng, pet 10/10/10, ván 5/5/5...):
     * mỗi món một bộ chỉ số riêng (cố định, sinh theo mã món nên chạy lại vẫn ra như nhau), không trùng với món nào khác.
     * Danh sách món đổi lưu ở item_stat_varied để đồ cũ của người chơi lấy chỉ số mới khi đăng nhập (ItemService.refreshTitle).
     */
    static void variedStats(Connection con) throws Exception {
        update(con, "create table if not exists item_stat_varied (template_id int primary key)");
        String sigSql = "select i.id, i.type, (select group_concat(concat(t.option_id, '=', t.param, if(t.param_max <> t.param, concat('..', t.param_max), '')) order by t.option_id)"
                + " from item_template_option t where t.template_id = i.id and t.option_id in (" + STAT_IDS + ")) sig from item_template i where i.NAME <> 'Vật phẩm đã gỡ'";
        java.util.Set<String> used = new java.util.HashSet<>();
        java.util.List<int[]> todo = new java.util.ArrayList<>();
        java.util.Map<Integer, java.util.Set<String>> temp = java.util.Map.of(
                5, java.util.Set.of("50=15,77=15,103=15", "14=3..5,50=12..18,77=12..18,103=12..18"),
                27, java.util.Set.of("50=10,77=10,103=10"),
                23, java.util.Set.of("50=5,77=5,103=5", "50=5..7,77=5..7,103=5..7"),
                24, java.util.Set.of("50=5,77=5,103=5"));
        try (java.sql.PreparedStatement ps = con.prepareStatement(sigSql); java.sql.ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String sig = rs.getString(3);
                int type = rs.getInt(2);
                if (sig != null && temp.containsKey(type) && temp.get(type).contains(sig)) {
                    todo.add(new int[]{rs.getInt(1), type});
                } else if (sig != null) {
                    used.add(sig);
                }
            }
        }
        java.util.List<int[][]> items = new java.util.ArrayList<>();
        for (int[] t : todo) {
            java.util.Random r = new java.util.Random(t[0] * 7919L + 17);
            int[][] st;
            String sig;
            do {
                st = makeStats(r, t[1]);
                java.util.TreeMap<Integer, Integer> m = new java.util.TreeMap<>();
                for (int[] o : st) {
                    m.put(o[0], o[1]);
                }
                StringBuilder b = new StringBuilder();
                m.forEach((k, v) -> b.append(b.length() == 0 ? "" : ",").append(k).append('=').append(v));
                sig = b.toString();
            } while (!used.add(sig));
            int[][] row = new int[st.length + 1][];
            row[0] = new int[]{t[0]};
            System.arraycopy(st, 0, row, 1, st.length);
            items.add(row);
            update(con, "insert ignore into item_stat_varied (template_id) values (" + t[0] + ")");
        }
        writeStats(con, items.toArray(new int[0][][]));
        nro.models.utils.Logger.success("Chỉ số riêng cho " + items.size() + " cải trang / pet / ván bay đang trùng chỉ số tạm\n");
    }

    /** Dòng phụ cho cải trang (mức pet = 60%, ván bay = 40%): {option, min, max}. */
    private static final int[][] EXTRA_POOL = {
        {14, 3, 12}, {5, 5, 20}, {108, 5, 20}, {191, 3, 15}, {94, 3, 15}, {97, 3, 12}, {47, 500, 15000},
        {80, 3, 15}, {81, 3, 12}, {95, 3, 12}, {104, 5, 15}, {10, 1, 8}, {204, 3, 15},
        {19, 5, 20}, {148, 10, 50}, {101, 5, 30}, {236, 5, 30}, {268, 5, 20}};

    /** Làm lại chỉ số các món ở item_stat_varied: SĐ / HP / KI % + 2 - 4 dòng phụ (không dùng Tấn công+# / HP+# / KI+#), không trùng nhau. */
    static void regenVaried(Connection con) throws Exception {
        regenVaried(con, null);
    }

    /** only: chỉ làm lại các mã này (null = mọi món ở item_stat_varied). */
    static void regenVaried(Connection con, java.util.Set<Integer> only) throws Exception {
        String sigSql = "select i.id, (select group_concat(concat(t.option_id, '=', t.param, if(t.param_max <> t.param, concat('..', t.param_max), '')) order by t.option_id)"
                + " from item_template_option t where t.template_id = i.id and t.option_id in (" + STAT_IDS + ")) sig from item_template i"
                + " where i.id not in (select template_id from item_stat_varied)" + (only == null ? "" : " or i.id not in (" + (only.isEmpty() ? "-1" : only.toString().replaceAll("[\\[\\] ]", "")) + ")");
        java.util.Set<String> used = new java.util.HashSet<>();
        try (java.sql.PreparedStatement ps = con.prepareStatement(sigSql); java.sql.ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if (rs.getString(2) != null) {
                    used.add(rs.getString(2));
                }
            }
        }
        java.util.List<int[]> todo = new java.util.ArrayList<>();
        try (java.sql.PreparedStatement ps = con.prepareStatement("select i.id, i.TYPE from item_template i join item_stat_varied v on v.template_id = i.id order by i.id");
                java.sql.ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if (only == null || only.contains(rs.getInt(1))) {
                    todo.add(new int[]{rs.getInt(1), rs.getInt(2)});
                }
            }
        }
        java.util.List<int[][]> items = new java.util.ArrayList<>();
        for (int[] t : todo) {
            java.util.Random r = new java.util.Random(t[0] * 104729L + 31);
            boolean mid = t[1] == 27 || t[1] == 11; // pet / đeo lưng
            int lo = t[1] == 5 ? 12 : mid ? 8 : 3, hi = t[1] == 5 ? 24 : mid ? 18 : 10;
            int scale = t[1] == 5 ? 100 : mid ? 60 : 40;
            int[][] st;
            String sig;
            do {
                java.util.TreeMap<Integer, Integer> m = new java.util.TreeMap<>();
                m.put(50, lo + r.nextInt(hi - lo + 1));
                m.put(77, lo + r.nextInt(hi - lo + 1));
                m.put(103, lo + r.nextInt(hi - lo + 1));
                java.util.List<int[]> pool = new java.util.ArrayList<>(java.util.Arrays.asList(EXTRA_POOL));
                java.util.Collections.shuffle(pool, r);
                int n = 2 + r.nextInt(3); // 2 - 4 dòng phụ
                for (int k = 0; k < n; k++) {
                    int[] e = pool.get(k);
                    int mn = Math.max(1, e[1] * scale / 100), mx = Math.max(mn, e[2] * scale / 100);
                    int v = mn + r.nextInt(mx - mn + 1);
                    if (e[2] >= 1000) {
                        v = v / 500 * 500; // số cộng thẳng làm tròn 500
                    }
                    m.put(e[0], Math.max(1, v));
                }
                st = new int[m.size()][];
                int k = 0;
                StringBuilder b = new StringBuilder();
                for (java.util.Map.Entry<Integer, Integer> en : m.entrySet()) {
                    st[k++] = new int[]{en.getKey(), en.getValue()};
                    b.append(b.length() == 0 ? "" : ",").append(en.getKey()).append('=').append(en.getValue());
                }
                sig = b.toString();
            } while (!used.add(sig));
            int[][] row = new int[st.length + 1][];
            row[0] = new int[]{t[0]};
            System.arraycopy(st, 0, row, 1, st.length);
            items.add(row);
            update(con, "delete from item_template_option where template_id = " + t[0] + " and option_id in (0, 6, 7, 19)"); // ngoài nhóm writeStats xoá
        }
        writeStats(con, items.toArray(new int[0][][]));
        nro.models.utils.Logger.success("Làm lại chỉ số đủ loại dòng phụ cho " + items.size() + " cải trang / pet / ván bay\n");
    }

    /** SĐ / HP / KI theo loại món + 1 - 2 dòng phụ (chí mạng, né đòn, giảm sát thương, SĐ chí mạng, tốc độ chạy, vàng rơi). */
    private static int[][] makeStats(java.util.Random r, int type) {
        int lo = type == 5 ? 12 : type == 27 ? 8 : 3, hi = type == 5 ? 24 : type == 27 ? 18 : 10;
        java.util.List<int[]> out = new java.util.ArrayList<>();
        out.add(new int[]{50, lo + r.nextInt(hi - lo + 1)});
        out.add(new int[]{77, lo + r.nextInt(hi - lo + 1)});
        out.add(new int[]{103, lo + r.nextInt(hi - lo + 1)});
        int[][] extra = type == 5 ? new int[][]{{14, 3, 10}, {108, 3, 12}, {94, 3, 10}, {5, 5, 15}, {148, 10, 30}, {268, 5, 20}}
                : type == 27 ? new int[][]{{14, 2, 8}, {108, 2, 8}, {94, 2, 8}, {5, 3, 10}, {268, 5, 15}}
                : new int[][]{{14, 1, 5}, {108, 1, 5}, {94, 1, 5}, {5, 2, 8}};
        int n = type == 5 ? 1 + r.nextInt(2) : r.nextInt(2);
        java.util.List<int[]> pool = new java.util.ArrayList<>(java.util.Arrays.asList(extra));
        java.util.Collections.shuffle(pool, r);
        for (int k = 0; k < n; k++) {
            int[] e = pool.get(k);
            out.add(new int[]{e[0], e[1] + r.nextInt(e[2] - e[1] + 1)});
        }
        return out.toArray(new int[0][]);
    }

    private static void writeStats(Connection con, int[][][] items) throws Exception {
        for (int[][] it : items) {
            int id = it[0][0];
            update(con, "delete from item_template_option where template_id = " + id + " and option_id in (" + STAT_IDS + ")");
            update(con, "delete from item_shop_option where option_id in (" + STAT_IDS + ") and item_shop_id in (select id from item_shop where temp_id = " + id + ")");
            for (int k = 1; k < it.length; k++) {
                int max = it[k].length > 2 ? it[k][2] : it[k][1]; // {opt, min, max}: chỉ số ngẫu nhiên khi tạo món
                update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) values (" + id + ", " + it[k][0] + ", " + it[k][1] + ", " + max + ", 100, 0)");
                update(con, "insert into item_shop_option (item_shop_id, option_id, param) select id, " + it[k][0] + ", " + it[k][1] + " from item_shop where temp_id = " + id);
            }
        }
    }
}
