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
                announce(pl);
            } catch (Exception e) {
                nro.models.utils.Logger.error("Thông báo top khi đăng nhập lỗi: " + e + "\n");
            }
        }, "announce-login").start(); // đọc hạng từ DB, không chặn luồng đăng nhập
    }

    private void announce(Player pl) {
        StringBuilder tops = new StringBuilder();
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
        if (tops.length() > 0) {
            ChatGlobalService.gI().chatVip(pl, tops + " vừa đăng nhập!");
        }
    }

    private static final int TOP_LIMIT = 10;

    private void append(StringBuilder sb, int rank, String name) {
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append("Top ").append(rank).append(" sự kiện ").append(name);
    }

    // ------------------------------------------------------------------ nhặt set kích hoạt riêng từ quái

    public void onPickItem(Player pl, ItemMap itemMap, Item item) {
        if (pl == null || itemMap == null || !itemMap.fromMob || item == null || item.template == null || item.template.type > 4) {
            return;
        }
        boolean skh = false;
        for (Item.ItemOption io : item.itemOptions) {
            for (int id : SKH_RIENG) {
                if (io.optionTemplate.id == id) { // 3 set riêng: báo kèm tên set
                    announceSkh(pl, " " + io.optionTemplate.name);
                    return;
                }
            }
            if (io.optionTemplate.name != null && io.optionTemplate.name.startsWith("Set ")) {
                skh = true;
            }
        }
        if (skh) { // set cũ (Sôngôku, Kakarot, Picolo...): không kèm tên set
            announceSkh(pl, "");
        }
    }

    /** Báo song song: thông báo hệ thống (chạy chữ) và chat thế giới. */
    private void announceSkh(Player pl, String setName) {
        ServerNotify.gI().notify(pl.name + " vừa đánh quái may mắn nhận được 1 trang bị Set kích hoạt" + setName);
        ChatGlobalService.gI().chatVip(pl, "vừa đánh quái may mắn nhận được 1 trang bị Set kích hoạt" + setName);
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
