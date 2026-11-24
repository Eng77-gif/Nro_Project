package nro.models.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import nro.models.data.LocalManager;
import nro.models.player.Player;
import nro.models.player_system.Template.ItemTemplate;
import nro.models.server.Client;

/**
 * Hình dáng cho bảng xếp hạng (msg -96) mà không tải đầy đủ người chơi: người online / bot lấy trực tiếp,
 * người offline đọc 1 truy vấn gộp (tên, hành tinh, đồ đang mặc) + hình lúc lưu cuối (player_look: có hợp thể / biến hình).
 * Mỗi phần tử: {name, head, body, leg, gender}.
 */
public final class TopLook {

    private TopLook() {
    }

    public static Map<Long, Object[]> of(Collection<Long> ids) {
        Map<Long, Object[]> out = new HashMap<>();
        StringBuilder off = new StringBuilder();
        for (Long id : ids) {
            Player p = Client.gI().getPlayer(id);
            if (p == null && nro.models.Bot.BotWorld.isBotId(id)) {
                p = nro.models.Bot.BotWorld.gI().find(id);
            }
            if (p != null) {
                out.put(id, new Object[]{p.name, (int) p.getHead(), (int) p.getBody(), (int) p.getLeg(), (int) p.gender});
            } else if (!out.containsKey(id)) {
                off.append(off.length() == 0 ? "" : ",").append(id);
            }
        }
        if (off.length() == 0) {
            return out;
        }
        try (Connection con = LocalManager.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement("select id, name, head, gender, items_body from player where id in (" + off + ")");
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getLong(1), look(rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getString(5)));
                }
            }
            try (PreparedStatement ps = con.prepareStatement("select player_id, head, body, leg from player_look where player_id in (" + off + ")");
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Object[] lk = out.get(rs.getLong(1));
                    if (lk != null) {
                        lk[1] = rs.getInt(2);
                        lk[2] = rs.getInt(3);
                        lk[3] = rs.getInt(4);
                    }
                }
            } catch (Exception ignored) { // chưa có bảng: dùng hình từ đồ đang mặc
            }
        } catch (Exception e) {
            nro.models.utils.Logger.error("Lỗi đọc hình bảng xếp hạng: " + e.getMessage() + "\n");
        }
        return out;
    }

    /** Cải trang (ô 5) nếu có, không thì áo / quần / mặc định theo hành tinh. */
    static Object[] look(String name, int head, int gender, String body) {
        int b = gender == 1 ? 59 : 57, l = gender == 1 ? 60 : 58;
        try {
            org.json.simple.JSONArray arr = (org.json.simple.JSONArray) org.json.simple.JSONValue.parse(body);
            int[] ids = new int[6];
            for (int i = 0; i < 6; i++) {
                ids[i] = -1;
                if (arr != null && i < arr.size()) {
                    org.json.simple.JSONArray it = (org.json.simple.JSONArray) org.json.simple.JSONValue.parse(String.valueOf(arr.get(i)));
                    ids[i] = ItemService.merged(Short.parseShort(String.valueOf(it.get(0))));
                }
            }
            ItemTemplate ao = ids[0] < 0 ? null : ItemService.gI().getTemplate(ids[0]);
            ItemTemplate quan = ids[1] < 0 ? null : ItemService.gI().getTemplate(ids[1]);
            ItemTemplate ct = ids[5] < 0 ? null : ItemService.gI().getTemplate(ids[5]);
            if (ao != null) {
                b = ao.part;
            }
            if (quan != null) {
                l = quan.part;
            }
            if (ct != null) {
                head = ct.head != -1 ? ct.head : head;
                b = ct.body != -1 ? ct.body : b;
                l = ct.leg != -1 ? ct.leg : l;
            }
        } catch (Exception ignored) { // dữ liệu hỏng: dùng hình mặc định
        }
        return new Object[]{name, head, b, l, gender};
    }
}
