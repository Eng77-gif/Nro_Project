package nro.models.npc_list;

import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.Service;

/**
 *
 * @author Việt
 * 
 */

public class GokuSSJ2 extends Npc {

    public GokuSSJ2(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    private static final int BI_KIEP = 590, CAN = 9999;

    @Override
    public void openBaseMenu(Player player) {
        if (canOpenNpc(player)) {
            int co = InventoryService.gI().getParam(player, 31, BI_KIEP);
            this.createOtherMenu(player, ConstNpc.BASE_MENU, "Hạ chiến binh Yardrat ở 3 map Hành tinh Yardart để nhặt Bí kiếp.\n"
                    + "Đủ 9.999 bí kiếp ta đổi cho võ phục Yardrat (dịch chuyển tức thời tới người chơi).\n"
                    + "Con đang có " + nro.models.utils.Util.numberToMoney(co) + " / 9.999 bí kiếp",
                    "Đổi\nvõ phục", "Về\nNúi khỉ vàng", "OK");
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (!canOpenNpc(player)) {
            return;
        }
        if (select == 1) {
            nro.models.map.service.ChangeMapService.gI().changeMapBySpaceShip(player, 80, -1, 800);
            return;
        }
        if (select != 0) {
            return;
        }
        int co = InventoryService.gI().getParam(player, 31, BI_KIEP);
        if (co < CAN) {
            Service.gI().sendThongBao(player, "Còn thiếu " + nro.models.utils.Util.numberToMoney(CAN - co) + " bí kiếp");
            return;
        }
        if (InventoryService.gI().getCountEmptyBag(player) <= 0) {
            Service.gI().sendThongBao(player, "Hành trang đã đầy");
            return;
        }
        InventoryService.gI().subParamItemsBag(player, BI_KIEP, 31, CAN);
        Item yardart = ItemService.gI().createNewItem((short) (Math.max(0, Math.min(2, player.gender)) + 592));
        yardart.itemOptions.add(new Item.ItemOption(47, 400)); // như v10
        yardart.itemOptions.add(new Item.ItemOption(97, 10));
        yardart.itemOptions.add(new Item.ItemOption(14, 15));
        yardart.itemOptions.add(new Item.ItemOption(147, 30));
        yardart.itemOptions.add(new Item.ItemOption(108, 10));
        yardart.itemOptions.add(new Item.ItemOption(33, 0)); // dịch chuyển tức thời
        InventoryService.gI().addItemBag(player, yardart);
        InventoryService.gI().sendItemBags(player);
        Service.gI().sendThongBao(player, "Bạn nhận được võ phục của người Yardrat");
    }
}
