package nro.models.services;

import java.util.ArrayList;
import java.util.List;
import nro.models.boss.Boss;
import nro.models.item.Item;
import nro.models.item.Item.ItemOption;
import nro.models.map.ItemMap;
import nro.models.map.service.NpcService;
import nro.models.player.Player;
import nro.models.utils.Util;

/**
 * Sao pha lê 10 (Bà Hạt Mít): lỗ 10 chỉ khảm Ngọc rồng xanh 1 - 7 sao (chỉ số ngẫu nhiên theo khoảng).
 * Đá cấp 9 (Linh Hồn, Hiện Thực, Sức Mạnh, Không Gian, Thời Gian) dùng nâng cấp đồ +8 lên +9 (CombineSystem.DA_NANG_CAP_9).
 * Hộp đá nâng cấp +9 và Hộp ngọc rồng xanh cho chọn 1 viên. Nhiệm vụ mốc theo số món đã khảm đủ 10 sao.
 */
public class SaoPhaLe10Service {

    public static final int HOP_DA_9 = 2034;
    public static final int HOP_NRX = 2071;
    public static final int DA_9_FIRST = 2000, DA_9_LAST = 2004;
    public static final int NRX_FIRST = 2064, NRX_LAST = 2070;
    /** Menu nhận thưởng nhiệm vụ (Bà Hạt Mít) */
    public static final int MENU_NHIEM_VU = 2064_10;

    /** Ngọc rồng xanh 1 - 7 sao -> {option, min, max}; 7 sao thêm cả dòng 96 (biến tấn công thành KI) */
    private static final int[][] NRX = {{251, 1, 3}, {50, 3, 10}, {77, 5, 20}, {103, 5, 20}, {14, 1, 5}, {94, 3, 10}, {95, 5, 10}};

    /** Mốc nhiệm vụ: số món đủ 10 sao -> phần thưởng */
    public static final int[] MOC = {1, 2, 3, 5, 7};
    private static final String[] MOC_TEXT = {"1 hộp ngọc rồng xanh (tự chọn)", "2 hộp ngọc rồng xanh (tự chọn)", "1 Capsule Vỡ", "2 Capsule Vỡ",
        "1 Hộp Set kích hoạt tùy chọn theo nhân vật + Hào quang chân Sao Pha Lê 10 vĩnh viễn"};
    private static final int CAPSULE_KICH_HOAT = 1655;
    private static final int FOOT_REWARD = nro.models.database.Dot56Migration.ITEM_HAO_QUANG_SPL10;

    /** Dòng đánh dấu đồ đập lên 10 sao trong thời gian sự kiện (có hào quang chân, hiệu ứng 515). */
    public static final int OPT_HAO_QUANG = nro.models.database.Dot56Migration.OPT_HAO_QUANG_SPL10;
    public static final int EFF_HAO_QUANG = 515;
    /** Chỉ số hào quang (không cộng dồn dù mặc nhiều món): SĐ, HP, KI, sát thương cuối */
    public static final int BONUS_SD = 6, BONUS_HP = 6, BONUS_KI = 6, BONUS_ST_CUOI = 2;

    /** Theo sự kiện "Nâng cấp Sao Pha Lê 10" (season_event 18): tự chạy 31/10 - 20/11, admin bật / tắt trên web panel. */
    public static boolean isEventTime() {
        return SeasonEventService.gI().isActive(nro.models.database.Dot89Migration.EV_SPL10);
    }

    public static boolean hasMark(Item it) {
        if (it == null || !it.isNotNullItem()) {
            return false;
        }
        for (ItemOption io : it.itemOptions) {
            if (io.optionTemplate != null && io.optionTemplate.id == OPT_HAO_QUANG) {
                return true;
            }
        }
        return false;
    }

    /** Đập lên 10 sao trong thời gian sự kiện thì gắn hào quang chân Sao Pha Lê 10. */
    public static void markIfEvent(Item it, int star) {
        if (star >= 10 && isEventTime() && !hasMark(it)) {
            it.itemOptions.add(new ItemOption(OPT_HAO_QUANG, 0));
        }
    }

    /** Đang mặc đồ 10 sao sự kiện hoặc mang Hào quang chân Sao Pha Lê 10. */
    public static boolean hasHaoQuang(Player pl) {
        if (pl == null || !pl.isPl() || pl.inventory == null) {
            return false;
        }
        if (hasMark(pl.itemFoot)) {
            return true;
        }
        for (Item it : pl.inventory.itemsBody) {
            if (hasMark(it)) {
                return true;
            }
        }
        return false;
    }

