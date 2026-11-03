package nro.models.services;

import java.util.Map;

import nro.models.radar.Card;
import nro.models.radar.OptionCard;
import nro.models.radar.RadarCard;
import nro.models.player.Player;
import nro.models.network.Message;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Việt
 * 
 */

public class RadarService {

    public List<RadarCard> RADAR_TEMPLATE = new ArrayList<>();

    private static RadarService instance;

    public static RadarService gI() {
        if (instance == null) {
            instance = new RadarService();
        }
        return instance;
    }

    /** Nạp bảng radar từ database (khởi động và nút "nạp lại" trên panel). */
    public static void load(java.sql.Connection con) throws Exception {
        java.sql.PreparedStatement ps;
        java.sql.ResultSet rs;
        List<RadarCard> list = new ArrayList<>();
                ps = con.prepareStatement("select * from radar");
                rs = ps.executeQuery();
                while (rs.next()) {
                    RadarCard rd = new RadarCard();
                    rd.Id = rs.getShort("id");
                    rd.IconId = rs.getShort("iconId");
                    rd.Rank = rs.getByte("rank");
                    rd.Max = rs.getByte("max");
                    rd.Type = rs.getByte("type");
                    rd.Template = rs.getShort("mob_id");
                    rd.Name = rs.getString("name");
                    rd.Info = rs.getString("info");
                    org.json.simple.JSONArray arr = (org.json.simple.JSONArray) org.json.simple.JSONValue.parse(rs.getString("body"));
                    for (int i = 0; i < arr.size(); i++) {
                        org.json.simple.JSONObject ob = (org.json.simple.JSONObject) arr.get(i);
                        if (ob != null) {
                            rd.Head = Short.parseShort(ob.get("head").toString());
                            rd.Body = Short.parseShort(ob.get("body").toString());
                            rd.Leg = Short.parseShort(ob.get("leg").toString());
                            rd.Bag = Short.parseShort(ob.get("bag").toString());
                        }
                    }
                    rd.Options.clear();
                    arr = (org.json.simple.JSONArray) org.json.simple.JSONValue.parse(rs.getString("options"));
                    for (int i = 0; i < arr.size(); i++) {
                        org.json.simple.JSONObject ob = (org.json.simple.JSONObject) arr.get(i);
                        if (ob != null) {
                            rd.Options.add(new OptionCard(Integer.parseInt(ob.get("id").toString()), Short.parseShort(ob.get("param").toString()), Byte.parseByte(ob.get("activeCard").toString())));
                        }
                    }
                    rd.Require = rs.getShort("require");
                    rd.RequireLevel = rs.getShort("require_level");
                    rd.AuraId = rs.getShort("aura_id");
                    list.add(rd);
                }
        rs.close();
        ps.close();
        gI().RADAR_TEMPLATE.clear();
        gI().RADAR_TEMPLATE.addAll(list);
        loadAuraOverride(con);
    }

    /** Aura theo cấp thẻ chọn trên panel (radar_aura), ưu tiên hơn bảng mặc định trong auraFor. key = mã thẻ * 10 + cấp. */
    private static final Map<Integer, Short> AURA_OVERRIDE = new java.util.concurrent.ConcurrentHashMap<>();

    public static void loadAuraOverride(java.sql.Connection con) throws Exception {
        try (java.sql.Statement st = con.createStatement()) {
            st.executeUpdate("create table if not exists radar_aura (card_id int not null, lv int not null, aura int not null, primary key (card_id, lv))");
        }
        Map<Integer, Short> m = new java.util.HashMap<>();
        try (java.sql.Statement st = con.createStatement(); java.sql.ResultSet rs = st.executeQuery("select card_id, lv, aura from radar_aura")) {
            while (rs.next()) {
                m.put(rs.getInt(1) * 10 + rs.getInt(2), (short) rs.getInt(3));
            }
        }
        AURA_OVERRIDE.clear();
        AURA_OVERRIDE.putAll(m);
    }

    /** Thẻ Oozaru (1771) gộp 3 thẻ: mảnh Oozarun 1 (2104), Oozarun 2 (2105) cũng là Thẻ Oozaru. */
    public static final int OOZARU = 1771;

    public static int cardIdOf(int itemId) {
        return itemId == 2104 || itemId == 2105 ? OOZARU : itemId;
    }

