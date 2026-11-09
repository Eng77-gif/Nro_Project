package nro.models.services;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import nro.models.item.Item;
import nro.models.map.service.ChangeMapService;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.utils.Util;

/**
 * Sự kiện Ngũ Hành Sơn (theo bản chính thức): Đường Tăng xuất hiện ở các làng, đưa người chơi tới bản đồ Ngũ Hành Sơn
 * (122 - 124). Đánh quái ở đó nhặt Quả Hồng Đào (Chi Chi cũng bán Quả Hồng Đào / Quả Hồng Đào Chín), tặng Ngộ Không
 * (đang bị phong ấn) để tích điểm công đức, rồi gặp Đường Tăng đổi quà.
 *
 * @author Việt
 */
public class NguHanhSonService {

    public static final int EVENT_ID = 12;
    public static final int MAP_NGU_HANH_SON = 122;
    /** 3 vùng Ngũ Hành Sơn {map, x}: 1 = 122 (Ngộ Không), 2 = 124 (giữa), 3 = 123 (trong cùng) */
    private static final int[][] NHS_GATES = {{122, 560}, {124, 600}, {123, 400}};
    public static final int HONG_DAO = 541, HONG_DAO_CHIN = 542;
    public static final int VONG_KIM_CO = 543;
    /** Cải trang chỉ dành cho đệ tử */
    public static final int CT_BAT_GIOI_DE = 548, CT_NGO_KHONG_DE = 547, CT_SA_TANG_DE = 2130;
    public static final Set<Integer> PET_ONLY = Set.of(CT_BAT_GIOI_DE, CT_NGO_KHONG_DE, CT_SA_TANG_DE, 2128, 2129);
    /** Cải trang Tôn Ngộ Không có sẵn trong game: 544 Trái Đất, 545 Namếc, 546 Xayda (đổi bằng 10 chữ mỗi loại) */
    public static final int CT_NGO_KHONG = 544;
    /** 4 chữ "Giải Khai Phong Ấn" có sẵn trong game (537 - 540), nhận khi tặng đào cho Ngộ Không */
    public static final int[] CHU = {537, 538, 539, 540};
    public static final String[] CHU_NAME = {"Giải", "Khai", "Phong", "Ấn"};
    public static final int CHU_NEED = 10;
    /** Các cải trang sự kiện: chỉ số ngẫu nhiên, hạn 5 - 15 ngày, không bao giờ vĩnh viễn, không mặc trong Ngọc Rồng Sao Đen */
    public static final Set<Integer> EVENT_CT = Set.of(544, 545, 546, CT_BAT_GIOI_DE, CT_NGO_KHONG_DE, CT_SA_TANG_DE, 2128, 2129);
    public static final int YEU_QUAI_NUI = 1988;
    public static final int REWARD_DAYS = 30;
    /** công đức đánh quái (mặc CT Tôn Ngộ Không + có Vòng Kim Cô) tối đa / ngày */
    public static final int MOB_POINT_DAILY = 500;
    /** hộ tống: 15 phút, tối đa 5 lần / ngày, thưởng 20 - 40 công đức */
    public static final long ESCORT_MS = 15 * 60_000L;
    public static final int ESCORT_DAILY = 5;

    public static final int MENU_DUONG_TANG = 49_001, MENU_REWARD = 49_002, MENU_NGO_KHONG = 48_001;

    /** {điểm, mô tả} theo đúng thứ tự nút */
    private static final Object[][] REWARDS = {
        {500, "500 cộng thêm 7 ngày sử dụng Cải Trang đang dùng"},
        {600, "600 cộng thêm 9 ngày sử dụng Cải Trang đệ tử đang dùng"},
        {700, "700 cải trang thành Bát Giới (dành riêng cho đệ tử)"},
        {1000, "1.000 cải trang thành Tôn Ngộ Không (dành riêng cho đệ tử)"},
        {1300, "1.300 cải trang thành Sa Tăng (dành riêng cho đệ tử)"},
        {800, "800 Yêu quái núi Lãng Lãng (cờ đeo lưng)"},};

