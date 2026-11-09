package nro.models.Bot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import nro.models.data.LocalManager;
import nro.models.map.service.ChangeMapService;
import nro.models.player.Player;
import nro.models.server.Manager;
import nro.models.services.ItemService;
import nro.models.utils.Logger;
import nro.models.utils.Util;

/**
 * Bot cho đỡ trống server: luôn giữ đủ số bot (đặt trên web panel / menu admin), mỗi bot có tên, hành tinh, hình như người
 * chơi thật, lưu tiến độ (bảng bot_profile) nên sức mạnh tăng dần qua các lần khởi động.
 * Mỗi vài phút bot đổi hoạt động: luyện tập (đánh quái map hợp sức mạnh, nhặt đồ), săn boss, săn đệ (ưu tiên Broly),
 * và cộng điểm đua top sự kiện đang chạy. Bot có id từ ID_BASE trở lên: hiện trong Top 100 nhưng KHÔNG chiếm quà đua top
 * của người chơi thật (chốt top bỏ qua bot).
 *
 * @author Việt
 */
public final class BotWorld {

    public static final long ID_BASE = 1_900_000_000L;
    private static final int MAX_BOTS = 200;
    private static final BotWorld I = new BotWorld();
    private final Random rnd = new Random();
    private final Map<Long, long[]> nextSwitch = new HashMap<>(); // id -> {lúc đổi hoạt động}
    private volatile int target;
    private ScheduledExecutorService exec;
    private long lastSave, lastTop, lastUp, lastConsign;

    public static BotWorld gI() {
        return I;
    }

    public static boolean isBotId(long id) {
        return id >= ID_BASE;
    }

    public int target() {
        return target;
    }