    public static int effectId(Player pl) {
        return hasHaoQuang(pl) ? EFF_HAO_QUANG : -1;
    }

    private static SaoPhaLe10Service instance;

    public static SaoPhaLe10Service gI() {
        if (instance == null) {
            instance = new SaoPhaLe10Service();
        }
        return instance;
    }

    public static boolean isDaCap9(Item item) {
        return item != null && item.template != null && item.template.id >= DA_9_FIRST && item.template.id <= DA_9_LAST;
    }

    public static boolean isNgocRongXanh(Item item) {
        return item != null && item.template != null && item.template.id >= NRX_FIRST && item.template.id <= NRX_LAST;
    }

    /** Đá này có khảm được vào lỗ thứ slot (1 - 10) không. */
    public static boolean canSocket(Item da, int slot) {
        return slot == 10 ? isNgocRongXanh(da) : !isNgocRongXanh(da);
    }

    /** Các dòng chỉ số sẽ thêm vào trang bị khi khảm (ngọc rồng xanh: ngẫu nhiên trong khoảng). */
    public static List<ItemOption> rollOptions(Item da) {
        List<ItemOption> list = new ArrayList<>();
        if (isNgocRongXanh(da)) {
            int[] d = NRX[da.template.id - NRX_FIRST];
            int param = Util.nextInt(d[1], d[2]);
            list.add(new ItemOption(d[0], param));
            if (da.template.id == NRX_LAST) {
                list.add(new ItemOption(96, param));
            }
        }
        return list;
    }

    /** Mô tả chỉ số cho bảng thông tin ép sao. */
    public static String describe(Item da) {
        int[] d = NRX[da.template.id - NRX_FIRST];
        String range = d[1] + "-" + d[2];
        String s = optName(d[0], range);
        if (da.template.id == NRX_LAST) {
            s += "\n" + optName(96, range);
        }
        return s + " (ngẫu nhiên)";
    }

    private static String optName(int optionId, String param) {
        return ItemService.gI().getItemOptionTemplate(optionId).name.replaceAll("#", param);
    }

    // ------------------------------------------------------------------ hộp chọn 1

    public boolean isBox(Item item) {
        return item != null && item.template != null && (item.template.id == HOP_DA_9 || item.template.id == HOP_NRX);
    }

    public void openMenu(Player pl, Item box) {
        int first = box.template.id == HOP_DA_9 ? DA_9_FIRST : NRX_FIRST;
        int last = box.template.id == HOP_DA_9 ? DA_9_LAST : NRX_LAST;
        String[] names = new String[last - first + 2];
        for (int id = first; id <= last; id++) {
            names[id - first] = ItemService.gI().getTemplate(id).name.replace("Ngọc rồng xanh ", "Ngọc rồng\nxanh ");
        }
        names[names.length - 1] = "Đóng";
        NpcService.gI().createMenuConMeo(pl, box.template.id, -1,
                box.template.id == HOP_DA_9 ? "Chọn 1 viên đá nâng cấp trang bị +8 lên +9\nÁo: Hiện Thực, Quần: Không Gian, Găng: Sức Mạnh, Giày: Thời Gian, Rada: Linh Hồn"
                        : "Chọn 1 viên ngọc rồng xanh (khảm vào lỗ sao pha lê thứ 10)", names);
    }

    public void openBox(Player pl, int boxId, int select) {
        int first = boxId == HOP_DA_9 ? DA_9_FIRST : NRX_FIRST;
        int last = boxId == HOP_DA_9 ? DA_9_LAST : NRX_LAST;
        if (select < 0 || first + select > last) {
            return;
        }
        Item box = InventoryService.gI().findItemBag(pl, boxId);
        if (box == null) {
            return;
        }
        if (InventoryService.gI().getCountEmptyBag(pl) <= 0) {
            Service.gI().sendThongBao(pl, "Hành trang không còn chỗ trống");
            return;
        }
        Item it = ItemService.gI().createNewItem((short) (first + select));
        InventoryService.gI().subQuantityItemsBag(pl, box, 1);
        InventoryService.gI().addItemBag(pl, it);
        InventoryService.gI().sendItemBags(pl);
        Service.gI().sendThongBao(pl, "Bạn nhận được " + it.template.name);
    }

    // ------------------------------------------------------------------ rơi từ boss

