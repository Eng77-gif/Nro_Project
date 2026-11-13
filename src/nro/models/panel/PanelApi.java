package nro.models.panel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import nro.models.boss.Boss;
import nro.models.boss.BossID;
import nro.models.boss.Boss_Manager.BossManager;
import nro.models.boss.Boss_Manager.BrolyManager;
import nro.models.data.LocalManager;
import nro.models.database.PlayerDAO;
import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.player_system.Template.ItemOptionTemplate;
import nro.models.player_system.Template.ItemTemplate;
import nro.models.server.Client;
import nro.models.server.Maintenance;
import nro.models.server.Manager;
import nro.models.server.ServerManager;
import nro.models.server.ServerNotify;
import nro.models.services.ChatGlobalService;
import nro.models.services.Event2026Service;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.SeasonEventService;
import nro.models.services.Service;

/**
 * Các API của panel web. Mỗi hàm trả về một đối tượng (Map / List) để chuyển thành JSON.
 * Người chơi đang online: sửa trực tiếp trên bộ nhớ (lưu khi thoát); offline: sửa thẳng database.
 */
public class PanelApi {

    static final com.google.gson.Gson GSON_PUBLIC = new com.google.gson.GsonBuilder().serializeNulls().create();

    private final PanelServer server;
    private final long startedAt = System.currentTimeMillis();

    PanelApi(PanelServer server) {
        this.server = server;
    }

