package nro.models.combine;
import nro.models.consts.ConstItem;
import nro.models.consts.ConstNpc;
import nro.models.services.Service;
import nro.models.utils.Util;
import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;

/**
 *
 * @author Việt
 */

public class NhapNgocRong {

    // 7 Ngọc Rồng 1 sao -> 1 Ngọc rồng Siêu Cấp: 150 Tr vàng + 1 Lọ nước phép, 50%; thất bại mất 1 viên 1 sao (trừ khi dùng đá bảo vệ)
    private static final long GOLD_SIEU_CAP = 150_000_000L;
    private static final int RATE_SIEU_CAP = 50;
    private static final int[] DA_BAO_VE = {987, 1143};

    private static Item nr(Player player) {
        if (player.combineNew.itemsCombine.size() != 1) {
            return null;
        }
        Item item = player.combineNew.itemsCombine.get(0);
        return item != null && item.isNotNullItem() && item.template.id >= 14 && item.template.id <= 20 && item.quantity >= 7 ? item : null;
    }

    private static Item daBaoVe(Player player) {
        for (int id : DA_BAO_VE) {
            Item it = InventoryService.gI().findItemBag(player, id);
            if (it != null && it.isNotNullItem()) {
                return it;
            }
        }
        return null;
    }

    public static void showInfoCombine(Player player) {
        if (InventoryService.gI().getCountEmptyBag(player) <= 0) {
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, "Hành trang cần ít nhất 1 chỗ trống", "Đóng");
            return;
        }
        Item item = nr(player);
        if (item == null) {
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, "Cần 7 viên ngọc rồng cùng loại (1 sao để lên Ngọc rồng Siêu Cấp)", "Đóng");
            return;
        }
        if (item.template.id == ConstItem.NGOC_RONG_1_SAO) {
            boolean gold = player.inventory.gold >= GOLD_SIEU_CAP;
            boolean lo = InventoryService.gI().findItemBag(player, ConstItem.LO_NUOC_PHEP) != null;
            String npcSay = "|1|Con có muốn biến 7 Ngọc Rồng 1 sao thành\n1 viên Ngọc rồng Siêu Cấp\n"
                    + "|2|Cần 7 Ngọc Rồng 1 sao\n"
                    + (gold ? "|2|" : "|7|") + "Cần " + Util.numberToMoney(GOLD_SIEU_CAP) + " vàng\n"
                    + (lo ? "|2|" : "|7|") + "Cần 1 Lọ nước phép\n"
                    + "|1|Tỉ lệ thành công: " + RATE_SIEU_CAP + "%\n"
                    + "|7|Nếu dùng đá bảo vệ sẽ không bị mất 1 viên ngọc rồng 1 sao khi thất bại.";
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.MENU_START_COMBINE, npcSay, "Làm phép", "Nâng cấp\ndùng đá\nbảo vệ", "Từ chối");
            return;
        }
        String npcSay = "|2|Con có muốn biến 7 " + item.template.name + " thành\n"
                + "1 viên " + ItemService.gI().getTemplate((short) (item.template.id - 1)).name + "\n"
                + "|7|Cần 7 " + item.template.name;
        CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.MENU_START_COMBINE, npcSay, "Làm phép", "Từ chối");
    }

    public static void nhapNgocRong(Player player) {
        nhapNgocRong(player, false);
    }

    public static void nhapNgocRong(Player player, boolean useStone) {
        if (InventoryService.gI().getCountEmptyBag(player) <= 0) {
            return;
        }
        Item item = nr(player);
        if (item == null) {
            return;
        }
        if (item.template.id != ConstItem.NGOC_RONG_1_SAO) {
            if (useStone) {
                return;
            }
            CombineService.gI().sendEffectCombineDB(player, item.template.iconID);
            InventoryService.gI().addItemBag(player, ItemService.gI().createNewItem((short) (item.template.id - 1)));
            InventoryService.gI().subQuantityItemsBag(player, item, 7);
            InventoryService.gI().sendItemBags(player);
            CombineService.gI().reOpenItemCombine(player);
            return;
        }
        Item lo = InventoryService.gI().findItemBag(player, ConstItem.LO_NUOC_PHEP);
        Item stone = useStone ? daBaoVe(player) : null;
        if (player.inventory.gold < GOLD_SIEU_CAP) {
            Service.gI().sendThongBao(player, "Không đủ vàng, cần " + Util.numberToMoney(GOLD_SIEU_CAP) + " vàng");
            return;
        }
        if (lo == null) {
            Service.gI().sendThongBao(player, "Cần 1 Lọ nước phép");
            return;
        }
        if (useStone && stone == null) {
            Service.gI().sendThongBao(player, "Không có đá bảo vệ");
            return;
        }
        player.inventory.gold -= GOLD_SIEU_CAP;
        InventoryService.gI().subQuantityItemsBag(player, lo, 1);
        if (stone != null) {
            InventoryService.gI().subQuantityItemsBag(player, stone, 1);
        }
        if (Util.isTrue(RATE_SIEU_CAP, 100)) {
            InventoryService.gI().subQuantityItemsBag(player, item, 7);
            InventoryService.gI().addItemBag(player, ItemService.gI().createNewItem((short) ConstItem.NGOC_RONG_SIEU_CAP));
            CombineService.gI().sendEffectSuccessCombine(player);
        } else {
            if (stone == null) {
                InventoryService.gI().subQuantityItemsBag(player, item, 1);
            }
            CombineService.gI().sendEffectFailCombine(player);
        }
        InventoryService.gI().sendItemBags(player);
        Service.gI().sendMoney(player);
        CombineService.gI().reOpenItemCombine(player);
    }
}
