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
 * Mỗi cây chỉ tỉa được 1 lần (trúng hay hụt): hiệu ứng tỉa (241) hiện trên cây, xong cây biến mất và mọc lại ngẫu nhiên ở một map khác.
 * Cây do SeasonEventService đặt khi sự kiện 20/11 đang chạy.
 */
public class HoaHong extends Npc {

    public static final int KEO = 1387, KEO_DAC_BIET = 1382, BONG_HOA = 1395, BO_HOA = 1388; // kéo thường: 1 Đóa hoa hồng (có thể hụt), kéo đặc biệt: 1 Bó hoa hồng
    private static final int TI_LE_HUT = 30; // % tỉa hụt bằng kéo thường (mất kéo); shortcut: tỉ lệ tự đặt, video chỉ nói "tỉa hụt sẽ mất kéo"
    private final java.util.Set<Long> dangTia = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile boolean dangCat; // cây đang có người tỉa (mỗi cây 1 lần)

    public HoaHong(int mapId, int status, int cx, int cy, int tempId, int avatar) {
        super(mapId, status, cx, cy, tempId, avatar);
    }

    @Override
    public void openBaseMenu(Player player) {
        if (!canOpenNpc(player)) {
            return;
        }
        if (dangCat) {
            createOtherMenu(player, 0, "Hoa đang được người khác tỉa rồi.", "Đóng");
            return;
        }
        createOtherMenu(player, 0, "Muốn tỉa mình, xem trình bạn thế nào?", "Đồng ý", "Từ chối");
    }

    @Override
    public void confirmMenu(Player player, int select) {
        if (canOpenNpc(player) && player.idMark.getIndexMenu() == 0 && select == 0) {
            hai(player);
        }
    }

    private synchronized void hai(Player player) {
        if (dangCat) {
            Service.gI().sendThongBao(player, "Hoa đang được người khác tỉa rồi");
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
        dangCat = true;
        InventoryService.gI().subQuantityItemsBag(player, dacBiet ? keoDb : keo, 1); // mất kéo dù tỉa được hay hụt
        InventoryService.gI().sendItemBags(player);
        // tỉa hoa phải chờ vài giây (như bản chính thức)
        java.util.concurrent.CompletableFuture.delayedExecutor(3, java.util.concurrent.TimeUnit.SECONDS).execute(() -> {
            try {
                if (player.zone == null || player.zone.map == null || player.zone.map.mapId != this.mapId) {
                    return;
                }
                if (!dacBiet && Util.isTrue(TI_LE_HUT, 100)) {
                    // tỉa hụt: vẫn hiện hiệu ứng tỉa (241) nhưng không ra hoa
                    tiaFx(player);
                    npcChat(player, "Tỉa hụt rồi, mất kéo mà chẳng được bông nào!");
                    return;
                }
                synchronized (this) {
                    Item hoa = ItemService.gI().createNewItem((short) (dacBiet ? BO_HOA : BONG_HOA), 1);
                    hoa.itemOptions.add(new Item.ItemOption(30, 0));
                    hoa.itemOptions.add(new Item.ItemOption(93, 35));
                    InventoryService.gI().addItemBag(player, hoa);
                    InventoryService.gI().sendItemBags(player);
                    // hiệu ứng tỉa hoa như video / bản v10 (HaiHoaHong.activeHoahong): effect 241 = mây hoa hồng + kéo, xong hiện bó hoa trên người
                    tiaFx(player);
                    Service.gI().chat(player, "Bạn nhận được " + (dacBiet ? "Bó hoa hồng" : "Đóa hoa hồng"));
                }
            } catch (Exception e) {
                nro.models.utils.Logger.logException(HoaHong.class, e);
            } finally {
                dangTia.remove(player.id);
                // tỉa xong (trúng hay hụt): cây biến mất sau hiệu ứng rồi mọc ở map khác
                doiCho(); // như video: tỉa xong cây biến mất ngay
            }
        });
    }

    /** Hiệu ứng tỉa hoa như video: effect 241 (mây hoa hồng + kéo, bó hoa) gắn trên người tỉa, đi theo người ~3,5 giây. */
    private static void tiaFx(Player player) {
        Service.gI().LogicEffect(player, 241, 1, 1, 0, 1, 3500);
    }

    /** Ẩn cây ở map cũ và đặt sang 1 map thường ngẫu nhiên (cùng loại map Dr. Myuu được đứng), map đó chưa có cây hoa. */
    public synchronized void doiCho() {
        try {
            nro.models.map.Map old = this.map;
            java.util.List<nro.models.map.Map> maps = new java.util.ArrayList<>();
            for (nro.models.map.Map m : nro.models.server.Manager.MAPS) {
                if (m != old && nro.models.services.DrMyuuService.allowed(m) && m.npcs.stream().noneMatch(n -> n.tempId == this.tempId)) {
                    maps.add(m);
                }
            }
            for (int t = 0; t < 10 && !maps.isEmpty(); t++) {
                nro.models.map.Map m = maps.get(Util.nextInt(0, maps.size() - 1));
                int x = -1, y = 0;
                for (int k = 0; k < 40 && y <= 0; k++) { // chỗ có mặt đất
                    x = Util.nextInt(Math.min(150, m.mapWidth / 3), Math.max(160, m.mapWidth - 150));
                    y = m.yPhysicInTop(x, 24);
                }
                if (y <= 0) {
                    continue;
                }
                if (old != null) {
                    old.npcs.remove(this);
                    for (nro.models.map.Zone z : old.zones) {
                        for (Player p : new java.util.ArrayList<>(z.players)) {
                            Service.gI().sendHideNpc(p, this.tempId, true);
                        }
                    }
                }
                this.mapId = m.mapId;
                this.cx = x;
                this.cy = y;
                this.map = m;
                m.npcs.add(this);
                nro.models.utils.Logger.success("Hoa hồng mọc ở " + m.mapName + " [" + m.mapId + "]\n");
                return;
            }
        } catch (Exception e) {
            nro.models.utils.Logger.logException(HoaHong.class, e);
        } finally {
            dangCat = false;
        }
    }
}
