package nro.models.daily_Giftcode;

import nro.models.player.Player;

public class DailyGiftService {

    public static boolean checkDailyGift(Player player, byte id) {
        for (DailyGiftData data : player.dailyGiftData) {
            if (data.id == id && !data.daNhan) {
                return true;
            }
        }
        return false;
    }

    public static void updateDailyGift(Player player, byte id) {
        for (DailyGiftData data : player.dailyGiftData) {
            if (data.id == id && !data.daNhan) {
                data.daNhan = true;
                break;
            }
        }
    }

    /** Như checkDailyGift; dữ liệu cũ chưa có mục id này thì thêm vào (chưa nhận). */
    public static boolean checkOrAdd(Player player, byte id) {
        for (DailyGiftData data : player.dailyGiftData) {
            if (data.id == id) {
                return !data.daNhan;
            }
        }
        DailyGiftData data = new DailyGiftData();
        data.id = id;
        data.daNhan = false;
        player.dailyGiftData.add(data);
        return true;
    }

    public static void addAndReset(Player player) {
        if (player.dailyGiftData != null) {
            player.dailyGiftData.clear();
        }
        for (byte i = 0; i < 3; i++) {
            DailyGiftData data = new DailyGiftData();
            data.id = i;
            data.daNhan = false;
            player.dailyGiftData.add(data);
        }
    }

}
