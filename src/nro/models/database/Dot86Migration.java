package nro.models.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 86: Ngũ Hành Sơn làm lại theo bản chính thức (video): không có boss mới; Đường Tăng xuất hiện ở 3 làng đưa người chơi
 * vào bản đồ Ngũ Hành Sơn (122 - 124), quái ở đó rơi Quả Hồng Đào, Chi Chi bán Quả Hồng Đào / Quả Hồng Đào Chín, tặng Ngộ Không
 * lấy điểm công đức, đổi quà ở Đường Tăng (cải trang Bát Giới / Ngộ Không / Sa Tăng cho đệ tử, Yêu quái núi Lãng Lãng...).
 * Chỉ số tạm cho các vật phẩm Tây Du chưa có chỉ số (sửa lại trên panel khi có chỉ số thật).
 *
 * @author Việt
 */
public class Dot86Migration {

    /** {id, tên, icon, head, body, leg} - cải trang chỉ dành cho đệ tử */
    private static final Object[][] CT = {
        {2128, "Cải trang Bát Giới (Đệ)", 14082, 1583, 1584, 1585},
        {2129, "Cải trang Tôn Ngộ Không (Đệ)", 4520, 462, 468, 469},
        {2130, "Cải trang Sa Tăng (Đệ)", 3785, 344, 345, 346},};

    /** chỉ số tạm {item, {option, giá trị}...} */
    private static final int[][][] OPTS = {
        {{2128}, {50, 26}, {77, 25}, {103, 25}, {94, 10}},
        {{2129}, {50, 30}, {77, 25}, {103, 25}, {5, 15}},
        {{2130}, {50, 26}, {77, 30}, {103, 28}, {94, 12}},
        {{1024}, {50, 15}, {77, 12}, {103, 12}, {5, 10}}, // Như Ý Kim Cô Bổng
        {{1025}, {50, 13}, {77, 15}, {103, 12}, {94, 5}}, // Cửu Xỉ Đinh Ba
        {{1026}, {50, 13}, {77, 12}, {103, 15}, {108, 5}}, // Nguyệt Nha Sản
        {{1027}, {50, 12}, {77, 14}, {103, 14}, {101, 20}}, // Cửu Hoàn Thiên Tích Trượng
        {{1023}, {50, 10}, {77, 10}, {103, 10}, {148, 20}}, // Quạt ba tiêu
        {{1699}, {50, 12}, {77, 12}, {103, 12}, {236, 8}}, // Bồ cào 9 răng
    };

    public static void run(Connection con) {
        int changed = 0;
        try {
            if (!exists(con, "select 1 from information_schema.columns where table_schema = database() and table_name = 'player_extra' and column_name = 'cong_duc'")) {
                changed += 1 + update(con, "alter table player_extra add column cong_duc int not null default 0");
            }
            for (Object[] c : CT) {
                if (!exists(con, "select 1 from item_template where id = " + c[0])) {
                    try (PreparedStatement ps = con.prepareStatement("insert into item_template (id, TYPE, gender, NAME, description, level, icon_id, part, is_up_to_up, power_require, gold, gem, head, body, leg)"
                            + " values (?, 5, 3, ?, 'Sự kiện Ngũ Hành Sơn, chỉ dành cho đệ tử', 0, ?, -1, 0, 0, 0, 0, ?, ?, ?)")) {
                        ps.setInt(1, (int) c[0]);
                        ps.setString(2, (String) c[1]);
                        ps.setInt(3, (int) c[2]);
                        ps.setInt(4, (int) c[3]);
                        ps.setInt(5, (int) c[4]);
                        ps.setInt(6, (int) c[5]);
                        changed += ps.executeUpdate();
                    }
                }
            }
            for (int[][] o : OPTS) {
                int id = o[0][0];
                if (!exists(con, "select 1 from item_template_option where template_id = " + id)) {
                    for (int i = 1; i < o.length; i++) {
                        changed += update(con, "insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) values ("
                                + id + ", " + o[i][0] + ", " + o[i][1] + ", " + o[i][1] + ", 100, 0)");
                    }
                }
            }
            if (!exists(con, "select 1 from server_migration where name = 'dot86'")) {
                int ev = 12;
                changed += update(con, "update season_event set bosses = '', description = 'Đường Tăng xuất hiện ở Làng Aru, Làng Mori, Làng Kakarot: giúp giải cứu Ngộ Không bị phong ấn tại Ngũ Hành Sơn. "
                        + "Đánh quái ở Ngũ Hành Sơn nhặt Quả Hồng Đào (Chi Chi bán Quả Hồng Đào, Quả Hồng Đào Chín), tặng Ngộ Không lấy điểm công đức (1 / 5 điểm), "
                        + "đổi quà ở Đường Tăng: thêm ngày cải trang, cải trang Bát Giới / Ngộ Không / Sa Tăng cho đệ tử, Yêu quái núi Lãng Lãng.' where id = " + ev);
                changed += update(con, "delete from season_event_drop where event_id = " + ev);
                changed += update(con, "insert into season_event_drop (event_id, item_id, rate, qty_min, qty_max, source, daily_max, item_to, need_item, mobs, maps) values ("
                        + ev + ", 541, 800, 1, 2, 'mob', 300, 0, 0, '', '122,123,124')");
                changed += update(con, "delete from season_event_exchange where event_id = " + ev);
                changed += update(con, "insert into season_event_exchange (event_id, grp, name, need, gold, gem, ruby, cost_point, reward_item, reward_qty, reward_qty_max, reward_days, perm_rate, bonus_item, bonus_rate, open_now, event_point, top_id, top_point, npc)"
                        + " values (" + ev + ", 'Cửa\\nhàng', 'Quả Hồng Đào', '', 1000000, 0, 0, 0, 541, 1, 1, '', 0, 0, 0, 0, 0, 0, 0, '81')");
                changed += update(con, "insert into season_event_exchange (event_id, grp, name, need, gold, gem, ruby, cost_point, reward_item, reward_qty, reward_qty_max, reward_days, perm_rate, bonus_item, bonus_rate, open_now, event_point, top_id, top_point, npc)"
                        + " values (" + ev + ", 'Cửa\\nhàng', 'Quả Hồng Đào Chín', '', 0, 5, 0, 0, 542, 1, 1, '', 0, 0, 0, 0, 0, 0, 0, '81')");
                changed += update(con, "delete from season_event_top_reward where top_id = 1201");
                changed += update(con, "delete from season_event_top where id = 1201");
                changed += update(con, "delete from season_event_npc where event_id = " + ev);
                for (int m : new int[]{0, 7, 14}) {
                    changed += update(con, "insert into season_event_npc (event_id, npc_id, map_id, x, y) values (" + ev + ", 49, " + m + ", 0, 0)");
                }
                changed += update(con, "insert into server_migration (name) values ('dot86')");
            }
        } catch (Exception e) {
            Logger.error("Đợt 86: không cập nhật được database: " + e.getMessage() + "\n");
        }
        if (changed > 0) {
            Logger.success("Đợt 86: Ngũ Hành Sơn theo bản chính thức, chỉ số tạm vật phẩm Tây Du (" + changed + " dòng)\n");
        }
    }
}
