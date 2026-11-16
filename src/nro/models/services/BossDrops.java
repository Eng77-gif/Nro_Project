package nro.models.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Tỉ lệ quà riêng của từng boss (viết trong reward() của mỗi boss), chỉnh trên panel > Tỉ lệ rơi > Boss.
 * Mỗi dòng Util.isTrue(a, b) trong reward() đã thay bằng roll("Gói.Lớp#n", a, b); mặc định = a / b như cũ.
 * Danh sách sinh tự động từ code boss; boss thêm sau này cần thêm dòng vào DEF.
 */
public final class BossDrops {

    /** {key, tên boss, id vật phẩm, nhãn (rỗng = lấy tên vật phẩm), tỉ lệ mặc định %, chỉ tung khi dòng trên không rơi} */
    public static final Object[][] DEF = {
        {"cumber.Cumber#1", "Cumber", new int[]{}, "Đồ thần linh", 5.0, false},
        {"cumber.Cumber#2", "Cumber", new int[]{230,231,232,234,235,236,238,239,240,242,243,244,246,247,248,250,251,252,254,255,256,258,259,260,262,263,264,266,267,268,270,271,272,274,275,276,278,279,280}, "", 5.0, false},
        {"cumber.Cumber#3", "Cumber", new int[]{15,16,17,18,19,20,992}, "", 10.0, false},
        {"doanh_trai.NinjaAoTim#1", "Ninja Áo Tím", new int[]{17}, "", 50.0, false},
        {"doanh_trai.NinjaAoTim#2", "Ninja Áo Tím", new int[]{611}, "", 30.0, false},
        {"doanh_trai.RobotVeSi#1", "Rôbốt Vệ Sĩ 0", new int[]{17}, "", 30.0, false},
        {"doanh_trai.NinjaClone#1", "Ninja Áo Tím", new int[]{17}, "", 10.0, false},
        {"doanh_trai.TrungUyThep#1", "Trung uý Thép", new int[]{17}, "", 50.0, false},
        {"doanh_trai.TrungUyThep#2", "Trung uý Thép", new int[]{611}, "", 30.0, false},
        {"doanh_trai.TrungUyXanhLo#1", "Trung uý Xanh Lơ", new int[]{17}, "", 50.0, false},
        {"doanh_trai.TrungUyXanhLo#2", "Trung uý Xanh Lơ", new int[]{611}, "", 100.0, false},
        {"doanh_trai.TrungUyTrang#1", "Trung uý Trắng", new int[]{17}, "", 50.0, false},
        {"Cold.Cooler#1", "Cooler", new int[]{}, "Đồ thần linh", 5.0, false},
        {"Cold.Cooler#2", "Cooler", new int[]{230,231,232,234,235,236,238,239,240,242,243,244,246,247,248,250,251,252,254,255,256,258,259,260,262,263,264,266,267,268,270,271,272,274,275,276,278,279,280}, "", 5.0, false},
        {"Cold.Cooler#3", "Cooler", new int[]{15,16,17,18,19,20}, "", 80.0, false},
        {"MajinBuu_12h.Goku#1", "Gôku", new int[]{}, "Đồ thần linh", 1.0, false},
        {"MajinBuu_12h.Goku#2", "Gôku", new int[]{230,231,232,234,235,236,238,239,240,242,243,244,246,247,248,250,251,252,254,255,256,258,259,260,262,263,264,266,267,268,270,271,272,274,275,276,278,279,280}, "", 1.0, false},
        {"MajinBuu_12h.Goku#3", "Gôku", new int[]{15,16,17,18,19,20}, "", 10.0, false},
        {"MajinBuu_12h.Drabura#1", "Drabura", new int[]{}, "Đồ thần linh", 1.0, false},
        {"MajinBuu_12h.Drabura#2", "Drabura", new int[]{230,231,232,234,235,236,238,239,240,242,243,244,246,247,248,250,251,252,254,255,256,258,259,260,262,263,264,266,267,268,270,271,272,274,275,276,278,279,280}, "", 1.0, false},
        {"MajinBuu_12h.Drabura#3", "Drabura", new int[]{15,16,17,18,19,20}, "", 10.0, false},
        {"MajinBuu_12h.Yacon#1", "Ya côn", new int[]{}, "Đồ thần linh", 1.0, false},
        {"MajinBuu_12h.Yacon#2", "Ya côn", new int[]{230,231,232,234,235,236,238,239,240,242,243,244,246,247,248,250,251,252,254,255,256,258,259,260,262,263,264,266,267,268,270,271,272,274,275,276,278,279,280}, "", 1.0, false},
        {"MajinBuu_12h.Yacon#3", "Ya côn", new int[]{15,16,17,18,19,20}, "", 10.0, false},
        {"MajinBuu_12h.Mabu#1", "Mabư", new int[]{}, "Đồ thần linh", 1.0, false},
        {"MajinBuu_12h.Mabu#2", "Mabư", new int[]{230,231,232,234,235,236,238,239,240,242,243,244,246,247,248,250,251,252,254,255,256,258,259,260,262,263,264,266,267,268,270,271,272,274,275,276,278,279,280}, "", 1.0, false},
        {"MajinBuu_12h.Mabu#3", "Mabư", new int[]{15,16,17,18,19,20}, "", 10.0, false},
        {"MajinBuu_12h.BuiBui2#1", "Bui Bui", new int[]{}, "Đồ thần linh", 1.0, false},
        {"MajinBuu_12h.BuiBui2#2", "Bui Bui", new int[]{230,231,232,234,235,236,238,239,240,242,243,244,246,247,248,250,251,252,254,255,256,258,259,260,262,263,264,266,267,268,270,271,272,274,275,276,278,279,280}, "", 1.0, false},
        {"MajinBuu_12h.BuiBui2#3", "Bui Bui", new int[]{15,16,17,18,19,20}, "", 10.0, false},
        {"MajinBuu_12h.Drabura3#1", "Drabura", new int[]{}, "Đồ thần linh", 1.0, false},
        {"MajinBuu_12h.Drabura3#2", "Drabura", new int[]{230,231,232,234,235,236,238,239,240,242,243,244,246,247,248,250,251,252,254,255,256,258,259,260,262,263,264,266,267,268,270,271,272,274,275,276,278,279,280}, "", 1.0, false},
        {"MajinBuu_12h.Drabura3#3", "Drabura", new int[]{15,16,17,18,19,20}, "", 10.0, false},
        {"MajinBuu_12h.BuiBui#1", "Bui Bui", new int[]{}, "Đồ thần linh", 1.0, false},
        {"MajinBuu_12h.BuiBui#2", "Bui Bui", new int[]{230,231,232,234,235,236,238,239,240,242,243,244,246,247,248,250,251,252,254,255,256,258,259,260,262,263,264,266,267,268,270,271,272,274,275,276,278,279,280}, "", 1.0, false},
        {"MajinBuu_12h.BuiBui#3", "Bui Bui", new int[]{15,16,17,18,19,20}, "", 10.0, false},
        {"MajinBuu_12h.Drabura2#1", "Drabura", new int[]{}, "Đồ thần linh", 1.0, false},
        {"MajinBuu_12h.Drabura2#2", "Drabura", new int[]{230,231,232,234,235,236,238,239,240,242,243,244,246,247,248,250,251,252,254,255,256,258,259,260,262,263,264,266,267,268,270,271,272,274,275,276,278,279,280}, "", 1.0, false},
        {"MajinBuu_12h.Drabura2#3", "Drabura", new int[]{15,16,17,18,19,20}, "", 10.0, false},
        {"MajinBuu_12h.Cadic#1", "Ca Đít", new int[]{}, "Đồ thần linh", 1.0, false},
        {"MajinBuu_12h.Cadic#2", "Ca Đít", new int[]{230,231,232,234,235,236,238,239,240,242,243,244,246,247,248,250,251,252,254,255,256,258,259,260,262,263,264,266,267,268,270,271,272,274,275,276,278,279,280}, "", 1.0, false},
        {"MajinBuu_12h.Cadic#3", "Ca Đít", new int[]{15,16,17,18,19,20}, "", 10.0, false},
        {"TrungThu.Meowth#1", "Meowth", new int[]{1881}, "", 30.0, false},
        {"TrungThu.Meowth#2", "Meowth", new int[]{1482}, "", 90.0, true},
        {"TrungThu.Meowth#3", "Meowth", new int[]{1895}, "", 50.0, false},
        {"TrungThu.Musashi#1", "Musashi", new int[]{1880}, "", 30.0, false},
        {"TrungThu.Musashi#2", "Musashi", new int[]{1870}, "", 20.0, true},
        {"TrungThu.Musashi#3", "Musashi", new int[]{1892}, "", 30.0, true},
        {"TrungThu.Musashi#4", "Musashi", new int[]{1895}, "", 50.0, false},
        {"TrungThu.Kochiro#1", "Kochiro", new int[]{1879}, "", 90.0, false},
        {"TrungThu.Kochiro#2", "Kochiro", new int[]{1870}, "", 20.0, true},
        {"TrungThu.Kochiro#3", "Kochiro", new int[]{1892}, "", 30.0, true},
        {"TrungThu.Kochiro#4", "Kochiro", new int[]{1895}, "", 50.0, false},
        {"Black_Goku.BlackGoku#1", "Black Goku", new int[]{}, "Đồ thần linh", 5.0, false},
        {"Black_Goku.BlackGoku#2", "Black Goku", new int[]{230,231,232,234,235,236,238,239,240,242,243,244,246,247,248,250,251,252,254,255,256,258,259,260,262,263,264,266,267,268,270,271,272,274,275,276,278,279,280}, "", 5.0, false},
        {"Black_Goku.BlackGoku#3", "Black Goku", new int[]{15,16,17,18,19,20,992}, "", 10.0, false},
        {"Cell.XENCON1#1", "Xên con 1", new int[]{18,19,20}, "", 80.0, false},
        {"Cell.XENCON2#1", "Xên con 2", new int[]{18,19,20}, "", 80.0, false},
        {"Cell.XenBoHung#1", "Xên bọ hung", new int[]{18,19,20}, "", 80.0, false},
        {"Cell.XENCON5#1", "Xên con 5", new int[]{18,19,20}, "", 80.0, false},
        {"Cell.XENCON4#1", "Xên con 4", new int[]{18,19,20}, "", 80.0, false},
        {"Cell.SieuBoHung#1", "Xên Hoàn Thiện", new int[]{}, "Đồ thần linh", 5.0, false},
        {"Cell.SieuBoHung#2", "Xên Hoàn Thiện", new int[]{230,231,232,234,235,236,238,239,240,242,243,244,246,247,248,250,251,252,254,255,256,258,259,260,262,263,264,266,267,268,270,271,272,274,275,276,278,279,280}, "", 30.0, false},
        {"Cell.SieuBoHung#3", "Xên Hoàn Thiện", new int[]{15,16,17,18,19,20}, "", 80.0, false},
        {"Cell.XENCON6#1", "Xên con 6", new int[]{18,19,20}, "", 80.0, false},
        {"Cell.XENCON7#1", "Xên con 7", new int[]{18,19,20}, "", 80.0, false},
        {"Cell.XENCON3#1", "Xên con 3", new int[]{18,19,20}, "", 80.0, false},
        {"ban_do_kho_bau.TrungUyXanhLo#1", "Trung úy Xanh Lơ", new int[]{705}, "", 100.0, false},
        {"Android.Android19#1", "Android 19", new int[]{18,19,20}, "", 80.0, false},
        {"Android.KingKong#1", "King Kong", new int[]{18,19,20}, "", 80.0, false},
        {"Android.Android13#1", "Android 13", new int[]{18,19,20}, "", 80.0, false},
        {"Android.Android14#1", "Android 14", new int[]{18,19,20}, "", 80.0, false},
        {"Android.DrKore#1", "Dr.Kôrê", new int[]{18,19,20}, "", 80.0, false},
        {"Android.Pic#1", "Pic", new int[]{18,19,20}, "", 80.0, false},
        {"Android.Android15#1", "Android 15", new int[]{18,19,20}, "", 80.0, false},
        {"Android.Poc#1", "Poc", new int[]{18,19,20}, "", 80.0, false},
        {"Broly.BrolySuper#1", "SSJ Broly", new int[]{}, "Hộp đá cấp 9 (Hộp đá nâng cấp +9)", 30.0, false},
        {"Broly.BrolySuper#2", "SSJ Broly", new int[]{2055}, "", 10.0, false},
        {"Broly.BrolySuper#3", "SSJ Broly", new int[]{}, "Đồ thần linh", 10.0, false},
        {"Broly.BrolySuper#4", "SSJ Broly", new int[]{14,15,16,17}, "", 50.0, false},
        {"Broly.BrolySuper#5", "SSJ Broly", new int[]{1150,1151,1152,1153,1154}, "", 30.0, false},
        {"Broly.BrolySuper#6", "SSJ Broly", new int[]{}, "Trang bị sao pha lê 1 - 3 sao", 30.0, false},
        {"Broly.BrolySuper#7", "SSJ Broly", new int[]{}, "Trang bị cấp 8 - 11", 30.0, false},
        {"Broly.BrolySuper#8", "SSJ Broly", new int[]{1771}, "", 30.0, false},
        {"Broly.BrolySuper#9", "SSJ Broly", new int[]{}, "Trang bị cấp 11 - 12 từ 5 - 8 sao", 10.0, false},
        {"Broly.BrolySuper#10", "SSJ Broly", new int[]{}, "Set kích hoạt Thần hủy diệt Beerus (đồ cấp 12)", 1.0, false},
        {"Nappa.MapDauDinh#1", "Mập Đầu Đinh", new int[]{18,19,20}, "", 80.0, false},
        {"Nappa.Kuku#1", "Kuku", new int[]{18,19,20}, "", 80.0, false},
        {"Nappa.Rambo#1", "Rambo", new int[]{18,19,20}, "", 80.0, false},
        {"tieu_doi_sat_thu.SO2#1", "Số 2", new int[]{18,19,20}, "", 80.0, false},
        {"tieu_doi_sat_thu.SO1#1", "Số 1", new int[]{18,19,20}, "", 80.0, false},
        {"tieu_doi_sat_thu.SO3#1", "Số 3", new int[]{18,19,20}, "", 80.0, false},
        {"tieu_doi_sat_thu.SO4#1", "Số 4", new int[]{18,19,20}, "", 80.0, false},
        {"tieu_doi_sat_thu.TDT#1", "Tiểu đội trưởng", new int[]{18,19,20}, "", 80.0, false},
        {"Frieza.Fide#1", "Fide đại ca 1", new int[]{18,19,20}, "", 80.0, false},
        {"Boss_mini.Chilled#1", "Chilled", new int[]{14,16}, "", 20.0, false},
        {"Boss_mini.MatTroi#1", "Mặt Trời ", new int[]{1562}, "", 50.0, false},
        {"Boss_mini.RongNhi#1", "Rồng Nhí", new int[]{18,19,20,1821}, "", 20.0, false}};

