package nro.models.services_func;

import java.util.ArrayList;
import java.util.List;
import nro.models.data.LocalManager;
import nro.models.map.service.ChangeMapService;
import nro.models.player.Inventory;
import nro.models.player.Player;
import nro.models.server.Client;
import nro.models.server.Manager;
import nro.models.services.NpcService;
import nro.models.services.PlayerService;
import nro.models.services.Service;
import nro.models.utils.Logger;

/**
 * Công cụ admin trong game (gõ "a" → "Công cụ admin"): thông báo toàn server, tặng tiền, hồi đầy, dịch chuyển,
 * danh sách online, EXP server, nạp lại dữ liệu, khóa / mở khóa tài khoản (kể cả khi người chơi offline).
 * Mọi thao tác ghi vào log để đối chiếu.
 *
 * @author Việt
 */
public class AdminTools {

    public static final int MENU_MAIN = 77_001;
    public static final int MENU_EXP = 77_002;
    public static final int MENU_RELOAD = 77_003;

    public static final int INPUT_ANNOUNCE = 77_101;
    public static final int INPUT_MONEY = 77_102;
    public static final int INPUT_TELEPORT = 77_103;
    public static final int INPUT_BAN = 77_104;
    public static final int INPUT_QL_POINT = 77_105;

    private static final int[] EXP_RATES = {1, 2, 3, 5, 10};

    private static AdminTools instance;

    public static AdminTools gI() {
        if (instance == null) {
            instance = new AdminTools();
        }
        return instance;
    }

    public void showMain(Player pl) {
        if (!pl.isAdmin()) {
            return;
        }
        NpcService.gI().createMenuConMeo(pl, MENU_MAIN, -1, "|7|Công cụ admin\n|1|Online: " + Client.gI().getPlayers().size()
                + " · EXP server x" + Manager.RATE_EXP_SERVER,
                "Thông báo\ntoàn server", "Tặng vàng\nngọc", "Hồi đầy\nHP KI", "Dịch chuyển\nmap", "Người chơi\nonline",
                "EXP\nserver", "Nạp lại\ndữ liệu", "Khóa / mở\ntài khoản", "Tặng điểm\nQuy Lão");
    }

    /** Trả về true nếu menu thuộc công cụ admin (đã xử lý). */
    public boolean confirmMenu(Player pl, int menu, int select) {
        if (menu != MENU_MAIN && menu != MENU_EXP && menu != MENU_RELOAD) {
            return false;
        }
        if (!pl.isAdmin()) {
            return true;
        }
        switch (menu) {
            case MENU_MAIN -> {
                switch (select) {
                    case 0 -> Input.gI().createForm(pl, INPUT_ANNOUNCE, "Thông báo toàn server", new Input.SubInput("Nội dung", Input.ANY));
                    case 1 -> Input.gI().createForm(pl, INPUT_MONEY, "Tặng vàng / ngọc / hồng ngọc", new Input.SubInput("Tên người chơi", Input.ANY),
                            new Input.SubInput("Vàng", Input.NUMERIC), new Input.SubInput("Ngọc", Input.NUMERIC), new Input.SubInput("Hồng ngọc", Input.NUMERIC));
                    case 2 -> {
                        pl.nPoint.setFullHpMp();
                        PlayerService.gI().sendInfoHpMp(pl);
                        Service.gI().point(pl);
                        Service.gI().sendThongBao(pl, "Đã hồi đầy HP / KI");
                    }
                    case 3 -> Input.gI().createForm(pl, INPUT_TELEPORT, "Dịch chuyển", new Input.SubInput("Mã map", Input.NUMERIC), new Input.SubInput("Khu (-1: tự chọn)", Input.ANY));
                    case 4 -> showOnline(pl);
                    case 5 -> {
                        String[] opts = new String[EXP_RATES.length];
                        for (int i = 0; i < opts.length; i++) {
                            opts[i] = "x" + EXP_RATES[i];
                        }
                        NpcService.gI().createMenuConMeo(pl, MENU_EXP, -1, "EXP server đang x" + Manager.RATE_EXP_SERVER
                                + "\n(muốn giữ sau khi khởi động lại thì sửa server.expserver trên panel)", opts);
                    }
                    case 6 -> NpcService.gI().createMenuConMeo(pl, MENU_RELOAD, -1, "Nạp lại dữ liệu từ database (không cần khởi động lại)",
                            "Cửa hàng", "Chỉ số\nvật phẩm", "Thưởng boss", "Hộp quà", "Sự kiện", "Tất cả");
                    case 8 -> Input.gI().createForm(pl, INPUT_QL_POINT, "Tặng điểm Quy Lão (số âm để trừ)", new Input.SubInput("Tên người chơi", Input.ANY),
                            new Input.SubInput("Điểm sự kiện (đổi thưởng)", Input.ANY), new Input.SubInput("Điểm đua top ngọc rồng", Input.ANY));
                    case 7 -> Input.gI().createForm(pl, INPUT_BAN, "Khóa / mở khóa tài khoản", new Input.SubInput("Tên nhân vật", Input.ANY),
                            new Input.SubInput("1 = khóa, 0 = mở", Input.NUMERIC));
                }
            }
            case MENU_EXP -> {
                if (select >= 0 && select < EXP_RATES.length) {
                    Manager.RATE_EXP_SERVER = (byte) EXP_RATES[select];
                    Service.gI().sendThongBaoAllPlayer("Máy chủ: EXP x" + EXP_RATES[select]);
                    log(pl, "EXP server x" + EXP_RATES[select]);
                }
            }
            case MENU_RELOAD -> reload(pl, select);
        }
        return true;
    }

