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
        {1673, 1099, "Đổi tại Santa trong sự kiện Halloween"},
        {1765, 255, "Đổi tại Santa trong sự kiện Giáng sinh"},};

    /** season_event_exchange: {id, event, grp, name, need, gold, gem, reward, qty, days} */
    private static final Object[][] EXCHANGES = {
        {201, 7, "Danh\\nhiệu", "Tay nhanh\\nhơn não", "901:199", 0, 0, 1673, 1, "7"},
        {202, 8, "Cửa\\nhàng", "Kéo tỉa\\nhoa", "", 0, 2, 1387, 1, ""},
        {203, 8, "Cửa\\nhàng", "Kéo tỉa hoa\\nđặc biệt", "", 0, 10, 1382, 1, ""},
        {204, 8, "Đổi\\nquà", "Hộp quà\\n(hoa hồng)", "1530:20", 0, 0, 1739, 1, ""},
        {205, 8, "Danh\\nhiệu", "Gõ đầu trẻ\\n7 ngày", "1388:10", 0, 0, 1392, 1, "7"},
        {206, 9, "Danh\\nhiệu", "Mùa đông\\nkhông lạnh", "1459:50,1464:50,649:3", 0, 0, 1765, 1, "7"},};

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
        {2076, 301, "Phần thưởng sự kiện Trung Thu (VIP)"},
        {1885, 300, "Vòng quay ngọc sự kiện Trung Thu"},
        {1978, 303, "Top 1 đua top Hộp đen bí ẩn"},};

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
