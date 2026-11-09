package nro.models.Bot;

import nro.models.item.Item;
import nro.models.map.Zone;
import nro.models.map.service.ChangeMapService;
import nro.models.map.service.MapService;
import nro.models.mob.Mob;
import nro.models.npc.MabuEgg;
import nro.models.player.Pet;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.utils.Util;

/**
 * Tài khoản bot chơi phần sau như người chơi: cho đệ đi đánh để lên, đánh Hirudegarn lúc 22h (trứng Mabư, bình hút năng lượng),
 * tích Kilis ở Sa mạc hoang vu rồi đổi đệ tử 2, mua bông tai cấp 1, nhặt mảnh vỡ ở Thánh địa và ghép bông tai cấp 2 / 3.
 * Ngọc chỉ kiếm qua nhiệm vụ hằng ngày nên chỗ cần ngọc bot trả bằng tiềm năng (GEM_TN tiềm năng / 1 ngọc).
 */
public class BotEndgame {

    static final int BT1 = 454, BT2 = 921, BT3 = 1884, MANH_BT = 933, MANH_BT3 = 1868, EGG = 568, JAR = 1852;
    static final int MAP_HIRU = 126, MAP_KILIS = 165;
    static final int[] MAP_THANH_DIA = {156, 157, 158, 159};
    static final long GEM_TN = 1_000_000L;

    /** Mỗi lượt BotQuest (15 giây): đệ, trứng, mua / ghép bông tai, đổi đệ 2. */
    static void step(Bot b) {
        pet(b);
        useEgg(b);
        buyBt1(b);
        combine(b, BT1, MANH_BT, 9_999, 99, BT2, 2);
        combine(b, BT2, MANH_BT3, 20_000, 200, BT3, 3);
        changePet(b);
    }

    /** Hoạt động phần sau (BotWorld.switchActivity gọi trước khi chọn ngẫu nhiên). true nếu bot đã nhận việc. */
    static boolean pickActivity(Bot b) {
        if (!strong(b)) {
            return false;
        }
        if (java.time.LocalTime.now().getHour() == 22) { // Hirudegarn 22h - 23h (Tapion)
            Zone z = hirudegarn();
            if (z != null) {
                go(b, z, "Săn Hirudegarn", nro.models.consts.ConstMob.HIRUDEGARN);
                return true;
            }
        }
        Item jar = InventoryService.gI().findItemBag(b, JAR);
        if (jar != null && !jar.hasOption(250, 3000) && Util.isTrue(60, 100)) {
            go(b, zoneOf(MAP_KILIS), "Hút Kilis", -1);
            return true;
        }
        boolean needManh = has(b, BT1) && param31(b, MANH_BT) < 9_999;
        if (needManh && Util.isTrue(40, 100)) {
            go(b, zoneOf(MAP_THANH_DIA[Util.nextInt(0, MAP_THANH_DIA.length - 1)]), "Up bông tai", -1);
            return true;
        }
        return false;
    }

    /** Chỉ số đủ đánh map Cold (như Bot.MapToPow) mới đi các việc phần sau. */
    private static boolean strong(Bot b) {
        return b.nPoint.hpMax >= 3_000_000 && b.nPoint.dame >= 150_000;
    }

    private static void go(Bot b, Zone z, String activity, int mob) {
        if (z == null) {
            return;
        }
        b.boss.prefer = null;
        b.setMode(0);
        b.activity = activity;
        b.questMap = z.map.mapId;
        b.questMob = mob;
        b.stayMap = true; // người chơi vào các map này qua NPC: đứng lại đánh, không tự đổi map
        ChangeMapService.gI().goToMap(b, z);
        b.zone.load_Me_To_Another(b);
    }

    private static Zone zoneOf(int mapId) {
        nro.models.map.Map m = MapService.gI().getMapById(mapId);
        return m == null || m.zones.isEmpty() ? null : m.zones.get(Util.nextInt(0, m.zones.size() - 1));
    }

