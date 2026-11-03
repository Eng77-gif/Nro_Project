package nro.models.data;

import nro.models.player_system.Template.ArrHead2Frames;
import nro.models.player_system.Template.ItemOptionTemplate;
import nro.models.server.Manager;
import nro.models.network.Message;
import nro.models.network.MySession;
import nro.models.player_system.Template;

public class ItemData {

    public static void updateItem(MySession session) {
        if (session.longNumbers) {
            updateItemJ2me(session);
            return;
        }
        updateItemOptionItemplate(session);
        updateItemArrHead2FTemplate(session);
        // Client APK / PC lưu bộ nhớ đệm item theo 2 khóa: gói loại 1 và gói loại 2 (chỉ giữ gói loại 2 CUỐI CÙNG).
        // Trước đây phần sau id 750 bị chia thành nhiều gói loại 2 -> vào lại game client chỉ còn gói cuối,
        // thiếu item ở giữa -> mở shop / hành trang có item đó bị lỗi. Giờ luôn gửi đúng 2 gói, mỗi gói vừa 65535 byte
        // (dời điểm chia, nếu vẫn không vừa thì rút gọn mô tả dài nhất).
        int size = Manager.ITEM_TEMPLATES.size();
        String[] desc = new String[size];
        for (int i = 0; i < size; i++) {
            String d = Manager.ITEM_TEMPLATES.get(i).description;
            desc[i] = d == null ? "" : d;
        }
        int split = fitTwoPackets(desc);
        updateItemTemplate(session, 0, split, (byte) 1, desc);
        if (split < size) {
            updateItemTemplate(session, split, size, (byte) 2, desc);
        }
    }

    private static final int PACKET_LIMIT = 65_000;

    private static int itemSize(Template.ItemTemplate it, String desc) {
        return 13 + utfLength(it.name) + utfLength(desc);
    }

