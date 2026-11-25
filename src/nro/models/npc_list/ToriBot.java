package nro.models.npc_list;

import nro.models.item.Item;
import nro.models.item.Item.ItemOption;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.PetService;
import nro.models.services.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.Duration;

public class ToriBot extends Npc {

    // mùa VIP (tích nạp): thời gian, giá, số lần mua chỉnh ở panel > Mùa VIP Tori Bot (VipSeason)
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    public ToriBot(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        LocalDateTime currentDateTime = LocalDateTime.now();
        nro.models.services.VipSeason.check(player);
        if (currentDateTime.isAfter(nro.models.services.VipSeason.end()) || currentDateTime.isBefore(nro.models.services.VipSeason.start())) {
            this.createOtherMenu(player, 0, "Ngươi tìm ta có việc gì?", "Đóng");
        } else {
            int currentPurchases = player.vipPurchaseCount;
            String vipInfoMessage = "Lưu ý: nâng cấp VIP chỉ được tối đa " + nro.models.services.VipSeason.maxBuy() + " lần mỗi mùa (Bạn đã mua: " + currentPurchases + "/" + nro.models.services.VipSeason.maxBuy() + " lần)";
            String startDateStr = nro.models.services.VipSeason.start().format(DATE_TIME_FORMATTER);
            String endDateStr = nro.models.services.VipSeason.end().format(DATE_TIME_FORMATTER);
            Duration remainingDuration = Duration.between(currentDateTime, nro.models.services.VipSeason.end());
            long days = remainingDuration.toDays();
            long hours = remainingDuration.toHours() % 24;
            long minutes = remainingDuration.toMinutes() % 60;
            long seconds = remainingDuration.getSeconds() % 60;

            String remainingTimeString;
            if (remainingDuration.isNegative() || remainingDuration.isZero()) {
                remainingTimeString = "Mùa VIP sắp kết thúc!";
            } else if (days == 0 && hours == 0 && minutes == 0) {
                remainingTimeString = "Còn " + seconds + " giây!";
            } else if (days == 0 && hours == 0) {
                remainingTimeString = "Còn " + minutes + " phút " + seconds + " giây!";
            } else if (days == 0) {
                remainingTimeString = "Còn " + hours + " giờ " + minutes + " phút " + seconds + " giây!";
            } else {
                remainingTimeString = "Còn " + days + " ngày " + hours + " giờ " + minutes + " phút " + seconds + " giây!";
            }

            this.createOtherMenu(player, 0,
                    "Trong thời gian mùa VIP diễn ra\n(Từ " + startDateStr + " đến hết " + endDateStr + ")\n"
                    + "Thời gian còn lại: " + remainingTimeString + "\n"
                    + // Thêm dòng thời gian còn lại
                    "Tạo nhân vật mới sẽ được X2 Kinh nghiệm toàn mùa\n Nếu nâng cấp VIP sẽ được nhận\n nhiều ưu đãi hơn nữa.\n" + vipInfoMessage,
                    "Vip 1", "Vip 2", "Vip 3", "Vip 4");
        }
    }

    @Override
    public void confirmMenu(Player pl, int select) {
        if (!canOpenNpc(pl)) {
            return;
        }
        LocalDateTime currentDateTime = LocalDateTime.now();
        if (currentDateTime.isAfter(nro.models.services.VipSeason.end()) || currentDateTime.isBefore(nro.models.services.VipSeason.start())) {
            Service.gI().sendThongBao(pl, "Mùa VIP chưa mở hoặc đã kết thúc.");
           // openBaseMenu(pl);
            return;
        }
        switch (pl.idMark.getIndexMenu()) {
            case 0 -> {
                if (select >= 0 && select <= 3) {
                    int lv = select + 1;
                    createOtherMenu(pl, lv, nro.models.services.VipSeason.describe(lv),
                            nro.models.services.VipSeason.price(lv) + "\nđiểm sự kiện [" + pl.event.getEventPoint() + "]", "Đóng");
                }
            }

            case 1, 2, 3, 4 -> {
                int lv = pl.idMark.getIndexMenu();
                if (select != 0) { // "Đóng"
                    return;
                }
                if (nro.models.services.VipSeason.givePet(lv) && pl.pet != null) {
                    createOtherMenu(pl, 10 + lv, "Bạn đang có đệ tử,\nbạn có muốn đổi lấy đệ tử mới (sơ sinh)\ntheo tộc mình không ?\nNhớ thu lại trang bị của đệ tử trước khi đổi.",
                            "Đồng ý", "Không nhận\nđệ tử mới", "Từ chối");
                    return;
                }
                BuyVip(pl, lv, nro.models.services.VipSeason.price(lv), PET_AUTO);
            }
            case 11, 12, 13, 14 -> {
                int lv = pl.idMark.getIndexMenu() - 10;
                if (select == 0 || select == 1) {
                    BuyVip(pl, lv, nro.models.services.VipSeason.price(lv), select == 0 ? PET_NEW : PET_NONE);
                }
            }
        }
    }

