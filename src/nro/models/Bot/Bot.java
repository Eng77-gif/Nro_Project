package nro.models.Bot;

import java.util.Random;
import nro.models.consts.ConstPlayer;
import nro.models.map.Map;
import nro.models.map.Zone;
import nro.models.map.service.ChangeMapService;
import nro.models.map.service.MapService;
import nro.models.player.Player;
import nro.models.services.PlayerService;
import nro.models.player_system.Template.SkillTemplate;
import nro.models.server.Manager;
import nro.models.services.EffectSkillService;
import nro.models.services.Service;
import nro.models.services.SkillService;
import nro.models.skill.NClass;
import nro.models.skill.Skill;
import nro.models.utils.Util;

public class Bot extends Player {

    private short head_;
    private short body_;
    private short leg_;
    private short flag_;
    private int type;
    private int index_ = 0;
    public BotGiaoDich shop;
    public BotSanBoss boss;
    public BotPemQuai mo1;
    public Bot bot;

    private Player plAttack;

    private int[] TraiDat = new int[]{1, 2, 3, 4, 6, 29, 30, 28, 27, 110, 103, 106, 105, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74 ,75 ,76, 77, 78, 79, 80, 81, 82, 83, 92, 93, 94, 95, 96, 97, 98, 99, 100, 107, 108, 109};
    private int[] Namec = new int[]{8, 9, 10, 11, 12, 13, 33, 34, 32, 31, 110, 103, 106, 105, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74 ,75 ,76, 77, 78, 79, 80, 81, 82, 83, 92, 93, 94, 95, 96, 97, 98, 99, 100, 107, 108, 109};
    private int[] XayDa = new int[]{15, 16, 17, 18, 19, 20, 37, 36, 35, 110, 106, 105, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74 ,75 ,76, 77, 78, 79, 80, 81, 82, 83, 92, 93, 94, 95, 96, 97, 98, 99, 100, 107, 108, 109};
    Player player;

    public Bot(short head, short body, short leg, int type, String name, BotGiaoDich shop, short flag) {
        this.head_ = head;
        this.body_ = body;
        this.leg_ = leg;
        this.shop = shop;
        this.name = name;
        this.id = new Random().nextInt(2000000000);
        this.type = type;
        this.isBot = true;
        this.flag_ = flag;
    }

    /** Map nhiệm vụ / úp SKH đang luyện (BotQuest), -1: chọn theo sức mạnh. */
    public volatile int questMap = -1;
    /** Loại quái nhiệm vụ cần đánh (BotQuest), -1: đánh quái bất kỳ. */
    public volatile int questMob = -1;
    /** Đang ở map vào qua NPC (Hirudegarn, Sa mạc hoang vu, Thánh địa): không tự đổi map / không chặn theo cổng. */
    public volatile boolean stayMap;

    public int MapToPow() {
        if (this.questMap >= 0) {
            return this.questMap;
        }
        if (!this.accountBot) {
            return pickTierMap(); // bot giả lập: chọn ngẫu nhiên
        }
        int m = BotLearn.choose(this, BotLearn.candidates(this, this::pickTierMap)); // tài khoản bot: map đã học là lên nhanh nhất (BotLearn)
        return m >= 0 ? m : pickTierMap();
    }

    int learnMap = -1, learnTier, learnDeaths; // lượt luyện tập đang ghi của BotLearn
    long learnPower, learnGold, learnSince;
    private long lastLearnTick;
    private int deathTotal;
    int brainAction = -1, brainTier, brainDeaths; // lượt đang chạy của BotBrain
    long brainPower, brainAt, brainGold, brainGem;

    int deaths() {
        return deathTotal;
    }

