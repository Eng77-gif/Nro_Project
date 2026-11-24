package nro.models.matches.dai_hoi_vo_thuat;
import nro.models.consts.ConstSuperRank;
import java.util.List;
import nro.models.consts.ConstTaskBadges;
import nro.models.database.VietLoader;
import nro.models.database.SuperRankDAO;
import nro.models.managers.SuperRankManager;
import nro.models.matches.dai_hoi_vo_thuat.SuperRank;
import nro.models.matches.dai_hoi_vo_thuat.SuperRankBuilder;
import nro.models.map.Map;
import nro.models.map.Zone;
import nro.models.network.Message;
import nro.models.player.Player;
import nro.models.server.Client;
import nro.models.map.service.MapService;
import nro.models.services.Service;
import nro.models.task.BadgesTaskService;
import nro.models.utils.Logger;
import nro.models.utils.TimeUtil;
import nro.models.utils.Util;

public class SuperRankService {

    private static SuperRankService instance;

    public static SuperRankService gI() {
        if (instance == null) {
            instance = new SuperRankService();
        }
        return instance;
    }

    public void competing(Player player, long id) {
        if (player.zone.map.mapId != 113 || id == -1) {
            return;
        }
        int menuType = player.idMark.getMenuType();
        Player pl = loadPlayer(id);
        if (pl == null) {
            return;
        }
        if (SuperRankManager.gI().currentlyCompeting(player.id)) {
            Service.gI().sendThongBao(player, ConstSuperRank.TEXT_DANG_THI_DAU);
            return;
        } else if (SuperRankManager.gI().currentlyCompeting(pl.id)) {
            Service.gI().sendThongBao(player, ConstSuperRank.TEXT_DOI_THU_DANG_THI_DAU);
            return;
        } else if (SuperRankManager.gI().awaitingCompetition(player.id)) {
            Service.gI().sendThongBao(player, ConstSuperRank.TEXT_DANG_CHO);
            return;
        } else if (SuperRankManager.gI().awaitingCompetition(pl.id)) {
            Service.gI().sendThongBao(player, ConstSuperRank.TEXT_DOI_THU_CHO_THI_DAU);
            return;
        } else if (player.superRank.rank < pl.superRank.rank) {
            Service.gI().sendThongBao(player, ConstSuperRank.TEXT_DUOI_HANG);
            return;
        } else if (player.superRank.rank == pl.superRank.rank || pl.id == player.id) {
            Service.gI().sendThongBao(player, ConstSuperRank.TEXT_CHINH_MINH);
            return;
        } else if (pl.superRank.rank < 10 && player.superRank.rank - pl.superRank.rank > 2) {
            Service.gI().sendThongBao(player, ConstSuperRank.TEXT_KHONG_THE_THI_DAU_TREN_2_HANG);
            return;
        } else if (player.superRank.ticket <= 0 && player.inventory.getGem() < 1 ) {
            Service.gI().sendThongBao(player, "Bạn không đủ ngọc, còn thiếu 1 ngọc nữa");
            return;
        }
        SuperRankDAO.loadData(player);
        switch (menuType) {
            case 0 -> {
                Service.gI().sendThongBao(player, ConstSuperRank.TEXT_TOP_100);
            }
            case 1 -> {
                if (SuperRankManager.gI().SPRCheck(player.zone)) {
                    Service.gI().sendThongBao(player, ConstSuperRank.TEXT_CHO_IT_PHUT);
                    SuperRankManager.gI().addWSPR(player.id, pl.id);
                } else {
                    SuperRankManager.gI().addSPR(new SuperRank(player, id, player.zone));
                }
            }
            case 2 -> {
                SuperRankManager.gI().addSPR(new SuperRank(player, id, getZone(113)));
            }
        }
    }

    public void topList(Player player, int type) {
        long st = System.currentTimeMillis();
        player.idMark.setMenuType(type);
        Message msg = null;
        try {
            List<SuperRankBuilder> list = type == 0 ? SuperRankDAO.getPlayerListInRank(player.superRank.rank, 100) : SuperRankDAO.getPlayerListInRankRange(player.superRank.rank, 11);
            msg = new Message(-96);
            msg.writer().writeByte(0);
            msg.writer().writeUTF(type == 0 ? "Top 100 Cao Thủ" : "Giải siêu hạng"); // danh sách thách đấu (Trọng tài) không phải bảng Top 100
            msg.writer().writeByte(list.size());
            for (int i = 0; i < list.size(); i++) {
                SuperRankBuilder sb = list.get(i);
                msg.writer().writeInt(sb.getRank());
                msg.writer().writeInt((int) sb.getId());
                msg.writer().writeShort(sb.getHead());
                if (214 < player.getSession().version) {
                    msg.writer().writeShort(-1);
                }
                msg.writer().writeShort(sb.getBody());
                msg.writer().writeShort(sb.getLeg());
                msg.writer().writeUTF(sb.getName());
                msg.writer().writeUTF(textStatus(sb));
                msg.writer().writeUTF(sb.getInfo());
            }
            player.sendMessage(msg);
            msg.cleanup();
            for (SuperRankBuilder sb : list) {
                sb.dispose();
            }
            list.clear();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (msg != null) {
                msg.cleanup();
            }
        }
    }


