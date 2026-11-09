package nro.models.combine;

import java.util.List;
import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import nro.models.item.Item.ItemOption;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.SaoPhaLe10Service;
import nro.models.services.Service;

/**
 * Gỡ sao pha lê 10 ở Bà Hạt Mít: trang bị đã ép đủ 10 sao + 1 Mũi khoan siêu cấp -> gỡ viên ở lỗ thứ 10 (ngọc rồng xanh), trả lại viên
 * đó, trang bị còn 9 sao để khảm viên khác. Chỉ gỡ được lỗ 10.
 */
public final class GoSaoPhaLe10 {

    public static final int MUI_KHOAN = 2073;
    // chỉ số của ngọc rồng xanh 1 - 7 sao (theo SaoPhaLe10Service): mã chỉ số -> viên ngọc
    private static final int[][] NRX_OPT = {{251, 2064}, {50, 2065}, {77, 2066}, {103, 2067}, {14, 2068}, {94, 2069}, {95, 2070}};

    private GoSaoPhaLe10() {
    }

    private static Item gear(Player p) {
        for (Item it : p.combineNew.itemsCombine) {
            if (it != null && it.isNotNullItem() && it.template.type <= 4 && star(it) >= 10) {
                return it;
            }
        }
        return null;
    }

    private static int star(Item it) {
        for (ItemOption io : it.itemOptions) {
            if (io.optionTemplate.id == 102) {
                return io.param;
            }
        }
        return 0;
    }

    /** Dòng của viên ở lỗ 10: dòng chỉ số ngọc rồng xanh cuối cùng (khảm lỗ 10 là lần ghi sau cùng); 7 sao có thêm dòng 96 ngay sau. */
    static int[] slot10(List<ItemOption> ops) {
        for (int i = ops.size() - 1; i >= 0; i--) {
            int id = ops.get(i).optionTemplate.id;
            if (id == 96 && i > 0 && ops.get(i - 1).optionTemplate.id == 95 && ops.get(i - 1).param == ops.get(i).param) {
                return new int[]{i - 1, 2, 2070};
            }
            for (int[] m : NRX_OPT) {
                if (m[0] == id && m[1] != 2070) {
                    return new int[]{i, 1, m[1]};
                }
            }
        }
        return null;
    }

    public static void showInfoCombine(Player player) {
        Item it = gear(player);
        if (it == null) {
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, "Cần 1 trang bị đã ép đủ 10 sao pha lê", "Đóng");
            return;
        }
        int[] s = slot10(it.itemOptions);
        if (s == null) {
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, "Trang bị này không có viên ngọc rồng xanh ở lỗ thứ 10", "Đóng");
            return;
        }
        boolean khoan = InventoryService.gI().findItemBag(player, MUI_KHOAN) != null;
        String say = "|2|Gỡ sao pha lê 10 của " + it.template.name + "\n"
                + "|7|" + it.itemOptions.get(s[0]).getOptionString() + "\n"
                + "|2|Nhận lại 1 " + ItemService.gI().getTemplate(s[2]).name + ", trang bị còn 9 sao\n"
                + (khoan ? "|2|" : "|7|") + "Cần 1 Mũi khoan siêu cấp";
        CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.MENU_START_COMBINE, say, "Gỡ sao", "Từ chối");
    }

    public static void go(Player player) {
        Item it = gear(player);
        Item khoan = InventoryService.gI().findItemBag(player, MUI_KHOAN);
        if (it == null || khoan == null || InventoryService.gI().getCountEmptyBag(player) <= 0) {
            Service.gI().sendThongBao(player, khoan == null ? "Cần 1 Mũi khoan siêu cấp" : "Hành trang cần ít nhất 1 chỗ trống");
            return;
        }
        int[] s = slot10(it.itemOptions);
        if (s == null) {
            return;
        }
        for (int k = 0; k < s[1]; k++) {
            it.itemOptions.remove(s[0]);
        }
        for (ItemOption io : it.itemOptions) {
            if (io.optionTemplate.id == 102) {
                io.param -= 1;
            }
        }
        it.itemOptions.removeIf(io -> io.optionTemplate.id == SaoPhaLe10Service.OPT_HAO_QUANG); // hào quang chỉ cho đồ 10 sao
        InventoryService.gI().subQuantityItemsBag(player, khoan, 1);
        InventoryService.gI().addItemBag(player, ItemService.gI().createNewItem((short) s[2]));
        CombineService.gI().sendEffectSuccessCombine(player);
        InventoryService.gI().sendItemBags(player);
        Service.gI().sendThongBao(player, "Đã gỡ viên ở lỗ thứ 10, nhận lại " + ItemService.gI().getTemplate(s[2]).name);
        CombineService.gI().reOpenItemCombine(player);
    }
}
