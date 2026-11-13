package nro.models.services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import nro.models.data.LocalManager;
import nro.models.database.PlayerExtraDAO;
import nro.models.item.Item;
import nro.models.player.Inventory;
import nro.models.player.Player;
import nro.models.server.Client;
import nro.models.utils.Logger;
import nro.models.utils.Util;

/**
 * Nội dung đợt Trung Thu / 20-10 2026: bóng bước chân (ô riêng), hộp quà mở ngẫu nhiên theo bảng
 * item_box_reward, đua top tặng bộ ngọc rồng cho Quy Lão và đổi Quà Trăng Tròn / Trăng Khuyết.
 */
public class Event2026Service {

    private static Event2026Service instance;

    public static Event2026Service gI() {
        if (instance == null) {
            instance = new Event2026Service();
        }
        return instance;
    }

    /** Bóng mờ (ô bóng bước chân) -> hiệu ứng quanh nhân vật (data/effdata). */
    public static final Map<Integer, Integer> FOOT_EFFECT = Map.of(
            1960, 508, // Bóng mờ tuyết rơi
            1971, 509, // Bóng mờ NRO
            2023, 513, // Bóng mờ sao may mắn
            2054, 514, // Bóng mờ trung thu
            2096, 518, // Bóng mờ hoa hồng đỏ
            2097, 519); // Bóng mờ hoa hồng băng
    /** Các vật phẩm mang ở ô bóng bước chân (Hào quang chân SPL10 dùng hiệu ứng riêng, xem SaoPhaLe10Service.effectId). */
    public static final Set<Integer> FOOT_IDS = java.util.stream.Stream.concat(FOOT_EFFECT.keySet().stream(),
            java.util.stream.Stream.of(nro.models.database.Dot56Migration.ITEM_HAO_QUANG_SPL10)).collect(java.util.stream.Collectors.toUnmodifiableSet());

    /** Hiệu ứng bóng mờ đang mang, -1 nếu không có. */
    public static int footEffectId(Player pl) {
        Item f = pl == null ? null : pl.itemFoot;
        if (f == null || !f.isNotNullItem()) {
            return -1;
        }
        return FOOT_EFFECT.getOrDefault((int) f.template.id, -1);
    }

    /**
     * Bóng mờ (bóng bước chân): nhân vật chạy / bay đi qua đâu thì vật (hoa hồng, bông tuyết...) rơi lại ở đó, nằm yên trên
     * map rồi nhỏ dần và biến mất. Gửi lệnh 113 (hiệu ứng cố định trên map) tại các điểm cách nhau FOOT_STEP px trên đoạn
     * đường từ vị trí cũ tới vị trí mới, vật nằm dưới chân; điểm đi qua lâu hơn dùng bản đã mờ hơn (1221 + 12k + 0..11).
     */
    private static final int[] TRAIL_BASE = {508, 509, 513, 514, 518, 519};
    private static final int FOOT_STEP = 8, FOOT_MAX = 36, FOOT_VARIANTS = 12; // dày hơn cho vệt liền, không khựng
    private static final long FRAME_MS = 33 * 3; // client chạy khoảng 30 nhịp / giây, mỗi bản bỏ 3 nhịp

    private static int footBase(int eff) {
        for (int k = 0; k < TRAIL_BASE.length; k++) {
            if (TRAIL_BASE[k] == eff) {
                return 1221 + FOOT_VARIANTS * k;
            }
        }
        return -1;
    }

    /** Đổi bóng mờ: tắt vệt đang hiện (vệt mới bật ở lần di chuyển sau). */
    private static void refreshFootEffect(Player pl, int before) {
        stopTrail(pl);
        if (before >= 0 && pl.zone != null) {
            Service.gI().removeEffPlayer(pl, before); // hiệu ứng luôn hiện của bản cũ
        }
    }

