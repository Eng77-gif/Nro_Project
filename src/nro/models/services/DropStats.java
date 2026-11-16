package nro.models.services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import nro.models.map.ItemMap;

/**
 * Bảng tỉ lệ rơi thật trên panel: đếm số lần quái / boss chết và đồ rơi ra (từ lúc bật server hoặc từ lúc bấm Đặt lại).
 * Đếm thẳng lúc rơi nên luôn khớp code, không cần ghi tay tỉ lệ của hơn 100 boss.
 * shortcut: chỉ giữ trong bộ nhớ, khởi động lại server là về 0; cần lâu dài thì lưu xuống database.
 */
public final class DropStats {

    private static final class Src {
        final AtomicLong kills = new AtomicLong();
        final Map<Integer, long[]> items = new ConcurrentHashMap<>(); // id -> {số lần rơi, tổng số lượng}
    }

    private static final Map<String, Src> MOB = new ConcurrentHashMap<>(), BOSS = new ConcurrentHashMap<>();
    private static volatile long since = System.currentTimeMillis();

    private DropStats() {
    }

    public static void mobKill(String mob, List<ItemMap> drops) {
        Src s = MOB.computeIfAbsent(mob == null ? "?" : mob, k -> new Src());
        s.kills.incrementAndGet();
        for (ItemMap it : drops) {
            if (it != null && it.itemTemplate != null && it.fromBoss == null) {
                add(s, it.itemTemplate.id, it.quantity);
            }
        }
    }

    public static void bossKill(String boss) {
        BOSS.computeIfAbsent(boss, k -> new Src()).kills.incrementAndGet();
    }

    /** Gọi trong ItemMap: đồ rơi ngay sau khi boss chết (zone.bossDropName). */
    public static void bossDrop(String boss, int itemId, int qty) {
        add(BOSS.computeIfAbsent(boss, k -> new Src()), itemId, qty);
    }

    private static void add(Src s, int id, int qty) {
        long[] c = s.items.computeIfAbsent(id, k -> new long[2]);
        synchronized (c) {
            c[0]++;
            c[1] += Math.max(1, qty);
        }
    }

    public static void reset() {
        MOB.clear();
        BOSS.clear();
        since = System.currentTimeMillis();
    }

    public static Map<String, Object> report() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("since", since);
        r.put("mobs", list(MOB));
        r.put("bosses", list(BOSS));
        return r;
    }

    private static List<Map<String, Object>> list(Map<String, Src> m) {
        List<Map<String, Object>> out = new ArrayList<>();
        m.forEach((name, s) -> {
            long k = s.kills.get();
            List<Map<String, Object>> items = new ArrayList<>();
            s.items.forEach((id, c) -> {
                var t = ItemService.gI().getTemplate(id.shortValue());
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("id", id);
                e.put("name", t != null ? t.name : "#" + id);
                e.put("icon", t != null ? t.iconID : 0);
                e.put("times", c[0]);
                e.put("qty", c[1]);
                e.put("rate", k > 0 ? c[0] * 100.0 / k : 0); // % số lần chết có rơi món này
                items.add(e);
            });
            items.sort(Comparator.comparingDouble(e -> -((Number) e.get("rate")).doubleValue()));
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("name", name);
            e.put("kills", k);
            e.put("items", items);
            out.add(e);
        });
        out.sort(Comparator.comparingLong(e -> -((Number) e.get("kills")).longValue()));
        return out;
    }
}
