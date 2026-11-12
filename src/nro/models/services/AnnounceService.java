package nro.models.services;

import java.util.List;
import nro.models.item.Item;
import nro.models.map.ItemMap;
import nro.models.player.Player;
import nro.models.server.ServerNotify;

/**
 * Thông báo toàn server:
 * - Top 1 - 10 các đua top sự kiện đang chạy (Chi Chi) và Hộp đen bí ẩn vừa đăng nhập (chat thế giới), mỗi lần đăng nhập.
 * - Nhặt trang bị set kích hoạt rơi từ quái / boss (thông báo server không có [HT] + chat thế giới có [HT]): set riêng (Thần Vũ Trụ Kaio, Nail chiến binh Namếc, Cađic M, Champa...) kèm tên set,
 *   set cũ (Sôngôku, Kakarot, Nappa...) chỉ báo chung "Set kích hoạt".
 * - Nhặt cải trang / pet / đeo lưng / thú cưỡi vĩnh viễn rơi từ boss, chế tạo đồ Thiên Sứ (thông báo server).
 * - Mở hộp quà nhận được cải trang, pet, đeo lưng, thú cưỡi.
 */
public class AnnounceService {

    private static AnnounceService instance;

    public static AnnounceService gI() {
        if (instance == null) {
            instance = new AnnounceService();
        }
        return instance;
    }

    // ------------------------------------------------------------------ top 1 đăng nhập

    /** Đứng top 1 - 10 đua top sự kiện / Hộp đen bí ẩn thì báo chat thế giới mỗi lần đăng nhập. */
    public void onLogin(Player pl) {
        if (pl == null || pl.isBot || pl.getSession() == null || pl.loginAnnounced == pl.getSession()) {
            return; // mỗi lần đăng nhập (mỗi phiên) báo 1 lần; client gửi "client ok" nhiều lần trong 1 phiên
        }
        pl.loginAnnounced = pl.getSession();
        new Thread(() -> {
            try {
                announce(pl);
            } catch (Exception e) {
                nro.models.utils.Logger.error("Thông báo top khi đăng nhập lỗi: " + e + "\n");
            }
        }, "announce-login").start(); // đọc hạng từ DB, không chặn luồng đăng nhập
    }