    /** Gộp thẻ Oozarun 1 / 2 cũ của người chơi vào Thẻ Oozaru (giữ cấp cao nhất), chỉ số lấy theo bảng radar hiện tại. */
    public static void mergeOozaru(nro.models.player.Player pl) {
        Card best = null;
        java.util.Iterator<Card> it = pl.Cards.iterator();
        java.util.List<Card> olds = new ArrayList<>();
        while (it.hasNext()) {
            Card c = it.next();
            if (c != null && (c.Id == OOZARU || c.Id == 2104 || c.Id == 2105)) {
                olds.add(c);
                it.remove();
            }
        }
        if (olds.isEmpty()) {
            return;
        }
        for (Card c : olds) {
            if (best == null || c.Level > best.Level || (c.Level == best.Level && c.Amount > best.Amount)) {
                best = c;
            }
        }
        boolean used = olds.stream().anyMatch(c -> c.Used == 1);
        RadarCard t = gI().RADAR_TEMPLATE.stream().filter(r -> r.Id == OOZARU).findFirst().orElse(null);
        Card m = new Card((short) OOZARU, best.Amount, t != null ? t.Max : best.MaxAmount, best.Level, t != null ? t.Options : best.Options, (byte) (used ? 1 : 0));
        pl.Cards.add(m);
    }

    /**
     * Cấp thẻ (Đợt 55): Level -1 = đang sưu tầm (chưa đạt Lv.1), 1 / 2 / 3 = Lv.1 / Lv.2 / Lv.3 (tối đa), 0 = chưa có.
     * Dòng chỉ số activeCard 0 có tác dụng từ Lv.1, activeCard k (1, 2) khi đang dùng thẻ ở Lv.(k + 1).
     */
    public static final int MAX_LEVEL = 3;

    public static int displayLevel(Card card) {
        return card.Level >= 1 ? Math.min(MAX_LEVEL, card.Level) : 0;
    }

    /** Số mảnh cần cho từng cấp {lên Lv.1, lên Lv.2, lên Lv.3}. */
    public static int[] thresholds(int cardId, int defMax) {
        return switch (cardId) {
            case 956, 1204 -> new int[]{3, 30, 30}; // Đội Trưởng Vàng, Rồng Thần Namek
            case OOZARU, 2040 -> new int[]{10, 125, 125}; // Oozaru, Khỉ con
            default -> new int[]{1, Math.max(1, defMax), Math.max(1, defMax)};
        };
    }

    /** Điều kiện dùng mảnh: {thẻ cần có, cấp tối thiểu}. Oozaru cần Rồng Thần Namek Lv.1, Khỉ con cần Oozaru Lv.2. */
    public static int[] requirement(int cardId) {
        return switch (cardId) {
            case OOZARU -> new int[]{1204, 1};
            case 2040 -> new int[]{OOZARU, 2};
            default -> null;
        };
    }

    /** Số mảnh tối đa hiển thị cho cấp hiện tại của thẻ. */
    public static void fixMax(Card card, int defMax) {
        int[] th = thresholds(card.Id, defMax);
        if (card.Level >= MAX_LEVEL) {
            card.MaxAmount = (byte) th[MAX_LEVEL - 1];
            card.Amount = card.MaxAmount;
        } else {
            card.MaxAmount = (byte) th[card.Level >= 1 ? card.Level : 0];
            if (card.Amount >= card.MaxAmount) {
                card.Amount = (byte) (card.MaxAmount - 1);
            }
        }
    }

    /** Khi vào game: số mảnh tối đa theo cấp hiện tại. */
    public static void normalizeCards(nro.models.player.Player pl) {
        for (Card c : pl.Cards) {
            if (c == null) {
                continue;
            }
            RadarCard t = gI().RADAR_TEMPLATE.stream().filter(r -> r.Id == c.Id).findFirst().orElse(null);
            fixMax(c, t != null ? t.Max : c.MaxAmount);
        }
    }

    /** Aura theo thẻ và cấp: Đội Trưởng Vàng 0, Rồng Thần Namek 1, Oozaru 2 / 3 / 4, Khỉ con 120 / 121 / 122. */
    public static short auraFor(int cardId, int lv) {
        int i = Math.max(1, Math.min(3, lv)) - 1;
        Short o = AURA_OVERRIDE.get(cardId * 10 + i + 1);
        if (o != null) {
            return o; // -1 = không có aura ở cấp này
        }
        return switch (cardId) {
            case 956 -> 0;
            case 1204 -> 1;
            case OOZARU -> new short[]{2, 3, 4}[i];
            case 2040 -> new short[]{120, 121, 122}[i];
            default -> -1;
        };
    }

    /** Điểm so sánh "thẻ có chỉ số cao nhất": tổng chỉ số các dòng đã mở tới cấp hiện tại. */
    public static int cardScore(Card card, int lv) {
        int s = 0;
        if (card.Options != null) {
            for (OptionCard o : card.Options) {
                if (o.active < lv) {
                    s += o.param;
                }
            }
        }
        return s * 10 + lv;
    }