    /** Chọn điểm chia và rút gọn mô tả (nếu cần) để gói loại 1 và loại 2 đều dưới PACKET_LIMIT. Trả về điểm chia. */
    private static int fitTwoPackets(String[] desc) {
        int size = desc.length;
        int maxLen = 0;
        for (int i = 0; i < size; i++) {
            maxLen = Math.max(maxLen, desc[i].getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        }
        for (int cap = Integer.MAX_VALUE; ; cap = cap == Integer.MAX_VALUE ? maxLen - 2 : cap - 2) {
            if (cap != Integer.MAX_VALUE) {
                for (int i = 0; i < size; i++) {
                    desc[i] = cut(Manager.ITEM_TEMPLATES.get(i).description, cap);
                }
            }
            int a = 0, i = 0;
            while (i < size && a + itemSize(Manager.ITEM_TEMPLATES.get(i), desc[i]) < PACKET_LIMIT) {
                a += itemSize(Manager.ITEM_TEMPLATES.get(i), desc[i]);
                i++;
            }
            int b = 0;
            for (int j = i; j < size; j++) {
                b += itemSize(Manager.ITEM_TEMPLATES.get(j), desc[j]);
            }
            if (b < PACKET_LIMIT || cap <= 10) {
                if (cap != Integer.MAX_VALUE) {
                    nro.models.utils.Logger.error("Mô tả item quá dài, đã rút gọn còn " + cap + " byte cho client APK / PC\n");
                }
                return i;
            }
        }
    }

    /** Cắt chuỗi còn tối đa cap byte UTF-8 (thêm "..."). */
    private static String cut(String s, int cap) {
        if (s == null) {
            return "";
        }
        if (s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= cap) {
            return s;
        }
        StringBuilder b = new StringBuilder();
        int n = 3;
        for (int k = 0; k < s.length(); ) {
            int cp = s.codePointAt(k);
            int l = new String(Character.toChars(cp)).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (n + l > cap) {
                break;
            }
            b.appendCodePoint(cp);
            n += l;
            k += Character.charCount(cp);
        }
        return b + "...";
    }

    private static void updateItemTemplate(MySession session, int start, int end, byte type, String[] desc) {
        try {
            Message msg = new Message(-28);
            msg.writer().writeByte(8);
            msg.writer().writeByte(DataGame.vsItem);
            msg.writer().writeByte(type); // 1: tải lại toàn bộ (từ 0), 2: thêm item [start, end)
            if (type == 1) {
                msg.writer().writeShort(end);
            } else {
                msg.writer().writeShort(start);
                msg.writer().writeShort(end);
            }
            for (int i = start; i < end; i++) {
                Template.ItemTemplate t = Manager.ITEM_TEMPLATES.get(i);
                msg.writer().writeByte(t.type);
                msg.writer().writeByte(t.gender);
                msg.writer().writeUTF(t.name);
                msg.writer().writeUTF(desc[i]);
                msg.writer().writeByte(t.level);
                msg.writer().writeInt(t.strRequire);
                msg.writer().writeShort(t.iconID);
                msg.writer().writeShort(t.part);
                msg.writer().writeBoolean(t.isUpToUp);
            }
            session.doSendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private static int utfLength(String s) {
        return s == null ? 2 : 2 + s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    private static void updateItemOptionItemplate(MySession session) {
        Message msg;
        try {
            msg = new Message(-28);
            msg.writer().writeByte(8);
            msg.writer().writeByte(DataGame.vsItem); //vcitem
            msg.writer().writeByte(0); //update option
            // client PC / APK: số dòng 1 byte không dấu (tối đa 255), option id >= 255 nằm ở ô trống (OptionIdMap)
            int n = nro.models.item.OptionIdMap.clientCount();
            msg.writer().writeByte(n);
            for (int i = 0; i < n; i++) {
                ItemOptionTemplate io = nro.models.item.OptionIdMap.clientSlot(i);
                msg.writer().writeUTF(io != null ? io.name : "");
                msg.writer().writeByte(io != null ? io.type : 0);
            }
            session.doSendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void updateItemTemplate(MySession session, int count) {
        Message msg;
        try {
            msg = new Message(-28);
            msg.writer().writeByte(8);

            msg.writer().writeByte(DataGame.vsItem); //vcitem
            msg.writer().writeByte(1); //reload itemtemplate
            msg.writer().writeShort(count);
            for (int i = 0; i < count; i++) {
                Template.ItemTemplate itemTemplate = Manager.ITEM_TEMPLATES.get(i);
                msg.writer().writeByte(itemTemplate.type);
                msg.writer().writeByte(itemTemplate.gender);
                msg.writer().writeUTF(itemTemplate.name);
                msg.writer().writeUTF(itemTemplate.description);
                msg.writer().writeByte(itemTemplate.level);
                msg.writer().writeInt(itemTemplate.strRequire);
                msg.writer().writeShort(itemTemplate.iconID);
                msg.writer().writeShort(itemTemplate.part);
                msg.writer().writeBoolean(itemTemplate.isUpToUp);
            }
            session.doSendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void updateItemTemplate(MySession session, int start, int end) {
        Message msg;
        try {
            msg = new Message(-28);
            msg.writer().writeByte(8);

            msg.writer().writeByte(DataGame.vsItem); //vcitem
            msg.writer().writeByte(2); //add itemtemplate
            msg.writer().writeShort(start);
            msg.writer().writeShort(end);
            for (int i = start; i < end; i++) {
//                System.out.println("start: " + start + " -> " + end + " id " + Manager.ITEM_TEMPLATES.get(i).id);
                msg.writer().writeByte(Manager.ITEM_TEMPLATES.get(i).type);
                msg.writer().writeByte(Manager.ITEM_TEMPLATES.get(i).gender);
                msg.writer().writeUTF(Manager.ITEM_TEMPLATES.get(i).name);
                msg.writer().writeUTF(Manager.ITEM_TEMPLATES.get(i).description);
                msg.writer().writeByte(Manager.ITEM_TEMPLATES.get(i).level);
                msg.writer().writeInt(Manager.ITEM_TEMPLATES.get(i).strRequire);
                msg.writer().writeShort(Manager.ITEM_TEMPLATES.get(i).iconID);
                msg.writer().writeShort(Manager.ITEM_TEMPLATES.get(i).part);
                msg.writer().writeBoolean(Manager.ITEM_TEMPLATES.get(i).isUpToUp);
            }
            session.doSendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void updateItemArrHead2FTemplate(MySession session) {
        Message msg;
        try {
            msg = new Message(-28);
            msg.writer().writeByte(8);
            msg.writer().writeByte(DataGame.vsItem); //vcitem
            msg.writer().writeByte(100);
            msg.writer().writeShort(Manager.ARR_HEAD_2_FRAMES.size());
            for (ArrHead2Frames io : Manager.ARR_HEAD_2_FRAMES) {
                msg.writer().writeByte(io.frames.size());
                for (int i : io.frames) {
                    msg.writer().writeShort(i);
                }
            }
            session.doSendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------------ client J2ME 2.4.x

    /** Gói -28 tối đa 65535 byte (độ dài 2 byte). */
    private static final int MAX_PACKET = 65_535 - 64;

    /**
     * Client J2ME 2.4.x (Dragonboy247...):
     * - bảng option: số lượng short, (tên, loại) rồi bảng loại (short số dòng, (short id, ubyte loại));
     * - danh sách item: chỉ nhận loại 1 (toàn bộ, id = thứ tự trong gói), bỏ qua loại 2, nên mọi item phải nằm
     *   trong 1 gói. Bỏ mô tả và rút gọn các tên dài nhất (bỏ dấu, rồi cắt) cho tới khi vừa 65535 byte.
     */
    private static void updateItemJ2me(MySession session) {
        try {
            Message msg = new Message(-28);
            msg.writer().writeByte(8);
            msg.writer().writeByte(DataGame.vsItem);
            msg.writer().writeByte(0);
            int n = Manager.ITEM_OPTION_TEMPLATES.size();
            msg.writer().writeShort(n);
            for (ItemOptionTemplate io : Manager.ITEM_OPTION_TEMPLATES) {
                msg.writer().writeUTF(io.name);
                msg.writer().writeByte(io.type);
            }
            msg.writer().writeShort(n);
            for (int i = 0; i < n; i++) {
                msg.writer().writeShort(i);
                msg.writer().writeByte(Manager.ITEM_OPTION_TEMPLATES.get(i).type);
            }
            session.doSendMessage(msg);
            msg.cleanup();

            updateItemArrHead2FTemplate(session);

            int count = Manager.ITEM_TEMPLATES.size();
            String[] names = fitNames(count);
            msg = new Message(-28);
            msg.writer().writeByte(8);
            msg.writer().writeByte(DataGame.vsItem);
            msg.writer().writeByte(1);
            msg.writer().writeShort(count);
            for (int i = 0; i < count; i++) {
                Template.ItemTemplate it = Manager.ITEM_TEMPLATES.get(i);
                msg.writer().writeByte(it.type);
                msg.writer().writeByte(it.gender);
                msg.writer().writeUTF(names[i]);
                msg.writer().writeUTF("");
                msg.writer().writeByte(it.level);
                msg.writer().writeInt(it.strRequire);
                msg.writer().writeShort(it.iconID);
                msg.writer().writeShort(it.part);
                msg.writer().writeBoolean(it.isUpToUp);
            }
            session.doSendMessage(msg);
            msg.cleanup();

            // Gói lớn (lệnh 12, độ dài 3 byte) như bản chính thức: đủ tên có dấu và mô tả item. Client J2ME bản mới
            // (Dragonboy247 v7.0) nhận gói này và thay danh sách ở trên; client không hiểu lệnh 12 thì bỏ qua, giữ danh sách rút gọn.
            msg = new Message(12);
            msg.writer().writeByte(0);
            msg.writer().writeByte(DataGame.vsItem);
            msg.writer().writeByte(1);
            msg.writer().writeShort(count);
            for (int i = 0; i < count; i++) {
                Template.ItemTemplate it = Manager.ITEM_TEMPLATES.get(i);
                msg.writer().writeByte(it.type);
                msg.writer().writeByte(it.gender);
                msg.writer().writeUTF(it.name == null ? "" : it.name);
                msg.writer().writeUTF(it.description == null ? "" : it.description);
                msg.writer().writeByte(it.level);
                msg.writer().writeInt(it.strRequire);
                msg.writer().writeShort(it.iconID);
                msg.writer().writeShort(it.part);
                msg.writer().writeBoolean(it.isUpToUp);
            }
            session.doSendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String[] cachedNames;
    private static int cachedCount = -1;

    /** Xoá bộ nhớ đệm tên (khi bảng item / shop / sự kiện đổi). */
    public static synchronized void resetNames() {
        cachedNames = null;
    }

    /**
     * Tên item cho J2ME (cả danh sách phải vừa 1 gói 65535 byte, không có chỗ cho đủ dấu của ~2100 tên):
     * 1. bỏ dấu mọi tên; item cũ không còn bán / đổi / rơi rút "Cai trang" thành "CT", "Vật phẩm đã xoá" thành rỗng;
     * 2. trả dấu theo thứ tự ưu tiên: quà đổi sự kiện / đua top, item bán trong shop, rồi quà trong hộp, vật phẩm rơi,
     *    nguyên liệu đổi quà, rồi các item còn lại (cùng mức: tên tốn ít byte trước);
     * 3. vẫn không vừa thì cắt ở giữa các tên dài nhất.
     */
    private static synchronized String[] fitNames(int count) {
        if (cachedNames != null && cachedCount == count) {
            return cachedNames;
        }
        java.util.Map<Integer, Integer> prio = priorities();
        String[] full = new String[count];
        String[] names = new String[count];
        int[] len = new int[count];
        long total = 5;
        for (int i = 0; i < count; i++) {
            String nm = Manager.ITEM_TEMPLATES.get(i).name;
            full[i] = nm == null || nm.equals("Vật phẩm đã xoá") ? "" : nm;
            names[i] = noAccent(full[i]);
            if (!prio.containsKey(i) && names[i].startsWith("Cai trang ")) {
                names[i] = "CT " + names[i].substring(10);
            }
            len[i] = utfLength(names[i]) - 2;
            total += 16 + len[i]; // 1+1+2+2+1+4+2+2+1 byte cố định
        }
        Integer[] order = new Integer[count];
        for (int i = 0; i < count; i++) {
            order[i] = i;
        }
        final String[] f = full;
        java.util.Arrays.sort(order, (x, y) -> {
            int px = prio.getOrDefault(x, 9), py = prio.getOrDefault(y, 9);
            if (px != py) {
                return px - py;
            }
            return (utfLength(f[x]) - utfLength(noAccent(f[x]))) - (utfLength(f[y]) - utfLength(noAccent(f[y])));
        });
        for (int i : order) {
            int extra = utfLength(full[i]) - 2 - len[i];
            if (extra > 0 && total + extra <= MAX_PACKET) {
                total += extra;
                names[i] = full[i];
                len[i] += extra;
            }
        }
        java.util.PriorityQueue<Integer> q = new java.util.PriorityQueue<>((a, b) -> len[b] - len[a]);
        for (int i = 0; i < count; i++) {
            q.add(i);
        }
        while (total > MAX_PACKET && !q.isEmpty()) {
            int i = q.poll();
            String cur = names[i];
            if (cur.isEmpty()) {
                break;
            }
            String nm = cur.length() > 5 ? cur.substring(0, cur.length() - 5) + cur.substring(cur.length() - 4) : cur.substring(0, cur.length() - 1);
            int nl = utfLength(nm) - 2;
            total -= len[i] - nl;
            names[i] = nm;
            len[i] = nl;
            q.add(i);
        }
        cachedNames = names;
        cachedCount = count;
        return names;
    }

    /** Mức ưu tiên giữ dấu: 0 quà đổi sự kiện / đua top, 1 shop, 2 hộp quà / rơi / nguyên liệu (cùng mức: tên tốn ít byte trước). */
    private static java.util.Map<Integer, Integer> priorities() {
        java.util.Map<Integer, Integer> m = new java.util.HashMap<>();
        String[][] q = {
            {"1", "select temp_id from item_shop where is_sell = 1"},
            {"0", "select reward_item from season_event_exchange"},
            {"0", "select item_id from season_event_top_reward"},
            {"2", "select box_id from item_box_reward"},
            {"2", "select item_id from item_box_reward"},
            {"2", "select item_id from season_event_drop"},};
        try (java.sql.Connection con = nro.models.data.LocalManager.getConnection()) {
            for (String[] e : q) {
                int p = Integer.parseInt(e[0]);
                try (java.sql.PreparedStatement ps = con.prepareStatement(e[1]); java.sql.ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        m.merge(rs.getInt(1), p, Math::min);
                    }
                }
            }
            try (java.sql.PreparedStatement ps = con.prepareStatement("select need from season_event_exchange"); java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String need = rs.getString(1);
                    if (need == null) {
                        continue;
                    }
                    for (String part : need.split(",")) {
                        int c = part.indexOf(':');
                        if (c > 0) {
                            try {
                                m.merge(Integer.parseInt(part.substring(0, c).trim()), 2, Math::min);
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            nro.models.utils.Logger.error("Không đọc được danh sách item ưu tiên giữ dấu: " + e.getMessage() + "\n");
        }
        return m;
    }

    private static String noAccent(String s) {
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.replace('đ', 'd').replace('Đ', 'D');
    }
}