    /** Trả về true nếu form thuộc công cụ admin (đã xử lý). */
    public boolean doInput(Player pl, int type, String[] text) {
        if (type < INPUT_ANNOUNCE || type > INPUT_QL_POINT) {
            return false;
        }
        if (!pl.isAdmin()) {
            return true;
        }
        try {
            switch (type) {
                case INPUT_ANNOUNCE -> {
                    String t = text[0].trim();
                    if (!t.isEmpty()) {
                        Service.gI().sendThongBaoAllPlayer(t);
                        log(pl, "thông báo: " + t);
                    }
                }
                case INPUT_MONEY -> giveMoney(pl, text[0].trim(), num(text[1]), num(text[2]), num(text[3]));
                case INPUT_TELEPORT -> {
                    int map = (int) num(text[0]);
                    int zone = text.length > 1 && !text[1].isBlank() ? (int) num(text[1]) : -1;
                    ChangeMapService.gI().changeMapInYard(pl, map, zone, -1);
                }
                case INPUT_BAN -> ban(pl, text[0].trim(), num(text[1]) != 0);
                case INPUT_QL_POINT -> giveQlPoint(pl, text[0].trim(), num(text[1]), text.length > 2 ? num(text[2]) : 0);
            }
        } catch (NumberFormatException e) {
            Service.gI().sendThongBao(pl, "Sai định dạng số");
        }
        return true;
    }

    private static long num(String s) {
        s = s == null ? "" : s.trim().replaceAll("[.,\\s+]", "");
        return s.isEmpty() ? 0 : Long.parseLong(s);
    }

    private void giveMoney(Player pl, String name, long gold, long gem, long ruby) {
        Player p = Client.gI().getPlayer(name);
        if (p == null) {
            Service.gI().sendThongBao(pl, "Người chơi không online (offline thì tặng trên panel → Người chơi)");
            return;
        }
        if (gold < 0 || gem < 0 || ruby < 0) {
            Service.gI().sendThongBao(pl, "Số không được âm");
            return;
        }
        p.inventory.gold = Math.min(Inventory.LIMIT_GOLD, p.inventory.gold + gold);
        p.inventory.gem = (int) Math.min(Integer.MAX_VALUE, (long) p.inventory.gem + gem);
        p.inventory.ruby = (int) Math.min(Integer.MAX_VALUE, (long) p.inventory.ruby + ruby);
        Service.gI().sendMoney(p);
        Service.gI().sendThongBao(p, "Bạn nhận được quà từ admin: " + gold + " vàng, " + gem + " ngọc, " + ruby + " hồng ngọc");
        Service.gI().sendThongBao(pl, "Đã tặng " + p.name);
        log(pl, "tặng " + p.name + " vàng " + gold + ", ngọc " + gem + ", hồng ngọc " + ruby);
    }