    /**
     * Thẻ đổi icon và hình theo cấp: {icon, head, body, leg} cho Lv.1 / Lv.2 / Lv.3 (đang gom mảnh dùng Lv.1).
     * Oozaru: 3 dạng của 3 thẻ Oozaru cũ đã gộp. Khỉ con: Goku GT Kid -> Goku GT -> Goku cận vô cực.
     */
    private static final java.util.Map<Integer, int[][]> LEVEL_LOOK = java.util.Map.of(
            OOZARU, new int[][]{{14247, 1727, 1728, 1729}, {14248, 1730, 1731, 1732}, {14249, 1733, 1734, 1735}},
            2040, new int[][]{{16482, 1977, 1978, 1979}, {16520, 1947, 1948, 1949}, {16976, 1980, 1981, 1982}});

    public static int[] levelLook(int cardId, int level) {
        int[][] l = LEVEL_LOOK.get(cardId);
        return l == null ? null : l[Math.max(0, Math.min(2, level - 1))];
    }

    public void sendRadar(Player pl, List<Card> cards) {
        try {
            Message m = new Message(127);
            m.writer().writeByte(0);
            m.writer().writeShort(RadarService.gI().RADAR_TEMPLATE.size());
            for (RadarCard radar : RadarService.gI().RADAR_TEMPLATE) {
                Card card = cards.stream().filter(c -> c.Id == radar.Id).findFirst().orElse(null);
                if (card == null) {
                    card = new Card(radar.Max, radar.Options);
                }
                int[] look = levelLook(radar.Id, card.Level);
                m.writer().writeShort(radar.Id);
                m.writer().writeShort(look != null ? look[0] : radar.IconId);
                m.writer().writeByte(radar.Rank);
                m.writer().writeByte(card.Amount);  //amount
                m.writer().writeByte(card.MaxAmount);  //max_amount
                m.writer().writeByte(radar.Type);  //type 0: monster, 1: charpart
                switch (radar.Type) {
                    case 0:
                        m.writer().writeShort(radar.Template); //Monster
                        break;
                    case 1:
                        m.writer().writeShort(look != null ? look[1] : radar.Head); //Head
                        m.writer().writeShort(look != null ? look[2] : radar.Body); //Body
                        m.writer().writeShort(look != null ? look[3] : radar.Leg); //Leg
                        m.writer().writeShort(radar.Bag); //bag
                        break;
                }
                m.writer().writeUTF(radar.Name);  //name
                m.writer().writeUTF(radar.Info);  //info
                m.writer().writeByte(card.Level);  //Level
                m.writer().writeByte(card.Used);  //use
                m.writer().writeByte(radar.Options.size());  //option radar
                for (OptionCard option : radar.Options) {
                    // client long (DragonBoy249, J2ME, Unity) đọc chỉ số (short id, int param): ghi byte / short làm lệch gói, Rađa trống
                    m.writeItemOption(option.id, option.param);
                    m.writer().writeByte(option.active);  //ActiveCard
                }
            }
            m.writer().flush();
            pl.sendMessage(m);
            m.cleanup();
        } catch (Exception e) {
        }
    }

    public void Radar1(Player pl, short id, int use) {
        try {
            Message message = new Message(127);
            message.writer().writeByte(1);
            message.writer().writeShort(id);
            message.writer().writeByte(use);
            message.writer().flush();
            pl.sendMessage(message);
            message.cleanup();
        } catch (Exception e) {
        }
    }

    public void RadarSetLevel(Player pl, int id, int level) {
        try {
            Message message = new Message(127);
            message.writer().writeByte(2);
            message.writer().writeShort(id);
            message.writer().writeByte(level);
            message.writer().flush();
            pl.sendMessage(message);
            message.cleanup();
        } catch (Exception e) {
        }
    }

    public void RadarSetAmount(Player pl, int id, int amount, int max_amount) {
        try {
            Message message = new Message(127);
            message.writer().writeByte(3);
            message.writer().writeShort(id);
            message.writer().writeByte(amount);
            message.writer().writeByte(max_amount);
            message.writer().flush();
            pl.sendMessage(message);
            message.cleanup();
        } catch (Exception e) {
        }
    }

    public void sendAura(Player pl, int id_Aura, int id_Eff_Set_Item) {
        try {
            Message message = new Message(127);
            message.writer().writeByte(4);
            message.writer().writeInt((int) pl.id);
            message.writer().writeShort(id_Aura);
            message.writer().writeByte(id_Eff_Set_Item);
            Service.gI().sendMessAllPlayer(message);
            message.cleanup();
        } catch (Exception e) {
        }
    }
}
