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

    private int[] TraiDat = new int[]{1, 2, 3, 4, 6, 29, 30, 28, 27, 42, 110, 103, 106, 105, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74 ,75 ,76, 77, 78, 79, 80, 81, 82, 83, 92, 93, 94, 95, 96, 97, 98, 99, 100, 107, 108, 109};
    private int[] Namec = new int[]{8, 9, 10, 11, 12, 13, 33, 34, 32, 31, 110, 103, 106, 105, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74 ,75 ,76, 77, 78, 79, 80, 81, 82, 83, 92, 93, 94, 95, 96, 97, 98, 99, 100, 107, 108, 109};
    private int[] XayDa = new int[]{15, 16, 17, 18, 19, 20, 37, 36, 35, 44, 52, 110, 106, 105, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74 ,75 ,76, 77, 78, 79, 80, 81, 82, 83, 92, 93, 94, 95, 96, 97, 98, 99, 100, 107, 108, 109};
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

    public int MapToPow() {
        if (this.questMap >= 0) {
            return this.questMap;
        }
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
            int[] home = this.gender == 0 ? new int[]{1, 2, 3, 4, 6, 29, 30, 28, 27, 42}
                    : this.gender == 1 ? new int[]{8, 9, 10, 11, 12, 13, 33, 34, 32, 31} : new int[]{15, 16, 17, 18, 19, 20, 37, 36, 35, 44, 52};
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

    /** Tài khoản bot chỉ vào map người chơi cùng nhiệm vụ vào được (luật ChangeMapService.checkMapCanJoin: Namếc, tương lai, Cold...). */
    public boolean canGo(int mapId) {
        if (!this.accountBot) {
            return true;
        }
        Map m = MapService.gI().getMapById(mapId);
        return m != null && !m.zones.isEmpty() && ChangeMapService.gI().checkMapCanJoin(this, m.zones.get(0)) != null;
    }

    public void joinMap() {
        int mapId = MapToPow();
        for (int t = 0; t < 8 && !canGo(mapId); t++) {
            mapId = this.questMap >= 0 ? -1 : MapToPow();
        }
        if (!canGo(mapId)) { // chưa tới nhiệm vụ mở map đó: luyện ở map đầu của hành tinh mình
            mapId = new int[]{1, 8, 15}[Math.max(0, Math.min(2, this.gender))];
            if (!canGo(mapId)) {
                return;
            }
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
        }
        if (this.isDie()) {
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
            long tiemNangUse;
            if (Util.isTrue(50, 100)) {
                int point = 100;
                int pointHp = point * 20;
                tiemNangUse = point * (2 * (this.nPoint.hpg + 1000) + pointHp - 20) / 2;
                if (!doUseTiemNang(tiemNangUse)) {
                    break;
                }
                this.nPoint.hpMax += point;
                this.nPoint.hpg += point;
            } else {
                int point = 10;
                tiemNangUse = (long) point * (2L * this.nPoint.dameg + point - 1) / 2 * 100;
                if (!doUseTiemNang(tiemNangUse)) {
                    break;
                }
                this.nPoint.dameg += point;
            }
            changed = true;
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
    /** hoạt động đang làm (hiện trên panel) */
    public String activity = "Luyện tập";
    /** đang đi vắng (doanh trại / ĐHVT) tới lúc này */
    public long awayUntil;
    /** bot đang PK (chỉ đánh bot khác) */
    public Bot pkTarget;
    public long pkUntil;

}