    /** Tặng / trừ điểm Quy Lão: điểm sự kiện (đổi thưởng ở Quy Lão) và điểm đua top tặng ngọc rồng. */
    private void giveQlPoint(Player pl, String name, long event, long top) {
        Player p = Client.gI().getPlayer(name);
        if (p == null) {
            Service.gI().sendThongBao(pl, "Người chơi không online");
            return;
        }
        int ev = (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, event));
        if (ev != 0) {
            int cur = p.event.getEventPoint();
            p.event.addEventPoint(Math.max(-cur, ev)); // không cho âm
        }
        if (top != 0) {
            p.qlPoints = (int) Math.max(0, Math.min(Integer.MAX_VALUE, (long) p.qlPoints + top));
        }
        Service.gI().sendThongBao(p, "Admin " + (event >= 0 && top >= 0 ? "tặng" : "điều chỉnh") + " điểm Quy Lão: điểm sự kiện "
                + p.event.getEventPoint() + ", điểm đua top " + p.qlPoints);
        Service.gI().sendThongBao(pl, p.name + ": điểm sự kiện " + p.event.getEventPoint() + ", điểm đua top " + p.qlPoints);
        log(pl, "điểm Quy Lão " + p.name + " sự kiện " + event + ", đua top " + top);
    }

    private void showOnline(Player pl) {
        List<Player> list = new ArrayList<>(Client.gI().getPlayers());
        list.sort((a, b) -> Long.compare(b.nPoint.power, a.nPoint.power));
        StringBuilder sb = new StringBuilder("|7|Online: " + list.size() + "\n|1|");
        int n = 0;
        for (Player p : list) {
            if (++n > 40) {
                sb.append("... và ").append(list.size() - 40).append(" người khác");
                break;
            }
            sb.append(n).append(". ").append(p.name).append(" - ").append(p.zone != null ? p.zone.map.mapName + " k" + p.zone.zoneId : "đang đổi map")
                    .append(" - SM ").append(nro.models.utils.Util.numberToMoney(p.nPoint.power)).append('\n');
        }
        NpcService.gI().createTutorial(pl, -1, sb.toString());
    }

    private void ban(Player pl, String name, boolean ban) {
        try {
            long acc = -1;
            try (java.sql.Connection con = LocalManager.getConnection();
                    java.sql.PreparedStatement ps = con.prepareStatement("select account_id from player where name = ?")) {
                ps.setString(1, name);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        acc = rs.getLong(1);
                    }
                }
            }
            if (acc < 0) {
                Service.gI().sendThongBao(pl, "Không có nhân vật " + name);
                return;
            }
            LocalManager.executeUpdate("update account set ban = ? where id = ?", ban ? 1 : 0, acc);
            Player p = Client.gI().getPlayer(name);
            if (ban && p != null) {
                PlayerService.gI().banPlayer(p);
            }
            Service.gI().sendThongBao(pl, (ban ? "Đã khóa " : "Đã mở khóa ") + name);
            log(pl, (ban ? "khóa " : "mở khóa ") + name);
        } catch (Exception e) {
            Service.gI().sendThongBao(pl, "Lỗi: " + e.getMessage());
        }
    }

    private void reload(Player pl, int select) {
        List<String> done = new ArrayList<>();
        try (java.sql.Connection con = LocalManager.getConnection()) {
            if (select == 0 || select == 5) {
                Manager.SHOPS = nro.models.database.ShopDAO.getShops(con);
                done.add("cửa hàng");
            }
            if (select == 1 || select == 5) {
                Manager.gI().reloadTemplateOptions();
                done.add("chỉ số vật phẩm");
            }
            if (select == 2 || select == 5) {
                nro.models.services.BossRewardService.gI().load(con);
                done.add("thưởng boss");
            }
            if (select == 3 || select == 5) {
                nro.models.services.Event2026Service.gI().reloadBoxes();
                done.add("hộp quà");
            }
            if (select == 4 || select == 5) {
                nro.models.services.SeasonEventService.gI().load();
                done.add("sự kiện");
            }
        } catch (Exception e) {
            Service.gI().sendThongBao(pl, "Lỗi nạp lại: " + e.getMessage());
            return;
        }
        Service.gI().sendThongBao(pl, "Đã nạp lại: " + String.join(", ", done));
        log(pl, "nạp lại " + String.join(", ", done));
    }

    private static void log(Player pl, String what) {
        Logger.warning("[ADMIN " + pl.name + "] " + what + "\n");
    }
}