    public Player loadPlayer(long id) {
        Player pl = VietLoader.loadById(id);
        if (pl != null) {
            pl.setClothes.setup();
            if (pl.pet != null) {
                pl.pet.setClothes.setup();
            }
            pl.nPoint.calPoint();
        }
        return pl;
    }

    /** Bản đang online (người chơi hoặc tài khoản bot) nếu có, không thì đọc từ DB. */
    public Player onlineOrLoad(long id) {
        Player p = getPlayer(id);
        if (p == null) {
            for (nro.models.Bot.Bot b : nro.models.Bot.BotWorld.gI().bots()) {
                if (b.id == id && b.superRank != null) {
                    p = b;
                    break;
                }
            }
        }
        return p != null && p.superRank != null ? p : loadPlayer(id);
    }

    public Player getPlayer(long id) {
        return Client.gI().getPlayer(id);
    }

    public String textInfo(Player pl) {
        return textInfoNew(pl);
    }

    /** Như bản chính thức: HP, sức đánh, giáp, "Win x Lose y", 5 trận gần nhất (mới nhất trước). */
    public String textInfoNew(Player pl) {
        if (pl == null || pl.nPoint == null) {
            return "Không xác định!";
        }
        pl.setClothes.setup();
        if (pl.pet != null) {
            pl.pet.setClothes.setup();
        }
        pl.nPoint.calPoint();
        StringBuilder text = new StringBuilder("HP " + Util.formatNumber(pl.nPoint.hpMax) + "\n");
        text.append("Sức đánh ").append(Util.formatNumber(pl.nPoint.dame)).append("\n");
        text.append("Giáp ").append(Util.formatNumber(pl.nPoint.def)).append("\n");
        text.append("Win ").append(pl.superRank.win).append(" Lose ").append(pl.superRank.lose);
        for (int i = pl.superRank.history.size() - 1, n = 0; i >= 0 && n < 5; i--, n++) {
            text.append("\n").append(pl.superRank.history.get(i).replaceAll("\\[\\d+\\]", "")).append(" ")
                    .append(TimeUtil.getTimeLeft(pl.superRank.lastTime.get(i)));
        }
        return text.toString();
    }

    public String textStatus(SuperRankBuilder srb) {
        if (SuperRankManager.gI().awaitingCompetition(srb.getId())) {
            return ConstSuperRank.TEXT_DANG_CHO;
        } else if (SuperRankManager.gI().currentlyCompeting(srb.getId())) {
            return SuperRankManager.gI().getCompeting(srb.getId());
        }
        return textReward(srb.getRank());
    }

    /** Thưởng ngày theo bài diễn đàn "Điều chỉnh phần thưởng giải Siêu hạng": {hồng ngọc, số Rương Siêu Hạng}. */
    public int[] reward(int rank) {
        if (rank == 1) return new int[]{200, 10};
        if (rank == 2) return new int[]{100, 5};
        if (rank == 3) return new int[]{50, 2};
        if (rank >= 4 && rank <= 10) return new int[]{20, 1};
        if (rank >= 11 && rank <= 20) return new int[]{10, 0};
        if (rank >= 21 && rank <= 100) return new int[]{5, 0};
        if (rank >= 101 && rank <= 1000) return new int[]{2, 0};
        return null;
    }

    public String textReward(int rank) {
        int[] rw = reward(rank);
        if (rw == null) return "";
        return "+" + rw[0] + " hồng ngọc" + (rw[1] > 0 ? ", " + rw[1] + " rương" : "") + (rank == 1 ? ", danh hiệu" : "") + "/ ngày";
    }

    public Zone getZone(int mapId) {
        Map map = MapService.gI().getMapById(mapId);
        try {
            if (map != null) {
                int zoneId = 0;
                while (zoneId < map.zones.size()) {
                    Zone zonez = map.zones.get(zoneId);
                    if (!SuperRankManager.gI().SPRCheck(zonez)) {
                        return zonez;
                    }
                    zoneId++;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
