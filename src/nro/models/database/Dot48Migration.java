package nro.models.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import nro.models.utils.Logger;

/**
 * Đợt 48 (sự kiện theo source V10 + danh hiệu mới): tự thêm dữ liệu vào database khi khởi động server.
 * Chạy nhiều lần không sao: mục nào đã có thì bỏ qua.
 */
public final class Dot48Migration {

    private Dot48Migration() {
    }

    /** {idEffect, idItem, tên, options JSON} */
    private static final Object[][] BADGES = {
        {215, 1286, "Kẻ thao túng sói", "[{\"param\":10,\"id\":50},{\"param\":5,\"id\":5},{\"param\":30,\"id\":93}]"},
        {506, 1904, "Chiến binh sao đen", "[{\"param\":10,\"id\":50},{\"param\":10,\"id\":77},{\"param\":10,\"id\":103},{\"param\":5,\"id\":100},{\"param\":7,\"id\":93}]"},
        {1099, 1673, "Tay nhanh hơn não", "[{\"param\":11,\"id\":50},{\"param\":11,\"id\":77},{\"param\":11,\"id\":103},{\"param\":5,\"id\":117},{\"param\":7,\"id\":93}]"},
        {255, 1765, "Mùa đông không lạnh", "[{\"param\":5,\"id\":108},{\"param\":12,\"id\":77},{\"param\":12,\"id\":103},{\"param\":7,\"id\":93}]"},};

    /** {itemId, part (hiệu ứng), mô tả} */
    private static final Object[][] TITLE_ITEMS = {
        {1904, 506, "Thắng ngọc rồng 1 sao đen tại khu 1 vào ngày chủ nhật"},
        {1673, 1099, "Top 1 - 100 đua top Túi mù Halloween"},
        {1765, 255, "Top 1 - 100 đua top Hộp quà Giáng sinh"},};

    /** season_event_exchange: {id, event, grp, name, need, gold, gem, reward, qty, days} */
    private static final Object[][] EXCHANGES = {
        {202, 8, "Cửa\\nhàng", "Kéo tỉa\\nhoa", "", 0, 2, 1387, 1, ""},
        {203, 8, "Cửa\\nhàng", "Kéo tỉa hoa\\nđặc biệt", "", 0, 10, 1382, 1, ""},
        {204, 8, "Đổi\\nquà", "Hộp quà\\n(hoa hồng)", "1530:20", 0, 0, 1739, 1, ""},
};

