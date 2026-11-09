package nro.models.Bot;

import com.mysql.jdbc.Messages;
import java.util.Random;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import nro.models.map.ItemMap;
import nro.models.map.service.ItemMapService;
import nro.models.mob.Mob;
import nro.models.services.PlayerService;
import nro.models.services.*;
import nro.models.services.SkillService;
import nro.models.skill.Skill;
import nro.models.utils.Logger;
import nro.models.utils.Util;

public class BotPemQuai {

    private Mob mAttack;
    public long lastTimeChanM;
    public Bot bot;
    private boolean isPickingItem = false;
    private long lastSpecialSkillTime;
    private long lastChatTime = 0;
    private final long chatInterval = 90_000;

    public BotPemQuai(Bot b) {
        this.bot = b;
    }

    /** câu chat bình thường như người chơi thật (thỉnh thoảng mới chat) */
    static final String[] chatMessages = {
        "up sm mệt ghê", "ai đi săn boss không", "map này đông quá", "cày xong lên map khác", "đệ mình sắp đánh được Broly rồi",
        "hôm nay sự kiện vui ghê", "có ai pt không", "lag quá ae", "sắp lên cấp rồi", "cày đua top thôi", "gg", "ae ở đâu đấy",
        "hello mọi người", "ai bán thỏi vàng không", "săn đệ mãi chưa ra", "đi ngọc rồng sao đen không ae"};

    private void autoChat() {
        long now = System.currentTimeMillis();
        if (now - lastChatTime >= chatInterval && Util.isTrue(15, 100)) {
            Service.gI().chat(this.bot, chatMessages[new Random().nextInt(chatMessages.length)]);
            lastChatTime = now;
        } else if (now - lastChatTime >= chatInterval) {
            lastChatTime = now;
        }
    }

    public void update() {
        try {
            autoChat();
        } catch (Exception e) {
            Logger.logException(BotPemQuai.class, e);
        }

        if (attackPkTarget()) {
            return;
        }
        if (this.bot.accountBot && defendNrsd()) {
            return;
        }
        if (this.bot.accountBot && doanhTrai()) {
            return;
        }
        Attack();
        if ((!this.bot.worldBot || !"NRSD".equals(this.bot.activity)) && !this.bot.stayMap) {
            chanGeMap(); // đang đánh NRSD / Hirudegarn / Kilis / Thánh địa thì ở lại map
        }
        if (this.bot.accountBot || this.bot.zone == null || !nro.models.map.service.MapService.gI().isMapBlackBallWar(this.bot.zone.map.mapId)) {
            NhatItem(); // bot giả lập không nhặt trong NRSD; tài khoản bot nhặt cả ngọc rồng sao đen như người chơi
        }
    }

    /** Tài khoản bot trong NRSD: đánh người khác cờ ở gần (thủ / cướp ngọc rồng sao đen như người chơi thật). */
    private boolean defendNrsd() {
        if (this.bot.zone == null || !nro.models.map.service.MapService.gI().isMapBlackBallWar(this.bot.zone.map.mapId) || this.bot.cFlag == 0) {
            return false;
        }
        nro.models.player.Player best = null;
        int bestD = 400;
        for (nro.models.player.Player p : this.bot.zone.getPlayers()) {
            if (p == null || p == this.bot || p.isDie() || p.isPet || p.isBoss || p.cFlag == 0 || p.cFlag == this.bot.cFlag) {
                continue;
            }
            int d = Util.getDistance(this.bot, p);
            if (d < bestD) {
                best = p;
                bestD = d;
            }
        }
        if (best == null) {
            return false;
        }
        Skill pick = bot.pickSkill(2);
        if (pick == null) {
            return true;
        }
        bot.playerSkill.skillSelect = pick;
        if (this.bot.UseLastTimeSkill()) {
            PlayerService.gI().playerMove(this.bot, best.location.x + Util.nextInt(-30, 30), best.location.y);
            SkillService.gI().useSkillAttack(bot, best, null);
        }
        NhatItem();
        return true;
    }