    public synchronized void start(int defaultTarget) {
        if (exec != null) {
            return;
        }
        try (Connection con = LocalManager.getConnection(); java.sql.Statement st = con.createStatement()) {
            st.executeUpdate("create table if not exists bot_profile (id bigint primary key, name varchar(20) not null, gender tinyint not null,"
                    + " head smallint not null, body smallint not null, leg smallint not null, flag smallint not null, power bigint not null,"
                    + " tiem_nang bigint not null, hpg bigint not null, mpg bigint not null, dameg bigint not null)");
            st.executeUpdate("create table if not exists bot_setting (k varchar(32) primary key, v varchar(64) not null)");
            BotLearn.load();
            BotBrain.load();
            st.executeUpdate("create table if not exists bot_account (player_id bigint primary key, account_id int not null default 0)");
            // tài khoản bot riêng (tạo trên panel, không ai đăng nhập được) + bật/tắt
            st.executeUpdate("alter table bot_account add column if not exists dedicated tinyint not null default 0");
            st.executeUpdate("alter table bot_account add column if not exists enabled tinyint not null default 1");
            st.executeUpdate("alter table bot_account add column if not exists created bigint not null default 0");
            st.executeUpdate("delete from bot_account where dedicated = 0"); // bỏ kiểu cũ: nhân vật của người chơi thật không còn làm bot
            try (ResultSet rs = st.executeQuery("select v from bot_setting where k = 'count'")) {
                target = 0; // bỏ bot giả lập: chỉ còn tài khoản bot (chơi thật)
            }
        } catch (Exception e) {
            Logger.error("Bot: không tạo được bảng bot_profile: " + e.getMessage() + "\n");
            target = defaultTarget;
        }
        exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BotWorld");
            t.setDaemon(true);
            return t;
        });
        exec.scheduleWithFixedDelay(this::tickSafe, 60, 10, TimeUnit.SECONDS);
        Logger.success("Bot: giữ " + target + " bot online (đổi trên web panel)\n");
    }

    /** Đặt số bot online (0 = tắt bot). */
    public void setTarget(int n) {
        target = 0; // bỏ bot giả lập (chỉ còn tài khoản bot)
        try {
            LocalManager.executeUpdate("replace into bot_setting (k, v) values ('count', ?)", String.valueOf(target));
        } catch (Exception ignored) {
        }
    }

    public List<Bot> bots() {
        List<Bot> out = new ArrayList<>();
        for (Bot b : new ArrayList<>(BotManager.gI().bot)) {
            if (b != null && b.worldBot) {
                out.add(b);
            }
        }
        return out;
    }

    public Bot find(long id) {
        for (Bot b : bots()) {
            if (b.id == id) {
                return b;
            }
        }
        return null;
    }

    private void tickSafe() {
        try {
            tick();
        } catch (Throwable t) {
            Logger.error("Bot: lỗi " + t + "\n");
        }
    }

    private final BotClan clan = new BotClan();

    /** Có người mời tài khoản bot vào bang. */
    public void onClanInvite(Player bot, nro.models.clan.Clan c) {
        clan.onInvite(bot, c);
    }

    private long lastSocial;

    /** Bang hội, doanh trại, buôn bán: chạy trên luồng cập nhật bot (BotManager) để không đụng hành trang / map cùng lúc với bot. */
    void socialTick() {
        long now = System.currentTimeMillis();
        if (now - lastSocial < 10_000) {
            return;
        }
        lastSocial = now;
        List<Bot> acc = new ArrayList<>(bots());
        acc.removeIf(x -> !x.accountBot);
        clan.tick(acc);
        BotTrade.gI().tick(acc);
        BotQuest.gI().tick(acc);
    }

    private void tick() {
        syncAccounts();
        List<Bot> on = new ArrayList<>();
        for (Bot b : bots()) {
            if (!b.accountBot) {
                on.add(b); // số bot giả lập (hồ sơ bot_profile) tính riêng
            }
        }
        if (on.size() < target) {
            int add = Math.min(5, target - on.size()); // vào dần, không dồn một lúc
            java.util.Set<Long> taken = new java.util.HashSet<>();
            for (Bot b : on) {
                taken.add(b.id);
            }
            for (int i = 0; i < add; i++) {
                spawn(taken);
            }
        } else if (on.size() > target) {
            for (int i = 0; i < on.size() - target && i < 10; i++) {
                remove(on.get(on.size() - 1 - i));
            }
        }
        long now = System.currentTimeMillis();
        for (Bot b : bots()) {
            if (b.awayUntil > 0) {
                if (now >= b.awayUntil) {
                    comeBack(b);
                }
                continue;
            }
            if ("PK".equals(b.activity) && now > b.pkUntil) {
                endPk(b);
                train(b);
            }
            if (!b.accountBot) {
                grow(b); // bot tài khoản lên sức mạnh thật bằng đánh quái, không cộng ảo
            }
            long[] ns = nextSwitch.computeIfAbsent(b.id, k -> new long[]{now + Util.nextInt(120, 420) * 1000L});
            if (now >= ns[0]) {
                switchActivity(b);
                ns[0] = now + Util.nextInt(180, 600) * 1000L;
            }
        }
        if (now - lastTop > 120_000) {
            lastTop = now;
            nro.models.services.SeasonEventService.gI().botRace(simBots()); // đua top bám sát người chơi thật
        }
        if (now - lastUp > Util.nextInt(4, 8) * 60_000L) {
            lastUp = now;
            upgradeAndFarm();
        }
        if (now - lastConsign > 10 * 60_000L) {
            lastConsign = now;
            consign();
        }
        if (now - lastSave > 300_000) {
            lastSave = now;
            saveAll();
        }
    }

    // ------------------------------------------------------------------ tài khoản bot (nhân vật thật do admin chọn)
    private long lastAccSync;
    private final Map<Long, Long> offlineUntil = new java.util.concurrent.ConcurrentHashMap<>(); // tài khoản bot đang "nghỉ chơi"

    /** Id các tài khoản bot (để chặn đăng nhập). */
    private volatile java.util.Set<Integer> botAccountIds = java.util.Set.of();

    /** Tài khoản bot đang bật: {player id -> account id}. */
    public Map<Long, Integer> accountBots() {
        return accountBots(true);
    }

    public Map<Long, Integer> accountBots(boolean enabledOnly) {
        Map<Long, Integer> m = new java.util.LinkedHashMap<>();
        java.util.Set<Integer> ids = new java.util.HashSet<>();
        try (Connection con = LocalManager.getConnection();
                PreparedStatement ps = con.prepareStatement("select b.player_id, p.account_id, b.enabled from bot_account b join player p on p.id = b.player_id where b.dedicated = 1 order by b.created");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getInt(2));
                if (!enabledOnly || rs.getInt(3) != 0) {
                    m.put(rs.getLong(1), rs.getInt(2));
                }
            }
            botAccountIds = ids;
        } catch (Exception e) {
            Logger.error("Bot: không đọc được bot_account: " + e.getMessage() + "\n");
        }
        return m;
    }

    /** Tài khoản bot: không ai đăng nhập được (tách biệt với tài khoản người chơi). */
    public boolean isBotAccount(int accountId) {
        if (botAccountIds.contains(accountId)) {
            return true;
        }
        try (Connection con = LocalManager.getConnection();
                PreparedStatement ps = con.prepareStatement("select 1 from bot_account b join player p on p.id = b.player_id where b.dedicated = 1 and p.account_id = ?")) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isEnabled(long playerId) {
        return accountBots(true).containsKey(playerId);
    }

    /**
     * Tạo tài khoản bot mới (tài khoản + nhân vật riêng, mật khẩu ngẫu nhiên không ai biết). power > 0: sức mạnh / tiềm năng ban đầu
     * (bot tự cộng tiềm năng vào HP / sức đánh). task > 0: nhiệm vụ bắt đầu.
     */
    public long createBotAccount(String name, int gender, long power, int task) throws Exception {
        if (name == null || name.isBlank()) {
            name = newName();
        }
        name = name.trim().toLowerCase();
        if (name.length() < 3 || name.length() > 15 || nro.models.utils.Util.haveSpecialCharacter(name)) {
            throw new IllegalArgumentException("Tên 3 - 15 ký tự, không ký tự đặc biệt");
        }
        if (LocalManager.executeQuery("select 1 from player where name = ?", name).first()) {
            throw new IllegalArgumentException("Tên nhân vật đã tồn tại");
        }
        if (gender < 0 || gender > 2) {
            gender = rnd.nextInt(3);
        }
        String user = null;
        for (int t = 0; t < 20 && user == null; t++) {
            String u = "bot" + (10_000_000 + rnd.nextInt(90_000_000));
            if (!LocalManager.executeQuery("select 1 from account where username = ?", u).first()) {
                user = u;
            }
        }
        if (user == null) {
            throw new IllegalStateException("Không tạo được tên tài khoản");
        }
        byte[] pw = new byte[16];
        new java.security.SecureRandom().nextBytes(pw);
        StringBuilder pass = new StringBuilder();
        for (byte x : pw) {
            pass.append(String.format("%02x", x));
        }
        LocalManager.executeUpdate("insert into account (username, password, email, token, xsrf_token, newpass, active) values (?, ?, '', '', '', '', 1)", user, pass.toString());
        int accId;
        nro.models.data.LocalResultSet ra = LocalManager.executeQuery("select id from account where username = ?", user);
        try {
            if (!ra.first()) {
                throw new IllegalStateException("Không tạo được tài khoản");
            }
            accId = ra.getInt("id");
        } finally {
            ra.dispose();
        }
        long pid;
        try {
            int[][] hairs = {{64, 30, 31}, {9, 29, 32}, {6, 27, 28}};
            if (!nro.models.database.PlayerDAO.createNewPlayer(accId, name, (byte) gender, hairs[gender][rnd.nextInt(3)])) {
                LocalManager.executeUpdate("delete from account where id = ?", accId);
                throw new IllegalStateException("Không tạo được nhân vật");
            }
            nro.models.data.LocalResultSet rs = LocalManager.executeQuery("select id, data_point from player where account_id = ? limit 1", accId);
            try {
                if (!rs.first()) {
                    throw new IllegalStateException("Không tạo được nhân vật");
                }
                pid = rs.getInt("id");
                if (power > 2000) {
                    org.json.simple.JSONArray pt = (org.json.simple.JSONArray) org.json.simple.JSONValue.parse(rs.getString("data_point"));
                    pt.set(1, Math.min(power, 120_000_000_000L));
                    pt.set(2, Math.min(power, 120_000_000_000L));
                    LocalManager.executeUpdate("update player set data_point = ? where id = ?", pt.toJSONString(), pid);
                }
            } finally {
                rs.dispose();
            }
            if (task > 0) {
                LocalManager.executeUpdate("update player set data_task = ? where id = ?", "[" + Math.min(task, 40) + ",0,0," + System.currentTimeMillis() + "]", pid);
            }
        } catch (Exception e) {
            LocalManager.executeUpdate("delete from player where account_id = ?", accId); // tạo dở thì xoá, không để tài khoản thừa
            LocalManager.executeUpdate("delete from account where id = ?", accId);
            throw e;
        }
        LocalManager.executeUpdate("insert into bot_account (player_id, account_id, dedicated, enabled, created) values (?, ?, 1, 1, ?)", pid, accId, System.currentTimeMillis());
        accountBots(false);
        lastAccSync = 0;
        return pid;
    }

    /** Xoá hẳn tài khoản bot (bot thoát, xoá nhân vật + tài khoản). */
    public void deleteBotAccount(long playerId) throws Exception {
        Map<Long, Integer> all = accountBots(false);
        Integer acc = all.get(playerId);
        if (acc == null) {
            throw new IllegalArgumentException("Không phải tài khoản bot");
        }
        Bot b = find(playerId);
        if (b != null && b.clan != null) {
            if (b.clan.isLeader(b)) {
                throw new IllegalStateException("Bot đang là bang chủ, tắt bot hoặc chờ bot nhường chức rồi xoá");
            }
            nro.models.services.ClanService.gI().leaveClan(b);
        } else if (b == null && clanOf(playerId)) {
            throw new IllegalStateException("Bot đang ở trong bang, bật bot cho vào game rồi xoá (để rời bang)");
        }
        LocalManager.executeUpdate("delete from bot_account where player_id = ?", playerId);
        if (b != null) {
            BotManager.gI().bot.remove(b);
            nextSwitch.remove(b.id);
            try {
                ChangeMapService.gI().exitMap(b);
            } catch (Exception ignored) {
            }
        }
        LocalManager.executeUpdate("delete from player where id = ?", playerId);
        LocalManager.executeUpdate("delete from account where id = ?", acc);
        accountBots(false);
    }

    /**
     * Xoá hết tài khoản bot. Bot thoát game trước; bang có bot: bot rời bang, bang do bot làm chủ thì nhường cho người thật
     * (ưu tiên phó bang), bang không còn người thật thì giải tán. Trả về {đã xoá, lỗi, bang giải tán}.
     */
    public synchronized int[] deleteAllBotAccounts() throws Exception {
        java.util.Set<Long> ids = new java.util.HashSet<>(accountBots(false).keySet());
        Map<Long, Integer> accs = accountBots(false);
        setAllAccountBots(false);
        int dissolved = 0;
        for (nro.models.clan.Clan c : new ArrayList<>(Manager.CLANS)) {
            List<nro.models.clan.ClanMember> bots = new ArrayList<>(), humans = new ArrayList<>();
            for (nro.models.clan.ClanMember m : new ArrayList<>(c.members)) {
                (ids.contains((long) m.id) ? bots : humans).add(m);
            }
            if (bots.isEmpty()) {
                continue;
            }
            synchronized (c.membersInGame) {
                c.membersInGame.removeIf(p -> p != null && ids.contains(p.id));
            }
            if (humans.isEmpty()) {
                LocalManager.executeUpdate("update player set clan_id = -1 where clan_id = ?", c.id);
                c.deleteDB(c.id);
                Manager.CLANS.remove(c);
                dissolved++;
                continue;
            }
            boolean leaderGone = bots.stream().anyMatch(m -> m.role == nro.models.clan.Clan.LEADER);
            for (nro.models.clan.ClanMember m : bots) {
                c.removeClanMember(m);
            }
            if (leaderGone) {
                humans.sort((x, y) -> Byte.compare(x.role, y.role)); // phó bang (1) trước thành viên (2)
                humans.get(0).role = nro.models.clan.Clan.LEADER;
            }
            c.update();
            c.sendMyClanForAllMember();
        }
        int ok = 0, fail = 0;
        for (long id : ids) {
            try {
                Bot b = find(id);
                if (b != null) {
                    BotManager.gI().bot.remove(b);
                    nextSwitch.remove(b.id);
                    try {
                        ChangeMapService.gI().exitMap(b);
                    } catch (Exception ignored) {
                    }
                }
                LocalManager.executeUpdate("delete from bot_account where player_id = ?", id);
                LocalManager.executeUpdate("delete from player where id = ?", id);
                LocalManager.executeUpdate("delete from account where id = ?", accs.get(id));
                ok++;
            } catch (Exception e) {
                fail++;
                Logger.error("Bot: không xoá được tài khoản bot " + id + ": " + e.getMessage() + "\n");
            }
        }
        accountBots(false);
        lastAccSync = 0;
        return new int[]{ok, fail, dissolved};
    }

    private static boolean clanOf(long playerId) throws Exception {
        nro.models.data.LocalResultSet rs = LocalManager.executeQuery("select clan_id from player where id = ?", playerId);
        try {
            return rs.first() && rs.getInt("clan_id") != -1;
        } finally {
            rs.dispose();
        }
    }

    /** Tạo n tài khoản bot (tên ngẫu nhiên, hành tinh ngẫu nhiên, bắt đầu từ nhiệm vụ đầu). Trả về số bot tạo được. */
    public int createBotAccounts(int n) {
        int ok = 0;
        for (int i = 0; i < n; i++) {
            try {
                createBotAccount("", -1, 0, 0);
                ok++;
            } catch (Exception e) {
                Logger.error("Bot: không tạo được tài khoản bot: " + e.getMessage() + "\n");
            }
        }
        return ok;
    }

    public void setAllAccountBots(boolean on) throws Exception {
        LocalManager.executeUpdate("update bot_account set enabled = ? where dedicated = 1", on ? 1 : 0);
        if (!on) {
            for (Bot b : bots()) {
                if (b.accountBot) {
                    remove(b);
                }
            }
        }
        lastAccSync = 0;
    }

    public Bot randomOnline() {
        List<Bot> l = new ArrayList<>(bots());
        l.removeIf(x -> !x.accountBot || x.zone == null);
        return l.isEmpty() ? null : l.get(rnd.nextInt(l.size()));
    }

    /** Tổng hợp cho menu bot trong game / panel: số bot đang chơi, tổng số, đang làm gì. */
    public String summary() {
        Map<String, Integer> act = new java.util.TreeMap<>();
        int online = 0;
        for (Bot b : bots()) {
            if (b.accountBot) {
                online++;
                act.merge(b.activity == null || b.activity.isEmpty() ? "Luyện tập" : b.activity, 1, Integer::sum);
            }
        }
        StringBuilder sb = new StringBuilder("Đang chơi: " + online + " / " + accountBots(false).size() + " tài khoản bot");
        for (Map.Entry<String, Integer> e : act.entrySet()) {
            sb.append("\n").append(e.getKey()).append(": ").append(e.getValue());
        }
        return sb.toString();
    }

    /** Bật / tắt 1 tài khoản bot (tắt: bot thoát game, nhân vật vẫn giữ). */
    public void setAccountBot(long playerId, boolean on) throws Exception {
        LocalManager.executeUpdate("update bot_account set enabled = ? where player_id = ? and dedicated = 1", on ? 1 : 0, playerId);
        if (!on) {
            Bot b = find(playerId);
            if (b != null) {
                remove(b);
            }
        }
        lastAccSync = 0;
    }

    /** Giữ mỗi tài khoản bot 1 nhân vật trong game khi chủ tài khoản không online (cứ 30 giây kiểm tra). */
    private void syncAccounts() {
        long now = System.currentTimeMillis();
        if (now - lastAccSync < 30_000) {
            return;
        }
        lastAccSync = now;
        Map<Long, Integer> want = accountBots();
        for (Bot b : bots()) {
            if (b.accountBot && !want.containsKey(b.id)) {
                remove(b);
            } else if (b.accountBot && b.sessionEnd > 0 && now > b.sessionEnd && !BotClan.inDungeon(b)
                    && !nro.models.services_func.TransactionService.gI().check(b)) {
                remove(b); // hết ca chơi: thoát, nghỉ một lúc rồi mới vào lại
                offlineUntil.put(b.id, now + offlineMillis());
            }
        }
        int added = 0;
        for (Map.Entry<Long, Integer> e : want.entrySet()) {
            if (added >= 20) {
                break; // vào dần (20 bot / 30 giây)
            }
            long pid = e.getKey();
            if (!offlineUntil.containsKey(pid) && Util.isTrue(35, 100)) {
                offlineUntil.put(pid, now + Util.nextInt(0, 120) * 60_000L); // lúc mới mở server: không phải ai cũng đang online
            }
            if (offlineUntil.getOrDefault(pid, 0L) > now) {
                continue;
            }
            if (find(pid) != null || nro.models.server.Client.gI().getPlayerByID((int) pid) != null) {
                continue; // đã có bot hoặc chủ tài khoản đang online
            }
            if (spawnAccount(pid, e.getValue())) {
                added++;
            }
        }
    }

    private boolean spawnAccount(long pid, int accountId) {
        Player p = nro.models.database.VietLoader.loadInto(pid, () -> new Bot((short) 0, (short) 0, (short) 0, 0, "", null, (short) -1));
        if (!(p instanceof Bot)) {
            return false;
        }
        Bot b = (Bot) p;
        b.isOffline = false; // nạp kiểu offline sẽ bị PlayerDAO.updatePlayer dispose sau khi lưu
        b.isBot = true;
        b.worldBot = true;
        b.accountBot = true;
        b.accountId = accountId;
        b.activity = "Luyện tập";
        b.sessionEnd = System.currentTimeMillis() + (night() ? Util.nextInt(20, 90) : Util.nextInt(40, 240)) * 60_000L;
        b.mo1 = new BotPemQuai(b);
        b.boss = new BotSanBoss(b);
        b.charms.tdThuHut = Math.max(b.charms.tdThuHut, System.currentTimeMillis() + 24L * 3600_000); // tự nhặt đồ như người treo máy
        if (b.playerSkill.skills.isEmpty()) {
            b.leakSkill();
        }
        if (b.pet != null) {
            b.pet.status = nro.models.player.Pet.GOHOME; // đệ ở nhà (bot không điều khiển đệ)
        }
        if (b.clan != null) {
            b.clan.addMemberOnline(b); // nạp kiểu offline không thêm vào danh sách thành viên online
        }
        BotManager.gI().bot.add(b);
        b.joinMap();
        if (b.zone == null) {
            BotManager.gI().bot.remove(b);
            if (b.clan != null) {
                b.clan.removeMemberOnline(null, b);
            }
            return false;
        }
        return true;
    }

    private static boolean night() {
        int h = java.time.LocalTime.now().getHour();
        return h >= 1 && h < 7;
    }

    /** Nghỉ giữa 2 ca chơi: ban ngày 10 phút - 2 tiếng, đêm khuya lâu hơn (đi ngủ). */
    private static long offlineMillis() {
        return (night() ? Util.nextInt(120, 420) : Util.nextInt(10, 120)) * 60_000L;
    }

    // ------------------------------------------------------------------ trò chuyện với người chơi
    private static final java.util.concurrent.ScheduledExecutorService CHAT = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "bot-chat");
        t.setDaemon(true);
        return t;
    });

    /** Người chơi chat trong khu: bot bị gọi tên thì trả lời; chào / hỏi chung thì thỉnh thoảng 1 bot đáp. */
    public void onPlayerChat(Player pl, String text) {
        if (pl == null || pl.isBot || pl.zone == null || text == null) {
            return;
        }
        String t = text.toLowerCase();
        List<Bot> near = new ArrayList<>();
        Bot called = null;
        for (Player p : new ArrayList<>(pl.zone.players)) {
            if (p instanceof Bot && ((Bot) p).accountBot && !p.isDie()) {
                Bot b = (Bot) p;
                near.add(b);
                if (b.name != null && b.name.length() >= 3 && t.contains(b.name.toLowerCase())) {
                    called = b;
                }
            }
        }
        if (near.isEmpty()) {
            return;
        }
        Bot b = called;
        if (b == null) {
            if (!Util.isTrue(45, 100) || !(t.matches(".*\\b(hi|hello|alo|chào|chao|hế lô|helo|ae|mọi người|bot)\\b.*") || t.endsWith("?"))) {
                return;
            }
            b = near.get(Util.nextInt(0, near.size() - 1));
        }
        long now = System.currentTimeMillis();
        if (now - b.lastReply < 20_000) {
            return;
        }
        b.lastReply = now;
        String reply = reply(t, pl.name);
        Bot who = b;
        CHAT.schedule(() -> {
            if (who.zone != null && who.zone == pl.zone) {
                nro.models.services.Service.gI().chat(who, reply);
            }
        }, Util.nextInt(1500, 5000), java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    static String reply(String t, String name) {
        String[] r;
        if (t.contains("bot")) {
            r = new String[]{"bot đâu mà bot", "người thật 100% nhé", "bot gì tầm này", "=))"};
        } else if (t.matches(".*\\b(hi|hello|alo|chào|chao|hế lô|helo)\\b.*")) {
            r = new String[]{"chào " + name, "hi bạn", "alo", "chào nha", "hế lô " + name};
        } else if (t.contains("bang") || t.contains("clan")) {
            r = new String[]{"bang mình đang tuyển đó, xin vào đi", "mình có bang rồi", "bang gì bạn"};
        } else if (t.contains("pk") || t.contains("solo")) {
            r = new String[]{"thôi đang up", "lát nhé", "solo thì ra võ đài"};
        } else if (t.contains("đâu") || t.contains("map")) {
            r = new String[]{"đang up ở đây nè", "không biết nữa", "hỏi admin xem"};
        } else if (t.endsWith("?")) {
            r = new String[]{"không biết nữa", "chịu", "hỏi admin đi bạn", "mình cũng mới chơi"};
        } else {
            r = new String[]{"sao bạn", "gọi gì đó", "?", "ơi", "đang up nè"};
        }
        return r[Util.nextInt(0, r.length - 1)];
    }

    /** Chủ tài khoản bot đăng nhập: bot thoát ngay và lưu nhân vật trước khi server nạp nhân vật cho chủ. */
    public void releaseAccount(int accountId) {
        for (Bot b : bots()) {
            if (b.accountBot && b.accountId == accountId) {
                remove(b);
            }
        }
    }

    /** Lên sức mạnh dần như người cày (thêm tiềm năng, bot tự cộng chỉ số trong Bot.update). */
    private void grow(Bot b) {
        long p = b.nPoint.power;
        long add = Math.max(2_000, (long) (p * (b.getMode() == 0 ? 0.0012 : 0.0006)));
        b.nPoint.power = Math.min(80_000_000_000L, p + add);
        b.nPoint.tiemNang += add;
    }

    private void switchActivity(Bot b) {
        if (b.accountBot && BotClan.inDungeon(b) && !"NRSD".equals(b.activity)) {
            return; // đang trong doanh trại: đánh cho xong
        }
        if (b.accountBot && b.zone != null && nro.models.map.service.MapService.gI().isMapDoanhTrai(b.zone.map.mapId)) {
            return;
        }
        endPk(b);
        if (b.accountBot && b.playerTask != null && b.playerTask.taskMain != null && b.playerTask.taskMain.id <= 10) {
            train(b); // người mới: làm nhiệm vụ đầu (BotQuest) trước đã
            return;
        }
        b.stayMap = false;
        BotBrain.end(b); // chấm điểm việc vừa làm (bộ não tự học)
        if (b.accountBot && BotEndgame.pickActivity(b)) {
            return; // Hirudegarn 22h, hút Kilis, up mảnh bông tai
        }
        boolean nrsd = nro.models.utils.TimeUtil.isBlackBallWarOpen();
        int a = BotBrain.pick(b, new boolean[]{true, true, true, nrsd, true, true});
        if (a < 0) { // bộ não tắt: chọn ngẫu nhiên như trước
            int r = Util.nextInt(1, 100);
            a = r <= 40 ? BotBrain.TRAIN : r <= 55 ? BotBrain.BOSS : r <= 63 ? BotBrain.BROLY
                    : r <= 73 && nrsd ? BotBrain.NRSD : r <= 81 ? BotBrain.SKH : r <= 90 ? BotBrain.PK : BotBrain.TRAIN;
        }
        switch (a) {
            case BotBrain.BOSS -> {
                b.boss.prefer = null;
                b.boss.bossAttack = null;
                b.setMode(2);
                b.activity = "Săn boss";
            }
            case BotBrain.BROLY -> { // săn đệ: đi đánh Broly
                b.boss.prefer = "Broly";
                b.boss.bossAttack = null;
                b.setMode(2);
                b.activity = "Săn đệ (Broly)";
            }
            case BotBrain.NRSD ->
                goNrsd(b);
            case BotBrain.SKH -> {
                if (b.accountBot) {
                    upSkh(b); // tài khoản bot đi doanh trại thật cùng bang (BotClan), lúc rảnh đi úp set kích hoạt
                } else {
                    goAway(b, Util.isTrue(50, 100) ? "Doanh trại" : "Đại hội võ thuật");
                }
            }
            case BotBrain.PK ->
                startPk(b);
            default ->
                train(b);
        }
        if (b.accountBot) {
            BotBrain.begin(b, a);
        }
    }

    /** Úp set kích hoạt ở map úp SKH của hành tinh mình (đồ rơi hiếm, BotQuest tự mặc vào). */
    private void upSkh(Bot b) {
        int[][] maps = {{1, 2, 3}, {8, 9, 11}, {15, 16, 17}};
        b.boss.prefer = null;
        b.setMode(0);
        b.questMap = maps[Math.max(0, Math.min(2, b.gender))][Util.nextInt(0, 2)];
        b.questMob = -1;
        b.activity = "Úp SKH";
        b.joinMap();
    }

    private void train(Bot b) {
        b.questMap = -1; // BotQuest đặt lại map / quái nhiệm vụ nếu có
        b.questMob = -1;
        b.stayMap = false;
        b.boss.prefer = null;
        b.setMode(0);
        b.activity = "Luyện tập";
        b.joinMap();
    }

    /** Vào NRSD: cắm cờ ngẫu nhiên, đánh quái / bị người chơi đánh như thường, không nhặt ngọc rồng sao đen. */
    private void goNrsd(Bot b) {
        nro.models.map.Map m = nro.models.map.service.MapService.gI().getMapById(Util.nextInt(85, 91));
        if (m == null || m.zones.isEmpty()) {
            train(b);
            return;
        }
        nro.models.map.Zone z = m.zones.get(Util.nextInt(0, m.zones.size() - 1));
        if (z.isFullPlayer()) {
            train(b);
            return;
        }
        b.setMode(0);
        b.activity = "NRSD";
        ChangeMapService.gI().goToMap(b, z);
        z.load_Me_To_Another(b);
        nro.models.services.Service.gI().changeFlag(b, Util.nextInt(1, 7));
    }

    /** Doanh trại / ĐHVT là phó bản / giải đấu riêng: bot rời map một lúc, về thì mạnh lên và kể lại. */
    private void goAway(Bot b, String where) {
        b.activity = where;
        b.awayUntil = System.currentTimeMillis() + Util.nextInt(5, 10) * 60_000L;
        try {
            ChangeMapService.gI().exitMap(b);
        } catch (Exception ignored) {
        }
    }

    private static final String[] BACK_CHAT = {"vừa đi doanh trại về, mệt ghê", "đánh doanh trại xong rồi ae", "ĐHVT vào tới vòng trong rồi",
        "đấu ĐHVT thua tiếc quá", "doanh trại hôm nay nhiều đồ ghê"};

    private void comeBack(Bot b) {
        b.awayUntil = 0;
        long bonus = Math.max(50_000, b.nPoint.power / 100);
        b.nPoint.power += bonus;
        b.nPoint.tiemNang += bonus;
        train(b);
        if (b.zone != null && Util.isTrue(60, 100)) {
            nro.models.services.Service.gI().chat(b, BACK_CHAT[rnd.nextInt(BACK_CHAT.length)]);
        }
    }

    /** PK với 1 bot khác (đưa 2 bot về cùng khu, bật PK, đánh nhau ~3 phút). Không đánh người chơi thật. */
    private void startPk(Bot b) {
        List<Bot> others = new ArrayList<>();
        for (Bot o : bots()) {
            if (o != b && o.zone != null && o.awayUntil == 0 && o.pkTarget == null && !"NRSD".equals(o.activity)) {
                others.add(o);
            }
        }
        if (others.isEmpty() || b.zone == null) {
            train(b);
            return;
        }
        Bot o = others.get(rnd.nextInt(others.size()));
        if (o.zone != b.zone) {
            ChangeMapService.gI().goToMap(b, o.zone);
            o.zone.load_Me_To_Another(b);
        }
        long until = System.currentTimeMillis() + 180_000;
        for (Bot x : new Bot[]{b, o}) {
            x.setMode(0);
            x.activity = "PK";
            x.pkUntil = until;
            nro.models.services.PlayerService.gI().changeAndSendTypePK(x, nro.models.consts.ConstPlayer.PK_ALL);
        }
        b.pkTarget = o;
        o.pkTarget = b;
        nro.models.services.Service.gI().chat(b, Util.isTrue(50, 100) ? "solo không " + o.name : "lại đây PK nào");
    }

    private void endPk(Bot b) {
        if (b.pkTarget != null || "PK".equals(b.activity)) {
            b.pkTarget = null;
            try {
                nro.models.services.PlayerService.gI().changeAndSendTypePK(b, 0);
            } catch (Exception ignored) {
            }
        }
    }

    // ------------------------------------------------------------------ đập đồ, úp vàng, ký gửi
    private static final String[] UP_CHAT = {"đập đồ +%d thành công rồi ae", "đập +%d tạch, buồn quá", "up vàng mỏi tay ghê", "vừa úp được mấy trăm triệu vàng",
        "đập lên +%d rồi, mạnh hẳn"};
    /** vật phẩm bot đem ký gửi: {id, giá vàng thấp, giá vàng cao} */
    private static final int[][] SELL = {{441, 2_000_000, 8_000_000}, {442, 2_000_000, 8_000_000}, {443, 2_000_000, 8_000_000}, {444, 2_000_000, 8_000_000},
        {445, 2_000_000, 8_000_000}, {446, 2_000_000, 8_000_000}, {447, 2_000_000, 8_000_000}, {220, 1_000_000, 5_000_000}, {221, 1_000_000, 5_000_000},
        {222, 1_000_000, 5_000_000}, {223, 1_000_000, 5_000_000}, {224, 1_000_000, 5_000_000}, {1909, 3_000_000, 15_000_000}, {457, 40_000_000, 60_000_000}};
    private static final int MAX_LISTINGS = 20;

    private void upgradeAndFarm() {
        List<Bot> on = simBots();
        if (on.isEmpty()) {
            return;
        }
        Bot b = on.get(rnd.nextInt(on.size()));
        if (b.zone == null || b.awayUntil > 0) {
            return;
        }
        String c = UP_CHAT[rnd.nextInt(UP_CHAT.length)];
        nro.models.services.Service.gI().chat(b, c.contains("%d") ? String.format(c, Util.nextInt(3, 8)) : c);
        b.nPoint.power += Math.max(20_000, b.nPoint.power / 300); // đồ mạnh hơn
    }

    /** Bot đem vật phẩm thường lên ký gửi (tối đa 20 món của bot cùng lúc), món đã bán thì bot "nhận tiền" (gỡ khỏi danh sách). */
    private void consign() {
        List<nro.models.shop_ky_gui.ConsignItem> list = nro.models.shop_ky_gui.ConsignShopManager.gI().listItem;
        list.removeIf(it -> it != null && isBotId(it.player_sell) && it.isBuy);
        long mine = list.stream().filter(it -> it != null && isBotId(it.player_sell)).count();
        List<Bot> on = simBots();
        if (mine >= MAX_LISTINGS || on.isEmpty()) {
            return;
        }
        Bot b = on.get(rnd.nextInt(on.size()));
        int[] s = SELL[rnd.nextInt(SELL.length)];
        nro.models.item.Item it = ItemService.gI().createNewItem((short) s[0], Util.nextInt(1, 5));
        it.itemOptions.addAll(ItemService.gI().getListOptionItemShop((short) s[0]));
        int price = Util.nextInt(s[1], s[2]) / 100_000 * 100_000;
        list.add(new nro.models.shop_ky_gui.ConsignItem(nro.models.shop_ky_gui.ConsignShopService.gI().getMaxId() + 1, it.template.id, (int) b.id,
                nro.models.shop_ky_gui.ConsignShopService.gI().getTabKiGui(it), price, -1, it.quantity, (byte) 0, it.itemOptions, false));
    }

    // ------------------------------------------------------------------ tạo / bỏ bot
    // tên giống người chơi thật: hanabi24h, meocon2k5, rongden99...
    // Tên kiểu người chơi Ngọc Rồng: nhân vật, chiêu thức, hành tinh, vật phẩm + dạng biến hình / số sao / đuôi quen thuộc.
    private static final String[] DB_HERO = {"goku", "gohan", "goten", "vegeta", "trunks", "picolo", "krillin", "yamcha", "tenshin", "chaozu",
        "bardock", "raditz", "nappa", "broly", "gogeta", "vegito", "kakarot", "frieza", "cell", "buu", "cooler", "zamasu", "hit", "jiren",
        "beerus", "whis", "zeno", "android17", "android18", "bulma", "chichi", "videl", "pan", "uub", "dende", "kami", "karin", "popo",
        "satan", "kaio", "tapion", "janemba", "baby", "omega", "turles", "kefla", "caulifla", "kale", "cabba", "goku", "vegeta", "gohan"};
    private static final String[] DB_SKILL = {"kamejoko", "kamehameha", "masenko", "galick", "kaioken", "genkidama", "bigbang", "finalflash",
        "dragonfist", "makankosappo", "destructo", "thaiduong", "thoimien", "tusat", "khien", "quaqua", "dichchuyen", "biendoi", "laze"};
    private static final String[] DB_PLACE = {"xayda", "namec", "traidat", "kame", "kaio", "fide", "yardrat", "nappa", "rongthan", "shenron",
        "porunga", "ngocrong", "dauthan", "capsule", "radar", "kinhdauvan", "gay", "nhuy", "satthu", "chienbinh"};
    private static final String[] DB_PRE = {"ssj", "sieu", "than", "hac", "bach", "tieu", "vua", "dai", "ma", "ac", "chien", "boss", "cute", "baby", "lao"};
    private static final String[] DB_FORM = {"ssj", "ssj2", "ssj3", "ssj4", "god", "blue", "ui", "rose", "xeno", "gt", "z", "kai", "super",
        "black", "dark", "max", "vip", "pro", "vn", "nro", "x", "zz", "99", "2k5", "2k6", "9x", "68", "88", "79", "", "", "", ""};

    private String newName() {
        for (int t = 0; t < 40; t++) {
            String hero = DB_HERO[rnd.nextInt(DB_HERO.length)], form = DB_FORM[rnd.nextInt(DB_FORM.length)];
            String n = switch (rnd.nextInt(8)) {
                case 0, 1 -> hero + form;                                              // gokussj3, vegetablue
                case 2 -> DB_PRE[rnd.nextInt(DB_PRE.length)] + hero;                   // hacgoku, tieugohan
                case 3 -> DB_SKILL[rnd.nextInt(DB_SKILL.length)] + (rnd.nextBoolean() ? form : String.valueOf(1 + rnd.nextInt(99)));
                case 4 -> "ngocrong" + (1 + rnd.nextInt(7)) + "sao";                   // ngocrong4sao
                case 5 -> hero + DB_PLACE[rnd.nextInt(DB_PLACE.length)];               // gokuxayda, picolonamec
                case 6 -> DB_PLACE[rnd.nextInt(DB_PLACE.length)] + form;               // xaydavip, namecpro
                default -> DB_PRE[rnd.nextInt(DB_PRE.length)] + DB_PLACE[rnd.nextInt(DB_PLACE.length)] + (rnd.nextInt(3) == 0 ? String.valueOf(rnd.nextInt(10)) : "");
            };
            if (n.length() < 4 || n.length() > 15 || Util.haveSpecialCharacter(n)) {
                continue;
            }
            try {
                if (LocalManager.executeQuery("select 1 from player where name = ?", n).first()
                        || LocalManager.executeQuery("select 1 from bot_profile where name = ?", n).first()) {
                    continue;
                }
            } catch (Exception ignored) {
            }
            return n;
        }
        return "chienbinh" + rnd.nextInt(100000);
    }

    private void spawn(java.util.Set<Long> ids) {
        long[] prof = null;
        String[] profName = {null};
        try (Connection con = LocalManager.getConnection();
                PreparedStatement ps = con.prepareStatement("select * from bot_profile order by rand()");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if (!ids.contains(rs.getLong("id"))) {
                    prof = new long[]{rs.getLong("id"), rs.getInt("gender"), rs.getInt("head"), rs.getInt("body"), rs.getInt("leg"), rs.getInt("flag"),
                        rs.getLong("power"), rs.getLong("tiem_nang"), rs.getLong("hpg"), rs.getLong("mpg"), rs.getLong("dameg")};
                    profName[0] = rs.getString("name");
                    break;
                }
            }
        } catch (Exception e) {
            Logger.error("Bot: không đọc được bot_profile: " + e.getMessage() + "\n");
        }
        if (prof == null) {
            prof = newProfile(profName);
        }
        ids.add(prof[0]);
        Bot b = new Bot((short) prof[2], (short) prof[3], (short) prof[4], 0, profName[0], null, (short) prof[5]);
        b.id = prof[0];
        b.worldBot = true;
        b.gender = (byte) prof[1];
        b.mo1 = new BotPemQuai(b);
        b.boss = new BotSanBoss(b);
        b.nPoint.limitPower = 8;
        b.nPoint.power = prof[6];
        b.nPoint.tiemNang = prof[7];
        b.nPoint.hpg = prof[8];
        b.nPoint.mpg = prof[9];
        b.nPoint.dameg = prof[10];
        b.nPoint.hpMax = prof[8];
        b.nPoint.hp = prof[8];
        b.nPoint.mpMax = prof[9];
        b.nPoint.mp = prof[9];
        b.nPoint.maxStamina = 20000;
        b.nPoint.stamina = 20000;
        b.nPoint.critg = 5;
        b.nPoint.defg = 10;
        b.charms.tdThuHut = System.currentTimeMillis() + 30L * 24 * 3600_000;
        for (int j = 0; j < 100; j++) {
            b.inventory.itemsBag.add(ItemService.gI().createItemNull());
        }
        while (b.inventory.itemsBody.size() < 13) { // đủ ô trang bị như người chơi thật (kiểm tra set đồ đọc ô 0 - 12)
            b.inventory.itemsBody.add(ItemService.gI().createItemNull());
        }
        b.leakSkill();
        BotManager.gI().bot.add(b);
        b.joinMap();
        if (b.zone == null) {
            BotManager.gI().bot.remove(b);
        }
    }

    /** Hồ sơ bot mới: hình lấy ngẫu nhiên từ cải trang có sẵn đúng hành tinh, sức mạnh khởi điểm như người chơi mới / trung bình. */
    private long[] newProfile(String[] nameOut) {
        NewBot.gI().LoadPart();
        int gender = rnd.nextInt(3);
        int idx = NewBot.gI().getIndex(gender);
        int head = NewBot.PARTBOT[idx][0], leg = NewBot.PARTBOT[idx][1], body = NewBot.PARTBOT[idx][2];
        int flag = Manager.gI().FLAGS_BAGS.isEmpty() ? -1 : Manager.gI().FLAGS_BAGS.get(rnd.nextInt(Manager.gI().FLAGS_BAGS.size())).id;
        long power = rnd.nextInt(4) == 0 ? Util.nextInt(1_000_000, 2_000_000_000) : Util.nextInt(2_000, 50_000_000);
        long hp = Math.max(1_000, power / 200), mp = Math.max(1_000, power / 250), dame = Math.max(100, power / 2_000);
        long id = ID_BASE;
        try {
            nro.models.data.LocalResultSet rs = LocalManager.executeQuery("select max(id) from bot_profile");
            if (rs.first() && rs.getLong(1) >= ID_BASE) {
                id = rs.getLong(1) + 1;
            }
            rs.close();
        } catch (Exception ignored) {
        }
        nameOut[0] = newName();
        try {
            LocalManager.executeUpdate("insert into bot_profile (id, name, gender, head, body, leg, flag, power, tiem_nang, hpg, mpg, dameg) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, nameOut[0], gender, head, body, leg, flag, power, power / 10, hp, mp, dame);
        } catch (Exception e) {
            Logger.error("Bot: không lưu được hồ sơ bot: " + e.getMessage() + "\n");
        }
        return new long[]{id, gender, head, body, leg, flag, power, power / 10, hp, mp, dame};
    }

    /** Bot giả lập (không phải tài khoản thật): chỉ những bot này mới được cộng điểm / đồ ảo. */
    private List<Bot> simBots() {
        List<Bot> l = new ArrayList<>(bots());
        l.removeIf(x -> x.accountBot);
        return l;
    }

    private void remove(Bot b) {
        save(b);
        if (b.accountBot && b.clan != null) {
            b.clan.removeMemberOnline(null, b);
        }
        BotManager.gI().bot.remove(b);
        nextSwitch.remove(b.id);
        try {
            ChangeMapService.gI().exitMap(b);
        } catch (Exception ignored) {
        }
    }

    private void save(Bot b) {
        if (b.accountBot) {
            if (b.idMark == null || b.nPoint == null || b.inventory == null) {
                return; // đã bị huỷ
            }
            if (b.zone != null) {
                b.mapIdBeforeLogout = b.zone.map.mapId;
            }
            nro.models.database.PlayerDAO.updatePlayer(b); // lưu vào chính nhân vật thật
            return;
        }
        try {
            LocalManager.executeUpdate("update bot_profile set power = ?, tiem_nang = ?, hpg = ?, mpg = ?, dameg = ? where id = ?",
                    b.nPoint.power, b.nPoint.tiemNang, b.nPoint.hpg, b.nPoint.mpg, b.nPoint.dameg, b.id);
        } catch (Exception ignored) {
        }
    }

    public void saveAll() {
        BotLearn.save();
        BotBrain.save();
        for (Bot b : bots()) {
            save(b);
        }
    }

    // ------------------------------------------------------------------ log lỗi bot
    private final Map<Long, int[]> errors = new java.util.concurrent.ConcurrentHashMap<>(); // id -> {số lỗi trong phút, phút}
    private final java.util.Deque<String> recentErrors = new java.util.concurrent.ConcurrentLinkedDeque<>();

    /** Ghi lỗi của bot vào logs/bot.log (tên bot, đang làm gì, ở map nào, chỗ lỗi); bot lỗi liên tục (> 30 lần / phút) thì cho thoát, vào lại sau. */
    public void logError(Bot b, Throwable t) {
        String where = b.zone != null ? b.zone.map.mapName + " [" + b.zone.map.mapId + "]" : "-";
        StringBuilder st = new StringBuilder();
        for (StackTraceElement e : t.getStackTrace()) {
            if (e.getClassName().startsWith("nro.")) {
                st.append("\n    at ").append(e);
                if (st.length() > 600) {
                    break;
                }
            }
        }
        String line = java.time.LocalDateTime.now().withNano(0) + " bot " + b.name + " #" + b.id + " (" + modeName(b) + ", " + where + "): " + t + st;
        int minute = (int) (System.currentTimeMillis() / 60_000);
        int[] c = errors.compute(b.id, (k, v) -> v == null || v[1] != minute ? new int[]{0, minute} : v);
        c[0]++;
        if (c[0] <= 3) { // cùng 1 bot chỉ ghi 3 lỗi / phút cho đỡ đầy file
            try (java.io.FileWriter fw = new java.io.FileWriter("logs/bot.log", true)) {
                fw.write(line + "\n");
            } catch (Exception ignored) {
            }
            recentErrors.addFirst(line);
            while (recentErrors.size() > 50) {
                recentErrors.pollLast();
            }
        }
        if (c[0] > 30 && b.worldBot) {
            errors.remove(b.id);
            remove(b);
        }
    }

    public List<String> recentErrors() {
        return new ArrayList<>(recentErrors);
    }

    private static String modeName(Bot b) {
        return b.activity == null ? "Luyện tập" : b.activity + (b.awayUntil > 0 ? " (vắng)" : "");
    }

    /** Cho web panel: danh sách bot đang online. */
    public List<Map<String, Object>> panelList() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Bot b : bots()) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", b.id);
            m.put("name", b.name);
            m.put("gender", b.gender);
            m.put("avatar", nro.models.panel.PanelApi.avatarOfHead(b.getHead()));
            m.put("power", b.nPoint.power);
            m.put("map", b.zone != null ? b.zone.map.mapName + " [" + b.zone.map.mapId + "] khu " + b.zone.zoneId : "-");
            m.put("activity", modeName(b));
            m.put("account", b.accountBot);
            out.add(m);
        }
        return out;
    }

    /** Tài khoản bot (nhân vật thật): tính thưởng / nhặt đồ như người chơi. */
    public static boolean isAccountBot(Player p) {
        return p instanceof Bot && ((Bot) p).accountBot;
    }

    /** Người chơi thật hoặc tài khoản bot: được thưởng boss, thông báo hạ boss... */
    public static boolean counts(Player p) {
        return p != null && (!p.isBot || isAccountBot(p));
    }

    public static boolean isWorldBot(Player p) {
        return p instanceof Bot && ((Bot) p).worldBot;
    }
}
