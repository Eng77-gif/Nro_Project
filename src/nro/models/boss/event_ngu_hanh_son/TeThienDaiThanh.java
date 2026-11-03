package nro.models.boss.event_ngu_hanh_son;

import nro.models.boss.Boss;
import nro.models.boss.BossData;
import nro.models.boss.BossID;
import nro.models.consts.ConstPlayer;
import nro.models.player.Player;
import nro.models.skill.Skill;
import nro.models.utils.Util;

import static nro.models.consts.BossType.TRUNGTHU_EVENT;

/**
 * Sự kiện Ngũ Hành Sơn: Tề Thiên Đại Thánh thoát khỏi núi Ngũ Hành, xuất hiện ở các bản đồ núi đồi.
 * Quà rơi khai báo trong season_event_drop (nguồn boss, mobs = -378): Vòng Kim Cô, Quả Hồng Đào Chín.
 *
 * @author Việt
 */
public class TeThienDaiThanh extends Boss {

    public static final BossData DATA = new BossData(
            "Tề Thiên Đại Thánh", //name
            ConstPlayer.XAYDA, //gender
            new short[]{198, 193, 194, -1, -1, -1}, //outfit: dáng khỉ
            150_000, //dame
            new int[]{500_000_000}, //hp
            new int[]{1, 2, 3, 8, 9, 11, 15, 16, 17}, //map: đồi núi Trái Đất, Namếc, Xayda
            new int[][]{
                {Skill.GALICK, 7, 1000},
                {Skill.KAMEJOKO, 7, 3000},
                {Skill.THAI_DUONG_HA_SAN, 3, 20000},
                {Skill.BIEN_KHI, 7, 60000},},
            new String[]{"|-1|Lão Tôn ta bị nhốt dưới núi Ngũ Hành 500 năm rồi!"},
            new String[]{"|-1|Ăn một gậy của lão Tôn đây!", "|-1|Bọn ngươi muốn lấy Vòng Kim Cô à?"},
            new String[]{"|-1|Hừ, lão Tôn về Hoa Quả Sơn đây..."},
            900 // nghỉ 15 phút rồi xuất hiện lại
    );

    public TeThienDaiThanh() throws Exception {
        super(TRUNGTHU_EVENT, BossID.TE_THIEN_DAI_THANH, true, true, DATA);
    }

    @Override
    public void reward(Player plKill) {
        // quà rơi theo bảng season_event_drop của sự kiện Ngũ Hành Sơn
    }

    @Override
    public void joinMap() {
        super.joinMap();
        st = System.currentTimeMillis();
    }

    private long st;

    @Override
    public void autoLeaveMap() {
        if (Util.canDoWithTime(st, 900_000)) {
            this.leaveMapNew();
        }
        if (this.zone != null && this.zone.getNumOfPlayers() > 0) {
            st = System.currentTimeMillis();
        }
    }
}
