package nro.models.Bot;

import java.util.List;
import nro.models.boss.Boss;
import nro.models.database.SuperRankDAO;
import nro.models.item.Item;
import nro.models.managers.SuperRankManager;
import nro.models.map.Zone;
import nro.models.map.service.ChangeMapService;
import nro.models.matches.dai_hoi_vo_thuat.SuperRankBuilder;
import nro.models.matches.dai_hoi_vo_thuat.SuperRankService;
import nro.models.matches.dai_hoi_vo_thuat.The23rdMartialArtCongressService;
import nro.models.matches.giai_dau.The23rdMartialArtCongressManager;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.PlayerService;
import nro.models.services.SkillService;
import nro.models.utils.Util;

/**
 * Bot đấu thật như người chơi:
 * - Giải Siêu hạng (map 113): thách đấu hạng trên theo luật như người chơi (SuperRankService.competing), đánh bản sao đối thủ, thắng thì đổi hạng.
 * - ĐHVT lần thứ 23 (map 129): trả vàng như ở NPC Ghi Danh, đánh lần lượt các đối thủ; hết trận nhận Rương gỗ, hôm sau mở lấy quà.
 */
public final class BotArena {

    static final int MODE_ARENA = 3, MAP_SIEU_HANG = 113, MAP_DHVT23 = 129, MAP_THANH_THUY = 146;
    static final String SIEU_HANG = "Giải siêu hạng", DHVT_23 = "Đại hội võ thuật lần thứ 23", MA_VUONG = "Tìm Đại Ma Vương", DHVT = "Đại hội võ thuật";
    static final int MAP_DHVT = 52;

    private BotArena() {
    }

    // ------------------------------------------------------------------ Siêu hạng
    public static boolean startSieuHang(Bot b) {
        if (b.superRank == null || b.zone == null || SuperRankManager.gI().currentlyCompeting(b.id) || SuperRankManager.gI().awaitingCompetition(b.id)) {
            return false;
        }
        synchronized (SuperRankDAO.class) { // chưa có hạng: xếp cuối bảng như người chơi mới
            if (b.superRank.rank < 1) {
                b.superRank.rank = SuperRankDAO.getRank((int) b.id);
                b.superRank.lastRewardTime = System.currentTimeMillis();
                SuperRankDAO.insertData(b);
            }
        }
        if (b.superRank.ticket <= 0 && b.inventory.gem < 1) {
            return false;
        }
        int my = b.superRank.rank;
        long rival = -1;
        List<SuperRankBuilder> list = SuperRankDAO.getPlayerListInRankRange(my, 11);
        for (SuperRankBuilder sb : list) {
            int r = sb.getRank();
            boolean ok = r < my && sb.getId() != b.id && (r >= 10 || my - r <= 2) && my - r <= 3
                    && !SuperRankManager.gI().currentlyCompeting(sb.getId()) && !SuperRankManager.gI().awaitingCompetition(sb.getId());
            if (ok && (rival < 0 || Util.isTrue(50, 100))) {
                rival = sb.getId();
            }
        }
        for (SuperRankBuilder sb : list) {
            sb.dispose();
        }
        if (rival < 0) {
            return false;
        }
        Zone z = SuperRankService.gI().getZone(MAP_SIEU_HANG);
        if (z == null) {
            return false;
        }
        if (b.zone.map.mapId != MAP_SIEU_HANG) {
            ChangeMapService.gI().goToMap(b, z);
            b.zone.load_Me_To_Another(b);
        }
        b.idMark.setMenuType(2); // thách đấu ở khu thi đấu riêng
        SuperRankService.gI().competing(b, rival);
        if (!SuperRankManager.gI().currentlyCompeting(b.id) && !SuperRankManager.gI().awaitingCompetition(b.id)) {
            return false;
        }
        begin(b, SIEU_HANG, 5 * 60_000L);
        return true;
    }

    // ------------------------------------------------------------------ ĐHVT 23
    public static boolean startDhvt23(Bot b) {
        if (b.zone == null || b.levelWoodChest >= 12 || The23rdMartialArtCongressManager.gI().plCheck(b)) {
            return false;
        }
        Item chest = InventoryService.gI().findItemBag(b, 570);
        if (chest != null) { // còn Rương gỗ hôm trước: mở lấy quà rồi mới đánh tiếp (như người chơi)
            if (!Util.isAfterMidnight(b.lastTimeRewardWoodChest)) {
                return false;
            }
            nro.models.services_func.UseItem.gI().openRuongGo(b);
            if (InventoryService.gI().findItemBag(b, 570) != null) {
                return false; // hành trang không đủ chỗ
            }
        }
        if (!InventoryService.gI().finditemWoodChest(b) || b.goldChallenge <= 0 || b.inventory.gold < b.goldChallenge) {
            return false;
        }
        b.inventory.gold -= b.goldChallenge;
        b.goldChallenge *= 2;
        b.rubyChallenge += 2;
        The23rdMartialArtCongressService.gI().startChallenge(b);
        begin(b, DHVT_23, 12 * 60_000L);
        return true;
    }