    private BossDrops() {
    }

    public static boolean roll(String key, int a, int b) {
        double r = DropRates.rateOr("boss:" + key, a * 100.0 / b);
        return ThreadLocalRandom.current().nextDouble(100) < r;
    }

    public static List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] d : DEF) {
            int[] ids = (int[]) d[2];
            StringBuilder name = new StringBuilder((String) d[3]);
            int icon = -1;
            if (ids.length > 0) {
                var t0 = ItemService.gI().getTemplate((short) ids[0]);
                icon = t0 != null ? t0.iconID : -1;
            } else if (name.toString().equals("Đồ thần linh")) {
                icon = ItemService.gI().getTemplate((short) 555).iconID;
            }
            if (name.length() == 0) {
                for (int i = 0; i < ids.length && i < 3; i++) {
                    var t = ItemService.gI().getTemplate((short) ids[i]);
                    name.append(i > 0 ? ", " : "").append(t != null ? t.name : "#" + ids[i]);
                }
                if (ids.length > 3) {
                    name.append("... (").append(ids.length).append(" món, ngẫu nhiên 1)");
                }
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", "boss:" + d[0]);
            m.put("boss", d[1]);
            m.put("icon", icon);
            m.put("name", name.toString());
            m.put("cond", (Boolean) d[5] ? "Chỉ tung khi dòng ngay trên không rơi" : "");
            m.put("def", d[4]);
            m.put("rate", DropRates.rateOr("boss:" + d[0], (Double) d[4]));
            out.add(m);
        }
        return out;
    }
}
