package nro.models.event;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import nro.models.boss.BossData;
import nro.models.boss.vo_dai_hat_mit.DeathOrAliveArena;
import nro.models.consts.BossStatus;
import static nro.models.consts.BossType.PHOBAN;
import nro.models.consts.ConstPlayer;
import nro.models.item.Item;
import nro.models.map.Zone;
import nro.models.map.service.ChangeMapService;
import nro.models.player.Player;
import nro.models.services.Event2026Service;
import nro.models.services.InventoryService;
import nro.models.services.PlayerService;
import nro.models.services.Service;
import nro.models.skill.Skill;
import nro.models.utils.Util;

/**
 * Map Thành Cổ (sự kiện Giỗ tổ Hùng Vương): dùng Lệnh bài thành cổ nhờ Hùng Vương đưa đến Thành cổ 1,
 * đi cổng sang Thành cổ 2 rồi Đấu trường thành cổ; Hùng Vương ở Đấu trường đưa lên đài đấu với Gohan chiến binh
 * (máu bằng máu người chơi) hoặc đưa về làng.
 * Thắng nhận Hộp quà thường Giỗ tổ.
 */
public class ThanhCo {

    public static final int LENH_BAI = 2018;
    public static final int REWARD_BOX = 1823;
    public static final int GOHAN_ID = -9501000;
    /** Đài đấu giữa Đấu trường thành cổ (hàng ô gạch y=456, x 792 - 1080): bấm Lên sàn đấu là bay thẳng lên đây. */
    private static final int DAI_Y = 456, DAI_X_PLAYER = 880, DAI_X_GOHAN = 1000;
    private static final Map<Zone, GohanChienBinh> fighting = new ConcurrentHashMap<>();

    private static final BossData GOHAN = new BossData(
            "Gohan chiến binh",
            ConstPlayer.TRAI_DAT,
            new short[]{1962, 1963, 1964, -1, -1, -1},
            1000,
            new long[]{1000},
            new int[]{VuaHung.MAP_DAU_TRUONG},
            new int[][]{
                {Skill.DRAGON, 7, 1000},
                {Skill.MASENKO, 7, 1500},
                {Skill.KAMEJOKO, 7, 3000}},
            new String[]{},
            new String[]{"|-1|Mù mắt chưa!", "|-1|Ngươi không qua được ta đâu"},
            new String[]{},
            1); // nghỉ 1 giây

    public static void enter(Player pl) {
        Item lb = InventoryService.gI().findItemBag(pl, LENH_BAI);
        if (lb == null) {
            Service.gI().sendThongBao(pl, "Cần 1 Lệnh bài thành cổ (mua ở Chi Chi) để đến cổ thành");
            return;
        }
        InventoryService.gI().subQuantityItemsBag(pl, lb, 1);
        InventoryService.gI().sendItemBags(pl);
        ChangeMapService.gI().changeMapBySpaceShip(pl, VuaHung.MAP_THANH_CO_1, -1, 100);
    }

    public static void home(Player pl) {
        ChangeMapService.gI().changeMapBySpaceShip(pl, 21 + pl.gender, -1, 250);
    }

    public static void fight(Player pl) {
        if (pl.zone == null || pl.zone.map.mapId != VuaHung.MAP_DAU_TRUONG) {
            return;
        }
        GohanChienBinh cur = fighting.get(pl.zone);
        if (cur != null && cur.zone != null && !cur.isDie()) {
            Service.gI().sendThongBao(pl, "Sàn đấu đang có người thi đấu, hãy chờ hoặc sang khu khác");
            return;
        }
        try {
            fighting.put(pl.zone, new GohanChienBinh(pl));
            Service.gI().resetPoint(pl, DAI_X_PLAYER, DAI_Y);
            PlayerService.gI().playerMove(pl, DAI_X_PLAYER, DAI_Y);
            Service.gI().sendThongBao(pl, "Gohan chiến binh đã lên sàn!");
        } catch (Exception e) {
            Service.gI().sendThongBao(pl, "Không gọi được đối thủ, thử lại sau");
        }
    }

    /** Khu đài (tường vô hình): người ngoài không vào được, người đang đấu + Gohan không ra được. */
    private static final int WALL_L = 744, WALL_R = 1128, WALL_TOP = DAI_Y + 96, IN_L = 804, IN_R = 1068;

