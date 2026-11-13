package nro.models.boss.event.TrungThu;

import nro.models.boss.Boss;
import nro.models.boss.BossID;
import nro.models.boss.BossesData;
import static nro.models.consts.BossType.TRUNGTHU_EVENT;
import nro.models.item.Item.ItemOption;
import nro.models.map.ItemMap;
import nro.models.player.Player;
import nro.models.services.Service;
import nro.models.utils.Util;

/**
 * Boss Đội Hỏa Tiễn "Musashi" (theo source V10, sự kiện Pokémon): đánh mất máu thật như video sự kiện hè 3, rơi thêm Rác (đổi Thùng rác).
 * Hạ rơi: 30% Cải trang Musashi (SĐ +38%, chí mạng +13%, 1 - 5 ngày), không thì 30% Bóng Poke, còn lại vật phẩm 1099 - 1103.
 */
public class Musashi extends Boss {

    private long st;

    public Musashi() throws Exception {
        super(TRUNGTHU_EVENT, BossID.MUSASHI, BossesData.MUSASHI);
    }

    @Override
    public void reward(Player plKill) {
        int x = this.location.x + Util.nextInt(-15, 15);
        int y = this.zone.map.yPhysicInTop(this.location.x, this.location.y - 24);
        ItemMap it;
        if (Util.isTrue(30, 100)) {
            it = new ItemMap(this.zone, 1880, 1, x, y, plKill.id); // Cải trang Musashi (mã v10 1877)
            it.options.add(new ItemOption(50, 38));
            it.options.add(new ItemOption(14, 13));
            it.options.add(new ItemOption(93, Util.nextInt(1, 5)));
            it.options.add(new ItemOption(30, 0));
        } else if (Util.isTrue(20, 100)) { // giải cứu Pikachu (sự kiện hè 3): 50% vĩnh viễn
            it = new ItemMap(this.zone, 1870, 1, x, y, plKill.id); // Pet Pikachu như video: SĐ / HP / KI +13%, tấn công +13% lên boss
            it.options.add(new ItemOption(50, 13));
            it.options.add(new ItemOption(77, 13));
            it.options.add(new ItemOption(103, 13));
            it.options.add(new ItemOption(204, 13));
            if (Util.isTrue(50, 100)) {
                it.options.add(new ItemOption(93, Util.nextInt(3, 7)));
            }
        } else if (Util.isTrue(30, 100)) {
            it = new ItemMap(this.zone, 1892, 1, x, y, plKill.id); // Bóng Poke (mã v10 1873)
            if (Util.isTrue(99, 100)) {
                it.options.add(new ItemOption(93, Util.nextInt(1, 5)));
            }
            it.options.add(new ItemOption(30, 0));
        } else {
            it = new ItemMap(this.zone, Util.nextInt(1099, 1103), 1, x, y, plKill.id);
            it.options.add(new ItemOption(30, 0));
        }
        Service.gI().dropItemMap(this.zone, it);
        if (Util.isTrue(50, 100)) { // Rác (hạn 33 ngày): bấm Sử dụng đổi Thùng rác
            ItemMap r = new ItemMap(this.zone, 1895, Util.nextInt(5, 10), x + 10, y, plKill.id);
            r.options.add(new ItemOption(93, 33));
            r.options.add(new ItemOption(30, 0));
            Service.gI().dropItemMap(this.zone, r);
        }
        nro.models.services.SeasonEventService.gI().addTopPointById(1901, plKill, 1); // top "Bóng Master" (v10: bongmaster++)
    }

    @Override
    public void joinMap() {
        super.joinMap();
        st = System.currentTimeMillis();
    }

    @Override
    public void autoLeaveMap() {
        if (Util.canDoWithTime(st, 900000)) {
            this.leaveMapNew();
        }
        if (this.zone != null && this.zone.getNumOfPlayers() > 0) {
            st = System.currentTimeMillis();
        }
    }
}