    public static void run(Connection con) {
        int changed = 0;
        try {
            for (Object[] b : BADGES) {
                if (!exists(con, "select 1 from data_badges where idEffect = " + b[0])) {
                    try (PreparedStatement ps = con.prepareStatement("insert into data_badges (idEffect, idItem, NAME, Options) values (?, ?, ?, ?)")) {
                        ps.setInt(1, (int) b[0]);
                        ps.setInt(2, (int) b[1]);
                        ps.setString(3, (String) b[2]);
                        ps.setString(4, (String) b[3]);
                        changed += ps.executeUpdate();
                    }
                }
            }
            for (Object[] t : TITLE_ITEMS) {
                try (PreparedStatement ps = con.prepareStatement("update item_template set part = ?, description = ? where id = ? and TYPE = 36 and (part <> ? or description <> ?)")) {
                    ps.setInt(1, (int) t[1]);
                    ps.setString(2, (String) t[2]);
                    ps.setInt(3, (int) t[0]);
                    ps.setInt(4, (int) t[1]);
                    ps.setString(5, (String) t[2]);
                    changed += ps.executeUpdate();
                }
            }
            // NPC cây hoa hồng (sự kiện 20/11)
            if (!exists(con, "select 1 from npc_template where id = 111")) {
                changed += update(con, "insert into npc_template (id, NAME, head, body, leg, avatar) values (111, 'Hoa Hồng', 1311, 1312, 1313, 11811)");
            }
            for (int map : new int[]{0, 7, 14, 5, 13}) {
                if (!exists(con, "select 1 from season_event_npc where event_id = 8 and npc_id = 111 and map_id = " + map)) {
                    changed += update(con, "insert into season_event_npc (event_id, npc_id, map_id, x, y) values (8, 111, " + map + ", 0, 0)");
                }
            }
            for (Object[] e : EXCHANGES) {
                if (!exists(con, "select 1 from season_event_exchange where id = " + e[0])) {
                    try (PreparedStatement ps = con.prepareStatement("insert into season_event_exchange (id, event_id, grp, name, need, gold, gem, reward_item, reward_qty, reward_days, npc) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '')")) {
                        ps.setInt(1, (int) e[0]);
                        ps.setInt(2, (int) e[1]);
                        ps.setString(3, ((String) e[2]).replace("\\n", "\n"));
                        ps.setString(4, ((String) e[3]).replace("\\n", "\n"));
                        ps.setString(5, (String) e[4]);
                        ps.setLong(6, ((Number) e[5]).longValue());
                        ps.setInt(7, (int) e[6]);
                        ps.setInt(8, (int) e[7]);
                        ps.setInt(9, (int) e[8]);
                        ps.setString(10, (String) e[9]);
                        changed += ps.executeUpdate();
                    }
                }
            }
            // Halloween: thêm boss Xương khô (-352) và kẹo khi hạ nó
            if (exists(con, "select 1 from season_event where id = 7 and bosses not like '%-352:%'")) {
                changed += update(con, "update season_event set bosses = concat(bosses, if(bosses = '', '', ','), '-352:10') where id = 7");
            }
            if (!exists(con, "select 1 from season_event_drop where event_id = 7 and mobs = '-352'")) {
                changed += update(con, "insert into season_event_drop (event_id, item_id, rate, qty_min, qty_max, source, daily_max, item_to, need_item, mobs, maps) values (7, 901, 10000, 3, 5, 'boss', 0, 0, 0, '-352', '')");
            }
            changed += runDot50(con);
            changed += runDot51(con);
            // Đợt 52: gộp 3 thẻ Oozaru làm 1 (chỉ số và aura của Oozarun 2)
            if (exists(con, "select 1 from radar where id = 2105")) {
                changed += update(con, "update radar r join (select options, aura_id from radar where id = 2105) b set r.options = b.options, r.aura_id = b.aura_id, r.name = 'Thẻ Oozaru' where r.id = 1771");
                changed += update(con, "delete from radar where id in (2104, 2105)");
            }
        } catch (Exception ex) {
            Logger.error("Đợt 48: không cập nhật được database: " + ex.getMessage() + "\n");
        }
        if (changed > 0) {
            Logger.success("Đợt 48: đã thêm sự kiện / danh hiệu mới vào database (" + changed + " dòng)\n");
        }
    }

    // ------------------------------------------------------------------ Đợt 50
    /** {itemId, part (hiệu ứng), mô tả cách nhận} cho các danh hiệu không có nhiệm vụ danh hiệu. */
    private static final Object[][] TITLE_ITEMS_50 = {
        {2081, 302, "Sở hữu đủ 3 Pet Xên vĩnh viễn"},
        {2076, 301, "Top 1 - 3 đua top Hộp bánh Trung Thu"},
        {1885, 300, "Top 4 - 100 đua top Hộp bánh Trung Thu"},
        {1978, 303, "Top 1 - 100 đua top Lì xì Tết"},};

    /** Danh hiệu thêm vào tab Danh hiệu của Santa (tab_shop 44). */
    private static final int[] SHOP_TITLES = {1904, 1673, 1765, 2081, 2076, 1885, 1978};