    /** Gọi khi người chơi gửi vị trí mới. */
    public static void onMove(Player pl, int oldX, int oldY, int x, int y) {
        if (pl == null || pl.isPet || pl.isBot || pl.zone == null || (oldX == x && oldY == y)) {
            return;
        }
        int base = footBase(footEffectId(pl));
        long now = System.currentTimeMillis();
        long dt = now - pl.trailLastMove;
        dt = dt > 500 || dt < 0 ? 200 : dt; // vừa đứng im rồi đi tiếp: coi như đoạn đường mới đi
        pl.trailLastMove = now;
        if (base < 0) {
            return;
        }
        double dx = x - oldX, dy = y - oldY, len = Math.hypot(dx, dy);
        if (len > 400 || pl.trailZone != pl.zone) { // dịch chuyển / đổi khu: chỉ đặt 1 điểm ở chỗ mới
            pl.trailZone = pl.zone;
            pl.footAcc = 0;
            nro.models.services_func.EffectMapService.gI().sendEffectMapToAllInMap(pl.zone, base, 2, 1, x, y, 1);
            return;
        }
        double d = FOOT_STEP - pl.footAcc;
        int n = 0;
        java.util.List<int[]> pts = new java.util.ArrayList<>();
        while (d <= len && n < FOOT_MAX) {
            pts.add(new int[]{(int) Math.round(oldX + dx * d / len), (int) Math.round(oldY + dy * d / len), (int) Math.round(d)});
            d += FOOT_STEP;
            n++;
        }
        pl.footAcc = n == FOOT_MAX ? 0 : (int) (len - (d - FOOT_STEP));
        // luôn có 1 điểm ngay dưới chân (mới nhất) để vệt bám sát người chơi, không bị tụt lại phía sau
        if (pts.isEmpty() || pts.get(pts.size() - 1)[2] < len - FOOT_STEP / 2.0) {
            pts.add(new int[]{x, y, (int) Math.round(len)});
            pl.footAcc = 0;
        }
        for (int[] p : pts) {
            long age = (long) (dt * (1 - p[2] / len)); // điểm gần chỗ cũ đã đi qua lâu hơn
            int v = (int) Math.min(FOOT_VARIANTS - 1, age / FRAME_MS);
            int fx = p[0], fy = pl.zone.map.yPhysicInTop(p[0], p[1] - 24); // bám mặt đất dưới điểm đi qua
            if (Math.abs(fy - p[1]) > 48) {
                fy = p[1]; // đang bay / không có đất gần: giữ độ cao của chân
            }
            nro.models.services_func.EffectMapService.gI().sendEffectMapToAllInMap(pl.zone, base + v, 2, 1, fx, fy, 1);
        }
    }

    /** Gọi trong vòng cập nhật người chơi: đứng im thì tắt vệt. */
    public static void updateTrail(Player pl) {
        if (pl.trailEff >= 0 && System.currentTimeMillis() - pl.trailLastMove > 750) { // vệt kiểu cũ (gắn theo người) nếu còn
            stopTrail(pl);
        }
    }

    public static void stopTrail(Player pl) {
        if (pl.trailEff >= 0) {
            if (pl.zone != null && pl.trailZone == pl.zone) {
                Service.gI().removeEffPlayer(pl, pl.trailEff);
            }
            pl.trailEff = -1;
            pl.trailZone = null;
        }
    }

    /** Người mới vào map thấy vệt của người đang chạy. */
    public static int currentTrail(Player pl) {
        return pl.trailEff >= 0 && pl.trailZone == pl.zone ? pl.trailEff : -1;
    }

    /** Hộp ra vật phẩm chỉ số tối đa: Trứng Xên Vàng */
    public static final Set<Integer> MAX_STAT_BOXES = Set.of(2084);
    public static final int VAN_RONG_7_SAO = 2100;
    public static final int CAPSULE_VO = 2111;
    public static final int MANH_CAPSULE_VO = 1909;
    public static final int KHOA_BONG_TAI_3 = 2046;
    public static final int QUA_TRANG_TRON = 2088;
    public static final int QUA_TRANG_KHUYET = 2087;
    public static final int TRANG_TRON_POINT = 99;
    public static final long TRANG_KHUYET_GOLD = 20_000_000L;
    /** Số ngày giữ aura thưởng của top 1 - 3 */
    public static final int TOP_AURA_DAYS = 30;
    private static final int[] TOP_AURA = {122, 121, 120};

    // ------------------------------------------------------------------ bóng bước chân

    public boolean isFoot(Item item) {
        return item != null && item.isNotNullItem() && FOOT_IDS.contains((int) item.template.id);
    }

    public void equipFoot(Player pl, Item item) {
        int index = pl.inventory.itemsBag.indexOf(item);
        if (index < 0) {
            return;
        }
        Item old = pl.itemFoot;
        int effTruoc = footEffectId(pl);
        int spl10Truoc = SaoPhaLe10Service.effectId(pl);
        pl.itemFoot = item;
        pl.inventory.itemsBag.set(index, old != null && old.isNotNullItem() ? old : ItemService.gI().createItemNull());
        InventoryService.gI().sendItemBags(pl);
        InventoryService.gI().sendItemBody(pl); // ô bóng mờ cuối danh sách trang bị
        Service.gI().point(pl);
        refreshFootEffect(pl, effTruoc);
        Service.gI().refreshEffect(pl, spl10Truoc, SaoPhaLe10Service.effectId(pl));
    }

    public void unequipFoot(Player pl) {
        if (pl.itemFoot == null) {
            Service.gI().sendThongBao(pl, "Bạn chưa mang bóng bước chân");
            return;
        }
        if (InventoryService.gI().getCountEmptyBag(pl) <= 0) {
            Service.gI().sendThongBao(pl, "Hành trang không còn chỗ trống");
            return;
        }
        Item foot = pl.itemFoot;
        int effTruoc = footEffectId(pl);
        int spl10Truoc = SaoPhaLe10Service.effectId(pl);
        pl.itemFoot = null;
        InventoryService.gI().addItemBag(pl, foot);
        InventoryService.gI().sendItemBags(pl);
        InventoryService.gI().sendItemBody(pl);
        Service.gI().point(pl);
        refreshFootEffect(pl, effTruoc);
        Service.gI().refreshEffect(pl, spl10Truoc, SaoPhaLe10Service.effectId(pl));
    }

