package nro.models.task;

import nro.models.task.BadgesTask;
import nro.models.player.Player;
import nro.models.player_badges.BadgesData;
import nro.models.server.Manager;
import nro.models.task.BadgesTaskTemplate;

/**
 *
 * @author Việt
 * 
 */

public class BadgesTaskService {

    public static void createAndResetTask(Player player) {
        player.dataTaskBadges.clear();
        for (BadgesTaskTemplate BTT : Manager.TASKS_BADGES_TEMPLATE) {
            BadgesTask data = new BadgesTask();
            data.id = BTT.id;
            data.count = 0;
            data.countMax = BTT.count;
            data.idBadgesReward = BTT.idbadgesReward;
            player.dataTaskBadges.add(data);
        }
    }

    /** Nhiệm vụ đã lưu của người chơi theo đúng mẫu hiện tại (số lần cần, danh hiệu thưởng), thêm nhiệm vụ mới. */
    public static void syncWithTemplate(Player player) {
        for (BadgesTaskTemplate t : Manager.TASKS_BADGES_TEMPLATE) {
            BadgesTask found = null;
            for (BadgesTask d : player.dataTaskBadges) {
                if (d.id == t.id) {
                    found = d;
                    break;
                }
            }
            if (found == null) {
                found = new BadgesTask();
                found.id = t.id;
                player.dataTaskBadges.add(found);
            }
            found.countMax = t.count;
            found.idBadgesReward = t.idbadgesReward;
            found.count = Math.min(found.count, found.countMax);
        }
    }

    public static void updateDoneTask(Player player) {
        for (BadgesTask data : player.dataTaskBadges) {
            if (data.isDone()) {
                for (BadgesData bg : player.dataBadges) {
                    if (bg.idBadGes == data.idBadgesReward) {
                        return;
                    }
                }
                BadgesData danhHieu = new BadgesData(player, data.idBadgesReward, 30);
                player.dataBadges.add(danhHieu);
                data.count = 0;
            }
        }
    }

    public static void updateCountBagesTask(Player player, int id, int amount) {
        for (BadgesTask data : player.dataTaskBadges) {
            if (data.id == id) {
                data.count += amount;
                if (data.count > data.countMax) {
                    data.count = data.countMax;
                }
                break;
            }
        }
    }

    /** Danh hiệu này có nhiệm vụ mở khóa (task_badges_template) không. */
    public static boolean hasTask(int idBadgesReward) {
        for (BadgesTaskTemplate t : Manager.TASKS_BADGES_TEMPLATE) {
            if (t.idbadgesReward == idBadgesReward) {
                return true;
            }
        }
        return false;
    }

    public static int sendPercenBadgesTask(Player player, int idBadgesReward) {
        for (BadgesTask data : player.dataTaskBadges) {
            if (data.idBadgesReward == idBadgesReward) {
                if (data.getPercentProcess() > 0) {
                    return data.getPercentProcess();
                } else {
                    return 0;
                }
            }
        }
        return 0;
    }

    public static int sendDay(Player player, int id) {
        for (BadgesData data : player.dataBadges) {
            if (data.idBadGes == id) {
                long timeDifference = data.timeofUseBadges - System.currentTimeMillis();
                return (int) (timeDifference / (24 * 60 * 60 * 1000L));
            }
        }
        return 0;
    }

}
