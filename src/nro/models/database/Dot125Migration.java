package nro.models.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 125:
 * - Sa mạc hoang vu (165) dùng nền đồi cỏ xanh đá vàng (nền 14) như bản chính thức, mặt đất cỏ bộ ô gạch Trái Đất (1).
 * - Cađíc M đứng yên lại như cũ.
 * - Siêu hạng: đánh số lại hạng liên tục 1, 2, 3... mỗi lần khởi động (trước đây 2 người cùng hạng 2, không ai hạng 1).
 *
 * @author Việt
 */
public class Dot125Migration {

    public static void run(Connection con) {
        try {
            update(con, "update map_template set tile_id = 1, bg_id = 14, bg_type = 0 where id = 165");
            update(con, "update mob_template set range_move = 0 where id = 95"); // Cađíc M đứng yên như cũ (đi lại thì cứ đi tới đi lui quanh người chơi)
            renumberSuperRank(con);
        } catch (Exception e) {
            Logger.error("Đợt 125: " + e.getMessage() + "\n");
        }
    }

    static void renumberSuperRank(Connection con) throws Exception {
        List<int[]> rows = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement("select id, `rank` from super_rank where `rank` > 0 order by `rank`, id");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new int[]{rs.getInt(1), rs.getInt(2)});
            }
        }
        try (PreparedStatement ps = con.prepareStatement("update super_rank set `rank` = ? where id = ?")) {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i)[1] != i + 1) {
                    ps.setInt(1, i + 1);
                    ps.setInt(2, rows.get(i)[0]);
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        }
    }
}
