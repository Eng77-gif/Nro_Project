package nro.models.services;

import java.util.Set;
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
    public static final int HONG_DAO = 541, HONG_DAO_CHIN = 542;
    public static final int POINT_HONG_DAO = 1, POINT_HONG_DAO_CHIN = 5;
    /** Cải trang chỉ dành cho đệ tử */
    public static final int CT_BAT_GIOI_DE = 2128, CT_NGO_KHONG_DE = 2129, CT_SA_TANG_DE = 2130;
    public static final Set<Integer> PET_ONLY = Set.of(CT_BAT_GIOI_DE, CT_NGO_KHONG_DE, CT_SA_TANG_DE);
    public static final int YEU_QUAI_NUI = 1988;
    public static final int REWARD_DAYS = 30;

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
    public void openDuongTang(Npc npc, Player pl) {
        boolean inMap = pl.zone != null && pl.zone.map.mapId >= 122 && pl.zone.map.mapId <= 124;
        if (!active() && !inMap) {
            npc.createOtherMenu(pl, MENU_DUONG_TANG, "A mi phò phò, Ngũ Hành Sơn chỉ mở trong sự kiện Ngũ Hành Sơn", "Nhận\nthưởng", "Từ chối");
            return;
        }
        npc.createOtherMenu(pl, MENU_DUONG_TANG, "A mi phò phò, thí chủ hãy giúp giải cứu đồ đệ của bần tăng đang bị phong ấn tại Ngũ Hành Sơn\n"
                + "|2|Điểm công đức: " + pl.congDuc,
                inMap ? "Về\nlàng" : "Đồng ý", "Nhận\nthưởng", "Từ chối");
    }

    public void selectDuongTang(Npc npc, Player pl, int menu, int select) {
        if (menu == MENU_DUONG_TANG) {
            boolean inMap = pl.zone != null && pl.zone.map.mapId >= 122 && pl.zone.map.mapId <= 124;
            boolean open = active() || inMap;
            int s = open ? select : select + 1; // khi đóng không có nút "Đồng ý"
            switch (s) {
                case 0 -> {
                    if (inMap) {
                        ChangeMapService.gI().changeMapBySpaceShip(pl, 21 + pl.gender, -1, 250);
                    } else {
                        ChangeMapService.gI().changeMapBySpaceShip(pl, MAP_NGU_HANH_SON, -1, 560);
                    }
                }
                case 1 -> openRewards(npc, pl);
            }
        } else if (menu == MENU_REWARD) {
            if (select < 0 || select >= REWARDS.length) {
                return;
            }
            reward(pl, select);
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
                    Service.gI().sendThongBao(pl, ct.template.name + " không có hạn sử dụng (đã vĩnh viễn)");
                    return;
                }
                hsd.param += idx == 0 ? 7 : 9;
                pl.congDuc -= cost;
                InventoryService.gI().sendItemBody(pl);
                if (idx == 1) {
                    Service.gI().showInfoPet(pl);
                }
                Service.gI().sendThongBao(pl, ct.template.name + " được cộng thêm " + (idx == 0 ? 7 : 9) + " ngày sử dụng");
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
                Item it = ItemService.gI().createNewItem((short) itemId);
                it.itemOptions.addAll(ItemService.gI().getListOptionItemShop((short) itemId));
                it.itemOptions.removeIf(o -> o.optionTemplate != null && o.optionTemplate.id == 93);
                it.itemOptions.add(new Item.ItemOption(93, REWARD_DAYS));
                InventoryService.gI().addItemBag(pl, it);
                InventoryService.gI().sendItemBags(pl);
                pl.congDuc -= cost;
                Service.gI().sendThongBao(pl, "Bạn nhận được " + it.template.name + " (" + REWARD_DAYS + " ngày)");
            }
        }
        nro.models.database.PlayerExtraDAO.save(pl);
    }

    // ------------------------------------------------------------------ Ngộ Không (bị phong ấn)
    public void openNgoKhong(Npc npc, Player pl) {
        npc.createOtherMenu(pl, MENU_NGO_KHONG, "Chu cha nga, lão Tôn đói quá! Mang Quả Hồng Đào cho lão Tôn đi\n"
                + "|2|Mỗi Quả Hồng Đào: " + POINT_HONG_DAO + " điểm công đức, Quả Hồng Đào Chín: " + POINT_HONG_DAO_CHIN + " điểm\n"
                + "Điểm công đức: " + pl.congDuc, "Tặng quả\nHồng Đào", "Tặng quả\nHồng Đào Chín");
    }

    public void selectNgoKhong(Player pl, int select) {
        int id = select == 0 ? HONG_DAO : select == 1 ? HONG_DAO_CHIN : -1;
        if (id < 0) {
            return;
        }
        Item it = InventoryService.gI().findItemBag(pl, id);
        if (it == null || it.quantity <= 0) {
            Service.gI().sendThongBao(pl, "Thí chủ không có " + (id == HONG_DAO ? "Quả Hồng Đào" : "Quả Hồng Đào Chín"));
            return;
        }
        int qty = it.quantity;
        int add = qty * (id == HONG_DAO ? POINT_HONG_DAO : POINT_HONG_DAO_CHIN);
        InventoryService.gI().subQuantityItemsBag(pl, it, qty);
        InventoryService.gI().sendItemBags(pl);
        pl.congDuc += add;
        nro.models.database.PlayerExtraDAO.save(pl);
        Service.gI().sendThongBao(pl, "Đã tặng " + qty + " quả, +" + add + " điểm công đức (đang có " + pl.congDuc + ")");
    }
}
