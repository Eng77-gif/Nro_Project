package nro.models.Bot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import nro.models.data.LocalManager;
import nro.models.utils.Logger;

/**
 * "Bộ não" tự học của bot (không dùng model AI có sẵn): học tăng cường dạng bảng (contextual bandit), bản 2.
 * <ul>
 * <li>Trạng thái: bậc sức mạnh của bot (số chữ số của sức mạnh: dưới 10 nghìn ... trên 100 nghìn tỷ).</li>
 * <li>Hành động: hoạt động bot chọn mỗi lần đổi việc (luyện tập, săn boss, săn đệ, NRSD, úp SKH, PK).</li>
 * <li>Phần thưởng nhiều mục tiêu, thang log để việc ít lên sức mạnh nhưng kiếm vàng / ngọc (săn boss, NRSD) vẫn được điểm:
 * 8·log10(1 + ‰ sức mạnh tăng / phút) + 2·log10(1 + vàng / phút / 1 Tr) + 1,5·log10(1 + ngọc / phút / 10) − 4 · số lần chết / phút.</li>
 * <li>Cập nhật: Q += α_hiệu_dụng · (thưởng − Q) với α_hiệu_dụng = max(α, 1/(n+1)): lúc ít mẫu là trung bình cộng (học nhanh, ổn định),
 * nhiều mẫu thì theo α (bám thay đổi của server).</li>
 * <li>Chọn việc: UCB1 (điểm + độ thưởng cho việc ít được thử) thay vì thử ngẫu nhiên; bậc mới mượn kinh nghiệm của 2 bậc kề bên;
 * ε phần nhỏ số lần vẫn thử ngẫu nhiên.</li>
 * </ul>
 * Mọi bot dùng chung một bảng nên học nhanh; bảng lưu ở bot_brain, chỉnh và xem trên panel (trang AI huấn luyện bot).
 */
public final class BotBrain {

    public static final String[] ACTIONS = {"Luyện tập", "Săn boss", "Săn đệ", "NRSD", "Úp SKH", "PK"};
    public static final int TRAIN = 0, BOSS = 1, BROLY = 2, NRSD = 3, SKH = 4, PK = 5;
    static final int TIERS = 12; // < 10^4, 10^4, ..., >= 10^14
    private static final double DEATH_PENALTY = 4.0, MAX_REWARD = 30, MIN_REWARD = -20, UCB_C = 2.0, PRIOR_WEIGHT = 2.0;
    static final int VERSION = 2; // đổi thang điểm thì tăng: bảng cũ khác thang điểm, học lại từ đầu

    private static final double[][] Q = new double[TIERS][ACTIONS.length];
    private static final int[][] N = new int[TIERS][ACTIONS.length];
    private static final Deque<double[]> RECENT = new ArrayDeque<>(); // {lúc, bậc, hành động, thưởng, ‰ tăng / phút, số lần chết, số phút}
    private static final Random RND = new Random();
    static volatile double epsilon = 0.05, alpha = 0.1;
    static volatile boolean enabled = true;
    private static long episodes;

    private BotBrain() {
    }

    static int tier(long power) {
        int t = (int) Math.floor(Math.log10(Math.max(1, power))) - 3;
        return Math.max(0, Math.min(TIERS - 1, t));
    }

    static String tierName(int t) {
        String[] n = {"< 10K", "10K", "100K", "1Tr", "10Tr", "100Tr", "1 tỷ", "10 tỷ", "100 tỷ", "1 nghìn tỷ", "10 nghìn tỷ", "≥ 100 nghìn tỷ"};
        return n[t];
    }