    private static NguHanhSonService instance;

    public static NguHanhSonService gI() {
        if (instance == null) {
            instance = new NguHanhSonService();
        }
        return instance;
    }

    public boolean active() {
        return SeasonEventService.gI().isActive(EVENT_ID);
    }

    // ------------------------------------------------------------------ Đường Tăng
    private static boolean inNhs(Player pl) {
        return pl.zone != null && pl.zone.map.mapId >= 122 && pl.zone.map.mapId <= 124;
    }

    public void openDuongTang(Npc npc, Player pl) {
        boolean inMap = inNhs(pl);
        StringBuilder chu = new StringBuilder();
        for (int i = 0; i < CHU.length; i++) {
            chu.append(CHU_NAME[i]).append(": ").append(count(pl, CHU[i])).append("/").append(CHU_NEED).append(i < CHU.length - 1 ? ", " : "");
        }
        String say = "A mi phò phò, thí chủ hãy giúp giải cứu đồ đệ của bần tăng đang bị phong ấn tại Ngũ Hành Sơn\n"
                + "|2|Điểm công đức: " + pl.congDuc + "\nChữ: " + chu;
        if (!active() && !inMap) {
            npc.createOtherMenu(pl, MENU_DUONG_TANG, "A mi phò phò, Ngũ Hành Sơn chỉ mở trong sự kiện Ngũ Hành Sơn\n|2|Điểm công đức: " + pl.congDuc,
                    "Nhận\nthưởng", "Từ chối");
            return;
        }
        if (inMap) {
            npc.createOtherMenu(pl, MENU_DUONG_TANG, say, "Về\nlàng", "Nhiệm vụ\nhộ tống", "Đổi CT\nTôn Ngộ Không", "Nhận\nthưởng", "Từ chối");
        } else {
            npc.createOtherMenu(pl, MENU_DUONG_TANG, say, "Ngũ Hành\nSơn 1", "Ngũ Hành\nSơn 2", "Ngũ Hành\nSơn 3",
                    "Nhiệm vụ\nhộ tống", "Đổi CT\nTôn Ngộ Không", "Nhận\nthưởng", "Từ chối");
        }
    }

    public void selectDuongTang(Npc npc, Player pl, int menu, int select) {
        if (menu == MENU_DUONG_TANG) {
            boolean inMap = inNhs(pl);
            boolean open = active() || inMap;
            if (open && !inMap) { // ở làng: 3 nút chọn vùng Ngũ Hành Sơn đứng đầu
                if (select <= 2) {
                    int[] g = NHS_GATES[select];
                    ChangeMapService.gI().changeMapBySpaceShip(pl, g[0], -1, g[1]);
                    return;
                }
                select -= 2;
            }
            int s = open ? select : (select == 0 ? 3 : 4); // khi đóng chỉ có "Nhận thưởng", "Từ chối"
            switch (s) {
                case 0 -> ChangeMapService.gI().changeMapBySpaceShip(pl, 21 + pl.gender, -1, 250);
                case 1 -> startEscort(pl);
                case 2 -> exchangeCt(pl);
                case 3 -> openRewards(npc, pl);
            }
        } else if (menu == MENU_REWARD) {
            if (select < 0 || select >= REWARDS.length) {
                return;
            }
            reward(pl, select);
        }
    }

    private static int count(Player pl, int itemId) {
        Item it = InventoryService.gI().findItemBag(pl, itemId);
        return it == null ? 0 : it.quantity;
    }

