package nro.models.panel;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nro.models.data.DataGame;
import nro.models.player_system.Template.FlagBag;
import nro.models.player_system.Template.ItemTemplate;
import nro.models.server.Manager;
import nro.models.services.ItemService;

import static nro.models.panel.PanelApi.intv;
import static nro.models.panel.PanelApi.rows;
import static nro.models.panel.PanelApi.str;
import static nro.models.panel.PanelApi.update;

/**
 * Panel: trang "Đeo lưng & thú cưỡi" - xem khung hình đeo lưng, vật phẩm nào dùng cờ nào (bắt lỗi gán nhầm như cánh hiện
 * thành Ngọc Rồng Super), sửa khung hình / gán cờ khác; kiểm tra ảnh thú cưỡi; phiên bản dữ liệu item.
 *
 * @author Việt
 */
final class PanelLooks {

    private PanelLooks() {
    }

    static Object flags() throws Exception {
        Map<Integer, List<Map<String, Object>>> users = new HashMap<>();
        for (ItemTemplate t : Manager.ITEM_TEMPLATES) {
            if (t != null && t.type == 11 && t.part >= 0) {
                Map<String, Object> it = new LinkedHashMap<>();
                it.put("id", t.id);
                it.put("name", t.name);
                it.put("icon", t.iconID);
                users.computeIfAbsent((int) t.part, k -> new ArrayList<>()).add(it);
            }
        }
        Map<String, Integer> firstByFrames = new HashMap<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows("select id, icon_data, NAME, icon_id from flag_bag order by id")) {
            int id = intv(r.get("id"));
            String frames = str(r.get("icon_data")).replaceAll("[^0-9,-]", "");
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", id);
            o.put("name", r.get("NAME"));
            o.put("frames", frames);
            o.put("items", users.getOrDefault(id, List.of()));
            Integer same = firstByFrames.putIfAbsent(frames, id);
            List<String> warn = new ArrayList<>();
            if (same != null && !frames.contains("-") && users.containsKey(id) && users.containsKey(same)) {
                warn.add("Trùng khung hình với cờ " + same + " (" + names(users.get(same)) + ")");
            }
            for (String f : frames.split(",")) {
                if (!f.isEmpty() && !f.startsWith("-") && !new File("data/icon/x4/" + f + ".png").exists()) {
                    warn.add("Thiếu icon " + f);
                    break;
                }
            }
            o.put("warn", String.join("; ", warn));
            out.add(o);
        }
        for (Map.Entry<Integer, List<Map<String, Object>>> e : users.entrySet()) {
            if (out.stream().noneMatch(o -> (int) o.get("id") == e.getKey())) {
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("id", e.getKey());
                o.put("name", "(chưa có trong flag_bag)");
                o.put("frames", "");
                o.put("items", e.getValue());
                // 201 - 254: đeo lưng hiệu ứng (client vẽ bằng DataEffect cùng mã), không cần khung hình
                o.put("warn", e.getKey() >= 201 && e.getKey() <= 254 ? "" : "Chưa có khung hình: đang tạm hiện icon vật phẩm");
                if (e.getKey() >= 201 && e.getKey() <= 254) {
                    o.put("name", "(đeo lưng hiệu ứng " + e.getKey() + ")");
                }
                out.add(o);
            }
        }
        return out;
    }

    private static String names(List<Map<String, Object>> l) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> m : l) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(m.get("name"));
        }
        return sb.toString();
    }

    /** Lưu khung hình cờ (tạo mới nếu chưa có). Client tải lại hình cờ khi vào lại game. */
    static Object flagSave(Map<String, Object> b) throws Exception {
        int id = intv(b.get("id"));
        if (id <= 0 || id > 32000) {
            throw new IllegalArgumentException("Mã cờ không hợp lệ");
        }
        List<Short> list = new ArrayList<>();
        for (String f : str(b.get("frames")).split("[,\\s]+")) {
            if (f.isBlank()) {
                continue;
            }
            int v = Integer.parseInt(f.trim());
            if (v < 0 || v > 32767 || !new File("data/icon/x4/" + v + ".png").exists()) {
                throw new IllegalArgumentException("Không có icon " + v);
            }
            list.add((short) v);
        }
        if (list.isEmpty()) {
            throw new IllegalArgumentException("Cần ít nhất 1 khung hình");
        }
        String data = list.toString().replaceAll("[\\[\\]]", "");
        if (rows("select id from flag_bag where id=?", id).isEmpty()) {
            update("insert into flag_bag (id, icon_data, NAME, gold, gem, icon_id) values (?, ?, 'flag_bag', -1, -1, 0)", id, data);
        } else {
            update("update flag_bag set icon_data=? where id=?", data, id);
        }
        synchronized (Manager.FLAGS_BAGS) {
            FlagBag fb = null;
            for (FlagBag f : Manager.FLAGS_BAGS) {
                if (f.id == id) {
                    fb = f;
                }
            }
            if (fb == null) {
                fb = new FlagBag();
                fb.id = id;
                fb.name = "flag_bag";
                fb.gold = -1;
                fb.gem = -1;
                Manager.FLAGS_BAGS.add(fb);
            }
            short[] arr = new short[list.size()];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = list.get(i);
            }
            fb.iconEffect = arr;
        }
        return Map.of("ok", true, "note", "Đã lưu cờ " + id + " (" + list.size() + " khung). Người chơi thấy hình mới sau khi vào lại game");
    }

    /** Gán vật phẩm đeo lưng sang cờ khác. */
    static Object flagAssign(Map<String, Object> b) throws Exception {
        int item = intv(b.get("item")), flag = intv(b.get("flag"));
        ItemTemplate t = ItemService.gI().getTemplate(item);
        if (t == null || t.type != 11) {
            throw new IllegalArgumentException("Vật phẩm " + item + " không phải đeo lưng");
        }
        update("update item_template set part=? where id=?", flag, item);
        t.part = (short) flag;
        nro.models.data.DataVersionService.resync();
        return Map.of("ok", true, "note", t.name + " -> cờ " + flag + ". Người chơi mặc lại để thấy hình mới");
    }

    /** Thú cưỡi: ảnh mount_{part}_0 / _1 có đủ và đúng số khung không. */
    static Object mounts() throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ItemTemplate t : Manager.ITEM_TEMPLATES) {
            if (t == null || (t.type != 23 && t.type != 24)) {
                continue;
            }
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", t.id);
            o.put("name", t.name);
            o.put("icon", t.iconID);
            o.put("part", t.part);
            List<String> warn = new ArrayList<>();
            if (t.type == 23 && t.part >= 0) {
                int ok = 0;
                for (int s = 0; s < 2; s++) {
                    String nm = "mount_" + t.part + "_" + s;
                    int n = Manager.getNFrameImageByName(nm);
                    File f = new File("data/img_by_name/x4/" + nm + ".png");
                    if (n > 0 && f.exists()) {
                        ok++;
                    } else if (n > 0) {
                        warn.add("thiếu file " + nm);
                    }
                }
                if (ok == 0) {
                    warn.add("chưa có ảnh mount_" + t.part + " (cưỡi sẽ không hiện hình)");
                }
                o.put("frames", Manager.getNFrameImageByName("mount_" + t.part + "_0") + " / " + Manager.getNFrameImageByName("mount_" + t.part + "_1"));
            } else {
                o.put("frames", "");
            }
            o.put("warn", String.join("; ", warn));
            out.add(o);
        }
        return out;
    }

    static Object dataVersion() {
        return Map.of("vsItem", DataGame.vsItem, "vsData", DataGame.vsData, "vsMap", DataGame.vsMap, "items", Manager.ITEM_TEMPLATES.size());
    }

    /** Buộc mọi client tải lại danh sách item ở lần vào game sau. */
    static Object bumpItemVersion() throws Exception {
        int v = DataGame.vsItem >= 120 || DataGame.vsItem < 1 ? 1 : DataGame.vsItem + 1;
        update("update data_version set ver=? where name='item'", v);
        DataGame.vsItem = (byte) v;
        return Map.of("ok", true, "note", "Phiên bản item: " + v + ". Client tải lại danh sách item ở lần vào game sau");
    }

    // ------------------------------------------------------------------ đeo lưng hiệu ứng (cờ 201 - 254)

    /** Đọc DataEffect: ảnh, khung, hoạt ảnh theo trạng thái nhân vật (1 đứng, 2 chạy, 3 nhảy, 4 rơi, 10 bay...). */
    static Map<String, Object> effectData(int id) throws Exception {
        File f = new File("data/effdata/DataEffect_" + id);
        if (!f.exists()) {
            throw new IllegalArgumentException("Không có hiệu ứng " + id);
        }
        java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(java.nio.file.Files.readAllBytes(f.toPath())));
        List<int[]> imgs = new ArrayList<>();
        int n = in.readByte();
        for (int i = 0; i < n; i++) {
            imgs.add(new int[]{in.readByte(), in.readUnsignedByte(), in.readUnsignedByte(), in.readUnsignedByte(), in.readUnsignedByte()});
        }
        List<List<int[]>> frames = new ArrayList<>();
        int nf = in.readShort();
        for (int i = 0; i < nf; i++) {
            int k = in.readByte();
            List<int[]> parts = new ArrayList<>();
            for (int j = 0; j < k; j++) {
                parts.add(new int[]{in.readShort(), in.readShort(), in.readByte()});
            }
            frames.add(parts);
        }
        int na = in.readShort();
        Map<String, List<Integer>> anim = new LinkedHashMap<>();
        List<Integer> all = new ArrayList<>(), cur = new ArrayList<>();
        for (int i = 0; i < na; i++) {
            int v = in.readShort();
            if (v >= 0) {
                cur.add(v);
                all.add(v);
            } else {
                anim.put(String.valueOf(Math.abs(v + 500)), cur);
                cur = new ArrayList<>();
            }
        }
        if (anim.isEmpty()) {
            anim.put("all", all);
        }
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", id);
        o.put("imgs", imgs);
        o.put("frames", frames);
        o.put("anim", anim);
        return o;
    }

    /** Hiệu ứng dùng được làm đeo lưng (có hoạt ảnh theo trạng thái) + vật phẩm đang dùng. */
    static Object effectBags() throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int id = 201; id <= 254; id++) {
            if (!new File("data/effdata/DataEffect_" + id).exists()) {
                continue;
            }
            Map<String, Object> d = effectData(id);
            @SuppressWarnings("unchecked")
            Map<String, List<Integer>> anim = (Map<String, List<Integer>>) d.get("anim");
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", id);
            o.put("byStatus", !anim.containsKey("all"));
            List<Map<String, Object>> used = new ArrayList<>();
            for (ItemTemplate t : Manager.ITEM_TEMPLATES) {
                if (t != null && t.type == 11 && t.part == id) {
                    used.add(Map.of("id", t.id, "name", t.name, "icon", t.iconID));
                }
            }
            o.put("items", used);
            out.add(o);
        }
        return out;
    }
}

