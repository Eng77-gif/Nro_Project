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
import nro.models.utils.Util;

/**
 * Rồng thần siêu cấp: đủ 3 viên Ngọc rồng Siêu Cấp (ghép 7 viên 1 sao ở Bà Hạt Mít) thì gọi được rồng, ban 1 trong 8 điều ước.
 */
public class RongThanSieuCap {

    public static final int MENU_SUMMON = 31720500, MENU_WISH = 31720501, MENU_CONFIRM = 31720502, MENU_WISH2 = 31720503;
    public static final int MUI_KHOAN_SIEU_CAP = 2073, SPL_ANH_KIM = 2074, MAX_WISH_CRIT = 5;
    private static final int NEED = 3;
    private static final long WAIT_MS = 5 * 60_000L, BUFF_MS = 60 * 60_000L;
    private static final String SAY = "Ta là Rồng thần siêu cấp, ta sẽ ban cho ngươi một điều ước, ngươi có 5 phút, hãy chọn đi:\n"
            + "1) 100.000 ngọc xanh.\n"
            + "2) 5 tỷ vàng.\n"
            + "3) Tiềm năng sức mạnh X3 cho bản thân 60 phút.\n"
            + "4) 1 Mũi khoan siêu cấp (gỡ sao pha lê 10 tại Bà Hạt Mít).\n"
            + "5) 10 viên Đá bảo vệ.";
    private static final String SAY2 = "Ta sẽ ban cho ngươi một điều ước, ngươi có 5 phút, hãy chọn đi:\n"
            + "6) Tiềm năng sức mạnh X3 cho đệ tử 60 phút.\n"
            + "7) Tăng 1% chí mạng gốc (tối đa " + MAX_WISH_CRIT + " lần / nhân vật).\n"
            + "8) Ngẫu nhiên 1 ngọc rồng xanh hoặc 1 Sao pha lê ánh kim (vàng rơi 10% - 25%).";

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
        page(pl, 1);
    }

    private static void page(Player pl, int p) {
        if (p == 1) {
            NpcService.gI().createMenuRongThieng(pl, MENU_WISH, SAY, "Điều\nước 1", "Điều\nước 2", "Điều\nước 3", "Điều\nước 4", "Điều\nước 5", "->");
        } else {
            NpcService.gI().createMenuRongThieng(pl, MENU_WISH2, SAY2, "<-", "Điều\nước 6", "Điều\nước 7", "Điều\nước 8");
        }
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
            case MENU_WISH, MENU_WISH2, MENU_CONFIRM -> {
                long[] st = dragons.get(pl.id);
                if (st == null) {
                    return true;
                }
                int chosen = menu == MENU_WISH && select >= 0 && select < 5 ? select + 1
                        : menu == MENU_WISH2 && select >= 1 && select <= 3 ? select + 5 : 0;
                if (System.currentTimeMillis() - st[0] > WAIT_MS) {
                    leave(pl, "Hết giờ rồi, ta đi đây...");
                } else if (menu == MENU_WISH && select == 5) {
                    page(pl, 2);
                } else if (menu == MENU_WISH2 && select == 0) {
                    page(pl, 1);
                } else if (chosen > 0) {
                    st[1] = chosen;
                    NpcService.gI().createMenuRongThieng(pl, MENU_CONFIRM, "Ngươi có chắc muốn ước điều ước " + st[1] + "?", "Điều\nước " + st[1], "Từ chối");
                } else if (menu == MENU_CONFIRM && select == 0 && st[1] > 0) {
                    wish(pl, (int) st[1]);
                } else {
                    page(pl, st[1] > 5 ? 2 : 1);
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
            case 4 ->
                give(pl, ItemService.gI().createNewItem((short) MUI_KHOAN_SIEU_CAP));
            case 5 -> {
                Item da = ItemService.gI().createNewItem((short) 987);
                da.quantity = 10;
                give(pl, da);
            }
            case 6 -> {
                if (pl.pet == null) {
                    Service.gI().sendThongBao(pl, "Ngươi chưa có đệ tử, hãy ước điều khác");
                    return;
                }
                pl.hlwPetX3Until = Math.max(now, pl.hlwPetX3Until) + BUFF_MS;
            }
            case 7 -> {
                if (pl.wishCrit >= MAX_WISH_CRIT) {
                    Service.gI().sendThongBao(pl, "Ngươi đã ước tăng chí mạng đủ " + MAX_WISH_CRIT + " lần, hãy ước điều khác");
                    return;
                }
                pl.wishCrit++;
                pl.nPoint.critg++;
                pl.nPoint.calPoint();
                Service.gI().point(pl);
                nro.models.database.PlayerExtraDAO.save(pl);
            }
            case 8 -> {
                Item it;
                if (Util.isTrue(50, 100)) {
                    it = ItemService.gI().createNewItem((short) Util.nextInt(2064, 2070)); // ngọc rồng xanh: chỉ số tung khi khảm
                } else {
                    it = ItemService.gI().createNewItem((short) SPL_ANH_KIM);
                    it.itemOptions.add(new Item.ItemOption(100, Util.nextInt(10, 25))); // +% vàng từ quái
                }
                give(pl, it);
            }
            default -> {
                return;
            }
        }
        if (wish == 3 || wish == 6) {
            ItemTimeService.gI().sendItemTime(pl, ItemService.gI().getTemplate(ConstItem.NGOC_RONG_SIEU_CAP).iconID, (int) (BUFF_MS / 1000));
        }
        PlayerService.gI().sendInfoHpMpMoney(pl);
        leave(pl, "Điều ước của ngươi đã trở thành sự thật. Hẹn gặp lại!");
    }

    private static void give(Player pl, Item it) {
        InventoryService.gI().addItemBag(pl, it);
        InventoryService.gI().sendItemBags(pl);
        Service.gI().sendThongBao(pl, "Bạn nhận được " + it.template.name);
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