    // ------------------------------------------------------------------ hộp quà

    /** box_id -> các dòng phần thưởng */
    private final Map<Integer, List<BoxReward>> boxes = new ConcurrentHashMap<>();

    private static class BoxReward {

        int itemId, qtyMin, qtyMax, weight, permRate;
        /** Bảo hiểm: mở đủ số lần này mà chưa ra thì lần đó chắc chắn ra (0 = không có). */
        int pity;
        int[] days;
    }

    public void loadBoxes(Connection con) {
        boxes.clear();
        int n = 0;
        try (PreparedStatement p = con.prepareStatement("select * from item_box_reward order by id");
                ResultSet r = p.executeQuery()) {
            while (r.next()) {
                BoxReward b = new BoxReward();
                b.itemId = r.getInt("item_id");
                b.qtyMin = Math.max(1, r.getInt("qty_min"));
                b.qtyMax = Math.max(b.qtyMin, r.getInt("qty_max"));
                b.weight = r.getInt("weight");
                b.permRate = r.getInt("perm_rate");
                try {
                    b.pity = Math.max(0, r.getInt("pity"));
                } catch (Exception noColumn) {
                    b.pity = 0;
                }
                String hsd = r.getString("hsd_days");
                List<Integer> days = new ArrayList<>();
                if (hsd != null) {
                    for (String s : hsd.split(",")) {
                        try {
                            int d = Integer.parseInt(s.replaceAll("[^0-9]", ""));
                            if (d > 0) {
                                days.add(d);
                            }
                        } catch (NumberFormatException badText) {
                            // "7 ngày", ô trống... bỏ qua thay vì làm hỏng cả bảng hộp quà
                        }
                    }
                }
                b.days = days.stream().mapToInt(Integer::intValue).toArray();
                if (b.weight <= 0 || (b.itemId >= 0 && (b.itemId >= nro.models.server.Manager.ITEM_TEMPLATES.size() || ItemService.gI().getTemplate(b.itemId).name.isEmpty()))) {
                    continue;
                }
                boxes.computeIfAbsent(r.getInt("box_id"), k -> new ArrayList<>()).add(b);
                n++;
            }
            Logger.success("Successfully loaded item box reward (" + n + ")\n");
        } catch (Exception e) {
            Logger.error("Không tải được item_box_reward: " + e.getMessage() + "\n");
        }
    }

    public void reloadBoxes() {
        try (Connection con = nro.models.data.LocalManager.getConnection()) {
            loadBoxes(con);
        } catch (Exception e) {
            Logger.error("Không tải được item_box_reward: " + e.getMessage() + "\n");
        }
    }

    public boolean isBox(Item item) {
        return item != null && item.isNotNullItem() && boxes.containsKey((int) item.template.id);
    }

    public void openBox(Player pl, Item box) {
        if (InventoryService.gI().getCountEmptyBag(pl) <= 0) {
            Service.gI().sendThongBao(pl, "Hành trang không còn chỗ trống");
            return;
        }
        String boxName = box.template.name; // hộp cuối bị xóa (template = null) khi trừ số lượng
        short boxIcon = box.template.iconID;
        short[] icon = {-1};
        String got = openBoxById(pl, box.template.id, icon);
        if (got != null) {
            InventoryService.gI().subQuantityItemsBag(pl, box, 1);
            InventoryService.gI().sendItemBags(pl);
            if (icon[0] >= 0) {
                // hiệu ứng mở giống Capsule kì bí: icon hộp -> icon quà
                nro.models.combine.CombineService.gI().sendEffectOpenItem(pl, boxIcon, icon[0]);
            }
            // không báo "Mở ... nhận được ..." nữa: hiệu ứng mở hộp đã hiện món nhận được
        }
    }

    /** Đồ theo hành tinh (vd Nón Tết Trái Đất / Namếc / Xayda cùng tên): đổi sang bản đúng hành tinh của người mở. */
    private static int genderVariant(int itemId, Player pl) {
        nro.models.player_system.Template.ItemTemplate t = ItemService.gI().getTemplate(itemId);
        if (t == null || pl == null || t.gender == 3 || t.gender == pl.gender) {
            return itemId;
        }
        for (nro.models.player_system.Template.ItemTemplate o : nro.models.server.Manager.ITEM_TEMPLATES) {
            if (o.type == t.type && o.gender == pl.gender && o.name.equals(t.name) && Math.abs(o.id - t.id) <= 6) {
                return o.id;
            }
        }
        return itemId;
    }

