package nro.models.boss.event_tet;
import nro.models.boss.Boss;
import nro.models.boss.BossesData;
import nro.models.boss.BossID;
import nro.models.consts.BossStatus;
import nro.models.boss.Boss_Manager.BossManager;
import static nro.models.consts.BossType.TET_EVENT;
import nro.models.player.Player;
import nro.models.server.Client;
import nro.models.services.EffectSkillService;
import nro.models.services.Service;
import nro.models.map.service.ChangeMapService;
import nro.models.utils.Logger;
import nro.models.utils.Util;

/**
 * Boss Tết "Bé Na" (theo source V10): đánh hết máu thì Bé Na đi theo người hạ, đưa đến Quy Lão Kame bấm "Giao Bé Na" nhận Capsule Tết.
 */
public class BeNa extends Boss {

    private long st;
    private int timeLeave;
    private long lastTimeAtt;
    private long playerId;
    private boolean afk;

    public BeNa() throws Exception {
        super(TET_EVENT, BossID.BE_NA - Util.nextInt(1000000), BossesData.BE_NA);
    }

    @Override
    public void joinMap() {
        if (zoneFinal != null) {
            joinMapByZone(zoneFinal);
            this.notifyJoinMap();
            this.changeStatus(BossStatus.CHAT_S);
            this.wakeupAnotherBossWhenAppear();
            return;
        }
        if (this.zone == null) {
            if (this.parentBoss != null) {
                this.zone = parentBoss.zone;
            } else if (this.lastZone == null) {
                this.zone = getMapJoin();
            } else {
                this.zone = this.lastZone;
            }
        }
        if (this.zone != null) {
            try {
                int zoneid = 0;
                // Check trong khu lớn hơn 10 người chuyển sang khu n + 1
                while (zoneid < this.zone.map.zones.size() && this.zone.map.zones.get(zoneid).getNumOfPlayers() > 10) {
                    zoneid++;
                }
                // Check trong khu có boss sẽ chuyển sang khu n + 1
                while (zoneid < this.zone.map.zones.size() && BossManager.gI().checkBosses(this.zone.map.zones.get(zoneid), BossID.ONG_GIA_NOEL)) {
                    zoneid++;
                }
                if (zoneid < this.zone.map.zones.size()) {
                    this.zone = this.zone.map.zones.get(zoneid);
                } else {
                    this.leaveMapNew();
                    return;
                }
                ChangeMapService.gI().changeMap(this, this.zone, Util.nextInt(100, 500), this.zone.map.yPhysicInTop(this.location.x,
                        this.location.y - 24));
                this.changeStatus(BossStatus.CHAT_S);
                st = System.currentTimeMillis();
                timeLeave = Util.nextInt(100000, 300000);
            } catch (Exception e) {
                Logger.error(this.data[0].getName() + ": Lỗi đang tiến hành REST\n");
                this.changeStatus(BossStatus.REST);
            }
        } else {
            Logger.error(this.data[0].getName() + ": Lỗi map đang tiến hành RESPAWN\n");
            this.changeStatus(BossStatus.RESPAWN);
        }
    }

    @Override
    public void chatM() {
        if (this.data[this.currentLevel].getTextM().length == 0) {
            return;
        }
        if (!Util.canDoWithTime(this.lastTimeChatM, this.timeChatM)) {
            return;
        }
        String textChat = this.data[this.currentLevel].getTextM()[Util.nextInt(0, this.data[this.currentLevel].getTextM().length - 1)];
        int prefix = Integer.parseInt(textChat.substring(1, textChat.lastIndexOf("|")));
        textChat = textChat.substring(textChat.lastIndexOf("|") + 1);
        this.chat(prefix, textChat);
        this.lastTimeChatM = System.currentTimeMillis();
        this.timeChatM = Util.nextInt(3000, 20000);
    }

    @Override
    public void autoLeaveMap() {
        if (Util.canDoWithTime(st, timeLeave)) {
            this.leaveMapNew();
        }
    }

