package nro.models.mob_bigboss;

import nro.models.item.Item.ItemOption;
import nro.models.map.ItemMap;
import nro.models.mob.BigBoss;
import nro.models.mob.Mob;
import nro.models.network.Message;
import nro.models.player.Player;
import nro.models.services.ItemService;
import nro.models.services.Service;
import nro.models.utils.Util;

public class Hirudegarn extends BigBoss {

    private int errors;

    public Hirudegarn(Mob mob) {
        super(mob);
    }

    /** Người đã đánh trong trận (nhận Trứng Mabư / Bình hút năng lượng theo điều kiện). */
    private final java.util.Set<Long> attackers = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Override
    public void injured(Player plAtt, long damage, boolean dieWhenHpFull) {
        if (plAtt != null && plAtt.isPl()) {
            attackers.add(plAtt.id);
        }
        damage = this.point.hp / 100 > 0 ? this.point.hp / 100 : 1;
        super.injured(plAtt, damage, false);
    }

    @Override
    public void update() {
        super.update();
        if (isDie() && (System.currentTimeMillis() - lastTimeDie) > 600000 && lvMob == 3) {
            lvMob = 0;
            action = 0;
            this.location.x = Util.nextInt(100, 900);
            this.location.y = 360;
            this.point.hp = this.point.getHpFull();
            Service.gI().sendBigBoss2(this.zone, action, this);
            Message msg = null;
            try {
                msg = new Message(-9);
                msg.writer().writeByte(this.id);
                msg.writeNum(this.point.gethp());
                msg.writeNum(1);
                Service.gI().sendMessAllPlayerInMap(this.zone, msg);
            } catch (Exception e) {
            } finally {
                if (msg != null) {
                    msg.cleanup();
                }
            }
        } else if (isDie() && (System.currentTimeMillis() - lastTimeDie) > 5000 && lvMob <= 2) {
            switch (lvMob) {
                case 0 -> {
                    lvMob = 1;
                    action = 6;
                    this.point.hp = this.point.getHpFull();
                }
                case 1 -> {
                    lvMob = 2;
                    action = 5;
                    this.point.hp = this.point.getHpFull();
                }
                case 2 -> {
                    lvMob = 3;
                    action = 9;
                }
                default -> {
                }
            }

            int trai = 0;
            int phai = 1;
            int next = 0;
            for (int i = 0; i < 30; i++) {
                int X = next == 0 ? -5 * trai : 5 * phai;
                if (next == 0) {
                    trai++;
                } else {
                    phai++;
                }
                next = next == 0 ? 1 : 0;
                if (trai > 10) {
                    trai = 0;
                }
                if (phai > 10) {
                    phai = 1;
                }
                Service.gI().dropItemMap(
                        this.zone,
                        new ItemMap(zone, 190, 32000, this.location.x + X, this.location.y, -1)
                );
            }
            // Đệ tử 2: đệ đạt 40 tỷ -> có tỉ lệ rơi Trứng Mabư; trứng nở xong (về 0:00) -> có tỉ lệ rơi Bình hút năng lượng
            for (Player pl : new java.util.ArrayList<>(this.zone.getPlayers())) {
                if (pl == null || !attackers.contains(pl.id) || !pl.isPl()) {
                    continue;
                }
                int x = this.location.x + Util.nextInt(-40, 40);
                int y = this.zone.map.yPhysicInTop(x, this.location.y - 24);
                boolean coTrung = pl.mabuEgg != null || nro.models.services.InventoryService.gI().findItemBag(pl, 568) != null;
                if (!coTrung && pl.pet != null && pl.pet.nPoint.power >= 40_000_000_000L && Util.isTrue(20, 100)) {
                    Service.gI().dropItemMap(this.zone, new ItemMap(this.zone, 568, 1, x, y, pl.id));
                    Service.gI().sendThongBao(pl, "Hirudegarn rơi Quả trứng Mabư cho bạn!");
                } else if (((pl.mabuEgg != null && pl.mabuEgg.getSecondDone() <= 0) || (pl.pet != null && pl.pet.typePet == 1))
                        && nro.models.services.InventoryService.gI().findItemBag(pl, 1852) == null && Util.isTrue(20, 100)) {
                    ItemMap it = new ItemMap(this.zone, 1852, 1, x, y, pl.id);
                    it.options.add(new ItemOption(250, 0));
                    Service.gI().dropItemMap(this.zone, it);
                    Service.gI().sendThongBao(pl, "Hirudegarn rơi Bình hút năng lượng cho bạn! Tích đủ 3000 Kilis ở Sa mạc hoang vu rồi dùng để đổi đệ tử 2");
                }
            }
            attackers.clear();

            if (Util.isTrue(1, 40)) {
                short[] possibleIds = {555, 557, 559};
                short idItem = possibleIds[Util.nextInt(possibleIds.length)];
                ItemMap it = new ItemMap(this.zone, idItem, 1, this.location.x,
                        this.zone.map.yPhysicInTop(this.location.x, this.location.y - 24), -1);
                ItemMap itemWithOptions = ItemService.gI().randDoTL(this.zone, 1, this.location.x,
                        this.zone.map.yPhysicInTop(this.location.x, this.location.y - 24), -1);
                it.options.clear();
                it.options.addAll(nro.models.item.Item.ItemOption.copyAll(itemWithOptions.options));
                Service.gI().dropItemMap(this.zone, it);

            }
            Service.gI().sendBigBoss2(this.zone, action, this);
            if (lvMob <= 2) {
                Message msg = null;
                try {
                    msg = new Message(-9);
                    msg.writer().writeByte(this.id);
                    msg.writeNum(this.point.gethp());
                    msg.writeNum(1);
                    Service.gI().sendMessAllPlayerInMap(this.zone, msg);
                } catch (Exception e) {
                } finally {
                    if (msg != null) {
                        msg.cleanup();
                    }
                }
            } else {
                this.location.x = -1000;
                this.location.y = -1000;
            }
        }
        if (isDie() && lvMob == 3 && (System.currentTimeMillis() - lastTimeDie) > 15000) {
            lvMob = 0;
            action = 0;
            this.location.x = Util.nextInt(100, 900);
            this.location.y = 360;
            this.point.hp = this.point.getHpFull();
            Service.gI().sendBigBoss2(this.zone, action, this);
            Message msg = null;
            try {
                msg = new Message(-9);
                msg.writer().writeByte(this.id);
                msg.writeNum(this.point.gethp());
                msg.writeNum(1);
                Service.gI().sendMessAllPlayerInMap(this.zone, msg);
            } catch (Exception e) {
            } finally {
                if (msg != null) {
                    msg.cleanup();
                }
            }
        }
    }