    private static final int PET_AUTO = 0, PET_NEW = 1, PET_NONE = 2;

    private void BuyVip(Player pl, int vipLevel, int cost, int petMode) {
        LocalDateTime currentDateTime = LocalDateTime.now();
        if (currentDateTime.isAfter(nro.models.services.VipSeason.end()) || currentDateTime.isBefore(nro.models.services.VipSeason.start())) {
            Service.gI().sendThongBao(pl, "Mùa VIP chưa mở hoặc đã kết thúc.");
            openBaseMenu(pl); // Mở lại menu mặc định
            return;
        }

        nro.models.services.VipSeason.check(pl);
        if (pl.vipPurchaseCount >= nro.models.services.VipSeason.maxBuy()) {
            Service.gI().sendThongBao(pl, "Bạn đã mua tối đa " + nro.models.services.VipSeason.maxBuy() + " lượt VIP mùa này rồi!");
            return;
        }
        if (pl.vip >= vipLevel) {
            Service.gI().sendThongBao(pl, "Bạn đã có VIP cấp " + pl.vip + ". Bạn vẫn có thể mua gói này để nhận lại phần thưởng.");
        }

        int need = nro.models.services.VipSeason.rewards(vipLevel).size();
        if (InventoryService.gI().getCountEmptyBag(pl) < need) {
            Service.gI().sendThongBao(pl, "Hành trang cần trống ít nhất " + need + " ô");
            return;
        }
        // dùng chung điểm sự kiện với cửa hàng đổi điểm Quy Lão (một loại điểm, không còn "điểm mùa" riêng)
        if (pl.event.getEventPoint() < cost) {
            Service.gI().sendThongBao(pl, "Không đủ điểm sự kiện (" + cost + ") để mua VIP!");
            return;
        }

        pl.event.subEventPoint(cost);
        if (vipLevel > pl.vip) {
            pl.vip = (byte) vipLevel;
        }
        pl.vipPurchaseCount++;
        grant(pl, vipLevel, petMode);

        Service.gI().sendThongBao(pl, "Mua VIP " + vipLevel + " thành công! Bạn đã mua " + pl.vipPurchaseCount + "/" + nro.models.services.VipSeason.maxBuy() + " lượt VIP mùa này.");
        openBaseMenu(pl);
    }

    /** Quà theo cấu hình panel > Mùa VIP Tori Bot: VIP (X3 kinh nghiệm) tới hết mùa, đệ tử nếu chưa có, các vật phẩm. */
    private void grant(Player pl, int vipLevel, int petMode) {
        long seasonEnd = nro.models.services.VipSeason.end().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        pl.timevip = Math.max(pl.timevip, seasonEnd);
        if (nro.models.services.VipSeason.givePet(vipLevel) && petMode != PET_NONE) {
            if (pl.pet == null) {
                PetService.gI().createNormalPet(pl);
            } else if (petMode == PET_NEW) { // đổi lấy đệ tử thường sơ sinh theo tộc người chơi
                PetService.gI().changeNormalPet(pl, pl.gender);
            }
        }
        for (nro.models.services.VipSeason.Reward r : nro.models.services.VipSeason.rewards(vipLevel)) {
            if (r.isRandGem()) {
                Service.gI().sendThongBao(pl, "Nhận " + r.giveGem(pl) + (r.id == -11 ? " ngọc xanh" : " hồng ngọc"));
                continue;
            }
            int id = r.pickId();
            Item it = ItemService.gI().createNewItem((short) id, r.qty);
            for (int[] op : r.opts) {
                it.itemOptions.add(new ItemOption(op[0], op[1]));
            }
            if (r.opts.length == 0 && nro.models.services.VipSeason.isWearable(it.template)) { // CT / balo / pet không ghi chỉ số: chỉ số gốc, vĩnh viễn
                it.itemOptions.addAll(ItemService.gI().getListOptionItemShop((short) id));
                it.itemOptions.removeIf(io -> io.optionTemplate != null && io.optionTemplate.id == 93);
            }
            InventoryService.gI().addItemBag(pl, it);
        }
        InventoryService.gI().sendItemBags(pl);
        nro.models.services.PlayerService.gI().sendInfoHpMpMoney(pl); // ngọc / hồng ngọc ngẫu nhiên
    }
}
