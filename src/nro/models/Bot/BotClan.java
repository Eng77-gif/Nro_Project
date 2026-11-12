package nro.models.Bot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import nro.models.clan.Clan;
import nro.models.clan.ClanMember;
import nro.models.consts.ConstTask;
import nro.models.map.Zone;
import nro.models.map.service.ChangeMapService;
import nro.models.map.service.MapService;
import nro.models.player.Player;
import nro.models.server.Manager;
import nro.models.services.ClanService;
import nro.models.services.Service;
import nro.models.services.TaskService;
import nro.models.services_dungeon.RedRibbonHQService;
import nro.models.utils.Util;

/**
 * Tài khoản bot dùng bang hội như người chơi: xin vào bang (bang của người chơi thật thì bang chủ duyệt), nhận lời mời, tự tạo
 * bang khi không ai nhận, bot làm bang chủ thì tự duyệt đơn xin vào. Bang đủ điều kiện thì bang chủ bot gom bot cùng bang tới Lính
 * canh (Rừng Bamboo) và mở doanh trại; bang mở doanh trại (kể cả do người chơi thật mở) thì bot cùng bang vào đánh cùng.
 */
public class BotClan {

    private static final int MAP_LINH_CANH = 27;
    /** Tên bang kiểu Ngọc Rồng: tên chính + (đôi khi) đuôi. */
    private static final String[] CLAN_NAME = {"Rồng Thần", "Shenron", "Porunga", "Siêu Rồng Thần", "Ngọc Rồng Sao Đen", "7 Viên Ngọc Rồng",
        "Kame House", "Capsule Corp", "Red Ribbon", "Hành Tinh Vegeta", "Namếc", "Xayda", "Siêu Xayda", "Saiyan Elite", "Hoàng Tử Xayda",
        "Chiến Binh Z", "Z Fighters", "Tiểu Đội Ginyu", "Quân Đoàn Frieza", "Đội Hỏa Tiễn", "Thần Điện", "Tháp Karin", "Đậu Thần",
        "Đại Hội Võ Thuật", "Võ Đài Xên", "Kamejoko", "Kaioken", "Genki Dama", "Galick Ho", "Final Flash", "Makankosappo",
        "Bản Năng Vô Cực", "Ultra Instinct", "Fusion Gogeta", "Vegito", "Thiên Sứ Whis", "Thần Hủy Diệt", "Vũ Trụ 7", "Vũ Trụ 11",
        "Quy Lão Kame", "Đệ Tử Kaio", "Gohan Beast", "Super Broly", "Black Goku", "Hắc Mabư", "Cell Hoàn Thiện", "Cooler",
        "Rồng Xương", "Ngọc Rồng Namek", "Thánh Địa Kaio"};
    private static final String[] CLAN_SUFFIX = {"VN", "Team", "Clan", "Z", "Super", "Huyền Thoại", "Pro", "Vô Đối", "SSJ"};

    private final Random rnd = new Random();
    private long lastStep;
    private final Map<Long, Long> lastAsk = new HashMap<>(); // bot id -> lần xin vào bang gần nhất
    private final Map<Long, Integer> askCount = new HashMap<>();
    private final Map<Integer, Long> gatherAt = new HashMap<>(); // clan id -> lúc gom quân ở Lính canh
    private final Map<Long, Long> lastTryDt = new HashMap<>();

    public void tick(List<Bot> accountBots) {
        long now = System.currentTimeMillis();
        for (Bot b : accountBots) {
            if (b.zone == null || b.awayUntil > now || b.isDie() || nro.models.services_func.TransactionService.gI().check(b)) {
                continue;
            }
            try {
                doanhTrai(b, now);
            } catch (Exception e) {
                BotWorld.gI().logError(b, e);
            }
        }
        if (now - lastStep < 60_000) {
            return;
        }
        lastStep = now;
        for (Bot b : accountBots) {
            if (b.zone == null || b.awayUntil > now || inDungeon(b)) {
                continue;
            }
            try {
                clanStep(b, now);
            } catch (Exception e) {
                BotWorld.gI().logError(b, e);
            }
        }
    }

