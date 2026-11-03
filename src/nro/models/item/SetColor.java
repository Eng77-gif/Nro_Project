package nro.models.item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nro.models.player.Player;
import nro.models.player_system.Template.ItemOptionTemplate;
import nro.models.server.Manager;

/**
 * Dòng hiệu ứng bộ đồ ("$(5 món +100% sát thương Kamejoko)", "$[2] / $[4] / $[5] ..."): client tô xanh khi tham số = 1,
 * đen khi = 0. Đặt tham số theo số món đang mặc (áo, quần, găng, giày, rađa) có cùng dòng đó: đủ số món yêu cầu thì 1, chưa đủ 0.
 * Các dòng "$(Ở gần ...)" phụ thuộc người xung quanh nên giữ nguyên.
 *
 * @author Việt
 */
public final class SetColor {

    /** option id -> số món cần (null = không phải dòng bộ đồ đếm theo món) */
    private static volatile Map<Integer, Integer> need;

    private SetColor() {
    }

    private static Map<Integer, Integer> need() {
        Map<Integer, Integer> m = need;
        if (m == null) {
            m = new HashMap<>();
            for (ItemOptionTemplate t : Manager.ITEM_OPTION_TEMPLATES) {
                String n = t.name == null ? "" : t.name;
                if (n.startsWith("$(5 món")) {
                    m.put(t.id, 5);
                } else if (n.length() > 3 && n.startsWith("$[") && Character.isDigit(n.charAt(2)) && n.charAt(3) == ']') {
                    m.put(t.id, n.charAt(2) - '0');
                }
            }
            need = m;
        }
        return m;
    }

    /** Cập nhật tham số dòng bộ đồ trên mọi món của người chơi (mặc, hành trang, rương) trước khi gửi xuống client. */
    public static void refresh(Player pl) {
        if (pl == null || pl.inventory == null || pl.inventory.itemsBody == null) {
            return;
        }
        Map<Integer, Integer> need = need();
        if (need.isEmpty()) {
            return;
        }
        Map<Integer, Integer> worn = new HashMap<>();
        List<Item> body = pl.inventory.itemsBody;
        for (int i = 0; i < 5 && i < body.size(); i++) {
            Item it = body.get(i);
            if (it == null || !it.isNotNullItem()) {
                continue;
            }
            for (Item.ItemOption io : it.itemOptions) {
                if (io.optionTemplate != null && need.containsKey(io.optionTemplate.id)) {
                    worn.merge(io.optionTemplate.id, 1, Integer::sum);
                }
            }
        }
        apply(body, need, worn);
        apply(pl.inventory.itemsBag, need, worn);
        apply(pl.inventory.itemsBox, need, worn);
    }

    private static void apply(List<Item> list, Map<Integer, Integer> need, Map<Integer, Integer> worn) {
        if (list == null) {
            return;
        }
        for (Item it : list) {
            if (it == null || !it.isNotNullItem()) {
                continue;
            }
            for (Item.ItemOption io : it.itemOptions) {
                if (io.optionTemplate == null) {
                    continue;
                }
                Integer n = need.get(io.optionTemplate.id);
                if (n != null) {
                    io.param = worn.getOrDefault(io.optionTemplate.id, 0) >= n ? 1 : 0;
                }
            }
        }
    }
}
