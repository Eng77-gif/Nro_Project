package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 116:
 * - Cửa hàng theo ảnh anh gửi. Bunma / Dende / Appule tab Đặc biệt: Gói 10 viên Capsule 3 ngọc, Viên Capsule đặc biệt 799, Nho tím 10,
 *   Gói 10 Rađa dò ngọc 5 ngọc, Tự động luyện tập 1 / 2 / 3 giá 1 / 9 / 22 ngọc (20 / 200 / 500 phút). Santa tab Hỗ trợ đứng đầu:
 *   Năng lượng E10 2 ngọc, Phiếu sao vàng may mắn 11.111, Cỏ bốn lá 10, Bùa x2 TN SM đệ tử 5, Tự động luyện tập 1 / 2 / 3.
 * - Dòng 5 món set Ốc Tiêu "Bất tử xung quanh khi đánh quái", set Gohan "+300% may mắn +50% vàng rơi từ quái".
 *
 * @author Việt
 */
public class Dot116Migration {

    /** {tab, mã vật phẩm, giá ngọc, phút tự động luyện tập (0: không)} theo đúng thứ tự trên cửa hàng. */
    private static final int[][] SPEC = {{193, 3, 0}, {194, 799, 0}, {211, 10, 0}, {361, 5, 0}, {521, 1, 20}, {1523, 9, 200}, {1524, 22, 500}};
    private static final int[][] SANTA = {{2035, 2, 0}, {2021, 11111, 0}, {1635, 10, 0}, {1628, 5, 0}, {521, 1, 20}, {1523, 9, 200}, {1524, 22, 500}};

    public static void run(Connection con) {
        try {
            if (!exists(con, "select 1 from server_migration where name = 'dot116_shops'")) {
                for (int tab : new int[]{3, 6, 9}) {
                    place(con, tab, SPEC);
                }
                place(con, 34, SANTA);
                update(con, "insert into server_migration (name) values ('dot116_shops')");
                Logger.success("Đợt 116: sắp lại cửa hàng Bunma / Dende / Appule / Santa theo ảnh\n");
            }
            update(con, "update item_option_template set NAME = '$(5 món Bất tử xung quanh khi đánh quái)' where id = 143");
            update(con, "update item_option_template set NAME = '$(5 món +300% may mắn +50% vàng rơi từ quái)' where id = 234");
        } catch (Exception e) {
            Logger.error("Đợt 116: " + e.getMessage() + "\n");
        }
    }

    /** Đặt các món lên đầu tab theo thứ tự (cửa hàng xếp theo create_time giảm dần), giá ngọc, thời gian tự động luyện tập. */
    private static void place(Connection con, int tab, int[][] list) throws Exception {
        for (int i = 0; i < list.length; i++) {
            int id = list[i][0], cost = list[i][1], minutes = list[i][2];
            if (!exists(con, "select 1 from item_shop where tab_id = " + tab + " and temp_id = " + id)) {
                update(con, "insert into item_shop (tab_id, temp_id, is_new, is_sell, type_sell, cost) values (" + tab + ", " + id + ", 0, 1, 1, " + cost + ")");
            }
            // trùng nhiều dòng (Tự động luyện tập có 2 dòng ở Santa): giữ dòng đầu, ẩn phần còn lại
            update(con, "update item_shop s join (select min(id) keep_id from item_shop where tab_id = " + tab + " and temp_id = " + id + ") k "
                    + "set s.is_sell = if(s.id = k.keep_id, 1, 0) where s.tab_id = " + tab + " and s.temp_id = " + id);
            update(con, "update item_shop set type_sell = 1, cost = " + cost + ", create_time = timestamp('2030-01-01') - interval " + i + " minute "
                    + "where tab_id = " + tab + " and temp_id = " + id + " and is_sell = 1");
            if (minutes > 0) {
                update(con, "delete o from item_shop_option o join item_shop s on s.id = o.item_shop_id where s.tab_id = " + tab + " and s.temp_id = " + id + " and o.option_id = 1");
                update(con, "insert into item_shop_option (item_shop_id, option_id, param) select id, 1, " + minutes + " from item_shop where tab_id = " + tab
                        + " and temp_id = " + id + " and is_sell = 1");
            }
        }
    }
}
