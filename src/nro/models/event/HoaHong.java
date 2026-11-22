package nro.models.event;

import nro.models.item.Item;
import nro.models.npc.Npc;
import nro.models.player.Player;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.utils.Util;

/**
 * Cây hoa hồng sự kiện Nhà giáo Việt Nam 20/11 (theo source V10 "HaiHoaHong").
 * Dùng Kéo tỉa hoa (1387): chờ 3 giây, có thể hụt (mất kéo), được 1 Đóa hoa hồng (1395); Kéo tỉa hoa đặc biệt (1382) không hụt, được 1 Bó hoa hồng (1388).
 * Mỗi cây hái được 7 lần rồi tàn, 5 phút sau nở lại. Cây do SeasonEventService đặt khi sự kiện 20/11 đang chạy.
 */
public class HoaHong extends Npc {

    public static final int KEO = 1387, KEO_DAC_BIET = 1382, BONG_HOA = 1395, BO_HOA = 1388; // kéo thường: 1 Đóa hoa hồng (có thể hụt), kéo đặc biệt: 1 Bó hoa hồng
    private static final int TI_LE_HUT = 30; // % tỉa hụt bằng kéo thường (mất kéo); shortcut: tỉ lệ tự đặt, video chỉ nói "tỉa hụt sẽ mất kéo"
    private final java.util.Set<Long> dangTia = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final int LUOT_HAI = 7;
    private static final long TAN_MS = 5 * 60_000L;

    private int count;
    private long tanDen;

    public HoaHong(int mapId, int status, int cx, int cy, int tempId, int avatar) {
        super(mapId, status, cx, cy, tempId, avatar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (!canOpenNpc(player)) {
            return;
        }
        long con = tanDen - System.currentTimeMillis();
        if (con > 0) {
            createOtherMenu(player, 0, "Hoa vừa bị hái hết, " + (con / 1000 + 1) + " giây nữa sẽ nở lại.", "Đóng");
            return;
        }
        createOtherMenu(player, 0, "Muốn tỉa mình, xem trình bạn thế nào?", "Đồng ý", "Từ chối");
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (canOpenNpc(player) && player.idMark.getIndexMenu() == 0 && select == 0 && tanDen <= System.currentTimeMillis()) {
            hai(player);
        }
    }

    private synchronized void hai(Player player) {
        if (tanDen > System.currentTimeMillis()) {
            Service.gI().sendThongBao(player, "Hoa đã bị hái hết, chờ nở lại nhé");
            return;
        }
        Item keo = InventoryService.gI().findItemBag(player, KEO);
        Item keoDb = InventoryService.gI().findItemBag(player, KEO_DAC_BIET);
        boolean dacBiet = keoDb != null && keoDb.quantity > 0;
        if (!dacBiet && (keo == null || keo.quantity <= 0)) {
            Service.gI().sendThongBao(player, "Bạn cần có Kéo tỉa hoa hoặc Kéo tỉa hoa đặc biệt để hái hoa!");
            return;
        }
        if (InventoryService.gI().getCountEmptyBag(player) < 1) {
            Service.gI().sendThongBaoOK(player, "Cần ít nhất 1 ô trống trong hành trang!");
            return;
        }
        if (!dangTia.add(player.id)) {
            return; // đang tỉa
        }
        InventoryService.gI().subQuantityItemsBag(player, dacBiet ? keoDb : keo, 1); // mất kéo dù tỉa được hay hụt
        InventoryService.gI().sendItemBags(player);
        Service.gI().sendThongBao(player, "Đang tỉa hoa, chờ chút...");
        // tỉa hoa phải chờ vài giây (như bản chính thức)
        java.util.concurrent.CompletableFuture.delayedExecutor(3, java.util.concurrent.TimeUnit.SECONDS).execute(() -> {
            try {
                if (player.zone == null || player.zone.map == null || player.zone.map.mapId != this.mapId) {
                    return;
                }
                if (!dacBiet && Util.isTrue(TI_LE_HUT, 100)) {
                    npcChat(player, "Tỉa hụt rồi, mất kéo mà chẳng được bông nào!");
                    return;
                }
                synchronized (this) {
                    Item hoa = ItemService.gI().createNewItem((short) (dacBiet ? BO_HOA : BONG_HOA), 1);
                    hoa.itemOptions.add(new Item.ItemOption(30, 0));
                    hoa.itemOptions.add(new Item.ItemOption(93, 35));
                    InventoryService.gI().addItemBag(player, hoa);
                    InventoryService.gI().sendItemBags(player);
                    npcChat(player, "Bạn nhận được " + (dacBiet ? "Bó hoa hồng" : "1 Đóa hoa hồng"));
                    if (++count >= LUOT_HAI) {
                        count = 0;
                        tanDen = System.currentTimeMillis() + TAN_MS;
                    }
                }
            } catch (Exception e) {
                nro.models.utils.Logger.logException(HoaHong.class, e);
            } finally {
                dangTia.remove(player.id);
            }
        });
    }
}
