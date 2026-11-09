package nro.models.npc_list;

import nro.models.services.Event2026Service;
import nro.models.consts.ConstNpc;
import nro.models.item.Item;
import nro.models.map.phoban.BanDoKhoBau;
import nro.models.services_dungeon.TreasureUnderSeaService;
import nro.models.npc.Npc;
import static nro.models.npc.NpcFactory.PLAYERID_OBJECT;
import nro.models.player.Player;
import nro.models.services.ItemService;
import nro.models.services.InventoryService;
import nro.models.map.service.NpcService;
import nro.models.services.RewardService;
import nro.models.services.Service;
import nro.models.shop.ShopService;
import nro.models.services.TaskService;
import nro.models.map.service.ChangeMapService;
import nro.models.services_func.Input;
import nro.models.services.PlayerService;
import nro.models.skill.Skill;
import nro.models.utils.Logger;
import nro.models.utils.SkillUtil;
import nro.models.utils.TimeUtil;
import nro.models.utils.Util;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QuyLaoKame extends Npc {

    private static class RewardItem {

        int itemId; // -1: ngọc xanh ngẫu nhiên quantity..days
        int quantity;
        int days; // hạn sử dụng, 0 = vĩnh viễn

        RewardItem(int itemId, int quantity) {
            this(itemId, quantity, 0);
        }

        RewardItem(int itemId, int quantity, int days) {
            this.itemId = itemId;
            this.quantity = quantity;
            this.days = days;
        }
    }

    private static class KOLQuestData {

        int questType;
        int itemId;
        int requiredQuantity;

        List<RewardItem> rewards;
        String description;

        public KOLQuestData(int questType, int itemId, int requiredQuantity, List<RewardItem> rewards, String description) {
            this.questType = questType;
            this.itemId = itemId;
            this.requiredQuantity = requiredQuantity;
            this.rewards = rewards;
            this.description = description;
        }
    }

    /** Đợt KOL đang chạy: đổi giá trị này khi sang tháng mới để mọi người làm lại từ nhiệm vụ 1. */
    public static final String KOL_SEASON = "2026-11";
    public static final int KOL_VIP_GEM = 450; // nạp 450 ngọc xanh cho Quy Lão để mở phần thưởng VIP
    private static final int VE_KOL_VIP = 2110;

    private static final Map<Integer, KOLQuestData> KOL_QUESTS = new HashMap<>();
    private static final Map<Integer, KOLQuestData> KOL_VIP_QUESTS = new HashMap<>();

    private static void quest(int stage, int type, int itemId, int need, String desc, List<RewardItem> normal, List<RewardItem> vip) {
        KOL_QUESTS.put(stage, new KOLQuestData(type, itemId, need, normal, desc));
        KOL_VIP_QUESTS.put(stage, new KOLQuestData(type, itemId, need, vip, desc));
    }

    static {
        // KOL tháng 11 (Trứng Rồng Con = Trứng Rồng Nhí 1820, RaDa Ngọc Rồng = Máy dò Ngọc rồng sự kiện 1115, Gohan Trung Học = CT Gohan học đường 1933)
        quest(1, ConstNpc.KOL_QUEST_TYPE_BO_MONG, -1, 70, "Nhiệm vụ 1:\nHoàn thành 70 nhiệm vụ Bò Mộng (Dễ, Khó, Siêu Khó)",
                Arrays.asList(new RewardItem(1820, 5)), Arrays.asList(new RewardItem(1803, 1), new RewardItem(1820, 10)));
        quest(2, ConstNpc.KOL_QUEST_TYPE_MOC_NHAN, -1, 6000, "Nhiệm vụ 2:\nĐánh 6.000 Mộc Nhân (dùng tự động luyện tập)",
                Arrays.asList(new RewardItem(1900, 10)), Arrays.asList(new RewardItem(1900, 20)));
        quest(3, ConstNpc.KOL_QUEST_TYPE_BDKB, -1, 10, "Nhiệm vụ 3:\nHoàn thành Đảo Kho Báu cấp 20 trở lên 10 lần",
                Arrays.asList(new RewardItem(1115, 10)), Arrays.asList(new RewardItem(1115, 20), new RewardItem(-1, 100, 1000)));
        quest(4, ConstNpc.KOL_QUEST_TYPE_PVP_WINS, -1, 10, "Nhiệm vụ 4:\nĐánh bại 10 người trong Đại Hội Võ Thuật",
                Arrays.asList(new RewardItem(1503, 1)), Arrays.asList(new RewardItem(1933, 1)));
        quest(5, ConstNpc.KOL_QUEST_TYPE_NINJA_TIM, -1, 10, "Nhiệm vụ 5:\nHạ 10 Ninja tím trong Doanh Trại (tính luôn bóng)",
                Arrays.asList(new RewardItem(1592, 5), new RewardItem(1757, 5)), Arrays.asList(new RewardItem(1592, 10), new RewardItem(1757, 10)));
        quest(6, ConstNpc.KOL_QUEST_TYPE_MONSTER_KILL_COUNT, -1, 100000, "Nhiệm vụ 6:\nĐánh 100.000 quái (dùng tự động luyện tập)",
                Arrays.asList(new RewardItem(2019, 1, 60)), Arrays.asList(new RewardItem(1898, 1, 365)));
        quest(7, ConstNpc.KOL_QUEST_TYPE_ITEM_COLLECTION, 14, 7, "Nhiệm vụ 7:\nNói chuyện và tặng Quy Lão bộ ngọc rồng 1 sao (7 viên)",
                Arrays.asList(new RewardItem(1593, 1)), Arrays.asList(new RewardItem(2020, 1)));
    }

    public QuyLaoKame(int mapId, int status, int cx, int cy, int tempId, int avartar) {
        super(mapId, status, cx, cy, tempId, avartar);
    }

    @Override
    public void openBaseMenu(Player player) {
        Item ruacon = InventoryService.gI().findItemBag(player, 874);
        if (canOpenNpc(player)) {
            ArrayList<String> menu = new ArrayList<>();
            if (!player.canReward) {
                List<Integer> act = new ArrayList<>();
                menu.add("Nói\nchuyện");
                act.add(0);
                menu.add("Đổi điểm\nsự kiện\n[" + player.event.getEventPoint() + "]");
                act.add(1);
                menu.add("Nhận quà\nKOL");
                act.add(2);
                menu.add("Nhận quà\nKOL VIP");
                act.add(3);
                if (nro.models.services.SeasonEventService.gI().isActive(nro.models.database.Dot89Migration.EV_QUY_LAO)) {
                    // đua top tặng bộ ngọc rồng: sự kiện riêng, bật / tắt trên web panel
                    menu.add("Đua top\nngọc rồng\n[" + player.qlPoints + "]");
                    act.add(4);
                }
                if (nro.models.services.SeasonEventService.gI().isActive(TRUNG_THU)) {
                    // nội dung riêng của sự kiện Trung Thu
                    menu.add("Quà\nTrung Thu");
                    act.add(5);
                }
                if (ruacon != null && ruacon.quantity >= 1) {
                    menu.add("Giao\nRùa con");
                    act.add(6);
                }
                actions.put(player.id, act);
            } else {
                menu.add("Giao\n" + new String[]{"Lân con", "Tuần Lộc", "Bé Na"}[Math.max(0, Math.min(2, player.escortType))]);
            }
            String[] menus = menu.toArray(String[]::new);
            if (!TaskService.gI().checkDoneTaskTalkNpc(player, this)) {
                this.createOtherMenu(player, ConstNpc.BASE_MENU, "Con muốn hỏi gì nào?", menus);
            }
        }
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (!canOpenNpc(player)) {
            return;
        }

        if (player.canReward) {
            RewardService.gI().rewardEscort(player);
            return;
        }

        switch (player.idMark.getIndexMenu()) {
            case ConstNpc.BASE_MENU:
                handleBaseMenu(player, select);
                break;
            case 12:
                handleMenu12(player, select);
                break;
            case 13:
                // huỷ học kỹ năng: trước đây menu này không được xử lý nên bấm "Ok" không có tác dụng
                if (select == 0 && player.LearnSkill.Time != -1) {
                    player.nPoint.tiemNang += player.LearnSkill.Potential / 2;
                    player.LearnSkill.Time = -1;
                    player.LearnSkill.Potential = 0;
                    Service.gI().point(player);
                    Service.gI().sendThongBao(player, "Đã huỷ học kỹ năng và hoàn lại 50% tiềm năng");
                }
                break;
            case 0:
                handleMenu0(player, select);
                break;
            case 4:
                handleMenu4(player, select);
                break;
            case ConstNpc.MENU_OPENED_DBKB:
                handleMenuOpenedDBKB(player, select);
                break;
            case ConstNpc.MENU_OPEN_DBKB:
                handleMenuOpenDBKB(player, select);
                break;
            case ConstNpc.MENU_ACCEPT_GO_TO_BDKB:
                handleMenuAcceptGoToBDKB(player, select);
                break;
            case ConstNpc.KOL_QUEST_MENU:
                handleKOLQuestRewardConfirm(player, select, false);
                break;
            case ConstNpc.KOL_VIP_REWARD_MENU:
                handleKOLQuestRewardConfirm(player, select, true);
                break;
            case MENU_DUA_TOP:
                if (select <= 1 && !nro.models.services.SeasonEventService.gI().isActive(nro.models.database.Dot89Migration.EV_QUY_LAO)) {
                    Service.gI().sendThongBao(player, "Đua top tặng ngọc rồng đã kết thúc");
                    break;
                }
                switch (select) {
                    case 0 -> Event2026Service.gI().giveDragonBallSet(player, false);
                    case 1 -> Event2026Service.gI().giveDragonBallSet(player, true);
                    case 2 -> NpcService.gI().createTutorial(player, tempId, this.avartar, Event2026Service.gI().topText());
                }
                break;
            case MENU_QUA_TRUNG_THU:
                switch (select) {
                    case 0 -> Event2026Service.gI().exchangeTrangTron(player);
                    case 1 -> Event2026Service.gI().exchangeTrangKhuyet(player);
                }
                break;
        }
    }

    private static final int TRUNG_THU = 6; // season_event Trung Thu
    private final java.util.Map<Long, List<Integer>> actions = new java.util.concurrent.ConcurrentHashMap<>();

    private void handleBaseMenu(Player player, int select) {
        List<Integer> act = actions.get(player.id);
        if (act == null || select < 0 || select >= act.size()) {
            return;
        }
        select = act.get(select);
        switch (select) {
            case 0:
                handleTalk(player);
                break;
            case 1:
                // đổi điểm sự kiện: cửa hàng theo sự kiện đang chạy, trả bằng điểm sự kiện
                nro.models.services.SeasonEventService.gI().openEventShop(player, true);
                break;
            case 2:
                handleKOLQuest(player, false);
                break;
            case 3:
                handleKOLQuest(player, true);
                break;
            case 4:
                this.createOtherMenu(player, MENU_DUA_TOP, "Tặng ta 1 bộ ngọc rồng 1 - 7 sao để nhận 1 điểm đua top.\n"
                        + "Điểm của con: " + player.qlPoints + "\nTop 1 - 100 nhận Ván Rồng 7 Sao và nhiều phần quà, top 1 - 3 nhận thêm hào quang " + Event2026Service.TOP_AURA_DAYS + " ngày",
                        "Tặng\n1 bộ", "Tặng\ntất cả", "Xem top", "Đóng");
                break;
            case 5:
                this.createOtherMenu(player, MENU_QUA_TRUNG_THU, "Quà Trăng Tròn: " + Event2026Service.TRANG_TRON_POINT + " điểm sự kiện\n"
                        + "Quà Trăng Khuyết: " + Util.numberToMoney(Event2026Service.TRANG_KHUYET_GOLD) + " vàng",
                        "Quà\nTrăng Tròn", "Quà\nTrăng Khuyết", "Đóng");
                break;
            case 6:
                handleTradeRuacon(player);
                break;
        }
    }

    private static final int MENU_DUA_TOP = 2026_1;
    private static final int MENU_QUA_TRUNG_THU = 2026_2;

    private void handleTalk(Player player) {
        if (player.LearnSkill.Time != -1 && player.LearnSkill.Time <= System.currentTimeMillis()) {
            player.LearnSkill.Time = -1;
            try {
                var curSkill = SkillUtil.createSkill(SkillUtil.getTempSkillSkillByItemID(player.LearnSkill.ItemTemplateSkillId), SkillUtil.getSkillByItemID(player, player.LearnSkill.ItemTemplateSkillId).point);
                player.BoughtSkill.add((int) player.LearnSkill.ItemTemplateSkillId);
                SkillUtil.setSkill(player, curSkill);
                var msg = Service.gI().messageSubCommand((byte) 62);
                msg.writer().writeShort(curSkill.skillId);
                player.sendMessage(msg);
                msg.cleanup();
                PlayerService.gI().sendInfoHpMpMoney(player);
            } catch (Exception e) {
                Logger.log(e.toString());
            }
        }

        ArrayList<String> menu = new ArrayList<>();
        menu.add("Nhiệm vụ");
        menu.add("Học\nKỹ năng");
        if (player.clan != null) {
            menu.add("Về khu\nvực bang");
            menu.add("Kho báu\ndưới biển");
            if (player.clan.isLeader(player)) {
                menu.add("Giải tán\nBang hội");
            }
        }
        this.createOtherMenu(player, 0, "Chào con, ta rất vui khi gặp con\nCon muốn làm gì nào ?", menu.toArray(new String[0]));
    }

    private void handleTradeRuacon(Player player) {
        Item ruacon = InventoryService.gI().findItemBag(player, 874);
        if (ruacon != null && ruacon.quantity >= 1) {
            this.createOtherMenu(player, 1, "Cảm ơn cậu đã cứu con rùa của ta\nĐể cảm ơn ta sẽ tặng cậu món quà.", "Nhận quà", "Đóng");
        }
    }

    private void learnSkill(Player player) {
        try {
            String[] subName = ItemService.gI().getTemplate(player.LearnSkill.ItemTemplateSkillId).name.split("");
            byte level = Byte.parseByte(subName[subName.length - 1]);
            Skill curSkill = SkillUtil.createSkill(SkillUtil.getTempSkillSkillByItemID(player.LearnSkill.ItemTemplateSkillId), level);
            player.BoughtSkill.add((int) player.LearnSkill.ItemTemplateSkillId);
            SkillUtil.setSkill(player, curSkill);
            var msg = Service.gI().messageSubCommand((byte) 62);
            msg.writer().writeShort(curSkill.skillId);
            player.sendMessage(msg);
            msg.cleanup();
            PlayerService.gI().sendInfoHpMpMoney(player);
        } catch (Exception e) {
            Logger.log(e.toString());
        }
    }

    private void handleMenu12(Player player, int select) {
        switch (select) {
            case 0 -> {
                if (player.LearnSkill.Time == -1) {
                    return; // không có kỹ năng nào đang học
                }
                var time = player.LearnSkill.Time - System.currentTimeMillis();
                var ngoc = 5;
                if (time / 600_000 >= 2) {
                    ngoc += time / 600_000;
                }
                if (player.inventory.gem < ngoc) {
                    Service.gI().sendThongBao(player, "Bạn không có đủ ngọc");
                    return;
                }
                player.inventory.subGem(ngoc);
                player.LearnSkill.Time = -1;
                learnSkill(player);
            }
            case 1 ->
                createOtherMenu(player, 13, "Con có muốn huỷ học kỹ năng này và nhận lại 50% số tiềm năng không ?", "Ok", "Đóng");
        }
    }

    private void handleMenu0(Player player, int select) {
        switch (select) {
            case 0: // Nhiệm vụ
                NpcService.gI().createTutorial(player, tempId, avartar, player.playerTask.taskMain.subTasks.get(player.playerTask.taskMain.index).name);
                break;
            case 1: // Học Kỹ năng
                if (player.LearnSkill.Time != -1) {
                    handleSkillLearning(player);
                } else {
                    ShopService.gI().opendShop(player, "QUY_LAO", false);
                }
                break;
            case 2: // Về khu vực bang
                handleClanMapChange(player);
                break;
            default:
                if (player.clan != null) {
                    if (select == 3) {
                        handleTreasureMap(player);
                    } else if (player.clan.isLeader(player) && select == 4) {
                        handleClanDissolution(player);
                    }
                }
                break;
        }
    }

    private void handleSkillLearning(Player player) {
        if (player.LearnSkill.Time != -1) {
            var ngoc = 5;
            var time = player.LearnSkill.Time - System.currentTimeMillis();
            if (time / 600_000 >= 2) {
                ngoc += time / 600_000;
            }
            String[] subName = ItemService.gI().getTemplate(player.LearnSkill.ItemTemplateSkillId).name.split("");
            byte level = Byte.parseByte(subName[subName.length - 1]);
            createOtherMenu(player, 12,
                    "Con đang học kỹ năng\n" + SkillUtil.findSkillTemplate(SkillUtil.getTempSkillSkillByItemID(player.LearnSkill.ItemTemplateSkillId)).name
                    + " cấp " + level + "\nThời gian còn lại " + TimeUtil.getTime(time),
                    "Học Cấp tốc " + ngoc + " ngọc", "Huỷ", "Bỏ qua");
        } else {
            ShopService.gI().opendShop(player, "QUY_LAO", false);
        }
    }

    private void handleClanMapChange(Player player) {
        if (player.clan != null) {
            ChangeMapService.gI().changeMapNonSpaceship(player, 153, Util.nextInt(100, 200), 432);
        } else {
            Service.gI().sendThongBao(player, "Bạn cần có bang hội để thực hiện chức năng này.");
        }
    }

    private void handleClanDissolution(Player player) {
        if (player.clan != null && player.clan.isLeader(player)) {
            createOtherMenu(player, 4, "Con có chắc muốn giải tán bang hội không?", "Đồng ý", "Từ chối");
        }
    }

    private void handleTreasureMap(Player player) {
        if (player.clan != null && player.clan.BanDoKhoBau != null) {
            this.createOtherMenu(player, ConstNpc.MENU_OPENED_DBKB, "Bang hội con đang ở hang kho báu cấp " + player.clan.BanDoKhoBau.level + "\ncon có muốn đi cùng họ không?", "Top\nBang hội", "Thành tích\nBang", "Đồng ý", "Từ chối");
        } else {
            this.createOtherMenu(player, ConstNpc.MENU_OPEN_DBKB, "Đây là bản đồ kho báu hải tặc tí hon\nCác con cứ yên tâm lên đường\nỞ đây có ta lo\nNhớ chọn cấp độ vừa sức mình nhé", "Top\nBang hội", "Thành tích\nBang", "Chọn\ncấp độ", "Từ chối");
        }
    }

    private void handleMenu4(Player player, int select) {
        if (player.clan != null && player.clan.isLeader(player) && select == 0) {
            Input.gI().createFormGiaiTanBangHoi(player);
        }
    }

    private void handleMenuOpenedDBKB(Player player, int select) {
        if (select == 0) {
            Service.gI().showTopClanBDKB(player);
        } else if (select == 1) {
            Service.gI().showMyTopClanBDKB(player);
        } else if (select == 2) {
            if (player.isAdmin() || player.nPoint.power >= BanDoKhoBau.POWER_CAN_GO_TO_DBKB) {
                if (player.clan.BanDoKhoBau != null) {
                    ChangeMapService.gI().goToDBKB(player);
                } else {
                    this.npcChat(player, "Bang hội của con hiện không có hang kho báu đang hoạt động.");
                }
            } else {
                this.npcChat(player, "Yêu cầu sức mạnh lớn hơn " + Util.numberToMoney(BanDoKhoBau.POWER_CAN_GO_TO_DBKB));
            }
        }
    }

    private void handleMenuOpenDBKB(Player player, int select) {
        if (select == 0) {
            Service.gI().showTopClanBDKB(player);
        } else if (select == 1) {
            Service.gI().showTopClanBDKB(player);
        } else if (select == 2) {
            if (player.clan == null) {
                Service.gI().sendThongBao(player, "Hãy vào bang hội trước");
                return;
            }
            if (player.isAdmin() || player.nPoint.power >= BanDoKhoBau.POWER_CAN_GO_TO_DBKB) {
                Input.gI().createFormChooseLevelBDKB(player);
            } else {
                this.npcChat(player, "Yêu cầu sức mạnh lớn hơn " + Util.numberToMoney(BanDoKhoBau.POWER_CAN_GO_TO_DBKB));
            }
        }
    }

    private void handleMenuAcceptGoToBDKB(Player player, int select) {
        if (select == 0) {
            TreasureUnderSeaService.gI().openBanDoKhoBau(player, Byte.parseByte(String.valueOf(PLAYERID_OBJECT.get(player.id))));
        }
    }

    private void handleKOLQuest(Player player, boolean isVIP) {
        int currentStage;
        Map<Integer, KOLQuestData> questsMap;
        int menuType;

        if (isVIP) {
            currentStage = player.kolVIPQuestStage;
            questsMap = KOL_VIP_QUESTS;
            menuType = ConstNpc.KOL_VIP_REWARD_MENU;
        } else {
            currentStage = player.kolQuestStage;
            questsMap = KOL_QUESTS;
            menuType = ConstNpc.KOL_QUEST_MENU;
        }

        if (currentStage < 1) {
            currentStage = 1;
            if (isVIP) {
                player.kolVIPQuestStage = 1;
            } else {
                player.kolQuestStage = 1;
            }
        }

        KOLQuestData questData = questsMap.get(currentStage);

        if (questData == null) {
            this.createOtherMenu(player, menuType, "Con đã hoàn thành tất cả nhiệm vụ " + (isVIP ? "KOL VIP" : "KOL") + " rồi! Chúc mừng con!", "Đóng");
            return;
        }

        long currentProgress = progress(player, questData);

        int percent = (int) ((currentProgress * 100.0) / questData.requiredQuantity);
        if (percent > 100) {
            percent = 100;
        }

        String rewardDetails = formatRewardDetails(questData.rewards);

        String npcText = (isVIP && !player.kolVip ? "KOL VIP: nạp " + KOL_VIP_GEM + " ngọc xanh khi nhận thưởng lần đầu trong tháng\n" : "")
                + questData.description + "\nPhần thưởng: " + rewardDetails + "\nHoàn thành: "
                + currentProgress + "/" + questData.requiredQuantity + " (" + percent + "%)";

        if (currentProgress >= questData.requiredQuantity) {
            this.createOtherMenu(player, menuType, npcText, "Nhận thưởng", "Đóng");
        } else {
            this.createOtherMenu(player, menuType, npcText, "Đóng");
        }
    }

    private static long progress(Player player, KOLQuestData q) {
        return switch (q.questType) {
            case ConstNpc.KOL_QUEST_TYPE_ITEM_COLLECTION -> {
                Item it = InventoryService.gI().findItemBag(player, q.itemId);
                yield it != null ? it.quantity : 0;
            }
            case ConstNpc.KOL_QUEST_TYPE_PVP_WINS -> player.martialArtsTournamentWins;
            case ConstNpc.KOL_QUEST_TYPE_MONSTER_KILL_COUNT -> player.monsterKillCountAutoTrain;
            case ConstNpc.KOL_QUEST_TYPE_BO_MONG -> player.kolBoMong;
            case ConstNpc.KOL_QUEST_TYPE_MOC_NHAN -> player.kolMocNhan;
            case ConstNpc.KOL_QUEST_TYPE_BDKB -> player.kolBdkb;
            case ConstNpc.KOL_QUEST_TYPE_NINJA_TIM -> player.kolNinjaTim;
            default -> 0;
        };
    }

    private void handleKOLQuestRewardConfirm(Player player, int select, boolean isVIP) {
        if (select == 0) {
            int currentStage;
            Map<Integer, KOLQuestData> questsMap;

            if (isVIP) {
                if (!player.kolVip) {
                    Item vipTicket = InventoryService.gI().findItemBag(player, VE_KOL_VIP);
                    if (vipTicket != null && vipTicket.quantity > 0) {
                        InventoryService.gI().subQuantityItemsBag(player, vipTicket, 1);
                    } else if (player.inventory.gem >= KOL_VIP_GEM) {
                        player.inventory.subGem(KOL_VIP_GEM);
                        PlayerService.gI().sendInfoHpMpMoney(player);
                    } else {
                        Service.gI().sendThongBao(player, "Cần nạp " + KOL_VIP_GEM + " ngọc xanh (hoặc 1 Vé KOL VIP) để mở phần thưởng KOL VIP tháng này");
                        return;
                    }
                    player.kolVip = true;
                    Service.gI().sendThongBao(player, "Đã mở phần thưởng KOL VIP tháng này");
                }
                currentStage = player.kolVIPQuestStage;
                questsMap = KOL_VIP_QUESTS;
            } else {
                currentStage = player.kolQuestStage;
                questsMap = KOL_QUESTS;
            }

            if (currentStage < 1) {
                currentStage = 1;
                if (isVIP) {
                    player.kolVIPQuestStage = 1;
                } else {
                    player.kolQuestStage = 1;
                }
            }

            KOLQuestData questData = questsMap.get(currentStage);

            if (questData == null) {
                Service.gI().sendThongBao(player, "Không tìm thấy nhiệm vụ hiện tại.");
                return;
            }

            long currentProgress = progress(player, questData);

            if (currentProgress >= questData.requiredQuantity) {
                if (questData.questType == ConstNpc.KOL_QUEST_TYPE_ITEM_COLLECTION) {
                    Item requiredItem = InventoryService.gI().findItemBag(player, questData.itemId);
                    if (requiredItem != null) {
                        InventoryService.gI().subQuantityItemsBag(player, requiredItem, questData.requiredQuantity);
                    }
                }

                if (InventoryService.gI().getCountEmptyBag(player) < questData.rewards.size()) {
                    Service.gI().sendThongBao(player, "Hành trang cần trống ít nhất " + questData.rewards.size() + " ô");
                    return;
                }
                for (RewardItem rewardData : questData.rewards) {
                    if (rewardData.itemId < 0) {
                        int gem = Util.nextInt(rewardData.quantity, rewardData.days);
                        player.inventory.gem += gem;
                        PlayerService.gI().sendInfoHpMpMoney(player);
                        Service.gI().sendThongBao(player, "Nhận " + gem + " ngọc xanh");
                        continue;
                    }
                    Item rewardItem = ItemService.gI().createNewItem((short) rewardData.itemId);
                    rewardItem.quantity = rewardData.quantity;
                    rewardItem.itemOptions.addAll(ItemService.gI().getListOptionItemShop((short) rewardData.itemId));
                    rewardItem.itemOptions.add(new Item.ItemOption(30, 1)); // không thể giao dịch
                    if (rewardData.days > 0) {
                        rewardItem.itemOptions.add(new Item.ItemOption(93, rewardData.days));
                    }
                    InventoryService.gI().addItemBag(player, rewardItem);
                }
                InventoryService.gI().sendItemBags(player);

                Service.gI().sendThongBao(player, "Bạn đã nhận phần thưởng nhiệm vụ " + (isVIP ? "KOL VIP" : "KOL") + " cấp " + currentStage + "!");

                if (isVIP) {
                    player.kolVIPQuestStage++;
                } else {
                    player.kolQuestStage++;
                }

                openBaseMenu(player);

            } else {
                Service.gI().sendThongBao(player, "Bạn không đủ điều kiện hoàn thành nhiệm vụ!");
                openBaseMenu(player);
            }
        } else {
            openBaseMenu(player);
        }
    }

    private String formatRewardDetails(List<RewardItem> rewards) {
        StringBuilder details = new StringBuilder();
        for (int i = 0; i < rewards.size(); i++) {
            RewardItem rewardData = rewards.get(i);
            if (rewardData.itemId < 0) {
                details.append(Util.numberToMoney(rewardData.quantity)).append(" - ").append(Util.numberToMoney(rewardData.days)).append(" ngọc xanh");
            } else {
                details.append(rewardData.quantity).append(" ").append(ItemService.gI().getTemplate(rewardData.itemId).name)
                        .append(rewardData.days > 0 ? " (" + rewardData.days + " ngày)" : ItemService.gI().getTemplate(rewardData.itemId).type == 5 ? " (vĩnh viễn)" : "");
            }
            if (i < rewards.size() - 1) {
                details.append(", ");
            }
        }
        return details.toString();
    }
}