    /** 10 chữ mỗi loại (Giải, Khai, Phong, Ấn) đổi Cải trang Tôn Ngộ Không. */
    private void exchangeCt(Player pl) {
        for (int i = 0; i < CHU.length; i++) {
            if (count(pl, CHU[i]) < CHU_NEED) {
                Service.gI().sendThongBao(pl, "Cần đủ " + CHU_NEED + " chữ mỗi loại Giải, Khai, Phong, Ấn (thiếu chữ " + CHU_NAME[i] + ")");
                return;
            }
        }
        if (InventoryService.gI().getCountEmptyBag(pl) <= 0) {
            Service.gI().sendThongBao(pl, "Hành trang không còn chỗ trống");
            return;
        }
        for (int c : CHU) {
            InventoryService.gI().subQuantityItemsBag(pl, InventoryService.gI().findItemBag(pl, c), CHU_NEED);
        }
        Item ct = createEventCt(CT_NGO_KHONG + Math.max(0, Math.min(2, pl.gender)));
        InventoryService.gI().addItemBag(pl, ct);
        InventoryService.gI().sendItemBags(pl);
        Service.gI().sendThongBao(pl, "Phong ấn đã được giải! Bạn nhận được " + "Cải trang Tôn Ngộ Không (hạn " + ct.itemOptions.stream().filter(o -> o.optionTemplate.id == 93).map(o -> o.param + " ngày)").findFirst().orElse(")"));
    }

    /** Cải trang sự kiện: SĐ 15 - 20%, HP / KI 50 - 100%, TN-SM 30 - 100%, vàng 70 - 130%, tốc độ chạy 50 - 120%, 20% có chống lạnh, hạn 5 - 15 ngày. */
    public static Item createEventCt(int itemId) {
        Item it = ItemService.gI().createNewItem((short) itemId);
        it.itemOptions.clear();
        it.itemOptions.add(new Item.ItemOption(50, Util.nextInt(15, 20)));
        it.itemOptions.add(new Item.ItemOption(77, Util.nextInt(50, 100)));
        it.itemOptions.add(new Item.ItemOption(103, Util.nextInt(50, 100)));
        it.itemOptions.add(new Item.ItemOption(101, Util.nextInt(30, 100)));
        it.itemOptions.add(new Item.ItemOption(100, Util.nextInt(70, 130)));
        it.itemOptions.add(new Item.ItemOption(148, Util.nextInt(50, 120)));
        if (Util.isTrue(20, 100)) {
            it.itemOptions.add(new Item.ItemOption(106, 0));
        }
        it.itemOptions.add(new Item.ItemOption(93, Util.nextInt(5, 15)));
        return it;
    }

    /** Tên hiển thị (CT gốc trong game chỉ tên "Cải trang") */
    public static String ctName(int id) {
        return switch (id) {
            case 544, 545, 546 -> "Cải trang Tôn Ngộ Không";
            case CT_NGO_KHONG_DE -> "Cải trang Tôn Ngộ Không (Đệ)";
            case CT_BAT_GIOI_DE -> "Cải trang Bát Giới (Đệ)";
            default -> ItemService.gI().getTemplate(id).name;
        };
    }

    public static boolean isNrsdMap(int mapId) {
        return mapId >= 85 && mapId <= 91;
    }

    /** Vào Ngọc Rồng Sao Đen: cải trang sự kiện Ngũ Hành Sơn tự cất vào hành trang. */
    public static void onJoinNrsd(Player pl) {
        if (pl == null || pl.isPet || pl.inventory.itemsBody.size() <= 5) {
            return;
        }
        Item ct = pl.inventory.itemsBody.get(5);
        if (ct != null && ct.isNotNullItem() && EVENT_CT.contains((int) ct.template.id)) {
            boolean bag = InventoryService.gI().getCountEmptyBag(pl) > 0;
            boolean box = !bag && pl.inventory.itemsBox.stream().anyMatch(i -> !i.isNotNullItem());
            if (bag || box) {
                if (bag) {
                    InventoryService.gI().addItemBag(pl, ct);
                } else {
                    InventoryService.gI().addItemBox(pl, ct);
                }
                pl.inventory.itemsBody.set(5, ItemService.gI().createItemNull());
                InventoryService.gI().sendItemBags(pl);
                InventoryService.gI().sendItemBody(pl);
                Service.gI().point(pl);
                Service.gI().Send_Caitrang(pl);
                Service.gI().sendThongBao(pl, ctName(ct.template.id) + " không dùng được trong Ngọc Rồng Sao Đen, đã cất vào " + (bag ? "hành trang" : "rương đồ"));
            } else {
                Service.gI().sendThongBaoOK(pl, "Hành trang và rương đồ đã đầy, hãy tháo " + ctName(ct.template.id) + " trước khi vào Ngọc Rồng Sao Đen");
                nro.models.map.service.ChangeMapService.gI().changeMapBySpaceShip(pl, 21 + pl.gender, -1, 250);
            }
        }
    }

