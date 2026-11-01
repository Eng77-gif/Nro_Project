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
        updateItemTemplate(session, 750);
        // độ dài gói -28 chỉ có 2 byte (tối đa 65535): chia phần còn lại thành nhiều gói "thêm vật phẩm"
        int start = 750;
        int size = Manager.ITEM_TEMPLATES.size();
        while (start < size) {
            int end = start;
            int bytes = 0;
            while (end < size && (end == start || bytes + itemSize(Manager.ITEM_TEMPLATES.get(end)) < MAX_ITEM_BYTES)) {
                bytes += itemSize(Manager.ITEM_TEMPLATES.get(end));
                end++;
            }
            updateItemTemplate(session, start, end);
            start = end;
        }
    }

    private static final int MAX_ITEM_BYTES = 60_000;

    private static int itemSize(Template.ItemTemplate it) {
        return 13 + utfLength(it.name) + utfLength(it.description);
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
            msg.writer().writeByte(Manager.ITEM_OPTION_TEMPLATES.size());
            for (ItemOptionTemplate io : Manager.ITEM_OPTION_TEMPLATES) {
                msg.writer().writeUTF(io.name);
                msg.writer().writeByte(io.type);
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Tên item cho J2ME: bỏ dấu tất cả, rồi trả lại dấu cho các tên tốn ít byte thêm nhất khi gói còn chỗ.
     * Nếu bỏ dấu hết vẫn không vừa (item tăng thêm), cắt bớt ở giữa các tên dài nhất, giữ 4 ký tự cuối.
     */
    private static String[] fitNames(int count) {
        String[] full = new String[count];
        String[] names = new String[count];
        int[] len = new int[count];
        long total = 5;
        for (int i = 0; i < count; i++) {
            String nm = Manager.ITEM_TEMPLATES.get(i).name;
            full[i] = nm == null ? "" : nm;
            names[i] = noAccent(full[i]);
            len[i] = utfLength(names[i]) - 2;
            total += 16 + len[i]; // 1+1+2+2+1+4+2+2+1 byte cố định
        }
        Integer[] order = new Integer[count];
        for (int i = 0; i < count; i++) {
            order[i] = i;
        }
        final String[] f = full;
        java.util.Arrays.sort(order, (a, b) -> (utfLength(f[a]) - utfLength(noAccent(f[a]))) - (utfLength(f[b]) - utfLength(noAccent(f[b]))));
        for (int i : order) {
            int extra = utfLength(full[i]) - 2 - len[i];
            if (total + extra <= MAX_PACKET) {
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
        return names;
    }

    private static String noAccent(String s) {
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.replace('đ', 'd').replace('Đ', 'D');
    }
}
