package nro.models.player;

import java.util.ArrayList;
import java.util.List;
import nro.models.consts.ConstTaskBadges;
import nro.models.matches.dai_hoi_vo_thuat.SuperRankService;
import nro.models.services.Service;
import nro.models.services.InventoryService;
import nro.models.services.ItemService;
import nro.models.services.Event2026Service;
import nro.models.map.service.NpcService;
import nro.models.task.BadgesTaskService;

/**
 *
 * @author Việt
 * 
 */

public class SuperRank {

    private Player player;
    public int rank;
    public int win;
    public int lose;
    public List<String> history;
    public List<Long> lastTime;
    public long lastPKTime;
    public long lastRewardTime;
    public int ticket = 3;

    public SuperRank(Player player) {
        this.player = player;
        this.history = new ArrayList<>();
        this.lastTime = new ArrayList<>();
    }

    public void history(String text, long lastTime) {
        while (this.history.size() >= 5) { // giữ 5 trận gần nhất, không phình mãi trong DB
            this.history.remove(0);
            this.lastTime.remove(0);
        }
        this.history.add(text);
        this.lastTime.add(lastTime);
    }

    public void reward() {
        if (rank == 1) {
            BadgesTaskService.updateCountBagesTask(player, ConstTaskBadges.CAO_THU_SIEU_HANG, 1);
        }

        int[] rw = SuperRankService.gI().reward(rank);
        if (rw != null) {
            player.inventory.ruby += rw[0];
            String text = "Bạn đang ở TOP " + rank + " Siêu Hạng, nhận được " + rw[0] + " hồng ngọc";
            if (rw[1] > 0) {
                give(ItemService.gI().createNewItem((short) 1924, rw[1])); // Rương Siêu Hạng
                text += ", " + rw[1] + " Rương Siêu Hạng";
            }
            if (rank == 1) {
                give(Event2026Service.gI().createItem(1293, 1, 3)); // Danh hiệu Cao thủ siêu hạng 3 ngày
                text += ", Danh hiệu Cao thủ siêu hạng 3 ngày";
            }
            InventoryService.gI().sendItemBags(player);
            Service.gI().sendMoney(player);
            Service.gI().sendThongBao(player, text);
        }

        lastRewardTime = System.currentTimeMillis();
    }

    /** Hành trang đầy thì cất vào rương đồ, không mất quà. */
    private void give(nro.models.item.Item it) {
        if (!InventoryService.gI().addItemBag(player, it)) {
            InventoryService.gI().addItemBox(player, it);
        }
    }

    public void dispose() {
        history.clear();
        lastTime.clear();
        win = -1;
        lose = -1;
        lastPKTime = -1;
        player = null;
    }
}
