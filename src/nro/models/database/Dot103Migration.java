package nro.models.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 103: tài khoản bot cộng tiềm năng sai (trả giá 2.000 HP chỉ được 100 HP) nên bot hàng tỷ sức mạnh chỉ có vài trăm / vài trăm
 * nghìn HP, bị quái đánh chết liên tục. Chạy 1 lần: chỉ số gốc (HP / KI / sức đánh) của tài khoản bot về mức khởi đầu, trả lại tiềm
 * năng bằng sức mạnh; bot tự cộng lại đúng công thức (Bot.increasePoint). Không đụng nhân vật người chơi.
 *
 * @author Việt
 */
public class Dot103Migration {

    public static void run(Connection con) {
        try {
            // Danh hiệu Phá Cổ Trung Thu: hiệu ứng thật 505 (trăng, đèn lồng, thỏ ngọc, chữ Phá Cỗ Trung Thu) thay banner 300; người đã có đổi theo
            int b = update(con, "update data_badges set idEffect = 505 where idItem = 1885 and idEffect = 300")
                    + update(con, "update player set dataBadges = replace(dataBadges, '\"idBadGes\":\"300\"', '\"idBadGes\":\"505\"') where dataBadges like '%\"idBadGes\":\"300\"%'");
            if (b > 0) {
                Logger.success("Đợt 103: danh hiệu Phá Cổ Trung Thu dùng hiệu ứng 505\n");
            }
        } catch (Exception e) {
            Logger.error("Đợt 103: không đổi được hiệu ứng Phá Cổ Trung Thu: " + e.getMessage() + "\n");
        }
        try {
            if (exists(con, "select 1 from server_migration where name = 'dot103_botstats'")) {
                return;
            }
            int n = 0;
            try (PreparedStatement ps = con.prepareStatement("select p.id, p.data_point from player p join bot_account b on b.player_id = p.id where b.dedicated = 1");
                    ResultSet rs = ps.executeQuery();
                    PreparedStatement up = con.prepareStatement("update player set data_point = ? where id = ?")) {
                while (rs.next()) {
                    org.json.simple.JSONArray pt = (org.json.simple.JSONArray) org.json.simple.JSONValue.parse(rs.getString(2));
                    if (pt == null || pt.size() < 8) {
                        continue;
                    }
                    long power = Long.parseLong(String.valueOf(pt.get(1)));
                    pt.set(2, power); // tiềm năng
                    pt.set(5, 100);   // HP gốc
                    pt.set(6, 100);   // KI gốc
                    pt.set(7, 10);    // sức đánh gốc
                    up.setString(1, pt.toJSONString());
                    up.setLong(2, rs.getLong(1));
                    up.addBatch();
                    n++;
                }
                up.executeBatch();
            } catch (java.sql.SQLException e) {
                if (!String.valueOf(e.getMessage()).contains("bot_account")) {
                    throw e;
                }
            }
            update(con, "insert into server_migration (name) values ('dot103_botstats')");
            Logger.success("Đợt 103: " + n + " tài khoản bot được trả lại tiềm năng để cộng chỉ số đúng\n");
        } catch (Exception e) {
            Logger.error("Đợt 103: không cập nhật được chỉ số bot: " + e.getMessage() + "\n");
        }
    }
}
