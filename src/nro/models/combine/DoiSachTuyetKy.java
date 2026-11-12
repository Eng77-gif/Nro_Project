package nro.models.combine;

import nro.models.consts.ConstFont;
import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.services.InventoryService;
import nro.models.utils.Util;

/**
 *
 * @author Việt
 */
public class DoiSachTuyetKy {

    private static final int REQUIRED_CUON_SACH_CU = 10;
    private static final int REQUIRED_KIM_BAM_GIAY = 1;
    private static final int REQUIRED_CON_DAU = 1;
    private static final int SUCCESS_RATE_PERCENT = 20;
    /** Tỉ lệ sách ra thêm 1 dòng khi đổi thường; dùng con dấu x5. */
    private static final int EXTRA_LINE_PERCENT = 4;
    public static final int CON_DAU = 1851, CON_DAU_VIP = 2131;
    private static final short[] SACH_IDS = new short[]{1044, 1211, 1212};
    /** Cách đổi: 0 thường, 1 con dấu, 2 con dấu VIP. */
    private static final int THUONG = 0, DAU = 1, DAU_VIP = 2;

    private static int count(Player player, int id) {
        Item it = InventoryService.gI().findItemBag(player, id);
        return it != null ? it.quantity : 0;
    }

    /** Các nút của menu theo nguyên liệu đang có (cùng thứ tự với onSelect). */
    private static java.util.List<Integer> modes(Player player) {
        java.util.List<Integer> m = new java.util.ArrayList<>();
        if (count(player, 1283) >= REQUIRED_CUON_SACH_CU && count(player, 1285) >= REQUIRED_KIM_BAM_GIAY) {
            m.add(THUONG);
            if (count(player, CON_DAU) >= REQUIRED_CON_DAU) {
                m.add(DAU);
            }
            if (count(player, CON_DAU_VIP) >= REQUIRED_CON_DAU) {
                m.add(DAU_VIP);
            }
        }
        return m;
    }

