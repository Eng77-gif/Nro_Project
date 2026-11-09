package nro.models.boss.event.HungVuong;

import nro.models.boss.Boss;
import nro.models.boss.BossID;
import nro.models.boss.BossesData;
import static nro.models.consts.BossType.HUNGVUONG_EVENT;
import nro.models.consts.ConstPlayer;
import nro.models.item.Item.ItemOption;
import nro.models.map.ItemMap;
import nro.models.player.Player;
import nro.models.services.EffectSkillService;
import nro.models.services.Service;
import nro.models.utils.Util;

/**
 * Boss Giỗ tổ "Rồng nhí 1 - 7 sao" (theo source V10): chỉ chạy theo người chơi, không đánh trả; mỗi đòn mất tối đa 25.000 HP.
 * Hạ rơi: 90% 1 - 3 Mảnh trứng rồng nhí, 10% 1 Trứng rồng nhí.
 */
public class RongNhi extends Boss {

    private long st;
    private int timeLeaveMap;

    public RongNhi(int star) throws Exception {
        super(HUNGVUONG_EVENT, BossID.RONG_NHI_1_SAO - (star - 1), true, false, BossesData.RONG_NHI_SAO[star - 1]);
    }

    @Override
    public void reward(Player plKill) {
        boolean trung = !Util.isTrue(90, 100);
        ItemMap it = new ItemMap(this.zone, trung ? 1820 : 1822, trung ? 1 : Util.nextInt(1, 3),
                this.location.x + Util.nextInt(-15, 15), this.zone.map.yPhysicInTop(this.location.x, this.location.y - 24), plKill.id);
        it.options.add(new ItemOption(30, 0));
        Service.gI().dropItemMap(this.zone, it);
    }

    @Override
    public synchronized long injured(Player plAtt, long damage, boolean piercing, boolean isMobAttack) {
        if (this.isDie()) {
            return 0;
        }
        if (!piercing && Util.isTrue(this.nPoint.tlNeDon, 1000)) {
            this.chat("Xí hụt");
            return 0;
        }
        damage = this.nPoint.subDameInjureWithDeff(damage);
        if (!piercing && effectSkill.isShielding) {
            if (damage > nPoint.hpMax) {
                EffectSkillService.gI().breakShield(this);
            }
            damage = 1;
        }
        if (damage > 25000) {
            damage = 25000;
        }
        this.nPoint.subHP(damage);
        if (isDie()) {
            this.setDie(plAtt);
            die(plAtt);
        }
        return damage;
    }

    @Override
    public void joinMap() {
        this.name = this.data[this.currentLevel].getName() + " " + Util.nextInt(1, 100);
        super.joinMap();
        st = System.currentTimeMillis();
        timeLeaveMap = Util.nextInt(600000, 900000);
    }

    @Override
    public void autoLeaveMap() {
        if (Util.canDoWithTime(st, timeLeaveMap)) {
            this.leaveMapNew();
        }
        if (this.zone != null && this.zone.getNumOfPlayers() > 0) {
            st = System.currentTimeMillis();
            timeLeaveMap = Util.nextInt(300000, 900000);
        }
    }

    @Override
    public void attack() {
        // rồng nhí không đánh, chỉ chạy quanh người chơi
        if (Util.canDoWithTime(this.lastTimeAttack, 300) && this.typePk == ConstPlayer.PK_ALL) {
            this.lastTimeAttack = System.currentTimeMillis();
            try {
                Player pl = getPlayerAttack();
                if (pl == null || pl.isDie()) {
                    return;
                }
                int dis = Util.getDistance(this, pl);
                if (dis > 450) {
                    move(pl.location.x - 24, pl.location.y);
                } else if (dis > 100) {
                    int dir = (this.location.x - pl.location.x < 0 ? 1 : -1);
                    move(this.location.x + dir * Util.nextInt(50, 100), pl.location.y);
                }
            } catch (Exception ignored) {
            }
        }
    }
}