    /** Chọn hoạt động trong các hoạt động được phép; -1 nếu bộ não đang tắt (BotWorld chọn ngẫu nhiên như cũ). */
    static synchronized int pick(Bot b, boolean[] allowed) {
        if (!enabled) {
            return -1;
        }
        int t = tier(b.nPoint.power);
        List<Integer> ok = new ArrayList<>();
        for (int a = 0; a < ACTIONS.length; a++) {
            if (allowed[a]) {
                ok.add(a);
            }
        }
        if (ok.isEmpty()) {
            return TRAIN;
        }
        if (RND.nextDouble() < epsilon) {
            return ok.get(RND.nextInt(ok.size()));
        }
        int total = 1;
        for (int a : ok) {
            total += N[t][a];
        }
        int best = ok.get(0);
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int a : ok) {
            double score = estimate(t, a) + UCB_C * Math.sqrt(Math.log(total) / (N[t][a] + 1));
            if (score > bestScore) {
                bestScore = score;
                best = a;
            }
        }
        return best;
    }

    /** Điểm ước lượng: bậc ít mẫu thì kéo về trung bình của 2 bậc kề bên (bot vừa lên bậc không phải học lại từ số 0). */
    static double estimate(int t, int a) {
        double prior = 0;
        int pn = 0;
        for (int d = -1; d <= 1; d += 2) {
            int u = t + d;
            if (u >= 0 && u < TIERS && N[u][a] > 0) {
                prior += Q[u][a];
                pn++;
            }
        }
        if (pn == 0) {
            return Q[t][a];
        }
        prior /= pn;
        return (Q[t][a] * N[t][a] + prior * PRIOR_WEIGHT) / (N[t][a] + PRIOR_WEIGHT);
    }

    /** Bắt đầu 1 lượt: ghi mốc để chấm điểm khi bot đổi việc. */
    static void begin(Bot b, int action) {
        b.brainAction = action;
        b.brainTier = tier(b.nPoint.power);
        b.brainPower = b.nPoint.power;
        b.brainAt = System.currentTimeMillis();
        b.brainDeaths = b.deaths();
        b.brainGold = b.inventory.gold;
        b.brainGem = b.inventory.gem;
    }

    /** Kết thúc lượt đang chạy (trước khi đổi việc): chấm thưởng và học. */
    static synchronized void end(Bot b) {
        int a = b.brainAction;
        b.brainAction = -1;
        long ms = System.currentTimeMillis() - b.brainAt;
        if (a < 0 || ms < 60_000 || b.nPoint == null) {
            return; // làm quá ngắn, chưa đủ để chấm
        }
        double min = ms / 60_000.0;
        double growth = Math.max(0, b.nPoint.power - b.brainPower) / min / Math.max(1_000, b.brainPower) * 1000; // ‰ / phút
        double gold = Math.max(0, b.inventory.gold - b.brainGold) / min;
        double gem = Math.max(0, b.inventory.gem - b.brainGem) / min;
        int died = Math.max(0, b.deaths() - b.brainDeaths);
        double r = 8 * Math.log10(1 + growth) + 2 * Math.log10(1 + gold / 1_000_000) + 1.5 * Math.log10(1 + gem / 10)
                - DEATH_PENALTY * died / min; // phạt theo số lần chết mỗi phút (lượt dài không bị phạt dồn)
        r = Math.max(MIN_REWARD, Math.min(MAX_REWARD, r));
        int t = b.brainTier;
        Q[t][a] += Math.max(alpha, 1.0 / (N[t][a] + 1)) * (r - Q[t][a]);
        N[t][a]++;
        episodes++;
        RECENT.addLast(new double[]{System.currentTimeMillis(), t, a, r, growth, died, min, gold, gem});
        while (RECENT.size() > 300) {
            RECENT.removeFirst();
        }
    }

    // ------------------------------------------------------------------ lưu / nạp
    static void load() {
        try (java.sql.Connection con = LocalManager.getConnection(); java.sql.Statement st = con.createStatement()) {
            st.executeUpdate("create table if not exists bot_brain (tier int not null, action int not null, q double not null, n int not null, primary key (tier, action))");
            st.executeUpdate("create table if not exists bot_brain_cfg (k varchar(16) primary key, v varchar(32) not null)");
            int ver = 1;
            try (java.sql.ResultSet rs = st.executeQuery("select v from bot_brain_cfg where k = 'version'")) {
                if (rs.next()) {
                    ver = Integer.parseInt(rs.getString(1));
                }
            } catch (Exception ignored) {
            }
            if (ver < VERSION) { // bảng của thang điểm cũ: học lại theo thang mới, tham số về mặc định mới
                st.executeUpdate("delete from bot_brain");
                st.executeUpdate("delete from bot_brain_cfg where k in ('epsilon', 'alpha')");
                st.executeUpdate("replace into bot_brain_cfg (k, v) values ('version', '" + VERSION + "')");
                Logger.success("Bot: AI huấn luyện bot lên bản " + VERSION + " (thang điểm mới, học lại từ đầu)\n");
            }
            synchronized (BotBrain.class) {
                try (java.sql.ResultSet rs = st.executeQuery("select tier, action, q, n from bot_brain")) {
                    while (rs.next()) {
                        int t = rs.getInt(1), a = rs.getInt(2);
                        if (t >= 0 && t < TIERS && a >= 0 && a < ACTIONS.length) {
                            Q[t][a] = rs.getDouble(3);
                            N[t][a] = rs.getInt(4);
                            episodes += N[t][a];
                        }
                    }
                }
            }
            try (java.sql.ResultSet rs = st.executeQuery("select k, v from bot_brain_cfg")) {
                while (rs.next()) {
                    if (!"version".equals(rs.getString(1))) {
                        set(rs.getString(1), rs.getString(2));
                    }
                }
            }
        } catch (Exception e) {
            Logger.error("Bot: không đọc được bot_brain: " + e.getMessage() + "\n");
        }
    }

    static synchronized void save() {
        try (java.sql.Connection con = LocalManager.getConnection();
                java.sql.PreparedStatement ps = con.prepareStatement("replace into bot_brain (tier, action, q, n) values (?, ?, ?, ?)");
                java.sql.PreparedStatement pc = con.prepareStatement("replace into bot_brain_cfg (k, v) values (?, ?)")) {
            for (int t = 0; t < TIERS; t++) {
                for (int a = 0; a < ACTIONS.length; a++) {
                    if (N[t][a] > 0) {
                        ps.setInt(1, t);
                        ps.setInt(2, a);
                        ps.setDouble(3, Q[t][a]);
                        ps.setInt(4, N[t][a]);
                        ps.addBatch();
                    }
                }
            }
            ps.executeBatch();
            for (String[] kv : new String[][]{{"epsilon", String.valueOf(epsilon)}, {"alpha", String.valueOf(alpha)}, {"enabled", String.valueOf(enabled)}, {"version", String.valueOf(VERSION)}}) {
                pc.setString(1, kv[0]);
                pc.setString(2, kv[1]);
                pc.addBatch();
            }
            pc.executeBatch();
        } catch (Exception e) {
            Logger.error("Bot: không lưu được bot_brain: " + e.getMessage() + "\n");
        }
    }

    // ------------------------------------------------------------------ panel
    /** Đổi tham số (panel): epsilon / alpha (0 - 1), enabled (true / false). */
    public static void set(String k, String v) {
        switch (k) {
            case "epsilon" ->
                epsilon = Math.max(0, Math.min(1, Double.parseDouble(v)));
            case "alpha" ->
                alpha = Math.max(0.01, Math.min(1, Double.parseDouble(v)));
            case "enabled" ->
                enabled = Boolean.parseBoolean(v);
            default ->
                throw new IllegalArgumentException("Không có tham số " + k);
        }
    }

    /** Xoá hết kinh nghiệm đã học (học lại từ đầu). */
    public static synchronized void reset() {
        for (int t = 0; t < TIERS; t++) {
            java.util.Arrays.fill(Q[t], 0);
            java.util.Arrays.fill(N[t], 0);
        }
        episodes = 0;
        RECENT.clear();
        try {
            LocalManager.executeUpdate("delete from bot_brain");
        } catch (Exception e) {
            Logger.error("Bot: không xoá được bot_brain: " + e.getMessage() + "\n");
        }
    }

    public static synchronized Map<String, Object> panel() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", enabled);
        m.put("epsilon", epsilon);
        m.put("alpha", alpha);
        m.put("episodes", episodes);
        m.put("version", VERSION);
        m.put("actions", ACTIONS);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int t = 0; t < TIERS; t++) {
            int seen = 0, best = -1;
            for (int a = 0; a < ACTIONS.length; a++) {
                seen += N[t][a];
                if (N[t][a] > 0 && (best < 0 || estimate(t, a) > estimate(t, best))) {
                    best = a;
                }
            }
            if (seen == 0) {
                continue;
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("tier", tierName(t));
            r.put("q", Q[t].clone());
            r.put("n", N[t].clone());
            r.put("best", best);
            rows.add(r);
        }
        m.put("table", rows);
        List<double[]> rec = new ArrayList<>(RECENT);
        m.put("recent", rec.subList(Math.max(0, rec.size() - 120), rec.size()));
        m.put("maps", BotLearn.top(15));
        return m;
    }
}
