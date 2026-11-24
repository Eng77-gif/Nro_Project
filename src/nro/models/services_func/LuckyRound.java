package nro.models.services_func;

import nro.models.item.Item;
import java.io.IOException;
import java.util.ArrayList;
import nro.models.player.Player;
import nro.models.network.Message;
import nro.models.services.RewardService;
import nro.models.services.Service;
import java.util.List;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;

/**
 *
 * @author Việt
 * 
 */

public class LuckyRound {

    private static final byte MAX_ITEM_IN_BOX = 100;

    public static final byte USING_GEM = 7;
    public static final byte USING_GOLD = 0;
    public static final byte USING_TICKET = 1;

    private static final byte PRICE_GEM = 4;
    private static final int PRICE_GOLD = 250000000;
    private static final int PRICE_TICKET = 1;
    private static final int TICKET = 821;

    private static LuckyRound instance;

    public static LuckyRound gI() {
        if (instance == null) {
            instance = new LuckyRound();
        }
        return instance;
    }

    /** người đang mở giao diện "Vòng quay đặc biệt" (quay bằng vàng nhưng có vật phẩm đặc biệt / mùa sự kiện) */
    private final java.util.Set<Long> vipUi = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public void openCrackBallUI(Player pl, byte type) {
        pl.idMark.setTypeLuckyRound(type);
        vipUi.remove(pl.id);
        Message msg = null;
        try {
            msg = new Message(-127);
            msg.writer().writeByte(0);
            msg.writer().writeByte(7);
            for (int i = 0; i < 7; i++) {
                msg.writer().writeShort(419 + i);
            }
            msg.writer().writeByte(type);
            msg.writer().writeInt(type == USING_GEM ? PRICE_GEM : PRICE_GOLD);
            msg.writer().writeShort(-1);
            pl.sendMessage(msg);
        } catch (IOException e) {
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void openCrackBallVipUI(Player pl, byte type) {
        pl.idMark.setTypeLuckyRound(type);
        vipUi.add(pl.id);
        Message msg = null;
        try {
            msg = new Message(-127);
            msg.writer().writeByte(0);
            msg.writer().writeByte(7);
            for (int i = 0; i < 7; i++) {
                msg.writer().writeShort(419 + i); // ngọc rồng 1 - 7 sao (trước đây gửi toàn 1 sao)
            }
            msg.writer().writeByte(type);
            msg.writer().writeInt(type == USING_GEM ? PRICE_GEM : PRICE_GOLD);
            msg.writer().writeShort(-1);
            pl.sendMessage(msg);
        } catch (IOException e) {
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    public void readOpenBall(Player player, Message msg) {
        try {
            msg.reader().readByte();
            byte count = msg.reader().readByte();
            if (count <= 0 || count > 10) {
                return; // số lần âm sẽ thành giá âm (cộng ngọc/vàng)
            }
            switch (player.idMark.getTypeLuckyRound()) {
                case USING_GEM:
                    openBallByGem(player, count);
                    break;
                case USING_GOLD:
                    openBallByGold(player, count);
                    break;
                case USING_TICKET:
                    openBallByTicket(player, count);
                    break;
            }
        } catch (Exception e) {
            switch (player.idMark.getTypeLuckyRound()) {
                case USING_GEM:
                    openCrackBallVipUI(player, player.idMark.getTypeLuckyRound());
                    break;
                default:
                    if (vipUi.contains(player.id)) {
                        openCrackBallVipUI(player, player.idMark.getTypeLuckyRound());
                    } else {
                        openCrackBallUI(player, player.idMark.getTypeLuckyRound());
                    }
                    break;
            }
        }
    }

    private void openBallByGem(Player player, byte count) {
        int gemNeed = (count * PRICE_GEM);
        if (player.inventory.gem < gemNeed) {
            Service.gI().sendThongBao(player, "Bạn không đủ ngọc để mở");
        } else {
            if (count + player.inventory.itemsBoxCrackBall.size() <= MAX_ITEM_IN_BOX) {
                player.inventory.gem -= gemNeed;
                nro.models.services.SeasonEventService.gI().addTopPointById(nro.models.database.Dot128Migration.TOP_VQ_NGOC, player, count); // hè 2: top vòng quay ngọc
                List<Item> list = RewardService.gI().getListItemLuckyRound(player, count, true);
                addItemToBox(player, list);
                sendReward(player, list);
                announce(player, list, "Vòng quay May mắn");
                Service.gI().sendMoney(player);
                gioToInfo(player);
            } else {
                Service.gI().sendThongBao(player, "Rương phụ đã đầy");
            }
        }
    }

    private void openBallByGold(Player player, byte count) {
        int goldNeed = (count * PRICE_GOLD);
        if (player.inventory.gold < goldNeed) {
            Service.gI().sendThongBao(player, "Bạn không đủ vàng để mở");
        } else {
            if (count + player.inventory.itemsBoxCrackBall.size() <= MAX_ITEM_IN_BOX) {
                player.inventory.gold -= goldNeed;
                nro.models.services.SeasonEventService.gI().addTopPointById(nro.models.database.Dot128Migration.TOP_VQ_VANG, player, count); // hè 2: top vòng quay vàng
                boolean vip = vipUi.contains(player.id);
                List<Item> list = RewardService.gI().getListItemLuckyRound(player, count, vip);
                addItemToBox(player, list);
                sendReward(player, list);
                announce(player, list, vip ? "Vòng quay May mắn" : "Vòng quay Vàng");
                Service.gI().sendMoney(player);
                if (vip) {
                    gioToInfo(player);
                }
            } else {
                Service.gI().sendThongBao(player, "Rương phụ đã đầy");
            }
        }
    }

    private void openBallByTicket(Player player, byte count) {
        int ticketNeed = (count * PRICE_TICKET);
        Item ticket = InventoryService.gI().findItemBag(player, TICKET);
        if (ticket == null || ticket.quantity < ticketNeed) {
            Service.gI().sendThongBao(player, "Bạn không đủ " + ItemService.gI().createNewItem((short) TICKET).template.name + " để quay");
            sendReward(player, new ArrayList<>());
        } else {
            if (count + player.inventory.itemsBoxCrackBall.size() <= MAX_ITEM_IN_BOX) {
                InventoryService.gI().subQuantityItemsBag(player, ticket, ticketNeed);
                InventoryService.gI().sendItemBags(player);
                List<Item> list = RewardService.gI().getListItemLuckyRound(player, count, true);
                addItemToBox(player, list);
                sendReward(player, list);
                announce(player, list, "Vòng quay May mắn");
                Service.gI().sendMoney(player);
                gioToInfo(player);
            } else {
                Service.gI().sendThongBao(player, "Rương phụ đã đầy");
            }
        }
    }

    /** Quay ra vật phẩm may mắn (cải trang, đeo lưng, ván bay / thú cưỡi, pet): báo toàn server. */
    private void announce(Player player, List<Item> items, String wheel) {
        if (player == null || !player.isPl()) {
            return;
        }
        for (Item it : items) {
            if (it == null || !it.isNotNullItem()) {
                continue;
            }
            int t = it.template.type;
            if (t != 5 && t != 11 && t != 23 && t != 24 && !(t == 27 && it.template.name.startsWith("Pet"))) {
                continue;
            }
            if (it.getOptionById(93) != null) {
                continue; // chỉ báo đồ vĩnh viễn
            }
            nro.models.server.ServerNotify.gI().notify("Chúc mừng " + player.name + " đã may mắn quay " + wheel + " nhận được "
                    + it.template.name + " hạn dùng vĩnh viễn");
        }
    }

    /** Mùa Giỗ tổ: báo số lần đã quay (cộng dồn cả mùa) và tỉ lệ ra đồ vĩnh viễn hiện tại. */
    private void gioToInfo(Player player) {
        if (RewardService.gioToWheelActive()) {
            Service.gI().sendThongBao(player, "Vòng quay Thượng Đế mùa Giỗ tổ: đã quay " + RewardService.eventSpins(player)
                    + " lần trong sự kiện, tỉ lệ ra đồ vĩnh viễn " + RewardService.gioToPermRate(player) + "% (quay càng nhiều càng cao)");
        }
    }

    private void sendReward(Player player, List<Item> items) {
        Message msg = null;
        try {
            msg = new Message(-127);
            msg.writer().writeByte(1);
            msg.writer().writeByte(items.size());
            for (Item item : items) {
                msg.writer().writeShort(item.template.iconID);
            }
            player.sendMessage(msg);
        } catch (IOException e) {
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    /** Vàng (vật phẩm loại 9) cộng thẳng vào hành trang kèm thông báo; vật phẩm khác vào rương phụ. */
    private void addItemToBox(Player player, List<Item> items) {
        long gold = 0;
        for (Item it : items) {
            if (it != null && it.template != null && it.template.type == 9) {
                gold += it.quantity;
            } else {
                player.inventory.itemsBoxCrackBall.add(it);
            }
        }
        if (gold > 0) {
            player.inventory.gold = Math.min(nro.models.player.Inventory.LIMIT_GOLD, player.inventory.gold + gold);
            Service.gI().sendMoney(player);
            Service.gI().sendThongBao(player, "Bạn nhận được " + nro.models.utils.Util.formatNumber(gold) + " vàng");
        }
    }
}
