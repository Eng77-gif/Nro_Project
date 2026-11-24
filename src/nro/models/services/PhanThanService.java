package nro.models.services;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import nro.models.item.Item;
import nro.models.map.Zone;
import nro.models.mob.Mob;
import nro.models.player.Player;
import nro.models.network.Message;
import nro.models.utils.Logger;
import nro.models.utils.Util;

/**
 * Option 277 "Tạo ra phân thân gây mù đối phương CD # giây" (Cải trang Thiên sư phụ): khi ra đòn mà hết hồi chiêu,
 * một phân thân giống hệt người mặc hiện cạnh mục tiêu, làm mù đối phương (người chơi đánh được + quái) quanh phân thân,
 * phân thân tự biến mất sau vài giây. Phân thân chỉ là hình ảnh (không đánh, không bị đánh).
 */
public final class PhanThanService {

    public static final int OPTION = 277;
    private static final int TIME_BLIND = 3000, TIME_LIVE = 5000, RANGE = 200;
    private static final Map<Long, Long> LAST = new ConcurrentHashMap<>();

    private PhanThanService() {
    }

    public static void onAttack(Player pl, Player plTarget, Mob mobTarget) {
        try {
            if (pl == null || pl.isBoss || pl.isPet || pl.isNewPet || pl.zone == null || pl.isDie() || pl.inventory == null
                    || pl.inventory.itemsBody.size() <= 5 || (plTarget == null && mobTarget == null)) {
                return;
            }
            Item ct = pl.inventory.itemsBody.get(5);
            Item.ItemOption o = ct != null && ct.isNotNullItem() ? ct.getOptionById(OPTION) : null;
            if (o == null) {
                return;
            }
            long now = System.currentTimeMillis();
            Long last = LAST.get(pl.id);
            if (last != null && now - last < o.param * 1000L) {
                return;
            }
            LAST.put(pl.id, now);
            Zone zone = pl.zone;
            int tx = plTarget != null ? plTarget.location.x : mobTarget.location.x;
            int ty = plTarget != null ? plTarget.location.y : mobTarget.location.y;
            int x = tx + (tx >= pl.location.x ? 40 : -40); // phân thân đứng phía sau mục tiêu
            int cloneId = -(1_500_000_000 + (int) (Math.abs(pl.id) % 500_000_000));
            sendClone(zone, pl, cloneId, x, ty);
            for (Player p : new ArrayList<>(zone.players)) {
                if (p != null && p != pl && p.nPoint != null && !p.nPoint.khangTDHS && !p.isDie()
                        && Math.abs(p.location.x - x) <= RANGE && Math.abs(p.location.y - ty) <= RANGE
                        && SkillService.gI().canAttackPlayer(pl, p)) {
                    Service.gI().chat(p, "Mù mắt rồi");
                    EffectSkillService.gI().startStun(p, now, TIME_BLIND);
                }
            }
            for (Mob m : new ArrayList<>(zone.mobs)) {
                if (m != null && !m.isDie() && Math.abs(m.location.x - x) <= RANGE && Math.abs(m.location.y - ty) <= RANGE) {
                    m.effectSkill.startStun(now, TIME_BLIND);
                }
            }
            Util.setTimeout(() -> removeClone(zone, cloneId), TIME_LIVE);
        } catch (Exception e) {
            Logger.logException(PhanThanService.class, e);
        }
    }

    /** Gửi một nhân vật giả (cùng đầu / thân / chân / tên với người mặc) cho mọi người trong khu. */
    private static void sendClone(Zone zone, Player pl, int cloneId, int x, int y) {
        Message msg = null;
        try {
            msg = new Message(-5);
            msg.writer().writeInt(cloneId);
            msg.writer().writeInt(-1);
            msg.writer().writeByte(Service.gI().getCurrLevel(pl));
            msg.writer().writeBoolean(false);
            msg.writer().writeByte(0); // typePk: không ai đánh được
            msg.writer().writeByte(pl.gender);
            msg.writer().writeByte(pl.gender);
            msg.writer().writeShort(pl.getHead());
            msg.writer().writeUTF(pl.name);
            msg.writeNum(pl.nPoint.hp);
            msg.writeNum(pl.nPoint.hpMax);
            msg.writer().writeShort(pl.getBody());
            msg.writer().writeShort(pl.getLeg());
            int flagbag = pl.getFlagBag();
            msg.writeDual(flagbag, 1, flagbag, 2); // túi/cờ: J2ME đọc short
            msg.writer().writeByte(-1);
            msg.writer().writeShort(x);
            msg.writer().writeShort(y);
            msg.writer().writeShort(0);
            msg.writer().writeShort(0);
            msg.writer().writeByte(0);
            msg.writer().writeByte(0);
            msg.writer().writeByte(0);
            msg.writer().writeShort(0);
            msg.writer().writeByte(0);
            msg.writer().writeByte(0);
            msg.writer().writeShort(pl.getAura());
            msg.writer().writeByte(pl.getEffFront());
            msg.writer().writeShort(pl.getHat());
            Service.gI().sendMessAllPlayerInMap(zone, msg);
        } catch (Exception e) {
            Logger.logException(PhanThanService.class, e);
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }

    private static void removeClone(Zone zone, int cloneId) {
        Message msg = null;
        try {
            msg = new Message(-6);
            msg.writer().writeInt(cloneId);
            Service.gI().sendMessAllPlayerInMap(zone, msg);
        } catch (Exception e) {
            Logger.logException(PhanThanService.class, e);
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }
}
