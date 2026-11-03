package nro.models.Bot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.Service;
import nro.models.services_func.Trade;
import nro.models.shop_ky_gui.ConsignItem;
import nro.models.shop_ky_gui.ConsignShopManager;
import nro.models.shop_ky_gui.ConsignShopService;
import nro.models.utils.Util;

/**
 * Tài khoản bot buôn bán như người chơi, chỉ bằng đồ / tiền thật của nhân vật: đem đồ trong hành trang lên ký gửi (trả phí 1 thỏi
 * vàng như người chơi, giá theo giá đang bán trên chợ), đồ bán được thì tự nhận tiền, săn đồ ký gửi rẻ hơn giá chợ; nhận giao
 * dịch với người chơi (người chơi đưa đồ: bot trả vàng theo giá chợ; người chơi chỉ đưa vàng: bot bán lại đồ vừa giá).
 */
public class BotTrade {

    private static final BotTrade I = new BotTrade();

    public static BotTrade gI() {
        return I;
    }

    /** 1 thỏi vàng quy ra vàng khi bot giao dịch bằng vàng. */
    static final long GOLD_PER_TV = 500_000_000L;
    private static final int THOI_VANG = 457;
    private static final int MAX_LISTING = 3;

    private final Random rnd = new Random();
    private final Map<Long, Long> nextSell = new HashMap<>();
    private final Map<Long, Long> nextBuy = new HashMap<>();

    // ------------------------------------------------------------------ giá
    /** Giá 1 món (thỏi vàng): theo giá thấp nhất người khác đang bán trên ký gửi, chưa ai bán thì theo bảng giá mặc định. */
    double unitPrice(Item it) {
        double best = -1;
        for (ConsignItem c : new ArrayList<>(ConsignShopManager.gI().listItem)) {
            if (c != null && !c.isBuy && c.itemId == it.template.id && c.goldSell > 0 && c.quantity > 0) {
                double u = (double) c.goldSell / c.quantity;
                if (best < 0 || u < best) {
                    best = u;
                }
            }
        }
        return best > 0 ? best : basePrice(it);
    }

    static double basePrice(Item it) {
        int id = it.template.id;
        if (id >= 14 && id <= 20) {
            return new int[]{300, 150, 80, 40, 15, 6, 3}[id - 14];
        }
        if (it.itemOptions.stream().anyMatch(o -> o.optionTemplate.id == 86 || o.optionTemplate.id == 87)) {
            return 25; // đồ ký gửi được (đồ thần, đồ hiếm)
        }
        switch (it.template.type) {
            case 14: // đá nâng cấp
                return 2;
            case 30: // sao pha lê
                return 6;
            case 15:
                return 3;
            default:
                return 1;
        }
    }

    static boolean tradeable(Item it) {
        if (it == null || !it.isNotNullItem() || it.template.id == THOI_VANG) {
            return false;
        }
        if (it.itemOptions.stream().anyMatch(o -> o.optionTemplate.id == 30)) {
            return false; // không thể giao dịch
        }
        return ConsignShopService.gI().itemCanConsign(it);
    }

    private static int thoiVang(Player b) {
        Item tv = InventoryService.gI().findItemBag(b, THOI_VANG);
        return tv == null ? 0 : tv.quantity;
    }

    // ------------------------------------------------------------------ ký gửi
    public void tick(List<Bot> accountBots) {
        long now = System.currentTimeMillis();
        for (Bot b : accountBots) {
            if (b.zone == null || b.isDie() || BotClan.inDungeon(b)) {
                continue;
            }
            try {
                claim(b);
                if (now >= nextSell.computeIfAbsent(b.id, k -> now + Util.nextInt(60, 600) * 1000L)) {
                    nextSell.put(b.id, now + Util.nextInt(8, 20) * 60_000L);
                    sell(b);
                }
                if (now >= nextBuy.computeIfAbsent(b.id, k -> now + Util.nextInt(120, 900) * 1000L)) {
                    nextBuy.put(b.id, now + Util.nextInt(10, 30) * 60_000L);
                    buy(b);
                }
            } catch (Exception e) {
                BotWorld.gI().logError(b, e);
            }
        }
    }

    /** Đồ đã bán: nhận tiền (thỏi vàng, trừ 10% phí như người chơi). */
    private void claim(Bot b) {
        for (ConsignItem c : new ArrayList<>(ConsignShopManager.gI().listItem)) {
            if (c != null && c.isBuy && c.player_sell == b.id) {
                ConsignShopService.gI().claimOrDel(b, (byte) 2, c.id);
            }
        }
    }

