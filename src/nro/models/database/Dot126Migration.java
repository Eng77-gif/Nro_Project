package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 126: sự kiện Phụ nữ Việt Nam 20/10 (13/10 - 27/10), Quy Lão mở "Đổi điểm" (điểm sự kiện từ nạp ngọc) 24 món theo ảnh
 * "Sự kiện đổi điểm Quy Lão". Giá dùng lại giá các sự kiện trước, món mới: hộp / rương 99, cải trang / pet / đeo lưng / ván bay 999, bóng mờ 1999.
 *
 * @author Việt
 */
public class Dot126Migration {

    static final int EV = 20;

    /** {mã vật phẩm, điểm, hạn dùng (ngày, 0 = vĩnh viễn)} */
    private static final int[][] SHOP = {
        {1757, 99, 30}, // Hộp Cađíc VIP
        {1840, 99, 30}, // Hộp Goku Day VIP 2025
        {1739, 99, 0}, // Hộp quà 20-11
        {1821, 99, 30}, // Trứng Vàng Rồng Nhí
        {1901, 99, 0}, // Rương Rồng Thần VIP
        {1713, 499, 0}, // Khủng Long Thơ Mộng
        {1682, 450, 0}, // Pet Hải Ly
        {1632, 999, 0}, // CT Himmel
        {2025, 499, 0}, // Pet Mèo thần tài
        {1941, 999, 0}, // Ba lô ngọc rồng
        {1735, 750, 0}, // Đeo lưng dụng cụ học tập
        {1711, 750, 0}, // Cân Đẩu Vân Thơ Mộng
        {1943, 999, 0}, // Bảng đen
        {2022, 999, 0}, // Ván bay sao băng
        {1997, 999, 0}, // Cải trang Chi Chi giỗ tổ
        {1731, 999, 0}, // CT Black Goku Rose
        {1795, 999, 0}, // CT Chi Chi Hoa Hồng Xanh
        {1567, 999, 0}, // CT Frieren
        {1476, 999, 0}, // Cải trang Bunma rực rỡ
        {1921, 999, 0}, // Cải trang Pan VIP
        {2023, 1999, 0}, // Bóng mờ sao may mắn (Bóng Ngôi Sao)
        {1976, 1999, 0}, // Hiệu ứng hoa Tết (Bóng Hoa Tết)
        {1971, 1999, 0}, // Bóng mờ NRO
        {1981, 1999, 0}, // Cờ đeo lưng Bư tập tạ
        {1963, 999, 0}, // Đeo lưng lồng đèn Tết
    };

    public static void run(Connection con) {
        try {
            if (exists(con, "select 1 from server_migration where name = 'dot126_2010'")) {
                return;
            }
            update(con, "delete from season_event where id = " + EV);
            update(con, "insert into season_event (id, name, lunar, month, day, days_before, days_after, enabled, bosses, description) values (" + EV
                    + ", 'Phụ nữ Việt Nam 20/10', 0, 10, 13, 0, 14, 1, '', 'Nạp ngọc tích điểm sự kiện (1 ngọc = 1 điểm), "
                    + "đến Quy Lão ở Đảo Kamê đổi điểm lấy cải trang, pet, đeo lưng, bóng mờ...')");
            update(con, "delete from season_event_exchange where event_id = " + EV);
            for (int[] s : SHOP) {
                update(con, "insert into season_event_exchange (event_id, grp, name, cost_point, reward_item, reward_qty, reward_days, npc) select " + EV
                        + ", 'Đổi\\nđiểm', NAME, " + s[1] + ", id, 1, '" + (s[2] > 0 ? s[2] : "") + "', '13' from item_template where id = " + s[0]);
            }
            update(con, "insert into server_migration (name) values ('dot126_2010')");
            Logger.success("Đợt 126: sự kiện 20/10, Quy Lão đổi điểm " + SHOP.length + " món\n");
        } catch (Exception e) {
            Logger.error("Đợt 126: " + e.getMessage() + "\n");
        }
    }
}
