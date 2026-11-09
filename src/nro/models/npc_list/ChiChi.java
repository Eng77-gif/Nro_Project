package nro.models.npc_list;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import nro.models.consts.ConstNpc;
import nro.models.map.service.NpcService;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.services.SeasonEventService;
import nro.models.services.Service;

/**
 * Chi Chi (Đảo Kamê) theo sự kiện đang chạy (giống video Dũng Phạm):
 * các đua top của sự kiện (thông tin, Top 100, Xem điểm), nhận giải đua top, Cửa hàng sự kiện,
 * hủy trang bị có hạn sử dụng, hủy bóng mờ hạn dưới 35 ngày. Đổi sự kiện thì menu / cửa hàng đổi theo.
 *
 * @author Việt
 */
public class ChiChi extends Npc {

    private static final int MENU_TOP = 2096; // + chỉ số top
    private static final int MENU_HUY_HSD = 2190;
    private static final int MENU_TOP_ENDED = 2195;
    private final Map<Long, SeasonEventService.Top> endedMenu = new ConcurrentHashMap<>();
    private static final int MENU_HUY_BONG_MO = 2191;

    /** playerId -> danh sách hành động của menu chính đang mở */
    private final Map<Long, List<String>> actions = new ConcurrentHashMap<>();

    public ChiChi(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (!canOpenNpc(player)) {
            return;
        }
        SeasonEventService sv = SeasonEventService.gI();
        List<String> act = new ArrayList<>();
        List<String> label = new ArrayList<>();
        List<SeasonEventService.Top> tops = sv.currentTops();
        for (int i = 0; i < tops.size(); i++) {
            act.add("TOP" + i);
            label.add("Top\n" + sv.topName(tops.get(i)));
        }
        List<SeasonEventService.Top> ended = sv.endedTops();
        for (int i = 0; i < ended.size(); i++) {
            act.add("END" + i);
            label.add("Top\n" + sv.topName(ended.get(i)));
        }
        if (ended.isEmpty() && tops.isEmpty() && sv.hasTopRewardBox(player)) {
            act.add("CLAIM");
            label.add("Nhận thưởng\nSự kiện\nĐua Top");
        }
        act.add("SHOP");
        label.add("Cửa\nhàng");
        act.add("HSD");
        label.add("Hủy bỏ\ntrang bị\ncó hsd");
        act.add("BONGMO");
        label.add("Hủy bỏ\nbóng mờ\nhsd dưới\n35 ngày");
        act.add("CLOSE");
        label.add("Đóng");
        actions.put(player.id, act);
        String name = sv.currentName();
        createOtherMenu(player, ConstNpc.BASE_MENU, name == null ? "Hiện chưa có sự kiện nào.\nBạn muốn hỏi chi ?" : "Sự kiện " + name + "\nBạn muốn hỏi chi ?",
                label.toArray(new String[0]));
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (!canOpenNpc(player)) {
            return;
        }
        SeasonEventService sv = SeasonEventService.gI();
        int menu = player.idMark.getIndexMenu();
        if (player.idMark.isBaseMenu()) {
            List<String> act = actions.get(player.id);
            if (act == null || select < 0 || select >= act.size()) {
                return;
            }
            String a = act.get(select);
            if (a.startsWith("TOP")) {
                int i = Integer.parseInt(a.substring(3));
                List<SeasonEventService.Top> tops = sv.currentTops();
                if (i < tops.size()) {
                    SeasonEventService.Top t = tops.get(i);
                    // đua top đang diễn ra: chưa có nút nhận thưởng (chỉ hiện khi đua top kết thúc)
                    createOtherMenu(player, MENU_TOP + i, sv.topIntro(t), "Top 100\n" + sv.topName(t), "Xem điểm", "Đóng");
                }
                return;
            }
            if (a.startsWith("END")) {
                int i = Integer.parseInt(a.substring(3));
                List<SeasonEventService.Top> ended = sv.endedTops();
                if (i < ended.size()) {
                    SeasonEventService.Top t = ended.get(i);
                    endedMenu.put(player.id, t);
                    createOtherMenu(player, MENU_TOP_ENDED, sv.endedTopIntro(t, player), "Top 100\n" + sv.topName(t), "Xem điểm", "Nhận thưởng\nSự kiện\nĐua Top");
                }
                return;
            }
            switch (a) {
                case "CLAIM" ->
                    sv.deliverTopRewards(player);
                case "SHOP" ->
                    sv.openEventShop(player, false);
                case "HSD" -> {
                    int n = sv.destroyTimedItems(player, false, true);
                    if (n == 0) {
                        Service.gI().sendThongBao(player, "Hành trang không có trang bị nào có hạn sử dụng");
                    } else {
                        createOtherMenu(player, MENU_HUY_HSD, "Hủy bỏ " + n + " vật phẩm có hạn sử dụng trong hành trang?\nVật phẩm đã hủy không lấy lại được.", "Đồng ý", "Từ chối");
                    }
                }
                case "BONGMO" -> {
                    int n = sv.destroyTimedItems(player, true, true);
                    if (n == 0) {
                        Service.gI().sendThongBao(player, "Hành trang không có bóng mờ nào hạn dưới 35 ngày");
                    } else {
                        createOtherMenu(player, MENU_HUY_BONG_MO, "Hủy bỏ " + n + " bóng mờ hạn dưới 35 ngày trong hành trang?", "Đồng ý", "Từ chối");
                    }
                }
                default -> {
                }
            }
            return;
        }
        if (menu == MENU_TOP_ENDED) {
            SeasonEventService.Top t = endedMenu.get(player.id);
            if (t == null) {
                return;
            }
            switch (select) {
                case 0 ->
                    sv.showTop100(player, t);
                case 1 ->
                    NpcService.gI().createTutorial(player, tempId, this.avartar, sv.endedViewPoint(t, player));
                case 2 ->
                    sv.openTopRewardBox(player);
                default -> {
                }
            }
            return;
        }
        if (menu >= MENU_TOP && menu < MENU_TOP + 50) {
            List<SeasonEventService.Top> tops = sv.currentTops();
            int i = menu - MENU_TOP;
            if (i >= tops.size()) {
                Service.gI().sendThongBao(player, "Sự kiện đã kết thúc");
                return;
            }
            SeasonEventService.Top t = tops.get(i);
            switch (select) {
                case 0 ->
                    sv.showTop100(player, t);
                case 1 ->
                    NpcService.gI().createTutorial(player, tempId, this.avartar, sv.viewPoint(t, player));
                default -> {
                }
            }
        } else if (menu == MENU_HUY_HSD && select == 0) {
            Service.gI().sendThongBao(player, "Đã hủy " + sv.destroyTimedItems(player, false, false) + " vật phẩm có hạn sử dụng");
        } else if (menu == MENU_HUY_BONG_MO && select == 0) {
            Service.gI().sendThongBao(player, "Đã hủy " + sv.destroyTimedItems(player, true, false) + " bóng mờ");
        }
    }
}
