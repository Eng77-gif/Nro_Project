package nro.models.Bot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nro.models.item.Item;
import nro.models.map.Zone;
import nro.models.map.service.ChangeMapService;
import nro.models.map.service.MapService;
import nro.models.mob.Mob;
import nro.models.npc.Npc;
import nro.models.server.Manager;
import nro.models.services.InventoryService;
import nro.models.services.PlayerService;
import nro.models.services.Service;
import nro.models.services.TaskService;
import nro.models.skill.Skill;
import nro.models.task.SubTaskMain;
import nro.models.task.TaskMain;
import nro.models.utils.SkillUtil;
import nro.models.utils.Util;

/**
 * Tài khoản bot tự lên như người chơi: làm nhiệm vụ chính từ đầu (mộc nhân...), úp set kích hoạt ở map úp SKH rồi mặc vào, học
 * chiêu cấp cao hơn khi đủ sức mạnh (trả ngọc như mua sách).
 * <p>
 * Nhiệm vụ: bước có NPC thì tới map đó nói chuyện / mở rương / hái đậu; bước đánh quái thì tìm map có quái trùng tên trong bước;
 * bước tới map thì tới map đó. Bước khác (đạt sức mạnh, vào bang...) bot cứ luyện tập như thường, hệ thống tự ghi.
 */
public class BotQuest {

    private static final BotQuest I = new BotQuest();

    public static BotQuest gI() {
        return I;
    }

    private static final int NPC_RUONG = 3, NPC_DAU_THAN = 4;
    private final Map<Long, Long> nextStep = new HashMap<>();
    private final Map<Long, long[]> stuck = new HashMap<>(); // bot id -> {mã bước (task * 100 + index), từ lúc nào}

    public void tick(List<Bot> accountBots) {
        long now = System.currentTimeMillis();
        for (Bot b : accountBots) {
            if (b.zone == null || b.isDie() || BotClan.inDungeon(b) || b.pkTarget != null || "Doanh trại".equals(b.activity)) {
                continue;
            }
            if (now < nextStep.getOrDefault(b.id, 0L)) {
                continue;
            }
            nextStep.put(b.id, now + 15_000);
            try {
                if (!b.itemTime.isOpenPower && b.nPoint.canOpenPower()) {
                    nro.models.services.OpenPowerService.gI().openPowerBasic(b); // mở giới hạn sức mạnh miễn phí (chờ như người chơi)
                }
                learnSkills(b);
                openIntrinsic(b);
                wearSkh(b);
                if ("Luyện tập".equals(b.activity) || "Làm nhiệm vụ".equals(b.activity)) {
                    quest(b);
                }
            } catch (Exception e) {
                BotWorld.gI().logError(b, e);
            }
        }
    }