    // ------------------------------------------------------------------ nhiệm vụ hộ tống
    private final Map<Long, int[]> escortDaily = new ConcurrentHashMap<>(); // id -> {ngày, số lần}

    private void startEscort(Player pl) {
        int day = java.time.LocalDate.now().getDayOfYear();
        int[] d = escortDaily.compute(pl.id, (k, v) -> v == null || v[0] != day ? new int[]{day, 0} : v);
        if (pl.escortUntil > System.currentTimeMillis()) {
            Service.gI().sendThongBao(pl, "Thí chủ đang hộ tống bần tăng rồi");
            return;
        }
        if (d[1] >= ESCORT_DAILY) {
            Service.gI().sendThongBao(pl, "Hôm nay thí chủ đã hộ tống đủ " + ESCORT_DAILY + " lần");
            return;
        }
        pl.escortUntil = System.currentTimeMillis() + ESCORT_MS;
        pl.escortStep = 1;
        pl.escortMask = 0;
        Service.gI().sendThongBaoOK(pl, "Hộ tống bần tăng qua Ngũ Hành Sơn: đi bộ qua đủ 2 vùng Ngũ Hành Sơn 2 và 3 "
                + "rồi quay lại gặp Ngộ Không (Ngũ Hành Sơn 1) trong 15 phút. Hoàn thành nhận 20 - 40 điểm công đức.");
        if (!inNhs(pl)) {
            ChangeMapService.gI().changeMapBySpaceShip(pl, MAP_NGU_HANH_SON, -1, 560);
        }
    }

    /** Gọi trong vòng cập nhật người chơi khi đang hộ tống. */
    public static void updateEscort(Player pl) {
        if (pl.escortUntil <= 0) {
            return;
        }
        if (System.currentTimeMillis() > pl.escortUntil || pl.isDie()) {
            pl.escortUntil = 0;
            pl.escortStep = 0;
            Service.gI().sendThongBao(pl, "Hộ tống thất bại");
            return;
        }
        int map = pl.zone == null ? -1 : pl.zone.map.mapId;
        int bit = map == 124 ? 1 : map == 123 ? 2 : 0;
        if (pl.escortStep < 3 && bit != 0 && (pl.escortMask & bit) == 0) {
            pl.escortMask |= bit;
            if (pl.escortMask == 3) {
                pl.escortStep = 3;
                Service.gI().sendThongBao(pl, "Hộ tống: đã qua đủ 2 vùng, quay lại gặp Ngộ Không (Ngũ Hành Sơn 1) để hoàn thành");
            } else {
                pl.escortStep = 2;
                Service.gI().sendThongBao(pl, "Hộ tống: đã tới Ngũ Hành Sơn " + (bit == 1 ? 2 : 3) + ", còn Ngũ Hành Sơn " + (bit == 1 ? 3 : 2));
            }
        }
    }

    private void finishEscort(Player pl) {
        if (pl.escortStep != 3 || pl.escortUntil < System.currentTimeMillis()) {
            Service.gI().sendThongBao(pl, "Chưa hoàn thành hộ tống");
            return;
        }
        int day = java.time.LocalDate.now().getDayOfYear();
        int[] d = escortDaily.compute(pl.id, (k, v) -> v == null || v[0] != day ? new int[]{day, 0} : v);
        d[1]++;
        pl.escortUntil = 0;
        pl.escortStep = 0;
        int add = Util.nextInt(20, 40);
        pl.congDuc += add;
        nro.models.database.PlayerExtraDAO.save(pl);
        Service.gI().sendThongBao(pl, "Hộ tống thành công, +" + add + " điểm công đức (còn " + (ESCORT_DAILY - d[1]) + " lần hôm nay)");
    }

