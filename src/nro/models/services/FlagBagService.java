package nro.models.services;

import nro.models.player_system.Template.FlagBag;
import java.util.List;
import nro.models.player.Player;
import nro.models.server.Manager;
import nro.models.network.Message;
import java.util.ArrayList;

/**
 *
 * @author Việt
 * 
 */

public class FlagBagService {

    private final List<FlagBag> flagClan = new ArrayList<>();
    private static FlagBagService i;

    public static FlagBagService gI() {
        if (i == null) {
            i = new FlagBagService();
        }
        return i;
    }

    public void sendIconFlagChoose(Player player, int id) {
        FlagBag fb = getFlagBag(id);
        if (fb != null) {
            Message msg;
            try {
                msg = new Message(-62);
                msg.writer().writeByte(fb.id);
                msg.writer().writeByte(fb.iconEffect.length + 1);
                msg.writer().writeShort(fb.iconId);
                for (Short iconId : fb.iconEffect) {
                    msg.writer().writeShort(iconId);
                }
                player.sendMessage(msg);
                msg.cleanup();
            } catch (Exception e) {
            }
        }
    }

    /** Cờ đeo lưng thiếu trong flag_bag (mã cờ lấy từ part của vật phẩm loại 11): dùng icon vật phẩm làm hình. */
    private final java.util.Map<Integer, FlagBag> fallback = new java.util.concurrent.ConcurrentHashMap<>();

    private FlagBag fallbackFlag(int id) {
        return fallback.computeIfAbsent(id, k -> {
            if (k >= 201 && k <= 254) { // đeo lưng hiệu ứng: client vẽ bằng DataEffect cùng mã, không vẽ thêm icon tĩnh
                FlagBag f = new FlagBag();
                f.id = k;
                f.name = "effect";
                f.iconEffect = new short[]{-1};
                return f;
            }
            for (nro.models.player_system.Template.ItemTemplate t : Manager.ITEM_TEMPLATES) {
                if (t != null && t.type == 11 && t.part == k) {
                    FlagBag f = new FlagBag();
                    f.id = k;
                    f.name = t.name;
                    f.iconId = t.iconID;
                    f.iconEffect = new short[]{t.iconID};
                    nro.models.utils.Logger.error("Cờ đeo lưng " + k + " (" + t.name + ") chưa có trong flag_bag, tạm dùng icon vật phẩm\n");
                    return f;
                }
            }
            return null;
        });
    }

    public void sendIconEffectFlag(Player player, int id) {
        FlagBag fb = getFlagBag(id);
        if (fb == null && id > 0) {
            fb = fallbackFlag(id);
        }
        if (fb != null) {
            Message msg;
            try {
                msg = new Message(-63);
                msg.writeDual(fb.id, 1, fb.id, 2); // J2ME đọc short
                msg.writer().writeByte(fb.iconEffect.length);
                for (Short iconId : fb.iconEffect) {
                    msg.writer().writeShort(iconId);
                }
                player.sendMessage(msg);
                msg.cleanup();
            } catch (Exception e) {
            }
        }
    }

    public void sendListFlagClan(Player pl) {
        List<FlagBag> list = getFlagsForChooseClan();
        Message msg;
        try {
            msg = new Message(-46);
            msg.writer().writeByte(1); //type
            msg.writeDual(list.size(), 1, list.size(), 2); // J2ME đọc short
            for (FlagBag fb : list) {
                msg.writeDual(fb.id, 1, fb.id, 2);
                msg.writer().writeUTF(fb.name);
                msg.writer().writeInt(fb.gold);
                msg.writer().writeInt(fb.gem);
            }
            pl.sendMessage(msg);
            msg.cleanup();
        } catch (Exception e) {
        }
    }

    public FlagBag getFlagBag(int id) {
        for (FlagBag fb : Manager.FLAGS_BAGS) {
            if (fb.id == id) {
                return fb;
            }
        }
        return null;
    }

    public List<FlagBag> getFlagsForChooseClan() {
        if (flagClan.isEmpty()) {
            int[] flagsId = {0, 8, 7, 6, 5, 4, 3, 2, 1, 18, 17, 16, 15, 14, 13,
                12, 11, 10, 9, 27, 26, 25, 24, 23, 36, 32, 33, 34, 35, 19, 22, 21, 20, 29, 37, 38,
                69, 70, 71, 77, 78, 79
            };
            for (int i = 0; i < flagsId.length; i++) {
                flagClan.add(getFlagBag(flagsId[i]));
            }
        }
        return flagClan;
    }
}