    private int pickTierMap() {
        Random random = new Random();
        long power = this.nPoint.power;
        if (this.accountBot) {
            // chọn vùng map theo chỉ số thật (HP, sức đánh) như người chơi tự lượng sức, không chỉ theo sức mạnh
            long hp = this.nPoint.hpMax, dm = this.nPoint.dame;
            long byStat = hp >= 3_000_000 && dm >= 150_000 ? Long.MAX_VALUE
                    : hp >= 500_000 && dm >= 30_000 ? 39_000_000_000L
                    : hp >= 20_000 ? 1_900_000_000L : 90_000_000L;
            power = Math.min(power, byStat);
        }
        // chết nhiều ở vùng map hiện tại: xuống vùng dễ hơn (mỗi bậc giảm ngưỡng sức mạnh)
        for (int i = 0, n = TIER_DROP.getOrDefault(this.id, 0); i < n; i++) {
            power = power >= 40_000_000_000L ? 39_000_000_000L : power >= 2_000_000_000L ? 1_900_000_000L : 90_000_000L;
        }
        int mapId = 21;
        if (power < 100000000 && this.accountBot) {
            // tài khoản bot: dưới 100 triệu chỉ luyện ở các map của hành tinh mình như người mới chơi
            // không có Vách núi 42 - 44 (máy đo sức mạnh) và Đại hội võ thuật 52: không phải chỗ luyện tập
            int[] home = this.gender == 0 ? new int[]{1, 2, 3, 4, 6, 29, 30, 28, 27}
                    : this.gender == 1 ? new int[]{8, 9, 10, 11, 12, 13, 33, 34, 32, 31} : new int[]{15, 16, 17, 18, 19, 20, 37, 36, 35};
            int lim = power < 2_000_000 ? Math.min(5, home.length) : home.length;
            mapId = home[random.nextInt(lim)];
        } else if (power < 100000000) {
            if (this.gender == 0) {
                mapId = TraiDat[random.nextInt(TraiDat.length)];
            } else if (this.gender == 1) {
                mapId = Namec[random.nextInt(Namec.length)];
            } else {
                mapId = XayDa[random.nextInt(XayDa.length)];
            }
        } else if (power < 2_000_000_000L) {
            mapId = 62 + random.nextInt(15);
        } else if (power < 40_000_000_000L) {
            if (Util.isTrue(40, 100)) {
                mapId = 92 + random.nextInt(3); // 92-94 tương lai
            } else {
                mapId = 96 + random.nextInt(5); // 96-100 (bỏ 91 NRSD, 95/103/104 không có quái)
            }
        } else {
            mapId = 105 + random.nextInt(6); // trên 40 tỷ: map Cold (trước đây 70% rơi về Nhà Gôhan không có quái)
        }
        return mapId;
    }

    /**
     * Đủ sức đánh quái ở map chưa (như người chơi tự lượng sức): quái tiêu biểu (trung vị HP) chết trong khoảng 30 đòn và
     * 6 đòn của quái chưa hạ được bot. Map không có quái luôn đi được.
     */
    public boolean strongEnough(int mapId) {
        Map m = MapService.gI().getMapById(mapId);
        if (m == null || m.zones.isEmpty() || this.nPoint == null) {
            return true;
        }
        java.util.List<nro.models.mob.Mob> mobs = new java.util.ArrayList<>(m.zones.get(0).mobs);
        // máy đo sức mạnh (2 tỷ HP, không đánh trả) ở Vách núi: trước đây làm map như "quá sức" nên bot kẹt nhiệm vụ 3.1
        mobs.removeIf(x -> x.tempId == nro.models.consts.ConstMob.MAY_DO_SUC_MANH);
        if (mobs.isEmpty()) {
            return true;
        }
        mobs.sort(java.util.Comparator.comparingLong(x -> x.point.getHpFull()));
        nro.models.mob.Mob mid = mobs.get(mobs.size() / 2);
        long hp = mid.point.getHpFull();
        long dame = mid.point.dame != 0 ? mid.point.dame : hp * mid.pDame / 100;
        return hp <= this.nPoint.dame * 30 && dame * 6 <= this.nPoint.hpMax;
    }

    /** Map luyện tập đã mở theo nhiệm vụ có quái yếu nhất (trung vị HP thấp nhất) trên hành tinh của bot; -1 nếu chưa mở map nào. */
    int easiestMap() {
        int[] home = this.gender == 0 ? new int[]{1, 2, 3, 4, 6, 29, 30, 28, 27}
                : this.gender == 1 ? new int[]{8, 9, 10, 11, 12, 13, 33, 34, 32, 31} : new int[]{15, 16, 17, 18, 19, 20, 37, 36, 35};
        int best = -1;
        long bestHp = Long.MAX_VALUE;
        for (int id : home) {
            Map m = MapService.gI().getMapById(id);
            if (m == null || m.zones.isEmpty() || m.zones.get(0).mobs.isEmpty() || !canGo(id)) {
                continue;
            }
            java.util.List<nro.models.mob.Mob> mobs = new java.util.ArrayList<>(m.zones.get(0).mobs);
            mobs.sort(java.util.Comparator.comparingLong(x -> x.point.getHpFull()));
            long hp = mobs.get(mobs.size() / 2).point.getHpFull();
            if (hp < bestHp) {
                bestHp = hp;
                best = id;
            }
        }
        return best;
    }

