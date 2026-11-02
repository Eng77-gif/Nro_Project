package nro.models.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import nro.models.boss.Boss;
import nro.models.map.ItemMap;
import nro.models.player.Player;
import nro.models.utils.Logger;
import nro.models.utils.Util;

/**
 * Quà rơi thêm theo độ mạnh boss (máu tối đa): nguyên liệu farm (đá nâng cấp, ngọc rồng, sao pha lê, đậu thần,
 * mảnh bông tai...). Boss không rơi vàng, không cộng điểm sự kiện, không rơi đồ đua top.
 * Bậc 1 dưới 5 triệu HP, bậc 2 dưới 50 triệu, bậc 3 dưới 500 triệu, bậc 4 dưới 1,5 tỷ, bậc 5 từ 1,5 tỷ.
 * Bảng quà nằm ở boss_reward (sửa trên panel, trang "Thưởng boss").
 * Quà riêng của từng boss (đồ thần linh, ngọc rồng, cải trang...) vẫn giữ trong reward() của boss đó.
 *
 * @author Việt
 */
public class BossRewardService {

    private static BossRewardService instance;

    public static BossRewardService gI() {
        if (instance == null) {
            instance = new BossRewardService();
        }
        return instance;
    }

    public static final long[] TIER_HP = {5_000_000L, 50_000_000L, 500_000_000L, 1_500_000_000L};
    public static final String[] TIER_NAME = {"", "Bậc 1 (< 5 triệu HP)", "Bậc 2 (< 50 triệu HP)", "Bậc 3 (< 500 triệu HP)", "Bậc 4 (< 1,5 tỷ HP)", "Bậc 5 (từ 1,5 tỷ HP)"};

    /** Dòng thưởng: item_id -1 = vàng (rơi đất), -4 = điểm sự kiện; rate tính trên 10.000. */
    public static class Row {

        public int id, tier, itemId, qtyMin, qtyMax, rate, permRate;
        public String boss;
        public int[] days;
    }

    private final List<Row> rows = new ArrayList<>();

    public static int tierOf(Boss boss) {
        long hp = boss.nPoint == null ? 0 : boss.nPoint.hpMax;
        int t = 1;
        for (long h : TIER_HP) {
            if (hp >= h) {
                t++;
            }
        }
        return t;
    }

    public synchronized void load(Connection con) {
        rows.clear();
        try (PreparedStatement ps = con.prepareStatement("select * from boss_reward order by id"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Row r = new Row();
                r.id = rs.getInt("id");
                r.tier = rs.getInt("tier");
                r.boss = rs.getString("boss") == null ? "" : rs.getString("boss").trim().toLowerCase();
                r.itemId = rs.getInt("item_id");
                r.qtyMin = Math.max(1, rs.getInt("qty_min"));
                r.qtyMax = Math.max(r.qtyMin, rs.getInt("qty_max"));
                r.rate = rs.getInt("rate");
                r.permRate = rs.getInt("perm_rate");
                List<Integer> d = new ArrayList<>();
                for (String x : String.valueOf(rs.getString("hsd_days")).split(",")) {
                    try {
                        int v = Integer.parseInt(x.replaceAll("[^0-9]", ""));
                        if (v > 0) {
                            d.add(v);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
                r.days = d.stream().mapToInt(Integer::intValue).toArray();
                if (r.itemId >= 0 && (r.itemId >= nro.models.server.Manager.ITEM_TEMPLATES.size() || ItemService.gI().getTemplate(r.itemId) == null)) {
                    continue;
                }
                rows.add(r);
            }
            Logger.success("Successfully loaded boss reward (" + rows.size() + ")\n");
        } catch (Exception e) {
            Logger.error("Không tải được boss_reward: " + e.getMessage() + "\n");
        }
    }

    public void reload() {
        try (Connection con = nro.models.data.LocalManager.getConnection()) {
            load(con);
        } catch (Exception e) {
            Logger.error("Không tải được boss_reward: " + e.getMessage() + "\n");
        }
    }

    /** Gọi khi boss chết (sau reward() riêng của boss). */
    public void reward(Boss boss, Player pl) {
        if (pl == null || boss.zone == null || !pl.isPl() || pl.isBot) {
            return;
        }
        if (boss.id == nro.models.boss.BossID.BROLY_SUPER) {
            return; // SSJ Broly giữ nguyên quà riêng (vàng, mảnh Oozaru, trang bị...), không nhận quà theo bậc
        }
        int tier = tierOf(boss);
        String name = boss.name == null ? "" : boss.name.toLowerCase();
        // dòng riêng của boss (theo tên) thay thế dòng chung của bậc cùng loại vật phẩm
        List<Row> own = new ArrayList<>();
        for (Row r : rows) {
            if (!r.boss.isEmpty() && name.contains(r.boss)) {
                own.add(r);
            }
        }
        int dx = 0;
        for (Row r : rows) {
            boolean match = r.boss.isEmpty() ? r.tier == tier && own.stream().noneMatch(o -> o.itemId == r.itemId) : own.contains(r);
            if (!match || !Util.isTrue(Math.min(10000, Math.max(0, r.rate)), 10000)) {
                continue;
            }
            int qty = Util.nextInt(r.qtyMin, r.qtyMax);
            if (r.itemId == -4) {
                pl.event.addEventPoint(qty);
                Service.gI().sendThongBao(pl, "+" + qty + " điểm sự kiện (hạ " + boss.name + ")");
                continue;
            }
            int itemId = r.itemId == -1 ? 190 : r.itemId;
            int x = boss.location.x + (dx += 15) * (dx / 15 % 2 == 0 ? 1 : -1) / 2;
            ItemMap it = new ItemMap(boss.zone, itemId, qty, x, boss.zone.map.yPhysicInTop(x, boss.location.y - 24), pl.id);
            if (itemId != 190) {
                it.options.addAll(ItemService.gI().getListOptionItemShop((short) itemId));
                if (r.days.length > 0 && !Util.isTrue(r.permRate, 100)) {
                    it.options.removeIf(io -> io.optionTemplate != null && io.optionTemplate.id == 93);
                    it.options.add(new nro.models.item.Item.ItemOption(93, r.days[Util.nextInt(0, r.days.length - 1)]));
                }
            }
            Service.gI().dropItemMap(boss.zone, it);
        }
    }

    public synchronized List<Row> rows() {
        return new ArrayList<>(rows);
    }
}
