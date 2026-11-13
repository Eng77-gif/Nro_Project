package nro.models.Bot;

import java.util.ArrayDeque;
import java.util.Deque;
import nro.models.player.Player;
import nro.models.utils.Util;

/**
 * Chat thế giới của bot: câu ghép từ nhiều mẫu theo tình huống thật của bot (bản đồ, việc đang làm, bang, sức mạnh, giờ trong ngày),
 * không lặp câu đã nói gần đây, cả server tối đa 1 câu bot / 1 - 3 phút; thỉnh thoảng trả lời người chơi thật vừa chat thế giới.
 */
public final class BotWorldChat {

    private BotWorldChat() {
    }

    private static final String[] BOSS = {"Broly", "Super Broly", "Fide", "Cooler", "Xên bọ hung", "Black Goku", "Kuku", "Mập đầu đinh",
        "Rambo", "Android 19", "Pic", "Poc", "King Kong", "Số 4", "Tiểu đội sát thủ", "Zamasu", "Baby", "Hirudegarn"};
    private static final String[] ITEM = {"đồ thần", "set kích hoạt", "bông tai cấp 2", "bông tai cấp 3", "đá bảo vệ", "cải trang", "thỏi vàng",
        "ngọc rồng 4 sao", "đồ hủy diệt", "đồ thiên sứ", "sao pha lê", "mảnh bông tai", "capsule kích hoạt", "pet", "ván bay", "đeo lưng"};
    private static final String[] ENDS = {"", "", "", " ae", " mn", " ae ơi", " nha", " nhé", " :))", " =))", " haha", " :v", " ...", " !!"};
    private static final String[][] T = {
        // rủ đi cùng
        {"ai pt săn {boss} không", "có ai đi đánh {boss} cùng không", "{boss} ra chưa", "{boss} khu mấy rồi", "vừa thấy {boss} ở {map}",
            "solo {boss} khó quá, ai phụ với", "tối nay săn {boss} không", "{boss} rớt đồ ngon không vậy"},
        {"cần người up đệ cùng", "ai up bông tai cùng không", "đi NRSD tối nay không", "ai đi doanh trại với bang mình không",
            "rủ ae đi kho báu dưới biển", "ai đi khí gas không", "lập pt luyện tập ở {map} nè"},
        // mua bán
        {"bán {item} giá mềm, ib", "mua {item}, ai có ib mình", "có ai bán {item} không", "đổi {item} lấy {item2} không", "{item} giá bao nhiêu vậy",
            "ký gửi {item} rồi đó, ghé siêu thị xem", "thu mua {item} số lượng lớn"},
        // bang hội
        {"bang {clan} tuyển thành viên, ae vào chơi cùng", "bang {clan} còn chỗ nha", "ai chưa có bang vào {clan} đi", "cần tìm bang hoạt động",
            "bang mình đi doanh trại thiếu người"},
        // kể chuyện / khoe
        {"vừa lên {power} sức mạnh", "đang {act} ở {map}", "up ở {map} mỏi tay quá", "đập đồ tạch 3 lần liền buồn ghê", "vừa ép được sao pha lê",
            "đệ mình mới ra chiêu mới", "hôm nay đen thôi rồi", "ra {item} rồi ae ơi", "{map} đông người ghê", "kẹt nhiệm vụ mãi chưa qua"},
        // hỏi han
        {"có ai biết {boss} mấy giờ ra không", "nhiệm vụ này làm sao vậy ae", "nạp ngọc ở đâu vậy", "up đệ ở đâu nhanh nhất",
            "map nào luyện tập ngon nhất", "{item} lấy ở đâu vậy", "ai chỉ mình cách mở giới hạn sức mạnh với", "event giờ có gì vậy"},
        // tán gẫu
        {"server vui ghê", "{greet}", "{greet}, hôm nay up gì đây", "ai còn thức không", "ngồi buồn quá vào chơi tí", "pk không",
            "ai pk mình ở {map} thế", "khu {zone} {map} vắng quá", "lag quá ae", "game này chơi cuốn thật"}};
    private static final String[][] REPLY = {
        {"boss", "{n} boss đó khu mấy vậy", "{n} cho mình theo với", "{n} mình đang đánh {boss} nè"},
        {"bang", "{n} vào bang {clan} đi", "{n} bang mình còn chỗ", "{n} mình cũng đang tìm bang"},
        {"nrsd", "{n} tối nay đi chứ", "{n} cho mình theo với", "{n} bang mình đi đủ người rồi"},
        {"bán", "{n} bao nhiêu vậy", "{n} ib mình xem", "{n} đắt quá à"},
        {"mua", "{n} mình có nè", "{n} giá bao nhiêu", "{n} mình bán cho"},
        {"pk", "{n} sợ quá :v", "{n} ra {map} solo", "{n} thôi tha mình"},
        {"chào", "chào {n}", "hello {n}", "{n} chào nha"},
        {"", "{n} đúng rồi", "{n} :))", "{n} chuẩn", "{n} ok", "{n} thế à", "{n} nghe hay đó", "{n} ai biết đâu", "{n} hỏi admin đi bạn"}};

