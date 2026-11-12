package nro.models.services;

import java.util.List;
import nro.models.item.Item;
import nro.models.map.ItemMap;
import nro.models.player.Player;
import nro.models.server.ServerNotify;

/**
 * Thông báo toàn server:
 * - Top 1 - 10 các đua top sự kiện đang chạy (Chi Chi) và Hộp đen bí ẩn vừa đăng nhập (chat thế giới), mỗi lần đăng nhập.
 * - Đánh quái nhặt được trang bị set kích hoạt (thông báo hệ thống + chat thế giới): 3 set riêng (Thần Vũ Trụ Kaio, Nail chiến binh Namếc, Cađic M) kèm tên set,
 *   set cũ (Sôngôku, Kakarot...) chỉ báo chung.
 * - Mở hộp quà nhận được cải trang, pet, đeo lưng, thú cưỡi.
 */
public class AnnounceService {

    /** Set kích hoạt riêng rơi từ quái: Nail chiến binh Namếc, Cađic M, Thần Vũ Trụ Kaio */
    private static final int[] SKH_RIENG = {237, 241, 245};

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
                if (pl.hasMam()) { // đệ tử nhắc hạn tìm set kích hoạt (còn mầm), chờ đệ tử hiện trên map
                    Thread.sleep(4000);
                    if (pl.pet != null) {
                        String until = new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new java.util.Date(pl.newMemberUntil));
                        nro.models.services.Service.gI().chatJustForMe(pl, pl.pet, "Thời gian tìm set kích hoạt đến " + until);
                    }
                }
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

    // ------------------------------------------------------------------ nhặt set kích hoạt riêng từ quái

    public void onPickItem(Player pl, ItemMap itemMap, Item item) {
        if (pl == null || itemMap == null || !itemMap.fromMob || item == null || item.template == null || item.template.type > 4) {
            return;
        }
        String skh = null;
        for (Item.ItemOption io : item.itemOptions) {
            for (int id : SKH_RIENG) {
                if (io.optionTemplate.id == id) { // 3 set riêng: báo kèm tên set
                    announceSkh(pl, " " + io.optionTemplate.name);
                    return;
                }
            }
            if (io.optionTemplate.name != null && io.optionTemplate.name.startsWith("Set ")) {
                skh = " " + io.optionTemplate.name.replaceAll("\\s*[#%(].*$", "").trim(); // set cũ (Sôngôku, Kakarot...): kèm tên set, bỏ phần chỉ số
            }
        }
        if (skh != null) {
            announceSkh(pl, skh);
        }
    }

    /** Báo song song: thông báo hệ thống (chạy chữ) và chat thế giới. */
    private void announceSkh(Player pl, String setName) {
        String text = "[HT] " + pl.name + " vừa đánh quái may mắn nhận được 1 trang bị Set kích hoạt" + setName;
        ServerNotify.gI().notify(text);
        ChatGlobalService.gI().chatVip(pl, text); // tin thế giới gửi từ chính người nhặt, như bản chính thức
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