    private void announce(Player pl) {
        List<String> tops = new java.util.ArrayList<>();
        try {
            List<Object[]> hd = nro.models.database.PlayerExtraDAO.top("hdba_count", TOP_LIMIT);
            for (int i = 0; i < hd.size(); i++) {
                if (((Number) hd.get(i)[0]).longValue() == pl.id) {
                    append(tops, i + 1, "Hộp đen bí ẩn");
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        for (SeasonEventService.Season s : SeasonEventService.gI().activeSeasons()) { // đua top mọi sự kiện đang chạy ở Chi Chi
            for (SeasonEventService.Top t : s.tops) {
                int rank = SeasonEventService.gI().myPoint(t, pl)[1];
                if (rank >= 1 && rank <= TOP_LIMIT) {
                    append(tops, rank, SeasonEventService.gI().topName(t).replaceFirst("(?iu)^đua top ", ""));
                }
            }
        }
        for (String t : tops) { // mỗi đua top 1 tin như bản chính thức: "TOP 8 Túi mù Halloween kiily888 vừa đăng nhập"
            ChatGlobalService.gI().chatVip(pl, t + " " + pl.name + " vừa đăng nhập");
        }
    }

    private static final int TOP_LIMIT = 10;

    private void append(List<String> tops, int rank, String name) {
        tops.add("TOP " + rank + " " + name);
    }

    // ------------------------------------------------------------------ nhặt đồ rơi từ quái / boss

    /** Set cũ 3 hành tinh (Sôngôku, Thên Xin Hăng, Kirin, Picolo, Ốc tiêu, Pikkoro Daimao, Kakarot, Ca Đíc, Nappa): chỉ báo chung "Set kích hoạt". */
    private static boolean isOldSet(int optId) {
        return optId >= 127 && optId <= 135;
    }

    /** Tên set kích hoạt của món đồ: "" với set cũ, " Set ..." với set riêng (Thần Vũ Trụ Kaio, Cađic M, Nail chiến binh, Champa...), null nếu không phải SKH. */
    private static String skhName(Item item) {
        if (item == null || item.template == null || item.template.type > 4) {
            return null;
        }
        String name = null;
        for (Item.ItemOption io : item.itemOptions) {
            String n = io.optionTemplate == null ? null : io.optionTemplate.name;
            if (n == null || !n.startsWith("Set ")) {
                continue;
            }
            if (isOldSet(io.optionTemplate.id)) {
                name = "";
            } else {
                return " " + n.replaceAll("\\s*[#%(].*$", "").trim();
            }
        }
        return name;
    }

    public void onPickItem(Player pl, ItemMap itemMap, Item item) {
        if (pl == null || itemMap == null || item == null || item.template == null) { // bot nhặt cũng báo như người chơi
            return;
        }
        String skh = skhName(item);
        if (itemMap.fromMob) {
            if (skh != null) {
                announceSkh(pl, pl.name + " vừa đánh quái may mắn nhận được 1 trang bị Set kích hoạt" + skh);
            } else if (item.template.id >= 555 && item.template.id <= 567) { // đồ Thần Linh
                ServerNotify.gI().notify(pl.name + " vừa đánh quái may mắn nhận được 1 trang bị " + item.template.name);
            }
            return;
        }
        if (itemMap.fromBoss == null) {
            return;
        }
        if (skh != null) {
            announceSkh(pl, pl.name + " vừa hạ boss " + itemMap.fromBoss + " may mắn nhận được 1 trang bị Set kích hoạt" + skh);
        } else if (isRare(item) && !hasExpiry(item)) {
            ServerNotify.gI().notify("Chúc mừng " + pl.name + " đã may mắn hạ Boss " + itemMap.fromBoss + " nhận được " + item.template.name + " hạn dùng vĩnh viễn");
        }
    }

    /** Thông báo server (chạy chữ, không có [HT]) và chat thế giới gửi từ chính người nhặt (có [HT]). */
    private void announceSkh(Player pl, String text) {
        ServerNotify.gI().notify(text);
        ChatGlobalService.gI().chatVip(pl, "[HT] " + text);
    }

    private static boolean hasExpiry(Item it) {
        for (Item.ItemOption io : it.itemOptions) {
            if (io.optionTemplate.id == 93 && io.param > 0) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ chế tạo đồ thiên sứ

    /** Whis: "X đã chế tạo thành công Găng Thiên Sứ với 2 dòng chỉ số thưởng, mọi người đều kinh ngạc." (dòng thưởng = option 15). */
    public void onCraftThienSu(Player pl, Item it) {
        if (pl == null || it == null || it.template == null) {
            return;
        }
        int bonus = 0;
        for (Item.ItemOption io : it.itemOptions) {
            if (io.optionTemplate.id == 15) {
                bonus = io.param;
            }
        }
        ServerNotify.gI().notify("Whis: " + pl.name + " đã chế tạo thành công " + it.template.name
                + (bonus > 0 ? " với " + bonus + " dòng chỉ số thưởng, mọi người đều kinh ngạc." : ", mọi người đều trầm trồ."));
    }

    // ------------------------------------------------------------------ mở hộp

    /** Cải trang, đeo lưng có hình, thú cưỡi, pet có hình. */
    public static boolean isRare(Item it) {
        if (it == null || it.template == null) {
            return false;
        }
        int type = it.template.type;
        return type == 5 || type == 23 || type == 24 || ((type == 11 || type == 27) && it.template.part >= 0);
    }

    public void onOpenBox(Player pl, int boxId, Item it) {
        if (pl == null || !isRare(it)) {
            return;
        }
        int days = 0;
        for (Item.ItemOption io : it.itemOptions) {
            if (io.optionTemplate.id == 93) {
                days = io.param;
            }
        }
        if (days > 0) {
            return; // đồ có hạn dùng: không thông báo, chỉ thông báo đồ vĩnh viễn
        }
        String box = ItemService.gI().getTemplate(boxId) != null ? ItemService.gI().getTemplate(boxId).name : "hộp quà";
        ServerNotify.gI().notify("Chúc mừng " + pl.name + " đã may mắn mở " + box + " nhận được " + it.template.name
                + " hạn dùng vĩnh viễn");
    }
}