    static boolean inDungeon(Player b) {
        return b.zone != null && (MapService.gI().isMapDoanhTrai(b.zone.map.mapId) || MapService.gI().isMapBlackBallWar(b.zone.map.mapId));
    }

    // ------------------------------------------------------------------ bang hội
    private void clanStep(Bot b, long now) {
        if (b.clan == null) {
            if (TaskService.gI().getIdTask(b) < ConstTask.TASK_10_0) {
                return; // như người chơi: chưa tới nhiệm vụ vào bang
            }
            if (now - lastAsk.getOrDefault(b.id, 0L) < 5 * 60_000L) {
                return; // đã xin, chờ bang chủ duyệt
            }
            Clan want = pickClan(b);
            if (want != null) { // ưu tiên xin vào bang có sẵn, cứ xin tiếp bang khác đến khi được nhận
                ClanService.gI().botAskJoin(b, want.id);
                lastAsk.put(b.id, now);
                return;
            }
            int asked = askCount.getOrDefault(b.id, 0);
            if (asked >= 2 && Util.isTrue(30, 100) && (b.nPoint.power >= ClanService.BOT_CLAN_MIN_POWER || Manager.CLANS.isEmpty())) { // không còn bang nào còn chỗ (đã chờ vài lượt): mới tự lập bang
                createClan(b);
                asked = 0;
            }
            askCount.put(b.id, asked + 1);
            lastAsk.put(b.id, now);
            return;
        }
        askCount.remove(b.id);
        if (b.clan.isLeader(b)) {
            ClanService.gI().botHandleAsks(b);
        }
    }

    /** Bang còn chỗ: ưu tiên bang có bot làm bang chủ (duyệt ngay), cũng xin cả bang của người chơi thật. */
    private Clan pickClan(Bot b) {
        List<Clan> botLed = new ArrayList<>();
        List<Clan> others = new ArrayList<>();
        for (Clan c : Manager.CLANS) {
            if (c == null || c.members.size() >= c.maxMember) {
                continue;
            }
            ClanMember lead = c.getLeader();
            if (lead != null && BotWorld.gI().find(lead.id) != null) {
                if (b.nPoint.power >= ClanService.BOT_CLAN_MIN_POWER) {
                    botLed.add(c); // chưa đủ sức mạnh thì bang chủ bot sẽ từ chối, khỏi xin
                }
            } else if (lead != null && lead.id > 0 && nro.models.server.Client.gI().getPlayer(lead.id) != null) {
                others.add(c); // bang của người chơi thật: chỉ xin khi bang chủ đang online (có người duyệt)
            }
        }
        if (!others.isEmpty() && Util.isTrue(30, 100)) {
            return others.get(rnd.nextInt(others.size()));
        }
        if (!botLed.isEmpty()) {
            return botLed.get(rnd.nextInt(botLed.size()));
        }
        return others.isEmpty() ? null : others.get(rnd.nextInt(others.size()));
    }

    private void createClan(Bot b) {
        List<nro.models.player_system.Template.FlagBag> flags = new ArrayList<>();
        for (nro.models.player_system.Template.FlagBag f : nro.models.services.FlagBagService.gI().getFlagsForChooseClan()) {
            if (f != null && f.gem <= 0 && f.gold <= b.inventory.gold) {
                flags.add(f);
            }
        }
        if (flags.isEmpty()) {
            return;
        }
        String name = null;
        for (int t = 0; t < 10 && name == null; t++) {
            String n = CLAN_NAME[rnd.nextInt(CLAN_NAME.length)] + (rnd.nextInt(3) == 0 ? " " + CLAN_SUFFIX[rnd.nextInt(CLAN_SUFFIX.length)] : "");
            boolean used = false;
            for (Clan c : Manager.CLANS) {
                if (c != null && n.equalsIgnoreCase(c.name)) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                name = n;
            }
        }
        if (name != null) {
            ClanService.gI().botCreateClan(b, flags.get(rnd.nextInt(flags.size())).id, name);
            if (b.clan != null) {
                Service.gI().chat(b, "Lập bang " + name + " rồi, ae vào chơi cùng nhé");
            }
        }
    }

