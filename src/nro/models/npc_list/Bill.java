package nro.models.npc_list;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.map.service.NpcService;
import nro.models.services.TaskService;
import nro.models.map.service.ChangeMapService;
import nro.models.services.ItemService;
import nro.models.services.PlayerService;
import nro.models.services.Service;
import nro.models.shop.ShopService;

/**
 *
 * @author Việt
 *
 */
public class Bill extends Npc {

    public Bill(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (canOpenNpc(player)) {
            TaskService.gI().checkDoneTaskTalkNpc(player, this);
            player.idMark.setIndexMenu(ConstNpc.BASE_MENU);

            if (mapId == 154) {
                createOtherMenu(player, ConstNpc.BASE_MENU,
                        "...",
                        "Về\nthánh địa\nKaio", "Từ chối");
            } else {
                createOtherMenu(player, ConstNpc.BASE_MENU, // bỏ đổi thức ăn / Phiếu thức ăn (không có trong data gốc)
                        "Chưa tới giờ thi đấu, xem hướng dẫn để biết thêm chi tiết",
                        "Nói\nchuyện", "Hướng\ndẫn\nthêm", "Từ chối");
            }
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (!canOpenNpc(player)) {
            return;
        }

        switch (mapId) {
            case 48 -> {
                switch (player.idMark.getIndexMenu()) {
                    case ConstNpc.BASE_MENU -> {
                        switch (select) {
                            case 0 -> {
                                if (InventoryService.gI().canOpenBillShop(player)) {
                                    createOtherMenu(player, 2,
                                            "Đói bụng quá... ngươi mang cho ta 99 phần đồ ăn\n"
                                            + "ta sẽ cho một món đồ Hủy Diệt.\n"
                                            + "Nếu tâm trạng ta vui ngươi có thể nhận trang bị tăng đến 15%",
                                            "OK", "Từ chối");
                                    player.idMark.setIndexMenu(100);
                                } else {
                                    createOtherMenu(player, 2,
                                            "Ngươi trang bị đủ bộ 5 món trang bị Thần\n"
                                            + "và mang 99 phần đồ ăn tới đây...\n"
                                            + "rồi ta nói chuyện tiếp.",
                                            "OK");
                                    player.idMark.setIndexMenu(101);
                                }
                            }
                            case 1 ->
                                NpcService.gI().createTutorial(player, tempId, this.avartar, ConstNpc.HUONG_DAN_BILL);
                        }
                    }
                }
            }

            case 2 -> {
                if (select == 100 && InventoryService.gI().canOpenBillShop(player)) {
                    ShopService.gI().opendShop(player, "BILL", true);
                }
            }

            case 154 -> {
                if (select == 0) {
                    ChangeMapService.gI().changeMap(player, 50, -1, 318, 336);
                }
            }
        }
    }

}
