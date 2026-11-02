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
        if (changed > 0) {
            Logger.success("Đợt 56: đã thêm Hào quang chân Sao Pha Lê 10 và cải trang mới (" + changed + " dòng)\n");
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