    /** Trong doanh trại: đánh hết quái, đánh boss của map, xong thì sang map tiếp theo của doanh trại; nhặt ngọc rồng rơi ra. */
    private boolean doanhTrai() {
        if (this.bot.zone == null || !nro.models.map.service.MapService.gI().isMapDoanhTrai(this.bot.zone.map.mapId)) {
            return false;
        }
        nro.models.player.Player boss = null;
        for (nro.models.player.Player p : this.bot.zone.getBosses()) {
            if (p != null && !p.isDie()) {
                boss = p;
                break;
            }
        }
        Mob alive = null;
        for (Mob m : this.bot.zone.mobs) {
            if (trainable(m) || (m.tempId == this.bot.questMob && !m.isDie())) {
                alive = m;
                break;
            }
        }
        if (alive != null) {
            if (this.mAttack == null || this.mAttack.isDie() || this.mAttack.zone != this.bot.zone) {
                this.mAttack = alive;
            }
            Attack();
        } else if (boss != null) {
            Skill pick = bot.pickSkill(2);
            if (pick != null) {
                bot.playerSkill.skillSelect = pick;
            }
            if (this.bot.UseLastTimeSkill()) {
                PlayerService.gI().playerMove(this.bot, boss.location.x + Util.nextInt(-30, 30), boss.location.y);
                SkillService.gI().useSkillAttack(bot, boss, null);
            }
        } else if (this.bot.clan != null && this.bot.clan.doanhTrai != null && this.bot.zone.items.isEmpty()
                && System.currentTimeMillis() - this.lastTimeChanM > 5_000) {
            for (nro.models.map.Zone z : this.bot.clan.doanhTrai.zones) {
                if (z == this.bot.zone) {
                    continue;
                }
                boolean left = !z.getBosses().stream().allMatch(p -> p == null || p.isDie());
                for (Mob m : z.mobs) {
                    left |= !m.isDie();
                }
                if (left) {
                    nro.models.map.service.ChangeMapService.gI().goToMap(this.bot, z);
                    this.bot.zone.load_Me_To_Another(this.bot);
                    this.lastTimeChanM = System.currentTimeMillis();
                    break;
                }
            }
        }
        NhatItem();
        return true;
    }

    /** PK với bot khác (không đánh người chơi thật). */
    private boolean attackPkTarget() {
        Bot t = this.bot.pkTarget;
        if (t == null) {
            return false;
        }
        if (t.isDie() || t.zone == null || t.zone != this.bot.zone || System.currentTimeMillis() > this.bot.pkUntil) {
            this.bot.pkTarget = null;
            return false;
        }
        Skill pick = bot.pickSkill(2);
        if (pick == null) {
            return true;
        }
        bot.playerSkill.skillSelect = pick;
        if (this.bot.UseLastTimeSkill()) {
            PlayerService.gI().playerMove(this.bot, t.location.x + Util.nextInt(-30, 30), t.location.y);
            SkillService.gI().useSkillAttack(bot, t, null);
        }
        return true;
    }

    public void GetMobAttack() {
        if (this.bot.questMob >= 0 && (this.mAttack == null || this.mAttack.isDie() || this.mAttack.tempId != this.bot.questMob)) {
            for (Mob m : this.bot.zone.mobs) {
                if (m.tempId == this.bot.questMob && !m.isDie()) { // ưu tiên quái nhiệm vụ cần đánh
                    this.mAttack = m;
                    return;
                }
            }
        }
        if (this.mAttack == null || this.mAttack.isDie() || !trainable(this.mAttack) && this.mAttack.tempId != this.bot.questMob) {
            java.util.List<Mob> ok = new java.util.ArrayList<>();
            for (Mob m : this.bot.zone.mobs) {
                if (trainable(m)) {
                    ok.add(m);
                }
            }
            mAttack = ok.isEmpty() ? null : ok.get(new Random().nextInt(ok.size()));
        }
    }

