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
     * Mỗi con Baby chỉ rơi đúng 1 món: đồ thần linh (20%), đồ có ô sao pha lê (40%) hoặc sao pha lê cấp 2 (40%).
     */
    @Override
    public void reward(Player plKill) {
        int x = this.location.x;
        int y = this.zone.map.yPhysicInTop(x, this.location.y - 24);
        plKill.bossBabyDefeatParticipationCount++;
        int r = Util.nextInt(1, 100);
        if (r <= 20) {
            ItemMap it = ItemService.gI().randDoTLBoss(this.zone, 1, x, y, plKill.id);
            if (it != null) {
                Service.gI().dropItemMap(zone, it);
                return;
            }
        }
        if (r <= 60) {
            int group = Util.nextInt(1, 100) <= 70 ? 0 : 1; // 70% áo / quần / giày, 30% găng / rada
            int[][] drops = {
                {230, 231, 232, 234, 235, 236, 238, 239, 240, 242, 243, 244, 246, 247, 248, 250, 251, 252, 266, 267, 268, 270, 271, 272, 274, 275, 276},
                {254, 255, 256, 258, 259, 260, 262, 263, 264, 278, 279, 280}
            };
            int id = drops[group][Util.nextInt(0, drops[group].length - 1)];
            ItemMap it = new ItemMap(this.zone, id, 1, x, y, plKill.id);
            List<Item.ItemOption> ops = ItemService.gI().getListOptionItemShop((short) id);
            ops.forEach(o -> o.param = (int) (o.param * Util.nextInt(100, 115) / 100.0));
            it.options.addAll(ops);
            int rand = Util.nextInt(1, 100); // ô sao pha lê: 60% 1 - 3, 30% 4 - 5, 10% 6
            it.options.add(new Item.ItemOption(107, rand <= 60 ? Util.nextInt(1, 3) : rand <= 90 ? Util.nextInt(4, 5) : 6));
            Service.gI().dropItemMap(zone, it);
            return;
        }
        Service.gI().dropItemMap(zone, new ItemMap(this.zone, Util.nextInt(1416, 1422), 1, x, y, plKill.id)); // sao pha lê cấp 2
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