    /**
     * Quà "ngẫu nhiên theo loại" (item_id trong item_box_reward = -100 - loại): -100 áo, -101 quần, -102 găng, -103 giày, -104 rada,
     * -107 sách kỹ năng, -120 đồ Thần Linh; lấy đồ đúng hành tinh, cấp 1 - 10 (đồ thường, không phải đồ thần / đồ sự kiện).
     */
    private static int randomByType(int type, Player pl) {
        List<Integer> c = new ArrayList<>();
        if (type == 20) { // -120: 1 món đồ Thần Linh (áo, quần, găng, giày đúng hành tinh, nhẫn)
            for (int id = 555; id <= 567; id++) {
                nro.models.player_system.Template.ItemTemplate t = ItemService.gI().getTemplate(id);
                if (t != null && (pl == null || t.gender == pl.gender || t.gender == 3)) {
                    c.add(id);
                }
            }
            return c.isEmpty() ? -1 : c.get(Util.nextInt(0, c.size() - 1));
        }
        for (nro.models.player_system.Template.ItemTemplate t : nro.models.server.Manager.ITEM_TEMPLATES) {
            if (t.type == type && (pl == null || t.gender == pl.gender || t.gender == 3) && t.level >= 1 && t.level <= 10
                    && !t.name.toLowerCase().contains("thần") && !t.name.toLowerCase().contains("thiên sứ")) {
                c.add((int) t.id);
            }
        }
        return c.isEmpty() ? -1 : c.get(Util.nextInt(0, c.size() - 1));
    }

    /** Rút 1 phần quà của hộp boxId và cho vào hành trang; trả về tên quà (null nếu hộp không có quà). */
    public String openBoxById(Player pl, int boxId) {
        return openBoxById(pl, boxId, null);
    }

    /** Như trên, iconOut[0] nhận icon của quà (để làm hiệu ứng mở hộp). */
    public String openBoxById(Player pl, int boxId, short[] iconOut) {
        List<BoxReward> list = boxes.get(boxId);
        if (list == null || list.isEmpty()) {
            return null;
        }
        int total = 0;
        for (BoxReward b : list) {
            total += b.weight;
        }
        int roll = Util.nextInt(0, total - 1);
        BoxReward pick = list.get(list.size() - 1);
        for (BoxReward b : list) {
            roll -= b.weight;
            if (roll < 0) {
                pick = b;
                break;
            }
        }
        boolean baoHiem = false;
        if (pl != null && pl.isPl() && list.stream().anyMatch(b -> b.pity > 0)) {
            BoxReward forced = applyPity(pl, boxId, list, pick);
            if (forced != null) {
                pick = forced;
                baoHiem = true;
            }
        }
        int qty = Util.nextInt(pick.qtyMin, pick.qtyMax);
        String got;
        int rewardId = pick.itemId <= -100 ? randomByType(-100 - pick.itemId, pl) : genderVariant(pick.itemId, pl);
        if (pick.itemId <= -100 && rewardId < 0) {
            return null;
        }
        if (pick.itemId < 0 && pick.itemId > -100) {
            got = giveCurrency(pl, pick.itemId, qty);
            if (iconOut != null) {
                iconOut[0] = (short) (pick.itemId == -1 ? 930 : pick.itemId == -2 ? 932 : 7743);
            }
        } else {
            int days = 0;
            if (pick.days.length > 0 && !Util.isTrue(pick.permRate, 100)) {
                days = pick.days[Util.nextInt(0, pick.days.length - 1)];
            }
            Item it = createItem(rewardId, qty, days);
            if (MAX_STAT_BOXES.contains(boxId)) { // Trứng Xên Vàng: pet chỉ số tối đa
                it.itemOptions.removeIf(io -> io.optionTemplate == null || io.optionTemplate.id != 93);
                it.itemOptions.addAll(0, ItemService.gI().getMaxOptions((short) rewardId));
            }
            if (iconOut != null) {
                iconOut[0] = it.template.iconID;
            }
            InventoryService.gI().addItemBag(pl, it);
            AnnounceService.gI().onOpenBox(pl, boxId, it);
            got = (qty > 1 ? "x" + qty + " " : "") + it.template.name + (days > 0 ? " (" + days + " ngày)" : (pick.days.length > 0 ? " (vĩnh viễn)" : ""));
        }
        return baoHiem ? got + " [bảo hiểm]" : got;
    }

