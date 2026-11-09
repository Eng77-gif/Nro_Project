package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 96: giá bán thỏi vàng (457) 37 triệu, thỏi vàng NRJ (2032) 150 triệu (giá lấy từ item_template.gold, dùng chung cho cửa hàng,
 * bán nhiều và bot).
 *
 * @author Việt
 */
public class Dot96Migration {

    public static void run(Connection con) {
        try {
            int n = update(con, "update item_template set gold = 37000000 where id = 457 and gold <> 37000000")
                    + update(con, "update item_template set gold = 150000000, description = 'Bán cho cửa hàng được 150 triệu vàng' where id = 2032 and gold <> 150000000");
            // Danh hiệu Phá cỗ Trung Thu VIP / Perfect: hiệu ứng mới (data/effect 516, 517; hiệu ứng cũ 301 đơn sơ, 302 vỡ hình)
            n += update(con, "update data_badges set idEffect = 516 where idItem = 2076 and idEffect = 301")
                    + update(con, "update data_badges set idEffect = 517 where idItem = 2081 and idEffect = 302")
                    // danh hiệu người chơi đã có lưu theo mã hiệu ứng: đổi theo
                    + update(con, "update player set dataBadges = replace(dataBadges, '\"idBadGes\":\"301\"', '\"idBadGes\":\"516\"') where dataBadges like '%\"idBadGes\":\"301\"%'")
                    + update(con, "update player set dataBadges = replace(dataBadges, '\"idBadGes\":\"302\"', '\"idBadGes\":\"517\"') where dataBadges like '%\"idBadGes\":\"302\"%'");
            if (n > 0) {
                Logger.success("Đợt 96: giá thỏi vàng / hiệu ứng danh hiệu đã cập nhật\n");
            }
        } catch (Exception e) {
            Logger.error("Đợt 96: không đổi được giá thỏi vàng: " + e.getMessage() + "\n");
        }
    }
}
