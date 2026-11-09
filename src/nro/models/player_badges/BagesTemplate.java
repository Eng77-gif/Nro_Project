package nro.models.player_badges;

import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.server.Manager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 * @author Việt
 *
 */
public class BagesTemplate {

    public int id;
    public int idEffect;
    public int idItem;
    public String NAME;
    public List<Item.ItemOption> options = new ArrayList<>();

    /** Nạp lại bảng danh hiệu từ database (panel). */
    public static void reload(java.sql.Connection con) throws Exception {
        List<BagesTemplate> list = new ArrayList<>();
        try (java.sql.PreparedStatement ps = con.prepareStatement("select * from data_badges"); java.sql.ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                BagesTemplate t = new BagesTemplate();
                t.id = rs.getInt("id");
                t.idEffect = rs.getInt("idEffect");
                t.idItem = rs.getInt("idItem");
                t.NAME = rs.getString("NAME");
                org.json.simple.JSONArray op = (org.json.simple.JSONArray) org.json.simple.JSONValue.parse(rs.getString("Options"));
                if (op != null) {
                    for (Object o : op) {
                        org.json.simple.JSONObject j = (org.json.simple.JSONObject) o;
                        t.options.add(new Item.ItemOption(Integer.parseInt(j.get("id").toString()), Integer.parseInt(j.get("param").toString())));
                    }
                }
                list.add(t);
            }
        }
        synchronized (Manager.BAGES_TEMPLATES) {
            Manager.BAGES_TEMPLATES.clear();
            Manager.BAGES_TEMPLATES.addAll(list);
        }
    }

    public static int findIdItemByIdIdEffect(int idEffect) {
        for (BagesTemplate data : Manager.BAGES_TEMPLATES) {
            if (data.idEffect == idEffect) {
                return data.idItem;
            }
        }
        return -1;
    }

    public static int fineIdEffectbyIdItem(int idItem) {
        for (BagesTemplate data : Manager.BAGES_TEMPLATES) {
            if (data.idItem == idItem) {
                return data.idEffect;
            }
        }
        return -1;
    }

    public static BagesTemplate fineBadgesbyIdItem(int idItem) {
        for (BagesTemplate data : Manager.BAGES_TEMPLATES) {
            if (data.idItem == idItem) {
                return data;
            }
        }
        return null;
    }

    public static List<Integer> listEffect(Player player) {
        Set<Integer> setIdItem = new HashSet<>();
        for (BadgesData data : player.dataBadges) {
            for (BagesTemplate temp : Manager.BAGES_TEMPLATES) {
                if (temp.idEffect == data.idBadGes) {
                    setIdItem.add(temp.idItem);
                }
            }
        }
        return new ArrayList<>(setIdItem);
    }

    public static List<Item.ItemOption> sendListItemOption(Player player) {
        List<Item.ItemOption> listOptions = new ArrayList<>();
        for (BadgesData data : player.dataBadges) {
            for (BagesTemplate temp : Manager.BAGES_TEMPLATES) {
                if (data.idBadGes == temp.idEffect && data.isUse) {
                    listOptions.addAll(temp.options);
                }
            }
        }
        return listOptions;
    }

}