    private static boolean inBox(int x, int y) {
        return x >= WALL_L && x <= WALL_R && y <= WALL_TOP;
    }

    /**
     * Tường vô hình quanh đài Đấu trường thành cổ, gọi từ PlayerService.playerMove.
     * Trả null nếu đi bình thường, ngược lại là toạ độ đã chặn {x, y}.
     */
    public static int[] wall(Player pl, int x, int y) {
        if (pl.zone == null || pl.zone.map.mapId != VuaHung.MAP_DAU_TRUONG) {
            return null;
        }
        GohanChienBinh cur = fighting.get(pl.zone);
        boolean active = cur != null && cur.zone != null && !cur.isDie();
        Player owner = pl.isPet && pl instanceof nro.models.player.Pet p ? p.master : pl;
        if (active && (pl == cur || owner == cur.fighter())) { // trong đài: không nhảy / rơi khỏi đài
            int nx = Math.max(IN_L, Math.min(IN_R, x)), ny = Math.min(DAI_Y, y);
            return nx == x && ny == y ? null : new int[]{nx, ny};
        }
        if (pl.isBoss || !inBox(x, y) || inBox(pl.location.x, pl.location.y)) {
            return null; // đang ở trên đài (vd vừa đấu xong) thì được tự do đi xuống
        }
        if (pl.location.y > WALL_TOP && pl.location.x >= WALL_L && pl.location.x <= WALL_R) {
            return new int[]{x, WALL_TOP + 1}; // bay từ dưới lên: chạm trần vô hình
        }
        return new int[]{pl.location.x < WALL_L ? WALL_L - 1 : WALL_R + 1, y}; // bay ngang vào: chạm tường
    }

    public static class GohanChienBinh extends DeathOrAliveArena {

        Player fighter() {
            return playerAtt;
        }

        private final Zone arena;

        public GohanChienBinh(Player pl) throws Exception {
            super(PHOBAN, GOHAN_ID - Util.nextInt(1000000), GOHAN);
            this.playerAtt = pl;
            this.arena = pl.zone;
        }

        @Override
        public void joinMap() {
            if (playerAtt.zone == null || playerAtt.zone != arena) {
                return;
            }
            this.zone = arena;
            this.nPoint.hpMax = Math.max(1000, playerAtt.nPoint.hpMax);
            this.nPoint.hp = this.nPoint.hpMax;
            this.nPoint.dame = Math.max(1, playerAtt.nPoint.hpMax / 25);
            ChangeMapService.gI().changeMap(this, this.zone, DAI_X_GOHAN, DAI_Y);
            PlayerService.gI().changeAndSendTypePK(playerAtt, ConstPlayer.PK_PVP);
        }

        @Override
        public void afk() {
            if (playerAtt.zone == arena) {
                this.changeStatus(BossStatus.ACTIVE);
            } else {
                end();
            }
        }

        @Override
        public synchronized long injured(Player plAtt, long damage, boolean piercing, boolean isMobAttack) {
            if (plAtt != playerAtt) {
                return 0; // chỉ người lên sàn được đánh
            }
            return super.injured(plAtt, damage, piercing, isMobAttack);
        }

        @Override
        public void attack() {
            if (playerAtt.isDie() || playerAtt.zone != arena) {
                this.chat("Về tập luyện thêm đi!");
                end();
                return;
            }
            super.attack();
        }

        @Override
        public void die(Player plKill) {
            this.changeStatus(BossStatus.DIE);
            Player pl = playerAtt;
            if (pl != null && pl.zone == arena) {
                this.chat("Chúc mừng bạn đã hoàn thành thử thách");
                if (InventoryService.gI().getCountEmptyBag(pl) > 0) {
                    InventoryService.gI().addItemBag(pl, Event2026Service.gI().createItem(REWARD_BOX, 1, 0));
                    InventoryService.gI().sendItemBags(pl);
                    Service.gI().sendThongBao(pl, "Hoàn thành thử thách thành cổ, nhận 1 Hộp quà thường Giỗ tổ");
                } else {
                    Service.gI().sendThongBao(pl, "Hành trang đầy, không nhận được quà thành cổ");
                }
            }
            end();
        }

        private void end() {
            fighting.remove(arena, this);
            if (playerAtt != null && playerAtt.zone != null) {
                PlayerService.gI().changeAndSendTypePK(playerAtt, ConstPlayer.NON_PK);
            }
            this.leaveMap();
        }
    }
}
