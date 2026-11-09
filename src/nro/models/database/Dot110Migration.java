package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.exists;
import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 110: "Tờ mảnh VIP" trong danh sách hộp quà chính là Mảnh thẻ VIP (2089), thêm vào Hộp bánh Trung Thu VIP 2026 (Picolo Kid không
 * có trong game nên bỏ). Bảng nrsd_result ghi kết quả từng trận Ngọc Rồng Sao Đen cho panel, bảng nrsd_tournament là các Giải Ngọc
 * Rồng Sao Đen Đặc Biệt (ngày diễn ra + phần thưởng cho người giữ ngọc thắng), kèm giải đầu tiên: 5 Hộp Ngọc Rồng Xanh, 99 Thỏi
 * Vàng, 200 Đá Bảo Vệ.
 *
 * @author Việt
 */
public class Dot110Migration {

    public static void run(Connection con) {
        try {
            if (!exists(con, "select 1 from item_box_reward where box_id = 2079 and item_id = 2089")) {
                update(con, "insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate) values (2079, 2089, 1, 3, 4, '', 0)");
                Logger.success("Đợt 110: Mảnh thẻ VIP vào Hộp bánh Trung Thu VIP 2026\n");
            }
            update(con, "create table if not exists nrsd_result (id int auto_increment primary key, played_at datetime not null default current_timestamp, "
                    + "star tinyint not null, map_id int not null, zone_id int not null, player_id bigint not null, player_name varchar(64), "
                    + "clan_id int null, clan_name varchar(64) null, tournament varchar(128) null, reward varchar(512) null, key (played_at), key (star))");
            update(con, "create table if not exists nrsd_tournament (id int auto_increment primary key, name varchar(128) not null, "
                    + "play_date date not null, rewards varchar(255) not null, created_at datetime not null default current_timestamp, key (play_date))");
            if (!exists(con, "select 1 from server_migration where name = 'dot110_giai_nrsd'")) {
                update(con, "insert into nrsd_tournament (name, play_date, rewards, created_at) values ('Giải Ngọc Rồng Sao Đen Đặc Biệt', '2026-11-15', '2071:5,457:99,987:200', '2026-11-08 00:00:00')");
                update(con, "insert into server_migration (name) values ('dot110_giai_nrsd')");
            }
        } catch (Exception e) {
            Logger.error("Đợt 110: " + e.getMessage() + "\n");
        }
    }
}
