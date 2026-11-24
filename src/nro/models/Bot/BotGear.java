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

    /** Làm ngay, bỏ qua thời gian chờ (panel giao việc). */
    static void stepNow(Bot b) {
        next.remove(b.id);
        step(b);
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

    /** Mã set kích hoạt của món (Cađíc, Kakarot, Nappa, Cađíc M...), 0 = không phải đồ kích hoạt. */
    static int setId(Item it) {
        if (it == null || !it.isNotNullItem()) {
            return 0;
        }
        for (Item.ItemOption o : it.itemOptions) {
            int id = o.optionTemplate.id;
            if ((id >= 127 && id <= 144) || (id >= 233 && id <= 248)) {
                return id;
            }
        }
        return 0;
    }

    /** Thứ tự ưu tiên khi số món bằng nhau: Cađíc, Kakarot, Nappa, Cađíc M rồi các set khác. */
    private static final int[] SET_PRIORITY = {134, 133, 135, 241};

    private static int setPriority(int set) {
        for (int i = 0; i < SET_PRIORITY.length; i++) {
            if (SET_PRIORITY[i] == set) {
                return SET_PRIORITY.length - i;
            }
        }
        return 0;
    }

    /** Set kích hoạt bot nhắm mặc đủ bộ: set có nhiều ô (áo, quần, găng, giầy, rada) nhất trong đồ đang mặc + hành trang. */
    private static int targetSet(Bot b) {
        java.util.Map<Integer, java.util.Set<Integer>> slots = new java.util.HashMap<>();
        java.util.List<Item> all = new java.util.ArrayList<>(b.inventory.itemsBag);
        for (int slot = 0; slot <= 4 && slot < b.inventory.itemsBody.size(); slot++) {
            all.add(b.inventory.itemsBody.get(slot));
        }
        for (Item it : all) {
            int set = setId(it);
            if (set > 0 && it.template.type <= 4 && wearable(b, it.template)) {
                slots.computeIfAbsent(set, k -> new java.util.HashSet<>()).add((int) it.template.type);
            }
        }
        int best = 0, bestN = 0;
        for (var e : slots.entrySet()) {
            int n = e.getValue().size();
            if (n > bestN || (n == bestN && setPriority(e.getKey()) > setPriority(best))) {
                best = e.getKey();
                bestN = n;
            }
        }
        return best;
    }

    /** Áo, quần, găng, giầy, rada (ô 0 - 4): ưu tiên mặc đủ bộ 1 set kích hoạt (cùng set), ô thiếu thì mặc món hạng cao nhất. */
    private static void wearBest(Bot b) {
        int target = targetSet(b);
        for (int slot = 0; slot <= 4 && slot < b.inventory.itemsBody.size(); slot++) {
            Item cur = b.inventory.itemsBody.get(slot);
            boolean curTarget = target > 0 && setId(cur) == target;
            int curRank = rank(cur), best = -1;
            long bestScore = score(cur);
            boolean bestTarget = curTarget;
            for (int i = 0; i < b.inventory.itemsBag.size(); i++) {
                Item it = b.inventory.itemsBag.get(i);
                if (it == null || !it.isNotNullItem() || it.template.type != slot || !wearable(b, it.template)) {
                    continue;
                }
                boolean itTarget = target > 0 && setId(it) == target;
                boolean better;
                if (itTarget != bestTarget) {
                    better = itTarget; // món đúng set đang gom luôn hơn món khác
                } else if (itTarget) {
                    better = score(it) > bestScore; // cùng set: món chỉ số cao hơn
                } else {
                    // hạng cao hơn thì mặc; cùng hạng (trừ set kích hoạt: giữ đủ bộ) thì mặc món chỉ số cao hơn (+ cấp nâng)
                    better = rank(it) > curRank || (curRank < 3 && rank(it) == curRank && score(it) > bestScore);
                }
                if (better) {
                    best = i;
                    curRank = rank(it);
                    bestScore = score(it);
                    bestTarget = itTarget;
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

    /** Giá trị 1 điểm của từng dòng % cho đồ ngoại hình (cải trang, pet, đeo lưng, ván bay, cờ...); dòng không có trong bảng: 8. */
    private static final java.util.Map<Integer, Integer> LOOK_WEIGHT = java.util.Map.ofEntries(
            java.util.Map.entry(50, 30), java.util.Map.entry(77, 20), java.util.Map.entry(103, 12), java.util.Map.entry(14, 30),
            java.util.Map.entry(5, 18), java.util.Map.entry(108, 20), java.util.Map.entry(191, 12), java.util.Map.entry(94, 25),
            java.util.Map.entry(97, 12), java.util.Map.entry(10, 35), java.util.Map.entry(204, 15), java.util.Map.entry(19, 6),
            java.util.Map.entry(95, 10), java.util.Map.entry(104, 6), java.util.Map.entry(80, 8), java.util.Map.entry(81, 4),
            java.util.Map.entry(148, 2), java.util.Map.entry(114, 2), java.util.Map.entry(101, 3), java.util.Map.entry(236, 1),
            java.util.Map.entry(268, 1), java.util.Map.entry(100, 1), java.util.Map.entry(253, 2));

    /** Lối chơi của bot (cố định theo mã bot): 0 sức đánh, 1 HP / trâu, 2 cân bằng. */
    static int style(Bot b) {
        return (int) Math.floorMod(b.id, 3L);
    }

    private static final java.util.Set<Integer> DAME_OPTS = java.util.Set.of(50, 14, 5, 10, 204, 19);
    private static final java.util.Set<Integer> TANK_OPTS = java.util.Set.of(77, 94, 108, 97, 191, 80, 95);

    /** Nhân hệ số theo lối chơi (phần trăm): dòng hợp lối chơi x1.6, dòng ngược lối chơi x0.7. */
    private static int styleMul(Bot b, int id) {
        int st = style(b);
        if (st == 0) {
            return DAME_OPTS.contains(id) ? 160 : TANK_OPTS.contains(id) ? 70 : 100;
        }
        if (st == 1) {
            return TANK_OPTS.contains(id) ? 160 : DAME_OPTS.contains(id) ? 70 : 100;
        }
        return 100;
    }

    /**
     * Độ ngon của đồ ngoại hình với chính bot này (theo lối chơi: sức đánh đeo Pháo hoa NRO, trâu đeo Cờ bông tuyết): chỉ số theo giá trị thật (sức đánh / chí mạng / giảm sát thương nặng hơn may mắn / vàng rơi),
     * hợp với đồ đang mặc (dòng "Đi cùng X" khi bot hoặc đệ đang mặc X, cải trang hợp thể đặc biệt khi đệ mặc món còn lại), đồ vĩnh viễn hơn đồ có hạn.
     */
    static long lookScore(Bot b, Item it) {
        if (it == null || !it.isNotNullItem()) {
            return -1;
        }
        long s = 0;
        boolean expiring = false;
        for (Item.ItemOption io : it.itemOptions) {
            if (io == null || io.optionTemplate == null || io.optionTemplate.name == null) {
                continue;
            }
            int id = io.optionTemplate.id;
            String n = io.optionTemplate.name;
            if (id == 93) {
                expiring = true;
                continue;
            }
            if (id == 30 || id == 73 || id == 102 || id == 107 || id == 231) {
                continue;
            }
            if (n.startsWith("Mặc giảm")) {
                s -= io.param * 60L;
            } else if (n.startsWith("Đi cùng ")) {
                s += io.param * (wearsMatch(b, n) ? 40L : 2L);
            } else if (id == 47) {
                s += io.param / 200;
            } else if (id == 0 || id == 6) { // Tấn công+# / HP+#: lối sức đánh thích tấn công, lối trâu thích HP
                int st = style(b);
                boolean fit = (id == 0 && st == 0) || (id == 6 && st == 1);
                s += Math.min(io.param, 1_000_000) / (fit ? 80 : st == 2 ? 400 : 2000);
            } else if (n.contains("%")) {
                s += (long) io.param * LOOK_WEIGHT.getOrDefault(id, 8) * styleMul(b, id) / 100;
            } else {
                s += Math.min(io.param, 1_000_000) / 1000;
            }
        }
        int tid = it.template.id;
        int pair = tid == 1553 ? 1693 : tid == 1693 ? 1553 : tid == 1590 ? 1746 : tid == 1746 ? 1590 : -1;
        if (pair > 0 && b.pet != null && b.pet.inventory != null && b.pet.inventory.itemsBody.size() > 5) {
            Item pc = b.pet.inventory.itemsBody.get(5);
            if (pc != null && pc.isNotNullItem() && pc.template.id == pair) {
                s += 800; // hợp thể Gogeta SSJ4 / SSJ Blue: +5% HP, +5% sức đánh
            }
        }
        return expiring ? s * 9 / 10 : s;
    }

    /** Dòng "Đi cùng X tăng ..." mà bot hoặc đệ đang mặc món có tên chứa X. */
    private static boolean wearsMatch(Bot b, String optName) {
        String t = optName.substring("Đi cùng ".length());
        int cut = t.length();
        for (String k : new String[]{" tăng", " +"}) {
            int i = t.indexOf(k);
            if (i > 0) {
                cut = Math.min(cut, i);
            }
        }
        String want = t.substring(0, cut).trim().toLowerCase();
        if (want.isEmpty()) {
            return false;
        }
        java.util.List<java.util.List<Item>> lists = new java.util.ArrayList<>();
        lists.add(b.inventory.itemsBody);
        if (b.pet != null && b.pet.inventory != null) {
            lists.add(b.pet.inventory.itemsBody);
        }
        for (java.util.List<Item> l : lists) {
            for (Item x : l) {
                if (x != null && x.isNotNullItem() && x.template.name != null && x.template.name.toLowerCase().contains(want)) {
                    return true;
                }
            }
        }
        return false;
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
            long cur = lookScore(b, bodyAt(b, slot));
            int best = -1;
            for (int i = 0; i < b.inventory.itemsBag.size(); i++) {
                Item it = b.inventory.itemsBag.get(i);
                long v;
                if (slotOf(it) == slot && wearable(b, it.template) && (v = lookScore(b, it)) > cur) {
                    best = i;
                    cur = v;
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
        for (java.util.List<Item> list : java.util.List.of(b.inventory.itemsBag, b.inventory.itemsBox)) { // đồ hành tinh khác nhặt / cất từ trước
            for (int i = 0; i < list.size(); i++) {
                Item x = list.get(i);
                if (x != null && x.isNotNullItem() && otherPlanetGear(b, x.template)) {
                    list.set(i, ItemService.gI().createItemNull());
                }
            }
        }
        for (int i = b.inventory.itemsBag.size() - 1; i >= 0; i--) {
            Item it = b.inventory.itemsBag.get(i);
            int slot = slotOf(it);
            boolean otherGender = slot != -1 && it.template.gender < 3 && it.template.gender != b.gender;
            boolean look = slot >= 5 || slot == -2;
            if (slot == -1 || (!otherGender && (look ? lookScore(b, it) > lookScore(b, bodyAt(b, slot)) : score(it) > score(bodyAt(b, slot))))) {
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

    /** Áo / quần / găng / giày / rada của hành tinh khác mà cả bot lẫn đệ của bot đều không mặc được: bot không nhặt, không giữ. */
    static boolean otherPlanetGear(Bot b, ItemTemplate t) {
        if (t == null || t.type > 4 || t.gender >= 3 || t.gender == b.gender) {
            return false;
        }
        return b.pet == null || b.pet.gender != t.gender;
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
        long pickScore = Long.MIN_VALUE;
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
                    if (!afford) {
                        continue;
                    }
                    Item probe = ItemService.gI().createNewItem((short) is.temp.id); // món ngon nhất trong tầm tiền (trước đây: món đắt nhất)
                    probe.itemOptions.addAll(is.options != null && !is.options.isEmpty() ? Item.ItemOption.copyAll(is.options)
                            : ItemService.gI().getMaxOptions((short) is.temp.id));
                    long v = lookScore(b, probe);
                    if (pick == null || v > pickScore || (v == pickScore && is.cost > pick.cost)) {
                        pick = is;
                        pickScore = v;
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
            if ((sold && !legit) || boxOnly().contains(id)) {
                b.inventory.itemsBody.set(slot, ItemService.gI().createItemNull());
                changed = true;
            }
        }
        // đệ tử của bot đeo (Pet Po trên đệ vẫn biến người xung quanh thành bánh bao)
        if (b.pet != null && b.pet.inventory != null && b.pet.inventory.itemsBody != null) {
            boolean pc = false;
            for (int i = 0; i < b.pet.inventory.itemsBody.size(); i++) {
                Item x = b.pet.inventory.itemsBody.get(i);
                if (x != null && x.isNotNullItem() && slotOf(x) >= 5 && boxOnly().contains((int) x.template.id)) {
                    b.pet.inventory.itemsBody.set(i, ItemService.gI().createItemNull());
                    pc = true;
                }
            }
            if (pc) {
                if (b.pet.nPoint != null) {
                    b.pet.nPoint.calPoint();
                }
                Service.gI().Send_Caitrang(b.pet);
            }
        }
        // cùng loại đồ nằm trong hành trang / rương (bản cũ mua về chưa mặc) cũng bỏ, kẻo lại mặc vào
        for (java.util.List<Item> list : java.util.List.of(b.inventory.itemsBag, b.inventory.itemsBox)) {
            for (int i = 0; i < list.size(); i++) {
                Item x = list.get(i);
                if (x != null && x.isNotNullItem() && slotOf(x) >= 5 && boxOnly().contains((int) x.template.id)) {
                    list.set(i, ItemService.gI().createItemNull());
                }
            }
        }
        if (changed) {
            Service.gI().point(b);
            Service.gI().Send_Caitrang(b);
        }
    }

    private static volatile java.util.Set<Integer> boxOnly;

    /**
     * Món chỉ có trong hộp quà mà bot không có cách nào lấy được hộp (vd Pet Po chỉ ở Quà Trăng Khuyết đổi ở NPC): không bán ở Santa / Urôn,
     * không có trong quà boss / rơi sự kiện / đổi sự kiện / thưởng đua top, và hộp chứa nó cũng không lấy được theo các đường đó.
     * Bản cũ bot "mua" được ở mọi shop nên có những món này. Món không nằm trong bảng nào (đồ rơi viết trong code) thì giữ.
     */
    private static java.util.Set<Integer> boxOnly() {
        java.util.Set<Integer> r = boxOnly;
        if (r != null) {
            return r;
        }
        java.util.Set<Integer> ok = new java.util.HashSet<>(), inBox = new java.util.HashSet<>();
        java.util.Map<Integer, java.util.List<Integer>> boxOf = new java.util.HashMap<>(); // món -> các hộp chứa nó
        for (var shop : Manager.SHOPS) {
            if (REAL_SHOPS.contains(shop.tagName)) {
                for (var tab : shop.tabShops) {
                    for (ItemShop is : tab.itemShops) {
                        if (is.temp != null) {
                            ok.add((int) is.temp.id);
                        }
                    }
                }
            }
        }
        for (int id : nro.models.services.RewardService.SPECIAL_WHEEL_ITEMS) { // rơi từ vòng quay (viết trong code, không có trong bảng)
            ok.add(id);
        }
        try (java.sql.Connection con = nro.models.data.LocalManager.getConnection(); java.sql.Statement st = con.createStatement()) {
            for (String q : new String[]{"select item_id from boss_reward", "select item_id from season_event_drop",
                "select reward_item from season_event_exchange", "select item_id from season_event_top_reward"}) {
                try (java.sql.ResultSet rs = st.executeQuery(q)) {
                    while (rs.next()) {
                        ok.add(rs.getInt(1));
                    }
                } catch (Exception ignored) {
                }
            }
            try (java.sql.ResultSet rs = st.executeQuery("select box_id, item_id from item_box_reward")) {
                while (rs.next()) {
                    inBox.add(rs.getInt(2));
                    boxOf.computeIfAbsent(rs.getInt(2), k -> new java.util.ArrayList<>()).add(rs.getInt(1));
                }
            }
        } catch (Exception e) {
            return boxOnly = java.util.Set.of(); // không đọc được thì không gỡ gì
        }
        boolean grew = true;
        while (grew) { // hộp lấy được thì đồ trong hộp cũng lấy được (hộp lồng hộp)
            grew = false;
            for (var e : boxOf.entrySet()) {
                if (!ok.contains(e.getKey()) && e.getValue().stream().anyMatch(ok::contains)) {
                    ok.add(e.getKey());
                    grew = true;
                }
            }
        }
        inBox.removeAll(ok);
        return boxOnly = inBox;
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
