package nro.models.Bot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import nro.models.data.LocalManager;
import nro.models.utils.Logger;

/**
 * Bot tự rút kinh nghiệm chọn map luyện tập (bản 2, không cần model AI ngoài).
 * <ul>
 * <li>Kinh nghiệm chia theo bậc sức mạnh (như BotBrain): map hợp bot 1 triệu và map hợp bot 100 tỷ không còn so chung một bảng.</li>
 * <li>Mỗi lượt luyện tập ở 1 map (từ lúc vào tới lúc rời map / đổi việc, kể cả bị kéo đi săn boss, làm nhiệm vụ) được chấm:
 * 8·log10(1 + ‰ sức mạnh tăng / phút) + 2·log10(1 + vàng / phút / 1 Tr) − 4 · số lần chết / phút. Trước đây chỉ chấm khi bot tự
 * đổi map luyện tập nên gần như không có dữ liệu.</li>
 * <li>Chọn map: gom các map hợp sức (Bot.pickTierMap), bỏ map chưa đi được / quá mạnh, chọn theo UCB1 (điểm + thưởng cho map ít
 * được thử, bậc mới mượn kinh nghiệm 2 bậc kề bên), 10% số lần chọn ngẫu nhiên để còn khám phá.</li>
 * <li>Dùng chung cho mọi tài khoản bot, lưu ở bảng bot_map_learn (5 phút / lần cùng BotWorld.saveAll), xem trên panel trang AI.</li>
 * </ul>
 */
public final class BotLearn {

    private static final Map<Long, double[]> STAT = new ConcurrentHashMap<>(); // bậc * 10000 + map -> {điểm, số lượt}
    private static final Random RND = new Random();
    private static final double UCB_C = 1.5, EPSILON = 0.10, PRIOR_WEIGHT = 2.0, ALPHA = 0.15;
    private static volatile boolean loaded;

    private BotLearn() {
    }

    private static long key(int tier, int mapId) {
        return tier * 10000L + mapId;
    }

    // ------------------------------------------------------------------ ghi nhận lượt luyện tập
    /** Gọi định kỳ từ Bot.update: đang luyện tập ở 1 map thì giữ lượt, rời map / đổi việc thì chấm lượt cũ. */
    static void tick(Bot b) {
        if (!b.accountBot || b.nPoint == null) {
            return;
        }
        boolean training = b.zone != null && b.getMode() == 0 && ("Luyện tập".equals(b.activity) || "Làm nhiệm vụ".equals(b.activity));
        int map = training ? b.zone.map.mapId : -1;
        if (b.learnMap >= 0 && b.learnMap != map) {
            close(b);
        }
        if (map >= 0 && b.learnMap < 0) {
            b.learnMap = map;
            b.learnTier = BotBrain.tier(b.nPoint.power);
            b.learnPower = b.nPoint.power;
            b.learnGold = b.inventory.gold;
            b.learnSince = System.currentTimeMillis();
            b.learnDeaths = b.deaths();
        }
    }

    /** Kết thúc lượt ở map đang ghi: chấm điểm và học. */
    static void close(Bot b) {
        int map = b.learnMap;
        b.learnMap = -1;
        long ms = System.currentTimeMillis() - b.learnSince;
        if (map < 0 || ms < 60_000 || b.nPoint == null) {
            return; // ở quá ngắn, chưa đủ để chấm
        }
        double min = ms / 60_000.0;
        double growth = Math.max(0, b.nPoint.power - b.learnPower) / min / Math.max(1_000, b.learnPower) * 1000; // ‰ / phút
        double gold = Math.max(0, b.inventory.gold - b.learnGold) / min;
        int died = Math.max(0, b.deaths() - b.learnDeaths);
        double r = 8 * Math.log10(1 + growth) + 2 * Math.log10(1 + gold / 1_000_000) - 4.0 * died / min;
        record(b.learnTier, map, Math.max(-20, Math.min(30, r)));
    }

    static void record(int tier, int map, double r) {
        STAT.compute(key(tier, map), (k, v) -> {
            if (v == null) {
                return new double[]{r, 1};
            }
            double a = Math.max(ALPHA, 1.0 / (v[1] + 1)); // ít mẫu: trung bình cộng; nhiều mẫu: bám thay đổi
            return new double[]{v[0] + a * (r - v[0]), v[1] + 1};
        });
    }

    // ------------------------------------------------------------------ chọn map
    /** Điểm ước lượng: map ít lượt ở bậc này thì kéo về kinh nghiệm của 2 bậc kề bên. */
    static double estimate(int tier, int map) {
        double[] v = STAT.get(key(tier, map));
        double prior = 0;
        int pn = 0;
        for (int d = -1; d <= 1; d += 2) {
            double[] u = STAT.get(key(tier + d, map));
            if (u != null) {
                prior += u[0];
                pn++;
            }
        }
        if (v == null) {
            return pn == 0 ? Double.NaN : prior / pn;
        }
        if (pn == 0) {
            return v[0];
        }
        prior /= pn;
        return (v[0] * v[1] + prior * PRIOR_WEIGHT) / (v[1] + PRIOR_WEIGHT);
    }

