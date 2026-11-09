package nro.models.npc_list;

import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.services.NguHanhSonService;

/**
 * Ngộ Không (bị phong ấn ở Ngũ Hành Sơn): nhận Quả Hồng Đào, cộng điểm công đức.
 *
 * @author Việt
 */
public class NgoKhong extends Npc {

    public NgoKhong(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (canOpenNpc(player)) {
            NguHanhSonService.gI().openNgoKhong(this, player);
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (canOpenNpc(player)) {
            if (player.idMark.getIndexMenu() == NguHanhSonService.MENU_NGO_KHONG) {
                NguHanhSonService.gI().selectNgoKhong(player, select);
            }
        }
    }
}
