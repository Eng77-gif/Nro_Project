package nro.models.player;

import nro.models.consts.ConstPlayer;
import lombok.Setter;
import nro.models.utils.Util;

/**
 *
 * @author Việt
 * 
 */

public class Fusion {

    public static final int TIME_FUSION = 600000;

    @Setter
    private Player player;
    public byte typeFusion;
    public long lastTimeFusion;
    public Fusion(Player player) {
        this.player = player;
    }

    private long lastCheck;

    public void update() {
        if (typeFusion == ConstPlayer.NON_FUSION || player == null) {
            return;
        }
        if (player.pet == null) { // đệ đã bị xóa / hợp thể vĩnh viễn: bỏ trạng thái hợp thể
            typeFusion = ConstPlayer.NON_FUSION;
            return;
        }
        if (typeFusion == ConstPlayer.LUONG_LONG_NHAT_THE && Util.canDoWithTime(lastTimeFusion, TIME_FUSION)) {
            this.player.pet.unFusion();
            return;
        }
        // hợp thể bông tai: bông tai không còn trong hành trang (bán, cất rương, giao dịch) thì tách ra
        if (Util.canDoWithTime(lastCheck, 3000)) {
            lastCheck = System.currentTimeMillis();
            int need = typeFusion == ConstPlayer.HOP_THE_PORATA ? 454 : typeFusion == ConstPlayer.HOP_THE_PORATA2 ? 921
                    : typeFusion == ConstPlayer.HOP_THE_PORATA3 ? 1884 : -1;
            if (need > 0 && player.inventory != null && player.inventory.itemsBag.stream()
                    .noneMatch(it -> it != null && it.isNotNullItem() && it.template.id == need)) {
                this.player.pet.unFusion();
                nro.models.services.Service.gI().sendThongBao(player, "Bông tai không còn trong hành trang, đã tách hợp thể");
            }
        }
    }
    
    public void dispose(){
        this.player = null;
    }

}
