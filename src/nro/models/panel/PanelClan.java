package nro.models.panel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nro.models.clan.Clan;
import nro.models.clan.ClanMember;
import nro.models.data.LocalManager;
import nro.models.player.Player;
import nro.models.server.Manager;
import nro.models.services.ClanService;
import nro.models.services.Service;

/**
 * Trang Bang hội của panel: đọc / sửa bang đang chạy trong bộ nhớ (Manager.CLANS), không sửa thẳng bảng clan
 * (lần lưu bang tiếp theo sẽ ghi đè). Mọi thay đổi gửi lại cho thành viên đang online như khi bang chủ tự làm trong game.
 */
final class PanelClan {

    private static final String[] ROLE = {"Bang chủ", "Phó bang", "Thành viên"};

    private PanelClan() {
    }

    private static Clan find(int id) {
        for (Clan c : new ArrayList<>(Manager.CLANS)) {
            if (c.id == id) {
                return c;
            }
        }
        throw new IllegalArgumentException("Không tìm thấy bang " + id);
    }

    private static int flagIcon(Clan c) {
        try {
            var f = nro.models.services.FlagBagService.gI().getFlagBag(c.imgId);
            return f != null ? f.iconId : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private static Map<String, Object> member(Clan c, ClanMember m, java.util.Set<Long> bots) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", m.id);
        r.put("name", m.name);
        r.put("role", m.role);
        r.put("roleName", m.role >= 0 && m.role < ROLE.length ? ROLE[m.role] : "?");
        r.put("power", m.powerPoint);
        r.put("donate", m.donate);
        r.put("receiveDonate", m.receiveDonate);
        r.put("memberPoint", m.memberPoint);
        r.put("clanPoint", m.clanPoint);
        r.put("joinTime", (long) m.joinTime * 1000);
        r.put("days", m.getNumDateFromJoinTimeToToday());
        r.put("avatar", PanelApi.avatarOfHead(m.head));
        r.put("online", c.getPlayerOnline(m.id) != null);
        r.put("bot", bots.contains((long) m.id));
        return r;
    }

    /** Danh sách bang (sắp theo sức mạnh) kèm bang chủ, số thành viên / online / bot, 5 ảnh thành viên mạnh nhất. */
    static Map<String, Object> list(String q) {
        String key = q == null ? "" : q.trim().toLowerCase();
        java.util.Set<Long> bots = nro.models.Bot.BotWorld.gI().accountBots(false).keySet();
        List<Map<String, Object>> out = new ArrayList<>();
        long totalMembers = 0, online = 0;
        for (Clan c : new ArrayList<>(Manager.CLANS)) {
            List<ClanMember> ms = new ArrayList<>(c.members);
            totalMembers += ms.size();
            int on = 0, nb = 0;
            long power = 0;
            ClanMember leader = null;
            for (ClanMember m : ms) {
                power += m.powerPoint;
                if (c.getPlayerOnline(m.id) != null) {
                    on++;
                }
                if (bots.contains((long) m.id)) {
                    nb++;
                }
                if (m.role == Clan.LEADER) {
                    leader = m;
                }
            }
            online += on;
            if (!key.isEmpty() && !(c.name + " " + c.id + " " + (leader != null ? leader.name : "")).toLowerCase().contains(key)) {
                continue;
            }
            ms.sort((a, b) -> Long.compare(b.powerPoint, a.powerPoint));
            List<Integer> faces = new ArrayList<>();
            for (int i = 0; i < Math.min(5, ms.size()); i++) {
                faces.add(PanelApi.avatarOfHead(ms.get(i).head));
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", c.id);
            r.put("name", c.name);
            r.put("slogan", c.slogan);
            r.put("level", c.level);
            r.put("flag", flagIcon(c));
            r.put("members", ms.size());
            r.put("maxMember", c.maxMember);
            r.put("online", on);
            r.put("bots", nb);
            r.put("power", power);
            r.put("clanPoint", c.capsuleClan);
            r.put("leader", leader != null ? leader.name : "");
            r.put("leaderId", leader != null ? leader.id : -1);
            r.put("leaderAvatar", leader != null ? PanelApi.avatarOfHead(leader.head) : -1);
            r.put("faces", faces);
            r.put("created", (long) c.createTime * 1000);
            r.put("inDungeon", c.doanhTrai != null || c.BanDoKhoBau != null || c.ConDuongRanDoc != null || c.KhiGasHuyDiet != null);
            out.add(r);
        }
        out.sort((a, b) -> Long.compare((long) b.get("power"), (long) a.get("power")));
        for (int i = 0; i < out.size(); i++) {
            out.get(i).put("rank", i + 1);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", Manager.CLANS.size());
        m.put("members", totalMembers);
        m.put("online", online);
        m.put("clans", out);
        return m;
    }

    /** Chi tiết 1 bang: thông tin, thành viên, thành tích phó bản, NRSD 30 ngày. */
    static Map<String, Object> detail(int id) throws Exception {
        Clan c = find(id);
        java.util.Set<Long> bots = nro.models.Bot.BotWorld.gI().accountBots(false).keySet();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", c.id);
        r.put("name", c.name);
        r.put("slogan", c.slogan);
        r.put("level", c.level);
        r.put("maxMember", c.maxMember);
        r.put("flag", flagIcon(c));
        r.put("capsule", c.capsuleClan);
        r.put("created", (long) c.createTime * 1000);
        r.put("bdkb", c.levelDoneBanDoKhoBau);
        r.put("cdrd", c.levelDoneCDRD);
        r.put("khigas", c.levelDoneKhiGas);
        r.put("doanhTraiToday", c.lastTimeOpenDoanhTrai > 0 && !nro.models.utils.Util.isAfterMidnight(c.lastTimeOpenDoanhTrai));
        List<ClanMember> ms = new ArrayList<>(c.members);
        ms.sort((a, b) -> a.role != b.role ? Byte.compare(a.role, b.role) : Long.compare(b.powerPoint, a.powerPoint));
        List<Map<String, Object>> list = new ArrayList<>();
        long power = 0;
        for (ClanMember m : ms) {
            list.add(member(c, m, bots));
            power += m.powerPoint;
        }
        r.put("power", power);
        r.put("members", list);
        try {
            r.put("nrsd", PanelApi.rows("select star, count(*) n, max(played_at) last from nrsd_result where clan_id = ? and played_at >= now() - interval 30 day group by star order by star", c.id));
        } catch (Exception e) {
            r.put("nrsd", List.of());
        }
        return r;
    }

    /** Sửa tên, khẩu hiệu, cấp, số thành viên tối đa. */
    static Map<String, Object> edit(Map<String, Object> b) {
        Clan c = find(PanelApi.intv(b.get("id")));
        String name = PanelApi.str(b.get("name")).trim(), slogan = PanelApi.str(b.get("slogan")).trim();
        if (!name.isEmpty()) {
            if (name.length() > 30) {
                throw new IllegalArgumentException("Tên bang tối đa 30 ký tự");
            }
            for (Clan o : new ArrayList<>(Manager.CLANS)) {
                if (o != c && o.name != null && o.name.equalsIgnoreCase(name)) {
                    throw new IllegalArgumentException("Đã có bang tên " + name);
                }
            }
            c.name = name;
        }
        if (b.containsKey("slogan")) {
            c.slogan = slogan.length() > 60 ? slogan.substring(0, 60) : slogan;
        }
        if (b.get("level") != null && !PanelApi.str(b.get("level")).isEmpty()) {
            c.level = Math.max(1, Math.min(100, PanelApi.intv(b.get("level"))));
        }
        if (b.get("maxMember") != null && !PanelApi.str(b.get("maxMember")).isEmpty()) {
            c.maxMember = (byte) Math.max(c.members.size(), Math.min(100, PanelApi.intv(b.get("maxMember"))));
        }
        c.update();
        c.sendMyClanForAllMember();
        return Map.of("ok", true, "note", "Đã lưu bang " + c.name);
    }

    /** Đuổi thành viên (không đuổi bang chủ: nhường chức trước). */
    static Map<String, Object> kick(int id, int memberId) {
        Clan c = find(id);
        ClanMember cm = c.getClanMember(memberId);
        if (cm == null) {
            throw new IllegalArgumentException("Không có thành viên này");
        }
        if (cm.role == Clan.LEADER) {
            throw new IllegalArgumentException("Không đuổi được bang chủ, hãy chuyển bang chủ cho người khác trước");
        }
        Player pl = c.getPlayerOnline(memberId);
        c.removeClanMember(cm);
        c.removeMemberOnline(cm, pl);
        if (pl != null) {
            pl.clan = null;
            pl.clanMember = null;
            ClanService.gI().sendMyClan(pl);
            ClanService.gI().sendClanId(pl);
            Service.gI().sendFlagBag(pl);
            Service.gI().sendThongBao(pl, "Bạn đã bị đưa ra khỏi bang " + c.name);
        } else {
            try {
                LocalManager.executeUpdate("update player set clan_id = -1 where id = ?", memberId);
            } catch (Exception ignored) {
            }
        }
        c.update();
        c.sendMyClanForAllMember();
        return Map.of("ok", true, "note", "Đã đưa " + cm.name + " ra khỏi bang");
    }

    /** Đổi chức: 0 bang chủ (bang chủ cũ thành phó bang), 1 phó bang, 2 thành viên. */
    static Map<String, Object> role(int id, int memberId, int role) {
        Clan c = find(id);
        ClanMember cm = c.getClanMember(memberId);
        if (cm == null || role < 0 || role > 2) {
            throw new IllegalArgumentException("Không thực hiện được");
        }
        if (cm.role == Clan.LEADER && role != Clan.LEADER) {
            throw new IllegalArgumentException("Bang phải có bang chủ: hãy chọn người khác làm bang chủ");
        }
        if (role == Clan.LEADER) {
            ClanMember old = c.getLeader();
            if (old != null && old != cm) {
                old.role = Clan.DEPUTY;
            }
        }
        cm.role = (byte) role;
        c.update();
        c.sendMyClanForAllMember();
        return Map.of("ok", true, "note", cm.name + " giờ là " + ROLE[role]);
    }

    /** Giải tán bang (gõ tên bang để xác nhận). */
    static Map<String, Object> dissolve(int id, String confirm) throws Exception {
        Clan c = find(id);
        if (confirm == null || !confirm.trim().equals(c.name)) {
            throw new IllegalArgumentException("Gõ đúng tên bang để xác nhận");
        }
        for (Player pl : new ArrayList<>(c.membersInGame)) {
            if (pl != null) {
                pl.clan = null;
                pl.clanMember = null;
                ClanService.gI().sendMyClan(pl);
                ClanService.gI().sendClanId(pl);
                Service.gI().sendFlagBag(pl);
                Service.gI().sendThongBao(pl, "Bang hội đã bị giải tán");
            }
        }
        LocalManager.executeUpdate("update player set clan_id = -1 where clan_id = ?", c.id);
        c.deleteDB(c.id);
        Manager.CLANS.remove(c);
        return Map.of("ok", true, "note", "Đã giải tán bang " + c.name);
    }
}
