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
        dropIllGotten(b);
        useCards(b);
        wearBest(b);
        wearBestLook(b);
        tidyBag(b);
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
            Item cur = b.inventory.itemsBody.get(slot);
            int curRank = rank(cur), best = -1;
            long bestScore = score(cur);
            for (int i = 0; i < b.inventory.itemsBag.size(); i++) {
                Item it = b.inventory.itemsBag.get(i);
                if (it == null || !it.isNotNullItem() || it.template.type != slot || !wearable(b, it.template)) {
                    continue;
                }
                // hạng cao hơn thì mặc; cùng hạng (trừ set kích hoạt: giữ đủ bộ) thì mặc món chỉ số cao hơn (+ cấp nâng)
                if (rank(it) > curRank || (curRank < 3 && rank(it) == curRank && score(it) > bestScore)) {
                    best = i;
                    curRank = rank(it);
                    bestScore = score(it);
                }
            }
            if (best >= 0) {
                InventoryService.gI().itemBagToBody(b, best);
            }
        }
    }

    // ------------------------------------------------------------------ đồ ngon nhất cho mọi ô (cải trang, pet, đeo lưng, ván bay, bóng mờ...)
    /** Ô trang bị của món trong hành trang (như putItemBody), -2 = ô bóng mờ, -1 = không mặc được. */
    static int slotOf(Item it) {
        if (it == null || !it.isNotNullItem()) {
            return -1;
        }
        if (nro.models.services.Event2026Service.gI().isFoot(it)) {
            return -2;
        }
        return switch (it.template.type) {
            case 0, 1, 2, 3, 4, 5 -> it.template.type;
            case 32 -> 6;
            case 27 -> it.template.head >= 0 ? 7 : -1; // loại 27 không có hình (vật phẩm) thì không phải pet
            case 11 -> 8;
            case 23, 24 -> 9;
            case 25 -> 10;
            default -> -1;
        };
    }

    /** Độ ngon: hạng (set kích hoạt > thiên sứ > hủy diệt) rồi chỉ số (% tính nặng, chỉ số cộng thẳng tính nhẹ), dòng "Mặc giảm ..." trừ điểm. */
    static long score(Item it) {
        if (it == null || !it.isNotNullItem()) {
            return -1;
        }
        long s = rank(it) * 1_000_000_000L + it.template.strRequire / 1000;
        for (Item.ItemOption io : it.itemOptions) {
            if (io == null || io.optionTemplate == null || io.optionTemplate.name == null) {
                continue;
            }
            String n = io.optionTemplate.name;
            int id = io.optionTemplate.id;
            if (id == 30 || id == 93 || id == 73 || id == 102 || id == 107) { // không giao dịch, hạn dùng, ô sao pha lê... không phải chỉ số
                continue;
            }
            if (n.startsWith("Mặc giảm")) {
                s -= io.param * 3000L;
            } else if (n.contains("%")) {
                s += io.param * 1000L;
            } else {
                s += Math.min(io.param, 1_000_000) / 10;
            }
        }
        return s;
    }

    private static Item bodyAt(Bot b, int slot) {
        if (slot == -2) {
            return b.itemFoot;
        }
        return slot < b.inventory.itemsBody.size() ? b.inventory.itemsBody.get(slot) : null;
    }

    /** Cải trang, cờ, pet, đeo lưng, ván bay, ô 10, bóng mờ: mặc món ngon nhất trong hành trang nếu hơn món đang mặc. */
    private static void wearBestLook(Bot b) {
        for (int slot : new int[]{5, 6, 7, 8, 9, 10, -2}) {
            long cur = score(bodyAt(b, slot));
            int best = -1;
            for (int i = 0; i < b.inventory.itemsBag.size(); i++) {
                Item it = b.inventory.itemsBag.get(i);
                if (slotOf(it) == slot && wearable(b, it.template) && score(it) > cur) {
                    best = i;
                    cur = score(it);
                }
            }
            if (best >= 0) {
                InventoryService.gI().itemBagToBody(b, best);
            }
        }
    }

    // ------------------------------------------------------------------ mảnh thẻ rađa
    /** Dùng hết mảnh thẻ (loại 33) vào Rađa như bấm "Sử dụng", rồi bật những thẻ ngon nhất (tối đa RadarService.MAX_USED). */
    private static void useCards(Bot b) {
        for (int i = 0; i < b.inventory.itemsBag.size(); i++) {
            Item it = b.inventory.itemsBag.get(i);
            for (int k = 0; k < 200 && it != null && it.isNotNullItem() && it.template.type == 33; k++) {
                int q = it.quantity;
                nro.models.services_func.UseItem.gI().UseCard(b, it);
                if (it.quantity == q) {
                    break; // chưa đủ điều kiện thẻ trước / thẻ đã tối đa
                }
            }
        }
        if (b.Cards == null || b.Cards.isEmpty()) {
            return;
        }
        java.util.List<nro.models.radar.Card> owned = new java.util.ArrayList<>();
        for (nro.models.radar.Card c : b.Cards) {
            if (c != null && c.Level >= 1) {
                owned.add(c);
            }
        }
        owned.sort((x, y) -> Integer.compare(nro.models.services.RadarService.cardScore(y, y.Level), nro.models.services.RadarService.cardScore(x, x.Level)));
        boolean changed = false;
        for (int i = 0; i < owned.size(); i++) {
            byte want = (byte) (i < nro.models.services.RadarService.MAX_USED ? 1 : 0);
            if (owned.get(i).Used != want) {
                owned.get(i).Used = want;
                if (want == 1) {
                    owned.get(i).usedAt = System.currentTimeMillis();
                }
                changed = true;
            }
        }
        if (changed) {
            Service.gI().point(b);
        }
    }

    // ------------------------------------------------------------------ dọn hành trang
    /**
     * Mỗi lượt dọn hành trang; sắp đầy thì mua thêm ô (Mở rộng hành trang 100 ngọc, rương 50 triệu vàng như cửa hàng Santa), đồ kém hơn đồ đang mặc
     * thì đồ hạng cao / không hạn dùng cất rương, đồ thường hoặc có hạn dùng thì vứt. Chỉ đụng tới đồ mặc được, không đụng vật phẩm khác.
     */
    private static void tidyBag(Bot b) {
        if (InventoryService.gI().getCountEmptyBag(b) < 5 && b.inventory.itemsBag.size() < nro.models.player.Inventory.MAX_ITEMS_BAG && b.inventory.gem >= 300) {
            b.inventory.gem -= 100;
            b.inventory.itemsBag.add(ItemService.gI().createItemNull());
        }
        if (InventoryService.gI().getCountEmptyListItem(b.inventory.itemsBox) <= 1
                && b.inventory.itemsBox.size() < nro.models.player.Inventory.MAX_ITEMS_BOX && b.inventory.gold >= 250_000_000L) {
            b.inventory.gold -= 50_000_000L;
            b.inventory.itemsBox.add(ItemService.gI().createItemNull());
        }
        for (int i = b.inventory.itemsBag.size() - 1; i >= 0; i--) {
            Item it = b.inventory.itemsBag.get(i);
            int slot = slotOf(it);
            boolean otherGender = slot != -1 && it.template.gender < 3 && it.template.gender != b.gender;
            if (slot == -1 || (!otherGender && score(it) > score(bodyAt(b, slot)))) {
                continue; // không phải đồ mặc, hoặc ngon hơn đồ đang mặc (chưa đủ sức mạnh mặc thì giữ lại)
            }
            boolean expiring = it.itemOptions.stream().anyMatch(o -> o.optionTemplate != null && o.optionTemplate.id == 93);
            if ((rank(it) >= 1 || (!expiring && slot >= 5)) && InventoryService.gI().getCountEmptyListItem(b.inventory.itemsBox) > 0) {
                InventoryService.gI().itemBagToBox(b, i);
            } else if (rank(it) < 1) {
                b.inventory.itemsBag.set(i, ItemService.gI().createItemNull());
            }
        }
        InventoryService.gI().sortItems(b.inventory.itemsBag);
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
            if (!REAL_SHOPS.contains(shop.tagName) || !reach(b, shop.npcId)) {
                continue; // chỉ mua ở cửa hàng vàng / ngọc thật (Santa, Urôn) khi bot đã mở được map có NPC đó
            }
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

    /**
     * Cửa hàng bán bằng vàng / ngọc thật. Các shop khác (đổi điểm Quy Lão, shop bang, shop VIP, shop sự kiện) ghi giá bằng điểm /
     * điều kiện riêng nên bot không có điểm thì không được "mua".
     */
    private static final java.util.Set<String> REAL_SHOPS = java.util.Set.of("SANTA", "URON");

    /**
     * Gỡ cải trang / pet / đeo lưng / ván bay bot đang mặc mà không thể có thật: món chỉ bán ở shop đổi điểm, shop bang, shop VIP...
     * (trước đây bot "mua" được dù không có điểm) hoặc bán ở Santa / Urôn khi bot chưa mở được map có NPC đó. Đồ rơi / quà (không
     * bán ở cửa hàng nào) giữ nguyên.
     */
    private static void dropIllGotten(Bot b) {
        if (!b.accountBot) {
            return;
        }
        boolean changed = false;
        for (int slot : new int[]{5, 7, 8, 9}) {
            if (slot >= b.inventory.itemsBody.size() || !b.inventory.itemsBody.get(slot).isNotNullItem()) {
                continue;
            }
            int id = b.inventory.itemsBody.get(slot).template.id;
            boolean sold = false, legit = false;
            for (var shop : Manager.SHOPS) {
                for (var tab : shop.tabShops) {
                    for (ItemShop is : tab.itemShops) {
                        if (is.temp != null && is.temp.id == id) {
                            sold = true;
                            legit |= REAL_SHOPS.contains(shop.tagName) && reach(b, shop.npcId);
                        }
                    }
                }
            }
            if (sold && !legit) {
                b.inventory.itemsBody.set(slot, ItemService.gI().createItemNull());
                changed = true;
            }
        }
        if (changed) {
            Service.gI().point(b);
            Service.gI().Send_Caitrang(b);
        }
    }

    /** Bot đã vào được ít nhất 1 map có NPC bán hàng này chưa (Santa / Urôn ở Đảo Kamê, Đảo Guru, Vách núi đen: sau nhiệm vụ 7). */
    static boolean reach(Bot b, int npcId) {
        for (nro.models.map.Map m : Manager.MAPS) {
            for (nro.models.npc.Npc n : m.npcs) {
                if (n.tempId == npcId && b.canGo(m.mapId)) {
                    return true;
                }
            }
        }
        return false;
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
        if (low >= 0 && reach(b, nro.models.consts.ConstNpc.SANTA) && b.inventory.gem >= PET_BOOK_GEM[low] * 3L && SkillUtil.upSkillPet(b.pet.playerSkill.skills, low)) {
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
