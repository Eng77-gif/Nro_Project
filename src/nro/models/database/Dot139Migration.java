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
