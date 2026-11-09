package nro.models.boss.Boss_mini;

import nro.models.boss.Boss;
import nro.models.boss.BossData;
import nro.models.boss.BossID;
import nro.models.consts.ConstPlayer;
import nro.models.map.ItemMap;
import nro.models.player.Player;
import nro.models.services.Service;
import nro.models.skill.Skill;
import nro.models.utils.Util;

/**
 * Chilled (tổ tiên của Frieza) xâm lược hành tinh thực vật quá khứ: bước "Hạ Chilled" của chuỗi nhiệm vụ Bardock (nhiệm vụ 29).
 * Hình và chiêu theo bản V10.
 */
public class Chilled extends Boss {

    public Chilled() throws Exception {
        super(BossID.CHILLED, new BossData(
                "Chilled",
                ConstPlayer.XAYDA,
                new short[]{1024, 1025, 1026, -1, -1, -1},
                30_000,
                new long[]{1_000_000_000},
                new int[]{160, 161, 162, 163}, // hành tinh thực vật quá khứ
                new int[][]{
                    {Skill.KAMEJOKO, 7, 5000},
                    {Skill.DRAGON, 7, 1000},
                    {Skill.MASENKO, 7, 2000},
                    {Skill.TAI_TAO_NANG_LUONG, 3, 60000}},
                new String[]{"|-1|Hành tinh này từ nay thuộc về ta", "|-1|Lũ khỉ Xayda các ngươi biết điều thì quỳ xuống"},
                new String[]{"|-1|Một tên Xayda dám chống lại ta sao?", "|-1|Ngươi là ai mà mạnh đến vậy?"},
                new String[]{"|-1|Mọi chuyện chưa kết thúc đâu"},
                300));
    }

    @Override
    public void reward(Player plKill) {
        int x = this.location.x;
        int y = this.zone.map.yPhysicInTop(x, this.location.y - 24);
        for (int i = Util.nextInt(2, 4); i > 0; i--) { // sao pha lê
            Service.gI().dropItemMap(zone, new ItemMap(zone, Util.nextInt(441, 447), 1, x + Util.nextInt(-50, 50), y, plKill.id));
        }
        if (Util.isTrue(20, 100)) { // ngọc rồng 1 - 3 sao
            Service.gI().dropItemMap(zone, new ItemMap(zone, Util.nextInt(14, 16), 1, x, y, plKill.id));
        }
    }
}