    Object route(String r, String method, Map<String, String> q, Map<String, Object> b) throws Exception {
        return switch (r) {
            case "me" -> Map.of("ok", true);
            case "stats" -> stats();
            case "players/page" -> playersPage(q);
            case "players" -> players(q.getOrDefault("q", ""), "1".equals(q.get("online")));
            case "player" -> player(lng(q.get("id")));
            case "player/money" -> playerMoney(b);
            case "player/give" -> playerGive(b);
            case "player/kick" -> playerKick(b);
            case "player/stats" -> playerStats(b);
            case "player/task" -> playerTask(b);
            case "player/rename" -> playerRename(b);
            case "player/removeitem" -> playerRemoveItem(b);
            case "player/delete" -> playerDelete(b);
            case "tasks" -> taskList();
            case "bots" -> Map.of("errors", nro.models.Bot.BotWorld.gI().recentErrors());
            case "bot/accounts" -> botAccounts();
            case "bot/account" -> { // bật / tắt tài khoản bot
                nro.models.Bot.BotWorld.gI().setAccountBot(lng(b.get("id")), lng(b.getOrDefault("on", 1)) != 0);
                yield Map.of("ok", true);
            }
            case "bot/create" -> {
                int n = Math.max(1, Math.min(1000, intv(b.getOrDefault("count", 1)))); // tạo liên tục tới 1000 bot
                String nm = str(b.get("name")).trim();
                List<Long> ids = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    ids.add(nro.models.Bot.BotWorld.gI().createBotAccount(n == 1 ? nm : "", intv(b.getOrDefault("gender", -1)),
                            lng(b.getOrDefault("power", 0)), intv(b.getOrDefault("task", 0))));
                }
                yield Map.of("ok", true, "ids", ids);
            }
            case "bot/deleteall" -> {
                if (!"XOA HET".equals(str(b.get("confirm")))) {
                    throw new IllegalArgumentException("Thiếu xác nhận");
                }
                int[] del = nro.models.Bot.BotWorld.gI().deleteAllBotAccounts();
                yield Map.of("ok", true, "deleted", del[0], "failed", del[1], "dissolved", del[2],
                        "note", "Đã xoá " + del[0] + " tài khoản bot" + (del[1] > 0 ? ", lỗi " + del[1] : "") + (del[2] > 0 ? ", giải tán " + del[2] + " bang chỉ có bot" : ""));
            }
            case "bot/delete" -> {
                if (!"XOA".equals(str(b.get("confirm")))) {
                    throw new IllegalArgumentException("Thiếu xác nhận");
                }
                nro.models.Bot.BotWorld.gI().deleteBotAccount(lng(b.get("id")));
                yield Map.of("ok", true);
            }
            case "chatlog" -> ChatLog.list(str(q.getOrDefault("type", "")), str(q.getOrDefault("q", "")));
            case "ai" -> nro.models.Bot.BotBrain.panel();
            case "ai/set" -> {
                for (String k : new String[]{"epsilon", "alpha", "enabled"}) {
                    if (b.get(k) != null) {
                        nro.models.Bot.BotBrain.set(k, String.valueOf(b.get(k)));
                    }
                }
                yield nro.models.Bot.BotBrain.panel();
            }
            case "ai/reset" -> {
                nro.models.Bot.BotBrain.reset();
                yield nro.models.Bot.BotBrain.panel();
            }
            case "bot/all" -> {
                nro.models.Bot.BotWorld.gI().setAllAccountBots(lng(b.getOrDefault("on", 1)) != 0);
                yield Map.of("ok", true);
            }
            case "bot/perms" -> nro.models.Bot.BotWorld.perms(lng(q.getOrDefault("id", "0")));
            case "bot/perm" -> { // id > 0: riêng 1 bot (on -1 = theo cài đặt chung)
                long id = lng(b.getOrDefault("id", 0));
                if (id > 0) {
                    nro.models.Bot.BotWorld.setBotAllow(id, str(b.get("k")), (int) lng(b.getOrDefault("on", 1)));
                } else {
                    nro.models.Bot.BotWorld.setAllow(str(b.get("k")), lng(b.getOrDefault("on", 1)) != 0);
                }
                yield nro.models.Bot.BotWorld.perms(id);
            }
            case "bot/permcfg" -> {
                nro.models.Bot.BotWorld.setConfig(str(b.get("k")), (int) lng(b.getOrDefault("limit", 0)), str(b.getOrDefault("hours", "")));
                yield nro.models.Bot.BotWorld.perms(0);
            }
            case "bot/preset" -> {
                long id = lng(b.getOrDefault("id", 0));
                nro.models.Bot.BotWorld.applyPreset(str(b.get("name")), id);
                yield nro.models.Bot.BotWorld.perms(id);
            }
            case "bot/permreset" -> {
                long id = lng(b.get("id"));
                nro.models.Bot.BotWorld.resetBot(id);
                yield nro.models.Bot.BotWorld.perms(id);
            }
            case "bot/set" -> {
                nro.models.Bot.BotWorld.gI().setTarget(intv(b.get("count")));
                yield Map.of("ok", true);
            }
            case "player/save" -> playerSave(b);
            case "accounts" -> accounts(q.getOrDefault("q", ""));
            case "accounts/page" -> paged(q, "select a.id, a.username, a.ban, a.is_admin, a.vnd, a.tongnap, a.create_time, a.last_time_login, a.ip_address, "
                    + "(select group_concat(p.name) from player p where p.account_id=a.id) players, a.id in " + BOT_ACC + " is_bot",
                    "from account a where (a.username like ? escape '!' or a.id = ? or exists (select 1 from player p where p.account_id=a.id and p.name like ? escape '!'))"
                    + ("bot".equals(q.get("kind")) ? " and a.id in " + BOT_ACC : "human".equals(q.get("kind")) ? " and a.id not in " + BOT_ACC : ""),
                    "a.id desc", like(q), idOf(q), like(q));
            case "clans/page" -> paged(q, "select id, NAME name, slogan, power_point, max_member, clan_point, LEVEL level, create_time",
                    "from clan where NAME like ? escape '!' or id = ?", "power_point desc, id", like(q), idOf(q));
            case "kygui/page" -> paged(q, "select k.id, k.player_id, p.name player, k.item_id, t.name, t.icon_id, k.gold, k.gem, k.quantity, k.isBuy",
                    "from shop_ky_gui k left join player p on p.id=k.player_id left join item_template t on t.id=k.item_id where (t.name like ? escape '!' or p.name like ? escape '!' or k.id = ?)"
                    + ("1".equals(q.get("state")) ? " and k.isBuy = 1" : "0".equals(q.get("state")) ? " and k.isBuy = 0" : ""),
                    "k.id desc", like(q), like(q), idOf(q));
            case "giftcodes/page" -> paged(q, "select id, code, count_left, detail, datecreate, expired", "from giftcode where code like ? escape '!' or id = ?", "id desc", like(q), idOf(q));
            case "mobs/page" -> paged(q, "select id, NAME name, type, hp", "from mob_template where NAME like ? escape '!' or id = ?", "id", like(q), idOf(q));
            case "chatlog/page" -> {
                ChatLog.list("", "x"); // tạo bảng nếu chưa có
                String type = str(q.getOrDefault("type", ""));
                yield type.isBlank()
                        ? paged(q, "select date_format(t, '%d/%m %H:%i:%s') t, type, name, text", "from chat_log where name like ? escape '!' or text like ? escape '!'", "id desc", like(q), like(q))
                        : paged(q, "select date_format(t, '%d/%m %H:%i:%s') t, type, name, text", "from chat_log where type = ? and (name like ? escape '!' or text like ? escape '!')", "id desc", type, like(q), like(q));
            }
            case "tops/page" -> {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> top = new ArrayList<>((List<Map<String, Object>>) tops(str(q.getOrDefault("kind", "power"))));
                for (int i = 0; i < top.size(); i++) {
                    top.set(i, new LinkedHashMap<>(top.get(i)));
                    top.get(i).put("rank", i + 1);
                }
                yield pagedList(top, q);
            }
            case "bosses/page" -> {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> list = new ArrayList<>((List<Map<String, Object>>) ((Map<String, Object>) bosses()).get("bosses"));
                String kw = str(q.getOrDefault("q", "")).trim().toLowerCase(), st = str(q.getOrDefault("state", ""));
                list.removeIf(x -> !kw.isEmpty() && !String.valueOf(x.get("name")).toLowerCase().contains(kw) && !String.valueOf(x.get("id")).equals(kw)
                        && !String.valueOf(x.get("map")).toLowerCase().contains(kw)
                        || st.equals("alive") && !Boolean.TRUE.equals(x.get("alive")) || st.equals("rest") && Boolean.TRUE.equals(x.get("alive"))
                        || st.equals("panel") && !Boolean.TRUE.equals(x.get("panel")));
                Comparator<Map<String, Object>> cmp = switch (str(q.getOrDefault("sort", ""))) {
                    case "hp_desc" -> Comparator.comparingLong((Map<String, Object> x) -> PanelDirectory.numeric(x.get("hpMax"))).reversed();
                    case "name_asc" -> Comparator.comparing((Map<String, Object> x) -> String.valueOf(x.get("name")), String.CASE_INSENSITIVE_ORDER);
                    default -> (x, y) -> Boolean.compare(Boolean.TRUE.equals(y.get("alive")), Boolean.TRUE.equals(x.get("alive"))); // boss đang ở bản đồ lên trước
                };
                list.sort(cmp);
                yield pagedList(list, q);
            }
            case "account/update" -> accountUpdate(b);
            case "account/delete" -> accountDelete(b);
            case "items/page" -> itemsPage(q);
            case "items" -> items(q.getOrDefault("q", ""), q.getOrDefault("type", ""));
            case "item" -> item(intv(q.get("id")));
            case "item/options" -> rows("select o.id, o.option_id, t.NAME name, o.param, o.param_max, o.chance, o.group_id from item_template_option o left join item_option_template t on t.id=o.option_id where o.template_id=? order by o.id", intv(q.get("id")));
            case "item/options/save" -> itemOptionsSave(b);
            case "exchanges" -> rows("select e.id, e.event_id, e.grp, e.name, e.need, e.gold, e.gem, e.ruby, e.cost_point, e.reward_item, t.NAME item_name, t.icon_id, e.reward_qty, e.reward_days, e.perm_rate, e.npc from season_event_exchange e left join item_template t on t.id=e.reward_item where e.event_id=? order by e.grp, e.id", intv(q.get("event")));
            case "exchange/save" -> exchangeSave(b);
            case "exchange/delete" -> exchangeDelete(b);
            case "health" -> health();
            case "db/tables" -> PanelDb.tables();
            case "db/rows" -> PanelDb.rows(str(q.get("table")), intv(q.getOrDefault("page", "0")), intv(q.getOrDefault("size", "50")), str(q.get("q")), str(q.get("sort")), "1".equals(q.get("desc")));
            case "db/update" -> PanelDb.update(b);
            case "db/insert" -> PanelDb.insert(b);
            case "db/delete" -> PanelDb.delete(b);
            case "db/export" -> PanelDb.export(str(q.get("table")), str(q.get("format")));
            case "bossrewards" -> bossRewards();
            case "bossreward/save" -> bossRewardSave(b);
            case "bossreward/delete" -> bossRewardDelete(b);
            case "options" -> options(q.getOrDefault("q", ""));
            case "giftcodes" -> rows("select id, code, count_left, detail, datecreate, expired from giftcode order by id desc");
            case "giftcode/save" -> giftcodeSave(b);
            case "giftcode/delete" -> giftcodeDelete(b);
            case "boxes" -> rows("select b.box_id, t.name, t.icon_id, t.description, count(*) n, sum(b.weight) w from item_box_reward b left join item_template t on t.id=b.box_id group by b.box_id, t.name, t.icon_id, t.description order by t.name, b.box_id");
            case "box" -> boxRows(intv(q.get("id")));
            case "box/save" -> boxSave(b);
            case "box/delete" -> boxDelete(b);
            case "nrsd/page" -> nrsdPage(q);
            case "pets/page" -> petsPage(q);
            case "web/posts/page" -> webPosts(q);
            case "web/post" -> webPost(intv(q.get("id")));
            case "web/post/reply" -> webReply(b);
            case "web/post/status" -> Map.of("ok", update("update web_post set status=?, updated_at=now() where id=?", Math.max(0, Math.min(3, intv(b.get("status")))), lng(b.get("id"))) > 0, "note", "Đã đổi trạng thái");
            case "web/post/pin" -> Map.of("ok", update("update web_post set pinned=1-pinned where id=?", lng(b.get("id"))) > 0, "note", "Đã đổi ghim");
            case "web/post/delete" -> webPostDelete(b);
            case "web/topups/page" -> webTopups(q);
            case "web/banktxn/page" -> paged(q, "select id, sepay_id, gateway, account_number, amount, content, transfer_type, reference_code, txn_time, topup_id, result",
                    "from web_bank_txn where (content like ? escape '!' or ifnull(reference_code,'') like ? escape '!')" + ("1".equals(q.get("unmatched")) ? " and topup_id is null" : ""),
                    "id desc", like(q), like(q));
            case "web/topup/approve" -> webTopupApprove(b);
            case "web/topup/reject" -> Map.of("ok", update("update web_topup set status=2, note=?, updated_at=now() where id=? and status=0", str(b.getOrDefault("note", "Admin từ chối")), lng(b.get("id"))) > 0, "note", "Đã từ chối");
            case "nrsd/summary" -> nrsdSummary();
            case "nrsd/tournament/save" -> nrsdTournamentSave(b);
            case "nrsd/tournament/delete" -> Map.of("ok", update("delete from nrsd_tournament where id=?", lng(b.get("id"))) > 0);
            case "shops" -> rows("select s.id shop_id, s.npc_id, s.tag_name, t.id tab_id, t.NAME tab_name, (select count(*) from item_shop i where i.tab_id=t.id) n from shop s join tab_shop t on t.shop_id=s.id order by s.id, t.id");
            case "shop/items" -> rows("select i.id, i.temp_id, it.name, it.icon_id, i.cost, i.type_sell, i.is_sell, i.is_new from item_shop i left join item_template it on it.id=i.temp_id where i.tab_id=? order by i.id", intv(q.get("tab")));
            case "shop/save" -> shopSave(b);
            case "shop/delete" -> shopDelete(b);
            case "shop/reload" -> shopReload();
            case "seasons" -> seasons();
            case "server" -> serverInfo();
            case "eventtops" -> SeasonEventService.gI().panelTops();
            case "eventtop" -> rows("select e.player_id id, e.name, e.points from season_event_point e where e.top_id = ? and e.player_id not in (select id from player where not (" + Manager.NOT_ADMIN + ")) order by e.points desc, e.player_id limit 100", intv(q.get("id")));
            case "radar" -> radarList();
            case "radar/save" -> radarSave(b);
            case "badges" -> rows("select b.id, b.idEffect effect, b.idItem item, b.NAME name, b.Options options, t.icon_id icon, t.description descr from data_badges b left join item_template t on t.id = b.idItem order by b.id");
            case "badge/save" -> badgeSave(b);
            case "fx" -> fxInfo();
            case "ddos/unban" -> Map.of("result", "Đã gỡ chặn " + nro.models.network.netty.AntiDdos.unbanAll() + " IP");
            case "season/cmd" -> Map.of("result", SeasonEventService.gI().admin(" " + str(b.get("cmd")).trim()));
            case "bosses" -> bosses();
            case "boss/spawn" -> bossSpawn(b);
            case "boss/remove" -> bossRemove(b);
            case "boss/respawn" -> bossRespawn(b);
            case "boss/templates" -> bossTemplates();
            case "notify" -> notify(b);
            case "maintenance" -> maintenance(b);
            case "logs" -> PanelServer.LOG.since(lng(q.getOrDefault("since", "0")));
            case "audit" -> audit();
            case "sql" -> sql(str(b.get("query")));
            case "napthe" -> rows("select n.*, a.username from napthe n left join account a on a.id = n.user_nap order by n.id desc limit 300");
            case "config" -> config();
            case "config/save" -> configSave(b);
            case "maps" -> maps();
            case "map" -> map(intv(q.get("id")));
            case "map/npc/add" -> mapNpcAdd(b);
            case "map/npc/remove" -> mapNpcRemove(b);
            case "npcs" -> rows("select id, NAME name, head, avatar from npc_template order by id");
            case "mobs" -> rows("select id, NAME name, type, hp from mob_template order by id");
            case "player/teleport" -> playerTeleport(b);
            case "giveall" -> giveAll(b);
            case "exprate" -> expRate(b);
            case "tops" -> tops(str(q.getOrDefault("kind", "power")));
            case "clans/live" -> PanelClan.list(q.getOrDefault("q", ""));
            case "clan/detail" -> PanelClan.detail(intv(q.get("id")));
            case "clan/edit" -> PanelClan.edit(b);
            case "clan/kick" -> PanelClan.kick(intv(b.get("id")), intv(b.get("member")));
            case "clan/role" -> PanelClan.role(intv(b.get("id")), intv(b.get("member")), intv(b.get("role")));
            case "clan/dissolve" -> PanelClan.dissolve(intv(b.get("id")), str(b.get("confirm")));
            case "clans" -> rows("select id, NAME name, slogan, power_point, max_member, clan_point, LEVEL level, create_time from clan order by power_point desc limit 300");
            case "kygui" -> rows("select k.id, k.player_id, p.name player, k.item_id, t.name, t.icon_id, k.gold, k.gem, k.quantity, k.isBuy from shop_ky_gui k left join player p on p.id=k.player_id left join item_template t on t.id=k.item_id order by k.id desc limit 300");
            case "flags" -> PanelLooks.flags();
            case "flag/save" -> PanelLooks.flagSave(b);
            case "flag/assign" -> PanelLooks.flagAssign(b);
            case "mounts" -> PanelLooks.mounts();
            case "dataver" -> PanelLooks.dataVersion();
            case "fxbags" -> PanelLooks.effectBags();
            case "auras" -> PanelLooks.auras();
            case "mountimgs" -> PanelLooks.mountImages();
            case "mount/assign" -> PanelLooks.mountAssign(b);
            case "radar/aura" -> PanelLooks.radarAura(b);
            case "flaggallery" -> PanelLooks.flagGallery();
            case "fxdata" -> PanelLooks.effectData(intv(q.get("id")));
            case "dataver/bump" -> PanelLooks.bumpItemVersion();
            default -> throw new IllegalArgumentException("Không có API " + r);
        };
    }

    // ------------------------------------------------------------------ tổng quan

    private Object stats() throws Exception {
        Runtime rt = Runtime.getRuntime();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("server", ServerManager.NAME);
        m.put("startedAt", ServerManager.timeStart);
        m.put("uptimeSec", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        m.put("online", onlinePlayers().size());
        m.put("maxPlayer", Manager.MAX_PLAYER);
        m.put("memUsedMb", (rt.totalMemory() - rt.freeMemory()) / 1048576);
        m.put("memMaxMb", rt.maxMemory() / 1048576);
        m.put("threads", Thread.activeCount());
        m.put("cpuLoad", ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage());
        m.put("cpus", rt.availableProcessors());
        m.put("maintenance", Maintenance.isRunning);
        m.put("bosses", allBosses().stream().filter(x -> x.zone != null && !x.isDie()).count());
        m.put("accounts", scalar("select count(*) from account"));
        m.put("players", scalar("select count(*) from player"));
        Map<Long, Integer> bots = nro.models.Bot.BotWorld.gI().accountBots(false);
        m.put("botPlayers", bots.size());
        m.put("botAccounts", new java.util.HashSet<>(bots.values()).size());
        m.put("botsOnline", nro.models.Bot.BotWorld.gI().bots().size());
        m.put("seasons", SeasonEventService.gI().activeSeasons().stream().map(s -> s.name).toList());
        return m;
    }

    // ------------------------------------------------------------------ người chơi

    static List<Player> onlinePlayers() {
        for (int i = 0; i < 3; i++) {
            try {
                List<Player> l = new ArrayList<>();
                for (Player p : new ArrayList<>(Client.gI().getPlayers())) {
                    if (p != null && p.isPl()) {
                        l.add(p);
                    }
                }
                return l;
            } catch (Exception ignored) {
            }
        }
        return new ArrayList<>();
    }

    private Object players(String kw, boolean onlineOnly) throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        if (onlineOnly) {
            for (Player p : onlinePlayers()) {
                if (kw.isEmpty() || p.name.toLowerCase().contains(kw.toLowerCase())) {
                    out.add(onlineRow(p));
                }
            }
            return out;
        }
        for (Map<String, Object> r : rows("select p.id, p.name, p.gender, p.head, p.items_body, a.username, a.ban, a.is_admin, p.data_point, p.data_inventory, p.data_location from player p left join account a on a.id=p.account_id "
                + "where p.name like ? or a.username like ? or p.id = ? order by p.id desc limit 200", "%" + kw + "%", "%" + kw + "%", kw.matches("\\d+") ? Long.parseLong(kw) : -1)) {
            out.add(directoryRow(r));
        }
        return out;
    }

    private Map<String, Object> directoryRow(Map<String, Object> r) {
            long id = ((Number) r.get("id")).longValue();
            Player on = Client.gI().getPlayerByID((int) id);
            if (on != null) {
                Map<String, Object> o = onlineRow(on);
                o.put("username", r.get("username"));
                o.put("ban", r.get("ban"));
                return o;
            }
            JsonArray pt = arr(r.get("data_point")), inv = arr(r.get("data_inventory")), loc = arr(r.get("data_location"));
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", id);
            o.put("name", r.get("name"));
            o.put("username", r.get("username"));
            o.put("gender", r.get("gender"));
            o.put("avatar", avatarOf(r.get("head"), r.get("items_body")));
            o.put("power", pt != null && pt.size() > 1 ? pt.get(1).getAsLong() : 0);
            o.put("gold", inv != null && inv.size() > 0 ? inv.get(0).getAsLong() : 0);
            o.put("gem", inv != null && inv.size() > 1 ? inv.get(1).getAsLong() : 0);
            o.put("ruby", inv != null && inv.size() > 2 ? inv.get(2).getAsLong() : 0);
            o.put("map", loc != null && loc.size() > 0 ? mapName(loc.get(0).getAsInt()) : "");
            o.put("online", false);
            o.put("ban", r.get("ban"));
            return o;
    }

    private Object playersPage(Map<String,String> params) throws Exception {
        java.util.Set<Long> online=new java.util.HashSet<>();
        for(Player p:onlinePlayers())online.add(p.id);
        Map<Long,Integer> bots=nro.models.Bot.BotWorld.gI().accountBots(false);
        for(Long id:bots.keySet())if(nro.models.Bot.BotWorld.gI().find(id)!=null)online.add(id);
        PanelDirectory.Query query=PanelDirectory.query(params,online,bots.keySet());
        long total=((Number)rows(query.countSql,query.countArgs.toArray()).get(0).get("total")).longValue();
        PanelDirectory.Page page=query.page(total);
        List<Map<String,Object>> list=new ArrayList<>();
        for(Map<String,Object> raw:rows(page.sql,page.args.toArray())){
            long id=((Number)raw.get("id")).longValue();
            Player bot=nro.models.Bot.BotWorld.gI().find(id);
            Map<String,Object> row=bot==null?directoryRow(raw):onlineRow(bot);
            row.put("username",raw.get("username"));row.put("ban",raw.get("ban"));
            row.put("botAccount",bots.containsKey(id));list.add(row);
        }
        return page.result(list);
    }

    private static volatile Map<Integer, Integer> headAvatar;

    /** Ảnh đại diện (icon) của 1 đầu / cải trang theo bảng head_avatar; -1 nếu không có (panel vẽ hình mặc định). */
    public static int avatarOfHead(int head) {
        Map<Integer, Integer> m = headAvatar;
        if (m == null || m.size() != Manager.HEAD_AVATARS.size()) {
            m = new java.util.HashMap<>();
            for (nro.models.player_system.Template.HeadAvatar h : new ArrayList<>(Manager.HEAD_AVATARS)) {
                m.put(h.headId, h.avatarId);
            }
            headAvatar = m;
        }
        Integer av = m.get(head);
        return av != null ? av : headIcon(head);
    }

    private static volatile Map<Integer, Integer> headIcons;

    /** Không có ảnh đại diện (boss, đầu lạ): lấy hình đầu tiên của part đầu (bảng part, TYPE 0). */
    private static int headIcon(int head) {
        Map<Integer, Integer> m = headIcons;
        if (m == null) {
            m = new java.util.HashMap<>();
            try {
                for (Map<String, Object> r : rows("select id, DATA from part where TYPE = 0")) {
                    java.util.regex.Matcher mt = java.util.regex.Pattern.compile("^\\[\\[(\\d+)").matcher(String.valueOf(r.get("DATA")));
                    if (mt.find()) {
                        m.put(((Number) r.get("id")).intValue(), Integer.parseInt(mt.group(1)));
                    }
                }
            } catch (Exception ignored) {
            }
            headIcons = m;
        }
        return m.getOrDefault(head, -1);
    }

    /** Nhân vật offline: đầu trong bảng player, có cải trang (ô 5 của items_body) thì lấy đầu của cải trang. */
    static int avatarOf(Object head, Object itemsBody) {
        int h = head instanceof Number n ? n.intValue() : -1;
        try {
            JsonArray body = arr(itemsBody);
            if (body != null && body.size() > 5) {
                java.util.regex.Matcher mt = java.util.regex.Pattern.compile("^\\[(\\d+)").matcher(body.get(5).getAsString());
                if (mt.find()) {
                    nro.models.player_system.Template.ItemTemplate t = ItemService.gI().getTemplate(Integer.parseInt(mt.group(1)));
                    if (t != null && t.head >= 0) {
                        h = t.head;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return h < 0 ? -1 : avatarOfHead(h);
    }

    private Map<String, Object> onlineRow(Player p) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", p.id);
        o.put("avatar", avatarOfHead(p.getHead()));
        o.put("name", p.name);
        o.put("gender", (int) p.gender);
        o.put("power", p.nPoint.power);
        o.put("gold", p.inventory.gold);
        o.put("gem", p.inventory.gem);
        o.put("ruby", p.inventory.ruby);
        o.put("map", p.zone != null ? p.zone.map.mapName + " (khu " + p.zone.zoneId + ")" : "");
        o.put("ip", p.session != null ? p.session.ipAddress : "");
        o.put("online", true);
        return o;
    }

    private Object player(long id) throws Exception {
        Map<String, Object> o = new LinkedHashMap<>();
        Player on = Client.gI().getPlayerByID((int) id);
        if (on == null) {
            on = nro.models.Bot.BotWorld.gI().find(id); // tài khoản bot đang chơi: xem hành trang thật lúc này
        }
        o.put("botAccount", nro.models.Bot.BotWorld.gI().accountBots(false).containsKey(id));
        List<Map<String, Object>> rs = rows("select p.id, p.name, p.gender, p.account_id, a.username, a.ban, a.is_admin, a.vnd, a.tongnap, a.last_time_login, a.ip_address, p.data_point, p.data_inventory, p.data_location, p.items_bag, p.items_body, p.items_box from player p left join account a on a.id=p.account_id where p.id=?", id);
        if (rs.isEmpty()) {
            throw new IllegalArgumentException("Không có nhân vật " + id);
        }
        Map<String, Object> r = rs.get(0);
        o.putAll(on != null ? onlineRow(on) : directoryRow(r));
        for (String k : new String[]{"account_id", "username", "ban", "is_admin", "vnd", "tongnap", "last_time_login", "ip_address"}) {
            o.put(k, r.get(k));
        }
        // chỉ số gốc + nhiệm vụ (để sửa trên panel)
        Map<String, Object> st = new LinkedHashMap<>();
        Map<String, Object> tk = new LinkedHashMap<>();
        if (on != null) {
            nro.models.player.NPoint n = on.nPoint;
            st.put("limitPower", n.limitPower);
            st.put("power", n.power);
            st.put("tiemNang", n.tiemNang);
            st.put("stamina", n.stamina);
            st.put("maxStamina", n.maxStamina);
            st.put("hpg", n.hpg);
            st.put("mpg", n.mpg);
            st.put("dameg", n.dameg);
            st.put("defg", n.defg);
            st.put("critg", n.critg);
            if (on.playerTask != null && on.playerTask.taskMain != null) {
                tk.put("id", on.playerTask.taskMain.id);
                tk.put("index", on.playerTask.taskMain.index);
                tk.put("count", on.playerTask.taskMain.subTasks.get(on.playerTask.taskMain.index).count);
            }
        } else {
            JsonArray dp = arr(rows("select data_point from player where id=?", id).get(0).get("data_point"));
            if (dp != null) {
                for (Object[] f : STAT_FIELDS) {
                    st.put((String) f[0], dp.get((int) f[1]).getAsLong());
                }
            }
            JsonArray dt = arr(rows("select data_task from player where id=?", id).get(0).get("data_task"));
            if (dt != null && dt.size() >= 3) {
                tk.put("id", dt.get(0).getAsInt());
                tk.put("index", dt.get(1).getAsInt());
                tk.put("count", dt.get(2).getAsInt());
            }
        }
        o.put("stats", st);
        o.put("task", tk);
        if (on != null) {
            o.put("body", items(on.inventory.itemsBody));
            o.put("bag", items(on.inventory.itemsBag));
            o.put("box", items(on.inventory.itemsBox));
        } else {
            o.put("body", dbItems(r.get("items_body")));
            o.put("bag", dbItems(r.get("items_bag")));
            o.put("box", dbItems(r.get("items_box")));
        }
        return o;
    }

    private List<Map<String, Object>> items(List<Item> list) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Item it = list.get(i);
            if (it == null || !it.isNotNullItem()) {
                continue;
            }
            List<String> ops = new ArrayList<>();
            for (Item.ItemOption io : it.itemOptions) {
                ops.add(io.getOptionString());
            }
            out.add(itemRow(i, it.template, it.quantity, ops));
        }
        return out;
    }

    private List<Map<String, Object>> dbItems(Object json) {
        List<Map<String, Object>> out = new ArrayList<>();
        JsonArray a = arr(json);
        if (a == null) {
            return out;
        }
        for (int i = 0; i < a.size(); i++) {
            try {
                JsonArray it = parse(a.get(i).getAsString()).getAsJsonArray();
                int tid = it.get(0).getAsInt();
                if (tid < 0 || tid >= Manager.ITEM_TEMPLATES.size()) {
                    continue;
                }
                List<String> ops = new ArrayList<>();
                for (JsonElement e : parse(it.get(2).getAsString()).getAsJsonArray()) {
                    JsonArray op = parse(e.getAsString()).getAsJsonArray();
                    int oid = op.get(0).getAsInt();
                    ItemOptionTemplate t = oid >= 0 && oid < Manager.ITEM_OPTION_TEMPLATES.size() ? Manager.ITEM_OPTION_TEMPLATES.get(oid) : null;
                    ops.add(t == null ? "#" + oid : t.name.replace("#", op.get(1).getAsString()));
                }
                out.add(itemRow(i, ItemService.gI().getTemplate(tid), it.get(1).getAsInt(), ops));
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private Map<String, Object> itemRow(int slot, ItemTemplate t, int qty, List<String> ops) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("slot", slot);
        o.put("id", t.id);
        o.put("name", t.name);
        o.put("icon", t.iconID);
        o.put("qty", qty);
        o.put("options", ops);
        return o;
    }

    private Object playerMoney(Map<String, Object> b) throws Exception {
        long id = lng(b.get("id"));
        long gold = lng(b.get("gold")), gem = lng(b.get("gem")), ruby = lng(b.get("ruby"));
        Player on = Client.gI().getPlayerByID((int) id);
        if (on != null) {
            on.inventory.gold = Math.max(0, Math.min(nro.models.player.Inventory.LIMIT_GOLD, on.inventory.gold + gold));
            on.inventory.gem = Math.max(0, on.inventory.gem + gem);
            on.inventory.ruby = Math.max(0, on.inventory.ruby + ruby);
            Service.gI().sendMoney(on);
            Service.gI().sendThongBao(on, "Bạn nhận được từ quản trị: " + money(gold, gem, ruby));
            return Map.of("ok", true, "online", true);
        }
        List<Map<String, Object>> rs = rows("select data_inventory from player where id=?", id);
        if (rs.isEmpty()) {
            throw new IllegalArgumentException("Không có nhân vật");
        }
        JsonArray inv = arr(rs.get(0).get("data_inventory"));
        inv.set(0, new com.google.gson.JsonPrimitive(Math.max(0, inv.get(0).getAsLong() + gold)));
        inv.set(1, new com.google.gson.JsonPrimitive(Math.max(0, inv.get(1).getAsLong() + gem)));
        inv.set(2, new com.google.gson.JsonPrimitive(Math.max(0, inv.get(2).getAsLong() + ruby)));
        update("update player set data_inventory=? where id=?", inv.toString(), id);
        return Map.of("ok", true, "online", false);
    }

    private static String money(long gold, long gem, long ruby) {
        List<String> s = new ArrayList<>();
        if (gold != 0) {
            s.add(gold + " vàng");
        }
        if (gem != 0) {
            s.add(gem + " ngọc xanh");
        }
        if (ruby != 0) {
            s.add(ruby + " hồng ngọc");
        }
        return String.join(", ", s);
    }

    private Object playerGive(Map<String, Object> b) throws Exception {
        long id = lng(b.get("id"));
        int itemId = intv(b.get("itemId"));
        int qty = Math.max(1, intv(b.getOrDefault("qty", 1)));
        if (itemId < 0 || itemId >= Manager.ITEM_TEMPLATES.size()) {
            throw new IllegalArgumentException("Không có item " + itemId);
        }
        List<int[]> ops = new ArrayList<>();
        boolean shopOptions = !Boolean.FALSE.equals(b.get("shopOptions"));
        if (shopOptions) {
            for (Item.ItemOption io : ItemService.gI().getListOptionItemShop((short) itemId)) {
                ops.add(new int[]{io.optionTemplate.id, io.param});
            }
        }
        Object extra = b.get("options");
        if (extra instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof List<?> p && p.size() >= 2) {
                    int oid = intv(p.get(0));
                    ops.removeIf(x -> x[0] == oid);
                    ops.add(new int[]{oid, intv(p.get(1))});
                }
            }
        }
        Player on = Client.gI().getPlayerByID((int) id);
        ItemTemplate t = ItemService.gI().getTemplate(itemId);
        if (on != null) {
            Item it = ItemService.gI().createNewItem((short) itemId, qty);
            it.itemOptions.clear();
            for (int[] o : ops) {
                it.itemOptions.add(new Item.ItemOption(o[0], o[1]));
            }
            if (!InventoryService.gI().addItemBag(on, it)) {
                throw new IllegalStateException("Hành trang người chơi đã đầy");
            }
            InventoryService.gI().sendItemBags(on);
            Service.gI().sendThongBao(on, "Bạn nhận được từ quản trị: x" + qty + " " + t.name);
            return Map.of("ok", true, "online", true);
        }
        List<Map<String, Object>> rs = rows("select items_bag from player where id=?", id);
        if (rs.isEmpty()) {
            throw new IllegalArgumentException("Không có nhân vật");
        }
        JsonArray bag = arr(rs.get(0).get("items_bag"));
        JsonArray opsJson = new JsonArray();
        for (int[] o : ops) {
            opsJson.add("[" + o[0] + "," + o[1] + "]");
        }
        JsonArray it = new JsonArray();
        it.add(itemId);
        it.add(qty);
        it.add(opsJson.toString());
        it.add(System.currentTimeMillis());
        for (int i = 0; i < bag.size(); i++) {
            JsonArray slot = parse(bag.get(i).getAsString()).getAsJsonArray();
            if (slot.get(0).getAsInt() == -1) {
                bag.set(i, new com.google.gson.JsonPrimitive(it.toString()));
                update("update player set items_bag=? where id=?", bag.toString(), id);
                return Map.of("ok", true, "online", false);
            }
        }
        throw new IllegalStateException("Hành trang người chơi đã đầy");
    }

    // ------------------------------------------------------------------ sửa nhân vật (online sửa thẳng, offline sửa database)
    /** Chỉ số gốc: {khóa, vị trí trong data_point} */
    private static final Object[][] STAT_FIELDS = {{"limitPower", 0}, {"power", 1}, {"tiemNang", 2}, {"stamina", 3}, {"maxStamina", 4},
        {"hpg", 5}, {"mpg", 6}, {"dameg", 7}, {"defg", 8}, {"critg", 9}};

    private Object playerStats(Map<String, Object> b) throws Exception {
        long id = lng(b.get("id"));
        Player on = Client.gI().getPlayerByID((int) id);
        if (on != null) {
            nro.models.player.NPoint n = on.nPoint;
            for (Object[] f : STAT_FIELDS) {
                Object v = b.get((String) f[0]);
                if (v == null || String.valueOf(v).isBlank()) {
                    continue;
                }
                long x = lng(v);
                switch ((String) f[0]) {
                    case "limitPower" -> n.limitPower = (byte) Math.max(0, Math.min(127, x));
                    case "power" -> n.power = Math.max(0, x);
                    case "tiemNang" -> n.tiemNang = Math.max(0, x);
                    case "stamina" -> n.stamina = (short) Math.max(0, Math.min(Short.MAX_VALUE, x));
                    case "maxStamina" -> n.maxStamina = (short) Math.max(0, Math.min(Short.MAX_VALUE, x));
                    case "hpg" -> n.hpg = Math.max(1, x);
                    case "mpg" -> n.mpg = Math.max(1, x);
                    case "dameg" -> n.dameg = Math.max(1, x);
                    case "defg" -> n.defg = Math.max(0, x);
                    case "critg" -> n.critg = (byte) Math.max(0, Math.min(127, x));
                }
            }
            n.calPoint();
            Service.gI().point(on);
            PlayerDAO.updatePlayer(on);
            return Map.of("ok", true, "online", true);
        }
        List<Map<String, Object>> rs = rows("select data_point from player where id=?", id);
        if (rs.isEmpty()) {
            throw new IllegalArgumentException("Không có nhân vật");
        }
        JsonArray dp = arr(rs.get(0).get("data_point"));
        for (Object[] f : STAT_FIELDS) {
            Object v = b.get((String) f[0]);
            if (v != null && !String.valueOf(v).isBlank()) {
                dp.set((int) f[1], new com.google.gson.JsonPrimitive(Math.max(0, lng(v))));
            }
        }
        update("update player set data_point=? where id=?", dp.toString(), id);
        return Map.of("ok", true, "online", false);
    }

    private Object playerTask(Map<String, Object> b) throws Exception {
        long id = lng(b.get("id"));
        int taskId = intv(b.get("taskId")), index = intv(b.getOrDefault("index", 0)), count = intv(b.getOrDefault("count", 0));
        nro.models.task.TaskMain tpl = null;
        for (nro.models.task.TaskMain tm : Manager.TASKS) {
            if (tm.id == taskId) {
                tpl = tm;
            }
        }
        if (tpl == null) {
            throw new IllegalArgumentException("Không có nhiệm vụ " + taskId);
        }
        index = Math.max(0, Math.min(tpl.subTasks.size() - 1, index));
        Player on = Client.gI().getPlayerByID((int) id);
        if (on != null) {
            nro.models.task.TaskMain t = nro.models.services.TaskService.gI().getTaskMainById(on, taskId);
            t.index = index;
            t.subTasks.get(index).count = (short) count;
            t.lastTime = System.currentTimeMillis();
            on.playerTask.taskMain = t;
            nro.models.services.TaskService.gI().sendTaskMain(on);
            PlayerDAO.updatePlayer(on);
            return Map.of("ok", true, "online", true);
        }
        update("update player set data_task=? where id=?", "[" + taskId + "," + index + "," + count + "," + System.currentTimeMillis() + "]", id);
        return Map.of("ok", true, "online", false);
    }

    private Object botAccounts() throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        Map<Long, Integer> acc = nro.models.Bot.BotWorld.gI().accountBots(false);
        Map<Long, Integer> on = nro.models.Bot.BotWorld.gI().accountBots(true);
        Map<Long,Map<String,Object>> identities=new java.util.HashMap<>();
        List<Long> botIds=new ArrayList<>(acc.keySet());
        for(int start=0;start<botIds.size();start+=500){
            List<Long> batch=botIds.subList(start,Math.min(start+500,botIds.size()));
            String marks=String.join(",",java.util.Collections.nCopies(batch.size(),"?"));
            for(Map<String,Object> row:rows("select p.id,p.name,p.gender,p.head,p.items_body,a.username from player p left join account a on a.id=p.account_id where p.id in ("+marks+")",batch.toArray()))
                identities.put(((Number)row.get("id")).longValue(),row);
        }
        for (Map.Entry<Long, Integer> e : acc.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            long pid = e.getKey();
            List<Map<String,Object>> r=identities.containsKey(pid)?List.of(identities.get(pid)):List.of();
            m.put("id", pid);
            m.put("name", r.isEmpty() ? "?" : r.get(0).get("name"));
            m.put("username", r.isEmpty() ? "" : r.get(0).get("username"));
            nro.models.Bot.Bot bot = nro.models.Bot.BotWorld.gI().find(pid);
            m.put("enabled", on.containsKey(pid));
            m.put("state", !on.containsKey(pid) ? "Đã tắt" : bot != null ? "Đang chơi" : "Chờ vào game");
            m.put("clan", bot != null && bot.clan != null ? bot.clan.name : "");
            m.put("gender", bot != null ? bot.gender : r.isEmpty() ? -1 : r.get(0).get("gender"));
            m.put("avatar", bot != null ? avatarOfHead(bot.getHead()) : r.isEmpty() ? -1 : avatarOf(r.get(0).get("head"), r.get(0).get("items_body")));
            m.put("task", bot != null && bot.playerTask != null && bot.playerTask.taskMain != null ? bot.playerTask.taskMain.id + "." + bot.playerTask.taskMain.index : "");
            m.put("gold", bot != null ? bot.inventory.gold : null);
            m.put("activity", bot != null ? bot.activity : "");
            m.put("power", bot != null ? bot.nPoint.power : null);
            m.put("map", bot != null && bot.zone != null ? bot.zone.map.mapName + " khu " + bot.zone.zoneId : "");
            m.put("hp", bot != null ? bot.nPoint.hp : null);
            m.put("hpMax", bot != null ? bot.nPoint.hpMax : null);
            m.put("mapFit", bot == null || bot.zone == null || bot.strongEnough(bot.zone.map.mapId)); // quái ở map hiện tại có vừa sức bot không
            m.put("look", bot != null && bot.inventory.itemsBody.size() > 5 && bot.inventory.itemsBody.get(5).isNotNullItem() ? bot.inventory.itemsBody.get(5).template.name : "");
            out.add(m);
        }
        return out;
    }

    private Object taskList() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (nro.models.task.TaskMain tm : Manager.TASKS) {
            List<String> subs = new ArrayList<>();
            for (nro.models.task.SubTaskMain st : tm.subTasks) {
                subs.add(st.name + " (" + st.maxCount + ")");
            }
            out.add(Map.of("id", tm.id, "name", tm.name == null ? "" : tm.name, "subs", subs));
        }
        return out;
    }

    private Object playerRename(Map<String, Object> b) throws Exception {
        long id = lng(b.get("id"));
        String name = str(b.get("name")).trim();
        if (name.length() < 3 || name.length() > 20) {
            throw new IllegalArgumentException("Tên dài 3 - 20 ký tự");
        }
        if (!rows("select id from player where name=? and id<>?", name, id).isEmpty()) {
            throw new IllegalArgumentException("Tên đã có người dùng");
        }
        update("update player set name=? where id=?", name, id);
        Player on = Client.gI().getPlayerByID((int) id);
        if (on != null) {
            on.name = name;
            Service.gI().sendThongBao(on, "Quản trị đã đổi tên nhân vật thành " + name + ", thoát ra vào lại để thấy tên mới");
        }
        return Map.of("ok", true);
    }

    /** Xoá 1 vật phẩm: where = body / bag / box, index = vị trí ô. */
    private Object playerRemoveItem(Map<String, Object> b) throws Exception {
        long id = lng(b.get("id"));
        String where = str(b.get("where"));
        int index = intv(b.get("index"));
        String col = switch (where) {
            case "body" -> "items_body";
            case "box" -> "items_box";
            default -> "items_bag";
        };
        Player on = Client.gI().getPlayerByID((int) id);
        if (on != null) {
            List<Item> list = where.equals("body") ? on.inventory.itemsBody : where.equals("box") ? on.inventory.itemsBox : on.inventory.itemsBag;
            if (index < 0 || index >= list.size()) {
                throw new IllegalArgumentException("Sai ô");
            }
            list.set(index, ItemService.gI().createItemNull());
            InventoryService.gI().sendItemBags(on);
            InventoryService.gI().sendItemBody(on);
            nro.models.services.InventoryService.gI().sendItemBox(on);
            Service.gI().point(on);
            PlayerDAO.updatePlayer(on);
            return Map.of("ok", true, "online", true);
        }
        List<Map<String, Object>> rs = rows("select " + col + " from player where id=?", id);
        if (rs.isEmpty()) {
            throw new IllegalArgumentException("Không có nhân vật");
        }
        JsonArray a = arr(rs.get(0).get(col));
        if (a == null || index < 0 || index >= a.size()) {
            throw new IllegalArgumentException("Sai ô");
        }
        a.set(index, new com.google.gson.JsonPrimitive("[-1,0,\"[]\",0]"));
        update("update player set " + col + "=? where id=?", a.toString(), id);
        return Map.of("ok", true, "online", false);
    }

    /** Xoá hẳn nhân vật (đang online thì kick, đợi lưu xong rồi xoá). Tài khoản giữ lại, đăng nhập sẽ tạo nhân vật mới. */
    private Object playerDelete(Map<String, Object> b) throws Exception {
        long id = lng(b.get("id"));
        if (!"XOA".equals(str(b.get("confirm")))) {
            throw new IllegalArgumentException("Thiếu xác nhận");
        }
        Player on = Client.gI().getPlayerByID((int) id);
        if (on != null && on.session != null) {
            Client.gI().kickSession(on.session);
            for (int i = 0; i < 50 && Client.gI().getPlayerByID((int) id) != null; i++) {
                Thread.sleep(200);
            }
            Thread.sleep(1500); // đợi lưu dữ liệu khi thoát xong
        }
        int n = update("delete from player where id=?", id);
        try {
            update("delete from player_extra where player_id=?", id);
        } catch (Exception ignored) {
        }
        if (n == 0) {
            throw new IllegalArgumentException("Không có nhân vật");
        }
        return Map.of("ok", true);
    }

    private Object playerKick(Map<String, Object> b) {
        Player on = Client.gI().getPlayerByID(intv(b.get("id")));
        if (on == null || on.session == null) {
            throw new IllegalStateException("Người chơi không online");
        }
        Client.gI().kickSession(on.session);
        return Map.of("ok", true);
    }

    private Object playerSave(Map<String, Object> b) {
        int n = 0;
        for (Player p : onlinePlayers()) {
            if (b.get("id") == null || p.id == lng(b.get("id"))) {
                PlayerDAO.updatePlayer(p);
                n++;
            }
        }
        return Map.of("ok", true, "saved", n);
    }

    // ------------------------------------------------------------------ tài khoản

    private Object accounts(String kw) throws Exception {
        return rows("select a.id, a.username, a.ban, a.is_admin, a.vnd, a.tongnap, a.create_time, a.last_time_login, a.ip_address, "
                + "(select group_concat(p.name) from player p where p.account_id=a.id) players from account a "
                + "where a.username like ? or a.id = ? or exists (select 1 from player p where p.account_id=a.id and p.name like ?) order by a.id desc limit 200",
                "%" + kw + "%", kw.matches("\\d+") ? Long.parseLong(kw) : -1, "%" + kw + "%");
    }

    private Object accountUpdate(Map<String, Object> b) throws Exception {
        long id = lng(b.get("id"));
        if (b.containsKey("ban")) {
            update("update account set ban=? where id=?", intv(b.get("ban")), id);
            if (intv(b.get("ban")) == 1) {
                for (Player p : onlinePlayers()) {
                    if (p.session != null && p.session.userId == id) {
                        Client.gI().kickSession(p.session);
                    }
                }
            }
        }
        if (b.containsKey("is_admin")) {
            update("update account set is_admin=? where id=?", intv(b.get("is_admin")), id);
        }
        if (b.containsKey("vnd")) {
            update("update account set vnd=? where id=?", intv(b.get("vnd")), id);
        }
        if (b.containsKey("tongnap")) {
            update("update account set tongnap=? where id=?", intv(b.get("tongnap")), id);
        }
        String pw = str(b.get("password"));
        if (!pw.isEmpty()) {
            update("update account set password=? where id=?", pw, id);
        }
        return Map.of("ok", true);
    }

    /**
     * Xoá hẳn 1 tài khoản và các nhân vật của nó (gõ đúng tên tài khoản để xác nhận). Không xoá tài khoản bot (xoá ở trang Bot)
     * và nhân vật đang là bang chủ (chuyển bang chủ / giải tán bang trước). Đang online thì bị kick, lưu xong mới xoá.
     */
    private static nro.models.clan.Clan clanOrNull(int id) {
        for (nro.models.clan.Clan c : Manager.CLANS) {
            if (c.id == id) {
                return c;
            }
        }
        return null;
    }

    private Object accountDelete(Map<String, Object> b) throws Exception {
        long id = lng(b.get("id"));
        List<Map<String, Object>> acc = rows("select username from account where id = ?", id);
        if (acc.isEmpty()) {
            throw new IllegalArgumentException("Không có tài khoản này");
        }
        String user = String.valueOf(acc.get(0).get("username"));
        if (!user.equals(str(b.get("confirm")).trim())) {
            throw new IllegalArgumentException("Gõ đúng tên tài khoản \"" + user + "\" để xác nhận xoá");
        }
        if (!rows("select 1 from bot_account where account_id = ? limit 1", id).isEmpty()) {
            throw new IllegalArgumentException("Đây là tài khoản bot, hãy xoá ở trang Bot");
        }
        List<Map<String, Object>> pls = rows("select id, name, clan_id from player where account_id = ?", id);
        for (Map<String, Object> r : pls) {
            int clanId = intv(r.get("clan_id"));
            nro.models.clan.Clan c = clanOrNull(clanId);
            nro.models.clan.ClanMember cm = c == null ? null : c.getClanMember(intv(r.get("id")));
            if (cm != null && cm.role == nro.models.clan.Clan.LEADER) {
                throw new IllegalArgumentException(r.get("name") + " đang là bang chủ " + c.name + ": chuyển bang chủ hoặc giải tán bang trước");
            }
        }
        boolean kicked = false;
        for (Player p : onlinePlayers()) {
            if (p.session != null && p.session.userId == id) {
                Client.gI().kickSession(p.session);
                kicked = true;
            }
        }
        if (kicked) {
            Thread.sleep(2000); // chờ lưu khi thoát xong, kẻo bản lưu ghi lại sau khi xoá
        }
        for (Map<String, Object> r : pls) {
            int clanId = intv(r.get("clan_id"));
            nro.models.clan.Clan c = clanOrNull(clanId);
            if (c != null && c.getClanMember(intv(r.get("id"))) != null) {
                PanelClan.kick(clanId, intv(r.get("id")));
            }
        }
        java.util.Set<Integer> pids = new java.util.HashSet<>();
        for (Map<String, Object> r : pls) {
            pids.add(intv(r.get("id")));
        }
        // đồ ký gửi nằm trong bộ nhớ, lần lưu sau ghi lại cả bảng: bỏ khỏi bộ nhớ chứ xoá DB không đủ
        nro.models.shop_ky_gui.ConsignShopManager.gI().listItem.removeIf(it -> pids.contains(it.player_sell));
        try (java.sql.Connection con = nro.models.data.LocalManager.getConnection()) {
            con.setAutoCommit(false);
            try {
                for (Map<String, Object> r : pls) {
                    long pid = lng(r.get("id"));
                    for (String t : new String[]{"player_extra", "super_rank", "shop_ky_gui", "box_pity", "season_event_point",
                        "season_event_cook", "season_event_reward_item", "season_event_reward_pending"}) {
                        try (java.sql.PreparedStatement ps = con.prepareStatement("delete from " + t + " where player_id = ?")) {
                            ps.setLong(1, pid);
                            ps.executeUpdate();
                        } catch (java.sql.SQLException missingTable) {
                            if (!"42S02".equals(missingTable.getSQLState())) {
                                throw missingTable;
                            }
                        }
                    }
                }
                try (java.sql.PreparedStatement ps = con.prepareStatement("delete from player where account_id = ?")) {
                    ps.setLong(1, id);
                    ps.executeUpdate();
                }
                try (java.sql.PreparedStatement ps = con.prepareStatement("delete from account where id = ?")) {
                    ps.setLong(1, id);
                    ps.executeUpdate();
                }
                con.commit();
            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
        return Map.of("ok", true, "note", "Đã xoá tài khoản " + user + " và " + pls.size() + " nhân vật");
    }

    // ------------------------------------------------------------------ item

    private Object items(String kw,String type){return itemsList(kw,type,300);}

    private Object itemsPage(Map<String,String> params){
        List<Map<String,Object>> list=itemsList(params.getOrDefault("q","").trim(),params.getOrDefault("type",""),Integer.MAX_VALUE);
        String gender=params.getOrDefault("gender","");
        if(!gender.isEmpty())list.removeIf(x->!String.valueOf(x.get("gender")).equals(gender));
        return PanelDirectory.slice(list,params);
    }

    private List<Map<String,Object>> itemsList(String kw, String type, int maxResults) {
        List<Map<String, Object>> out = new ArrayList<>();
        String k = kw.toLowerCase();
        for (ItemTemplate t : Manager.ITEM_TEMPLATES) {
            if (t == null || t.name == null) {
                continue;
            }
            if (!type.isEmpty() && !String.valueOf(t.type).equals(type)) {
                continue;
            }
            if (!k.isEmpty() && !t.name.toLowerCase().contains(k) && !String.valueOf(t.id).equals(k)) {
                continue;
            }
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", t.id);
            o.put("name", t.name);
            o.put("type", t.type);
            o.put("gender", t.gender);
            o.put("level", t.level);
            o.put("icon", t.iconID);
            o.put("desc", t.description);
            out.add(o);
            if (out.size() >= maxResults) {
                break;
            }
        }
        return out;
    }

    private Object item(int id) {
        ItemTemplate t = ItemService.gI().getTemplate(id);
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", t.id);
        o.put("name", t.name);
        o.put("type", t.type);
        o.put("gender", t.gender);
        o.put("level", t.level);
        o.put("icon", t.iconID);
        o.put("part", t.part);
        o.put("strRequire", t.strRequire);
        o.put("desc", t.description);
        List<Map<String, Object>> ops = new ArrayList<>();
        for (Item.ItemOption io : ItemService.gI().getListOptionItemShop((short) id)) {
            ops.add(Map.of("id", io.optionTemplate.id, "param", io.param, "text", io.getOptionString()));
        }
        o.put("options", ops);
        return o;
    }

    // ------------------------------------------------------------------ chỉ số mặc định / cửa hàng sự kiện / kiểm tra dữ liệu

    @SuppressWarnings("unchecked")
    private Object itemOptionsSave(Map<String, Object> b) throws Exception {
        int id = intv(b.get("id"));
        if (ItemService.gI().getTemplate(id) == null) {
            throw new IllegalArgumentException("Không có vật phẩm " + id);
        }
        List<Object> list = (List<Object>) b.getOrDefault("options", List.of());
        for (Object x : list) {
            int oid = intv(((Map<String, Object>) x).get("option_id"));
            if (ItemService.gI().getItemOptionTemplate(oid) == null) {
                throw new IllegalArgumentException("Không có chỉ số (option) " + oid);
            }
        }
        update("delete from item_template_option where template_id=?", id);
        for (Object x : list) {
            Map<String, Object> o = (Map<String, Object>) x;
            int param = intv(o.getOrDefault("param", 0));
            update("insert into item_template_option (template_id, option_id, param, param_max, chance, group_id) values (?, ?, ?, ?, ?, ?)",
                    id, intv(o.get("option_id")), param, Math.max(param, intv(o.getOrDefault("param_max", param))),
                    Math.max(1, Math.min(100, intv(o.getOrDefault("chance", 100)))), Math.max(0, intv(o.getOrDefault("group_id", 0))));
        }
        Manager.gI().reloadTemplateOptions();
        return Map.of("ok", true, "note", "Đã lưu " + list.size() + " dòng chỉ số, áp dụng cho vật phẩm tạo mới");
    }

    private Object exchangeSave(Map<String, Object> b) throws Exception {
        int item = intv(b.get("reward_item"));
        if (item >= 0 && ItemService.gI().getTemplate(item) == null) {
            throw new IllegalArgumentException("Không có vật phẩm " + item);
        }
        String days = str(b.get("reward_days")).replaceAll("[^0-9,]", "");
        Object[] v = {intv(b.get("event_id")), str(b.get("grp")).replace("\\n", "\n"), str(b.get("name")).replace("\\n", "\n"), str(b.get("need")),
            lng(b.getOrDefault("gold", 0)), intv(b.getOrDefault("gem", 0)), intv(b.getOrDefault("ruby", 0)), intv(b.getOrDefault("cost_point", 0)),
            item, Math.max(1, intv(b.getOrDefault("reward_qty", 1))), days, intv(b.getOrDefault("perm_rate", 0)), str(b.get("npc"))};
        if (b.get("id") != null && lng(b.get("id")) > 0) {
            update("update season_event_exchange set event_id=?, grp=?, name=?, need=?, gold=?, gem=?, ruby=?, cost_point=?, reward_item=?, reward_qty=?, reward_days=?, perm_rate=?, npc=? where id=?",
                    v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10], v[11], v[12], lng(b.get("id")));
        } else {
            update("insert into season_event_exchange (event_id, grp, name, need, gold, gem, ruby, cost_point, reward_item, reward_qty, reward_days, perm_rate, npc) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", v);
        }
        SeasonEventService.gI().admin(" reload");
        return Map.of("ok", true, "note", "Đã lưu, sự kiện đã nạp lại");
    }

    private Object exchangeDelete(Map<String, Object> b) throws Exception {
        update("delete from season_event_exchange where id=?", lng(b.get("id")));
        SeasonEventService.gI().admin(" reload");
        return Map.of("ok", true);
    }

    private static final java.util.Set<Integer> WEAR_TYPES = java.util.Set.of(5, 11, 23, 24, 72);

    /** Kiểm tra dữ liệu: tìm các lỗi hay gặp (vật phẩm không có chỉ số, id không tồn tại, option hỏng, part thiếu...). */
    private Object health() throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        java.util.function.BiConsumer<String, String[]> add = (lv, a) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("level", lv);
            m.put("kind", a[0]);
            m.put("msg", a[1]);
            m.put("item", a.length > 2 ? a[2] : "");
            out.add(m);
        };
        // 1. vật phẩm không tồn tại trong hộp quà / cửa hàng sự kiện / quà top / shop
        for (Map<String, Object> r : rows("select box_id, item_id from item_box_reward where item_id >= 0 and item_id not in (select id from item_template)")) {
            add.accept("lỗi", new String[]{"Hộp quà", "Hộp #" + r.get("box_id") + " có vật phẩm #" + r.get("item_id") + " không tồn tại"});
        }
        for (Map<String, Object> r : rows("select id, event_id, reward_item from season_event_exchange where reward_item >= 0 and reward_item not in (select id from item_template) and reward_item not in (select box_id from item_box_reward)")) {
            add.accept("lỗi", new String[]{"Cửa hàng sự kiện", "Dòng #" + r.get("id") + " (sự kiện " + r.get("event_id") + ") thưởng vật phẩm #" + r.get("reward_item") + " không tồn tại"});
        }
        for (Map<String, Object> r : rows("select top_id, item_id from season_event_top_reward where item_id >= 0 and item_id not in (select id from item_template)")) {
            add.accept("lỗi", new String[]{"Quà đua top", "Top #" + r.get("top_id") + " có vật phẩm #" + r.get("item_id") + " không tồn tại"});
        }
        for (Map<String, Object> r : rows("select s.id, s.temp_id from item_shop s where s.temp_id not in (select id from item_template)")) {
            add.accept("lỗi", new String[]{"Shop", "Dòng shop #" + r.get("id") + " bán vật phẩm #" + r.get("temp_id") + " không tồn tại"});
        }
        // 2. chỉ số trỏ tới option không tồn tại (gây lỗi khi mở shop / xem đồ)
        for (Map<String, Object> r : rows("select template_id, option_id from item_template_option where option_id not in (select id from item_option_template)")) {
            add.accept("lỗi", new String[]{"Chỉ số mặc định", "Vật phẩm #" + r.get("template_id") + " có chỉ số #" + r.get("option_id") + " không tồn tại", String.valueOf(r.get("template_id"))});
        }
        for (Map<String, Object> r : rows("select s.temp_id, o.option_id from item_shop_option o join item_shop s on s.id=o.item_shop_id where o.option_id not in (select id from item_option_template)")) {
            add.accept("lỗi", new String[]{"Shop", "Vật phẩm #" + r.get("temp_id") + " trong shop có chỉ số #" + r.get("option_id") + " không tồn tại", String.valueOf(r.get("temp_id"))});
        }
        // 3. đồ mặc / đeo được bán hoặc tặng trong sự kiện nhưng không có dòng chỉ số nào
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        List<Map<String, Object>> used = rows("select reward_item id, concat('Cửa hàng sự kiện ', event_id) src from season_event_exchange where reward_item >= 0"
                + " union select item_id, concat('Hộp quà #', box_id) from item_box_reward where item_id >= 0"
                + " union select temp_id, concat('Shop tab #', tab_id) from item_shop"
                + " union select item_id, concat('Quà top #', top_id) from season_event_top_reward where item_id >= 0");
        for (Map<String, Object> r : used) {
            int id = ((Number) r.get("id")).intValue();
            ItemTemplate t = id >= 0 && id < Manager.ITEM_TEMPLATES.size() ? ItemService.gI().getTemplate(id) : null;
            if (t == null || !seen.add(id)) {
                continue;
            }
            boolean pet = t.type == 27 && t.head >= 0;
            if ((WEAR_TYPES.contains((int) t.type) || pet) && ItemService.gI().getListOptionItemShop((short) id).isEmpty()) {
                add.accept("cảnh báo", new String[]{"Thiếu chỉ số", t.name + " (#" + id + ", loại " + t.type + ") có trong " + r.get("src") + " nhưng không có dòng chỉ số nào", String.valueOf(id)});
            }
        }
        // 4. part / cờ đeo lưng không tồn tại (mặc vào không hiện)
        for (Map<String, Object> r : rows("select id, NAME, head, body, leg from item_template where (head >= 0 and head not in (select id from part)) or (body >= 0 and body not in (select id from part)) or (leg >= 0 and leg not in (select id from part))")) {
            add.accept("lỗi", new String[]{"Part", r.get("NAME") + " (#" + r.get("id") + ") trỏ tới part không tồn tại (" + r.get("head") + "/" + r.get("body") + "/" + r.get("leg") + ")", String.valueOf(r.get("id"))});
        }
        for (Map<String, Object> r : rows("select id, NAME, part from item_template where TYPE = 11 and part >= 0 and part not between 201 and 254 and part not in (select id from flag_bag)")) {
            add.accept("cảnh báo", new String[]{"Đeo lưng", r.get("NAME") + " (#" + r.get("id") + ") dùng cờ " + r.get("part") + " chưa có trong flag_bag: đang tạm hiện icon vật phẩm, thêm khung hình vào flag_bag để có hoạt ảnh", String.valueOf(r.get("id"))});
        }
        // 5. icon thiếu ảnh
        for (Map<String, Object> r : rows("select id, NAME, icon_id from item_template where id in (select reward_item from season_event_exchange union select item_id from item_box_reward)")) {
            int icon = ((Number) r.get("icon_id")).intValue();
            if (icon >= 0 && !new java.io.File("data/icon/x2/" + icon + ".png").exists() && !new java.io.File("data/icon/x4/" + icon + ".png").exists()) {
                add.accept("cảnh báo", new String[]{"Icon", r.get("NAME") + " (#" + r.get("id") + ") thiếu ảnh icon " + icon, String.valueOf(r.get("id"))});
            }
        }
        // 6. số option id >= 255 (client PC/APK chỉ nhận được qua 10 ô trống)
        long big = Manager.ITEM_OPTION_TEMPLATES.stream().filter(t -> t.id >= 255).count();
        int cap = nro.models.item.OptionIdMap.spareCount();
        add.accept(big > cap ? "lỗi" : big >= cap - 1 ? "cảnh báo" : "ok", new String[]{"Bảng chỉ số", big + " / " + cap + " chỉ số có mã từ 255 trở lên (client APK/PC đọc mã 1 byte). Hết ô thì chỉ số mới sẽ hiện sai"});
        if (out.stream().noneMatch(m -> !"ok".equals(m.get("level")))) {
            add.accept("ok", new String[]{"Tổng quát", "Không tìm thấy lỗi dữ liệu"});
        }
        return out;
    }

    // ------------------------------------------------------------------ thưởng boss theo bậc

    private Object bossRewards() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tiers", nro.models.services.BossRewardService.TIER_NAME);
        m.put("rows", rows("select b.*, t.NAME item_name, t.icon_id from boss_reward b left join item_template t on t.id = b.item_id order by b.tier, b.boss, b.id"));
        List<Map<String, Object>> bosses = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Boss bo : allBosses()) {
            if (bo.name != null && seen.add(bo.name)) {
                bosses.add(Map.of("name", bo.name, "hp", bo.nPoint == null ? 0 : bo.nPoint.hpMax, "tier", nro.models.services.BossRewardService.tierOf(bo)));
            }
        }
        bosses.sort((a, c) -> Long.compare(((Number) a.get("hp")).longValue(), ((Number) c.get("hp")).longValue()));
        m.put("bosses", bosses);
        return m;
    }

    private Object bossRewardSave(Map<String, Object> b) throws Exception {
        int item = intv(b.get("item_id"));
        if (item >= 0 && ItemService.gI().getTemplate(item) == null) {
            throw new IllegalArgumentException("Không có vật phẩm " + item);
        }
        Object[] v = {Math.max(0, Math.min(5, intv(b.getOrDefault("tier", 0)))), str(b.get("boss")).trim(), item, Math.max(1, intv(b.getOrDefault("qty_min", 1))),
            Math.max(1, intv(b.getOrDefault("qty_max", 1))), Math.max(0, Math.min(10000, intv(b.getOrDefault("rate", 10000)))),
            str(b.get("hsd_days")).replaceAll("[^0-9,]", ""), intv(b.getOrDefault("perm_rate", 0)), str(b.get("note"))};
        if (b.get("id") != null && lng(b.get("id")) > 0) {
            update("update boss_reward set tier=?, boss=?, item_id=?, qty_min=?, qty_max=?, rate=?, hsd_days=?, perm_rate=?, note=? where id=?",
                    v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], lng(b.get("id")));
        } else {
            update("insert into boss_reward (tier, boss, item_id, qty_min, qty_max, rate, hsd_days, perm_rate, note) values (?, ?, ?, ?, ?, ?, ?, ?, ?)", v);
        }
        nro.models.services.BossRewardService.gI().reload();
        return Map.of("ok", true, "note", "Đã lưu, thưởng boss đã nạp lại");
    }

    private Object bossRewardDelete(Map<String, Object> b) throws Exception {
        update("delete from boss_reward where id=?", lng(b.get("id")));
        nro.models.services.BossRewardService.gI().reload();
        return Map.of("ok", true);
    }

    private Object options(String kw) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ItemOptionTemplate t : Manager.ITEM_OPTION_TEMPLATES) {
            if (t != null && (kw.isEmpty() || t.name.toLowerCase().contains(kw.toLowerCase()) || String.valueOf(t.id).equals(kw))) {
                out.add(Map.of("id", t.id, "name", t.name));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ giftcode

    private Object giftcodeSave(Map<String, Object> b) throws Exception {
        String code = str(b.get("code")).trim();
        if (code.isEmpty()) {
            throw new IllegalArgumentException("Thiếu mã code");
        }
        String detail = str(b.get("detail"));
        parse(detail).getAsJsonArray(); // kiểm tra JSON hợp lệ
        int count = intv(b.getOrDefault("count", 1));
        String expired = str(b.get("expired")).replace('T', ' ');
        if (expired.length() == 16) {
            expired += ":00";
        }
        if (b.get("id") != null && lng(b.get("id")) > 0) {
            update("update giftcode set code=?, count_left=?, detail=?, expired=? where id=?", code, count, detail, expired, lng(b.get("id")));
        } else {
            update("insert into giftcode (code, count_left, detail, datecreate, expired) values (?, ?, ?, now(), ?)", code, count, detail, expired);
        }
        reloadGiftcodes();
        return Map.of("ok", true);
    }

    private Object giftcodeDelete(Map<String, Object> b) throws Exception {
        update("delete from giftcode where id=?", lng(b.get("id")));
        reloadGiftcodes();
        return Map.of("ok", true);
    }

    private void reloadGiftcodes() throws Exception {
        List<nro.models.player_system.GiftCode> list = new ArrayList<>();
        for (Map<String, Object> r : rows("select * from giftcode")) {
            nro.models.player_system.GiftCode g = new nro.models.player_system.GiftCode();
            g.id = intv(r.get("id"));
            g.code = str(r.get("code"));
            g.countLeft = intv(r.get("count_left"));
            if (g.countLeft == -1) {
                g.countLeft = 999999999;
            }
            g.datecreate = (java.sql.Timestamp) r.get("datecreate");
            g.dateexpired = (java.sql.Timestamp) r.get("expired");
            try {
                for (JsonElement e : parse(str(r.get("detail"))).getAsJsonArray()) {
                    var o = e.getAsJsonObject();
                    int id = o.get("id").getAsInt();
                    ArrayList<Item.ItemOption> ops = new ArrayList<>();
                    if (o.has("options")) {
                        for (JsonElement op : o.getAsJsonArray("options")) {
                            ops.add(new Item.ItemOption(op.getAsJsonObject().get("id").getAsInt(), op.getAsJsonObject().get("param").getAsInt()));
                        }
                    }
                    g.option.put(id, ops);
                    g.detail.put(id, o.get("quantity").getAsInt());
                }
            } catch (Exception ignored) {
            }
            list.add(g);
        }
        synchronized (nro.models.managers.GiftCodeManager.gI()) {
            nro.models.managers.GiftCodeManager.gI().listGiftCode.clear();
            nro.models.managers.GiftCodeManager.gI().listGiftCode.addAll(list);
        }
    }

    // ------------------------------------------------------------------ hộp quà

    private Object boxRows(int boxId) throws Exception {
        return rows("select b.id, b.box_id, b.item_id, t.name, t.icon_id, b.qty_min, b.qty_max, b.weight, b.hsd_days, b.perm_rate, b.pity from item_box_reward b left join item_template t on t.id=b.item_id where b.box_id=? order by b.id", boxId);
    }

    private Object boxSave(Map<String, Object> b) throws Exception {
        Object[] v = {intv(b.get("box_id")), intv(b.get("item_id")), Math.max(1, intv(b.getOrDefault("qty_min", 1))), Math.max(1, intv(b.getOrDefault("qty_max", 1))),
            intv(b.getOrDefault("weight", 1)), str(b.get("hsd_days")), intv(b.getOrDefault("perm_rate", 0)), Math.max(0, intv(b.getOrDefault("pity", 0)))};
        if (b.get("id") != null && lng(b.get("id")) > 0) {
            update("update item_box_reward set box_id=?, item_id=?, qty_min=?, qty_max=?, weight=?, hsd_days=?, perm_rate=?, pity=? where id=?",
                    v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], lng(b.get("id")));
        } else {
            update("insert into item_box_reward (box_id, item_id, qty_min, qty_max, weight, hsd_days, perm_rate, pity) values (?, ?, ?, ?, ?, ?, ?, ?)", v);
        }
        Event2026Service.gI().reloadBoxes();
        return Map.of("ok", true);
    }

    private Object boxDelete(Map<String, Object> b) throws Exception {
        update("delete from item_box_reward where id=?", lng(b.get("id")));
        Event2026Service.gI().reloadBoxes();
        return Map.of("ok", true);
    }

    // ------------------------------------------------------------------ web người chơi

    private Object webPosts(Map<String, String> q) throws Exception {
        StringBuilder w = new StringBuilder("from web_post where (title like ? escape '!' or author like ? escape '!' or ifnull(target,'') like ? escape '!')");
        List<Object> args = new ArrayList<>(List.of(like(q), like(q), like(q)));
        String kind = str(q.getOrDefault("kind", ""));
        if (nro.models.web.WebServer.KINDS.contains(kind)) {
            w.append(" and kind = ?");
            args.add(kind);
        }
        String st = str(q.getOrDefault("status", ""));
        if (st.matches("[0-3]")) {
            w.append(" and status = ?");
            args.add(Integer.parseInt(st));
        }
        return paged(q, "select id, kind, title, author, target, status, pinned, replies, date_format(created_at, '%d/%m/%Y %H:%i') created_at, "
                + "date_format(updated_at, '%d/%m/%Y %H:%i') updated_at", w.toString(), "status = 0 desc, updated_at desc", args.toArray());
    }

    private Object webPost(int id) throws Exception {
        List<Map<String, Object>> r = rows("select p.*, a.username from web_post p left join account a on a.id = p.account_id where p.id = ?", id);
        if (r.isEmpty()) {
            throw new IllegalArgumentException("Không có bài " + id);
        }
        Map<String, Object> m = new LinkedHashMap<>(r.get(0));
        m.put("created_at", String.valueOf(m.get("created_at")));
        m.put("updated_at", String.valueOf(m.get("updated_at")));
        m.put("replyList", rows("select author, is_admin, content, date_format(created_at, '%d/%m/%Y %H:%i') created_at from web_reply where post_id = ? order by id", id));
        return m;
    }

    private Object webReply(Map<String, Object> b) throws Exception {
        String c = str(b.get("content")).trim();
        long id = lng(b.get("id"));
        if (c.isEmpty() || c.length() > 2000) {
            throw new IllegalArgumentException("Nội dung từ 1 đến 2.000 ký tự");
        }
        update("insert into web_reply (post_id, account_id, author, is_admin, content) values (?, 0, 'Ban quản trị', 1, ?)", id, c);
        // phiếu mới được trả lời thì chuyển sang "đang xử lý"
        update("update web_post set replies = replies + 1, updated_at = now(), status = if(status = 0 and kind <> 'forum', 1, status) where id = ?", id);
        return Map.of("ok", true, "note", "Đã trả lời");
    }

    private Object webPostDelete(Map<String, Object> b) throws Exception {
        long id = lng(b.get("id"));
        update("delete from web_reply where post_id = ?", id);
        return Map.of("ok", update("delete from web_post where id = ?", id) > 0, "note", "Đã xoá bài");
    }

    private Object webTopups(Map<String, String> q) throws Exception {
        StringBuilder w = new StringBuilder("from web_topup where (username like ? escape '!' or ifnull(note,'') like ? escape '!' or ifnull(serial,'') like ? escape '!')");
        List<Object> args = new ArrayList<>(List.of(like(q), like(q), like(q)));
        String st = str(q.getOrDefault("status", ""));
        if (st.matches("[0-2]")) {
            w.append(" and status = ?");
            args.add(Integer.parseInt(st));
        }
        String m = str(q.getOrDefault("method", ""));
        if (m.equals("bank") || m.equals("card")) {
            w.append(" and method = ?");
            args.add(m);
        }
        return paged(q, "select id, username, method, amount, telco, serial, code, status, note, credited, date_format(created_at, '%d/%m/%Y %H:%i') created_at",
                w.toString(), "status = 0 desc, id desc", args.toArray());
    }

    /** Duyệt nạp: amount bỏ trống = đúng số tiền yêu cầu (chuyển khoản) / mệnh giá (thẻ). */
    private Object webTopupApprove(Map<String, Object> b) throws Exception {
        int id = (int) lng(b.get("id"));
        List<Map<String, Object>> r = rows("select amount from web_topup where id = ?", id);
        if (r.isEmpty()) {
            throw new IllegalArgumentException("Không có yêu cầu " + id);
        }
        int amount = b.get("amount") != null && intv(b.get("amount")) > 0 ? intv(b.get("amount")) : intv(r.get(0).get("amount"));
        if (!nro.models.web.WebServer.approve(id, amount, "Admin duyệt")) {
            throw new IllegalStateException("Yêu cầu đã được xử lý trước đó");
        }
        return Map.of("ok", true, "note", "Đã duyệt " + amount + "đ, cộng " + nro.models.services_func.Input.napGem(amount) + " ngọc");
    }

    // ------------------------------------------------------------------ ngọc rồng sao đen

    /** Kết quả các trận NRSD mới nhất trước; lọc theo sao, ngày (yyyy-mm-dd), tên người / bang. */
    private Object nrsdPage(Map<String, String> q) throws Exception {
        StringBuilder w = new StringBuilder("from nrsd_result r left join player p on p.id = r.player_id where (r.player_name like ? escape '!' or r.clan_name like ? escape '!')");
        List<Object> args = new ArrayList<>(List.of(like(q), like(q)));
        int star = intv(q.getOrDefault("star", "0"));
        if (star >= 1 && star <= 7) {
            w.append(" and r.star = ?");
            args.add(star);
        }
        String day = str(q.getOrDefault("date", ""));
        if (day.matches("\\d{4}-\\d{2}-\\d{2}")) {
            w.append(" and date(r.played_at) = ?");
            args.add(day);
        }
        if ("1".equals(q.get("tournament"))) {
            w.append(" and r.tournament is not null");
        }
        Map<String, Object> out = paged(q, "select r.id, date_format(r.played_at, '%Y-%m-%d %H:%i:%s') played_at, r.star, r.map_id, r.zone_id, r.player_id, r.player_name, r.clan_name, r.tournament, r.reward, p.gender, p.head, p.items_body",
                w.toString(), "r.id desc", args.toArray());
        for (Object o : (List<?>) out.get("rows")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> r = (Map<String, Object>) o;
            r.put("avatar", avatarOf(r.remove("head"), r.remove("items_body")));
        }
        return out;
    }

    private static final String[] PET_TYPE = {"Đệ tử", "Mabư", "Uub (đệ tử 2)", "Kid Beer (đệ tử 2)", "Kid Jiren (đệ tử 2)"};
    private static final String[] PLANET = {"Trái Đất", "Namếc", "Xayda"};

    private static String skillName(int id) {
        for (nro.models.skill.NClass c : new ArrayList<>(Manager.NCLASS)) {
            for (nro.models.player_system.Template.SkillTemplate t : c.skillTemplatess) {
                if (t.id == id) {
                    return t.name;
                }
            }
        }
        return "Chiêu " + id;
    }

    /** Đầu đệ tử cho ảnh đại diện (giống Pet.getHead khi không mặc cải trang). */
    private static int petHead(int type, int gender, long power) {
        int[] vip = {-1, 297, 946, 1422, 876};
        if (type >= 1 && type <= 4) {
            return vip[type];
        }
        int g = Math.max(0, Math.min(2, gender));
        return power < 1_500_000 ? new int[]{285, 288, 282}[g] : new int[]{304, 305, 303}[g];
    }

    /** Đệ tử của các tài khoản: người đang online lấy dữ liệu trực tiếp, còn lại đọc cột pet trong database. */
    private Object petsPage(Map<String, String> q) throws Exception {
        StringBuilder w = new StringBuilder("from player p where p.pet is not null and p.pet like '[%' and length(p.pet) > 10 and (p.name like ? escape '!' or p.id = ?)");
        List<Object> args = new ArrayList<>(List.of(like(q), idOf(q)));
        String type = str(q.getOrDefault("type", ""));
        if (type.matches("[0-4]")) {
            w.append(" and p.pet like ?");
            args.add("[\"[" + type + ",%");
        } else if ("vip".equals(type)) {
            w.append(" and (p.pet like '[\"[2,%' or p.pet like '[\"[3,%' or p.pet like '[\"[4,%')");
        }
        Map<String, Object> out = paged(q, "select p.id player_id, p.name player_name, p.gender, p.head, p.items_body, p.pet", w.toString(), "p.id desc", args.toArray());
        for (Object o : (List<?>) out.get("rows")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> r = (Map<String, Object>) o;
            r.put("avatar", avatarOf(r.remove("head"), r.remove("items_body")));
            String raw = str(r.remove("pet"));
            long pid = ((Number) r.get("player_id")).longValue();
            Player on = Client.gI().getPlayerByID((int) pid);
            if (on == null) {
                on = nro.models.Bot.BotWorld.gI().find(pid);
            }
            List<Map<String, Object>> skills = new ArrayList<>();
            List<Map<String, Object>> items = new ArrayList<>();
            if (on != null && on.pet != null) {
                nro.models.player.Pet pet = on.pet;
                nro.models.player.NPoint n = pet.nPoint;
                fillPet(r, pet.typePet, pet.gender, pet.name, n.power, n.hpg, n.mpg, n.dameg, n.defg, n.critg, n.hpMax, n.mpMax, n.dame);
                r.put("online", true);
                for (nro.models.skill.Skill sk : pet.playerSkill.skills) {
                    if (sk != null && sk.template != null && sk.template.id >= 0) {
                        skills.add(Map.of("name", sk.template.name, "level", sk.point, "icon", sk.template.iconId));
                    }
                }
                for (Item it : pet.inventory.itemsBody) {
                    if (it != null && it.isNotNullItem()) {
                        items.add(Map.of("name", it.template.name, "icon", (int) it.template.iconID, "info", it.getInfo()));
                    }
                }
            } else {
                JsonArray a = arr(raw);
                if (a == null || a.size() < 4) {
                    continue;
                }
                JsonArray h = arr(a.get(0).getAsString()), st = arr(a.get(1).getAsString());
                fillPet(r, h.get(0).getAsInt(), h.get(1).getAsInt(), h.get(2).getAsString(), st.get(1).getAsLong(), st.get(5).getAsLong(), st.get(6).getAsLong(),
                        st.get(7).getAsLong(), st.get(8).getAsLong(), st.get(9).getAsLong(), -1, -1, -1);
                r.put("online", false);
                JsonArray sks = arr(a.get(3).getAsString());
                for (int i = 0; sks != null && i < sks.size(); i++) {
                    JsonArray sk = arr(sks.get(i).getAsString());
                    int id = sk.get(0).getAsInt(), lv = sk.get(1).getAsInt();
                    if (id >= 0 && lv > 0) {
                        skills.add(Map.of("name", skillName(id), "level", lv, "icon", skillIcon(id)));
                    }
                }
                JsonArray body = arr(a.get(2).getAsString());
                for (int i = 0; body != null && i < body.size(); i++) {
                    JsonArray it = arr(body.get(i).getAsString());
                    int tid = it.get(0).getAsInt();
                    nro.models.player_system.Template.ItemTemplate t = tid >= 0 ? ItemService.gI().getTemplate(tid) : null;
                    if (t != null) {
                        items.add(Map.of("name", t.name, "icon", (int) t.iconID, "info", ""));
                    }
                }
            }
            r.put("skills", skills);
            r.put("items", items);
        }
        return out;
    }

    private static int skillIcon(int id) {
        for (nro.models.skill.NClass c : new ArrayList<>(Manager.NCLASS)) {
            for (nro.models.player_system.Template.SkillTemplate t : c.skillTemplatess) {
                if (t.id == id) {
                    return t.iconId;
                }
            }
        }
        return -1;
    }

    private static void fillPet(Map<String, Object> r, int type, int gender, String name, long power, long hpg, long mpg, long dameg, long defg, long critg,
            long hpMax, long mpMax, long dame) {
        r.put("pet_name", name.startsWith("$") ? name.substring(1) : name);
        r.put("type", type);
        r.put("type_name", type >= 0 && type < PET_TYPE.length ? PET_TYPE[type] : "Loại " + type);
        r.put("planet", PLANET[Math.max(0, Math.min(2, gender))]);
        r.put("power", power);
        r.put("hpg", hpg);
        r.put("mpg", mpg);
        r.put("dameg", dameg);
        r.put("def", defg * 4);
        r.put("crit", critg);
        r.put("hpMax", hpMax);
        r.put("mpMax", mpMax);
        r.put("dame", dame);
        r.put("pet_avatar", avatarOfHead(petHead(type, gender, power)));
    }

    /** Tổng quan: số trận hôm nay / 7 ngày, bang và người thắng nhiều nhất 30 ngày, danh sách giải đặc biệt kèm người thắng. */
    private Object nrsdSummary() throws Exception {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("today", scalar("select count(*) from nrsd_result where date(played_at) = curdate()"));
        m.put("week", scalar("select count(*) from nrsd_result where played_at >= now() - interval 7 day"));
        m.put("total", scalar("select count(*) from nrsd_result"));
        m.put("clans", rows("select clan_name name, count(*) n from nrsd_result where clan_name is not null and played_at >= now() - interval 30 day group by clan_name order by n desc limit 5"));
        m.put("players", rows("select player_name name, count(*) n from nrsd_result where played_at >= now() - interval 30 day group by player_id, player_name order by n desc limit 5"));
        List<Map<String, Object>> ts = rows("select t.id, t.name, date_format(t.play_date, '%Y-%m-%d') play_date, t.rewards, date_format(t.created_at, '%Y-%m-%d') created_at, (select count(*) from nrsd_result r where date(r.played_at) = t.play_date) wins from nrsd_tournament t order by t.play_date desc");
        for (Map<String, Object> t : ts) {
            List<Map<String, Object>> rw = new ArrayList<>();
            for (String part : str(t.get("rewards")).split(",")) {
                String[] kv = part.split(":");
                if (kv.length == 2) {
                    int id = Integer.parseInt(kv[0].trim());
                    var tpl = nro.models.services.ItemService.gI().getTemplate(id);
                    rw.add(Map.of("id", id, "qty", Integer.parseInt(kv[1].trim()), "name", tpl != null ? tpl.name : "#" + id, "icon", tpl != null ? tpl.iconID : -1));
                }
            }
            t.put("items", rw);
        }
        m.put("tournaments", ts);
        return m;
    }

    private Object nrsdTournamentSave(Map<String, Object> b) throws Exception {
        String name = str(b.get("name")).trim(), day = str(b.get("play_date")).trim(), rewards = str(b.get("rewards")).replace(" ", "");
        if (name.isEmpty() || name.length() > 128 || !day.matches("\\d{4}-\\d{2}-\\d{2}") || !rewards.matches("\\d{1,5}:\\d{1,6}(,\\d{1,5}:\\d{1,6})*")) {
            return Map.of("ok", false, "error", "Cần tên giải, ngày (yyyy-mm-dd) và phần thưởng dạng mã:số lượng, cách nhau dấu phẩy");
        }
        for (String part : rewards.split(",")) {
            if (nro.models.services.ItemService.gI().getTemplate(Integer.parseInt(part.split(":")[0])) == null) {
                return Map.of("ok", false, "error", "Không có vật phẩm mã " + part.split(":")[0]);
            }
        }
        if (b.get("id") != null && lng(b.get("id")) > 0) {
            update("update nrsd_tournament set name=?, play_date=?, rewards=? where id=?", name, day, rewards, lng(b.get("id")));
        } else {
            update("insert into nrsd_tournament (name, play_date, rewards) values (?, ?, ?)", name, day, rewards);
        }
        return Map.of("ok", true, "note", "Đã lưu giải " + name);
    }

    // ------------------------------------------------------------------ shop

    private Object shopSave(Map<String, Object> b) throws Exception {
        if (b.get("id") != null && lng(b.get("id")) > 0) {
            update("update item_shop set temp_id=?, cost=?, type_sell=?, is_sell=?, is_new=? where id=?",
                    intv(b.get("temp_id")), intv(b.get("cost")), intv(b.get("type_sell")), intv(b.get("is_sell")), intv(b.get("is_new")), lng(b.get("id")));
        } else {
            update("insert into item_shop (tab_id, temp_id, is_new, is_sell, type_sell, cost, icon_spec, create_time) values (?, ?, ?, ?, ?, ?, 0, now())",
                    intv(b.get("tab_id")), intv(b.get("temp_id")), intv(b.get("is_new")), intv(b.getOrDefault("is_sell", 1)), intv(b.get("type_sell")), intv(b.get("cost")));
        }
        return shopReload();
    }

    private Object shopDelete(Map<String, Object> b) throws Exception {
        update("delete from item_shop_option where item_shop_id=?", lng(b.get("id")));
        update("delete from item_shop where id=?", lng(b.get("id")));
        return shopReload();
    }

    private Object shopReload() throws Exception {
        try (Connection con = LocalManager.getConnection()) {
            Manager.SHOPS = nro.models.database.ShopDAO.getShops(con);
        }
        return Map.of("ok", true, "shops", Manager.SHOPS.size());
    }

    // ------------------------------------------------------------------ máy chủ / thẻ radar / danh hiệu / hiệu ứng

    private Object serverInfo() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("signature", nro.models.server.JarSignatureCheck.status);
        m.put("java", System.getProperty("java.version"));
        m.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        m.put("vsData", nro.models.data.DataGame.vsData);
        m.put("vsItem", nro.models.data.DataGame.vsItem);
        m.put("event", SeasonEventService.gI().currentName());
        m.put("eventConfig", SeasonEventService.configActive().toString());
        return m;
    }

    private Object radarList() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (nro.models.radar.RadarCard r : nro.models.services.RadarService.gI().RADAR_TEMPLATE) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.Id);
            m.put("icon", r.IconId);
            m.put("name", r.Name);
            m.put("info", r.Info);
            m.put("rank", r.Rank);
            m.put("max", r.Max);
            m.put("thresholds", nro.models.services.RadarService.thresholds(r.Id, r.Max));
            int[] req = nro.models.services.RadarService.requirement(r.Id);
            m.put("require", req == null ? (r.Require >= 0 ? r.Require + " Lv." + Math.max(1, r.RequireLevel) : "") : req[0] + " Lv." + req[1]);
            m.put("aura", new int[]{nro.models.services.RadarService.auraFor(r.Id, 1), nro.models.services.RadarService.auraFor(r.Id, 2), nro.models.services.RadarService.auraFor(r.Id, 3)});
            List<Map<String, Object>> ops = new ArrayList<>();
            for (nro.models.radar.OptionCard o : r.Options) {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("id", o.id);
                x.put("param", o.param);
                x.put("activeCard", o.active);
                try {
                    x.put("text", nro.models.services.ItemService.gI().getItemOptionTemplate(o.id).name.replace("#", String.valueOf(o.param)));
                } catch (Exception e) {
                    x.put("text", "option " + o.id);
                }
                ops.add(x);
            }
            m.put("options", ops);
            out.add(m);
        }
        return out;
    }

    private Object radarSave(Map<String, Object> b) throws Exception {
        int id = intv(b.get("id"));
        String opts = str(b.get("options"));
        org.json.simple.JSONArray arr = (org.json.simple.JSONArray) org.json.simple.JSONValue.parse(opts);
        if (arr == null) {
            throw new Exception("Dòng chỉ số không đúng dạng JSON");
        }
        for (Object o : arr) {
            org.json.simple.JSONObject j = (org.json.simple.JSONObject) o;
            if (j.get("id") == null || j.get("param") == null || j.get("activeCard") == null) {
                throw new Exception("Mỗi dòng cần id, param, activeCard");
            }
        }
        try (Connection con = LocalManager.getConnection()) {
            try (java.sql.PreparedStatement ps = con.prepareStatement("update radar set name = ?, info = ?, options = ? where id = ?")) {
                ps.setString(1, str(b.get("name")));
                ps.setString(2, str(b.get("info")));
                ps.setString(3, arr.toJSONString());
                ps.setInt(4, id);
                ps.executeUpdate();
            }
            nro.models.services.RadarService.load(con);
        }
        return Map.of("ok", true, "note", "Đã lưu và nạp lại thẻ radar (người chơi mở lại bảng thẻ để thấy)");
    }

    private Object badgeSave(Map<String, Object> b) throws Exception {
        String opts = str(b.get("options"));
        org.json.simple.JSONArray arr = (org.json.simple.JSONArray) org.json.simple.JSONValue.parse(opts);
        if (arr == null) {
            throw new Exception("Chỉ số không đúng dạng JSON");
        }
        try (Connection con = LocalManager.getConnection()) {
            try (java.sql.PreparedStatement ps = con.prepareStatement("update data_badges set NAME = ?, Options = ? where id = ?")) {
                ps.setString(1, str(b.get("name")));
                ps.setString(2, arr.toJSONString());
                ps.setInt(3, intv(b.get("id")));
                ps.executeUpdate();
            }
            nro.models.player_badges.BagesTemplate.reload(con);
        }
        return Map.of("ok", true, "note", "Đã lưu và nạp lại danh hiệu");
    }

    private Object fxInfo() {
        Map<String, Object> m = new LinkedHashMap<>();
        java.util.TreeSet<Integer> eff = new java.util.TreeSet<>();
        java.io.File[] ef = new java.io.File("data/effdata").listFiles();
        if (ef != null) {
            for (java.io.File f : ef) {
                java.util.regex.Matcher mt = java.util.regex.Pattern.compile("_(\\d+)$").matcher(f.getName());
                if (mt.find()) {
                    eff.add(Integer.parseInt(mt.group(1)));
                }
            }
        }
        java.util.TreeSet<Integer> aura = new java.util.TreeSet<>();
        java.io.File[] af = new java.io.File("data/img_by_name/x2").listFiles();
        if (af != null) {
            for (java.io.File f : af) {
                java.util.regex.Matcher mt = java.util.regex.Pattern.compile("^aura_(\\d+)_\\d+\\.png$").matcher(f.getName());
                if (mt.find() && !mt.group(1).equals("999") && Integer.parseInt(mt.group(1)) < 1000) { // >= 1000: bản gộp 2 lớp (tools/aura_merge.py)
                    aura.add(Integer.parseInt(mt.group(1)));
                }
            }
        }
        m.put("effects", eff);
        m.put("auras", aura);
        List<Map<String, Object>> used = new ArrayList<>();
        used.add(Map.of("what", "Đủ 5 món Set kích hoạt (mọi set, kể cả Champa)", "kind", "hiệu ứng", "id", Service.EFF_SET_KICH_HOAT));
        used.add(Map.of("what", "Đủ 5 món Set Beerus", "kind", "hiệu ứng", "id", Service.EFF_SET_BEERUS));
        used.add(Map.of("what", "Hào quang chân Sao Pha Lê 10 (đồ đập 10 sao trong sự kiện / vật phẩm hào quang)", "kind", "hiệu ứng", "id", nro.models.services.SaoPhaLe10Service.EFF_HAO_QUANG));
        for (Map.Entry<Integer, Integer> e : new java.util.TreeMap<>(nro.models.services.Event2026Service.FOOT_EFFECT).entrySet()) {
            String n;
            try {
                n = nro.models.services.ItemService.gI().getTemplate(e.getKey()).name;
            } catch (Exception x) {
                n = "Bóng mờ " + e.getKey();
            }
            used.add(Map.of("what", n, "kind", "hiệu ứng", "id", e.getValue()));
        }
        for (int[] c : new int[][]{{956}, {1204}, {nro.models.services.RadarService.OOZARU}, {2040}}) {
            String n = nro.models.services.RadarService.gI().RADAR_TEMPLATE.stream().filter(r -> r.Id == c[0]).map(r -> r.Name).findFirst().orElse("Thẻ " + c[0]);
            for (int lv = 1; lv <= 3; lv++) {
                used.add(Map.of("what", n + " Lv." + lv, "kind", "aura", "id", (int) nro.models.services.RadarService.auraFor(c[0], lv)));
            }
        }
        m.put("used", used);
        return m;
    }

    // ------------------------------------------------------------------ sự kiện mùa

    private Object seasons() {
        return SeasonEventService.gI().panelList();
    }

    // ------------------------------------------------------------------ boss

    private static List<Boss> allBosses() {
        List<Boss> l = new ArrayList<>();
        try {
            l.addAll(new ArrayList<>(BossManager.gI().getBosses()));
            l.addAll(new ArrayList<>(BrolyManager.gI().getBosses()));
            // boss sự kiện (Halloween, Trung Thu, Ngũ Hành Sơn, Giáng sinh, Hùng Vương, Tết)
            for (BossManager m : new BossManager[]{nro.models.boss.Boss_Manager.TrungThuEventManager.gI(), nro.models.boss.Boss_Manager.HalloweenEventManager.gI(),
                nro.models.boss.Boss_Manager.ChristmasEventManager.gI(), nro.models.boss.Boss_Manager.HungVuongEventManager.gI(),
                nro.models.boss.Boss_Manager.LunarNewYearEventManager.gI()}) {
                l.addAll(new ArrayList<>(m.getBosses()));
            }
        } catch (Exception ignored) {
        }
        return l;
    }

    private static String bossState(Boss b) {
        if (b.zone != null && !b.isDie()) {
            return "Đang ở bản đồ";
        }
        if (b.bossStatus == nro.models.consts.BossStatus.RESPAWN || b.bossStatus == nro.models.consts.BossStatus.JOIN_MAP) {
            return "Sắp xuất hiện";
        }
        return "Đang nghỉ";
    }

    private Object bosses() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Boss b : allBosses()) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("key", System.identityHashCode(b));
            o.put("id", b.id);
            o.put("name", b.name);
            int av = -1;
            try {
                av = avatarOfHead(b.getHead()); // boss vừa tạo chưa xuất hiện (cấp -1) thì không lấy được đầu
            } catch (Exception ignored) {
            }
            o.put("avatar", av);
            o.put("gender", (int) b.gender);
            o.put("state", bossState(b));
            o.put("alive", b.zone != null && !b.isDie());
            o.put("map", b.zone != null ? b.zone.map.mapName + " (khu " + b.zone.zoneId + ")" : "");
            o.put("mapId", b.zone != null ? b.zone.map.mapId : -1);
            o.put("hp", b.nPoint.hp);
            o.put("hpMax", b.nPoint.hpMax);
            o.put("dame", b.nPoint.dame);
            o.put("panel", PANEL_BOSSES.containsKey(b));
            list.add(o);
        }
        List<Map<String, Object>> ids = new ArrayList<>();
        for (Field f : BossID.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == int.class) {
                try {
                    ids.add(Map.of("key", f.getName(), "id", f.getInt(null)));
                } catch (Exception ignored) {
                }
            }
        }
        return Map.of("bosses", list, "ids", ids);
    }

    /** Danh sách boss gọi được: tên thật lấy theo boss đang có trên server (cùng mã), chưa có thì theo tên hằng số. */
    private Object bossTemplates() {
        Map<Integer, String> names = new java.util.HashMap<>();
        Map<Integer, Integer> count = new java.util.HashMap<>();
        for (Boss b : allBosses()) {
            names.putIfAbsent((int) b.id, b.name);
            count.merge((int) b.id, 1, Integer::sum);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (Field f : BossID.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || f.getType() != int.class) {
                continue;
            }
            try {
                int id = f.getInt(null);
                if (!seen.add(id)) {
                    continue;
                }
                String key = f.getName();
                String pretty = key.charAt(0) + key.substring(1).toLowerCase().replace('_', ' ');
                out.add(Map.of("id", id, "key", key, "name", names.getOrDefault(id, pretty), "running", count.getOrDefault(id, 0)));
            } catch (Exception ignored) {
            }
        }
        out.sort(Comparator.comparing(x -> String.valueOf(x.get("name")), String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    // Boss gọi từ panel: {boss -> [chỉ 1 lần?, đã vào map?]}; zoneFinal chỉ dùng cho lần xuất hiện đầu, boss 1 lần thì đi rồi là xoá hẳn
    private static final Map<Boss, boolean[]> PANEL_BOSSES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<Boss, nro.models.map.Zone> PANEL_TARGET = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ScheduledExecutorService BOSS_WATCH = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "PanelBossWatch");
        t.setDaemon(true);
        return t;
    });

    static {
        BOSS_WATCH.scheduleWithFixedDelay(() -> {
            try {
                for (Map.Entry<Boss, boolean[]> e : PANEL_BOSSES.entrySet()) {
                    Boss b = e.getKey();
                    boolean[] st = e.getValue();
                    if (b.zone != null && !st[1]) {
                        st[1] = true;
                        b.zoneFinal = null; // lần sau boss xuất hiện theo map mặc định
                        nro.models.map.Zone target = PANEL_TARGET.remove(b);
                        if (target != null && b.zone != target) { // boss tự chọn map (Broly...): đưa về đúng khu đã chọn
                            int x = target.map.mapWidth > 200 ? nro.models.utils.Util.nextInt(100, target.map.mapWidth - 100) : 100;
                            nro.models.map.service.ChangeMapService.gI().changeMap(b, target, x, target.map.yPhysicInTop(x, 100));
                        }
                    } else if (st[1] && st[0] && (b.zone == null || b.isDie()) && b.bossStatus != nro.models.consts.BossStatus.DIE
                            && b.bossStatus != nro.models.consts.BossStatus.CHAT_E && b.bossStatus != nro.models.consts.BossStatus.LEAVE_MAP) {
                        removeBoss(b); // boss gọi 1 lần: đã bị hạ / rời map thì không hồi sinh nữa
                        PANEL_BOSSES.remove(b);
                    } else if (!st[0] && st[1]) {
                        PANEL_BOSSES.remove(b);
                    }
                }
            } catch (Exception ex) {
                nro.models.utils.Logger.error("Panel: lỗi theo dõi boss: " + ex.getMessage() + "\n");
            }
        }, 3, 3, java.util.concurrent.TimeUnit.SECONDS);
    }

    private static void removeBoss(Boss b) {
        for (BossManager m : new BossManager[]{BossManager.gI(), BrolyManager.gI()}) {
            m.removeBoss(b);
        }
    }

    private static Boss bossByKey(Map<String, Object> b) {
        int key = intv(b.get("key"));
        for (Boss boss : allBosses()) {
            if (System.identityHashCode(boss) == key) {
                return boss;
            }
        }
        throw new IllegalStateException("Không còn thấy boss này, hãy làm mới danh sách");
    }

    /** Gọi boss: {bossId, count 1 - 10, mapId (bỏ trống: map mặc định của boss), zone (bỏ trống: ngẫu nhiên), once: chỉ xuất hiện 1 lần}. */
    private Object bossSpawn(Map<String, Object> b) {
        int bossId = intv(b.get("bossId"));
        int count = Math.max(1, Math.min(10, b.get("count") == null ? 1 : intv(b.get("count"))));
        boolean once = Boolean.parseBoolean(String.valueOf(b.getOrDefault("once", "false")));
        nro.models.map.Zone zone = null;
        String mapStr = str(b.get("mapId")).trim();
        if (!mapStr.isEmpty()) {
            nro.models.map.Map m = nro.models.map.service.MapService.gI().getMapById(Integer.parseInt(mapStr));
            if (m == null || m.zones.isEmpty()) {
                throw new IllegalArgumentException("Không có bản đồ " + mapStr);
            }
            String zs = str(b.get("zone")).trim();
            int z = zs.isEmpty() ? nro.models.utils.Util.nextInt(0, m.zones.size() - 1) : Integer.parseInt(zs);
            if (z < 0 || z >= m.zones.size()) {
                throw new IllegalArgumentException(m.mapName + " chỉ có khu 0 - " + (m.zones.size() - 1));
            }
            zone = m.zones.get(z);
        }
        String name = null;
        for (int i = 0; i < count; i++) {
            Boss boss = BossManager.gI().createBoss(bossId);
            if (boss == null) {
                throw new IllegalStateException("Không gọi được boss (BossManager chưa hỗ trợ ID này)");
            }
            name = str(boss.name).isBlank() && boss.data != null && boss.data.length > 0 ? boss.data[0].getName() : str(boss.name);
            if (zone != null) {
                boss.zoneFinal = zone;
                PANEL_TARGET.put(boss, zone);
            }
            if (zone != null || once) {
                PANEL_BOSSES.put(boss, new boolean[]{once, false});
            }
        }
        return Map.of("ok", true, "name", name, "note", "Đã gọi " + count + " boss " + name
                + (zone != null ? " tại " + zone.map.mapName + " khu " + zone.zoneId : "") + (once ? " (chỉ 1 lần)" : ""));
    }

    private Object bossRemove(Map<String, Object> b) {
        Boss boss = b.get("key") != null ? bossByKey(b) : null;
        if (boss == null) {
            long id = lng(b.get("id"));
            for (Boss x : allBosses()) {
                if (x.id == id && x.zone != null) {
                    boss = x;
                    break;
                }
            }
        }
        boolean delete = Boolean.parseBoolean(String.valueOf(b.getOrDefault("delete", "false")));
        if (boss == null || (boss.zone == null && !delete)) {
            throw new IllegalStateException("Không thấy boss đang ở trên bản đồ");
        }
        if (boss.zone != null) {
            boss.leaveMap();
        }
        if (delete) {
            removeBoss(boss); // xoá hẳn: không hồi sinh nữa (tới lần khởi động server sau)
            PANEL_BOSSES.remove(boss);
        }
        return Map.of("ok", true, "note", delete ? "Đã xoá hẳn boss " + boss.name : "Boss " + boss.name + " đã rời bản đồ");
    }

    /** Boss đang nghỉ: cho xuất hiện ngay. */
    private Object bossRespawn(Map<String, Object> b) {
        Boss boss = bossByKey(b);
        if (boss.zone != null && !boss.isDie()) {
            throw new IllegalStateException("Boss đang ở trên bản đồ");
        }
        boss.changeStatus(nro.models.consts.BossStatus.RESPAWN);
        return Map.of("ok", true, "note", "Boss " + boss.name + " sắp xuất hiện");
    }

    // ------------------------------------------------------------------ thông báo, bảo trì

    private Object notify(Map<String, Object> b) {
        String text = str(b.get("text")).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Thiếu nội dung");
        }
        switch (str(b.get("type"))) {
            case "popup" ->
                Service.gI().sendThongBaoAllPlayer(text);
            case "world" -> {
                List<Player> on = onlinePlayers();
                if (on.isEmpty()) {
                    throw new IllegalStateException("Không có ai online để gửi chat thế giới");
                }
                ChatGlobalService.gI().chatVip(on.get(0), "[Hệ thống] " + text);
            }
            default ->
                ServerNotify.gI().notify(text);
        }
        return Map.of("ok", true);
    }

    private Object maintenance(Map<String, Object> b) {
        if (Maintenance.isRunning) {
            throw new IllegalStateException("Server đang bảo trì");
        }
        int sec = Math.max(10, intv(b.getOrDefault("seconds", 60)));
        playerSave(Map.of());
        Maintenance.gI().startSeconds(sec);
        return Map.of("ok", true, "seconds", sec);
    }

    private Object audit() throws Exception {
        File f = new File("logs/panel_actions.log");
        if (!f.exists()) {
            return List.of();
        }
        List<String> all = java.nio.file.Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
        List<String> out = new ArrayList<>(all.subList(Math.max(0, all.size() - 300), all.size()));
        java.util.Collections.reverse(out);
        return out;
    }

    // ------------------------------------------------------------------ SQL, cấu hình

    private Object sql(String query) throws Exception {
        String s = query.trim().toLowerCase();
        if (!(s.startsWith("select") || s.startsWith("show") || s.startsWith("describe") || s.startsWith("desc ") || s.startsWith("explain"))) {
            throw new IllegalArgumentException("Chỉ cho phép câu lệnh đọc (SELECT, SHOW, DESCRIBE, EXPLAIN)");
        }
        if (query.contains(";") && query.indexOf(';') < query.trim().length() - 1) {
            throw new IllegalArgumentException("Chỉ chạy 1 câu lệnh");
        }
        List<Map<String, Object>> r = rows(query.replaceAll(";\\s*$", ""));
        return r.size() > 500 ? r.subList(0, 500) : r;
    }

    private static final List<String> EDITABLE = List.of("server.name", "server.expserver", "server.maxplayer", "server.maxperip", "server.waitlogin",
            "panel.port", "panel.host", "panel.user", "panel.pass", "panel.enable", "admin.xuyengiap");

    private Object config() throws Exception {
        Properties p = loadConfig();
        Map<String, Object> m = new LinkedHashMap<>();
        for (String k : p.stringPropertyNames().stream().sorted().toList()) {
            String v = p.getProperty(k);
            m.put(k, k.contains("pass") ? (v.isEmpty() ? "" : "******") : v);
        }
        return Map.of("values", m, "editable", EDITABLE);
    }

    private Object configSave(Map<String, Object> b) throws Exception {
        File f = new File("Config.properties");
        List<String> lines = java.nio.file.Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
        Map<String, String> changes = new LinkedHashMap<>();
        Object v = b.get("values");
        if (v instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String k = String.valueOf(e.getKey());
                String val = String.valueOf(e.getValue()).replace("\n", "").trim();
                if (EDITABLE.contains(k) && !val.equals("******")) {
                    changes.put(k, val);
                }
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            int eq = l.indexOf('=');
            if (eq > 0 && !l.trim().startsWith("#")) {
                String k = l.substring(0, eq).trim();
                if (changes.containsKey(k)) {
                    lines.set(i, k + "=" + changes.remove(k));
                }
            }
        }
        for (Map.Entry<String, String> e : changes.entrySet()) {
            lines.add(e.getKey() + "=" + e.getValue());
        }
        try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write(String.join("\n", lines) + "\n");
        }
        if (v instanceof Map<?, ?> m && m.get("admin.xuyengiap") != null) { // áp dụng ngay, không cần khởi động lại
            try {
                nro.models.server.Manager.ADMIN_XUYEN_GIAP = Math.max(0, Math.min(100, Integer.parseInt(String.valueOf(m.get("admin.xuyengiap")).trim())));
            } catch (NumberFormatException ignored) {
            }
        }
        return Map.of("ok", true, "note", "Đã lưu Config.properties (admin.xuyengiap áp dụng ngay, mục khác cần khởi động lại server)");
    }

    private static Properties loadConfig() throws Exception {
        Properties p = new Properties();
        try (InputStreamReader r = new InputStreamReader(new FileInputStream("Config.properties"), StandardCharsets.UTF_8)) {
            p.load(r);
        }
        return p;
    }

    // ------------------------------------------------------------------ bản đồ & NPC

    private Object maps() throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows("select id, NAME name, planet_id, zones, max_player from map_template order by id")) {
            nro.models.map.Map m = nro.models.map.service.MapService.gI().getMapById(intv(r.get("id")));
            r.put("loaded", m != null);
            r.put("npcCount", m != null ? m.npcs.size() : 0);
            int online = 0;
            if (m != null) {
                for (nro.models.map.Zone z : m.zones) {
                    online += z.getNumOfPlayers();
                }
            }
            r.put("online", online);
            out.add(r);
        }
        return out;
    }

    private Object map(int id) throws Exception {
        nro.models.map.Map m = nro.models.map.service.MapService.gI().getMapById(id);
        if (m == null) {
            throw new IllegalArgumentException("Bản đồ " + id + " chưa được tải");
        }
        List<Map<String, Object>> npcs = new ArrayList<>();
        for (nro.models.npc.Npc n : m.npcs) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("tempId", n.tempId);
            o.put("name", npcName(n.tempId));
            o.put("x", n.cx);
            o.put("y", n.cy);
            npcs.add(o);
        }
        List<Map<String, Object>> db = rows("select npcs from map_template where id=?", id);
        return Map.of("id", id, "name", m.mapName, "width", m.mapWidth, "zones", m.zones.size(), "npcs", npcs,
                "dbNpcs", db.isEmpty() ? "[]" : str(db.get(0).get("npcs")));
    }

    private static String npcName(int tempId) {
        try {
            return Manager.NPC_TEMPLATES.get(tempId).name;
        } catch (Exception e) {
            return "NPC " + tempId;
        }
    }

    /** Đọc cột npcs của map_template (dạng [[id,x,y],...] hoặc ["[id,x,y]",...]). */
    private static List<int[]> dbNpcs(int mapId) throws Exception {
        List<int[]> out = new ArrayList<>();
        List<Map<String, Object>> r = rows("select npcs from map_template where id=?", mapId);
        JsonArray a = r.isEmpty() ? null : arr(r.get(0).get("npcs"));
        if (a != null) {
            for (JsonElement e : a) {
                JsonArray n = e.isJsonArray() ? e.getAsJsonArray() : parse(e.getAsString()).getAsJsonArray();
                out.add(new int[]{n.get(0).getAsInt(), n.get(1).getAsInt(), n.get(2).getAsInt()});
            }
        }
        return out;
    }

    private static void saveDbNpcs(int mapId, List<int[]> list) throws Exception {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            int[] n = list.get(i);
            sb.append(i > 0 ? "," : "").append("[").append(n[0]).append(",").append(n[1]).append(",").append(n[2]).append("]");
        }
        update("update map_template set npcs=? where id=?", sb.append("]").toString(), mapId);
    }

    private Object mapNpcAdd(Map<String, Object> b) throws Exception {
        int mapId = intv(b.get("mapId")), tempId = intv(b.get("tempId"));
        nro.models.map.Map m = nro.models.map.service.MapService.gI().getMapById(mapId);
        if (m == null) {
            throw new IllegalArgumentException("Bản đồ " + mapId + " chưa được tải");
        }
        if (tempId < 0 || tempId >= Manager.NPC_TEMPLATES.size()) {
            throw new IllegalArgumentException("Không có NPC " + tempId);
        }
        int x = intv(b.getOrDefault("x", 0));
        if (x <= 0) {
            x = m.mapWidth / 2;
        }
        int y = intv(b.getOrDefault("y", 0));
        if (y <= 0) {
            y = m.yPhysicInTop(x, 0);
        }
        nro.models.npc.Npc npc = nro.models.npc.NpcFactory.createNPC(mapId, 1, x, y, tempId);
        if (npc == null) {
            throw new IllegalStateException("Không tạo được NPC");
        }
        npc.map = m;
        m.npcs.add(npc);
        if (!Boolean.FALSE.equals(b.get("persist"))) {
            List<int[]> list = dbNpcs(mapId);
            list.add(new int[]{tempId, x, y});
            saveDbNpcs(mapId, list);
        }
        return Map.of("ok", true, "note", "Người chơi vào lại bản đồ sẽ thấy NPC");
    }

    private Object mapNpcRemove(Map<String, Object> b) throws Exception {
        int mapId = intv(b.get("mapId")), tempId = intv(b.get("tempId")), x = intv(b.getOrDefault("x", -1));
        nro.models.map.Map m = nro.models.map.service.MapService.gI().getMapById(mapId);
        int removed = 0;
        if (m != null) {
            for (nro.models.npc.Npc n : new ArrayList<>(m.npcs)) {
                if (n.tempId == tempId && (x < 0 || n.cx == x)) {
                    m.npcs.remove(n);
                    Manager.NPCS.remove(n);
                    removed++;
                }
            }
        }
        if (!Boolean.FALSE.equals(b.get("persist"))) {
            List<int[]> list = dbNpcs(mapId);
            list.removeIf(n -> n[0] == tempId && (x < 0 || n[1] == x));
            saveDbNpcs(mapId, list);
        }
        return Map.of("ok", true, "removed", removed, "note", "Người chơi vào lại bản đồ sẽ không còn thấy NPC");
    }

    // ------------------------------------------------------------------ chức năng thêm

    private Object playerTeleport(Map<String, Object> b) {
        Player on = Client.gI().getPlayerByID(intv(b.get("id")));
        if (on == null) {
            throw new IllegalStateException("Người chơi không online");
        }
        long to = lng(b.getOrDefault("to", 0));
        if (to > 0) { // tới chỗ 1 người chơi / bot (cùng khu)
            Player t = Client.gI().getPlayerByID((int) to);
            if (t == null) {
                t = nro.models.Bot.BotWorld.gI().find(to);
            }
            if (t == null || t.zone == null) {
                throw new IllegalStateException("Người cần tới không online");
            }
            nro.models.map.service.ChangeMapService.gI().changeMap(on, t.zone, t.location.x, t.location.y);
            return Map.of("ok", true, "map", t.zone.map.mapId, "zone", t.zone.zoneId);
        }
        int mapId = intv(b.get("mapId"));
        nro.models.map.Map m = nro.models.map.service.MapService.gI().getMapById(mapId);
        if (m == null) {
            throw new IllegalArgumentException("Không có bản đồ " + mapId);
        }
        int x = intv(b.getOrDefault("x", 0));
        if (x <= 0) {
            x = m.mapWidth / 2;
        }
        nro.models.map.service.ChangeMapService.gI().changeMapBySpaceShip(on, mapId, -1, x);
        return Map.of("ok", true);
    }

    private Object giveAll(Map<String, Object> b) throws Exception {
        int n = 0, full = 0;
        for (Player p : onlinePlayers()) {
            Map<String, Object> one = new LinkedHashMap<>(b);
            one.put("id", p.id);
            try {
                if (intv(b.getOrDefault("itemId", -1)) >= 0) {
                    playerGive(one);
                }
                if (lng(b.get("gold")) != 0 || lng(b.get("gem")) != 0 || lng(b.get("ruby")) != 0) {
                    playerMoney(one);
                }
                n++;
            } catch (IllegalStateException e) {
                full++;
            }
        }
        return Map.of("ok", true, "given", n, "bagFull", full);
    }

    private Object expRate(Map<String, Object> b) {
        if (b.get("rate") != null) {
            int r = Math.max(1, Math.min(100, intv(b.get("rate"))));
            Manager.RATE_EXP_SERVER = (byte) r;
        }
        return Map.of("ok", true, "rate", Manager.RATE_EXP_SERVER);
    }

    private Object tops(String kind) throws Exception {
        return switch (kind) {
            case "sukien" -> rows("select id, name, point_sukien point from player where " + Manager.NOT_ADMIN + " order by point_sukien desc limit 100");
            case "sukien1" -> rows("select id, name, point_sukien1 point from player where " + Manager.NOT_ADMIN + " order by point_sukien1 desc limit 100");
            case "sukien2" -> rows("select id, name, point_sukien2 point from player where " + Manager.NOT_ADMIN + " order by point_sukien2 desc limit 100");
            case "hopden" -> rows("select e.player_id id, p.name, e.hdba_count point from player_extra e join player p on p.id=e.player_id where p." + Manager.NOT_ADMIN + " order by e.hdba_count desc limit 100");
            case "naptien" -> rows("select a.id, a.username name, a.tongnap point from account a where a.is_admin = 0 order by a.tongnap desc limit 100");
            default -> {
                List<Map<String, Object>> out = new ArrayList<>();
                for (Map<String, Object> r : rows("select id, name, gender, head, items_body, data_point from player where " + Manager.NOT_ADMIN)) {
                    JsonArray pt = arr(r.get("data_point"));
                    long power = pt != null && pt.size() > 1 ? pt.get(1).getAsLong() : 0;
                    Map<String, Object> row = new LinkedHashMap<>(Map.of("id", r.get("id"), "name", r.get("name"), "point", power, "gender", r.get("gender")));
                    Player on = Client.gI().getPlayerByID(((Number) r.get("id")).intValue());
                    row.put("avatar", on != null ? avatarOfHead(on.getHead()) : avatarOf(r.get("head"), r.get("items_body")));
                    out.add(row);
                }
                out.sort((x, y) -> Long.compare(((Number) y.get("point")).longValue(), ((Number) x.get("point")).longValue()));
                yield out.subList(0, Math.min(100, out.size()));
            }
        };
    }

    // ------------------------------------------------------------------ database

    /** Chia trang SQL: đếm tổng rồi lấy đúng 1 trang (LIMIT/OFFSET), trả {rows, page, size, total, totalPages}. */
    /** Id các tài khoản bot riêng (không ai đăng nhập được). */
    private static final String BOT_ACC = "(select p.account_id from bot_account b join player p on p.id = b.player_id where b.dedicated = 1)";

    private static Map<String, Object> paged(Map<String, String> p, String select, String fromWhere, String order, Object... args) throws Exception {
        long total = PanelDirectory.numeric(rows("select count(*) n " + fromWhere, args).get(0).get("n"));
        PanelDirectory.Page pg = new PanelDirectory.Page(select + " " + fromWhere + " order by " + order + " limit ? offset ?", List.of(args),
                Math.max(1, PanelDirectory.integer(p.get("page"), 1)), PanelDirectory.size(p), total);
        return pg.result(rows(pg.sql, pg.args.toArray()));
    }

    /** Chia trang danh sách trong bộ nhớ, giữ nguyên thứ tự. */
    private static Map<String, Object> pagedList(List<Map<String, Object>> list, Map<String, String> p) {
        PanelDirectory.Page pg = new PanelDirectory.Page(null, List.of(), Math.max(1, PanelDirectory.integer(p.get("page"), 1)), PanelDirectory.size(p), list.size());
        int from = (pg.page - 1) * pg.size;
        return pg.result(new ArrayList<>(list.subList(from, Math.min(from + pg.size, list.size()))));
    }

    private static String like(Map<String, String> p) {
        String k = str(p.getOrDefault("q", "")).trim();
        k = k.length() > 100 ? k.substring(0, 100) : k;
        return "%" + k.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private static long idOf(Map<String, String> p) {
        String k = str(p.getOrDefault("q", "")).trim();
        return k.matches("\\d{1,18}") ? Long.parseLong(k) : -1;
    }

    public static List<Map<String, Object>> rows(String sql, Object... args) throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        try (Connection con = LocalManager.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    for (int c = 1; c <= md.getColumnCount(); c++) {
                        Object o = rs.getObject(c);
                        if (o instanceof byte[] bytes) {
                            o = new String(bytes, StandardCharsets.UTF_8);
                        } else if (o instanceof java.sql.Timestamp) {
                            // giữ nguyên để đọc giftcode; Gson tự chuyển thành chuỗi
                        }
                        m.put(md.getColumnLabel(c), o);
                    }
                    out.add(m);
                }
            }
        }
        return out;
    }

    public static int update(String sql, Object... args) throws Exception {
        try (Connection con = LocalManager.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps.executeUpdate();
        }
    }

    private static Object scalar(String sql) throws Exception {
        List<Map<String, Object>> r = rows(sql);
        return r.isEmpty() ? 0 : r.get(0).values().iterator().next();
    }

    @SuppressWarnings("deprecation")
    private static JsonElement parse(String s) {
        return new JsonParser().parse(s);
    }

    private static JsonArray arr(Object json) {
        try {
            return json == null ? null : parse(String.valueOf(json)).getAsJsonArray();
        } catch (Exception e) {
            return null;
        }
    }

    private static String mapName(int mapId) {
        try {
            nro.models.map.Map m = nro.models.map.service.MapService.gI().getMapById(mapId);
            return m != null ? m.mapName : "Map " + mapId;
        } catch (Exception e) {
            return "Map " + mapId;
        }
    }

    static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    static long lng(Object o) {
        if (o == null || String.valueOf(o).isBlank()) {
            return 0;
        }
        return o instanceof Number n ? n.longValue() : (long) Double.parseDouble(String.valueOf(o).trim());
    }

    static int intv(Object o) {
        return (int) lng(o);
    }
}
