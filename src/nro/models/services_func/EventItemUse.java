package nro.models.services_func;

import java.util.Map;
import java.util.Set;
import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.ItemTimeService;
import nro.models.services.Service;

/**
 * Vật phẩm trước đây bấm "Sử dụng" không có phản hồi gì:
 * - Gói 30 đậu thần cấp 1 - 9: nhận 30 hạt đậu thần cấp tương ứng;
 * - đồ ăn sự kiện (Bánh tét, Bánh chưng, kẹo / súp Halloween, Mì thanh long, Cơm gà quay...): ăn như Bánh Pudding (+10% sức đánh);
 * - còn lại: báo vật phẩm dùng vào việc gì (nguyên liệu đổi quà ở NPC nào, hoặc mô tả) thay vì im lặng.
 *
 * @author Việt
 */
public final class EventItemUse {

    /** gói 30 đậu -> đậu thần */
    private static final Map<Integer, Integer> GOI_DAU = Map.of(293, 13, 294, 60, 295, 61, 296, 62, 297, 63, 298, 64, 299, 65, 596, 352, 597, 523);
    /** đồ ăn sự kiện dùng hiệu ứng "thức ăn" (+10% sức đánh) */
    private static final Set<Integer> MEALS = Set.of(752, 753, 899, 900, 902, 903, 1189, 1480, 1481, 1761, 1770, 1837);
    /** loại trang bị / thú cưỡi / pet: dùng ở chỗ khác, không báo */
    private static final Set<Integer> NO_HINT_TYPES = Set.of(0, 1, 2, 3, 4, 5, 6, 7, 11, 12, 23, 24, 25, 32, 33, 36, 72, 75);

    private EventItemUse() {
    }

    /** true nếu đã xử lý (item có thể đã bị trừ). */
    public static boolean use(Player pl, Item item) {
        int id = item.template.id;
        Integer dau = GOI_DAU.get(id);
        if (dau != null) {
            if (InventoryService.gI().getCountEmptyBag(pl) <= 0 && InventoryService.gI().findItemBag(pl, dau) == null) {
                Service.gI().sendThongBao(pl, "Hành trang không còn chỗ trống");
                return true;
            }
            Item beans = ItemService.gI().createNewItem(dau.shortValue(), 30);
            InventoryService.gI().subQuantityItemsBag(pl, item, 1);
            InventoryService.gI().addItemBag(pl, beans);
            InventoryService.gI().sendItemBags(pl);
            Service.gI().sendThongBao(pl, "Bạn nhận được 30 " + beans.template.name);
            return true;
        }
        if (id == 1559 || id == 1536 || id == 1537 || id == 1538) {
            return openSkh(pl, item, id);
        }
        if (MEALS.contains(id)) {
            pl.itemTime.lastTimeEatMeal = System.currentTimeMillis();
            pl.itemTime.isEatMeal = true;
            ItemTimeService.gI().removeItemTime(pl, pl.itemTime.iconMeal);
            pl.itemTime.iconMeal = item.template.iconID;
            String name = item.template.name;
            InventoryService.gI().subQuantityItemsBag(pl, item, 1);
            ItemTimeService.gI().sendAllItemTime(pl);
            Service.gI().point(pl);
            InventoryService.gI().sendItemBags(pl);
            Service.gI().sendThongBao(pl, "Bạn đã ăn " + name + ": tăng 10% sức đánh trong thời gian hiệu lực");
            return true;
        }
        return false;
    }

    /** đồ thường (2 mức) theo {loại: áo, quần, găng, giày, rađa}{hành tinh} - giống ItemService.randTempItemKichHoat */
    private static final int[][][] SKH_BASE = {{{0, 33}, {1, 41}, {2, 49}}, {{6, 35}, {7, 43}, {8, 51}},
        {{21, 24}, {22, 46}, {23, 53}}, {{27, 30}, {28, 47}, {29, 55}}, {{12, 57}, {12, 57}, {12, 57}}};

    /**
     * Capsule 1 món kích hoạt (1559): 1 món set kích hoạt ngẫu nhiên đúng hành tinh.
     * Hộp quà Set kích hoạt (1536) / 3 sao (1537) / 5 sao (1538): đủ 5 món cùng 1 set kích hoạt, bản 3 / 5 sao có sẵn 3 / 5 lỗ sao pha lê.
     */
    private static boolean openSkh(Player pl, Item item, int id) {
        int g = Math.max(0, Math.min(2, pl.gender));
        int need = id == 1559 ? 1 : 5;
        if (InventoryService.gI().getCountEmptyBag(pl) < need) {
            Service.gI().sendThongBao(pl, "Cần " + need + " ô trống trong hành trang");
            return true;
        }
        short boxIcon = item.template.iconID;
        int skh = ItemService.gI().randomSKHId((byte) g);
        int stars = id == 1537 ? 3 : id == 1538 ? 5 : 0;
        StringBuilder got = new StringBuilder();
        if (id == 1559) {
            Item it = ItemService.gI().createItemSKH(ItemService.gI().randTempItemKichHoat(g), skh);
            InventoryService.gI().addItemBag(pl, it);
            got.append(it.template.name);
        } else {
            for (int[][] type : SKH_BASE) {
                int[] pair = type[g];
                Item it = ItemService.gI().createItemSKH(pair[nro.models.utils.Util.nextInt(0, 1)], skh);
                if (stars > 0) {
                    it.itemOptions.add(new Item.ItemOption(107, stars));
                }
                InventoryService.gI().addItemBag(pl, it);
                got.append(got.length() > 0 ? ", " : "").append(it.template.name);
            }
        }
        InventoryService.gI().subQuantityItemsBag(pl, item, 1);
        InventoryService.gI().sendItemBags(pl);
        if (got.length() > 0) { // hiệu ứng mở hộp như các hộp quà khác, không báo bằng chữ
            Item first = null;
            for (Item x : pl.inventory.itemsBag) {
                if (x != null && x.isNotNullItem() && got.toString().startsWith(x.template.name)) {
                    first = x;
                    break;
                }
            }
            if (first != null) {
                nro.models.combine.CombineService.gI().sendEffectOpenItem(pl, boxIcon, first.template.iconID);
            }
        }
        return true;
    }

    /** Vật phẩm không dùng trực tiếp: báo công dụng thay vì im lặng. */
    public static void hint(Player pl, Item item) {
        if (item == null || item.template == null || NO_HINT_TYPES.contains((int) item.template.type)) {
            return;
        }
        String where = nro.models.services.SeasonEventService.gI().ingredientHint(item.template.id);
        String desc = item.template.description == null ? "" : item.template.description.trim();
        String text;
        if (where != null) {
            text = item.template.name + " là nguyên liệu sự kiện, " + where;
        } else if (!desc.isEmpty() && !desc.equalsIgnoreCase("VPSK") && !desc.equalsIgnoreCase("Vật phẩm sự kiện")) {
            text = item.template.name + ": " + desc;
        } else {
            text = item.template.name + " không sử dụng trực tiếp được, hãy giữ lại để đổi quà hoặc nâng cấp";
        }
        Service.gI().sendThongBao(pl, text);
    }
}