    private static int runDot50(Connection con) throws Exception {
        int changed = 0;
        // boss Tuần Lộc (Giáng sinh) và Bé Na (Tết)
        if (exists(con, "select 1 from season_event where id = 9 and bosses not like '%-372:%'")) {
            changed += update(con, "update season_event set bosses = concat(bosses, if(bosses = '', '', ','), '-372:10') where id = 9");
        }
        if (exists(con, "select 1 from season_event where id = 1 and bosses not like '%-373:%'")) {
            changed += update(con, "update season_event set bosses = concat(bosses, if(bosses = '', '', ','), '-373:10') where id = 1");
        }
        for (Object[] t : TITLE_ITEMS_50) {
            try (PreparedStatement ps = con.prepareStatement("update item_template set part = ?, description = ? where id = ? and TYPE = 36 and (part <> ? or description <> ?)")) {
                ps.setInt(1, (int) t[1]);
                ps.setString(2, (String) t[2]);
                ps.setInt(3, (int) t[0]);
                ps.setInt(4, (int) t[1]);
                ps.setString(5, (String) t[2]);
                changed += ps.executeUpdate();
            }
        }
        // thêm danh hiệu mới vào tab Danh hiệu (Santa), chỉ số lấy theo data_badges
        for (int itemId : SHOP_TITLES) {
            if (exists(con, "select 1 from item_shop where tab_id = 44 and temp_id = " + itemId)) {
                continue;
            }
            String opts = null;
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("select Options from data_badges where idItem = " + itemId)) {
                if (rs.next()) {
                    opts = rs.getString(1);
                }
            }
            if (opts == null) {
                continue;
            }
            int shopId;
            try (PreparedStatement ps = con.prepareStatement("insert into item_shop (tab_id, temp_id, is_new, is_sell, type_sell, cost, icon_spec) values (44, ?, 1, 1, 1, 2, 0)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, itemId);
                ps.executeUpdate();
                try (ResultSet k = ps.getGeneratedKeys()) {
                    k.next();
                    shopId = k.getInt(1);
                }
            }
            changed++;
            org.json.simple.JSONArray arr = (org.json.simple.JSONArray) org.json.simple.JSONValue.parse(opts);
            if (arr != null) {
                for (Object o : arr) {
                    org.json.simple.JSONObject j = (org.json.simple.JSONObject) o;
                    changed += update(con, "insert into item_shop_option (item_shop_id, option_id, param) values (" + shopId + ", "
                            + Integer.parseInt(String.valueOf(j.get("id"))) + ", " + Integer.parseInt(String.valueOf(j.get("param"))) + ")");
                }
            }
        }
        return changed;
    }

    // ------------------------------------------------------------------ Đợt 51
    /** Quà đổi điểm sự kiện ở Quy Lão (như video): {itemId, điểm, hạn dùng} — dùng cho mọi sự kiện. */
    private static final Object[][] POINT_SHOP = {
        {1757, 99, "30"}, {1840, 99, "30"}, {1887, 149, "30"}, {1709, 500, ""}, {1722, 750, ""}, {1832, 999, ""},
        {1731, 999, ""}, {1724, 999, ""}, {1929, 999, ""}, {1930, 999, ""}, {1698, 499, ""}, {1682, 499, ""},
        {1713, 499, ""}, {1711, 750, ""}, {2025, 499, ""}, {2022, 999, ""}, {2023, 1999, ""}, {1981, 1999, ""},};

    /** Cửa hàng Chi Chi sự kiện Halloween thêm theo video: {itemId, ngọc, hạn dùng} */
    private static final Object[][] HALLOWEEN_SHOP = {
        {1702, 10, ""}, {739, 5000, ""}, {742, 6000, ""}, {814, 100, "30"}, {815, 100, "30"}, {816, 100, "30"},
        {817, 100, "30"}, {915, 300, "30"}, {1110, 499, ""}, {1111, 499, ""},};

    /** Danh hiệu chỉ nhận qua đua top: {topId, itemId, rankFrom, rankTo, hạn dùng} */
    private static final Object[][] TITLE_TOP = {
        {101, 1978, 1, 10, "30"}, {101, 1978, 11, 100, "7"},
        {601, 2076, 1, 3, "30"}, {601, 1885, 4, 100, "15"},
        {702, 1673, 1, 10, "30"}, {702, 1673, 11, 100, "7"},
        {901, 1765, 1, 10, "30"}, {901, 1765, 11, 100, "7"},};

