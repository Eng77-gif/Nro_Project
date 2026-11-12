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
 * Boss Trung Thu "Meowth" (theo source V10): mỗi đòn chỉ mất 1 HP.
 * Hạ rơi: 30% Ván bay cá đuối VIP (1 - 5 ngày), không thì 90% Pet rồng Pikachu thanh long (SĐ/HP/KI +22%, 95% có hạn 1 - 5 ngày),
 * còn lại 1 vật phẩm ngẫu nhiên 1099 - 1103.
 */
public class Meowth extends Boss {

    private long st;

    public Meowth() throws Exception {
        super(TRUNGTHU_EVENT, BossID.MEOWTH, BossesData.MEOWTH);
    }

    @Override
    public synchronized long injured(Player plAtt, long damage, boolean piercing, boolean isMobAttack) {
        return super.injured(plAtt, 1, piercing, isMobAttack);
    }

    @Override
    public void reward(Player plKill) {
        int x = this.location.x + Util.nextInt(-15, 15);
        int y = this.zone.map.yPhysicInTop(this.location.x, this.location.y - 24);
        ItemMap it;
        if (Util.isTrue(30, 100)) {
            it = new ItemMap(this.zone, 1881, 1, x, y, plKill.id); // Pet Meowth (mã v10 1878, ở server này 1878 là Ván bay cá đuối VIP)
            it.options.add(new ItemOption(93, Util.nextInt(1, 5)));
        } else if (Util.isTrue(90, 100)) {
            it = new ItemMap(this.zone, 1482, 1, x, y, plKill.id);
            it.options.add(new ItemOption(50, 22));
            it.options.add(new ItemOption(77, 22));
            it.options.add(new ItemOption(103, 22));
            if (Util.isTrue(95, 100)) {
                it.options.add(new ItemOption(93, Util.nextInt(1, 5)));
            }
        } else {
            it = new ItemMap(this.zone, Util.nextInt(1099, 1103), 1, x, y, plKill.id);
        }
        it.options.add(new ItemOption(30, 0));
        Service.gI().dropItemMap(this.zone, it);
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