    /** Quái đánh để luyện tập: không đánh máy đo sức mạnh, mộc nhân, bù nhìn, cỗ máy (chỉ khi nhiệm vụ cần). */
    static boolean trainable(Mob m) {
        return m != null && !m.isDie() && m.tempId != nro.models.consts.ConstMob.MAY_DO_SUC_MANH && m.tempId != nro.models.consts.ConstMob.MOC_NHAN
                && m.tempId != nro.models.consts.ConstMob.BU_NHIN_MA_QUAI && m.tempId != nro.models.consts.ConstMob.CO_MAY_HUY_DIET;
    }

    public void Attack() {
        this.GetMobAttack();
        if (this.mAttack == null || this.mAttack.isDie()) {
            return;
        }

        Skill skill = bot.pickSkill(0);
        if (skill == null) {
            return; // chưa học chiêu nào
        }
        bot.playerSkill.skillSelect = skill;

        long now = System.currentTimeMillis();
        boolean isSpecialSkill = false;

        switch (bot.playerSkill.skillSelect.template.id) {
            case Skill.BIEN_KHI:
                isSpecialSkill = true;
                if (now - lastSpecialSkillTime >= 300_000) {
                    EffectSkillService.gI().sendEffectMonkey(bot);
                    EffectSkillService.gI().setIsMonkey(bot);
                    Service.gI().sendSpeedPlayer(bot, 5);
                    Service.gI().Send_Caitrang(bot);
                    Service.gI().sendSpeedPlayer(bot, -1);
                    PlayerService.gI().sendInfoHpMp(bot);
                    Service.gI().point(bot);
                    Service.gI().Send_Info_NV(bot);
                    Service.gI().sendInfoPlayerEatPea(bot);
                    SkillService.gI().sendPlayerPrepareSkill(bot, 300000);
                    lastSpecialSkillTime = now;
                }
                return;

            default:
                break;
        }

        if (isSpecialSkill && now - lastSpecialSkillTime < 300_000) {
            return;
        }

        boolean miss = false;
        int skillId = bot.playerSkill.skillSelect.template.id;
        if (skillId == Skill.KAMEJOKO || skillId == Skill.MASENKO || skillId == Skill.ANTOMIC) {
            if (mAttack != null) {
                int dame = Util.nextInt(10000, 15000);
            }
            SkillService.gI().useSkillAttack(bot, bot, mAttack);
            SkillService.gI().useSkillAlone(bot);
            return;
        }

        if (this.bot.UseLastTimeSkill()) {
            PlayerService.gI().playerMove(this.bot, this.mAttack.location.x, this.mAttack.location.y);
            SkillService.gI().useSkillAttack(bot, bot, mAttack);
            SkillService.gI().useSkillAlone(bot);
        }
    }

    public void chanGeMap() {
        if (this.lastTimeChanM < ((System.currentTimeMillis() - 150000) - new Random().nextInt(150000))) {
            this.bot.joinMap();
        }
    }

    private void NhatItem() {
        if (this.bot == null || this.bot.zone == null || this.bot.zone.items == null) {
            return;
        }
        if (isPickingItem) {
            return;
        }

        if (this.bot.charms != null && this.bot.charms.tdThuHut > System.currentTimeMillis()) {
            List<ItemMap> itemsToPick = new ArrayList<>();

            for (ItemMap item : this.bot.zone.getItemMapsForPlayer(this.bot)) { // như người chơi: không thấy vật phẩm nhiệm vụ của bước khác
                if (!item.isPickedUp
                        && Util.getDistance(this.bot.location.x, this.bot.location.y, item.x, item.y) < 500) {
                    itemsToPick.add(item);
                    if (itemsToPick.size() >= 3) {
                        break;
                    }
                }
            }

            if (itemsToPick.isEmpty()) {
                return;
            }

            isPickingItem = true;

            for (ItemMap item : itemsToPick) {
                try {
                    ItemMapService.gI().pickItem(bot, item.itemMapId, true); // tự xoá khỏi map (trừ vật phẩm nhiệm vụ cố định như em bé)
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    isPickingItem = false;
                }
            }, 500);
        }
    }
}