    /** Tài khoản bot chỉ vào map người chơi cùng nhiệm vụ vào được (luật ChangeMapService.checkMapCanJoin: Namếc, tương lai, Cold...). */
    public long arenaUntil, arenaIdle; // đang thi đấu Siêu hạng / ĐHVT 23 (BotArena)
    public long lastShopTop, lastSieuHang, lastDhvt23; // lần gần nhất mua hộp đua top / đi giải Siêu hạng / ĐHVT 23

    public boolean canGo(int mapId) {
        if (!this.accountBot || (this.stayMap && mapId == this.questMap)) {
            return true;
        }
        if (MapService.gI().isMapPhoBan(mapId)) {
            return false; // phó bản bang (doanh trại 53 - 62...) chỉ vào cùng bang qua NPC, không chọn làm map luyện tập (trước đây kẹt ở Trại độc nhãn 3)
        }
        Map m = MapService.gI().getMapById(mapId);
        return m != null && !m.zones.isEmpty() && ChangeMapService.gI().checkMapCanJoin(this, m.zones.get(0)) != null;
    }

    public void joinMap() {
        int mapId = MapToPow();
        for (int t = 0; t < 8 && !(canGo(mapId) && (!this.accountBot || strongEnough(mapId))); t++) {
            mapId = this.questMap >= 0 ? -1 : MapToPow(); // tài khoản bot: ưu tiên map đánh nổi quái
        }
        for (int t = 0; t < 8 && !canGo(mapId); t++) {
            mapId = MapToPow();
        }
        if (!canGo(mapId)) { // chưa tới nhiệm vụ mở map đó: luyện ở map đầu của hành tinh mình
            mapId = new int[]{1, 8, 15}[Math.max(0, Math.min(2, this.gender))];
            if (!canGo(mapId)) {
                return;
            }
        }
        if (this.accountBot && !strongEnough(mapId)) {
            // rút ngẫu nhiên không ra map vừa sức (hoặc map úp SKH / map nhiệm vụ quá mạnh): tới map dễ nhất đã mở, không liều vào map quái trâu
            int easy = easiestMap();
            if (easy < 0) {
                return;
            }
            mapId = easy;
        }
        Zone zone = getRandomZone(mapId);
        if (zone != null) {
            ChangeMapService.gI().goToMap(this, zone);
            this.zone.load_Me_To_Another(this);
            this.mo1.lastTimeChanM = System.currentTimeMillis();
        }
    }