    private static final Deque<String> RECENT = new ArrayDeque<>();
    private static long nextAt;
    private static volatile String replyTo, replyText;
    private static volatile long replyAt;

    /** Người chơi thật vừa chat thế giới: một bot có thể đáp lại sau vài giây. */
    public static void onPlayerChat(Player p, String text) {
        if (p == null || p instanceof Bot || p.isPet || text == null || !Util.isTrue(35, 100)) {
            return;
        }
        replyTo = p.name;
        replyText = text.toLowerCase();
        replyAt = System.currentTimeMillis() + Util.nextInt(5, 25) * 1000L;
    }

    /** Gọi mỗi lượt cập nhật của bot (BotEndgame.step). */
    static synchronized void tick(Bot b) {
        long now = System.currentTimeMillis();
        if (b.nPoint.power <= 1_500_000 || b.zone == null || now < nextAt) {
            return;
        }
        String text = null;
        if (replyTo != null && now >= replyAt) {
            if (!replyTo.equals(b.name)) {
                text = reply(b, replyTo, replyText);
            }
            replyTo = null;
        } else if (Util.isTrue(1, 6)) {
            text = make(b);
        }
        if (text == null) {
            return;
        }
        nro.models.services.ChatGlobalService.gI().chat(b, text);
        nextAt = now + Util.nextInt(60, 180) * 1000L;
    }

    /** Nói ngay 1 câu (panel giao việc), bỏ qua nhịp chung. */
    static synchronized void sayNow(Bot b) {
        String t = make(b);
        if (t != null) {
            b.inventory.gem = Math.max(b.inventory.gem, 5); // lệnh admin: không bị chặn vì thiếu ngọc / chờ 30 giây
            b.idMark.setLastTimeChatGlobal(0);
            nro.models.services.ChatGlobalService.gI().chat(b, t);
        }
    }

    private static String reply(Bot b, String name, String said) {
        for (String[] r : REPLY) {
            if (r[0].isEmpty() || said.contains(r[0])) {
                return fill(b, r[Util.nextInt(1, r.length - 1)]).replace("{n}", name);
            }
        }
        return null;
    }

    /** Câu mới chưa nói trong 60 câu gần nhất (thử vài lần). */
    static String make(Bot b) {
        String s = null;
        for (int i = 0; i < 8; i++) {
            String[] g = T[Util.nextInt(0, T.length - 1)];
            String t = g[Util.nextInt(0, g.length - 1)];
            if (t.contains("{clan}") && b.clan == null) {
                continue;
            }
            s = fill(b, t) + ENDS[Util.nextInt(0, ENDS.length - 1)];
            if (!RECENT.contains(s)) {
                break;
            }
        }
        if (s == null) {
            return null;
        }
        RECENT.addLast(s);
        while (RECENT.size() > 60) {
            RECENT.removeFirst();
        }
        return s;
    }

    private static String fill(Bot b, String t) {
        int h = java.time.LocalTime.now().getHour();
        String item = ITEM[Util.nextInt(0, ITEM.length - 1)], item2;
        do {
            item2 = ITEM[Util.nextInt(0, ITEM.length - 1)];
        } while (item2.equals(item));
        return t.replace("{boss}", BOSS[Util.nextInt(0, BOSS.length - 1)])
                .replace("{item2}", item2).replace("{item}", item)
                .replace("{map}", b.zone != null ? b.zone.map.mapName : "làng Aru")
                .replace("{zone}", String.valueOf(b.zone != null ? b.zone.zoneId : 0))
                .replace("{clan}", b.clan != null ? b.clan.name : "mình")
                .replace("{act}", b.activity == null ? "luyện tập" : b.activity.toLowerCase())
                .replace("{power}", nro.models.utils.Util.numberToMoney(b.nPoint.power))
                .replace("{greet}", h < 11 ? "chào buổi sáng" : h < 14 ? "trưa rồi ae ăn cơm chưa" : h < 18 ? "chiều rảnh vào up tí" : h < 23 ? "tối rồi vào đông ghê" : "khuya rồi còn ai on không");
    }
}