    private static int runDot51(Connection con) throws Exception {
        int changed = 0;
        // bỏ mọi chức năng đổi lấy danh hiệu
        changed += update(con, "delete from season_event_exchange where id in (201, 205, 206)");
        // hạn chót nhận giải đua top (7 ngày sau khi chốt)
        if (!exists(con, "select 1 from information_schema.columns where table_schema = database() and table_name = 'season_event_reward_pending' and column_name = 'created_at'")) {
            changed += 1 + update(con, "alter table season_event_reward_pending add column created_at timestamp null default current_timestamp");
        }
        // đua top mới: 20/11 và Giáng sinh
        if (!exists(con, "select 1 from season_event_top where id = 801")) {
            changed += update(con, "insert into season_event_top (id, event_id, name) values (801, 8, 'Hộp quà 20-11')");
            changed += update(con, "update season_event_exchange set top_id = 801, top_point = if(id = 76, 3, 1) where id in (75, 76, 204)");
        }
        if (!exists(con, "select 1 from season_event_top where id = 901")) {
            changed += update(con, "insert into season_event_top (id, event_id, name) values (901, 9, 'Hộp quà Giáng sinh')");
            changed += update(con, "update season_event_exchange set top_id = 901, top_point = if(id = 78, 3, 1) where id in (77, 78)");
        }
        for (int top : new int[]{801, 901}) {
            if (!exists(con, "select 1 from season_event_top_reward where top_id = " + top)) {
                changed += update(con, "insert into season_event_top_reward (top_id, rank_from, rank_to, item_id, qty, hsd_days) "
                        + "select " + top + ", rank_from, rank_to, item_id, qty, hsd_days from season_event_top_reward where top_id = 703");
            }
        }
        for (Object[] r : TITLE_TOP) {
            if (!exists(con, "select 1 from season_event_top_reward where top_id = " + r[0] + " and item_id = " + r[1] + " and rank_from = " + r[2])) {
                changed += update(con, "insert into season_event_top_reward (top_id, rank_from, rank_to, item_id, qty, hsd_days) values ("
                        + r[0] + ", " + r[2] + ", " + r[3] + ", " + r[1] + ", 1, '" + r[4] + "')");
            }
        }
        // Quy Lão: đổi điểm sự kiện cho mọi sự kiện
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("select id from season_event")) {
            java.util.List<Integer> ids = new java.util.ArrayList<>();
            while (rs.next()) {
                ids.add(rs.getInt(1));
            }
            for (int ev : ids) {
                for (Object[] p : POINT_SHOP) {
                    changed += addShopExchange(con, ev, "Đổi\nđiểm", (int) p[0], 0, (int) p[1], (String) p[2], "13");
                }
                if (ev == 7) {
                    changed += addShopExchange(con, ev, "Đổi\nđiểm", 1728, 0, 99, "30", "13"); // Túi mù Halloween
                }
            }
        }
        for (Object[] p : HALLOWEEN_SHOP) {
            changed += addShopExchange(con, 7, "Cửa\nhàng", (int) p[0], (int) p[1], 0, (String) p[2], "81");
        }
        // 20/11: kéo tỉa hoa bán ở cửa hàng Chi Chi
        changed += update(con, "update season_event_exchange set npc = '81' where id in (202, 203) and npc = ''");
        return changed;
    }

    /** Thêm 1 món vào cửa hàng sự kiện (nếu chưa có): gem > 0 bán bằng ngọc, point > 0 bằng điểm sự kiện. */
    private static int addShopExchange(Connection con, int event, String grp, int item, int gem, int point, String days, String npc) throws Exception {
        String g = grp.replace("\\n", "\n");
        try (PreparedStatement ps = con.prepareStatement("select 1 from season_event_exchange where event_id = ? and grp = ? and reward_item = ?")) {
            ps.setInt(1, event);
            ps.setString(2, g);
            ps.setInt(3, item);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return 0;
                }
            }
        }
        if (!exists(con, "select 1 from item_template where id = " + item)) {
            return 0;
        }
        String name;
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("select NAME from item_template where id = " + item)) {
            name = rs.next() ? rs.getString(1) : "Vật phẩm";
        }
        try (PreparedStatement ps = con.prepareStatement("insert into season_event_exchange (event_id, grp, name, need, gold, gem, cost_point, reward_item, reward_qty, reward_days, npc) values (?, ?, ?, '', 0, ?, ?, ?, 1, ?, ?)")) {
            ps.setInt(1, event);
            ps.setString(2, g);
            ps.setString(3, name.length() > 60 ? name.substring(0, 60) : name);
            ps.setInt(4, gem);
            ps.setInt(5, point);
            ps.setInt(6, item);
            ps.setString(7, days);
            ps.setString(8, npc);
            return ps.executeUpdate();
        }
    }

    private static boolean exists(Connection con, String sql) throws Exception {
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next();
        }
    }

    private static int update(Connection con, String sql) throws Exception {
        try (Statement st = con.createStatement()) {
            return st.executeUpdate(sql);
        }
    }
}