    private static Zone hirudegarn() {
        nro.models.map.Map m = MapService.gI().getMapById(MAP_HIRU);
        if (m != null) {
            for (Zone z : m.zones) {
                for (Mob mob : z.mobs) {
                    if (mob.tempId == nro.models.consts.ConstMob.HIRUDEGARN && !mob.isDie()) {
                        return z;
                    }
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ đệ tử, trứng, đệ 2
    /** Đệ đi đánh cùng bot để lên sức mạnh (trước đây luôn để ở nhà). */
    private static void pet(Bot b) {
        if (b.pet != null && b.pet.status == Pet.GOHOME && !b.pet.isDie() && b.fusion.typeFusion == 0 && b.nPoint.power >= 1_500_000) {
            b.pet.changeStatus(Pet.ATTACK);
            b.pet.joinMapMaster();
        }
    }

    /** Nhặt được Quả trứng Mabư thì dùng (nở sau 10 ngày như người chơi; trứng nở xong thì Hirudegarn rơi bình hút). */
    private static void useEgg(Bot b) {
        Item egg = InventoryService.gI().findItemBag(b, EGG);
        if (egg != null && b.mabuEgg == null) {
            MabuEgg.createMabuEgg(b);
            InventoryService.gI().subQuantityItemsBag(b, egg, 1);
        }
    }

    /** Bình hút đủ 3000 Kilis, đệ đạt 40 tỷ: đổi đệ tử 2 (UseItem, như người chơi dùng bình). */
    private static void changePet(Bot b) {
        Item jar = InventoryService.gI().findItemBag(b, JAR);
        if (jar != null && jar.hasOption(250, 3000) && b.pet != null && b.pet.nPoint.power >= 40_000_000_000L
                && b.pet.typePet != 2 && b.pet.typePet != 3 && b.pet.typePet != 4) {
            nro.models.services_func.UseItem.gI().botChangePet(b, jar);
            Service.gI().chat(b, "đổi được đệ tử 2 rồi ae");
        }
    }

    // ------------------------------------------------------------------ bông tai
    private static boolean has(Bot b, int id) {
        if (InventoryService.gI().findItemBag(b, id) != null) {
            return true;
        }
        for (Item it : b.inventory.itemsBody) {
            if (it != null && it.isNotNullItem() && it.template.id == id) {
                return true;
            }
        }
        return false;
    }

    /** Số mảnh (option 31) của loại mảnh trong hành trang. */
    private static int param31(Bot b, int id) {
        Item it = InventoryService.gI().findItemBag(b, id);
        Item.ItemOption o = it == null ? null : it.getOptionById(31);
        return o == null ? 0 : o.param;
    }

    /** Trả n ngọc, thiếu ngọc thì trả bằng tiềm năng. */
    private static boolean payGem(Bot b, int n) {
        if (b.inventory.gem >= n) {
            b.inventory.gem -= n;
            return true;
        }
        if (b.nPoint.tiemNang >= n * GEM_TN) {
            b.nPoint.tiemNang -= n * GEM_TN;
            return true;
        }
        return false;
    }

    /** Mua Bông tai Porata cấp 1 (cửa hàng 1000 ngọc) khi chưa có bông tai nào và đủ mạnh để lên Thánh địa. */
    private static void buyBt1(Bot b) {
        if (!strong(b) || has(b, BT1) || has(b, BT2) || has(b, BT3) || InventoryService.gI().getCountEmptyBag(b) <= 0 || !payGem(b, 1000)) {
            return;
        }
        Item bt = ItemService.gI().createNewItem((short) BT1);
        bt.itemOptions.addAll(ItemService.gI().getListOptionItemShop((short) BT1));
        InventoryService.gI().addItemBag(b, bt);
        Service.gI().chat(b, "mua bông tai rồi, đi up mảnh thôi");
    }

    /** Ghép bông tai như Bà Hạt Mít (NangCapBongTai / NangCapBongTai3): 200tr vàng + 1000 ngọc, 50%, hỏng mất ít mảnh. */
    private static void combine(Bot b, int from, int manh, int need, int lose, int to, int cap) {
        Item bt = InventoryService.gI().findItemBag(b, from);
        if (bt == null || param31(b, manh) < need || b.inventory.gold < 200_000_000L || has(b, to)) {
            return;
        }
        if (!payGem(b, 1000)) {
            return;
        }
        b.inventory.gold -= 200_000_000L;
        if (Util.isTrue(50, 100)) {
            bt.template = ItemService.gI().getTemplate(to);
            bt.itemOptions.clear();
            bt.itemOptions.add(new Item.ItemOption(72, cap));
            InventoryService.gI().subParamItemsBag(b, manh, 31, need);
            Service.gI().chat(b, "ghép được bông tai cấp " + cap + " rồi!");
        } else {
            InventoryService.gI().subParamItemsBag(b, manh, 31, lose);
        }
    }
}
