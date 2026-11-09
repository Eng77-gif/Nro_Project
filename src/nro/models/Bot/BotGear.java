package nro.models.Bot;

import java.util.HashMap;
import java.util.Map;
import nro.models.item.Item;
import nro.models.player_system.Template.ItemTemplate;
import nro.models.server.Manager;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.shop.ItemShop;
import nro.models.utils.SkillUtil;
import nro.models.utils.Util;

/**
 * Trang bị của tài khoản bot như người chơi: mặc đồ tốt nhất (set kích hoạt > thiên sứ > hủy diệt > đồ thường), mua cải trang, pet,
 * đeo lưng, ván bay / thú cưỡi ở cửa hàng khi ô đó còn trống, mua sách nâng chiêu cho đệ tử, đủ sức mạnh và vàng thì chế đồ thiên sứ.
 */
final class BotGear {

    private static final Map<Long, Long> next = new HashMap<>();
    private static final int[] PET_BOOK_GEM = {100, 150, 200, 500}; // giá sách nâng chiêu 1 - 4 đệ tử ở cửa hàng (ngọc)
    private static final long TS_POWER = 150_000_000_000L, TS_GOLD = 2_000_000_000L;

    private BotGear() {
    }

    static void step(Bot b) {
        long now = System.currentTimeMillis();
        if (now < next.getOrDefault(b.id, 0L)) {
            return;
        }
        next.put(b.id, now + Util.nextInt(90, 180) * 1000L);
        wearBest(b);
        buyLook(b, 5);  // cải trang
        buyLook(b, 7);  // pet
        buyLook(b, 8);  // đeo lưng
        buyLook(b, 9);  // ván bay / thú cưỡi
        petSkills(b);
        craftThienSu(b);
    }

    /** Hạng đồ: set kích hoạt 3, thiên sứ 2, hủy diệt 1, còn lại 0. */
    static int rank(Item it) {
        if (it == null || !it.isNotNullItem()) {
            return -1;
        }
        return BotQuest.isSkh(it) ? 3 : it.isDTS() ? 2 : it.isDHD() ? 1 : 0;
    }

    private static boolean wearable(Bot b, ItemTemplate t) {
        return (t.gender == b.gender || t.gender == 3) && t.strRequire <= b.nPoint.power;
    }

    /** Áo, quần, găng, giầy, rada (ô 0 - 4): mặc món hạng cao nhất trong hành trang nếu hơn món đang mặc. */
    private static void wearBest(Bot b) {
        for (int slot = 0; slot <= 4 && slot < b.inventory.itemsBody.size(); slot++) {
            int curRank = rank(b.inventory.itemsBody.get(slot)), best = -1, bestRank = curRank;
            for (int i = 0; i < b.inventory.itemsBag.size(); i++) {
                Item it = b.inventory.itemsBag.get(i);
                if (it != null && it.isNotNullItem() && it.template.type == slot && wearable(b, it.template) && rank(it) > bestRank) {
                    best = i;
                    bestRank = rank(it);
                }
            }
            if (best >= 0) {
                InventoryService.gI().itemBagToBody(b, best);
            }
        }
    }

    private static boolean fits(int slot, ItemTemplate t) {
        return switch (slot) {
            case 5 -> t.type == 5;
            case 7 -> t.type == 27 && t.head >= 0;
            case 8 -> t.type == 11;
            case 9 -> t.type == 23 || t.type == 24;
            default -> false;
        };
    }

