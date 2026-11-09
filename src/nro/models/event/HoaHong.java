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
 * Dùng Kéo tỉa hoa (1387) hái được 1 - 5 Bông hoa hồng (1530); Kéo tỉa hoa đặc biệt (1382) hái được 1 - 3 Bó hoa hồng (1388).
 * Mỗi cây hái được 7 lần rồi tàn, 5 phút sau nở lại. Cây do SeasonEventService đặt khi sự kiện 20/11 đang chạy.
 */
public class HoaHong extends Npc {

    public static final int KEO = 1387, KEO_DAC_BIET = 1382, BONG_HOA = 1530, BO_HOA = 1388;
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
        createOtherMenu(player, 0, "Muốn tỉa mình, xem trình bạn thế nào?\n(Cần Kéo tỉa hoa hoặc Kéo tỉa hoa đặc biệt)",
                "Đồng ý", "Từ chối");
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
        Item hoa = ItemService.gI().createNewItem((short) (dacBiet ? BO_HOA : BONG_HOA), dacBiet ? Util.nextInt(1, 3) : Util.nextInt(1, 5));
        InventoryService.gI().subQuantityItemsBag(player, dacBiet ? keoDb : keo, 1);
        InventoryService.gI().addItemBag(player, hoa);
        InventoryService.gI().sendItemBags(player);
        Service.gI().sendThongBao(player, "Bạn nhận được " + hoa.quantity + " " + hoa.template.name);
        if (++count >= LUOT_HAI) {
            count = 0;
            tanDen = System.currentTimeMillis() + TAN_MS;
            npcChat(player, "Hoa đã bị hái hết, 5 phút nữa ta sẽ nở lại!");
        }
    }
}
