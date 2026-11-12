package nro.models.database;

import java.sql.Connection;
import nro.models.utils.Logger;

import static nro.models.database.Dot77Migration.update;

/**
 * Đợt 124: sự kiện Pokémon thêm boss Đội Hỏa Tiễn Musashi x10, Kochiro x10 (cùng Meowth x20) như v10; Musashi / Kochiro rơi thêm Túi đựng vỏ Xên.
 *
 * @author Việt
 */
public class Dot124Migration {

    public static void run(Connection con) {
        try {
            update(con, "update season_event set bosses = '-377:20,-376:10,-375:10' where id = 19 and bosses = '-377:20'");
            update(con, "update season_event_drop set mobs = '-377,-376,-375' where event_id = 19 and item_id = 1665 and mobs = '-377'");
        } catch (Exception e) {
            Logger.error("Đợt 124: " + e.getMessage() + "\n");
        }
    }
}
