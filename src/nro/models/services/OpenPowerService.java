package nro.models.services;

import nro.models.utils.Util;

import nro.models.player.NPoint;
import nro.models.player.Pet;
import nro.models.player.Player;

/**
 *
 * @author Việt
 * 
 */

public class OpenPowerService {

    public static final int COST_SPEED_OPEN_LIMIT_POWER = 50000000;

    private static OpenPowerService i;

    private OpenPowerService() {

    }

    public static OpenPowerService gI() {
        if (i == null) {
            i = new OpenPowerService();
        }
        return i;
    }

    /** Giá mở nhanh giới hạn sức mạnh (ngọc): dưới 50 tỷ sức mạnh 100 ngọc / lần, từ 50 tỷ tới tối đa 200 ngọc / lần. */
    public static int gemCost(Player target) {
        return target.nPoint.power < 50_000_000_000L ? 100 : 200;
    }

    /** Trả ngọc của payer để mở ngay 1 bậc giới hạn sức mạnh cho target (bản thân hoặc đệ tử). */
    public boolean openPowerSpeedByGem(Player payer, Player target) {
        int cost = gemCost(target);
        if (payer.inventory.gem < cost) {
            Service.gI().sendThongBao(payer, "Bạn không đủ ngọc để mở, còn thiếu " + Util.numberToMoney(cost - payer.inventory.gem) + " ngọc");
            return false;
        }
        if (!openPowerSpeed(target)) {
            return false;
        }
        payer.inventory.gem -= cost;
        target.itemTime.isOpenPower = false; // đang chờ mở miễn phí thì huỷ (đã mở ngay)
        Service.gI().sendMoney(payer);
        return true;
    }

    public boolean openPowerBasic(Player player) {
        byte curLimit = player.nPoint.limitPower;
        if (curLimit < NPoint.MAX_LIMIT) {
            if (!player.itemTime.isOpenPower && player.nPoint.canOpenPower()) {
                player.itemTime.isOpenPower = true;
                player.itemTime.lastTimeOpenPower = System.currentTimeMillis();
                ItemTimeService.gI().sendAllItemTime(player);
                return true;
            } else {
                Service.gI().sendThongBao(player, "Sức mạnh của bạn không đủ để thực hiện");
                return false;
            }
        } else {
            Service.gI().sendThongBao(player, "Sức mạnh của bạn đã đạt tới mức tối đa");
            return false;
        }
    }

    public boolean openPowerSpeed(Player player) {
        if (player.nPoint.limitPower < NPoint.MAX_LIMIT) {
            player.nPoint.limitPower++;
            if (!player.isPet) {
                Service.gI().sendThongBao(player, "Giới hạn sức mạnh của bạn đã được tăng lên 1 bậc");
            } else {
                Service.gI().sendThongBao(((Pet) player).master, "Giới hạn sức mạnh của đệ tử đã được tăng lên 1 bậc");
            }
            return true;
        } else {
            if (!player.isPet) {
                Service.gI().sendThongBao(player, "Sức mạnh của bạn đã đạt tới mức tối đa");
            } else {
                Service.gI().sendThongBao(((Pet) player).master, "Sức mạnh của đệ tử đã đạt tới mức tối đa");
            }
            return false;
        }
    }

}