    /** Ô trống thì mua món đắt nhất mà bot dư sức trả (vàng: tối đa 20% số vàng, ngọc: tối đa 50% số ngọc) rồi mặc vào. */
    private static void buyLook(Bot b, int slot) {
        if (slot >= b.inventory.itemsBody.size() || b.inventory.itemsBody.get(slot).isNotNullItem()
                || InventoryService.gI().getCountEmptyBag(b) <= 0) {
            return;
        }
        for (int i = 0; i < b.inventory.itemsBag.size(); i++) { // có sẵn trong hành trang thì mặc, không mua thêm
            Item x = b.inventory.itemsBag.get(i);
            if (x != null && x.isNotNullItem() && fits(slot, x.template) && wearable(b, x.template)) {
                InventoryService.gI().itemBagToBody(b, i);
                return;
            }
        }
        ItemShop pick = null;
        for (var shop : Manager.SHOPS) {
            for (var tab : shop.tabShops) {
                for (ItemShop is : tab.itemShops) {
                    if (is.temp == null || is.cost <= 0 || !fits(slot, is.temp) || !wearable(b, is.temp)) {
                        continue;
                    }
                    boolean afford = is.typeSell == 0 ? is.cost <= b.inventory.gold / 5 : is.typeSell == 1 && is.cost <= b.inventory.gem / 2;
                    if (afford && (pick == null || is.cost > pick.cost)) {
                        pick = is;
                    }
                }
            }
        }
        if (pick == null) {
            return;
        }
        if (pick.typeSell == 0) {
            b.inventory.gold -= pick.cost;
        } else {
            b.inventory.gem -= pick.cost;
        }
        Item it = ItemService.gI().createNewItem((short) pick.temp.id);
        it.itemOptions.addAll(pick.options != null && !pick.options.isEmpty() ? Item.ItemOption.copyAll(pick.options)
                : ItemService.gI().getListOptionItemShop((short) pick.temp.id));
        InventoryService.gI().addItemBag(b, it);
        for (int i = 0; i < b.inventory.itemsBag.size(); i++) {
            Item x = b.inventory.itemsBag.get(i);
            if (x != null && x.isNotNullItem() && x.template.id == pick.temp.id) {
                InventoryService.gI().itemBagToBody(b, i);
                break;
            }
        }
        if (Util.isTrue(30, 100)) {
            Service.gI().chat(b, "vừa sắm " + pick.temp.name);
        }
    }

    /** Mua sách nâng chiêu đệ tử (ngọc, như cửa hàng) cho chiêu thấp cấp nhất, khi còn dư ngọc. */
    private static void petSkills(Bot b) {
        if (b.pet == null || b.pet.playerSkill == null) {
            return;
        }
        int low = -1;
        for (int i = 0; i < 4 && i < b.pet.playerSkill.skills.size(); i++) {
            var s = b.pet.playerSkill.skills.get(i);
            if (s != null && s.template != null && s.point > 0 && s.point < 7 && (low < 0 || s.point < b.pet.playerSkill.skills.get(low).point)) {
                low = i;
            }
        }
        if (low >= 0 && b.inventory.gem >= PET_BOOK_GEM[low] * 3L && SkillUtil.upSkillPet(b.pet.playerSkill.skills, low)) {
            b.inventory.gem -= PET_BOOK_GEM[low];
        }
    }

    /** Từ 150 tỷ sức mạnh, dư 2 tỷ vàng: chế 1 món thiên sứ cho ô chưa có đồ hạng cao (không đụng ô đang mặc set kích hoạt). */
    private static void craftThienSu(Bot b) {
        if (b.nPoint.power < TS_POWER || b.inventory.gold < TS_GOLD * 2 || InventoryService.gI().getCountEmptyBag(b) <= 0) {
            return;
        }
        for (int slot = 0; slot <= 4 && slot < b.inventory.itemsBody.size(); slot++) {
            if (rank(b.inventory.itemsBody.get(slot)) >= 2) {
                continue;
            }
            int id = slot == 4 ? 1060 + b.gender : 1048 + slot * 3 + b.gender; // áo / quần / găng / giầy / nhẫn theo hành tinh
            b.inventory.gold -= TS_GOLD;
            InventoryService.gI().addItemBag(b, ItemService.gI().DoThienSu(id, b.gender));
            wearBest(b);
            Service.gI().chat(b, "vừa chế được " + ItemService.gI().getTemplate(id).name);
            return;
        }
    }
}
