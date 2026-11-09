package nro.models.services;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import nro.models.consts.ConstNpc;
import nro.models.map.Map;
import nro.models.map.service.MapService;
import nro.models.npc.Npc;
import nro.models.npc.NpcFactory;
import nro.models.utils.Logger;
import nro.models.utils.Util;

/**
 * Dr. Myuu đi lang thang: cứ 30 - 45 phút xuất hiện ở 1 bản đồ ngẫu nhiên (trừ Tương lai, hành tinh Cold, hành tinh ngục tù,
 * hành tinh thực vật và các map đặc biệt / phó bản / nhà), báo trên kênh thế giới. Nói chuyện với Dr. Myuu để vào
 * Phòng thí nghiệm Myuu (map 166) đánh Baby; trong phòng thí nghiệm cũng có Dr. Myuu để về nhà (map không có lối ra).
 *
 * @author Việt
 */
public final class DrMyuuService {

    public static final int LAB = 166;
    private static final DrMyuuService I = new DrMyuuService();
    private Npc wander;
    private ScheduledExecutorService exec;

    public static DrMyuuService gI() {
        return I;
    }

    public synchronized void start() {
        if (exec != null) {
            return;
        }
        ensureLabNpc();
        exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DrMyuu");
            t.setDaemon(true);
            return t;
        });
        exec.schedule(this::moveLoop, 90, TimeUnit.SECONDS);
    }

    private void moveLoop() {
        try {
            move();
        } catch (Throwable t) {
            Logger.error("Dr. Myuu: " + t + "\n");
        }
        exec.schedule(this::moveLoop, Util.nextInt(30, 45), TimeUnit.MINUTES);
    }

    /** Map Dr. Myuu được xuất hiện: map thường của 3 hành tinh. */
    public static boolean allowed(Map m) {
        if (m == null || m.tileMap == null || m.zones == null || m.zones.isEmpty()) {
            return false;
        }
        int id = m.mapId;
        MapService ms = MapService.gI();
        return id >= 0 && id <= 163 && !ms.isHome(id) && !ms.isMapOffline(id) && !ms.isMapTuongLai(m) && !ms.isMapTuongLai(id)
                && !ms.isMapCold(m) && !ms.isMapNgucTu(id) && !ms.isMapHanhTinhThucVat(id) && !ms.isMapBlackBallWar(id)
                && !ms.isMapMaBu(id) && !ms.isMapYardart(id) && !ms.isMapCadic(id) && !ms.isMapBossFinal(id) && !ms.isMapPhoBan(id)
                && !ms.isMapSieuThanhThuy(id) && !ms.isMapRiengTu(id) && !ms.isMapHuyDiet(id) && !ms.isMapNguHanhSon(id)
                && !ms.isMapLuyenTap(id) && !ms.isMapMabu2H(id) && !ms.isMapUpPorata(id) && !SPECIAL.contains(id)
                && m.zones.get(0).mobs.stream().anyMatch(x -> x.tempId != nro.models.consts.ConstMob.MAY_DO_SUC_MANH); // map có quái thường
    }

    /** Map đặc biệt không cho Dr. Myuu đứng: trạm tàu, Vách núi (máy đo sức mạnh, Bà Hạt Mít), Đại hội võ thuật, các võ đài,
     *  đấu trường, doanh trại, thánh địa, Siêu thị, lãnh địa bang. */
    private static final java.util.Set<Integer> SPECIAL = java.util.Set.of(24, 25, 26, 39, 40, 41, 42, 43, 44, 45, 46, 49, 50, 51, 52,
            53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 84, 103, 104, 112, 113, 115, 116, 120, 129, 145, 153, 156, 157, 158, 159);


    public synchronized void move() {
        if (wander != null) {
            remove(wander);
            wander = null;
        }
        List<Map> maps = new ArrayList<>();
        for (Map m : nro.models.server.Manager.MAPS) {
            if (allowed(m)) {
                maps.add(m);
            }
        }
        if (maps.isEmpty()) {
            return;
        }
        Map m = maps.get(Util.nextInt(0, maps.size() - 1));
        Npc npc = place(m);
        if (npc != null) {
            wander = npc;
            nro.models.server.ServerNotify.gI().notify("Dr. Myuu vừa xuất hiện tại " + m.mapName + ", hãy giúp ông ấy chế ngự Baby!");
        }
    }

    public String where() {
        Npc n = wander;
        return n != null && n.map != null ? n.map.mapName : null;
    }

    private void ensureLabNpc() {
        Map lab = MapService.gI().getMapById(LAB);
        if (lab != null && lab.npcs.stream().noneMatch(n -> n.tempId == ConstNpc.DR_MYUU)) {
            place(lab);
        }
    }

    private static Npc place(Map m) {
        try {
            int x = -1, y = 0;
            for (int t = 0; t < 40 && y <= 0; t++) { // chọn chỗ có mặt đất (cột không có nền thì y = 0)
                x = Util.nextInt(Math.min(150, m.mapWidth / 3), Math.max(160, m.mapWidth - 150));
                y = m.yPhysicInTop(x, 24);
            }
            if (y <= 0) {
                return null;
            }
            Npc npc = NpcFactory.createNPC(m.mapId, 1, x, y, ConstNpc.DR_MYUU);
            if (npc != null) {
                npc.map = m;
                m.npcs.add(npc);
                Logger.success("Dr. Myuu ở " + m.mapName + " [" + m.mapId + "] x " + x + " y " + y + "\n");
            }
            return npc;
        } catch (Exception e) {
            Logger.error("Không đặt được Dr. Myuu ở " + m.mapId + ": " + e.getMessage() + "\n");
            return null;
        }
    }

    private static void remove(Npc npc) {
        if (npc.map != null) {
            npc.map.npcs.remove(npc);
        }
        nro.models.server.Manager.NPCS.remove(npc);
    }
}