    private void sell(Bot b) {
        long mine = ConsignShopManager.gI().listItem.stream().filter(c -> c != null && !c.isBuy && c.player_sell == b.id).count();
        if (mine >= MAX_LISTING || thoiVang(b) < 1) {
            return;
        }
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < b.inventory.itemsBag.size(); i++) {
            if (tradeable(b.inventory.itemsBag.get(i))) {
                idx.add(i);
            }
        }
        if (idx.isEmpty()) {
            return;
        }
        int i = idx.get(rnd.nextInt(idx.size()));
        Item it = b.inventory.itemsBag.get(i);
        int qty = it.quantity > 1 ? Util.nextInt(1, Math.min(it.quantity, 99)) : 1;
        int price = (int) Math.max(1, Math.min(2_000_000_000L, Math.round(unitPrice(it) * qty * (0.9 + rnd.nextDouble() * 0.3))));
        ConsignShopService.gI().KiGui(b, i, price, (byte) 0, qty);
        if (mine == 0 && Util.isTrue(30, 100)) {
            Service.gI().chat(b, "vừa ký gửi " + it.template.name + ", ae ghé mua nhé");
        }
    }

    /** Săn đồ ký gửi rẻ (dưới 70% giá thường) như người chơi buôn đồ, cần đủ sức mạnh và thỏi vàng như người chơi. */
    private void buy(Bot b) {
        if (b.nPoint.power < 17_000_000_000L || InventoryService.gI().getCountEmptyBag(b) <= 2) {
            return;
        }
        int tv = thoiVang(b);
        ConsignItem pick = null;
        for (ConsignItem c : new ArrayList<>(ConsignShopManager.gI().listItem)) {
            if (c == null || c.isBuy || c.player_sell == b.id || c.goldSell <= 0 || c.goldSell > tv / 2 || c.quantity <= 0) {
                continue;
            }
            Item tmp = nro.models.services.ItemService.gI().createNewItem((short) c.itemId);
            tmp.itemOptions.addAll(c.options);
            if ((double) c.goldSell / c.quantity < basePrice(tmp) * 0.7) {
                pick = c;
                break;
            }
        }
        if (pick != null) {
            ConsignShopService.gI().buyItem(b, pick.id);
        }
    }

    // ------------------------------------------------------------------ giao dịch với người chơi
    /** Người chơi mời giao dịch: bot rảnh thì nhận. */
    public void onInvite(Player pl, Bot bot) {
        if (bot.isDie() || BotClan.inDungeon(bot) || nro.models.services_func.TransactionService.gI().check(bot) || !Util.isTrue(85, 100)) {
            Service.gI().chat(bot, "đang bận, lát nhé");
            return;
        }
        Trade t = new Trade(pl, bot);
        t.openTabTrade();
        Service.gI().chat(bot, "đưa đồ thì mình trả vàng, đưa vàng thì mình bán đồ");
    }

    /** Người chơi khoá giao dịch: bot ra giá rồi khoá và đồng ý; không hợp lý thì huỷ. */
    public void onPlayerLock(Trade t, Bot bot, List<Item> theirItems, int theirGold) {
        try {
            if (!theirItems.isEmpty()) {
                double val = 0;
                for (Item it : theirItems) {
                    if (!tradeable(it)) {
                        decline(t, bot, "món này mình không mua");
                        return;
                    }
                    val += unitPrice(it) * it.quantity;
                }
                long pay = Math.min((long) (val * GOLD_PER_TV * 0.8), Math.min(bot.inventory.gold, Trade.MAX_GOLD_TRADE_PER_TIME));
                if (pay < 1_000_000 || InventoryService.gI().getCountEmptyBag(bot) < theirItems.size()) {
                    decline(t, bot, "mình không đủ vàng / hết chỗ rồi");
                    return;
                }
                t.addItemTrade(bot, (byte) -1, (int) pay);
                Service.gI().chat(bot, "trả " + Util.numberToMoney(pay) + " vàng nhé");
            } else if (theirGold > 0) {
                int best = -1;
                double bestVal = 0;
                for (int i = 0; i < bot.inventory.itemsBag.size(); i++) {
                    Item it = bot.inventory.itemsBag.get(i);
                    if (!tradeable(it)) {
                        continue;
                    }
                    double v = unitPrice(it) * GOLD_PER_TV;
                    if (v <= theirGold && v > bestVal) {
                        best = i;
                        bestVal = v;
                    }
                }
                if (best < 0) {
                    decline(t, bot, "từng ấy vàng chưa mua được gì đâu");
                    return;
                }
                t.addItemTrade(bot, (byte) best, 1);
                Service.gI().chat(bot, "bán " + bot.inventory.itemsBag.get(best).template.name + " nhé");
            } else {
                decline(t, bot, "chưa đưa gì mà");
                return;
            }
            t.lockTran(bot);
            t.acceptTrade(bot);
        } catch (Exception e) {
            BotWorld.gI().logError(bot, e);
            t.cancelTrade();
        }
    }

    private void decline(Trade t, Bot bot, String why) {
        Service.gI().chat(bot, why);
        t.cancelTrade();
    }
}