    public static void showCombine(Player player) {
        int sach = count(player, 1283), kim = count(player, 1285);
        StringBuilder text = new StringBuilder();
        text.append(ConstFont.BOLD_GREEN).append("Đổi sách Tuyệt Kỹ 1\n")
                .append(formatRequirement("Cuốn sách cũ", sach, REQUIRED_CUON_SACH_CU))
                .append(formatRequirement("Kìm bấm giấy", kim, REQUIRED_KIM_BAM_GIAY));
        boolean enough = sach >= REQUIRED_CUON_SACH_CU && kim >= REQUIRED_KIM_BAM_GIAY;
        text.append(enough ? ConstFont.BOLD_BLUE : ConstFont.BOLD_RED).append("Tỉ lệ thành công: ").append(SUCCESS_RATE_PERCENT).append("%\n");
        text.append(formatNote("Nếu dùng thêm con dấu tỉ lệ thành công là 100% và tăng x5 tỉ lệ ra thêm dòng cho sách"));
        text.append(formatNote("Nếu dùng con dấu VIP tỉ lệ thành công là 100% và sách ra tối thiểu 2 dòng (10% ra 3 dòng, 5% ra 4 dòng)"));
        java.util.List<Integer> m = modes(player);
        if (m.isEmpty()) {
            CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.IGNORE_MENU, text.toString(), "Từ chối");
            return;
        }
        java.util.List<String> opts = new java.util.ArrayList<>();
        for (int x : m) {
            opts.add(x == THUONG ? "Đồng ý" : x == DAU ? "Dùng\ncon dấu" : "Dùng con\ndấu VIP");
        }
        opts.add("Từ chối");
        CombineService.gI().baHatMit.createOtherMenu(player, ConstNpc.DOI_SACH_TUYET_KY, text.toString(), opts.toArray(new String[0]));
    }

    /** Bấm nút trong menu đổi sách. */
    public static void onSelect(Player player, int select) {
        java.util.List<Integer> m = modes(player);
        if (select >= 0 && select < m.size()) {
            doiSachTuyetKy(player, m.get(select));
        }
    }

    private static String formatNote(String note) {
        return ConstFont.BOLD_RED + note + "\n";
    }

    private static String formatRequirement(String itemName, int current, int required) {
        return (current >= required ? ConstFont.BOLD_BLUE : ConstFont.BOLD_RED)
                + itemName + " " + current + "/" + required + "\n";
    }

    public static void doiSachTuyetKy(Player player, int mode) {
        if (InventoryService.gI().getCountEmptyBag(player) == 0) {
            Service.gI().sendThongBao(player, "Cần 1 ô trống trong hành trang.");
            return;
        }
        Item cuonSachCu = InventoryService.gI().findItemBag(player, 1283);
        Item kimBamGiay = InventoryService.gI().findItemBag(player, 1285);
        Item conDau = mode == THUONG ? null : InventoryService.gI().findItemBag(player, mode == DAU ? CON_DAU : CON_DAU_VIP);
        if (cuonSachCu == null || cuonSachCu.quantity < REQUIRED_CUON_SACH_CU
                || kimBamGiay == null || kimBamGiay.quantity < REQUIRED_KIM_BAM_GIAY
                || (mode != THUONG && (conDau == null || conDau.quantity < REQUIRED_CON_DAU))) {
            Service.gI().sendThongBao(player, "Bạn không đủ nguyên liệu để đổi sách.");
            return;
        }
        CombineService.gI().sendAddItemCombine(player, ConstNpc.BA_HAT_MIT, cuonSachCu, kimBamGiay, conDau);
        if (mode != THUONG || Util.isTrue(SUCCESS_RATE_PERCENT, 100)) {
            processSuccess(player, cuonSachCu, kimBamGiay, conDau, mode);
        } else {
            processFailure(player, cuonSachCu, kimBamGiay);
        }
    }

    private static void processSuccess(Player player, Item cuonSachCu, Item kimBamGiay, Item conDau, int mode) {
        InventoryService.gI().subQuantityItemsBag(player, cuonSachCu, REQUIRED_CUON_SACH_CU);
        InventoryService.gI().subQuantityItemsBag(player, kimBamGiay, REQUIRED_KIM_BAM_GIAY);
        if (conDau != null) {
            InventoryService.gI().subQuantityItemsBag(player, conDau, REQUIRED_CON_DAU);
        }
        Item sachTuyetKy = createSach(lines(mode));
        InventoryService.gI().addItemBag(player, sachTuyetKy);
        InventoryService.gI().sendItemBags(player);
        CombineService.gI().sendEffSuccessVip(player, sachTuyetKy.template.iconID);
        Util.setTimeout(() -> {
            Service.gI().sendServerMessage(player, "Bạn nhận được " + sachTuyetKy.template.name);
            CombineService.gI().baHatMit.npcChat(player, "Chúc mừng con nhé");
        }, 2000);
    }

    /** Số dòng chưa giám định của sách: thường 1 (4% ra 2), con dấu x5 tỉ lệ thêm dòng (20% ra 2 - 3), con dấu VIP tối thiểu 2 (10% ra 3, 5% ra 4). */
    static int lines(int mode) {
        if (mode == DAU_VIP) {
            int r = Util.nextInt(0, 99);
            return r < 5 ? 4 : r < 15 ? 3 : 2;
        }
        int extra = mode == DAU ? EXTRA_LINE_PERCENT * 5 : EXTRA_LINE_PERCENT;
        return Util.isTrue(extra, 100) ? (mode == DAU ? Util.nextInt(2, 3) : 2) : 1;
    }

    private static void processFailure(Player player, Item cuonSachCu, Item kimBamGiay) {
        InventoryService.gI().subQuantityItemsBag(player, cuonSachCu, REQUIRED_CUON_SACH_CU / 2);
        InventoryService.gI().subQuantityItemsBag(player, kimBamGiay, REQUIRED_KIM_BAM_GIAY);

        CombineService.gI().sendEffFailVip(player);

        String[] failMessages = {
            "Chúc con may mắn lần sau, đừng buồn con nhé.",
            "Xịt rồi! Lại tốn giấy, tiếc ghê!",
            "Con có chắc là đã cầu nguyện trước khi đập không?",
            "Hên xui mà, biết đâu lần sau ăn luôn!",
            "Không sao, nhân phẩm ngủ quên chút thôi.",
            "Bình tĩnh, rồi cũng lên!",
            "Lại xịt, hay là đi xin vía trước đã?",
            "Làm lại đi, ba tin con sẽ thành công!"
        };

        Util.setTimeout(() -> {
            CombineService.gI().baHatMit.npcChat(player, failMessages[Util.nextInt(failMessages.length)]);
        }, 2000);
    }

    private static Item createSach(int lineCount) {
        short itemId = SACH_IDS[Util.nextInt(SACH_IDS.length)];
        Item sach = ItemService.gI().createNewItem(itemId);
        for (int i = 0; i < lineCount; i++) {
            sach.itemOptions.add(new Item.ItemOption(217, 0));
        }
        sach.itemOptions.add(new Item.ItemOption(21, 40));
        sach.itemOptions.add(new Item.ItemOption(30, 0));
        sach.itemOptions.add(new Item.ItemOption(87, 0));
        sach.itemOptions.add(new Item.ItemOption(219, 5));
        sach.itemOptions.add(new Item.ItemOption(212, 1000));
        return sach;
    }

}
