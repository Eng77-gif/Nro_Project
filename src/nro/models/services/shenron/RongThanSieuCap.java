package nro.models.services.shenron;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import nro.models.consts.ConstItem;
import nro.models.item.Item;
import nro.models.map.service.NpcService;
import nro.models.network.Message;
import nro.models.player.Player;
import nro.models.server.Client;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.ItemTimeService;
import nro.models.services.PlayerService;
import nro.models.services.Service;

/**
 * Rồng thần siêu cấp: đủ 3 viên Ngọc rồng Siêu Cấp (ghép 7 viên 1 sao ở Bà Hạt Mít) thì gọi được rồng, ban 1 trong 5 điều ước.
 */
public class RongThanSieuCap {

    public static final int MENU_SUMMON = 31720500, MENU_WISH = 31720501, MENU_CONFIRM = 31720502;
    private static final int NEED = 3;
    private static final long WAIT_MS = 5 * 60_000L, BUFF_MS = 60 * 60_000L;
    private static final String SAY = "Ta là Rồng thần siêu cấp, ta sẽ ban cho ngươi một điều ước, ngươi có 5 phút, hãy chọn đi:\n"
            + "1) 100.000 ngọc xanh.\n"
            + "2) 5 tỷ vàng.\n"
            + "3) Tiềm năng sức mạnh X3 cho bản thân 60 phút.\n"
            + "4) Tiềm năng sức mạnh X3 cho đệ tử 60 phút.\n"
            + "5) 10 viên Đá bảo vệ.";

    private static final Map<Long, long[]> dragons = new ConcurrentHashMap<>(); // id -> {lúc gọi, điều ước đang chọn}

    public static boolean isItem(int id) {
        return id == ConstItem.NGOC_RONG_SIEU_CAP;
    }

    public static void openSummon(Player pl) {
        NpcService.gI().createMenuConMeo(pl, MENU_SUMMON, -1, "Bạn có muốn dùng " + NEED + " viên Ngọc rồng Siêu Cấp gọi Rồng thần siêu cấp không?", "Đồng ý", "Từ chối");
    }

    private static void summon(Player pl) {
        if (pl.zone == null) {
            return;
        }
        int map = pl.zone.map.mapId;
        if (map == 0 || map == 7 || map == 14 || map == 21 || map == 22 || map == 23) {
            Service.gI().sendThongBao(pl, "Không thể gọi rồng ở đây");
            return;
        }
        if (dragons.containsKey(pl.id) || pl.isShenronAppear || pl.shenronEvent != null) {
            Service.gI().sendThongBao(pl, "Không thể thực hiện");
            return;
        }
        Item nr = InventoryService.gI().findItemBag(pl, ConstItem.NGOC_RONG_SIEU_CAP);
        if (nr == null || nr.quantity < NEED) {
            Service.gI().sendThongBao(pl, "Cần " + NEED + " viên Ngọc rồng Siêu Cấp (đang có " + (nr == null ? 0 : nr.quantity) + ")");
            return;
        }
        InventoryService.gI().subQuantityItemsBag(pl, nr, NEED);
        InventoryService.gI().sendItemBags(pl);
        dragons.put(pl.id, new long[]{System.currentTimeMillis(), -1});
        sendDragon(pl, true);
        Service.gI().sendThongBao(pl, "Rồng thần siêu cấp đã xuất hiện!");
        NpcService.gI().createMenuRongThieng(pl, MENU_WISH, SAY, "Điều\nước 1", "Điều\nước 2", "Điều\nước 3", "Điều\nước 4", "Điều\nước 5");
    }

