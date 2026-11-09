package nro.models.services_dungeon;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import nro.models.data.LocalManager;
import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.utils.Logger;

/**
 * Ghi kết quả từng trận Ngọc Rồng Sao Đen (ai giữ được viên mấy sao, khu nào, bang nào) cho panel theo dõi. Ngày có Giải Ngọc Rồng
 * Sao Đen Đặc Biệt (bảng nrsd_tournament) thì người giữ ngọc thắng nhận thêm phần thưởng của giải.
 */
public final class NrsdResultService {

    private NrsdResultService() {
    }

    public static void record(Player winner, int star) {
        String tournament = null, reward = null;
        try (Connection con = LocalManager.getConnection()) {
            try (PreparedStatement ps = con.prepareStatement("select name, rewards from nrsd_tournament where play_date = curdate() order by id limit 1");
                    ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    tournament = rs.getString(1);
                    reward = give(winner, rs.getString(2));
                    Service.gI().sendThongBao(winner, tournament + ": " + reward);
                }
            }
            try (PreparedStatement ps = con.prepareStatement("insert into nrsd_result (star, map_id, zone_id, player_id, player_name, clan_id, clan_name, tournament, reward) values (?,?,?,?,?,?,?,?,?)")) {
                ps.setInt(1, star);
                ps.setInt(2, winner.zone.map.mapId);
                ps.setInt(3, winner.zone.zoneId);
                ps.setLong(4, winner.id);
                ps.setString(5, winner.name);
                ps.setObject(6, winner.clan != null ? winner.clan.id : null);
                ps.setString(7, winner.clan != null ? winner.clan.name : null);
                ps.setString(8, tournament);
                ps.setString(9, reward);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            Logger.error("Không ghi được kết quả NRSD: " + e.getMessage() + "\n");
        }
    }

    /** Trao phần thưởng dạng "itemId:sốLượng,..." vào hành trang, trả về mô tả đã trao (hoặc món nào không vừa hành trang). */
    static String give(Player p, String rewards) {
        StringBuilder sb = new StringBuilder();
        for (String part : rewards == null ? new String[0] : rewards.split(",")) {
            String[] kv = part.trim().split(":");
            if (kv.length != 2) {
                continue;
            }
            int id = Integer.parseInt(kv[0].trim()), qty = Integer.parseInt(kv[1].trim());
            if (ItemService.gI().getTemplate(id) == null) {
                continue;
            }
            Item it = ItemService.gI().createNewItem((short) id, qty);
            it.itemOptions.addAll(ItemService.gI().getListOptionItemShop((short) id));
            boolean ok = InventoryService.gI().addItemBag(p, it);
            sb.append(sb.length() > 0 ? ", " : "").append(qty).append(' ').append(ItemService.gI().getTemplate(id).name).append(ok ? "" : " (hành trang đầy)");
        }
        InventoryService.gI().sendItemBags(p);
        return sb.toString();
    }
}
