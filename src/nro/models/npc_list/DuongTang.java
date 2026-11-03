package nro.models.npc_list;

import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.services.NguHanhSonService;

/**
 * Đường Tăng: đưa người chơi vào Ngũ Hành Sơn, đổi điểm công đức lấy quà.
 *
 * @author Việt
 */
public class DuongTang extends Npc {

    public DuongTang(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (canOpenNpc(player)) {
            NguHanhSonService.gI().openDuongTang(this, player);
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (canOpenNpc(player)) {
            NguHanhSonService.gI().selectDuongTang(this, player, player.idMark.getIndexMenu(), select);
        }
    }
}