    private static void sendDragon(Player pl, boolean appear) {
        try {
            Message msg = new Message(-83);
            msg.writer().writeByte(appear ? 0 : 1);
            if (appear) {
                msg.writer().writeShort(pl.zone.map.mapId);
                msg.writer().writeShort(pl.zone.map.bgId);
                msg.writer().writeByte(pl.zone.zoneId);
                msg.writer().writeInt((int) pl.id);
                msg.writer().writeUTF("null");
                msg.writer().writeShort(pl.location.x);
                msg.writer().writeShort(pl.location.y);
                msg.writer().writeByte(0); // hình Rồng thần
            }
            Service.gI().sendMessAllPlayerInMap(pl, msg);
            msg.cleanup();
            pl.isShenronAppear = appear;
        } catch (Exception ignored) {
        }
    }

    /** Menu con mèo (gọi rồng) và rồng thiêng (chọn điều ước). Trả về true nếu là menu của Rồng thần siêu cấp. */
    public static boolean confirmMenu(Player pl, int menu, int select) {
        switch (menu) {
            case MENU_SUMMON -> {
                if (select == 0) {
                    summon(pl);
                }
                return true;
            }
            case MENU_WISH, MENU_CONFIRM -> {
                long[] st = dragons.get(pl.id);
                if (st == null) {
                    return true;
                }
                if (System.currentTimeMillis() - st[0] > WAIT_MS) {
                    leave(pl, "Hết giờ rồi, ta đi đây...");
                } else if (menu == MENU_WISH && select >= 0 && select < 5) {
                    st[1] = select + 1;
                    NpcService.gI().createMenuRongThieng(pl, MENU_CONFIRM, "Ngươi có chắc muốn ước điều ước " + st[1] + "?", "Điều\nước " + st[1], "Từ chối");
                } else if (menu == MENU_CONFIRM && select == 0 && st[1] > 0) {
                    wish(pl, (int) st[1]);
                } else {
                    NpcService.gI().createMenuRongThieng(pl, MENU_WISH, SAY, "Điều\nước 1", "Điều\nước 2", "Điều\nước 3", "Điều\nước 4", "Điều\nước 5");
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static void wish(Player pl, int wish) {
        long now = System.currentTimeMillis();
        switch (wish) {
            case 1 ->
                pl.inventory.gem += 100_000;
            case 2 ->
                pl.inventory.gold += 5_000_000_000L;
            case 3 ->
                pl.hlwSelfX3Until = Math.max(now, pl.hlwSelfX3Until) + BUFF_MS;
            case 4 -> {
                if (pl.pet == null) {
                    Service.gI().sendThongBao(pl, "Ngươi chưa có đệ tử, hãy ước điều khác");
                    return;
                }
                pl.hlwPetX3Until = Math.max(now, pl.hlwPetX3Until) + BUFF_MS;
            }
            case 5 -> {
                Item da = ItemService.gI().createNewItem((short) 987);
                da.quantity = 10;
                InventoryService.gI().addItemBag(pl, da);
                InventoryService.gI().sendItemBags(pl);
            }
            default -> {
                return;
            }
        }
        if (wish == 3 || wish == 4) {
            ItemTimeService.gI().sendItemTime(pl, ItemService.gI().getTemplate(ConstItem.NGOC_RONG_SIEU_CAP).iconID, (int) (BUFF_MS / 1000));
        }
        PlayerService.gI().sendInfoHpMpMoney(pl);
        leave(pl, "Điều ước của ngươi đã trở thành sự thật. Hẹn gặp lại!");
    }

    private static void leave(Player pl, String text) {
        if (dragons.remove(pl.id) == null) {
            return;
        }
        NpcService.gI().createTutorial(pl, 0, text);
        sendDragon(pl, false);
    }

    /** Gọi định kỳ: rồng tự đi sau 5 phút không ước. */
    public static void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<Long, long[]> e : dragons.entrySet()) {
            if (now - e.getValue()[0] > WAIT_MS) {
                Player pl = Client.gI().getPlayer(e.getKey());
                if (pl != null) {
                    leave(pl, "Có không ước mất đừng tìm...");
                } else {
                    dragons.remove(e.getKey());
                }
            }
        }
    }
}
