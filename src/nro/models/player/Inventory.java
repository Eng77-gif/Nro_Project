package nro.models.player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import nro.models.consts.ConstTaskBadges;
import nro.models.item.Item;
import nro.models.item.Item.ItemOption;
import nro.models.task.BadgesTaskService;

/**
 *
 * @author Việt
 *
 */
public class Inventory {

    public static final long LIMIT_GOLD = 999_999_999_999_999L; // client 2.4.7 hiện vàng 8 byte
    public static final int MAX_ITEMS_BAG = 90; // sự kiện hè 3: hành trang tối đa 90 ô
    public static final int MAX_ITEMS_BOX = 100;
    public Item trainArmor;
    public List<String> giftCode;
    public List<Item> itemsBody;
    public List<Item> itemsBag;
    public List<Item> itemsBox;

    public List<Item> itemsBoxCrackBall;
    public List<Item> itemsDaBan;

    public long gold;
    public long gem;
    public long ruby;
    public int coupon;
    public int event;
    public Iterable<Item> items;

    public Inventory() {
        itemsBody = new ArrayList<>();
        itemsBag = new ArrayList<>();
        itemsBox = new ArrayList<>();
        itemsBoxCrackBall = new ArrayList<>();
        itemsDaBan = new ArrayList<>();
        giftCode = new ArrayList<>();
    }

    public long getGem() {
        return this.gem;
    }

    public int getParam(Item it, int id) {
        for (ItemOption op : it.itemOptions) {
            if (op != null && op.optionTemplate.id == id) {
                return op.param;
            }
        }
        return 0;
    }

    public boolean haveOption(List<Item> l, int index, int id) {
        Item it = l.get(index);
        if (it != null && it.isNotNullItem()) {
            return it.itemOptions.stream().anyMatch(op -> op != null && op.optionTemplate.id == id);
        }
        return false;
    }

    public void subGem(long num) {
        this.gem -= num;
    }

    public void subGold(long num) {
        this.gold -= num;
    }

    private static void disposeList(List<Item> list) {
        if (list == null) {
            return;
        }
        for (Item it : list.toArray(new Item[0])) {
            if (it != null) {
                it.dispose();
            }
        }
        list.clear();
    }

    public void addGold(long gold) {
        this.gold += gold;
        if (this.gold > LIMIT_GOLD) {
            this.gold = LIMIT_GOLD;
        }
    }

    public void dispose() {
        if (this.trainArmor != null) {
            this.trainArmor.dispose();
        }
        this.trainArmor = null;
        // đối thủ ảo Siêu hạng dùng chung hành trang với bản sao người chơi: luồng trận và luồng boss có thể cùng đụng vào
        // (trước đây ConcurrentModificationException / NullPointerException tại đây). Duyệt bản chụp, bỏ ô null.
        disposeList(this.itemsBody);
        disposeList(this.itemsBag);
        disposeList(this.itemsBox);
        disposeList(this.itemsBoxCrackBall);
        disposeList(this.itemsDaBan);
        this.itemsBody = null;
        this.itemsBag = null;
        this.itemsBox = null;
        this.itemsBoxCrackBall = null;
        this.itemsDaBan = null;
    }

    public void checkAndUpdateMeRongBadges(Player player) {
        Set<Integer> checkedItemIds = new HashSet<>();

        List<List<Item>> inventories = Arrays.asList(
                this.itemsBag,
                this.itemsBox,
                this.itemsBody
        );

        for (List<Item> inventory : inventories) {
            for (Item item : inventory) {
                if (item != null && isPermanent(item)) {
                    int itemId = item.template.id;
                    if (((itemId >= 1814 && itemId <= 1819) || itemId == 1929) && !checkedItemIds.contains(itemId)) {
                        BadgesTaskService.updateCountBagesTask(player, ConstTaskBadges.ME_RONG, 1);
                        checkedItemIds.add(itemId);
                    }
                }
            }
        }
    }

    private boolean isPermanent(Item item) {
        return item != null && item.getOptionById(93) == null;
    }
}