    // ------------------------------------------------------------------ nhiệm vụ
    private void quest(Bot b) {
        b.questMap = -1;
        b.questMob = -1;
        TaskService.gI().checkDoneTaskPower(b, b.nPoint.power); // bước "Đạt ... sức mạnh" chỉ được ghi khi sức mạnh tăng: chạm giới hạn thì kẹt
        TaskMain tm = b.playerTask == null ? null : b.playerTask.taskMain;
        if (tm == null || tm.subTasks == null || tm.index < 0 || tm.index >= tm.subTasks.size()) {
            return;
        }
        SubTaskMain st = tm.subTasks.get(tm.index);
        long now = System.currentTimeMillis(), code = tm.id * 100L + tm.index;
        long[] sk = stuck.computeIfAbsent(b.id, k -> new long[]{code, now});
        if (sk[0] != code) {
            sk[0] = code;
            sk[1] = now;
        }
        long age = now - sk[1];
        if (age > 30 * 60_000L && age % (60 * 60_000L) > 30 * 60_000L) {
            b.activity = "Luyện tập"; // kẹt bước này quá 30 phút: chơi bình thường 30 phút rồi thử lại
            return;
        }
        int map = st.mapId;
        if (tm.id == 0 && tm.index == 0) {
            map = 39 + b.gender; // "Di chuyển tới mũi tên chỉ dẫn": Vách núi của hành tinh mình, đi sang phải
        } else if (tm.id == 3 && tm.index == 1) {
            map = 42 + b.gender; // "Đi khám phá vật thể lạ": em bé nằm ở Vách núi 42 - 44 (Map.initItem)
        }
        if (st.npcId >= 0) { // gặp NPC (nói chuyện, mở rương, hái đậu, báo cáo)
            int npcId = st.npcId;
            if (map < 0) {
                map = npcMap(b, npcId);
            }
            if (npcId != NPC_RUONG && (map < 0 || !hasNpc(map, npcId))) {
                // dữ liệu bước ghi NPC không có ở map đó (vd "Đi về nhà ông Gôhan": Tiên Bry ở Vách núi): về nhà gặp ông nội
                map = 21 + b.gender;
                npcId = new int[]{nro.models.consts.ConstNpc.ONG_GOHAN, nro.models.consts.ConstNpc.ONG_MOORI, nro.models.consts.ConstNpc.ONG_PARAGUS}[Math.max(0, Math.min(2, b.gender))];
            }
            b.activity = "Làm nhiệm vụ";
            if (b.zone.map.mapId != map && !goTo(b, map)) {
                return;
            }
            interact(b, npcId);
            return;
        }
        int mobId = tm.id == 8 && tm.index == 1 ? new int[]{11, 12, 10}[Math.max(0, Math.min(2, b.gender))] // ngọc 7 sao rơi từ quái mẹ (Mob: TASK_8_1)
                : mobId(st.name);
        int mobMap = mobId < 0 ? -1 : mobMap(b, mobId);
        if (mobMap >= 0) { // đánh quái: luyện ở map có loại quái đó, ưu tiên đánh đúng loại
            b.activity = "Làm nhiệm vụ";
            b.questMap = mobMap;
            b.questMob = mobId;
            if (b.zone.map.mapId != mobMap) {
                goTo(b, mobMap);
            }
            return;
        }
        if (map >= 0) { // tới map (hệ thống tự ghi khi vào map)
            b.activity = "Làm nhiệm vụ";
            b.questMap = map; // ở lại map nhiệm vụ (đánh quái rơi đồ nhiệm vụ, nhặt vật phẩm...)
            if (b.zone.map.mapId != map) {
                goTo(b, map);
            }
            if (tm.id == 0 && tm.index == 0) {
                PlayerService.gI().playerMove(b, 700, b.zone.map.yPhysicInTop(700, 100)); // mũi tên chỉ dẫn ở bên phải
                TaskService.gI().checkDoneTaskGoToMap(b, b.zone);
            } else {
                for (nro.models.map.ItemMap it : new java.util.ArrayList<>(b.zone.items)) {
                    if (it.playerId == -1 || it.playerId == b.id) { // vật phẩm nhiệm vụ (em bé, ngọc rồng...) bot nhặt được: đi tới nhặt
                        PlayerService.gI().playerMove(b, it.x, it.y);
                        break;
                    }
                }
            }
            return;
        }
        b.activity = "Luyện tập"; // đạt sức mạnh, dùng tiềm năng, vào bang...: cứ chơi bình thường
    }

    private boolean goTo(Bot b, int mapId) {
        Zone z = b.getRandomZone(mapId);
        if (z == null) {
            return false;
        }
        ChangeMapService.gI().goToMap(b, z);
        b.zone.load_Me_To_Another(b);
        b.mo1.lastTimeChanM = System.currentTimeMillis();
        return true;
    }

    private void interact(Bot b, int npcId) {
        if (npcId == NPC_RUONG) {
            TaskService.gI().checkDoneTaskGetItemBox(b);
            return;
        }
        for (Npc npc : b.zone.map.npcs) {
            if (npc.tempId == npcId) {
                PlayerService.gI().playerMove(b, npc.cx, npc.cy); // đứng cạnh NPC như người chơi bấm vào
                if (npcId == NPC_DAU_THAN) {
                    TaskService.gI().botHarvest(b);
                } else {
                    TaskService.gI().checkDoneTaskTalkNpc(b, npc);
                }
                return;
            }
        }
    }

    /** Map người chơi đi tới được bằng cổng (Tây Karin... không có cổng nào thì bỏ qua). */
    private static boolean reachable(nro.models.map.Map m) {
        return m != null && m.tileMap != null && !m.zones.isEmpty() && m.wayPoints != null && !m.wayPoints.isEmpty()
                && !MapService.gI().isMapPhoBan(m.mapId); // phó bản bang (doanh trại, kho báu, rắn độc, khí gas) không vào làm nhiệm vụ
    }

    private static boolean hasNpc(int mapId, int npcId) {
        nro.models.map.Map m = MapService.gI().getMapById(mapId);
        if (m == null || m.npcs == null) {
            return false;
        }
        for (Npc n : m.npcs) {
            if (n.tempId == npcId) {
                return true;
            }
        }
        return false;
    }

    /** Map có NPC (ưu tiên hành tinh của bot), -1 nếu không thấy. */
    private int npcMap(Bot b, int npcId) {
        int best = -1;
        for (Npc npc : Manager.NPCS) {
            if (npc.tempId == npcId) {
                nro.models.map.Map m = MapService.gI().getMapById(npc.mapId);
                if (reachable(m) && (best < 0 || m.planetId == b.gender)) {
                    best = npc.mapId;
                }
            }
        }
        return best;
    }

    /** Loại quái có tên dài nhất nằm trong tên bước nhiệm vụ ("Đánh 3 con khủng long mẹ" -> khủng long mẹ), -1 nếu không có. */
    private static int mobId(String step) {
        if (step == null) {
            return -1;
        }
        String s = step.toLowerCase();
        int mobId = -1, len = 0;
        for (var t : Manager.MOB_TEMPLATES) {
            if (t.name != null && t.name.length() > len && s.contains(t.name.toLowerCase())) {
                mobId = t.id;
                len = t.name.length();
            }
        }
        return mobId;
    }