    // ------------------------------------------------------------------ nhiệm vụ cam go: Đại Ma Vương Pôcôlô
    /** Dùng Mảnh giấy có chữ 'MA' như người chơi (bấm Đồng ý): vào Siêu thánh thủy, hạ đám đệ tử rồi tới Pôcôlô. */
    public static boolean startMaVuong(Bot b) {
        if (b.zone == null || inArena(b) || (b.winSTT && !Util.isAfterMidnight(b.lastTimeWinSTT))) {
            return false;
        }
        if (b.zoneSieuThanhThuy == null && InventoryService.gI().findItemBag(b, 726) == null) {
            return false;
        }
        if (b.winSTT) {
            b.winSTT = false;
            b.callBossPocolo = false;
            b.zoneSieuThanhThuy = null;
        }
        nro.models.services_dungeon.SuperDivineWaterService.gI().joinMapThanhThuy(b);
        if (b.zone == null || b.zone.map.mapId != MAP_THANH_THUY) {
            return false;
        }
        begin(b, MA_VUONG, 15 * 60_000L);
        return true;
    }

    // ------------------------------------------------------------------ đại hội võ thuật (Nhi đồng, Siêu cấp 1 - 3, Ngoại hạng)
    /** Tới Đại hội võ thuật, đăng ký giải đang mở như người chơi (Trọng tài), chờ ghép cặp rồi đấu với người chơi / bot khác. */
    public static boolean startDhvt(Bot b) {
        if (b.zone == null || inArena(b) || !nro.models.matches.giai_dau.WorldMartialArtsTournamentManager.gI().canReg) {
            return false;
        }
        if (b.zone.map.mapId != MAP_DHVT) {
            Zone z = b.getRandomZone(MAP_DHVT);
            if (z == null) {
                return false;
            }
            ChangeMapService.gI().goToMap(b, z);
            b.zone.load_Me_To_Another(b);
        }
        if (!nro.models.matches.dai_hoi_vo_thuat.WorldMartialArtsTournamentService.botRegister(b)) {
            return false;
        }
        begin(b, DHVT, 40 * 60_000L);
        return true;
    }

    private static void updateDhvt(Bot b, long now) {
        nro.models.matches.giai_dau.WorldMartialArtsTournamentManager m = nro.models.matches.giai_dau.WorldMartialArtsTournamentManager.gI();
        nro.models.matches.dai_hoi_vo_thuat.WorldMartialArtsTournament match = null;
        try {
            for (nro.models.matches.dai_hoi_vo_thuat.WorldMartialArtsTournament w : new java.util.ArrayList<>(m.listTournaments)) {
                if (w.player_1 == b || w.player_2 == b) {
                    match = w;
                }
            }
        } catch (Exception e) { // danh sách trận đổi giữa chừng: nhịp sau xem lại
            return;
        }
        if (match != null) { // đang trên sàn: trọng tài đếm xong thì đánh đối thủ
            Player opp = match.player_1 == b ? match.player_2 : match.player_1;
            if (match.timeUp >= 23 && opp != null && !opp.isDie() && opp.zone == b.zone && !b.isDie()) {
                attack(b, opp);
            }
            return;
        }
        if (m.listReg.contains(b.id) || m.listWait.contains(b.id)) { // chờ vòng sau: đứng ở Đại hội võ thuật (vắng mặt là bị loại)
            if (b.zone.map.mapId != MAP_DHVT) {
                Zone z = b.getRandomZone(MAP_DHVT);
                if (z != null) {
                    ChangeMapService.gI().goToMap(b, z);
                    b.zone.load_Me_To_Another(b);
                }
            }
            if (now <= b.arenaUntil) {
                return;
            }
        }
        end(b); // thua / vô địch / hết giải
    }

