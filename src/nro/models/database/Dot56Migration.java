package nro.models.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import nro.models.utils.Logger;

/**
 * Đợt 56: Hào quang chân Sao Pha Lê 10 (thưởng mốc 7 món của Bà Hạt Mít, hiệu ứng 515),
 * dòng chỉ số đánh dấu đồ đập 10 sao trong thời gian sự kiện, 4 cải trang mới (part lấy từ gói icon 31/10).
 * Chạy lại không sao: chỉ thêm khi chưa có. Nếu id part mới đã có dữ liệu khác thì bỏ qua phần cải trang và báo lỗi.
 *
 * @author Việt
 */
public final class Dot56Migration {

    private Dot56Migration() {
    }

    public static final int OPT_HAO_QUANG_SPL10 = 261;
    public static final int ITEM_HAO_QUANG_SPL10 = 2112;
    /** Option 262: biến người xung quanh thành Zombi mỗi 30 giây (Cờ đeo lưng 3 con ma) */
    public static final int OPT_ZOMBIE_XQ = 262;
    public static final int PET_DAU_QUY = 2114, CO_3_CON_MA = 2117, HOP_NR_BI = 2118, THE_SUU_TAM = 2120;
    /** "Hộp Halloween" trong ảnh sự kiện = Hòm Halloween có sẵn (Chi Chi bán 10 triệu vàng, hạn 30 ngày) */
    public static final int HOM_HALLOWEEN = 1116;
    public static final int FLAG_3_CON_MA = 174;

    /** {id, TYPE, DATA} */
    private static final Object[][] PARTS = {
        {2379, 0, "[[17698,0,0],[17699,0,0],[2955,0,0]]"},
        {2380, 1, "[[17668,-6,-13],[17669,-6,-15],[17670,-7,-17],[17671,-5,-18],[17672,-6,-17],[17673,-5,-16],[17674,-6,-17],[17675,0,-12],[17676,0,-6],[17677,-7,-3],[17678,-2,-14],[17679,-7,-14],[17680,0,-9],[17681,0,-15],[17682,-3,-15],[17683,-7,-14],[2955,0,0]]"},
        {2381, 2, "[[17684,0,0],[17685,0,0],[17686,0,0],[17687,0,0],[17688,0,0],[17689,0,0],[17690,0,0],[17691,0,0],[17692,0,0],[17693,0,0],[17694,0,0],[17695,0,0],[17696,0,0],[2955,0,0]]"},
        {2382, 0, "[[2955,0,0],[2955,0,0],[2955,0,0]]"},
        {2383, 1, "[[2955,0,0],[17700,-18,-38],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0]]"},
        {2384, 2, "[[17703,-26,-14],[17701,1,-26],[17702,-37,-48],[17703,-37,-42],[17704,-20,-42],[17705,-18,-41],[17706,-28,-39],[17706,-26,-19],[17704,-18,-21],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0]]"},
        {2385, 0, "[[17711,-2,-13],[17712,-8,-14],[2955,0,0]]"},
        {2386, 1, "[[17713,1,-15],[17739,-10,-10],[17714,-39,-5],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[17715,-11,-7],[17716,-7,-1],[17717,-22,-9],[17736,-17,-35],[17737,-29,-12],[17718,-9,-20],[17719,-10,-18],[17720,-21,-13],[17721,-14,-4],[2955,0,0]]"},
        {2387, 2, "[[17722,10,7],[17723,-1,-4],[17724,1,-1],[17725,-34,-11],[17726,-38,-12],[17727,-34,-12],[17728,-38,-12],[17729,2,0],[17730,1,0],[17731,0,-5],[17732,-13,-19],[17733,-28,-29],[17734,-20,-12],[2955,0,0]]"},
        {2388, 0, "[[17761,-6,-7],[17762,-11,-9],[2955,0,0]]"},
        {2389, 1, "[[17763,-15,-3],[17764,-7,-6],[17765,-10,-9],[2955,0,0],[2955,0,0],[2955,0,0],[2955,0,0],[17766,-9,-20],[17767,-4,-17],[17768,-7,-15],[17769,-9,-37],[17770,-5,-35],[17771,-5,-22],[17772,-6,-8],[17773,-9,-7],[17774,-10,-5],[2955,0,0]]"},
        {2390, 2, "[[17775,4,2],[17776,1,-4],[17777,1,-3],[17778,-6,-13],[17779,-5,-19],[17780,-3,-18],[17781,-8,-17],[17782,-3,0],[17783,-5,-2],[17784,-2,-4],[17785,-27,-41],[17786,-13,-43],[17787,-8,-38],[2955,0,0]]"},};

    /** {id, TYPE, NAME, mô tả, icon, head, body, leg, ảnh đại diện (head_avatar)} */
    private static final Object[][] ITEMS = {
        {ITEM_HAO_QUANG_SPL10, 27, "Hào quang chân Sao Pha Lê 10", "Thưởng sự kiện Sao Pha Lê 10", 32299, -1, -1, -1, -1},
        {2113, 5, "Cải trang Goku Daima", "Cải trang", 17697, 2379, 2380, 2381, 17697},
        {2114, 5, "Cải trang Ma cà rồng", "Cải trang", 17707, 2382, 2383, 2384, 17707},
        {2115, 5, "Cải trang Kiếm sĩ tóc hồng", "Cải trang", 17735, 2385, 2386, 2387, 17738},
        {2116, 5, "Cải trang Phù thủy tóc vàng", "Cải trang", 17788, 2388, 2389, 2390, 17789},};

