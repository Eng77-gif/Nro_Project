package nro.models.item;

import java.util.List;
import nro.models.player_system.Template.ItemOptionTemplate;
import nro.models.utils.Logger;

/**
 * Client PC / APK (Unity) đọc số dòng option và id option bằng 1 byte không dấu: tối đa 255 dòng (id 0 - 254).
 * Option có id từ 255 trở lên (255, 256, Set Champa 257 - 260...) được gửi cho client ở các ô id không có
 * item nào dùng (SPARE). Server và database giữ nguyên id thật; chỉ đổi khi gửi gói tin.
 * Client J2ME đọc id dạng short nên luôn nhận id thật.
 */
public final class OptionIdMap {

    /** Số dòng tối đa client byte nhận được. */
    public static final int CLIENT_MAX = 255;
    /** Các id option không có item / shop / code nào dùng (kiểm tra trên database team2026). */
    public static int spareCount() {
        return SPARE.length;
    }

    public static boolean isSpare(int id) {
        for (int s : SPARE) {
            if (s == id) {
                return true;
            }
        }
        return false;
    }

    // 68 (Dùng để nâng cấp áo - Đá Titan) đang có item dùng nên thay bằng 41. 205 (Zenitsu) / 178 (Cải trang Gohan) có item dùng nên bỏ,
    // thêm 46, 182, 185, 187, 230, 249: không item / shop / đồ người chơi / ký gửi / code nào dùng (kiểm tra 25/11/2026)
    private static final int[] SPARE = {201, 202, 203, 195, 197, 192, 229, 62, 208, 67, 41, 69, 225, 45, 181, 190, 215, 158, 46, 182, 185, 187, 230, 249};
    /** Option >= 255 trùng nghĩa với option < 255 có sẵn: gửi thẳng id đó, không tốn ô. */
    private static final java.util.Map<Integer, Integer> ALIAS = java.util.Map.of(
            268, 100, // +#% vàng rơi = +#% vàng từ quái
            278, 108, // Né đòn +#% = #% Né đòn
            279, 106); // Chống lạnh = Không ảnh hưởng bởi cái lạnh

    private static volatile int[] toClient = new int[0];
    private static volatile ItemOptionTemplate[] slots = new ItemOptionTemplate[0];

    private OptionIdMap() {
    }

    public static synchronized void build(List<ItemOptionTemplate> list) {
        int max = -1;
        for (ItemOptionTemplate t : list) {
            max = Math.max(max, t.id);
        }
        int[] map = new int[max + 1];
        ItemOptionTemplate[] s = new ItemOptionTemplate[CLIENT_MAX];
        for (int i = 0; i < map.length; i++) {
            map[i] = i;
        }
        for (ItemOptionTemplate t : list) {
            if (t.id >= 0 && t.id < CLIENT_MAX) {
                s[t.id] = t;
            }
        }
        int spare = 0;
        java.util.List<Integer> noSlot = new java.util.ArrayList<>();
        for (ItemOptionTemplate t : list) {
            if (t.id < CLIENT_MAX) {
                continue;
            }
            Integer alias = ALIAS.get(t.id);
            if (alias != null) {
                map[t.id] = alias;
                continue;
            }
            if (spare >= SPARE.length) {
                noSlot.add(t.id);
                map[t.id] = 0;
                continue;
            }
            int slot = SPARE[spare++];
            map[t.id] = slot;
            s[slot] = t;
        }
        if (!noSlot.isEmpty()) { // 1 dòng thay vì mỗi option 1 dòng
            Logger.error("OptionIdMap: hết ô trống cho " + noSlot.size() + " option " + noSlot + " (client PC/APK sẽ hiển thị sai)\n");
        }
        toClient = map;
        slots = s;
    }

    /** Id gửi cho client byte (PC / APK). */
    public static int toClient(int id) {
        int[] m = toClient;
        return id >= 0 && id < m.length ? m[id] : id;
    }

    /** Số dòng bảng option gửi cho client byte. */
    public static int clientCount() {
        ItemOptionTemplate[] s = slots;
        int n = 0;
        for (int i = 0; i < s.length; i++) {
            if (s[i] != null) {
                n = i + 1;
            }
        }
        return n;
    }

    /** Option hiển thị ở ô slot của client byte (null nếu ô trống). */
    public static ItemOptionTemplate clientSlot(int slot) {
        ItemOptionTemplate[] s = slots;
        return slot >= 0 && slot < s.length ? s[slot] : null;
    }
}