    /** Có người mời vào bang: bot chưa có bang thì đồng ý (như người chơi). */
    public void onInvite(Player bot, Clan clan) {
        if (bot.clan == null && Util.isTrue(80, 100)) {
            ClanService.gI().botAcceptInvite(bot, clan.id);
        }
    }

    // ------------------------------------------------------------------ doanh trại
    private void doanhTrai(Bot b, long now) {
        Clan c = b.clan;
        if (c == null || b.clanMember == null) {
            return;
        }
        boolean inDt = MapService.gI().isMapDoanhTrai(b.zone.map.mapId);
        if (inDt) {
            b.activity = "Doanh trại";
            return; // BotPemQuai lo đánh trong doanh trại
        }
        if ("Doanh trại".equals(b.activity)) {
            b.activity = "Luyện tập"; // hết giờ / bị đưa về nhà
        }
        if (MapService.gI().isMapBlackBallWar(b.zone.map.mapId) || b.clanMember.getNumDateFromJoinTimeToToday() < 1) {
            return;
        }
        // bang đang đánh doanh trại (người chơi thật hay bot mở): vào cùng
        if (c.doanhTrai != null) {
            if (now - lastTryDt.getOrDefault(b.id, 0L) > 60_000 && Util.isAfterMidnight(b.lastTimeJoinDT)) {
                lastTryDt.put(b.id, now);
                RedRibbonHQService.gI().joinDoanhTrai(b);
                if (b.zone != null && MapService.gI().isMapDoanhTrai(b.zone.map.mapId)) {
                    b.setMode(0);
                    b.activity = "Doanh trại";
                    Service.gI().chat(b, "vào doanh trại với bang đây");
                }
            }
            return;
        }
        if (!c.isLeader(b) || c.members.size() < nro.models.map.phoban.RedRibbonHQ.N_PLAYER_CLAN) {
            return;
        }
        if (c.lastTimeOpenDoanhTrai > 0 && !Util.isAfterMidnight(c.lastTimeOpenDoanhTrai)) {
            return; // hôm nay bang đã vào
        }
        Long g = gatherAt.get(c.id);
        if (g == null) {
            if (now - lastTryDt.getOrDefault(b.id, 0L) < 20 * 60_000L || !Util.isTrue(25, 100)) {
                return;
            }
            lastTryDt.put(b.id, now);
            // gom bot cùng bang tới Lính canh
            List<Bot> team = new ArrayList<>();
            for (Bot o : BotWorld.gI().bots()) {
                if (o != b && o.accountBot && o.clan == c && o.zone != null && !o.isDie() && !inDungeon(o)
                        && o.clanMember != null && o.clanMember.getNumDateFromJoinTimeToToday() >= 1) {
                    team.add(o);
                }
            }
            if (team.isEmpty()) {
                return;
            }
            Zone z = MapService.gI().getMapById(MAP_LINH_CANH).zones.get(0);
            moveTo(b, z, 1452);
            for (Bot o : team) {
                moveTo(o, z, Util.nextInt(1320, 1600));
                o.setMode(0);
                o.activity = "Doanh trại";
            }
            b.activity = "Doanh trại";
            Service.gI().chat(b, "ae tập trung đi doanh trại nào");
            gatherAt.put(c.id, now);
            return;
        }
        if (now - g < 8_000) {
            return;
        }
        gatherAt.remove(c.id);
        if (b.zone.map.mapId == MAP_LINH_CANH) {
            RedRibbonHQService.gI().joinDoanhTrai(b); // đưa luôn thành viên đứng gần vào như người chơi
        }
    }

    private void moveTo(Bot b, Zone z, int x) {
        if (b.zone != z) {
            ChangeMapService.gI().goToMap(b, z);
            b.zone.load_Me_To_Another(b);
        }
        nro.models.services.PlayerService.gI().playerMove(b, x, z.map.yPhysicInTop(x, 100));
        b.mo1.lastTimeChanM = System.currentTimeMillis(); // không tự đổi map ngay
    }
}
