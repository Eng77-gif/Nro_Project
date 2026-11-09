package nro.models.panel;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import nro.models.data.LocalManager;
import nro.models.utils.Logger;

/**
 * Nhật ký chat thế giới và thông báo server (bảng chat_log), xem trên panel. Ghi ở luồng riêng để không chặn game.
 */
public final class ChatLog {

    public static final String WORLD = "world", NOTIFY = "notify", BROADCAST = "broadcast";
    private static final ExecutorService W = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "chat-log");
        t.setDaemon(true);
        return t;
    });
    private static volatile boolean ready;

    private ChatLog() {
    }

    public static void log(String type, String name, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        W.execute(() -> {
            try {
                if (!ready) {
                    LocalManager.executeUpdate("create table if not exists chat_log (id bigint auto_increment primary key, t timestamp not null default current_timestamp,"
                            + " type varchar(16) not null, name varchar(64) not null default '', text varchar(1000) not null, index (type, id))");
                    ready = true;
                }
                LocalManager.executeUpdate("insert into chat_log (type, name, text) values (?, ?, ?)", type, name == null ? "" : name,
                        text.length() > 1000 ? text.substring(0, 1000) : text);
            } catch (Exception e) {
                Logger.error("Không ghi được nhật ký chat: " + e.getMessage() + "\n");
            }
        });
    }

    /** Panel: tối đa 500 dòng mới nhất, lọc theo loại (rỗng = tất cả) và chữ cần tìm. */
    static List<Map<String, Object>> list(String type, String q) throws Exception {
        if (!ready) {
            LocalManager.executeUpdate("create table if not exists chat_log (id bigint auto_increment primary key, t timestamp not null default current_timestamp,"
                    + " type varchar(16) not null, name varchar(64) not null default '', text varchar(1000) not null, index (type, id))");
            ready = true;
        }
        String like = "%" + (q == null ? "" : q.trim()) + "%";
        return type == null || type.isBlank()
                ? PanelApi.rows("select date_format(t, '%d/%m %H:%i:%s') t, type, name, text from chat_log where name like ? or text like ? order by id desc limit 500", like, like)
                : PanelApi.rows("select date_format(t, '%d/%m %H:%i:%s') t, type, name, text from chat_log where type = ? and (name like ? or text like ?) order by id desc limit 500", type, like, like);
    }
}
