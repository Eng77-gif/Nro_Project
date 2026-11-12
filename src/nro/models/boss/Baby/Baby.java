package nro.models.boss.Baby;

import java.util.List;
import nro.models.consts.ConstPlayer;
import nro.models.boss.Boss;
import nro.models.boss.BossesData;
import nro.models.boss.BossID;
import nro.models.item.Item;
import nro.models.map.ItemMap;
import nro.models.player.Player;
import nro.models.services.EffectSkillService;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.services.SkillService;
import nro.models.utils.Util;

public class Baby extends Boss {

    public Baby() throws Exception {
        super(BossID.BABY, BossesData.BABY, BossesData.BABY_2, BossesData.BABY_3);
    }

    /**
     * Mỗi con Baby rơi đúng 1 phần thưởng: 50% vật phẩm cấp 2 (Cuồng nộ, Giáp Xên bọ hung, Bổ huyết, Ẩn danh 2) x5 - 10,
     * 20% ngọc rồng 1 - 7 sao, 20% đồ thần linh có 1 - 5 ô sao pha lê, 10% đồ set Thần hủy diệt Champa.
     */
    @Override
    public void reward(Player plKill) {
        int x = this.location.x;
        int y = this.zone.map.yPhysicInTop(x, this.location.y - 24);
        plKill.bossBabyDefeatParticipationCount++;
        int r = Util.nextInt(1, 100);
        if (r <= 50) {
            int[] lv2 = {1150, 1153, 1152, 1154};
            Service.gI().dropItemMap(zone, new ItemMap(this.zone, lv2[Util.nextInt(0, lv2.length - 1)], Util.nextInt(5, 10), x, y, plKill.id));
        } else if (r <= 70) {
            Service.gI().dropItemMap(zone, new ItemMap(this.zone, Util.nextInt(14, 20), 1, x, y, plKill.id)); // ngọc rồng 1 - 7 sao
        } else if (r <= 90) {
            ItemMap it = ItemService.gI().randDoTLBoss(this.zone, 1, x, y, plKill.id);
            if (it != null) {
                it.options.removeIf(o -> o.optionTemplate != null && o.optionTemplate.id == 107);
                it.options.add(new Item.ItemOption(107, Util.nextInt(1, 5)));
                Service.gI().dropItemMap(zone, it);
            }
        } else {
            int gear = nro.models.services.Event2026Service.randomSkhChampaGear();
            ItemMap champa = new ItemMap(this.zone, gear, 1, x, y, plKill.id);
            champa.options.addAll(ItemService.gI().getListOptionItemShop((short) gear));
            nro.models.services.Event2026Service.addSkhChampaOptions(champa.options);
            Service.gI().dropItemMap(zone, champa);
        }
    }

    @Override
    public void active() {
        if (this.typePk == ConstPlayer.NON_PK) {
            this.changeToTypePK();
        }
        this.attack();
    }

    @Override
    public synchronized long injured(Player plAtt, long damage, boolean piercing, boolean isMobAttack
    ) {
        if (!this.isDie()) {
            if (!piercing && Util.isTrue(this.nPoint.tlNeDon, 1000)) {
                this.chat("Xí hụt");
                return 0;
            }

            damage = (long) (damage * 0.7);

            damage = this.nPoint.subDameInjureWithDeff(damage / 2);

            if (!piercing && effectSkill.isShielding) {
                if (damage > nPoint.hpMax) {
                    EffectSkillService.gI().breakShield(this);
                }
                damage = damage / 4;
            }

            this.nPoint.subHP(damage);

            if (isDie()) {
                this.setDie(plAtt);
                die(plAtt);
            }
            return damage;
        } else {
            return 0;
        }
    }

}
