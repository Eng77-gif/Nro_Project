package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 128: sự kiện hè 2 - đua top Vòng quay Thượng Đế ngọc / vàng (mỗi lượt quay = 1 điểm), phần thưởng theo diễn đàn.
 *
 * @author Việt
 */
public class Dot128Migration {

    public static final int TOP_VQ_NGOC = 2103, TOP_VQ_VANG = 2104;

    public static void run(Connection con) {
        try {
            if (exists(con, "select 1 from server_migration where name = 'dot128_vongquay'")) {
                return;
            }
            // {hạng từ, đến, Rađa NR VIP, Sách cũ (ngọc) / Đá bảo vệ (vàng), Capsule kích hoạt, Capsule vỡ, Phiếu đổi capsule}
            int[][] ranks = {{1, 1, 100, 70, 3, 1, 0}, {2, 2, 50, 60, 2, 0, 0}, {3, 3, 30, 50, 1, 0, 0},
                {4, 10, 20, 20, 0, 0, 50}, {11, 50, 10, 10, 0, 0, 20}, {51, 100, 5, 5, 0, 0, 10}};
            int[][] tops = {{TOP_VQ_NGOC, 1283}, {TOP_VQ_VANG, 987}};
            String[] names = {"Vòng quay Thượng Đế ngọc", "Vòng quay Thượng Đế vàng"};
            for (int t = 0; t < tops.length; t++) {
                int id = tops[t][0];
                update(con, "delete from season_event_top where id = " + id);
                update(con, "insert into season_event_top (id, event_id, name) values (" + id + ", " + Dot127Migration.SUMMER_2 + ", '" + names[t] + "')");
                update(con, "delete from season_event_top_reward where top_id = " + id);
                int[] daBaoVe = {60, 40, 30, 20, 10, 5};
                for (int k = 0; k < ranks.length; k++) {
                    int[] r = ranks[k];
                    int[][] items = {{1809, r[2]}, {tops[t][1], t == 0 ? r[3] : daBaoVe[k]}, {1559, r[4]}, {2111, r[5]}, {1690, r[6]}};
                    for (int[] it : items) {
                        if (it[1] > 0) {
                            update(con, "insert into season_event_top_reward (top_id, rank_from, rank_to, item_id, qty, hsd_days, min_point) values ("
                                    + id + ", " + r[0] + ", " + r[1] + ", " + it[0] + ", " + it[1] + ", '', 10)");
                        }
                    }
                }
            }
            update(con, "insert into server_migration (name) values ('dot128_vongquay')");
            Logger.success("Đợt 128: đua top vòng quay Thượng Đế (hè 2)\n");
        } catch (Exception e) {
            Logger.error("Đợt 128: " + e.getMessage() + "\n");
        }
    }
}
