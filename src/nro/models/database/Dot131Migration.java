package nro.models.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import nro.models.utils.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONValue;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 131: thêm 2 nhiệm vụ còn thiếu sau "Tiêu diệt quái vật" (16), như bản chính thức:
 * - 17 Nhiệm vụ thử thách: đạt 15 triệu sức mạnh, thắng đại hội võ thuật (vòng 2, hoặc 3 lần vòng 1), báo cáo sư phụ.
 * - 18 Nhiệm vụ cam go: 25 triệu sức mạnh, diệt Trung Úy Trắng (doanh trại), 30 triệu sức mạnh, đến đại hội võ thuật,
 *   nhặt Mảnh giấy có chữ 'MA', dùng mảnh giấy tìm Đại Ma Vương (Siêu thánh thủy), diệt đám đệ tử rồi Pôcôlô.
 * Nhiệm vụ 17 - 30 cũ thành 19 - 32 (dữ liệu nhiệm vụ và tiến trình đã lưu của mọi nhân vật dời theo). Chạy 1 lần, trong 1 giao dịch.
 *
 * @author Việt
 */
public class Dot131Migration {

    public static void run(Connection con) {
        boolean auto = true;
        try {
            if (exists(con, "select 1 from server_migration where name = 'dot131_nv_thu_thach_cam_go'")) {
                return;
            }
            auto = con.getAutoCommit();
            con.setAutoCommit(false);
            update(con, "update task_main_template set id = id + 2 where id >= 17 order by id desc");
            update(con, "update task_sub_template set task_main_id = task_main_id + 2 where task_main_id >= 17");
            update(con, "insert into task_main_template (id, name, detail) values "
                    + "(17, 'Nhiệm vụ thử thách', 'Đạt 15 triệu sức mạnh\\nTham gia và chiến thắng vòng 2 hoặc chiến thắng 3 lần vòng 1 đại hội võ thuật\\n"
                    + "Thưởng 3 Tr sức mạnh\\nThưởng 3 Tr tiềm năng'),"
                    + "(18, 'Nhiệm vụ cam go', 'Đạt 25 triệu sức mạnh\\nVào doanh trại Độc Nhãn tìm diệt Trung Úy Trắng\\n"
                    + "Tìm Đại Ma Vương Pôcôlô qua mảnh giấy có chữ ''ma'' ở đại hội võ thuật\\nThưởng 4 Tr sức mạnh\\nThưởng 4 Tr tiềm năng')");
            String v = "insert into task_sub_template (task_main_id, NAME, max_count, notify, npc_id, map) values ";
            update(con, v + "(17, 'Đạt 15.000.000 sức mạnh', 1, '', -1, -1),"
                    + "(17, 'Đại hội võ thuật', 3, 'Thắng vòng 2 hoặc thắng 3 lần vòng 1 đại hội võ thuật', -1, 52),"
                    + "(17, 'Báo cáo với %10', 1, '', -5, -9)");
            update(con, v + "(18, 'Đạt 25 triệu sức mạnh', 1, '', -1, -1),"
                    + "(18, 'Diệt Trung Úy Trắng (Trại Độc Nhãn)', 1, 'Vào doanh trại Độc Nhãn cùng bang hội', -1, -1),"
                    + "(18, 'Báo cáo với %10', 1, '', -5, -9),"
                    + "(18, 'Đạt 30 Tr sức mạnh', 1, '', -1, -1),"
                    + "(18, 'Báo cáo với %10', 1, '', -5, -9),"
                    + "(18, 'Đến đại hội võ thuật', 1, '', -1, 52),"
                    + "(18, 'Tìm mảnh giấy có chữ ''ma''', 1, 'Mảnh giấy rơi ở đại hội võ thuật', -1, 52),"
                    + "(18, 'Sử dụng mảnh giấy tìm Đại Ma Vương', 1, '', -1, -1),"
                    + "(18, 'Diệt đám đệ tử của Ma Vương', 1, '', -1, -1),"
                    + "(18, 'Diệt Đại Ma Vương Pôcôlô', 1, '', -1, -1),"
                    + "(18, 'Báo cáo với %10', 1, '', -5, -9)");
            int moved = 0;
            try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("select id, data_task from player");
                    PreparedStatement up = con.prepareStatement("update player set data_task = ? where id = ?")) {
                while (rs.next()) {
                    String s = rs.getString("data_task");
                    Object o = s == null ? null : JSONValue.parse(s);
                    if (o instanceof JSONArray a && !a.isEmpty() && a.get(0) != null) {
                        int id = Integer.parseInt(a.get(0).toString());
                        if (id >= 17) {
                            a.set(0, id + 2);
                            up.setString(1, a.toJSONString());
                            up.setLong(2, rs.getLong("id"));
                            up.executeUpdate();
                            moved++;
                        }
                    }
                }
            }
            update(con, "insert into server_migration (name) values ('dot131_nv_thu_thach_cam_go')");
            con.commit();
            Logger.success("Đợt 131: thêm nhiệm vụ thử thách + cam go, dời " + moved + " nhân vật sang số nhiệm vụ mới\n");
        } catch (Exception e) {
            try {
                con.rollback();
            } catch (Exception ignored) {
            }
            Logger.error("Đợt 131: " + e.getMessage() + "\n");
        } finally {
            try {
                con.setAutoCommit(auto);
            } catch (Exception ignored) {
            }
        }
    }
}