    private static void attack(Bot b, Player target) {
        nro.models.skill.Skill pick = b.pickSkill(2);
        if (pick != null) {
            b.playerSkill.skillSelect = pick;
        }
        if (b.UseLastTimeSkill()) {
            PlayerService.gI().playerMove(b, target.location.x, target.location.y);
            SkillService.gI().useSkill(b, target, null, 0, null);
        }
    }

    /** Hết trận ĐHVT 23: nhận Rương gỗ cấp đã đạt (như bấm nhận thưởng ở NPC Ghi Danh). */
    private static void claimWoodChest(Bot b) {
        if (b.levelWoodChest <= 0 || !InventoryService.gI().finditemWoodChest(b) || InventoryService.gI().getCountEmptyBag(b) <= 0) {
            return;
        }
        Item it = ItemService.gI().createNewItem((short) 570);
        it.itemOptions.add(new Item.ItemOption(72, b.levelWoodChest));
        it.itemOptions.add(new Item.ItemOption(30, 0));
        it.createTime = System.currentTimeMillis();
        InventoryService.gI().addItemBag(b, it);
        b.levelWoodChest = 0;
        b.lastTimeRewardWoodChest = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------ đánh trận
    private static void begin(Bot b, String what, long maxMs) {
        b.activity = what;
        b.arenaUntil = System.currentTimeMillis() + maxMs;
        b.arenaIdle = 0;
        b.setMode(MODE_ARENA);
    }

    static boolean inArena(Bot b) {
        return b.getMode() == MODE_ARENA;
    }

    /** Mỗi nhịp của bot đang thi đấu: đánh đối thủ (boss trong khu), hết trận thì về luyện tập. */
    static void update(Bot b) {
        if (b.zone == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (DHVT.equals(b.activity)) {
            updateDhvt(b, now);
            return;
        }
        int map = b.zone.map.mapId;
        boolean arenaMap = map == MAP_SIEU_HANG || map == MAP_DHVT23 || map == MAP_THANH_THUY;
        boolean matchOn = MA_VUONG.equals(b.activity) ? map == MAP_THANH_THUY && !b.winSTT
                : SIEU_HANG.equals(b.activity)
                ? SuperRankManager.gI().currentlyCompeting(b.id) || SuperRankManager.gI().awaitingCompetition(b.id)
                : The23rdMartialArtCongressManager.gI().plCheck(b);
        Player target = null;
        if (map == MAP_THANH_THUY && !b.isDie()) { // đám đệ tử Ma Vương trước, hết quái mới tới Pôcôlô
            for (nro.models.mob.Mob mob : b.zone.mobs) {
                if (!mob.isDie()) {
                    b.arenaIdle = 0;
                    nro.models.skill.Skill pick = b.pickSkill(2);
                    if (pick != null) {
                        b.playerSkill.skillSelect = pick;
                    }
                    if (b.UseLastTimeSkill()) {
                        PlayerService.gI().playerMove(b, mob.location.x, mob.location.y);
                        SkillService.gI().useSkill(b, null, mob, 0, null);
                    }
                    return;
                }
            }
        }
        if (arenaMap) {
            for (Player p : b.zone.getBossesCopy()) {
                if (p instanceof Boss && !p.isDie() && p.nPoint != null) {
                    target = p;
                    break;
                }
            }
        }
        if (target != null && !b.isDie() && !(map == MAP_THANH_THUY && b.winSTT)) {
            b.arenaIdle = 0;
            nro.models.skill.Skill pick = b.pickSkill(2);
            if (pick != null) {
                b.playerSkill.skillSelect = pick;
            }
            if (b.UseLastTimeSkill()) {
                PlayerService.gI().playerMove(b, target.location.x, target.location.y);
                SkillService.gI().useSkill(b, target, null, 0, null);
            }
            return;
        }
        if (b.arenaIdle == 0) {
            b.arenaIdle = now;
        }
        // chờ đối thủ ra sân (giữa các vòng ĐHVT) tối đa 20 giây; hết trận / hết giờ / bị đưa ra khỏi sân thì về
        if (!arenaMap || now > b.arenaUntil || (!matchOn && now - b.arenaIdle > 3_000) || now - b.arenaIdle > 20_000) {
            end(b);
        }
    }

    private static void end(Bot b) {
        if (DHVT_23.equals(b.activity)) {
            claimWoodChest(b);
        }
        if (b.isDie()) {
            nro.models.services.Service.gI().hsChar(b, b.nPoint.hpMax, b.nPoint.mpMax);
        }
        b.setMode(0);
        b.activity = "Luyện tập";
        b.joinMap();
    }
}
