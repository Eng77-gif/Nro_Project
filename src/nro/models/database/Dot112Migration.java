package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 112: bảng cho trang web người chơi (nro.models.web.WebServer): bài viết diễn đàn và phiếu tố cáo / báo lỗi / góp ý
 * (web_post), bình luận và trả lời của admin (web_reply), yêu cầu nạp chuyển khoản / thẻ cào (web_topup).
 *
 * @author Việt
 */
public class Dot112Migration {

    public static void run(Connection con) {
        try {
            update(con, "create table if not exists web_post (id int auto_increment primary key, kind varchar(10) not null, account_id int not null, "
                    + "author varchar(64) not null, title varchar(150) not null, target varchar(64) null, content text not null, "
                    + "status tinyint not null default 0, pinned tinyint not null default 0, replies int not null default 0, "
                    + "created_at datetime not null default current_timestamp, updated_at datetime not null default current_timestamp, "
                    + "key (kind, id), key (account_id))");
            update(con, "create table if not exists web_reply (id int auto_increment primary key, post_id int not null, account_id int not null, "
                    + "author varchar(64) not null, is_admin tinyint not null default 0, content text not null, "
                    + "created_at datetime not null default current_timestamp, key (post_id))");
            update(con, "create table if not exists web_topup (id int auto_increment primary key, account_id int not null, username varchar(20) not null, "
                    + "method varchar(8) not null, amount int not null, telco varchar(16) null, serial varchar(40) null, code varchar(40) null, "
                    + "status tinyint not null default 0, note varchar(255) null, credited int not null default 0, "
                    + "created_at datetime not null default current_timestamp, updated_at datetime not null default current_timestamp, "
                    + "key (account_id), key (status))");
            // trang web đăng nhập mới: lượt thích bài diễn đàn, ảnh đính kèm bài viết (sql/web_community.sql, sql/web_post-photo.sql)
            update(con, "create table if not exists web_post_like (post_id int not null, account_id int not null, primary key (post_id, account_id)) engine=InnoDB");
            if (!Dot77Migration.exists(con, "select 1 from information_schema.columns where table_schema = database() and table_name = 'web_post' and column_name = 'photo'")) {
                update(con, "alter table web_post add column photo mediumtext null");
            }
            // bài ghim sự kiện Halloween / 20-11 (sql/web_forum-events.sql), chạy 1 lần khi đã có tài khoản admin
            java.io.File ev = new java.io.File("sql/web_forum-events.sql");
            if (ev.isFile() && Dot77Migration.exists(con, "select 1 from account where is_admin = 1")
                    && !Dot77Migration.exists(con, "select 1 from server_migration where name = 'web_forum_events'")) {
                for (String sql : new String(java.nio.file.Files.readAllBytes(ev.toPath()), java.nio.charset.StandardCharsets.UTF_8).split(";\\s*\\n")) {
                    sql = sql.trim();
                    if (!sql.isEmpty() && !sql.equalsIgnoreCase("START TRANSACTION") && !sql.equalsIgnoreCase("COMMIT") && !sql.equalsIgnoreCase("COMMIT;")) {
                        update(con, sql);
                    }
                }
                update(con, "insert into server_migration (name) values ('web_forum_events')");
            }
        } catch (Exception e) {
            Logger.error("Đợt 112: " + e.getMessage() + "\n");
        }
    }
}