    // ------------------------------------------------------------------ công đức đánh quái
    private final Map<Long, int[]> mobDaily = new ConcurrentHashMap<>();

    /** Hạ quái ở Ngũ Hành Sơn khi mặc CT Tôn Ngộ Không và có Vòng Kim Cô: +1 công đức (tối đa 500 / ngày). */
    public void onMobKilled(Player pl) {
        if (pl == null || pl.isPet || !inNhs(pl) || !active() || pl.inventory.itemsBody.size() <= 5) {
            return;
        }
        Item ct = pl.inventory.itemsBody.get(5);
        if (ct == null || !ct.isNotNullItem() || (ct.template.id < 544 || ct.template.id > 546) || count(pl, VONG_KIM_CO) <= 0) {
            return;
        }
        int day = java.time.LocalDate.now().getDayOfYear();
        int[] d = mobDaily.compute(pl.id, (k, v) -> v == null || v[0] != day ? new int[]{day, 0} : v);
        if (d[1] >= MOB_POINT_DAILY) {
            return;
        }
        d[1]++;
        pl.congDuc++;
        if (pl.congDuc % 10 == 0) {
            nro.models.database.PlayerExtraDAO.save(pl);
        }
    }

    private void openRewards(Npc npc, Player pl) {
        StringBuilder sb = new StringBuilder("A mi phò phò, thí chủ đang có " + pl.congDuc + " điểm công đức, xin hãy chọn 1 phần quà\n");
        String[] opts = new String[REWARDS.length + 1];
        for (int i = 0; i < REWARDS.length; i++) {
            sb.append(REWARDS[i][1]).append('\n');
            opts[i] = Util.numberToMoney((int) REWARDS[i][0]);
        }
        opts[REWARDS.length] = "Từ chối";
        npc.createOtherMenu(pl, MENU_REWARD, sb.toString(), opts);
    }

    private void reward(Player pl, int idx) {
        int cost = (int) REWARDS[idx][0];
        if (pl.congDuc < cost) {
            Service.gI().sendThongBao(pl, "Cần " + cost + " điểm công đức, thí chủ mới có " + pl.congDuc);
            return;
        }
        switch (idx) {
            case 0, 1 -> {
                Player who = idx == 0 ? pl : pl.pet;
                Item ct = who == null || who.inventory.itemsBody.size() <= 5 ? null : who.inventory.itemsBody.get(5);
                if (ct == null || !ct.isNotNullItem()) {
                    Service.gI().sendThongBao(pl, idx == 0 ? "Thí chủ chưa mặc cải trang" : "Đệ tử chưa mặc cải trang");
                    return;
                }
                Item.ItemOption hsd = null;
                for (Item.ItemOption io : ct.itemOptions) {
                    if (io.optionTemplate != null && io.optionTemplate.id == 93) {
                        hsd = io;
                    }
                }
                if (hsd == null) {
                    Service.gI().sendThongBao(pl, ctName(ct.template.id) + " không có hạn sử dụng (đã vĩnh viễn)");
                    return;
                }
                hsd.param += idx == 0 ? 7 : 9;
                pl.congDuc -= cost;
                InventoryService.gI().sendItemBody(pl);
                if (idx == 1) {
                    Service.gI().showInfoPet(pl);
                }
                Service.gI().sendThongBao(pl, ctName(ct.template.id) + " được cộng thêm " + (idx == 0 ? 7 : 9) + " ngày sử dụng");
            }
            default -> {
                int itemId = switch (idx) {
                    case 2 -> CT_BAT_GIOI_DE;
                    case 3 -> CT_NGO_KHONG_DE;
                    case 4 -> CT_SA_TANG_DE;
                    default -> YEU_QUAI_NUI;
                };
                if (InventoryService.gI().getCountEmptyBag(pl) <= 0) {
                    Service.gI().sendThongBao(pl, "Hành trang không còn chỗ trống");
                    return;
                }
                Item it;
                if (EVENT_CT.contains(itemId)) {
                    it = createEventCt(itemId);
                } else {
                    it = ItemService.gI().createNewItem((short) itemId);
                    it.itemOptions.addAll(ItemService.gI().getListOptionItemShop((short) itemId));
                    it.itemOptions.removeIf(o -> o.optionTemplate != null && o.optionTemplate.id == 93);
                    it.itemOptions.add(new Item.ItemOption(93, REWARD_DAYS));
                }
                InventoryService.gI().addItemBag(pl, it);
                InventoryService.gI().sendItemBags(pl);
                pl.congDuc -= cost;
                Service.gI().sendThongBao(pl, "Bạn nhận được " + ctName(it.template.id));
            }
        }
        nro.models.database.PlayerExtraDAO.save(pl);
    }

