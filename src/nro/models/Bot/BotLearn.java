package nro.models.Bot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import nro.models.data.LocalManager;
import nro.models.utils.Logger;

/**
 * Bot tự rút kinh nghiệm chọn map luyện tập (không cần model AI ngoài): mỗi lần rời 1 map, bot chấm điểm map đó = sức mạnh kiếm được
 * mỗi phút, chết bao nhiêu lần thì điểm giảm một nửa bấy nhiêu lần. Điểm trung bình trượt (mới nặng hơn cũ), dùng chung cho mọi bot
 * và lưu ở bảng bot_map_stat. Khi chọn map, bot rút vài map hợp sức (Bot.MapToPow) và lấy map điểm cao nhất; map chưa ai thử được
 * ưu tiên thử, và 1/5 số lần chọn ngẫu nhiên để còn khám phá.
 */
public final class BotLearn {

    private static final Map<Integer, double[]> SCORE = new ConcurrentHashMap<>(); // map id -> {điểm, số lần}
    private static volatile boolean loaded;

    private BotLearn() {
    }

    /** Ghi kết quả 1 lượt ở map. */
    static void record(int mapId, long powerGain, long millis, int deaths) {
        if (millis < 60_000 || mapId < 0) {
            return; // ở quá ngắn, chưa đủ để chấm
        }
        double s = Math.max(0, powerGain) / (millis / 60_000.0) / Math.pow(2, Math.min(deaths, 10));
        SCORE.compute(mapId, (k, v) -> v == null ? new double[]{s, 1} : new double[]{v[0] * 0.7 + s * 0.3, v[1] + 1});
    }

    /** Điểm của map; chưa ai thử thì cao nhất để bot thử. */
    static double score(int mapId) {
        double[] v = SCORE.get(mapId);
        return v == null ? Double.MAX_VALUE : v[0];
    }

    /** Các map điểm cao nhất (panel). */
    static java.util.List<java.util.Map<String, Object>> top(int n) {
        java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        SCORE.entrySet().stream().sorted((a, b) -> Double.compare(b.getValue()[0], a.getValue()[0])).limit(n).forEach(e -> {
            nro.models.map.Map m = nro.models.map.service.MapService.gI().getMapById(e.getKey());
            java.util.Map<String, Object> r = new java.util.LinkedHashMap<>();
            r.put("map", m != null ? m.mapName : "Map " + e.getKey());
            r.put("score", Math.round(e.getValue()[0]));
            r.put("n", (int) e.getValue()[1]);
            out.add(r);
        });
        return out;
    }

    static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        try (java.sql.Connection con = LocalManager.getConnection(); java.sql.Statement st = con.createStatement()) {
            st.executeUpdate("create table if not exists bot_map_stat (map_id int primary key, score double not null, n int not null)");
            try (java.sql.ResultSet rs = st.executeQuery("select map_id, score, n from bot_map_stat")) {
                while (rs.next()) {
                    SCORE.put(rs.getInt(1), new double[]{rs.getDouble(2), rs.getInt(3)});
                }
            }
        } catch (Exception e) {
            Logger.error("Bot: không đọc được bot_map_stat: " + e.getMessage() + "\n");
        }
    }

    static void save() {
        if (SCORE.isEmpty()) {
            return;
        }
        try (java.sql.Connection con = LocalManager.getConnection();
                java.sql.PreparedStatement ps = con.prepareStatement("replace into bot_map_stat (map_id, score, n) values (?, ?, ?)")) {
            for (Map.Entry<Integer, double[]> e : SCORE.entrySet()) {
                ps.setInt(1, e.getKey());
                ps.setDouble(2, e.getValue()[0]);
                ps.setInt(3, (int) e.getValue()[1]);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (Exception e) {
            Logger.error("Bot: không lưu được bot_map_stat: " + e.getMessage() + "\n");
        }
    }
}