    @Override
    public void leaveMap() {
        ChangeMapService.gI().exitMap(this);
        this.lastZone = null;
        this.playerId = -1;
        this.lastTimeRest = System.currentTimeMillis();
        this.changeStatus(BossStatus.REST);
    }

    @Override
    public void attack() {
        if (Util.canDoWithTime(this.lastTimeAttack, 250)) {
            this.lastTimeAttack = System.currentTimeMillis();
            try {
                Player pl = getPlayerAttack();
                if (pl == null || pl.location == null || pl.isDie()) {
                    return;
                }
                int dis = Util.getDistance(this, pl);
                if (dis > 450) {
                    move(pl.location.x - 24, pl.location.y);
                } else if (dis > 100) {
                    int dir = (this.location.x - pl.location.x < 0 ? 1 : -1);
                    int move = Util.nextInt(50, 100);
                    move(this.location.x + (dir == 1 ? move : -move), pl.location.y);
                } else {
                    if (Util.canDoWithTime(lastTimeAtt, 30000) && this.nPoint.hp < this.nPoint.hpMax) {
                        if (Util.isTrue(10, 100)) {
                            Service.gI().moveFast(pl, this.location.x, this.location.y);
                            pl.setDie();
                            Service.gI().sendThongBao(pl, "Bạn đã bị Bé Na quật chết!");
                        }
                        lastTimeAtt = System.currentTimeMillis();
                    }
                }
            } catch (Exception ex) {
            }
        }
    }

    @Override
    public void afk() {
        if (Util.canDoWithTime(this.lastTimeAttack, 500)) {
            this.lastTimeAttack = System.currentTimeMillis();
            Player pl = Client.gI().getPlayer(playerId);
            if (pl == null || pl.zone == null) {
                return;
            }
            if (pl.haveReward) {
                pl.haveReward = false;
                this.leaveMap();
                return;
            }
            if (this.zone.equals(pl.zone)) {
                int dis = Util.getDistance(this, pl);
                if (dis <= 300) {
                    if (dis > 50) {
                        int dir = (this.location.x - pl.location.x < 0 ? 1 : -1);
                        int move = Util.nextInt(50, 100);
                        move(this.location.x + (dir == 1 ? move : -move), pl.location.y);
                        st = System.currentTimeMillis();
                    }
                    afk = false;
                    pl.canReward = true;
                } else {
                    afk = true;
                    pl.canReward = false;
                }
            } else if (!afk) {
                if (pl.changeMapVIP) {
                    pl.changeMapVIP = false;
                    pl.canReward = false;
                    afk = true;
                    return;
                }
                ChangeMapService.gI().changeMap(this, pl.zone, pl.location.x + Util.nextInt(-10, 10), pl.location.y);
            }
        }
    }

    @Override
public synchronized long injured(Player plAtt, long damage, boolean piercing, boolean isMobAttack) {
    if (!this.isDie()) {
        if (!piercing && Util.isTrue(100, 1000)) {
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
        if (damage > 500_000) {
            damage = 500_000;
        }

        // Kiểm tra nếu damage >= HP hoặc người chơi chat "thang"
        if (damage >= this.nPoint.hp) {
            this.changeToTypeNonPK();
            this.playerId = Math.abs(plAtt.id);
            plAtt.escortType = 2;
            Service.gI().chat(plAtt, "Đi thôi bé rắn cute!");
            this.nPoint.hp = this.nPoint.hpMax;
            this.changeStatus(BossStatus.AFK);
            return 0;
        }
        this.nPoint.subHP(damage);
        return damage;
    } else {
        return 0;
    }
}
private boolean isPlayerChatThang(Player plAtt) {   
    String lastChat = plAtt.getLastChatMessage();  
    return lastChat != null && lastChat.toLowerCase().contains("thang");
}

    @Override
    public void reward(Player plKill) {
    }
}