    /**
     * Bảo hiểm hộp quà: đếm số lần mở chưa ra từng món có bảo hiểm (bảng box_pity). Món nào đạt đủ số lần thì lần này
     * chắc chắn ra món đó. Ra món nào thì đếm lại từ 0. Trả về món bị ép ra (null nếu không có).
     */
    private BoxReward applyPity(Player pl, int boxId, List<BoxReward> list, BoxReward pick) {
        try (Connection con = nro.models.data.LocalManager.getConnection()) {
            Map<Integer, Integer> cnt = new java.util.HashMap<>();
            try (PreparedStatement ps = con.prepareStatement("select item_id, cnt from box_pity where player_id = ? and box_id = ?")) {
                ps.setLong(1, pl.id);
                ps.setInt(2, boxId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        cnt.put(rs.getInt(1), rs.getInt(2));
                    }
                }
            }
            BoxReward forced = null;
            for (BoxReward b : list) {
                if (b.pity > 0 && b != pick && forced == null && cnt.getOrDefault(b.itemId, 0) + 1 >= b.pity) {
                    forced = b;
                }
            }
            BoxReward got = forced != null ? forced : pick;
            try (PreparedStatement ps = con.prepareStatement("insert into box_pity (player_id, box_id, item_id, cnt) values (?, ?, ?, ?) on duplicate key update cnt = values(cnt)")) {
                for (BoxReward b : list) {
                    if (b.pity <= 0) {
                        continue;
                    }
                    ps.setLong(1, pl.id);
                    ps.setInt(2, boxId);
                    ps.setInt(3, b.itemId);
                    ps.setInt(4, b == got ? 0 : cnt.getOrDefault(b.itemId, 0) + 1);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return forced;
        } catch (Exception e) {
            Logger.error("Bảo hiểm hộp quà lỗi: " + e.getMessage() + "\n");
            return null;
        }
    }

    public String giveCurrency(Player pl, int type, int qty) {
        switch (type) {
            case -1 -> {
                pl.inventory.gold = Math.min(Inventory.LIMIT_GOLD, pl.inventory.gold + qty);
                Service.gI().sendMoney(pl);
                return Util.numberToMoney(qty) + " vàng";
            }
            case -2 -> {
                pl.inventory.gem += qty;
                Service.gI().sendMoney(pl);
                return qty + " ngọc xanh";
            }
            default -> {
                pl.inventory.ruby += qty;
                Service.gI().sendMoney(pl);
                return qty + " hồng ngọc";
            }
        }
    }

    public Item createItem(int id, int qty, int days) {
        Item it = ItemService.gI().createNewItem((short) id, qty);
        it.itemOptions.addAll(ItemService.gI().getListOptionItemShop((short) id));
        if (ItemService.gI().isTrainArmor(it) && it.itemOptions.stream().noneMatch(io -> io.optionTemplate.id == 9)) {
            it.itemOptions.add(new Item.ItemOption(9, 0)); // Hiệu lực trong # phút
        }
        if (days > 0) {
            it.itemOptions.removeIf(io -> io.optionTemplate.id == 93);
            it.itemOptions.add(new Item.ItemOption(93, days));
        }
        return it;
    }

    // ------------------------------------------------------------------ Quà Trăng Tròn / Trăng Khuyết

    public void exchangeTrangTron(Player pl) {
        if (pl.event.getEventPoint() < TRANG_TRON_POINT) {
            Service.gI().sendThongBao(pl, "Cần " + TRANG_TRON_POINT + " điểm sự kiện để đổi Quà Trăng Tròn");
            return;
        }
        if (InventoryService.gI().getCountEmptyBag(pl) <= 0) {
            Service.gI().sendThongBao(pl, "Hành trang không còn chỗ trống");
            return;
        }
        pl.event.subEventPoint(TRANG_TRON_POINT);
        InventoryService.gI().addItemBag(pl, createItem(QUA_TRANG_TRON, 1, 0));
        InventoryService.gI().sendItemBags(pl);
        Service.gI().sendThongBao(pl, "Đổi thành công Quà Trăng Tròn");
    }

    public void exchangeTrangKhuyet(Player pl) {
        if (pl.inventory.gold < TRANG_KHUYET_GOLD) {
            Service.gI().sendThongBao(pl, "Cần " + Util.numberToMoney(TRANG_KHUYET_GOLD) + " vàng để đổi Quà Trăng Khuyết");
            return;
        }
        if (InventoryService.gI().getCountEmptyBag(pl) <= 0) {
            Service.gI().sendThongBao(pl, "Hành trang không còn chỗ trống");
            return;
        }
        pl.inventory.gold -= TRANG_KHUYET_GOLD;
        Service.gI().sendMoney(pl);
        InventoryService.gI().addItemBag(pl, createItem(QUA_TRANG_KHUYET, 1, 0));
        InventoryService.gI().sendItemBags(pl);
        Service.gI().sendThongBao(pl, "Đổi thành công Quà Trăng Khuyết");
    }

    // ------------------------------------------------------------------ đua top Quy Lão

    /** Tặng bộ ngọc rồng 1 - 7 sao (id 14 - 20): mỗi bộ được 1 điểm. */
    public synchronized void giveDragonBallSet(Player pl, boolean all) {
        int sets = Integer.MAX_VALUE;
        Item[] balls = new Item[7];
        for (int i = 0; i < 7; i++) {
            balls[i] = InventoryService.gI().findItemBag(pl, 14 + i);
            sets = Math.min(sets, balls[i] == null ? 0 : balls[i].quantity);
        }
        if (sets <= 0) {
            Service.gI().sendThongBao(pl, "Con cần đủ 1 bộ ngọc rồng từ 1 đến 7 sao");
            return;
        }
        if (!all) {
            sets = 1;
        }
        for (Item b : balls) {
            InventoryService.gI().subQuantityItemsBag(pl, b, sets);
        }
        PlayerExtraDAO.addQlPoints(pl, sets);
        InventoryService.gI().sendItemBags(pl);
        Service.gI().sendThongBao(pl, "Đã tặng " + sets + " bộ ngọc rồng, điểm đua top hiện tại: " + pl.qlPoints);
    }

    public String topText() {
        StringBuilder sb = new StringBuilder("|1|Top tặng bộ ngọc rồng\n");
        List<Object[]> top = PlayerExtraDAO.topQl(10);
        if (top.isEmpty()) {
            sb.append("Chưa có ai tham gia");
        }
        for (int i = 0; i < top.size(); i++) {
            sb.append("|").append(i < 3 ? 7 : 0).append("|").append(i + 1).append(". ").append(top.get(i)[1]).append(": ").append(top.get(i)[2]).append(" điểm\n");
        }
        return sb.toString();
    }

    /**
     * Admin: chốt top 100, lưu hạng thưởng cho từng người (nhận khi đăng nhập / ngay nếu đang online)
     * rồi đưa điểm về 0 cho mùa mới.
     */
    public synchronized int closeRace(Player admin) {
        try {
            return doCloseRace();
        } catch (Exception e) {
            Logger.logException(Event2026Service.class, e, "Lỗi chốt top Quy Lão");
            return 0;
        }
    }

    private int doCloseRace() throws Exception {
        // lưu điểm mới nhất của người đang online trước khi chốt
        for (Player p : new ArrayList<>(Client.gI().getPlayers())) {
            if (p != null && p.qlPoints > 0) {
                PlayerExtraDAO.save(p);
            }
        }
        List<Object[]> top = PlayerExtraDAO.topQl(100);
        for (int i = 0; i < top.size(); i++) {
            int playerId = (int) top.get(i)[0];
            int rank = i + 1;
            Player online = Client.gI().getPlayer((long) playerId);
            if (online != null) {
                online.qlRewardRank = rank;
            }
            LocalManager.executeUpdate("update player_extra set ql_reward_rank = ? where player_id = ?", rank, playerId);
        }
        LocalManager.executeUpdate("update player_extra set ql_points = 0");
        for (Player p : new ArrayList<>(Client.gI().getPlayers())) {
            if (p != null) {
                p.qlPoints = 0;
                deliverRaceReward(p);
            }
        }
        Logger.success("Đã chốt top Quy Lão: " + top.size() + " người\n");
        return top.size();
    }

    /** Phát quà top đang chờ (gọi khi vào game và khi chốt top). */
    public void deliverRaceReward(Player pl) {
        int rank = pl.qlRewardRank;
        if (rank <= 0) {
            return;
        }
        List<Item> items = raceRewards(rank);
        if (InventoryService.gI().getCountEmptyBag(pl) < items.size()) {
            Service.gI().sendThongBao(pl, "Bạn có quà đua top Quy Lão (hạng " + rank + "), cần " + items.size() + " ô trống hành trang để nhận");
            return;
        }
        pl.qlRewardRank = 0;
        for (Item it : items) {
            InventoryService.gI().addItemBag(pl, it);
        }
        if (rank <= 3) {
            pl.auraReward = TOP_AURA[rank - 1];
            pl.auraRewardUntil = System.currentTimeMillis() + TOP_AURA_DAYS * 86_400_000L;
            Service.gI().Send_Caitrang(pl);
        }
        PlayerExtraDAO.save(pl);
        InventoryService.gI().sendItemBags(pl);
        Service.gI().sendThongBao(pl, "Chúc mừng bạn đạt hạng " + rank + " đua top tặng ngọc rồng Quy Lão, quà đã được gửi vào hành trang");
    }

    private List<Item> raceRewards(int rank) {
        List<Item> l = new ArrayList<>();
        if (rank <= 3) {
            int k = rank - 1;
            l.add(createItem(VAN_RONG_7_SAO, 1, 0));
            l.add(createItem(1283, new int[]{70, 60, 50}[k], 0)); // Cuốn sách cũ
            l.add(createItem(1559, new int[]{3, 2, 1}[k], 0)); // Capsule 1 món kích hoạt
            l.add(createItem(CAPSULE_VO, new int[]{3, 2, 1}[k], 0));
            l.add(createItem(1868, new int[]{10000, 5000, 1000}[k], 0)); // Mảnh vỡ bông tai cấp 3
            l.add(createItem(KHOA_BONG_TAI_3, new int[]{10, 5, 2}[k], 0));
            if (rank == 1) {
                l.add(createItem(2006, 1, 0)); // Giáp tập luyện cấp 5
            }
        } else if (rank <= 10) {
            l.add(createItem(VAN_RONG_7_SAO, 1, 0));
            l.add(createItem(1283, 20, 0));
            l.add(createItem(1690, 50, 0)); // Phiếu đổi Capsule
            l.add(createItem(MANH_CAPSULE_VO, 100, 0));
        } else if (rank <= 50) {
            l.add(createItem(VAN_RONG_7_SAO, 1, 90));
            l.add(createItem(1283, 10, 0));
            l.add(createItem(1690, 20, 0));
            l.add(createItem(MANH_CAPSULE_VO, 20, 0));
        } else {
            l.add(createItem(VAN_RONG_7_SAO, 1, 60));
            l.add(createItem(1283, 5, 0));
            l.add(createItem(1690, 10, 0));
        }
        return l;
    }

    // ------------------------------------------------------------------ Hộp đen bí ẩn (NPC Chi Chi, đua top mua hộp)

    public static final int HOP_DEN_BI_AN = 2095;
    public static final int HOP_DEN_GIA_NGOC = 20;

    /** Mua Hộp đen bí ẩn bằng ngọc xanh; mỗi hộp mua được tính 1 điểm đua top. */
    public synchronized void buyHopDen(Player pl, int qty) {
        long cost = (long) HOP_DEN_GIA_NGOC * qty;
        if (pl.inventory.gem < cost) {
            Service.gI().sendThongBao(pl, "Cần " + cost + " ngọc để mua " + qty + " Hộp đen bí ẩn");
            return;
        }
        Item box = createItem(HOP_DEN_BI_AN, qty, 0);
        if (!InventoryService.gI().addItemBag(pl, box)) {
            Service.gI().sendThongBao(pl, "Hành trang không còn chỗ trống");
            return;
        }
        pl.inventory.gem -= (int) cost;
        pl.hdbaCount += qty;
        PlayerExtraDAO.save(pl);
        Service.gI().sendMoney(pl);
        InventoryService.gI().sendItemBags(pl);
        Service.gI().sendThongBao(pl, "Đã mua " + qty + " Hộp đen bí ẩn, điểm đua top: " + pl.hdbaCount);
    }

    public String topHopDenText() {
        StringBuilder sb = new StringBuilder("|1|Top mua Hộp đen bí ẩn\n");
        List<Object[]> top = PlayerExtraDAO.top("hdba_count", 10);
        if (top.isEmpty()) {
            sb.append("Chưa có ai tham gia");
        }
        for (int i = 0; i < top.size(); i++) {
            sb.append("|").append(i < 3 ? 7 : 0).append("|").append(i + 1).append(". ").append(top.get(i)[1]).append(": ").append(top.get(i)[2]).append(" hộp\n");
        }
        return sb.toString();
    }

    /** Admin: chốt top mua Hộp đen bí ẩn (top 10 nhận quà), đưa điểm về 0. */
    public synchronized int closeHopDenRace() {
        try {
            for (Player p : new ArrayList<>(Client.gI().getPlayers())) {
                if (p != null && p.hdbaCount > 0) {
                    PlayerExtraDAO.save(p);
                }
            }
            List<Object[]> top = PlayerExtraDAO.top("hdba_count", 10);
            for (int i = 0; i < top.size(); i++) {
                int playerId = (int) top.get(i)[0];
                Player online = Client.gI().getPlayer((long) playerId);
                if (online != null) {
                    online.hdbaRewardRank = i + 1;
                }
                LocalManager.executeUpdate("update player_extra set hdba_reward_rank = ? where player_id = ?", i + 1, playerId);
            }
            LocalManager.executeUpdate("update player_extra set hdba_count = 0");
            for (Player p : new ArrayList<>(Client.gI().getPlayers())) {
                if (p != null) {
                    p.hdbaCount = 0;
                    deliverHopDenReward(p);
                }
            }
            Logger.success("Đã chốt top Hộp đen bí ẩn: " + top.size() + " người\n");
            return top.size();
        } catch (Exception e) {
            Logger.logException(Event2026Service.class, e, "Lỗi chốt top Hộp đen bí ẩn");
            return 0;
        }
    }

    public void deliverHopDenReward(Player pl) {
        int rank = pl.hdbaRewardRank;
        if (rank <= 0) {
            return;
        }
        List<Item> items = new ArrayList<>();
        if (rank <= 3) {
            int k = rank - 1;
            if (rank == 1) {
                items.add(createItem(1978, 1, 180)); // Danh hiệu Đón Xuân 180 ngày
            }
            items.add(createItem(987, new int[]{100, 70, 50}[k], 0)); // Đá bảo vệ
            items.add(createItem(1559, new int[]{3, 2, 1}[k], 0)); // Capsule 1 món kích hoạt
            items.add(createItem(CAPSULE_VO, new int[]{3, 2, 1}[k], 0));
            if (rank == 1) {
                Item bt3 = createItem(1884, 1, 0); // Bông tai Porata cấp 3
                bt3.itemOptions.removeIf(io -> io.optionTemplate.id == 72);
                bt3.itemOptions.add(new Item.ItemOption(72, 3));
                items.add(bt3);
                items.add(createItem(2006, 1, 0)); // Giáp tập luyện cấp 5
            }
        } else {
            items.add(createItem(987, 20, 0));
            items.add(createItem(CAPSULE_VO, 1, 0));
        }
        if (InventoryService.gI().getCountEmptyBag(pl) < items.size()) {
            Service.gI().sendThongBao(pl, "Bạn có quà đua top Hộp đen bí ẩn (hạng " + rank + "), cần " + items.size() + " ô trống hành trang để nhận");
            return;
        }
        pl.hdbaRewardRank = 0;
        for (Item it : items) {
            InventoryService.gI().addItemBag(pl, it);
        }
        PlayerExtraDAO.save(pl);
        InventoryService.gI().sendItemBags(pl);
        Service.gI().sendThongBao(pl, "Chúc mừng bạn đạt hạng " + rank + " đua top mua Hộp đen bí ẩn, quà đã được gửi vào hành trang");
    }

    // ------------------------------------------------------------------ SKH Thần hủy diệt Beerus (đồ cấp 12)

    public static final int SKH_BEERUS_BOX = 2102; // Set kích hoạt Beerus 1 món tự chọn
    /** [hành tinh][Áo, Quần, Găng, Giày]: đồ cấp 12 (jean Calic / vàng Zealot / lưỡng long) */
    private static final int[][] SKH_BEERUS_GEAR = {{233, 245, 257, 269}, {237, 249, 261, 273}, {241, 253, 265, 277}};
    private static final int RADA_CAP_12 = 281;

    public Item createSkhBeerus(int tempId) {
        Item it = createItem(tempId, 1, 0);
        addSkhBeerusOptions(it.itemOptions);
        return it;
    }

    /** Dòng set Beerus (115), mô tả 2/4/5 món (163, 167, 168) và không giao dịch (30). */
    public static void addSkhBeerusOptions(List<Item.ItemOption> options) {
        options.add(new Item.ItemOption(115, 0));
        options.add(new Item.ItemOption(163, 0));
        options.add(new Item.ItemOption(167, 0));
        options.add(new Item.ItemOption(168, 0));
        options.add(new Item.ItemOption(30, 0));
    }

    public static int skhBeerusGear(int gender, int type) {
        return type == 4 ? RADA_CAP_12 : SKH_BEERUS_GEAR[Math.min(Math.max(gender, 0), 2)][type];
    }

    // ------------------------------------------------------------------ SKH Thần hủy diệt Champa (boss Baby rơi)

    /** [hành tinh][Áo, Quần, Găng, Giày, Rada][cấp 1 - 8] */
    private static final int[][][] SKH_CHAMPA_GEAR = {
        {{0, 3, 33, 34, 136, 137, 138, 139}, {6, 9, 35, 36, 140, 141, 142, 143}, {21, 24, 37, 38, 144, 145, 146, 147},
            {27, 30, 39, 40, 148, 149, 150, 151}, {12, 57, 58, 59, 184, 185, 186, 187}},
        {{1, 4, 41, 42, 152, 153, 154, 155}, {7, 10, 43, 44, 156, 157, 158, 159}, {22, 25, 45, 46, 160, 161, 162, 163},
            {28, 31, 47, 48, 164, 165, 166, 167}, {12, 57, 58, 59, 184, 185, 186, 187}},
        {{2, 5, 49, 50, 168, 169, 170, 171}, {8, 11, 51, 52, 172, 173, 174, 175}, {23, 26, 53, 54, 176, 177, 178, 179},
            {29, 32, 55, 56, 180, 181, 182, 183}, {12, 57, 58, 59, 184, 185, 186, 187}}};

    /** 1 món Set Thần hủy diệt Champa: hành tinh, loại đồ, cấp ngẫu nhiên. */
    public static int randomSkhChampaGear() {
        int[][] g = SKH_CHAMPA_GEAR[nro.models.utils.Util.nextInt(0, 2)];
        int[] t = g[nro.models.utils.Util.nextInt(0, 4)];
        return t[nro.models.utils.Util.nextInt(0, t.length - 1)];
    }

    /** Dòng set Champa (257), mô tả 2/4/5 món (258, 259, 260). */
    public static void addSkhChampaOptions(List<Item.ItemOption> options) {
        options.add(new Item.ItemOption(257, 0));
        options.add(new Item.ItemOption(258, 0));
        options.add(new Item.ItemOption(259, 0));
        options.add(new Item.ItemOption(260, 0));
    }

    public void openSkhBeerusMenu(Player pl) {
        nro.models.map.service.NpcService.gI().createMenuConMeo(pl, SKH_BEERUS_BOX, -1,
                "Chọn 1 món Set Thần hủy diệt Beerus", "Áo", "Quần", "Găng", "Giày", "Rada", "Đóng");
    }

    public void openSkhBeerusBox(Player pl, int select) {
        if (select < 0 || select > 4) {
            return;
        }
        Item box = InventoryService.gI().findItemBag(pl, SKH_BEERUS_BOX);
        if (box == null) {
            return;
        }
        if (InventoryService.gI().getCountEmptyBag(pl) <= 0) {
            Service.gI().sendThongBao(pl, "Hành trang không còn chỗ trống");
            return;
        }
        Item it = createSkhBeerus(skhBeerusGear(pl.gender, select));
        InventoryService.gI().subQuantityItemsBag(pl, box, 1);
        InventoryService.gI().addItemBag(pl, it);
        InventoryService.gI().sendItemBags(pl);
        Service.gI().sendThongBao(pl, "Bạn nhận được " + it.template.name + " (Set Thần hủy diệt Beerus)");
    }
}