    @Override
    public void attack() {
        if (!isDie() && !effectSkill.isHaveEffectSkill() && Util.canDoWithTime(lastBigBossAttackTime, 3000)) {
            Message msg = null;
            try {
                int[] idAction = new int[]{1, 2, 3, 7};
                if (this.lvMob >= 2) {
                    idAction = new int[]{1, 2};
                }
                action = action == 7 ? 0 : idAction[Util.nextInt(0, idAction.length - 1)];
                if (this.zone.getPlayers().isEmpty()) {
                    return;
                }
                int index = Util.nextInt(0, this.zone.getPlayers().size() - 1);
                Player player = this.zone.getPlayers().get(index);
                if (player == null || player.isDie()) {
                    return;
                }
                if (action == 1) {
                    this.location.x = (short) player.location.x;
                    Service.gI().sendBigBoss2(this.zone, 8, this);
                }
                msg = new Message(101);
                msg.writer().writeByte(action);
                if (action >= 0 && action <= 4) {
                    switch (action) {
                        case 1:
                            msg.writer().writeByte(1);
                            int dame = player.injured(null, this.point.getDameAttack(), false, true);
                            msg.writer().writeInt((int) player.id);
                            msg.writer().writeInt(dame);
                            break;
                        case 3:
                            this.location.x = (short) player.location.x;
                            msg.writer().writeShort(this.location.x);
                            msg.writer().writeShort(this.location.y);
                            break;
                        default:
                            msg.writer().writeByte(this.zone.getPlayers().size());
                            for (int i = 0; i < this.zone.getPlayers().size(); i++) {
                                Player pl = this.zone.getPlayers().get(i);
                                dame = pl.injured(null, this.point.getDameAttack(), false, true);
                                msg.writer().writeInt((int) pl.id);
                                msg.writer().writeInt(dame);
                            }
                            break;
                    }
                } else {
                    if (action == 6 || action == 8) {
                        this.location.x = (short) player.location.x;
                        msg.writer().writeShort(this.location.x);
                        msg.writer().writeShort(this.location.y);
                    }
                }
                Service.gI().sendMessAllPlayerInMap(this.zone, msg);
                lastBigBossAttackTime = System.currentTimeMillis();
            } catch (Exception e) {
                if (errors < 5) {
                    errors++;
                    e.printStackTrace();
                }
            } finally {
                if (msg != null) {
                    msg.cleanup();
                }
            }
        }
    }

}
