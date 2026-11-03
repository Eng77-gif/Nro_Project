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
    private long lastSave, lastTop;

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
            try (ResultSet rs = st.executeQuery("select v from bot_setting where k = 'count'")) {
                target = rs.next() ? Integer.parseInt(rs.getString(1)) : defaultTarget;
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
        target = Math.max(0, Math.min(MAX_BOTS, n));
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

    private void tick() {
        List<Bot> on = bots();
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
            grow(b);
            long[] ns = nextSwitch.computeIfAbsent(b.id, k -> new long[]{now + Util.nextInt(120, 420) * 1000L});
            if (now >= ns[0]) {
                switchActivity(b);
                ns[0] = now + Util.nextInt(180, 600) * 1000L;
            }
        }
        if (now - lastTop > 120_000) {
            lastTop = now;
            for (Bot b : bots()) {
                if (Util.isTrue(35, 100)) {
                    nro.models.services.SeasonEventService.gI().addBotTopPoint(b, Util.nextInt(1, 3));
                }
            }
        }
        if (now - lastSave > 300_000) {
            lastSave = now;
            saveAll();
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
        int r = Util.nextInt(1, 100);
        if (r <= 70) { // luyện tập
            b.boss.prefer = null;
            b.setMode(0);
            b.joinMap();
        } else if (r <= 88) { // săn boss
            b.boss.prefer = null;
            b.boss.bossAttack = null;
            b.setMode(2);
        } else { // săn đệ: đi đánh Broly
            b.boss.prefer = "Broly";
            b.boss.bossAttack = null;
            b.setMode(2);
        }
    }

    // ------------------------------------------------------------------ tạo / bỏ bot
    private static final String[] PRE = {"tuan", "minh", "hung", "long", "nam", "duc", "khoa", "huy", "phuc", "quang", "son", "trung", "vinh", "hoang",
        "ngoc", "linh", "lan", "mai", "thu", "trang", "an", "bao", "dat", "hieu", "kien", "loc", "tai", "tam", "thanh", "viet", "gohan", "goku",
        "vegeta", "cadic", "songoku", "pikachu", "rong", "sayan", "kame", "broly", "zeno", "bulma", "trunks", "picolo", "xayda", "namec"};
    private static final String[] SUF = {"pro", "vip", "99", "2k", "123", "kute", "dz", "vn", "gaming", "07", "88", "xd", "td", "nm", "kk", "zz", "1",
        "2009", "2010", "hn", "sg", "boy", "girl", "", "", ""};

    private String newName() {
        for (int t = 0; t < 30; t++) {
            String n = PRE[rnd.nextInt(PRE.length)] + SUF[rnd.nextInt(SUF.length)] + (rnd.nextInt(3) == 0 ? String.valueOf(rnd.nextInt(100)) : "");
            if (n.length() < 4 || n.length() > 15) {
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
        return "bot" + rnd.nextInt(100000);
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

    private void remove(Bot b) {
        save(b);
        BotManager.gI().bot.remove(b);
        nextSwitch.remove(b.id);
        try {
            ChangeMapService.gI().exitMap(b);
        } catch (Exception ignored) {
        }
    }

    private void save(Bot b) {
        try {
            LocalManager.executeUpdate("update bot_profile set power = ?, tiem_nang = ?, hpg = ?, mpg = ?, dameg = ? where id = ?",
                    b.nPoint.power, b.nPoint.tiemNang, b.nPoint.hpg, b.nPoint.mpg, b.nPoint.dameg, b.id);
        } catch (Exception ignored) {
        }
    }

    public void saveAll() {
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
        return switch (b.getMode()) {
            case 2 -> b.boss != null && b.boss.prefer != null ? "Săn đệ (" + b.boss.prefer + ")" : "Săn boss";
            default -> "Luyện tập";
        };
    }

    /** Cho web panel: danh sách bot đang online. */
    public List<Map<String, Object>> panelList() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Bot b : bots()) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", b.id);
            m.put("name", b.name);
            m.put("gender", b.gender);
            m.put("power", b.nPoint.power);
            m.put("map", b.zone != null ? b.zone.map.mapName + " [" + b.zone.map.mapId + "] khu " + b.zone.zoneId : "-");
            m.put("activity", modeName(b));
            out.add(m);
        }
        return out;
    }

    public static boolean isWorldBot(Player p) {
        return p instanceof Bot && ((Bot) p).worldBot;
    }
}
