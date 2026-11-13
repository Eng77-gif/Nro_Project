package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 133:
 * - Quái Cađíc M (Sa mạc hoang vu, mẫu 95) là quái loại 1 "đi lại" với vùng di chuyển 0: client tự cho nó đi qua lại tại chỗ nên
 *   quay 2 đầu liên tục. Đổi thành loại 0 đứng yên như Mộc nhân / máy đo sức mạnh (DataGame.vsData 29 để client tải lại).
 * - Mô tả Cỏ bốn lá đúng tác dụng thật (UseItem / Mob.skhLuckBoost).
 *
 * @author Việt
 */
public class Dot133Migration {

    /**
     * Cửa hàng Santa tab "Hỗ trợ" (34) xếp theo create_time giảm dần (ShopDAO): đặt lại để món cùng nhóm đứng cạnh nhau.
     * {temp_id, type_sell} theo thứ tự từ trên xuống; type_sell 1 ngọc, 0 vàng.
     */
    static final int[][] HO_TRO = {{2035, 1}, {2021, 1}, {1825, 1}, {1717, 1}, {1703, 1}, {1716, 1}, {1652, 1}, {1653, 1}, {1635, 1}, {1628, 1},
        {521, 1}, {1523, 1}, {1524, 1}, {529, 1}, {530, 1}, {531, 1}, {534, 0}, {535, 0}, {536, 0}}; // theo ảnh bản chính thức

    static void sortHoTro(Connection con) throws Exception {
        for (int i = 0; i < HO_TRO.length; i++) {
            update(con, "update item_shop set create_time = timestamp('2030-01-01') - interval " + i + " minute where tab_id = 34 and temp_id = "
                    + HO_TRO[i][0] + " and type_sell = " + HO_TRO[i][1]);
        }
    }

    public static void run(Connection con) {
        try {
            // Cađíc M đi lại như v10 (loại 1, vùng 33, tốc độ 1): vùng 0 làm client cho nó quay qua quay lại tại chỗ liên tục
            // Cađíc M: như đa số quái đi lại (vùng 33px, tốc độ 2). Mẫu quái gửi cùng gói bản đồ nên phải tăng DataGame.vsMap thì client mới tải lại
            update(con, "update mob_template set type = 1, range_move = 33, speed = 2 where id in (95, 118) and (type <> 1 or range_move <> 33 or speed <> 2)");
            sortHoTro(con);
            // Loa to, Bùa x2 đệ tử: dòng "Không thể giao dịch" như bản chính thức
            for (int id : new int[]{1628, 1652, 1653}) {
                update(con, "insert into item_shop_option (item_shop_id, option_id, param) select s.id, 30, 0 from item_shop s where s.tab_id = 34 and s.temp_id = " + id
                        + " and not exists (select 1 from item_shop_option o where o.item_shop_id = s.id and o.option_id = 30)");
            }
            if (exists(con, "select 1 from server_migration where name = 'dot133_cadic_co4la'")) {
                return;
            }
            // cột description tối đa 75 ký tự; mặc đủ cải trang, pet, đeo lưng, ván bay thì x3 (Mob.skhLuckBoost)
            update(con, "update item_template set description = '30 phút/lá (cộng dồn tối đa 500 phút): +50% rơi đồ kích hoạt từ quái' where id = 1635");
            update(con, "insert into server_migration (name) values ('dot133_cadic_co4la')");
            Logger.success("Đợt 133: mô tả Cỏ bốn lá\n");
        } catch (Exception e) {
            Logger.error("Đợt 133: " + e.getMessage() + "\n");
        }
    }
}
