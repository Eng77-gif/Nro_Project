package nro.models.npc_list;

import nro.models.consts.ConstNpc;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.map.service.ChangeMapService;

/**
 *
 * @author Việt
 * 
 */

public class GokuSSJ extends Npc {

    public GokuSSJ(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (canOpenNpc(player)) {
            switch (this.mapId) {
                case 80 ->
                    this.createOtherMenu(player, ConstNpc.BASE_MENU, "Ta mới hạ Fide, nhưng nó đã kịp đào 1 cái lỗ, hành tinh này sắp nổ tung rồi.\n"
                            + "Ta sẽ đưa con tới hành tinh Yardrat: hạ chiến binh ở đó nhặt Bí kiếp, đủ 9.999 thì gặp ta ở Yardart 3 đổi võ phục Yardrat",
                            "Tới\nYardrat", "Ở lại");
                case 131 ->
                    this.createOtherMenu(player, ConstNpc.BASE_MENU, "Đây là hành tinh Yardrat.\nHạ chiến binh Yardrat để nhặt Bí kiếp, đi tiếp sang phải tới Yardart 3 gặp ta đổi võ phục",
                            "OK", "Về\nNúi khỉ vàng");
                default ->
                    super.openBaseMenu(player);
            }
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (canOpenNpc(player)) {
            switch (player.idMark.getIndexMenu()) {
                case ConstNpc.BASE_MENU -> {
                    if (this.mapId == 131) {
                        if (select == 1) {
                            ChangeMapService.gI().changeMapBySpaceShip(player, 80, -1, 870);
                        }
                    } else if (this.mapId == 80) {
                        if (select == 0) {
                            ChangeMapService.gI().changeMapBySpaceShip(player, 131, -1, 870);
                        }
                    }
                }
            }
        }
    }
}