    public Zone getRandomZone(int mapId) {
        Map map = MapService.gI().getMapById(mapId);
        Zone zone = null;
        try {
            if (map != null && map.tileMap != null) { // map thiếu dữ liệu ô gạch: người chơi không vào được, bot cũng không
                zone = map.zones.stream()
                        .filter(z -> z.getNumOfPlayers() == 0)
                        .findFirst()
                        .orElseGet(() -> {
                            Zone randomZone = map.zones.get(Util.nextInt(0, map.zones.size() - 1));
                            return randomZone.isFullPlayer() ? null : randomZone;
                        });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (zone != null) {
            this.index_ = 0;
            return zone;
        } else {
            this.index_ += 1;
            if (this.index_ >= 20 && !this.accountBot) { // tài khoản bot không bị gỡ ngang (mất dữ liệu chưa lưu)
                BotManager.gI().bot.remove(this);
                ChangeMapService.gI().exitMap(this);
                return null;
            } else {
                return getRandomZone(MapToPow());
            }
        }
    }

    @Override
    public short getHead() {
        if (this.accountBot) {
            return super.getHead(); // nhân vật thật: hình theo đồ đang mặc
        }
        if (effectSkill.isMonkey) {
            return (short) ConstPlayer.HEADMONKEY[effectSkill.levelMonkey - 1];
        } else {
            return this.head_;
        }
    }

    @Override
    public short getBody() {
        if (this.accountBot) {
            return super.getBody(); // nhân vật thật: hình theo đồ đang mặc
        }
        if (effectSkill.isMonkey) {
            return 193;
        } else {
            return this.body_;
        }
    }

    @Override
    public short getLeg() {
        if (this.accountBot) {
            return super.getLeg(); // nhân vật thật: hình theo đồ đang mặc
        }
        if (effectSkill.isMonkey) {
            return 194;
        } else {
            return this.leg_;
        }
    }

    @Override
    public short getFlagBag() {
        if (this.accountBot) {
            return super.getFlagBag(); // nhân vật thật: hình theo đồ đang mặc
        }
        return this.flag_;
    }

    @Override
    public void update() {
        if (this.awayUntil > System.currentTimeMillis()) {
            return; // đang ở doanh trại / ĐHVT (không có mặt trên map)
        }
        if (this.nPoint == null || this.zone == null) {
            return; // đã bị huỷ hoặc chưa vào map
        }
        if (this.accountBot && nro.models.services_func.TransactionService.gI().check(this)) {
            return; // đang giao dịch: đứng yên như người chơi mở bảng giao dịch (nhặt đồ sẽ làm huỷ giao dịch)
        }
        super.update();
        this.increasePoint();
        if (this.accountBot && System.currentTimeMillis() - this.lastLearnTick > 15_000) {
            this.lastLearnTick = System.currentTimeMillis();
            BotLearn.tick(this); // ghi / chấm lượt luyện tập ở map (BotLearn)
        }
        switch (this.type) {
            case 0:
                this.mo1.update();
                break;
            case 1:
                this.shop.update();
                break;
            case 2:
                this.boss.update();
                break;
            case BotArena.MODE_ARENA:
                BotArena.update(this);
                break;
        }
        if (this.isDie()) {
            this.deathTotal++;
            if (this.accountBot) {
                long now = System.currentTimeMillis();
                if (now - this.deathWindow > 5 * 60_000L) {
                    this.deathWindow = now;
                    this.deaths = 0;
                }
                int td = TIER_DROP.getOrDefault(this.id, 0);
                if (++this.deaths >= 3 && td < 3) {
                    TIER_DROP.put(this.id, td + 1); // chết 3 lần / 5 phút: đổi sang vùng map dễ hơn (nhớ cả khi bot vào lại)
                    this.deaths = 0;
                    Service.gI().hsChar(this, nPoint.hpMax, nPoint.mpMax);
                    this.joinMap();
                    return;
                }
            }
            Service.gI().hsChar(this, nPoint.hpMax, nPoint.mpMax);
        }
    }

    private static final java.util.Map<Long, Integer> TIER_DROP = new java.util.concurrent.ConcurrentHashMap<>();
    private int deaths;
    private long deathWindow;

    /** Chọn ngẫu nhiên 1 chiêu đã học (point > 0) trong n chiêu đầu (n <= 0: tất cả); không có thì null. */
    public Skill pickSkill(int n) {
        java.util.List<Skill> ok = new java.util.ArrayList<>();
        for (Skill sk : this.playerSkill.skills) {
            if (sk != null && sk.point > 0) {
                ok.add(sk);
            }
        }
        if (ok.isEmpty()) {
            return null;
        }
        int lim = n <= 0 ? ok.size() : Math.min(n, ok.size());
        return ok.get(new java.util.Random().nextInt(lim));
    }

    public void leakSkill() {
        for (NClass n : Manager.gI().NCLASS) {
            if (n.classId == this.gender) {
                for (SkillTemplate template : n.skillTemplatess) {
                    for (Skill skill : template.skillss) {
                        if (skill.point == 7) {
                            this.playerSkill.skills.add(new Skill(skill));
                            break;
                        }
                    }
                }
                break;
            }
        }
    }

    public boolean UseLastTimeSkill() {
        if (this.playerSkill.skillSelect.lastTimeUseThisSkillbot < (System.currentTimeMillis() - this.playerSkill.skillSelect.coolDown)) {
            this.playerSkill.skillSelect.lastTimeUseThisSkillbot = System.currentTimeMillis();
            return true;
        } else {
            return false;
        }
    }

    private void increasePoint() {
        if (this.nPoint == null) {
            return;
        }
        // tài khoản bot cộng tiềm năng như người chơi bấm cộng nhanh (nhiều lần mỗi lượt) khi còn nhiều tiềm năng
        int times = this.accountBot && this.nPoint.tiemNang > 1_000_000_000L ? 30 : 1;
        boolean changed = false;
        for (int t = 0; t < times; t++) {
            // như người chơi bấm cộng 10 điểm (NPoint.increasePoint): HP / KI +20 mỗi điểm, sức đánh +1 mỗi điểm; ưu tiên HP để sống được
            // cân bằng như người chơi cày: HP gốc ~20 lần sức đánh gốc, KI ~ nửa HP, còn lại dồn sức đánh
            // (trước đây cộng ngẫu nhiên, 65% vào HP / KI: 2,8 triệu sức mạnh mà sức đánh gốc 70, không qua nổi map thứ 3)
            int point = 10;
            boolean needHp = this.nPoint.hpg < this.nPoint.dameg * 20, needKi = !needHp && this.nPoint.mpg < this.nPoint.hpg / 2;
            int r = needHp ? 1 : needKi ? 60 : 100;
            if (r <= 50) {
                int add = point * 20;
                if (this.nPoint.hpg + add > this.nPoint.getHpMpLimit()
                        || !doUseTiemNang((long) point * (2L * (this.nPoint.hpg + 1000L) + add - 20) / 2)) {
                    break;
                }
                this.nPoint.hpg += add;
            } else if (r <= 65) {
                int add = point * 20;
                if (this.nPoint.mpg + add > this.nPoint.getHpMpLimit()
                        || !doUseTiemNang((long) point * (2L * (this.nPoint.mpg + 1000L) + add - 20) / 2)) {
                    break;
                }
                this.nPoint.mpg += add;
            } else {
                if (this.nPoint.dameg + point > this.nPoint.getDameLimit()
                        || !doUseTiemNang((long) point * (2L * this.nPoint.dameg + point - 1) / 2 * 100)) {
                    break;
                }
                this.nPoint.dameg += point;
            }
            changed = true;
        }
        if (changed) {
            this.nPoint.calPoint(); // HP / KI / sức đánh tối đa tính lại từ chỉ số gốc (trước đây cộng thẳng hpMax, trả giá 2.000 HP chỉ được 100)
        }
        if (changed) {
            Service.gI().point(this);
            nro.models.services.TaskService.gI().checkDoneTaskUseTiemNang(this); // nhiệm vụ "Sử dụng tiềm năng"
        }
    }

    private boolean doUseTiemNang(long tiemNang) {
        if (this.nPoint.tiemNang < tiemNang) {
            return false;
        } else {
            this.nPoint.tiemNang -= tiemNang;
            return true;
        }
    }

    public void useSkill(int skillId) {
        new Thread(() -> {
            switch (skillId) {
                case Skill.BIEN_KHI:
                    EffectSkillService.gI().sendEffectMonkey(this);
                    EffectSkillService.gI().setIsMonkey(this);
                    EffectSkillService.gI().sendEffectMonkey(this);

                    Service.gI().sendSpeedPlayer(this, 0);
                    Service.gI().Send_Caitrang(this);
                    Service.gI().sendSpeedPlayer(this, -1);
                    PlayerService.gI().sendInfoHpMp(this);
                    Service.gI().point(this);
                    Service.gI().Send_Info_NV(this);
                    Service.gI().sendInfoPlayerEatPea(this);
                    break;
                case Skill.QUA_CAU_KENH_KHI:
                    this.playerSkill.prepareQCKK = !this.playerSkill.prepareQCKK;
                    this.playerSkill.lastTimePrepareQCKK = System.currentTimeMillis();
                    SkillService.gI().sendPlayerPrepareSkill(this, 1000);
                    break;
                case Skill.MAKANKOSAPPO:
                    this.playerSkill.prepareLaze = !this.playerSkill.prepareLaze;
                    this.playerSkill.lastTimePrepareLaze = System.currentTimeMillis();
                    SkillService.gI().sendPlayerPrepareSkill(this, 3000);
                    break;
            }
        }).start();
    }

    public String getChat() {
        return BotPemQuai.chatMessages[new Random().nextInt(BotPemQuai.chatMessages.length)];
    }

    /** Đổi hoạt động: 0 luyện tập (đánh quái), 1 bán đồ, 2 săn boss. */
    public void setMode(int type) {
        this.type = type;
    }

    public int getMode() {
        return this.type;
    }

    /** bot của BotWorld (tự duy trì, lưu tiến độ) */
    public boolean worldBot;
    /** tài khoản thật do admin đặt làm bot: nhân vật thật, chơi và nhận thưởng như người chơi */
    public boolean accountBot;
    public int accountId;
    public long sessionEnd; // hết ca chơi thì thoát game như người thật (BotWorld.syncAccounts)
    long lastReply;
    /** hoạt động đang làm (hiện trên panel) */
    public String activity = "Luyện tập";
    /** đang đi vắng (doanh trại / ĐHVT) tới lúc này */
    public long awayUntil;
    /** bot đang PK (chỉ đánh bot khác) */
    public Bot pkTarget;
    public long pkUntil;

}
