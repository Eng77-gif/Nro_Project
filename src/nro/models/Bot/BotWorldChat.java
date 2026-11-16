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

    // chỉ món giao dịch / ký gửi được (không pet, cải trang, đồ thần...)
    private static final String[] ITEM = {"thỏi vàng", "ngọc rồng 3 sao", "ngọc rồng 4 sao", "ngọc rồng 5 sao", "ngọc rồng 6 sao", "ngọc rồng 7 sao",
        "sao pha lê", "đá bảo vệ", "đá lục bảo", "đá ruby", "đá saphia", "đá titan", "đậu thần cấp 9", "bổ huyết", "cuồng nộ"};
    private static final String[] ENDS = {"", "", "", " ae", " mn", " ae ơi", " nha", " nhé", " :))", " =))", " haha", " :v", " ...", " !!"};
    /*
     * {boss} = boss đang sống thật, {bmap} = bản đồ boss đó đang đứng; câu có {boss} chỉ nói khi bot đủ sức đánh boss đó.
     * {solo}: chỉ nói khi bot đang đứng cùng bản đồ với boss. {clan}/{recruit}: bot có bang ({recruit} = bang còn chỗ). {noclan}: bot chưa có bang.
     */
    private static final String[][] T = {
        // rủ đi cùng
        {"ai pt săn {boss} không", "có ai đi đánh {boss} cùng không", "{boss} đang ở {bmap} kìa", "{boss} khu mấy rồi", "vừa thấy {boss} ở {bmap}",
            "{solo}đánh {boss} khó quá, ai phụ với", "đi săn {boss} không ae"},
        {"cần người up đệ cùng", "ai up bông tai cùng không", "đi NRSD tối nay không", "{clan}ai đi doanh trại với bang mình không",
            "rủ ae đi kho báu dưới biển", "ai đi khí gas không", "lập pt luyện tập ở {map} nè"},
        // mua bán
        {"bán {item} giá mềm, ib", "mua {item}, ai có ib mình", "có ai bán {item} không", "đổi {item} lấy {item2} không", "{item} giá bao nhiêu vậy",
            "thu mua {item} số lượng lớn"},
        // bang hội
        {"{recruit}bang {clan} tuyển thành viên, ae vào chơi cùng", "{recruit}bang {clan} còn chỗ nha", "{recruit}ai chưa có bang vào {clan} đi",
            "{noclan}cần tìm bang hoạt động", "{clan}bang mình đi doanh trại thiếu người"},
        // kể chuyện / khoe
        {"vừa lên {power} sức mạnh", "đang {act} ở {map}", "up ở {map} mỏi tay quá", "đập đồ tạch 3 lần liền buồn ghê",
            "hôm nay đen thôi rồi", "{map} đông người ghê"},
        // hỏi han
        {"nhiệm vụ này làm sao vậy ae", "nạp ngọc ở đâu vậy", "up đệ ở đâu nhanh nhất",
            "map nào luyện tập ngon nhất", "{item} lấy ở đâu vậy", "ai chỉ mình cách mở giới hạn sức mạnh với", "event giờ có gì vậy"},
        // tán gẫu
        {"server vui ghê", "{greet}", "{greet}, hôm nay up gì đây", "ai còn thức không", "ngồi buồn quá vào chơi tí", "pk không",
            "ai pk mình ở {map} thế", "khu {zone} {map} vắng quá", "lag quá ae", "game này chơi cuốn thật"}};
    private static final String[][] REPLY = {
        {"boss", "{n} boss đó khu mấy vậy", "{n} cho mình theo với", "{n} {boss} đang ở {bmap} đó"},
        {"bang", "{recruit}{n} vào bang {clan} đi", "{recruit}{n} bang mình còn chỗ", "{noclan}{n} mình cũng đang tìm bang"},
        {"nrsd", "{n} tối nay đi chứ", "{n} cho mình theo với"},
        {"bán", "{n} bao nhiêu vậy", "{n} ib mình xem", "{n} đắt quá à"},
        {"mua", "{n} mình có nè", "{n} giá bao nhiêu"},
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
                for (int i = 0; i < 4; i++) {
                    String t = fill(b, r[Util.nextInt(1, r.length - 1)]);
                    if (t != null) {
                        return t.replace("{n}", name);
                    }
                }
                return null;
            }
        }
        return null;
    }

    /** Câu mới chưa nói trong 60 câu gần nhất (thử vài lần). */
    static String make(Bot b) {
        String s = null;
        for (int i = 0; i < 8; i++) {
            String[] g = T[Util.nextInt(0, T.length - 1)];
            String f = fill(b, g[Util.nextInt(0, g.length - 1)]);
            if (f == null) {
                continue; // câu không đúng với tình hình thật (không có boss hợp, bang đầy...)
            }
            s = f + ENDS[Util.nextInt(0, ENDS.length - 1)];
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

    /** Ghép câu theo tình hình thật của bot; null nếu câu không đúng (bang đầy, không có boss hợp sức...). */
    private static String fill(Bot b, String t) {
        if (t.startsWith("{recruit}")) {
            if (b.clan == null || b.clan.members.size() >= b.clan.maxMember) {
                return null;
            }
            t = t.substring(9);
        } else if (t.startsWith("{clan}")) {
            if (b.clan == null) {
                return null;
            }
            t = t.substring(6);
        } else if (t.startsWith("{noclan}")) {
            if (b.clan != null) {
                return null;
            }
            t = t.substring(8);
        }
        boolean solo = t.startsWith("{solo}");
        if (solo) {
            t = t.substring(6);
        }
        if (t.contains("{boss}")) {
            nro.models.boss.Boss boss = liveBoss(b, solo);
            if (boss == null) {
                return null;
            }
            t = t.replace("{boss}", boss.name).replace("{bmap}", boss.zone.map.mapName);
        }
        int h = java.time.LocalTime.now().getHour();
        String item = ITEM[Util.nextInt(0, ITEM.length - 1)], item2;
        do {
            item2 = ITEM[Util.nextInt(0, ITEM.length - 1)];
        } while (item2.equals(item));
        return t.replace("{item2}", item2).replace("{item}", item)
                .replace("{map}", b.zone != null ? b.zone.map.mapName : "làng Aru")
                .replace("{zone}", String.valueOf(b.zone != null ? b.zone.zoneId : 0))
                .replace("{clan}", b.clan != null ? b.clan.name : "mình")
                .replace("{act}", b.activity == null ? "luyện tập" : b.activity.toLowerCase())
                .replace("{power}", nro.models.utils.Util.numberToMoney(b.nPoint.power))
                .replace("{greet}", h < 11 ? "chào buổi sáng" : h < 14 ? "trưa rồi ae ăn cơm chưa" : h < 18 ? "chiều rảnh vào up tí" : h < 23 ? "tối rồi vào đông ghê" : "khuya rồi còn ai on không");
    }

    /** Boss đang sống thật mà bot đủ sức đánh (máu boss không quá 50 lần sức đánh bot); sameMap: phải cùng bản đồ với bot. */
    private static nro.models.boss.Boss liveBoss(Bot b, boolean sameMap) {
        java.util.List<nro.models.boss.Boss> ok = new java.util.ArrayList<>();
        try {
            java.util.List<nro.models.boss.Boss> all = new java.util.ArrayList<>(nro.models.boss.Boss_Manager.BossManager.gI().getBosses());
            all.addAll(nro.models.boss.Boss_Manager.BrolyManager.gI().getBosses());
            for (nro.models.boss.Boss x : all) {
                if (x == null || x.isDie() || x.zone == null || x.nPoint == null || x.name == null) {
                    continue;
                }
                if (sameMap && (b.zone == null || b.zone.map.mapId != x.zone.map.mapId)) {
                    continue;
                }
                if (x.nPoint.hpMax <= (long) b.nPoint.dame * 50 && b.nPoint.hpMax >= x.nPoint.dame) {
                    ok.add(x);
                }
            }
        } catch (Exception ignored) {
        }
        return ok.isEmpty() ? null : ok.get(Util.nextInt(0, ok.size() - 1));
    }
}