    static int count(int tier, int map) {
        double[] v = STAT.get(key(tier, map));
        return v == null ? 0 : (int) v[1];
    }

    /** Chọn map luyện tập trong các map ứng viên (đã lọc đi được, vừa sức); -1 nếu không có ứng viên. */
    static int choose(Bot b, Set<Integer> candidates) {
        if (candidates.isEmpty()) {
            return -1;
        }
        List<Integer> ok = new ArrayList<>(candidates);
        if (RND.nextDouble() < EPSILON) {
            return ok.get(RND.nextInt(ok.size()));
        }
        int tier = BotBrain.tier(b.nPoint.power);
        int total = 1;
        for (int m : ok) {
            total += count(tier, m);
        }
        int best = ok.get(RND.nextInt(ok.size()));
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int m : ok) {
            double e = estimate(tier, m);
            double s = (Double.isNaN(e) ? 10 : e) + UCB_C * Math.sqrt(Math.log(total) / (count(tier, m) + 1)); // chưa ai thử: điểm khá để được thử
            if (s > bestScore) {
                bestScore = s;
                best = m;
            }
        }
        return best;
    }

    /** Gom các map hợp sức của bot (rút nhiều lần từ vùng map theo sức mạnh) rồi bỏ map chưa đi được / quá mạnh. */
    static Set<Integer> candidates(Bot b, java.util.function.IntSupplier pick) {
        Set<Integer> out = new LinkedHashSet<>();
        for (int i = 0; i < 24; i++) {
            int m = pick.getAsInt();
            if (b.canGo(m) && b.strongEnough(m)) {
                out.add(m);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ panel
    /** Map tốt nhất của từng bậc sức mạnh (panel). */
    static List<Map<String, Object>> top(int n) {
        List<Map<String, Object>> out = new ArrayList<>();
        STAT.entrySet().stream()
                .sorted((a, b) -> a.getKey() / 10000 != b.getKey() / 10000 ? Long.compare(b.getKey() / 10000, a.getKey() / 10000)
                : Double.compare(b.getValue()[0], a.getValue()[0]))
                .forEach(e -> {
                    int tier = (int) (e.getKey() / 10000), map = (int) (e.getKey() % 10000);
                    if (out.stream().filter(r -> r.get("tierId").equals(tier)).count() >= 3 || out.size() >= n) {
                        return; // mỗi bậc 3 map tốt nhất
                    }
                    nro.models.map.Map m = nro.models.map.service.MapService.gI().getMapById(map);
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("tierId", tier);
                    r.put("tier", BotBrain.tierName(Math.max(0, Math.min(BotBrain.TIERS - 1, tier))));
                    r.put("map", m != null ? m.mapName : "Map " + map);
                    r.put("score", Math.round(e.getValue()[0] * 10) / 10.0);
                    r.put("n", (int) e.getValue()[1]);
                    out.add(r);
                });
        return out;
    }

    // ------------------------------------------------------------------ lưu / nạp
    static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try (java.sql.Connection con = LocalManager.getConnection(); java.sql.Statement st = con.createStatement()) {
            st.executeUpdate("create table if not exists bot_map_learn (tier int not null, map_id int not null, score double not null, n int not null, primary key (tier, map_id))");
            try (java.sql.ResultSet rs = st.executeQuery("select tier, map_id, score, n from bot_map_learn")) {
                while (rs.next()) {
                    STAT.put(key(rs.getInt(1), rs.getInt(2)), new double[]{rs.getDouble(3), rs.getInt(4)});
                }
            }
        } catch (Exception e) {
            Logger.error("Bot: không đọc được bot_map_learn: " + e.getMessage() + "\n");
        }
    }

    static void save() {
        if (STAT.isEmpty()) {
            return;
        }
        try (java.sql.Connection con = LocalManager.getConnection();
                java.sql.PreparedStatement ps = con.prepareStatement("replace into bot_map_learn (tier, map_id, score, n) values (?, ?, ?, ?)")) {
            for (Map.Entry<Long, double[]> e : STAT.entrySet()) {
                ps.setInt(1, (int) (e.getKey() / 10000));
                ps.setInt(2, (int) (e.getKey() % 10000));
                ps.setDouble(3, e.getValue()[0]);
                ps.setInt(4, (int) e.getValue()[1]);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (Exception e) {
            Logger.error("Bot: không lưu được bot_map_learn: " + e.getMessage() + "\n");
        }
    }

    /** Xoá hết kinh nghiệm chọn map (nút Học lại trên panel). */
    static void reset() {
        STAT.clear();
        try {
            LocalManager.executeUpdate("delete from bot_map_learn");
        } catch (Exception e) {
            Logger.error("Bot: không xoá được bot_map_learn: " + e.getMessage() + "\n");
        }
    }
}