    /** Boss từ 50 triệu HP: 5% rơi Hộp đá +9; boss từ 500 triệu HP: 5% rơi 1 viên ngọc rồng xanh ngẫu nhiên. */
    public void onBossKilled(Boss boss, Player pl) {
        if (boss.zone == null) {
            return;
        }
        int hp = (int) Math.min(Integer.MAX_VALUE, boss.nPoint.hpMax);
        if (hp >= 50_000_000 && Util.isTrue(5, 100)) {
            drop(boss, pl, HOP_DA_9);
        }
        if (hp >= 500_000_000 && Util.isTrue(5, 100)) {
            drop(boss, pl, Util.nextInt(NRX_FIRST, NRX_LAST));
        }
    }

    private void drop(Boss boss, Player pl, int itemId) {
        int x = boss.location.x + Util.nextInt(-20, 20);
        ItemMap it = new ItemMap(boss.zone, itemId, 1, x, boss.zone.map.yPhysicInTop(x, boss.location.y - 24), pl.id);
        Service.gI().dropItemMap(boss.zone, it);
    }

    // ------------------------------------------------------------------ nhiệm vụ Bà Hạt Mít

    /** Số món (đang mặc + hành trang + rương) đã khảm đủ 10 sao trong thời gian sự kiện. */
    public int countFull(Player pl) {
        int n = 0;
        List<List<Item>> all = List.of(pl.inventory.itemsBody, pl.inventory.itemsBag, pl.inventory.itemsBox);
        for (List<Item> list : all) {
            for (Item it : list) {
                if (it != null && it.isNotNullItem() && hasMark(it)) {
                    for (ItemOption io : it.itemOptions) {
                        if (io.optionTemplate.id == 102 && io.param >= 10) {
                            n++;
                            break;
                        }
                    }
                }
            }
        }
        return n;
    }

    public void showQuest(Player pl, nro.models.npc.Npc npc) {
        int n = countFull(pl);
        StringBuilder sb = new StringBuilder("|1|Sự kiện Nâng cấp trang bị lên Sao Pha Lê 10\n|2|" + (isEventTime() ? "Sự kiện đang diễn ra" : "Sự kiện chưa mở / đã kết thúc")
                + "\nChỉ tính trang bị đập lên 10 sao trong thời gian sự kiện\n(có dòng Hào quang chân Sao Pha Lê 10).\nBạn đang có: " + n + " món\n");
        for (int i = 0; i < MOC.length; i++) {
            boolean got = (pl.spl10Reward & (1 << i)) != 0;
            sb.append(got ? "|7|" : n >= MOC[i] ? "|1|" : "|4|").append("Nâng cấp ").append(MOC[i]).append(" món: ").append(MOC_TEXT[i])
                    .append(got ? " (đã nhận)" : "").append("\n");
        }
        npc.createOtherMenu(pl, MENU_NHIEM_VU, sb.toString(), "Nhận\nthưởng", "Đóng");
    }

    public void claim(Player pl) {
        int n = countFull(pl);
        int got = 0;
        for (int i = 0; i < MOC.length; i++) {
            if ((pl.spl10Reward & (1 << i)) != 0 || n < MOC[i]) {
                continue;
            }
            List<Item> items = rewards(i);
            if (InventoryService.gI().getCountEmptyBag(pl) < items.size()) {
                Service.gI().sendThongBao(pl, "Hành trang cần " + items.size() + " ô trống để nhận mốc " + MOC[i] + " món");
                break;
            }
            for (Item it : items) {
                InventoryService.gI().addItemBag(pl, it);
            }
            pl.spl10Reward |= 1 << i;
            got++;
            Service.gI().sendThongBao(pl, "Nhận thưởng mốc " + MOC[i] + " món: " + MOC_TEXT[i]);
        }
        if (got == 0) {
            Service.gI().sendThongBao(pl, "Chưa có mốc nào để nhận (đang có " + n + " món đủ 10 sao)");
        }
        InventoryService.gI().sendItemBags(pl);
    }

    private List<Item> rewards(int moc) {
        List<Item> list = new ArrayList<>();
        switch (moc) {
            case 0 ->
                list.add(ItemService.gI().createNewItem((short) HOP_NRX, 1));
            case 1 ->
                list.add(ItemService.gI().createNewItem((short) HOP_NRX, 2));
            case 2 ->
                list.add(ItemService.gI().createNewItem((short) Event2026Service.CAPSULE_VO, 1));
            case 3 ->
                list.add(ItemService.gI().createNewItem((short) Event2026Service.CAPSULE_VO, 2));
            case 4 -> {
                list.add(ItemService.gI().createNewItem((short) CAPSULE_KICH_HOAT, 1));
                Item foot = ItemService.gI().createNewItem((short) FOOT_REWARD, 1);
                foot.itemOptions.add(new ItemOption(OPT_HAO_QUANG, 0));
                list.add(foot);
            }
        }
        return list;
    }
}
