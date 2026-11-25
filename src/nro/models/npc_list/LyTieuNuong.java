package nro.models.npc_list;

import nro.models.consts.ConstNpc;
import nro.models.minigame.ChonAiDay_Gem;
import nro.models.minigame.ChonAiDay_Gold;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.services.Service;
import nro.models.services_func.Input;
import nro.models.utils.Util;
import nro.models.services_func.MiniGame;

public class LyTieuNuong extends Npc {

    public LyTieuNuong(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (canOpenNpc(player)) {
            if (this.mapId == 5) { // như bản chính thức (video): chỉ Kéo Búa Bao, Chọn ai đây
                createOtherMenu(player, ConstNpc.BASE_MENU, "Mini game.", "Kéo\nBúa\nBao", "Chọn ai đây", "Đóng");
                return;
            }
            createOtherMenu(player, ConstNpc.BASE_MENU, "Mini game.",
                    "Kéo\nBúa\nBao",
                    "Con số\nmay mắn\nthỏi vàng",
                    "Con số\nmay mắn\nngọc xanh",
                    "Chọn ai đây",
                    "Điểm\nQuy Lão",
                    "Đóng");
            return;
        }
    }

    private static final int MENU_QL_POINT = 54_401;

    // ---------------- Kéo Búa Bao như bản chính thức: chọn là ra kết quả ngay; 15 giây không chọn thì dừng chơi
    private static final String[] KBB = {"Kéo", "Búa", "Bao"};
    private static final int KBB_SECONDS = 15;
    /** playerId -> {mức cược, lựa chọn (-1 chưa chọn), mã ván} */
    private static final java.util.Map<Long, long[]> ROUNDS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ScheduledExecutorService KBB_TIMER = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "keo-bua-bao");
        t.setDaemon(true);
        return t;
    });
    private static final java.util.concurrent.atomic.AtomicLong ROUND_SEQ = new java.util.concurrent.atomic.AtomicLong();

    private void startRound(Player player, long bet) {
        startRound(player, bet, null);
    }

    /** last: kết quả ván trước (như bản chính thức hiện ngay trong bảng của ván mới) */
    private void startRound(Player player, long bet, String last) {
        if (player.inventory.gold < bet) {
            ROUNDS.remove(player.id);
            Service.gI().sendThongBao(player, "Bạn không đủ vàng để chơi!");
            return;
        }
        long id = ROUND_SEQ.incrementAndGet();
        ROUNDS.put(player.id, new long[]{bet, -1, id});
        nro.models.services.ItemTimeService.gI().sendTextTimeKeoBuaBao(player, KBB_SECONDS);
        String head = last != null ? last : nro.models.consts.ConstFont.BOLD_GREEN + "Mức vàng cược: " + Util.numberToMoney(bet)
                + "\n" + nro.models.consts.ConstFont.BOLD_GREEN + "Hãy chọn Kéo, Búa hoặc Bao\n" + nro.models.consts.ConstFont.BOLD_RED + "Thời gian " + KBB_SECONDS + " giây bắt đầu";
        createOtherMenu(player, ConstNpc.OTT, head,
                "Kéo", "Búa", "Bao", "Đổi\nmức cược", "Nghỉ chơi");
        KBB_TIMER.schedule(() -> {
            try {
                endRound(player, id);
            } catch (Exception e) {
                nro.models.utils.Logger.logException(LyTieuNuong.class, e);
            }
        }, KBB_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
    }

    private void endRound(Player player, long id) {
        long[] r = ROUNDS.get(player.id);
        if (r == null) {
            return;
        }
        synchronized (r) { // người chơi bấm đúng lúc hết giờ: chỉ xử lý 1 lần
            if (r[2] != id) {
                return; // đã nghỉ / đổi cược / sang ván mới
            }
            r[2] = -1;
        }
        if (r[1] < 0 || player.zone == null || player.zone.map.mapId != this.mapId) { // không chọn hoặc rời map: dừng chơi
            ROUNDS.remove(player.id);
            nro.models.services.ItemTimeService.gI().removeTextTime(player, nro.models.item.ItemTime.TIME_KEO_BUA_BAO);
            return;
        }
        long bet = r[0];
        if (player.inventory.gold < bet) {
            ROUNDS.remove(player.id);
            Service.gI().sendThongBao(player, "Bạn không đủ vàng để chơi!");
            return;
        }
        int npcPick = Util.nextInt(0, 2);
        this.npcChat(player, KBB[npcPick]);
        String result = getResult(KBB[(int) r[1]], KBB[npcPick]);
        String b = nro.models.consts.ConstFont.BOLD_BLUE;
        String text = b + "Bạn ra cái <" + KBB[(int) r[1]] + ">\n" + b + "Tôi ra cái <" + KBB[npcPick] + ">\n" + b; // như bản chính thức
        if (result.equals("Thắng")) {
            long reward = bet * 96 / 100;
            player.inventory.gold += reward;
            text += "Bạn thắng rồi, nhận " + Util.numberToMoney(reward) + " vàng";
        } else if (result.equals("Thua")) {
            player.inventory.gold -= bet;
            text += "Bạn thua rồi, mất " + Util.numberToMoney(bet) + " vàng nhé haha";
        } else {
            text += "Hòa nhau nhé haha";
        }
        Service.gI().sendMoney(player);
        startRound(player, bet, text); // ván tiếp theo cho tới khi Nghỉ chơi
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (player.idMark.getIndexMenu() == MENU_QL_POINT) {
            if (select == 0) {
                nro.models.services.SeasonEventService.gI().openEventShop(player, true);
            }
            return;
        }
        String time = ((ChonAiDay_Gold.gI().lastTimeEnd - System.currentTimeMillis()) / 1000) + " giây";
        if (((ChonAiDay_Gold.gI().lastTimeEnd - System.currentTimeMillis()) / 1000) < 0) {
            ChonAiDay_Gold.gI().lastTimeEnd = System.currentTimeMillis() + 300000;
        }
        String time3 = ((ChonAiDay_Gem.gI().lastTimeEnd - System.currentTimeMillis()) / 1000) + " giây";
        if (((ChonAiDay_Gem.gI().lastTimeEnd - System.currentTimeMillis()) / 1000) < 0) {
            ChonAiDay_Gem.gI().lastTimeEnd = System.currentTimeMillis() + 300000;
        }

        if (canOpenNpc(player)) {
            if (this.mapId == 5 || this.mapId == -1) { // -1: mở từ Menu game
                if (player.idMark.isBaseMenu()) {
                    if (this.mapId == 5) {
                        select = select == 0 ? 0 : select == 1 ? 3 : -1; // menu rút gọn: Kéo Búa Bao, Chọn ai đây, Đóng
                    }
                    switch (select) {
                        case 0: // Kéo, Búa, Bao
                            createOtherMenu(player, ConstNpc.KEO_BUA_BAO, "Hãy chọn mức cược.", "1 Tr vàng", "5 Tr vàng", "10 Tr vàng");
                            break;
                        case 1: // Con số may mắn vàng
                            xửLýLựaChọnMiniGame_Gold(player);
                            break;
                        case 2: // Con số may mắn ngọc xanh
                            xửLýLựaChọnMiniGame_Gem(player);
                            break;
                        case 4: // Điểm Quy Lão: điểm sự kiện (đổi quà) + điểm đua top tặng ngọc rồng
                            createOtherMenu(player, MENU_QL_POINT, "|7|Điểm Quy Lão\n|1|Điểm sự kiện (đổi thưởng): "
                                    + nro.models.utils.Util.numberToMoney(player.event.getEventPoint())
                                    + "\nĐiểm đua top tặng ngọc rồng: " + nro.models.utils.Util.numberToMoney(player.qlPoints)
                                    + "\n|2|Đổi thưởng bằng điểm sự kiện ở Quy Lão hoặc bấm nút dưới", "Đổi\nthưởng", "Đóng");
                            break;
                        case 3: // Chọn ai đây
                            createOtherMenu(player, ConstNpc.CHON_AI_DAY, "Trò chơi Chọn Ai Đây đang được diễn ra, nếu bạn tin tưởng mình đang tràn đầy "
                                    + "may mắn thì có thể tham gia thử", "Thể lệ", "Chọn\nVàng", "Chọn\nngọc xanh");
                            break;
                    }
                } else if (player.idMark.getIndexMenu() == ConstNpc.KEO_BUA_BAO) {
                    long bet = switch (select) {
                        case 0 -> 1_000_000L;
                        case 1 -> 5_000_000L;
                        case 2 -> 10_000_000L;
                        default -> 0L;
                    };
                    if (bet > 0) {
                        startRound(player, bet);
                    }
                } else if (player.idMark.getIndexMenu() == ConstNpc.OTT) {
                    long[] r = ROUNDS.get(player.id);
                    if (r == null) {
                        return;
                    }
                    if (select < 3) {
                        r[1] = select;
                        endRound(player, r[2]); // như bản chính thức: báo kết quả ngay, sang ván mới (đếm lại 15 giây)
                    } else {
                        ROUNDS.remove(player.id);
                        nro.models.services.ItemTimeService.gI().removeTextTime(player, nro.models.item.ItemTime.TIME_KEO_BUA_BAO);
                        if (select == 3) { // đổi mức cược
                            createOtherMenu(player, ConstNpc.KEO_BUA_BAO, "Hãy chọn mức cược.", "1 Tr vàng", "5 Tr vàng", "10 Tr vàng");
                        }
                    }
                } else if (player.idMark.getIndexMenu() == ConstNpc.CON_SO_MAY_MAN_NGOC_XANH) {
                    xửLýConSoMayManGem(player, select);
                } else if (player.idMark.getIndexMenu() == ConstNpc.CON_SO_MAY_MAN_VANG) {
                    xửLýConSoMayManGold(player, select);
                } else if (player.idMark.getIndexMenu() == ConstNpc.CHON_AI_DAY) {
                    xửLýChonAiDay(player, select, time);
                } else if (player.idMark.getIndexMenu() == ConstNpc.CHON_AI_DAY_VANG) {
                    xửLýChonAiDayVang(player, select, time);
                } else if (player.idMark.getIndexMenu() == ConstNpc.CHON_AI_DAY_NGOC) {
                    xửLýChonAiDayGem(player, select, time3);
                } else if (player.idMark.getIndexMenu() == ConstNpc.UPDATE_CHON_AI_DAY_NGOC) {
                    switch (select) {
                        case 0:
                            createOtherMenu(player, ConstNpc.UPDATE_CHON_AI_DAY_NGOC, "Thời gian từ 8h đến hết 21h59 hằng ngày\n"
                                    + "Mỗi lượt được chọn 10 con số từ 0 đến 99\n"
                                    + "Thời gian mỗi lượt là 5 phút", "Cập nhật", "Đóng");
                            break;
                    }
                }
            }
        }
    }

    private String getResult(String playerChoice, String machineChoice) {
        if (playerChoice.equals(machineChoice)) {
            return "Hòa";
        } else if (playerChoice.equals("Kéo") && machineChoice.equals("Búa")
                || playerChoice.equals("Búa") && machineChoice.equals("Bao")
                || playerChoice.equals("Bao") && machineChoice.equals("Kéo")) {
            return "Thắng";
        } else {
            return "Thua";
        }
    }

    private void xửLýLựaChọnMiniGame_Gem(Player player) {

        String KQ = String.valueOf(MiniGame.gI().MiniGame_S1_Gem.result);
        String Money = String.valueOf(MiniGame.gI().MiniGame_S1_Gem.rewardAmount);
        String second = String.valueOf(MiniGame.gI().MiniGame_S1_Gem.second);
        String number = MiniGame.gI().MiniGame_S1_Gem.strNumber((int) player.id);

        StringBuilder previousResults = new StringBuilder("Lịch sử: ");
        if (MiniGame.gI().MiniGame_S1_Gem.dataKQ_CSMM != null && !MiniGame.gI().MiniGame_S1_Gem.dataKQ_CSMM.isEmpty()) {
            int maxResultsToShow = Math.min(10, MiniGame.gI().MiniGame_S1_Gem.dataKQ_CSMM.size());
            for (int i = MiniGame.gI().MiniGame_S1_Gem.dataKQ_CSMM.size() - maxResultsToShow; i < MiniGame.gI().MiniGame_S1_Gem.dataKQ_CSMM.size(); i++) {
                previousResults.append(MiniGame.gI().MiniGame_S1_Gem.dataKQ_CSMM.get(i));
                if (i < MiniGame.gI().MiniGame_S1_Gem.dataKQ_CSMM.size() - 1) {
                    previousResults.append(", ");
                }
            }
        } else {
            previousResults.append("Chưa có");
        }

        String npcSay = ""
                + "Kết quả giải trước: " + KQ + "\n"
                + previousResults.toString() + "\n"
                + "Tổng giải thưởng: " + Money + " ngọc\n"
                + "<" + second + ">giây\n"
                + (number != null && !number.isEmpty() ? "Các số bạn chọn: " + number : "Bạn chưa chọn số nào.");
        String[] Menus = {
                "Cập nhật",
                "1 Số\n" + MiniGame.gI().MiniGame_S1_Gem.cost + " ngọc xanh",
                "Ngẫu nhiên\n1 số lẻ\n" + MiniGame.gI().MiniGame_S1_Gem.cost + " ngọc xanh",
                "Ngẫu nhiên\n1 số chẵn\n" + MiniGame.gI().MiniGame_S1_Gem.cost + " ngọc xanh",
                "Hướng\ndẫn\nthêm",
                "Đóng"
        };
        createOtherMenu(player, ConstNpc.CON_SO_MAY_MAN_NGOC_XANH, npcSay, Menus);
        return;
    }

    private void xửLýLựaChọnMiniGame_Gold(Player player) {

        String KQ = String.valueOf(MiniGame.gI().MiniGame_S1_Gold.result);
        String Money = Util.mumberToBlue(MiniGame.gI().MiniGame_S1_Gold.rewardAmount);
        String second = String.valueOf(MiniGame.gI().MiniGame_S1_Gold.second);
        String number = MiniGame.gI().MiniGame_S1_Gold.strNumber((int) player.id);

        StringBuilder previousResults = new StringBuilder("Lịch sử: ");
        if (MiniGame.gI().MiniGame_S1_Gold.dataKQ_CSMM != null && !MiniGame.gI().MiniGame_S1_Gold.dataKQ_CSMM.isEmpty()) {
            int maxResultsToShow = Math.min(10, MiniGame.gI().MiniGame_S1_Gold.dataKQ_CSMM.size());
            for (int i = MiniGame.gI().MiniGame_S1_Gold.dataKQ_CSMM.size() - maxResultsToShow; i < MiniGame.gI().MiniGame_S1_Gold.dataKQ_CSMM.size(); i++) {
                previousResults.append(MiniGame.gI().MiniGame_S1_Gold.dataKQ_CSMM.get(i));
                if (i < MiniGame.gI().MiniGame_S1_Gold.dataKQ_CSMM.size() - 1) {
                    previousResults.append(", ");
                }
            }
        } else {
            previousResults.append("Chưa có");
        }

        String npcSay = ""
                + "Kết quả giải trước: " + KQ + "\n"
                + previousResults.toString() + "\n"
                + "Tổng giải thưởng: " + Money + " thỏi vàng\n"
                + "<" + second + ">giây\n"
                + (number != null && !number.isEmpty() ? "Các số bạn chọn: " + number : "Bạn chưa chọn số nào.");
        String[] Menus = {
                "Cập nhật",
                "1 Số\n" + MiniGame.gI().MiniGame_S1_Gold.cost + " thỏi vàng", // Sử dụng cost của Gold
                "Ngẫu nhiên\n1 số lẻ\n" + MiniGame.gI().MiniGame_S1_Gold.cost + " thỏi vàng",
                "Ngẫu nhiên\n1 số chẵn\n" + MiniGame.gI().MiniGame_S1_Gold.cost + " thỏi vàng",
                "Hướng\ndẫn\nthêm",
                "Đóng"
        };
        createOtherMenu(player, ConstNpc.CON_SO_MAY_MAN_VANG, npcSay, Menus);
        return;
    }

    private void xửLýConSoMayManGem(Player player, int select) {
        switch (select) {
            case 0: // cập nhật
                xửLýLựaChọnMiniGame_Gem(player);
                break;
            case 1: // chọn 1 số
                Input.gI().createFormConSoMayMan_Gem(player);
                break;
            case 2: // chọn 1 số lẻ
                MiniGame.gI().MiniGame_S1_Gem.ramdom1SoLe(player);
                break;
            case 3: // chọn 1 số chẵn
                MiniGame.gI().MiniGame_S1_Gem.ramdom1SoChan(player);
                break;
            case 4:
                createOtherMenu(player, 1, "Thời gian từ 8h đến hết 21h59 hằng ngày\n"
                        + "Mỗi lượt được chọn 10 con số từ 0 đến 99\n"
                        + "Thời gian mỗi lượt là 5 phút.", "Đồng ý");
                break;
        }
    }

    private void xửLýConSoMayManGold(Player player, int select) {
        switch (select) {
            case 0: // cập nhật
                xửLýLựaChọnMiniGame_Gold(player);
                break;
            case 1: // chọn 1 số
                Input.gI().createFormConSoMayMan_Gold(player);
                break;
            case 2: // chọn 1 số lẻ
                MiniGame.gI().MiniGame_S1_Gold.ramdom1SoLe(player);
                break;
            case 3: // chọn 1 số chẵn
                MiniGame.gI().MiniGame_S1_Gold.ramdom1SoChan(player);
                break;
            case 4:
                createOtherMenu(player, 1, "Thời gian từ 8h đến hết 21h59 hằng ngày\n"
                        + "Mỗi lượt được chọn 10 con số từ 0 đến 99\n"
                        + "Thời gian mỗi lượt là 5 phút.", "Đồng ý");
                break;
        }
    }

    private void xửLýChonAiDay(Player player, int select, String time) {
        switch (select) {
            case 0:
                createOtherMenu(player, ConstNpc.IGNORE_MENU, "Mỗi lượt chơi có 6 giải thưởng\n"
                        + "Được chọn tối đa 10 lần mỗi giải\n"
                        + "Thời gian 1 lượt chọn là 5 phút\n"
                        + "Khi hết giờ, hệ thống sẽ ngẫu nhiên chọn ra 1 người may mắn\n"
                        + "của từng giải và trao thưởng.\n"
                        + "Lưu ý: Nếu tham gia bằng Ngọc Xanh hoặc Hồng ngọc thì người thắng sẽ nhận thưởng là hồng ngọc.", "OK");
                break;
            case 1:
                createOtherMenu(player, ConstNpc.CHON_AI_DAY_VANG, "Tổng giải thường: " + Util.numberToMoney(ChonAiDay_Gold.gI().goldNormar) + " vàng, cơ hội trúng của bạn là: " + player.percentGold(0) + "%\n"
                        + "Tổng giải VIP: " + Util.numberToMoney(ChonAiDay_Gold.gI().goldVip) + " vàng, cơ hội trúng của bạn là: " + player.percentGold(1) + "%\n"
                        + "Thời gian còn lại: " + time, "Cập nhập", "Thường\n1 triệu\nvàng", "VIP\n10 triệu\nvàng", "Đóng");
                break;
            case 2:
                createOtherMenu(player, ConstNpc.CHON_AI_DAY_NGOC, "Tổng giải thường: " + Util.numberToMoney(ChonAiDay_Gem.gI().gemNormar) + " ngọc xanh, cơ hội trúng của bạn là: " + player.percentGem(0) + "%\nTổng giải VIP: " + Util.numberToMoney(ChonAiDay_Gem.gI().gemVip) + " ngọc xanh, cơ hội trúng của bạn là: " + player.percentGem(1) + "%\n Thời gian còn lại: " + time, "Cập nhập", "Thường\n10 ngọc\nxanh", "VIP\n100 ngọc\nxanh", "Đóng");
                break;
        }
    }

    private void xửLýChonAiDayVang(Player player, int select, String time) {
        switch (select) {
            case 0:
                createOtherMenu(player, ConstNpc.CHON_AI_DAY_VANG, "Tổng giải thường: " + Util.numberToMoney(ChonAiDay_Gold.gI().goldNormar) + " vàng, cơ hội trúng của bạn là: " + player.percentGold(0) + "%\nTổng giải VIP: " + Util.numberToMoney(ChonAiDay_Gold.gI().goldVip) + " vàng, cơ hội trúng của bạn là: " + player.percentGold(1) + "%\n Thời gian còn lại: " + time, "Cập nhập", "Thường\n1 triệu\nvàng", "VIP\n10 triệu\nvàng", "Đóng");
                break;
            case 1:
                xửLýThuong1TrieuVang(player);
                break;
            case 2:
                xửLýVIP10TrieuVang(player);
                break;
        }
    }

    private void xửLýChonAiDayGem(Player player, int select, String time) {
        switch (select) {
            case 0:
                createOtherMenu(player, ConstNpc.CHON_AI_DAY_NGOC, "Tổng giải thường: " + Util.numberToMoney(ChonAiDay_Gem.gI().gemNormar) + " ngọc xanh, cơ hội trúng của bạn là: " + player.percentGem(0) + "%\nTổng giải VIP: " + Util.numberToMoney(ChonAiDay_Gem.gI().gemVip) + " ngọc xanh, cơ hội trúng của bạn là: " + player.percentGem(1) + "%\n Thời gian còn lại: " + time, "Cập nhập", "Thường\n10 ngọc\nxanh", "VIP\n100 ngọc\nxanh", "Đóng");
                break;
            case 1:
                xửLýThuong10NgocXanh(player);
                break;
            case 2:
                xửLýVIP100NgocXanh(player);
                break;
        }
    }

    private void xửLýThuong1TrieuVang(Player player) {
        try {
            String time = ((ChonAiDay_Gold.gI().lastTimeEnd - System.currentTimeMillis()) / 1000) + " giây";
            if (((ChonAiDay_Gold.gI().lastTimeEnd - System.currentTimeMillis()) / 1000) < 0) {
                ChonAiDay_Gold.gI().lastTimeEnd = System.currentTimeMillis() + 300000;
            }
            if (player.inventory.gold >= 1_000_000) {
                player.inventory.gold -= 1_000_000;
                Service.gI().sendMoney(player);
                player.goldNormar += 1_000_000;
                ChonAiDay_Gold.gI().goldNormar += 1_000_000;
                ChonAiDay_Gold.gI().addPlayerNormar(player);
                createOtherMenu(player, ConstNpc.CHON_AI_DAY_VANG, "Tổng giải thường: " + Util.numberToMoney(ChonAiDay_Gold.gI().goldNormar) + " vàng, cơ hội trúng của bạn là: " + player.percentGold(0) + "%\nTổng giải VIP: " + Util.numberToMoney(ChonAiDay_Gold.gI().goldVip) + " vàng, cơ hội trúng của bạn là: " + player.percentGold(1) + "%\n Thời gian còn lại: " + time, "Cập nhập", "Thường\n1 triệu\nvàng", "VIP\n10 triệu\nvàng", "Đóng");
            } else {
                Service.gI().sendThongBao(player, "Bạn không đủ vàng");
            }
        } catch (Exception ex) {
        }
    }

    private void xửLýVIP10TrieuVang(Player player) {
        try {
            String time = ((ChonAiDay_Gold.gI().lastTimeEnd - System.currentTimeMillis()) / 1000) + " giây";
            if (((ChonAiDay_Gold.gI().lastTimeEnd - System.currentTimeMillis()) / 1000) < 0) {
                ChonAiDay_Gold.gI().lastTimeEnd = System.currentTimeMillis() + 300000;
            }
            if (player.inventory.gold >= 10_000_000) {
                player.inventory.gold -= 10_000_000;
                Service.gI().sendMoney(player);
                player.goldVIP += 10_000_000;
                ChonAiDay_Gold.gI().goldVip += 10_000_000;
                ChonAiDay_Gold.gI().addPlayerVIP(player);
                createOtherMenu(player, ConstNpc.CHON_AI_DAY_VANG, "Tổng giải thường: " + Util.numberToMoney(ChonAiDay_Gold.gI().goldNormar) + " vàng, cơ hội trúng của bạn là: " + player.percentGold(0) + "%\nTổng giải VIP: " + Util.numberToMoney(ChonAiDay_Gold.gI().goldVip) + " vàng, cơ hội trúng của bạn là: " + player.percentGold(1) + "%\n Thời gian còn lại: " + time, "Cập nhập", "Thường\n1 triệu\nvàng", "VIP\n10 triệu\nvàng", "Đóng");
            } else {
                Service.gI().sendThongBao(player, "Bạn không đủ vàng");
            }
        } catch (Exception ex) {
        }
    }

    private void xửLýThuong10NgocXanh(Player player) {
        try {
            String time = ((ChonAiDay_Gem.gI().lastTimeEnd - System.currentTimeMillis()) / 1000) + " giây";
            if (((ChonAiDay_Gem.gI().lastTimeEnd - System.currentTimeMillis()) / 1000) < 0) {
                ChonAiDay_Gem.gI().lastTimeEnd = System.currentTimeMillis() + 300000;
            }
            if (player.inventory.gem >= 10) {
                player.inventory.gem -= 10;
                Service.gI().sendMoney(player);
                player.gemNormar += 10;
                ChonAiDay_Gem.gI().gemNormar += 10;
                ChonAiDay_Gem.gI().addPlayerNormar(player);
                createOtherMenu(player, ConstNpc.CHON_AI_DAY_NGOC, "Tổng giải thường: " + Util.numberToMoney(ChonAiDay_Gem.gI().gemNormar) + " ngọc xanh, cơ hội trúng của bạn là: " + player.percentGem(0) + "%\nTổng giải VIP: " + Util.numberToMoney(ChonAiDay_Gem.gI().gemVip) + " ngọc xanh, cơ hội trúng của bạn là: " + player.percentGem(1) + "%\n Thời gian còn lại: " + time, "Cập nhập", "Thường\n10 ngọc\nxanh", "VIP\n100 ngọc\nxanh", "Đóng");
            } else {
                Service.gI().sendThongBao(player, "Bạn không đủ ngọc xanh");
            }
        } catch (Exception ex) {
        }
    }

    private void xửLýVIP100NgocXanh(Player player) {
        try {
            String time = ((ChonAiDay_Gem.gI().lastTimeEnd - System.currentTimeMillis()) / 1000) + " giây";
            if (((ChonAiDay_Gem.gI().lastTimeEnd - System.currentTimeMillis()) / 1000) < 0) {
                ChonAiDay_Gem.gI().lastTimeEnd = System.currentTimeMillis() + 300000;
            }
            if (player.inventory.gem >= 100) {
                player.inventory.gem -= 100;
                Service.gI().sendMoney(player);
                player.gemVIP += 100;
                ChonAiDay_Gem.gI().gemVip += 100;
                ChonAiDay_Gem.gI().addPlayerVIP(player);
                createOtherMenu(player, ConstNpc.CHON_AI_DAY_NGOC, "Tổng giải thường: " + Util.numberToMoney(ChonAiDay_Gem.gI().gemNormar) + " ngọc xanh, cơ hội trúng của bạn là: " + player.percentGem(0) + "%\nTổng giải VIP: " + Util.numberToMoney(ChonAiDay_Gem.gI().gemVip) + " ngọc xanh, cơ hội trúng của bạn là: " + player.percentGem(1) + "%\n Thời gian còn lại: " + time, "Cập nhập", "Thường\n10 ngọc\nxanh", "VIP\n100 ngọc\nxanh", "Đóng");
            } else {
                Service.gI().sendThongBao(player, "Bạn không đủ ngọc xanh");
            }
        } catch (Exception ex) {
        }
    }
}