    /** Map có loại quái đó yếu nhất (máu thấp nhất; cùng máu thì ưu tiên hành tinh của bot), như người chơi chọn chỗ dễ đánh. */
    private int mobMap(Bot b, int mobId) {
        int best = -1;
        long bestScore = Long.MAX_VALUE;
        for (nro.models.map.Map m : Manager.MAPS) {
            if (!reachable(m) || MapService.gI().isMapBlackBallWar(m.mapId)) {
                continue;
            }
            for (Mob mob : m.zones.get(0).mobs) {
                if (mob.tempId == mobId) {
                    long score = mob.point.maxHp * 2L + (m.planetId == b.gender ? 0 : 1);
                    if (score < bestScore) {
                        bestScore = score;
                        best = m.mapId;
                    }
                    break;
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ úp SKH
    /** Mặc đồ kích hoạt nhặt được khi ô đó đang trống hoặc đang mặc đồ không kích hoạt. */
    private void wearSkh(Bot b) {
        for (int i = 0; i < b.inventory.itemsBag.size(); i++) {
            Item it = b.inventory.itemsBag.get(i);
            if (it == null || !it.isNotNullItem() || it.template.type > 4 || (it.template.gender != b.gender && it.template.gender != 3)
                    || !isSkh(it) || it.template.strRequire > b.nPoint.power) {
                continue;
            }
            int slot = it.template.type;
            Item cur = slot < b.inventory.itemsBody.size() ? b.inventory.itemsBody.get(slot) : null;
            if (cur == null || !cur.isNotNullItem() || !isSkh(cur)) {
                InventoryService.gI().itemBagToBody(b, i);
                Service.gI().chat(b, "vừa úp được " + it.template.name + " kích hoạt");
                return;
            }
        }
    }

    /** Đồ có dòng set kích hoạt (option 127 - 144, 233 - 248 như ItemService.randOptionItemKichHoat). */
    static boolean isSkh(Item it) {
        for (Item.ItemOption o : it.itemOptions) {
            int id = o.optionTemplate.id;
            if ((id >= 127 && id <= 144) || (id >= 233 && id <= 248)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ nội tại
    /** Từ 10 tỷ sức mạnh: mở nội tại bằng vàng (như người chơi) tới khi được nội tại tăng sát thương cho 1 chiêu đánh bot đã học. */
    private void openIntrinsic(Bot b) {
        if (b.nPoint.power < 10_000_000_000L || b.playerIntrinsic == null || fitsIntrinsic(b)) {
            return;
        }
        long cost = 10_000_000L << Math.min(7, b.playerIntrinsic.countOpen); // IntrinsicService.COST_OPEN (triệu vàng)
        if (b.inventory.gold >= cost + 50_000_000L) { // chừa vàng dự trữ
            nro.models.services.IntrinsicService.gI().open(b);
        }
    }

    private static boolean fitsIntrinsic(Bot b) {
        nro.models.intrinsic.Intrinsic in = b.playerIntrinsic.intrinsic;
        if (in == null || in.id == 0 || in.name == null || !in.name.contains("sát thương")) {
            return false;
        }
        for (Skill s : b.playerSkill.skills) {
            if (s != null && s.point > 0 && s.template != null && s.template.name != null && in.name.startsWith(s.template.name)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ nâng chiêu
    /**
     * Học chiêu mới (cấp 1) hoặc cấp tiếp theo của chiêu đã có khi đủ sức mạnh. Ngọc chỉ kiếm được qua nhiệm vụ hằng ngày nên bot trả bằng
     * tiềm năng: 1/10 sức mạnh yêu cầu của cấp đó.
     */
    private void learnSkills(Bot b) {
        for (Skill cur : b.playerSkill.skills) {
            if (cur == null || cur.template == null || cur.point < 0 || cur.point >= 7) { // point 0: chiêu của phái chưa học
                continue;
            }
            if (cur.point == 0 && cur.template.id >= 19) {
                continue; // khiên, dịch chuyển, thôi miên, trói, huýt sáo, siêu chiêu: người chơi phải có sách đặc biệt, bot cũng vậy
            }
            Skill next = SkillUtil.createSkill(cur.template.id, cur.point + 1);
            long cost = Math.max(1, next == null ? 0 : next.powRequire / 10);
            if (next == null || next.powRequire > b.nPoint.power || b.nPoint.tiemNang < cost) {
                continue;
            }
            b.nPoint.tiemNang -= cost;
            SkillUtil.setSkill(b, next);
            if (Util.isTrue(30, 100)) {
                Service.gI().chat(b, "lên " + next.template.name + " cấp " + next.point + " rồi");
            }
            return; // mỗi lượt học 1 cấp như người chơi mua từng quyển
        }
    }
}