    public static void run(Connection con) {
        int changed = 0;
        try {
            if (!exists(con, "select 1 from item_option_template where id = " + OPT_HAO_QUANG_SPL10)) {
                try (PreparedStatement ps = con.prepareStatement("insert into item_option_template (id, NAME) values (?, ?)")) {
                    ps.setInt(1, OPT_HAO_QUANG_SPL10);
                    ps.setString(2, "Hào quang chân Sao Pha Lê 10: SĐ +6%, HP +6%, KI +6%, ST cuối +2% (không cộng dồn)");
                    changed += ps.executeUpdate();
                }
            }
            boolean partsOk = true;
            for (Object[] p : PARTS) {
                try (PreparedStatement ps = con.prepareStatement("select DATA from part where id = ?")) {
                    ps.setInt(1, (int) p[0]);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && !rs.getString(1).trim().equals(p[2])) {
                            partsOk = false;
                        }
                    }
                }
            }
            if (!partsOk) {
                Logger.error("Đợt 56: part 2379 - 2390 đã có dữ liệu khác, bỏ qua 4 cải trang mới\n");
            } else {
                for (Object[] p : PARTS) {
                    if (!exists(con, "select 1 from part where id = " + p[0])) {
                        try (PreparedStatement ps = con.prepareStatement("insert into part (id, TYPE, DATA) values (?, ?, ?)")) {
                            ps.setInt(1, (int) p[0]);
                            ps.setInt(2, (int) p[1]);
                            ps.setString(3, (String) p[2]);
                            changed += ps.executeUpdate();
                        }
                    }
                }
            }
            for (Object[] it : ITEMS) {
                int head = (int) it[5];
                if (head >= 0 && !partsOk) {
                    continue;
                }
                if (!exists(con, "select 1 from item_template where id = " + it[0])) {
                    try (PreparedStatement ps = con.prepareStatement("insert into item_template (id, TYPE, gender, NAME, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg)"
                            + " values (?, ?, 3, ?, ?, 0, ?, -1, 0, ?, 0, 0, ?, ?, ?)")) {
                        ps.setInt(1, (int) it[0]);
                        ps.setInt(2, (int) it[1]);
                        ps.setString(3, (String) it[2]);
                        ps.setString(4, (String) it[3]);
                        ps.setInt(5, (int) it[4]);
                        ps.setLong(6, head >= 0 ? 1_500_000L : 0L);
                        ps.setInt(7, head);
                        ps.setInt(8, (int) it[6]);
                        ps.setInt(9, (int) it[7]);
                        changed += ps.executeUpdate();
                    }
                }
                if (head >= 0 && (int) it[8] > 0 && !exists(con, "select 1 from head_avatar where head_id = " + head)) {
                    changed += update(con, "insert into head_avatar (head_id, avatar_id) values (" + head + ", " + it[8] + ")");
                }
            }
        } catch (Exception ex) {
            Logger.error("Đợt 56: không cập nhật được database: " + ex.getMessage() + "\n");
        }
        try {
            changed += runHalloween(con);
            changed += runDot57(con);
            changed += runDot58(con);
            changed += runDot59(con);
            changed += runDot60(con);
            changed += runDot61(con);
            changed += runDot62(con);
            changed += runDot63(con);
            changed += runDot65(con);
            changed += runDot66(con);
        } catch (Exception ex) {
            Logger.error("Đợt 56 (Halloween): không cập nhật được database: " + ex.getMessage() + "\n");
        }
        if (changed > 0) {
            Logger.success("Đợt 56: đã thêm Hào quang chân Sao Pha Lê 10, vật phẩm và quà Halloween mới (" + changed + " dòng)\n");
        }
    }

    // ------------------------------------------------------------------ Halloween (theo ảnh sự kiện)

    /** {id, TYPE, NAME, mô tả, icon, part} */
    private static final Object[][] HAL_ITEMS = {
        {CO_3_CON_MA, 11, "Cờ đeo lưng 3 con ma", "Vật phẩm sự kiện Halloween", 17760, FLAG_3_CON_MA},
        {HOP_NR_BI, 27, "Hộp Ngọc Rồng Bí", "Mở ra 1 viên Ngọc Rồng Bí 1 - 7 sao", 17442, -1},
        {THE_SUU_TAM, 27, "Thẻ sưu tầm thường", "Mở ra 1 mảnh Đội trưởng Vàng, Rồng thần Namếc hoặc Khỉ Oozaru", 17638, -1},};

    /** Đổi tên theo ảnh sự kiện: {id, TYPE, NAME} */
    private static final Object[][] RENAME = {
        {2113, 5, "Cải trang Goku Ninja"},
        {PET_DAU_QUY, 27, "Pet Đầu Quỷ"},
        {2115, 5, "Cải trang Công Chúa Quỷ"},
        {2116, 5, "Tiểu thư Malfoy"},};

    /** Chỉ số mặc định (item_template_option): {template, option, param, param_max} */
    private static final int[][] OPTIONS = {
        // Cải trang Goku Ninja: SĐ 28%, HP 28%, KI 28%, 21% vàng rơi, SĐ chí mạng 15 - 27%
        {2113, 50, 28, 28}, {2113, 77, 28, 28}, {2113, 103, 28, 28}, {2113, 100, 21, 21}, {2113, 5, 15, 27},
        // Tiểu thư Malfoy: SĐ 20%, chí mạng 10%, tấn công +20% khi đánh quái, đẹp +15% SĐ, 11% vàng rơi
        {2116, 50, 20, 20}, {2116, 14, 10, 10}, {2116, 19, 20, 20}, {2116, 117, 15, 15}, {2116, 100, 11, 11},
        // Pet Đầu Quỷ: SĐ 14%, SĐ lên boss 13%, chí mạng 3%, né chí mạng 7%
        {PET_DAU_QUY, 50, 14, 14}, {PET_DAU_QUY, 204, 13, 13}, {PET_DAU_QUY, 14, 3, 3}, {PET_DAU_QUY, 191, 7, 7},
        // Cờ đeo lưng 3 con ma: SĐ 19%, HP 18%, KI 18%, chí mạng 8%, né 25%, 1 ngọc mỗi ngày,
        // hồi 5% HP/KI mỗi 30 giây cho bản thân và đồng bang, biến người xung quanh thành Zombi
        {CO_3_CON_MA, 50, 19, 19}, {CO_3_CON_MA, 77, 18, 18}, {CO_3_CON_MA, 103, 18, 18}, {CO_3_CON_MA, 14, 8, 8},
        {CO_3_CON_MA, 108, 25, 25}, {CO_3_CON_MA, 161, 1, 1}, {CO_3_CON_MA, 221, 5, 5}, {CO_3_CON_MA, OPT_ZOMBIE_XQ, 0, 0},};

    private static final String T = "7,15", T3 = "3,7,15";

    /** Nội dung hộp: box -> {item, qty_min, qty_max, weight, hsd, perm_rate %, bảo hiểm (số lần mở)} */
    private static final Object[][][] BOXES = {
        // Túi mù Halloween
        {{1728}, {CO_3_CON_MA, 1, 1, 150, T, 2, 0}, {1951, 1, 1, 150, T, 2, 70}, {1654, 1, 1, 150, T, 2, 0},
            {1927, 1, 1, 150, T, 2, 0}, {2097, 1, 1, 60, T, 2, 200}, {2096, 1, 1, 60, T, 2, 200},
            {2116, 1, 1, 25, T, 2, 0}, {1906, 1, 1, 5, T, 1, 0}, {HOP_NR_BI, 1, 1, 250, "", 0, 0}},
        // Kẹo mù Halloween (Kẹo mù ma quỷ)
        {{1726}, {909, 1, 1, 100, T3, 2, 0}, {910, 1, 1, 100, T3, 2, 0}, {743, 1, 1, 100, T3, 2, 0},
            {644, 1, 1, 35, T3, 2, 0}, {645, 1, 1, 35, T3, 2, 0}, {646, 1, 1, 35, T3, 2, 0},
            {1106, 1, 1, 80, T3, 2, 0}, {1104, 1, 1, 80, T3, 2, 0}, {1042, 1, 1, 80, T3, 2, 0},
            {1359, 1, 1, 100, T3, 2, 0}, {1708, 1, 1, 80, T3, 2, 0}, {1923, 1, 1, 100, T3, 2, 0},
            {894, 1, 1, 35, T3, 2, 0}, {895, 1, 1, 35, T3, 2, 0}, {896, 1, 1, 35, T3, 2, 0}},
        // Thẻ sưu tầm thường: 1 mảnh thẻ radar
        {{THE_SUU_TAM}, {956, 1, 1, 40, "", 0, 0}, {1204, 1, 1, 40, "", 0, 0}, {1771, 1, 1, 20, "", 0, 0}},
        // Thiệp Ma Halloween
        {{1117}, {THE_SUU_TAM, 1, 1, 150, "", 0, 0}, {597, 1, 1, 250, "", 0, 0}, {1721, 1, 1, 150, T3, 2, 100}, {2017, 1, 1, 100, T3, 2, 100},
            {1923, 1, 1, 150, T3, 2, 0}, {1926, 1, 1, 80, T3, 2, 100}, {1453, 1, 1, 100, "", 0, 0}},
        // Hòm Halloween (10 triệu vàng)
        {{HOM_HALLOWEEN}, {1104, 1, 1, 100, T3, 2, 0}, {1105, 1, 1, 100, T3, 2, 0}, {1106, 1, 1, 100, T3, 2, 0},
            {906, 1, 1, 100, T3, 2, 0}, {1318, 1, 1, 100, T3, 2, 0}, {1926, 1, 1, 100, T3, 2, 0},
            {2113, 1, 1, 60, T3, 2, 0}, {1453, 1, 1, 150, "", 0, 0}, {1173, 10, 10, 150, "", 0, 0}},
        // Hộp Ngọc Rồng Bí: Bí ngô (ngọc rồng bí) 1 - 7 sao
        {{HOP_NR_BI}, {702, 1, 1, 1, "", 0, 0}, {703, 1, 1, 1, "", 0, 0}, {704, 1, 1, 1, "", 0, 0}, {705, 1, 1, 1, "", 0, 0},
            {706, 1, 1, 1, "", 0, 0}, {707, 1, 1, 1, "", 0, 0}, {708, 1, 1, 1, "", 0, 0}},};

    /** Đua top: top_id -> {rank_from, rank_to, item, qty, hsd, min_point} */
    private static final Object[][][] TOPS = {
        // Túi mù Halloween; top 1 đạt 21000 điểm nhận thêm SKH Beerus tự chọn + Trứng Xên Vàng
        {{702}, {1, 1, 1922, 1, "", 0}, {1, 1, 1283, 70, "", 0}, {1, 1, 1559, 5, "", 0}, {1, 1, 2111, 5, "", 0}, {1, 1, 1771, 30, "", 0}, {1, 1, 1851, 5, "", 0},
            {1, 1, 2102, 1, "", 21000}, {1, 1, 2084, 1, "", 21000},
            {2, 2, 1922, 1, "", 0}, {2, 2, 1283, 60, "", 0}, {2, 2, 1559, 3, "", 0}, {2, 2, 2111, 3, "", 0}, {2, 2, 1771, 20, "", 0}, {2, 2, 1851, 3, "", 0},
            {3, 3, 1922, 1, "", 0}, {3, 3, 1283, 50, "", 0}, {3, 3, 1559, 2, "", 0}, {3, 3, 2111, 2, "", 0}, {3, 3, 1771, 10, "", 0}, {3, 3, 1851, 2, "", 0},
            {4, 10, 1922, 1, "365", 0}, {4, 10, 1283, 20, "", 0}, {4, 10, 1559, 1, "", 0}, {4, 10, 2111, 1, "", 0}, {4, 10, 1771, 5, "", 0}, {4, 10, 1851, 1, "", 0},
            {11, 50, 1922, 1, "180", 0}, {11, 50, 1283, 10, "", 0}, {11, 50, 1690, 50, "", 0},
            {51, 100, 1922, 1, "60", 0}, {51, 100, 1283, 5, "", 0}, {51, 100, 1690, 20, "", 0}},
        // Dùng Hộp Kẹo Ma Quỷ
        {{701}, {1, 1, 1809, 300, "", 0}, {1, 1, 1283, 70, "", 0}, {1, 1, 1559, 3, "", 0}, {1, 1, 2111, 3, "", 0}, {1, 1, 2046, 10, "", 0}, {1, 1, 1884, 1, "", 0},
            {2, 2, 1809, 200, "", 0}, {2, 2, 1283, 60, "", 0}, {2, 2, 1559, 2, "", 0}, {2, 2, 2111, 2, "", 0}, {2, 2, 2046, 5, "", 0},
            {3, 3, 1809, 100, "", 0}, {3, 3, 1283, 50, "", 0}, {3, 3, 1559, 1, "", 0}, {3, 3, 2111, 1, "", 0}, {3, 3, 2046, 2, "", 0},
            {4, 10, 1809, 50, "", 0}, {4, 10, 1283, 20, "", 0}, {4, 10, 1690, 50, "", 0}, {4, 10, 1909, 250, "", 0},
            {11, 50, 1809, 20, "", 0}, {11, 50, 1283, 10, "", 0}, {11, 50, 1690, 20, "", 0}, {11, 50, 1909, 100, "", 0},
            {51, 100, 1809, 10, "", 0}, {51, 100, 1283, 5, "", 0}, {51, 100, 1690, 10, "", 0}, {51, 100, 1909, 20, "", 0}},};

    private static int runHalloween(Connection con) throws Exception {
        int changed = 0;
        // cột / bảng mới: bảo hiểm hộp quà, điểm tối thiểu của quà top
        if (!columnExists(con, "item_box_reward", "pity")) {
            changed += 1 + update(con, "alter table item_box_reward add column pity int not null default 0");
        }
        if (!columnExists(con, "season_event_top_reward", "min_point")) {
            changed += 1 + update(con, "alter table season_event_top_reward add column min_point int not null default 0");
        }
        if (!columnExists(con, "season_event_reward_pending", "points")) {
            changed += 1 + update(con, "alter table season_event_reward_pending add column points bigint not null default 0");
        }
        update(con, "create table if not exists box_pity (player_id bigint not null, box_id int not null, item_id int not null, cnt int not null default 0, primary key (player_id, box_id, item_id))");
        update(con, "create table if not exists server_migration (name varchar(64) primary key, done_at timestamp default current_timestamp)");
        if (!exists(con, "select 1 from item_option_template where id = " + OPT_ZOMBIE_XQ)) {
            changed += update(con, "insert into item_option_template (id, NAME) values (" + OPT_ZOMBIE_XQ + ", 'Biến người xung quanh thành Zombi mỗi 30 giây')");
        }
        if (!exists(con, "select 1 from flag_bag where id = " + FLAG_3_CON_MA)) {
            StringBuilder frames = new StringBuilder();
            for (int i = 17742; i <= 17759; i++) {
                frames.append(frames.length() == 0 ? "" : ", ").append(i);
            }
            changed += update(con, "insert into flag_bag (id, icon_data, NAME, gold, gem, icon_id) values (" + FLAG_3_CON_MA + ", '" + frames + "', 'flag_bag', -1, -1, 0)");
        }
        for (Object[] it : HAL_ITEMS) {
            if (!exists(con, "select 1 from item_template where id = " + it[0])) {
                try (PreparedStatement ps = con.prepareStatement("insert into item_template (id, TYPE, gender, NAME, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg)"
                        + " values (?, ?, 3, ?, ?, 0, ?, ?, ?, 0, 0, 0, -1, -1, -1)")) {
                    ps.setInt(1, (int) it[0]);
                    ps.setInt(2, (int) it[1]);
                    ps.setString(3, (String) it[2]);
                    ps.setString(4, (String) it[3]);
                    ps.setInt(5, (int) it[4]);
                    ps.setInt(6, (int) it[5]);
                    ps.setInt(7, (int) it[1] == 27 ? 1 : 0);
                    changed += ps.executeUpdate();
                }
            }
        }
        for (Object[] r : RENAME) {
            try (PreparedStatement ps = con.prepareStatement("update item_template set NAME = ?, TYPE = ? where id = ? and (NAME <> ? or TYPE <> ?)")) {
                ps.setString(1, (String) r[2]);
                ps.setInt(2, (int) r[1]);
                ps.setInt(3, (int) r[0]);
                ps.setString(4, (String) r[2]);
                ps.setInt(5, (int) r[1]);
                changed += ps.executeUpdate();
            }
        }
        if (exists(con, "select 1 from server_migration where name = 'dot56_halloween'")) {
            return changed; // nội dung hộp / top / chỉ số chỉ ghi 1 lần, sau đó admin sửa trên panel không bị ghi đè
        }
        for (int[] o : OPTIONS) {
            if (!exists(con, "select 1 from item_template_option where template_id = " + o[0] + " and option_id = " + o[1])) {
                changed += update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) values (" + o[0] + ", " + o[1] + ", " + o[2] + ", " + o[3] + ", 100, 0)");
            }
        }
        for (Object[][] box : BOXES) {
            int boxId = (int) box[0][0];
            changed += update(con, "delete from item_box_reward where box_id = " + boxId);
            try (PreparedStatement ps = con.prepareStatement("insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) values (?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (int i = 1; i < box.length; i++) {
                    Object[] r = box[i];
                    ps.setInt(1, boxId);
                    ps.setInt(2, (int) r[0]);
                    ps.setInt(3, (int) r[1]);
                    ps.setInt(4, (int) r[2]);
                    ps.setInt(5, (int) r[3]);
                    ps.setString(6, (String) r[4]);
                    ps.setInt(7, (int) r[5]);
                    ps.setInt(8, (int) r[6]);
                    changed += ps.executeUpdate();
                }
            }
        }
        // Đại chiến Zombi: thêm CT Lý Tiểu Nương Bikini và Pet Đầu Quỷ (tỉ lệ vĩnh viễn cao)
        for (int item : new int[]{1512, PET_DAU_QUY}) {
            if (!exists(con, "select 1 from item_box_reward where box_id = 100703 and item_id = " + item)) {
                changed += update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate) values (100703, " + item + ", 1, 1, 60, '3,7,15', 10)");
            }
        }
        for (Object[][] top : TOPS) {
            int topId = (int) top[0][0];
            if (!exists(con, "select 1 from season_event_top where id = " + topId)) {
                continue;
            }
            // giữ lại quà danh hiệu đã thêm ở đợt trước
            changed += update(con, "delete from season_event_top_reward where top_id = " + topId + " and item_id not in (select id from item_template where TYPE = 36)");
            try (PreparedStatement ps = con.prepareStatement("insert into season_event_top_reward (top_id, rank_from, rank_to, item_id, qty, hsd_days, min_point) values (?, ?, ?, ?, ?, ?, ?)")) {
                for (int i = 1; i < top.length; i++) {
                    Object[] r = top[i];
                    ps.setInt(1, topId);
                    ps.setInt(2, (int) r[0]);
                    ps.setInt(3, (int) r[1]);
                    ps.setInt(4, (int) r[2]);
                    ps.setInt(5, (int) r[3]);
                    ps.setString(6, (String) r[4]);
                    ps.setInt(7, (int) r[5]);
                    changed += ps.executeUpdate();
                }
            }
        }
        changed += update(con, "insert into server_migration (name) values ('dot56_halloween')");
        return changed;
    }

    /** Chỉ số mặc định thêm ở đợt 57: Avatar mang kính đo sức mạnh, Cánh thiên sứ hắc ám */
    private static final int[][] OPTIONS_57 = {
        {894, 50, 20, 20}, {894, 77, 17, 17}, {895, 50, 20, 20}, {895, 77, 17, 17}, {896, 50, 20, 20}, {896, 77, 17, 17},
        {1149, 50, 15, 15}, {1149, 77, 13, 13}, {1149, 103, 13, 13}, {1149, 94, 3, 3}, {1149, 97, 5, 5}, {1149, 30, 1, 1},};

    /**
     * Đợt 57: cửa hàng Chi Chi mùa Halloween giống ảnh (Hòm Halloween 10 triệu vàng hạn 30 ngày, giỏ / giấy hạn 35 ngày,
     * Giỏ đựng kẹo trái bí 5 triệu vàng, thêm Cánh thiên sứ hắc ám 999 ngọc); bỏ "Hộp Halloween" 2119 của đợt 56
     * (trùng Hòm Halloween); Thẻ sưu tầm thường; Avatar mang kính trong Kẹo mù.
     */
    private static int runDot57(Connection con) throws Exception {
        int changed = 0;
        if (exists(con, "select 1 from server_migration where name = 'dot57_halloween'")) {
            return 0;
        }
        changed += update(con, "delete from season_event_exchange where reward_item = 2119");
        changed += update(con, "delete from item_template_option where template_id = 2119");
        changed += update(con, "update season_event_exchange set gem = 0, gold = 10000000, reward_days = '30' where event_id = 7 and reward_item = " + HOM_HALLOWEEN);
        changed += update(con, "update season_event_exchange set gem = 0, gold = 5000000, reward_days = '35' where event_id = 7 and reward_item = 1355");
        changed += update(con, "update season_event_exchange set reward_days = '35' where event_id = 7 and reward_item in (1348, 1354)");
        if (!exists(con, "select 1 from season_event_exchange where event_id = 7 and reward_item = 1149")) {
            changed += update(con, "insert into season_event_exchange (event_id, grp, name, need, gold, gem, reward_item, reward_qty, reward_days, npc) values (7, 'Cửa\nhàng', 'Cánh thiên sứ\nhắc ám', '', 0, 999, 1149, 1, '', '81')");
        }
        for (int[] o : OPTIONS_57) {
            if (!exists(con, "select 1 from item_template_option where template_id = " + o[0] + " and option_id = " + o[1])) {
                changed += update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) values (" + o[0] + ", " + o[1] + ", " + o[2] + ", " + o[3] + ", 100, 0)");
            }
        }
        // nội dung Hòm Halloween, Thẻ sưu tầm thường, thêm vào Thiệp Ma / Kẹo mù (DB đã chạy đợt 56 trước đó)
        for (Object[][] box : BOXES) {
            int boxId = (int) box[0][0];
            if (boxId != HOM_HALLOWEEN && boxId != THE_SUU_TAM && boxId != 1117 && boxId != 1726) {
                continue;
            }
            for (int i = 1; i < box.length; i++) {
                Object[] r = box[i];
                boolean replaceAll = boxId == HOM_HALLOWEEN || boxId == THE_SUU_TAM;
                if (replaceAll && i == 1) {
                    changed += update(con, "delete from item_box_reward where box_id = " + boxId);
                }
                if (!replaceAll && exists(con, "select 1 from item_box_reward where box_id = " + boxId + " and item_id = " + r[0])) {
                    continue;
                }
                try (PreparedStatement ps = con.prepareStatement("insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) values (?, ?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setInt(1, boxId);
                    ps.setInt(2, (int) r[0]);
                    ps.setInt(3, (int) r[1]);
                    ps.setInt(4, (int) r[2]);
                    ps.setInt(5, (int) r[3]);
                    ps.setString(6, (String) r[4]);
                    ps.setInt(7, (int) r[5]);
                    ps.setInt(8, (int) r[6]);
                    changed += ps.executeUpdate();
                }
            }
        }
        changed += update(con, "insert into server_migration (name) values ('dot57_halloween')");
        return changed;
    }

    /** Option 263: biến người xung quanh thành Bí Ngô mỗi 30 giây (Cải trang Bill Bí Ngô) */
    public static final int OPT_BINGO_XQ = 263;

    /**
     * Đợt 58: chỉ số đúng theo ảnh shop Chi Chi / túi mù (trước đây các món này không có dòng chỉ số nào).
     * {template, option, param}
     */
    private static final int[][] OPTIONS_58 = {
        {814, 94, 14}, // Ma trơi: giảm 14% sát thương
        {815, 77, 24}, // Hồn ma Goku: HP +24%
        {816, 50, 14}, // Hồn ma Ca đíc: SĐ +14%
        {817, 103, 24}, // Hồn ma Pôcôlô: KI +24%
        {818, 30, 1}, // Capsule Halloween: không giao dịch
        {915, 30, 1}, // Capsule Squid Game: không giao dịch
        {739, 50, 20}, {739, 77, 20}, {739, 103, 20}, {739, OPT_BINGO_XQ, 0}, {739, 32, 0}, {739, 154, 0}, // Bill Bí Ngô
        {1654, 50, 16}, {1654, 77, 15}, {1654, 103, 15}, {1654, 106, 0}, // Pet Cerberus (chống lạnh)
        {1927, 204, 30}, {1927, 95, 15}, {1927, 94, 10}, {1927, 30, 1}, // Cánh thiên thần sa ngã
        {1110, 50, 11}, {1111, 50, 11},}; // Cánh thiên thần, Cánh thiên thần 2

    private static int runDot58(Connection con) throws Exception {
        int changed = 0;
        if (!exists(con, "select 1 from item_option_template where id = " + OPT_BINGO_XQ)) {
            changed += update(con, "insert into item_option_template (id, NAME) values (" + OPT_BINGO_XQ + ", 'Biến người xung quanh thành Bí Ngô mỗi 30 giây')");
        }
        // cờ Labubu (141) có trong source V10 nhưng thiếu ở database này: đeo vào không hiện
        if (!exists(con, "select 1 from flag_bag where id = 141")) {
            changed += update(con, "insert into flag_bag (id, icon_data, NAME, gold, gem, icon_id) values (141, '13993, 13994', 'flag_bag', -1, -1, 0)");
        }
        if (exists(con, "select 1 from server_migration where name = 'dot58_items'")) {
            return changed;
        }
        java.util.Set<Integer> done = new java.util.HashSet<>();
        for (int[] o : OPTIONS_58) {
            if (done.add(o[0])) {
                changed += update(con, "delete from item_template_option where template_id = " + o[0]);
            }
            changed += update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) values (" + o[0] + ", " + o[1] + ", " + o[2] + ", " + o[2] + ", 100, 0)");
        }
        // Capsule Halloween bán ở Chi Chi hạn 30 ngày như Capsule Squid Game
        changed += update(con, "update season_event_exchange set reward_days = '30' where event_id = 7 and reward_item = 818 and reward_days = ''");
        changed += update(con, "insert into server_migration (name) values ('dot58_items')");
        return changed;
    }

    /** Đợt 59: chỉ số theo ảnh tham khảo. {template, option, param} */
    private static final int[][] OPTIONS_59 = {
        {2115, 77, 20}, {2115, 103, 20}, {2115, 50, 25}, {2115, 101, 150}, // CT Công Chúa Quỷ (trước là "Melee")
        {742, 8, 4}, {742, 50, 20}, {742, 77, 20}, {742, 103, 20}, {742, 154, 0}, // Caufila Dơi
        {1702, 30, 1}, {1702, 50, 18}, {1702, 77, 15}, {1702, 103, 15}, {1702, 117, 3}, {1702, 80, 10}, // Lưỡi hái hồng
        {1737, 30, 1}, // Rađa dò Zombie
        {1797, 50, 24}, {1797, 77, 23}, {1797, 108, 23}, {1797, 5, 12}, {1797, 95, 10}, {1797, 253, 30}, {1797, 30, 1}, // CT Bunma Rider
        {1235, 50, 24}, {1235, 77, 24}, {1235, 103, 24}, {1235, 14, 10}, {1235, 108, 15}, {1235, 148, 20}, // Poc Bikini
        {1557, 50, 25}, {1557, 77, 24}, {1557, 103, 24}, {1557, 80, 15}, {1557, 108, 5}, {1557, 94, 15}, {1557, 148, 50}, {1557, 30, 1}, // Hắc Mị Nương
        {1931, 50, 23}, {1931, 77, 23}, {1931, 103, 23}, {1931, 95, 10}, {1931, 108, 10}, {1931, 80, 8}, {1931, 30, 1}, // Janemba
        {1942, 50, 14}, {1942, 77, 10}, {1942, 103, 10}, {1942, 94, 9}, {1942, 108, 20}, {1942, 85, 0}, {1942, 30, 1}, // Máy bay giấy
        {1735, 77, 23}, {1735, 103, 20}, {1735, 94, 10}, {1735, 30, 1}, // Đeo lưng dụng cụ học tập
        {1674, 50, 23}, {1674, 77, 23}, {1674, 103, 23}, {1674, 14, 11}, {1674, 108, 10}, {1674, 80, 8}, {1674, 183, 11}, {1674, 30, 1}, // Pikkon
        {1898, 30, 1}, {1898, 50, 13}, {1898, 95, 15}, {1898, 85, 0}, // Thú cưỡi rồng Đen
        {1897, 103, 17}, {1897, 81, 10}, {1897, 85, 0}, // Thú cưỡi rồng Băng
        {2059, 30, 1}, {2059, 50, 18}, {2059, 77, 18}, {2059, 103, 22}, {2059, 10, 15}, {2059, 14, 10}, {2059, 204, 10}, // Pet Minion
        {1567, 30, 1}, {1567, 50, 23}, {1567, 77, 20}, {1567, 103, 20}, {1567, 5, 20}, {1567, 80, 5}, {1567, 226, 17}, // CT Frieren
        {1945, 30, 1}, {1945, 50, 14}, {1945, 103, 25}, {1945, 253, 61}, // Pet ngựa KPI
        {1966, 50, 15}, {1966, 77, 15},}; // Đeo lưng Mãi Dell thành công

    private static int runDot59(Connection con) throws Exception {
        int changed = 0;
        changed += update(con, "update item_template set NAME = 'Cải trang Công Chúa Quỷ' where id = 2115 and NAME <> 'Cải trang Công Chúa Quỷ'");
        if (exists(con, "select 1 from server_migration where name = 'dot59_items'")) {
            return changed;
        }
        java.util.Set<Integer> done = new java.util.HashSet<>();
        for (int[] o : OPTIONS_59) {
            if (done.add(o[0])) {
                changed += update(con, "delete from item_template_option where template_id = " + o[0]);
            }
            changed += update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) values (" + o[0] + ", " + o[1] + ", " + o[2] + ", " + o[2] + ", 100, 0)");
        }
        // Thiệp Ma: 50 ngọc, hạn 30 ngày; Rađa dò Zombie hạn 30 ngày (như ảnh shop Chi Chi)
        changed += update(con, "update season_event_exchange set gem = 50, reward_days = '30' where event_id = 7 and reward_item = 1117");
        changed += update(con, "update season_event_exchange set reward_days = '30' where event_id = 7 and reward_item = 1737");
        // Công Chúa Quỷ trong Thiệp Ma (có bảo hiểm)
        if (!exists(con, "select 1 from item_box_reward where box_id = 1117 and item_id = 2115")) {
            changed += update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) values (1117, 2115, 1, 1, 80, '3,7,15', 2, 100)");
        }
        changed += update(con, "insert into server_migration (name) values ('dot59_items')");
        return changed;
    }

    /** Đợt 60: chỉ số ngẫu nhiên trong khoảng. {template, option, từ, đến} */
    private static final int[][] OPTIONS_60 = {
        // Bóng bước chân hoa hồng xanh (băng): SĐ 3-7%, SĐ +3000-10000, đi cùng Pet Xên hoàn hảo +6% STCM
        {2097, 50, 3, 7}, {2097, 0, 3000, 10000}, {2097, 169, 6, 6},
        // Bóng bước chân hoa hồng đỏ: HP 3-7%, HP +5000-15000, đi cùng Pet Xên Max +5% giảm sát thương
        {2096, 77, 3, 7}, {2096, 6, 5000, 15000}, {2096, 42, 5, 5},
        // Pet Xên con: KI 18-25%, KI +15000-35000, vàng rơi 5-15%, may mắn 5-20%
        {2037, 103, 18, 25}, {2037, 7, 15000, 35000}, {2037, 100, 5, 15}, {2037, 236, 5, 20},
        // Pet Xên hoàn hảo: SĐ 17-23%, SĐ +10000-25000, chí mạng 5-10%, SĐ chí mạng 5-15%, sát thương chuẩn 1-3%
        {2036, 50, 17, 23}, {2036, 0, 10000, 25000}, {2036, 14, 5, 10}, {2036, 5, 5, 15}, {2036, 10, 1, 3},
        // Pet Xên Max: HP 17-25%, HP +20000-150000, giảm sát thương 5-10%, né 10-20%, giáp +5000-35000
        {2038, 77, 17, 25}, {2038, 6, 20000, 150000}, {2038, 94, 5, 10}, {2038, 108, 10, 20}, {2038, 47, 5000, 35000},
        // Pháo hoa NRO (đeo lưng): SĐ 20%, SĐ +10000-25000
        {1965, 50, 20, 20}, {1965, 0, 10000, 25000},
        // Đeo lưng bông tuyết: HP 20%, HP +10000-25000
        {1951, 77, 20, 20}, {1951, 6, 10000, 25000},
        // Lưỡi hái hồng đi cùng CT Black Goku +4% SĐ, HP, KI; Poc Bikini giảm 5% SĐ người xung quanh
        {1702, 264, 4, 4}, {1235, 265, 5, 5},};

    public static final int FLAG_PHAO_HOA_NRO = 175;

    private static int runDot60(Connection con) throws Exception {
        int changed = 0;
        if (!exists(con, "select 1 from item_option_template where id = 264")) {
            changed += update(con, "insert into item_option_template (id, NAME) values (264, 'Đi cùng CT Black Goku tăng #% sức đánh, HP, KI')");
        }
        if (!exists(con, "select 1 from item_option_template where id = 265")) {
            changed += update(con, "insert into item_option_template (id, NAME) values (265, 'Giảm #% sức đánh của người xung quanh, tối đa 10 người. Mặc CT hè Pic, Poc, King Kong không bị ảnh hưởng')");
        }
        // Pháo hoa NRO: cờ đeo lưng hoạt ảnh pháo hoa (icon 16560 - 16571)
        if (!exists(con, "select 1 from flag_bag where id = " + FLAG_PHAO_HOA_NRO)) {
            StringBuilder f = new StringBuilder();
            for (int i = 16560; i <= 16571; i++) {
                f.append(f.length() == 0 ? "" : ", ").append(i);
            }
            changed += update(con, "insert into flag_bag (id, icon_data, NAME, gold, gem, icon_id) values (" + FLAG_PHAO_HOA_NRO + ", '" + f + "', 'flag_bag', -1, -1, 0)");
        }
        changed += update(con, "update item_template set part = " + FLAG_PHAO_HOA_NRO + " where id = 1965 and part < 0");
        if (exists(con, "select 1 from server_migration where name = 'dot60_items'")) {
            return changed;
        }
        java.util.Set<Integer> done = new java.util.HashSet<>();
        for (int[] o : OPTIONS_60) {
            if (o[0] != 1702 && o[0] != 1235 && done.add(o[0])) { // Lưỡi hái / Poc Bikini chỉ thêm dòng
                changed += update(con, "delete from item_template_option where template_id = " + o[0]);
            }
            changed += update(con, "delete from item_template_option where template_id = " + o[0] + " and option_id = " + o[1]);
            changed += update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) values (" + o[0] + ", " + o[1] + ", " + o[2] + ", " + o[3] + ", 100, 0)");
        }
        // Trứng Xên Vàng: 1 trong 3 Pet Xên, chỉ số tối đa, vĩnh viễn
        changed += update(con, "delete from item_box_reward where box_id = 2084");
        for (int pet : new int[]{2036, 2037, 2038}) {
            changed += update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) values (2084, " + pet + ", 1, 1, 1, '', 0, 0)");
        }
        changed += update(con, "update item_template set description = 'Mở ra 1 Pet Xên chỉ số tối đa, vĩnh viễn' where id = 2084");
        changed += update(con, "insert into server_migration (name) values ('dot60_items')");
        return changed;
    }

    /** Đợt 61: vàng boss theo bậc. {bậc, item (-1 vàng), sl từ, đến, tỉ lệ /10000} */
    private static final int[][] BOSS_REWARD = {
        {1, -1, 10000, 20000, 10000}, {2, -1, 30000, 60000, 10000}, {3, -1, 80000, 150000, 10000},
        {4, -1, 200000, 400000, 10000}, {5, -1, 500000, 1000000, 10000},};

    private static int runDot61(Connection con) throws Exception {
        int changed = 0;
        update(con, "create table if not exists boss_reward (id int primary key auto_increment, tier int not null default 0, boss varchar(64) not null default '', "
                + "item_id int not null, qty_min int not null default 1, qty_max int not null default 1, rate int not null default 10000, "
                + "hsd_days varchar(64) not null default '', perm_rate int not null default 0, note varchar(255) not null default '')");
        if (exists(con, "select 1 from server_migration where name = 'dot61_boss_reward'")) {
            return changed;
        }
        for (int[] r : BOSS_REWARD) {
            changed += update(con, "insert into boss_reward (tier, item_id, qty_min, qty_max, rate) values (" + r[0] + ", " + r[1] + ", " + r[2] + ", " + r[3] + ", " + r[4] + ")");
        }
        changed += update(con, "insert into server_migration (name) values ('dot61_boss_reward')");
        return changed;
    }

    /** Đợt 62: boss chỉ rơi vàng theo bậc; bỏ điểm sự kiện và quà thêm (đồ đua top không cho rơi). */
    private static int runDot62(Connection con) throws Exception {
        if (exists(con, "select 1 from server_migration where name = 'dot62_boss_gold_only'")) {
            return 0;
        }
        int changed = update(con, "delete from boss_reward where item_id <> -1");
        changed += update(con, "insert into server_migration (name) values ('dot62_boss_gold_only')");
        return changed;
    }

    /**
     * Đợt 63: boss không rơi vàng; quà thêm là nguyên liệu farm (không phải đồ đua top).
     * {bậc, item, sl từ, đến, tỉ lệ /10000}. Mỗi dòng tung riêng.
     */
    private static final int[][] BOSS_DROP_63 = {
        // bậc 1: đá nâng cấp, ngọc rồng 7 sao, đậu thần cấp 7, sao pha lê
        {1, 220, 1, 1, 400}, {1, 221, 1, 1, 400}, {1, 222, 1, 1, 400}, {1, 223, 1, 1, 400}, {1, 224, 1, 1, 400},
        {1, 20, 1, 1, 1500}, {1, 65, 1, 3, 2000}, {1, 441, 1, 1, 150}, {1, 442, 1, 1, 150}, {1, 443, 1, 1, 150}, {1, 444, 1, 1, 150},
        {1, 445, 1, 1, 150}, {1, 446, 1, 1, 150}, {1, 447, 1, 1, 150},
        // bậc 2: thêm mảnh vỡ bông tai, cuồng nộ / bổ huyết / bổ khí
        {2, 220, 1, 2, 500}, {2, 221, 1, 2, 500}, {2, 222, 1, 2, 500}, {2, 223, 1, 2, 500}, {2, 224, 1, 2, 500},
        {2, 19, 1, 1, 1500}, {2, 20, 1, 1, 1500}, {2, 352, 1, 3, 2000}, {2, 441, 1, 1, 250}, {2, 442, 1, 1, 250}, {2, 443, 1, 1, 250},
        {2, 444, 1, 1, 250}, {2, 445, 1, 1, 250}, {2, 446, 1, 1, 250}, {2, 447, 1, 1, 250}, {2, 933, 1, 3, 1000},
        {2, 381, 1, 1, 500}, {2, 382, 1, 1, 500}, {2, 383, 1, 1, 500},
        // bậc 3: thêm mảnh hồn bông tai, viên capsule kì bí
        {3, 220, 2, 3, 600}, {3, 221, 2, 3, 600}, {3, 222, 2, 3, 600}, {3, 223, 2, 3, 600}, {3, 224, 2, 3, 600},
        {3, 18, 1, 1, 1500}, {3, 19, 1, 1, 1500}, {3, 523, 1, 3, 2000}, {3, 441, 1, 1, 400}, {3, 442, 1, 1, 400}, {3, 443, 1, 1, 400},
        {3, 444, 1, 1, 400}, {3, 445, 1, 1, 400}, {3, 446, 1, 1, 400}, {3, 447, 1, 1, 400}, {3, 933, 2, 5, 1500}, {3, 934, 1, 1, 500},
        {3, 381, 1, 1, 800}, {3, 382, 1, 1, 800}, {3, 383, 1, 1, 800}, {3, 380, 1, 1, 300},
        // bậc 4: thêm đá may mắn cấp 1
        {4, 220, 3, 5, 800}, {4, 221, 3, 5, 800}, {4, 222, 3, 5, 800}, {4, 223, 3, 5, 800}, {4, 224, 3, 5, 800},
        {4, 17, 1, 1, 1500}, {4, 18, 1, 1, 1500}, {4, 441, 1, 2, 600}, {4, 442, 1, 2, 600}, {4, 443, 1, 2, 600}, {4, 444, 1, 2, 600},
        {4, 445, 1, 2, 600}, {4, 446, 1, 2, 600}, {4, 447, 1, 2, 600}, {4, 933, 3, 8, 2500}, {4, 934, 1, 2, 1000}, {4, 380, 1, 1, 600},
        {4, 1079, 1, 1, 500},
        // bậc 5: thêm đá may mắn cấp 2
        {5, 220, 5, 10, 1000}, {5, 221, 5, 10, 1000}, {5, 222, 5, 10, 1000}, {5, 223, 5, 10, 1000}, {5, 224, 5, 10, 1000},
        {5, 17, 1, 1, 2000}, {5, 441, 2, 3, 800}, {5, 442, 2, 3, 800}, {5, 443, 2, 3, 800}, {5, 444, 2, 3, 800},
        {5, 445, 2, 3, 800}, {5, 446, 2, 3, 800}, {5, 447, 2, 3, 800}, {5, 933, 5, 10, 3000}, {5, 934, 1, 3, 1500}, {5, 380, 1, 1, 1000},
        {5, 1080, 1, 1, 500},};

    private static int runDot63(Connection con) throws Exception {
        if (exists(con, "select 1 from server_migration where name = 'dot63_boss_drop'")) {
            return 0;
        }
        int changed = update(con, "delete from boss_reward where boss = '' or item_id = -1 or item_id = -4");
        for (int[] r : BOSS_DROP_63) {
            changed += update(con, "insert into boss_reward (tier, item_id, qty_min, qty_max, rate) values (" + r[0] + ", " + r[1] + ", " + r[2] + ", " + r[3] + ", " + r[4] + ")");
        }
        changed += update(con, "insert into server_migration (name) values ('dot63_boss_drop')");
        return changed;
    }

    /** Đợt 65: chỉ số theo ảnh shop Quy Lão "Đổi thưởng". {template, option, param từ, đến} */
    private static final int[][] OPTIONS_65 = {
        {1981, 50, 10, 10}, {1981, 0, 7000, 7000}, // Bư tập tạ
        {2023, 100, 10, 10}, {2023, 236, 10, 10}, // Bóng mờ sao may mắn
        {2022, 50, 8, 8}, {2022, 77, 8, 8}, {2022, 103, 8, 8}, {2022, 100, 15, 15}, {2022, 96, 15, 15}, {2022, 85, 0, 0}, // Ván bay sao băng
        {2025, 50, 15, 15}, {2025, 77, 15, 15}, {2025, 103, 15, 15}, {2025, 100, 15, 15}, {2025, 95, 15, 15}, // Pet Mèo thần tài
        {1711, 50, 8, 8}, {1711, 77, 8, 8}, {1711, 103, 8, 8}, {1711, 85, 0, 0}, {1711, 226, 5, 5}, {1711, 236, 10, 10}, // Cân Đẩu Vân Thơ Mộng
        {1713, 50, 18, 18}, {1713, 77, 15, 15}, {1713, 103, 15, 15}, {1713, 226, 3, 3}, {1713, 236, 15, 15}, // Khủng Long Thơ Mộng
        {1682, 50, 15, 15}, {1682, 77, 15, 15}, {1682, 103, 18, 18}, {1682, 236, 15, 15}, {1682, 253, 20, 20}, // Pet Hải Ly
        {1698, 50, 26, 26}, {1698, 77, 25, 25}, {1698, 103, 25, 25}, {1698, 148, 45, 45}, {1698, 94, 15, 15}, {1698, 236, 15, 15}, // Urôn Trư Bát Giới
        {1930, 50, 7, 7}, {1930, 77, 7, 7}, {1930, 103, 7, 7}, // Cánh thiên thần sa ngã (Đệ)
        {1929, 50, 17, 17}, {1929, 77, 17, 17}, {1929, 103, 17, 17}, {1929, 148, 100, 100}, {1929, 10, 5, 10}, // Pet Ma tốc độ
        {1724, 50, 8, 8}, {1724, 77, 10, 10}, {1724, 103, 8, 8}, {1724, 117, 3, 3}, {1724, 85, 0, 0}, // Ván bay Sọ Dừa
        {1722, 50, 17, 17}, {1722, 77, 15, 15}, {1722, 103, 15, 15}, {1722, 5, 10, 10}, {1722, 94, 7, 7}, // Cánh Thiên thần - Ác quỷ
        {1709, 50, 29, 29}, {1709, 77, 20, 20}, {1709, 103, 20, 20}, {1709, 8, 5, 5}, {1709, 5, 25, 25}, // CT Android 21 Evil
        {1887, 50, 7, 7}, {1887, 77, 7, 7}, {1887, 103, 7, 7}, {1887, 97, 10, 10}, {1887, 204, 3, 3}, {1887, 84, 0, 0},}; // Ván bay cung trăng

    /** Đợt 65: Ngục tù may mắn (map 187, bản sao Hành tinh ngục tù), cột hạn phiếu, chỉ số vật phẩm Quy Lão. */
    private static int runDot65(Connection con) throws Exception {
        int changed = 0;
        if (!columnExists(con, "player_extra", "lucky_map_until")) {
            changed += 1 + update(con, "alter table player_extra add column lucky_map_until bigint not null default 0");
        }
        if (!exists(con, "select 1 from map_template where id = 187")) {
            changed += update(con, "insert into map_template (id, NAME, zones, max_player, data, type, planet_id, bg_type, tile_id, bg_id, waypoints, mobs, npcs, is_map_double) "
                    + "select 187, 'Ngục tù may mắn', zones, max_player, data, type, planet_id, bg_type, tile_id, bg_id, waypoints, mobs, npcs, is_map_double from map_template where id = 155");
        }
        changed += update(con, "update item_template set description = 'Vào Ngục tù may mắn (vàng rơi nhiều) trong thời gian sử dụng' where id = 2021 and description = 'Vật phẩm sự kiện'");
        if (exists(con, "select 1 from server_migration where name = 'dot65_items'")) {
            return changed;
        }
        java.util.Set<Integer> done = new java.util.HashSet<>();
        for (int[] o : OPTIONS_65) {
            if (done.add(o[0])) {
                changed += update(con, "delete from item_template_option where template_id = " + o[0]);
            }
            changed += update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) values (" + o[0] + ", " + o[1] + ", " + o[2] + ", " + o[3] + ", 100, 0)");
        }
        if (!exists(con, "select 1 from item_template_option where template_id = 2021")) {
            changed += update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) values (2021, 1, 1440, 1440, 100, 0), (2021, 30, 1, 1, 100, 0)");
        }
        changed += update(con, "insert into server_migration (name) values ('dot65_items')");
        return changed;
    }

    /** Đợt 66: Pet Ma tốc độ 10% có dòng chống lạnh; mô tả "chỉ dành cho đệ tử". */
    private static int runDot66(Connection con) throws Exception {
        if (exists(con, "select 1 from server_migration where name = 'dot66'")) {
            return 0;
        }
        int changed = 0;
        if (!exists(con, "select 1 from item_template_option where template_id = 1929 and option_id = 106")) {
            changed += update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) values (1929, 106, 0, 0, 10, 0)");
        }
        changed += update(con, "update item_template set description = 'Vật phẩm sự kiện (chỉ dành cho đệ tử)' where id in (1930, 1981)");
        changed += update(con, "insert into server_migration (name) values ('dot66')");
        return changed;
    }

    private static boolean columnExists(Connection con, String table, String column) throws Exception {
        return exists(con, "select 1 from information_schema.columns where table_schema = database() and table_name = '" + table + "' and column_name = '" + column + "'");
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
