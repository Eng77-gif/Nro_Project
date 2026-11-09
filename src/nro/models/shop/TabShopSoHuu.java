package nro.models.shop;

import java.util.ArrayList;
import nro.models.item.Item;
import nro.models.player.Player;
import nro.models.player_badges.BadgesData;
import nro.models.player_badges.BagesTemplate;
import nro.models.server.Manager;
import nro.models.services.ItemService;
import nro.models.task.BadgesTaskService;

/**
 * Tab "Sở hữu" của Santa: các danh hiệu người chơi đang có (bấm để đổi sang dùng).
 * Trước đây chỉ hiện danh hiệu nằm sẵn trong tab 45 của database, nên danh hiệu mới (đua top, sự kiện...)
 * vẫn được đếm ở tên tab ("Sở hữu 1") nhưng bấm xem thì trống. Giờ dựng thẳng từ danh hiệu người chơi có.
 *
 * @author Việt
 */
public class TabShopSoHuu extends TabShop {

    public TabShopSoHuu(TabShop tabShop, Player player) {
        this.itemShops = new ArrayList<>();
        this.shop = tabShop.shop;
        this.id = tabShop.id;
        java.util.Set<Integer> done = new java.util.HashSet<>();
        for (BadgesData data : player.dataBadges) {
            for (BagesTemplate temp : Manager.BAGES_TEMPLATES) {
                if (temp.idEffect != data.idBadGes || !done.add(temp.idItem)) {
                    continue;
                }
                ItemShop base = null;
                for (ItemShop is : tabShop.itemShops) {
                    if (is.temp != null && is.temp.id == temp.idItem) {
                        base = is;
                        break;
                    }
                }
                ItemShop it;
                if (base != null) {
                    it = new ItemShop(base);
                } else {
                    it = new ItemShop();
                    it.tabShop = this;
                    it.id = temp.idItem;
                    it.temp = ItemService.gI().getTemplate(temp.idItem);
                    it.typeSell = 0;
                    it.cost = 0;
                    if (it.temp == null) {
                        continue;
                    }
                    it.options.addAll(Item.ItemOption.copyAll(temp.options));
                }
                it.tabShop = this;
                it.isNew = false;
                it.typeSell = 1; // hiện giá đổi danh hiệu: 2 ngọc (ShopService.changeDanhHieu trừ)
                it.cost = 2;
                int days = BadgesTaskService.sendDay(player, temp.idEffect);
                it.options.removeIf(o -> o == null || o.optionTemplate == null || o.optionTemplate.id == 93);
                if (days > 0 && days < 3650) {
                    it.options.add(new Item.ItemOption(93, days));
                }
                this.itemShops.add(it);
            }
        }
        this.name = tabShop.name + this.itemShops.size();
    }
}