    // ------------------------------------------------------------------ Ngộ Không (bị phong ấn)
    public void openNgoKhong(Npc npc, Player pl) {
        String say = "Chu cha nga, lão Tôn đói quá! Mang Quả Hồng Đào cho lão Tôn đi\n"
                + "|2|Mỗi Quả Hồng Đào: 1 chữ ngẫu nhiên, Quả Hồng Đào Chín: 3 chữ (Giải, Khai, Phong, Ấn)\n"
                + "Đủ " + CHU_NEED + " chữ mỗi loại gặp Đường Tăng đổi Cải trang Tôn Ngộ Không";
        if (pl.escortStep == 3) {
            npc.createOtherMenu(pl, MENU_NGO_KHONG, say, "Tặng quả\nHồng Đào", "Tặng quả\nHồng Đào Chín", "Hoàn thành\nhộ tống");
        } else {
            npc.createOtherMenu(pl, MENU_NGO_KHONG, say, "Tặng quả\nHồng Đào", "Tặng quả\nHồng Đào Chín");
        }
    }

    public void selectNgoKhong(Player pl, int select) {
        if (select == 2) {
            finishEscort(pl);
            return;
        }
        int id = select == 0 ? HONG_DAO : select == 1 ? HONG_DAO_CHIN : -1;
        if (id < 0) {
            return;
        }
        Item it = InventoryService.gI().findItemBag(pl, id);
        if (it == null || it.quantity <= 0) {
            Service.gI().sendThongBao(pl, "Thí chủ không có " + (id == HONG_DAO ? "Quả Hồng Đào" : "Quả Hồng Đào Chín"));
            return;
        }
        int qty = Math.min(it.quantity, 99); // mỗi lần tối đa 99 quả
        int per = id == HONG_DAO ? 1 : 3;
        int[] got = new int[CHU.length];
        for (int i = 0; i < qty * per; i++) {
            got[Util.nextInt(0, CHU.length - 1)]++;
        }
        int needSlots = 0;
        for (int i = 0; i < CHU.length; i++) {
            if (got[i] > 0 && InventoryService.gI().findItemBag(pl, CHU[i]) == null) {
                needSlots++;
            }
        }
        if (InventoryService.gI().getCountEmptyBag(pl) < needSlots) {
            Service.gI().sendThongBao(pl, "Hành trang cần " + needSlots + " ô trống");
            return;
        }
        InventoryService.gI().subQuantityItemsBag(pl, it, qty);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CHU.length; i++) {
            if (got[i] > 0) {
                InventoryService.gI().addItemBag(pl, ItemService.gI().createNewItem((short) CHU[i], got[i]));
                sb.append(sb.length() > 0 ? ", " : "").append(got[i]).append(" chữ ").append(CHU_NAME[i]);
            }
        }
        InventoryService.gI().sendItemBags(pl);
        Service.gI().sendThongBao(pl, "Lão Tôn cảm ơn " + qty + " quả! Nhận được " + sb);
    }
}
