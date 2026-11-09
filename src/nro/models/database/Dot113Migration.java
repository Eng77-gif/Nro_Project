package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 113: bảng web_bank_txn lưu giao dịch ngân hàng SePay gửi về (webhook /callback/sepay). sepay_id duy nhất để SePay gửi lại
 * cũng chỉ cộng tiền 1 lần; topup_id là yêu cầu nạp đã khớp, result ghi lý do khi không khớp.
 *
 * @author Việt
 */
public class Dot113Migration {

    public static void run(Connection con) {
        try {
            update(con, "create table if not exists web_bank_txn (id int auto_increment primary key, sepay_id bigint not null unique, gateway varchar(64), "
                    + "account_number varchar(64), amount bigint not null default 0, content varchar(500), transfer_type varchar(8), reference_code varchar(64), "
                    + "txn_time varchar(32), topup_id int null, result varchar(255), created_at datetime not null default current_timestamp, key (topup_id))");
        } catch (Exception e) {
            Logger.error("Đợt 113: " + e.getMessage() + "\n");
        }
    }
}
