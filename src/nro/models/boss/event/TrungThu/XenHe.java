package nro.models.boss.event.TrungThu;

import nro.models.boss.Boss;
import nro.models.boss.BossID;
import nro.models.boss.BossesData;
import static nro.models.consts.BossType.TRUNGTHU_EVENT;
import nro.models.player.Player;
import nro.models.services.TaskService;

/**
 * Sự kiện hè 3: Xên Hoàn Thiện ở map Mùa hè (vào bằng Rađa dò vỏ Xên), hạ xong 1 tiếng sau xuất hiện lại.
 * Rơi vỏ Xên bọ hung và có tỉ lệ set kích hoạt Gohan (SeasonEventService).
 */
public class XenHe extends Boss {

    public XenHe() throws Exception {
        super(TRUNGTHU_EVENT, BossID.XEN_HE, BossesData.XEN_HE);
    }

    @Override
    public void reward(Player plKill) {
        TaskService.gI().checkDoneTaskKillBoss(plKill, this);
    }
}
