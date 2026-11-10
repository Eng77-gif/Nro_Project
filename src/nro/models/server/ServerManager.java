package nro.models.server;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import nro.models.database.HistoryTransactionDAO;
import nro.models.boss.Boss_Manager.BossManager;
import nro.models.boss.Boss_Manager.OtherBossManager;
import nro.models.boss.Boss_Manager.TreasureUnderSeaManager;
import nro.models.boss.Boss_Manager.SnakeWayManager;
import nro.models.boss.Boss_Manager.RedRibbonHQManager;
import nro.models.boss.Boss_Manager.GasDestroyManager;
import nro.models.boss.Boss_Manager.YardartManager;
import nro.models.boss.Boss_Manager.SkillSummonedManager;
import nro.models.interfaces.ISession;
import nro.models.network.Network;
import nro.models.network.MyKeyHandler;
import nro.models.network.MySession;
import nro.models.services_dungeon.NgocRongNamecService;
import nro.models.utils.Logger;
import nro.models.utils.TimeUtil;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import nro.models.matches.giai_dau.The23rdMartialArtCongressManager;
import nro.models.matches.giai_dau.DeathOrAliveArenaManager;
import nro.models.matches.giai_dau.WorldMartialArtsTournamentManager;
import nro.models.network.MessageSendCollect;
import nro.models.managers.SuperRankManager;
import nro.models.interfaces.ISessionAcceptHandler;
import nro.models.boss.Boss_Manager.BrolyManager;
import nro.models.event.EventManager;
import nro.models.Bot.BotManager;
import nro.models.boss.Boss_Manager.FinalBossManager;
import nro.models.data.LocalManager;
import nro.models.managers.ShenronEventManager;
import nro.models.minigame.ChonAiDay_Gem;
import nro.models.minigame.ChonAiDay_Gold;
import nro.models.minigame.ConSoMayManGem;
import nro.models.minigame.ConSoMayManGold;
import nro.models.services.ClanService;
import nro.models.services.TaskService;
import nro.models.shop.ShopTab;
import nro.models.shop_ky_gui.ConsignShopManager;

/**
 *
 * @author Việt
 *
 */
public class ServerManager {

    public static String timeStart;
    public static final Map<Object, Object> CLIENTS = new HashMap<>();
    public static String NAME_SERVER = "Ngọc Rồng Onlime";
    public static String DOMAIN = "Server 1";
    public static String NAME = "Ngọc Rồng Online";
    public static String IP = "36.50.135.149";
    public static int PORT = 14445;
    public static int EVENT_SEVER = 0;
    private static ServerManager instance;
    public static boolean isRunning;
    private ScheduledExecutorService topUpdater;

    public void init() {
        Manager.gI();
        //TaskService.gI().loadTask();
        HistoryTransactionDAO.deleteHistory();
    }

    public static ServerManager gI() {
        if (instance == null) {
            instance = new ServerManager();
            instance.init();
        }
        return instance;
    }

    public static void main(String[] args) {
        if (!JarSignatureCheck.verify()) {
            System.exit(1);
            return;
        }
        try {
            timeStart = TimeUtil.getTimeNow("dd/MM/yyyy HH:mm:ss");
            //ShopTab.loadItem();
            new Thread(() -> {
                try {
                    ServerManager.gI().run();
                } catch (Exception e) {
                    Logger.logException(ServerManager.class, e);
                }
            }, "ServerMain").start();

            activeCommandLine();
        } catch (Exception e) {
            Logger.logException(ServerManager.class, e);
        }
    }

    public static void logException(Class<?> clazz, Exception e) {
        System.err.println("Lỗi tại " + clazz.getSimpleName() + ": " + e.getMessage());
        e.printStackTrace();
    }

    public void run() {
        try {
            isRunning = true;
            activeServerSocket();
            nro.models.panel.PanelServer.gI().start();
            nro.models.web.WebServer.gI().start();

            // Gửi các nhiệm vụ cập nhật theo từng dịch vụ
            new Thread(NgocRongNamecService.gI(), "Update NRNM").start();
            new Thread(SuperRankManager.gI(), "Update Super Rank").start();
            new Thread(The23rdMartialArtCongressManager.gI(), "Update DHVT23").start();
            new Thread(DeathOrAliveArenaManager.gI(), "Update Võ Đài Sinh Tử").start();
            new Thread(WorldMartialArtsTournamentManager.gI(), "Update WMAT").start();
            new Thread(AutoMaintenance.gI(), "Update Bảo Trì Tự Động").start();
            AutoMaintenance.AutoMaintenance = true;
            AutoMaintenance.gI().start();
            new Thread(ShenronEventManager.gI(), "Update Shenron").start();

            BossManager.gI().loadBoss();
            Manager.MAPS.forEach(nro.models.map.Map::initBoss);
            EventManager.gI().init();

            new Thread(BossManager.gI(), "Update boss").start();
            new Thread(YardartManager.gI(), "Update yardart boss").start();
            new Thread(FinalBossManager.gI(), "Update final boss").start();
            new Thread(SkillSummonedManager.gI(), "Update skill-summoned boss").start();
            new Thread(BrolyManager.gI(), "Update broly boss").start();
            new Thread(OtherBossManager.gI(), "Update other boss").start();
            new Thread(RedRibbonHQManager.gI(), "Update red ribbon hq boss").start();
            new Thread(TreasureUnderSeaManager.gI(), "Update treasure under sea boss").start();
            new Thread(SnakeWayManager.gI(), "Update snake way boss").start();
            new Thread(GasDestroyManager.gI(), "Update gas destroy boss").start();
            // boss sự kiện theo mùa (trước đây các luồng này không được chạy nên boss sự kiện đứng im)
            new Thread(nro.models.boss.Boss_Manager.LunarNewYearEventManager.gI(), "Update tet boss").start();
            new Thread(nro.models.boss.Boss_Manager.HungVuongEventManager.gI(), "Update hung vuong boss").start();
            new Thread(nro.models.boss.Boss_Manager.TrungThuEventManager.gI(), "Update trung thu boss").start();
            new Thread(nro.models.boss.Boss_Manager.HalloweenEventManager.gI(), "Update halloween boss").start();
            new Thread(nro.models.boss.Boss_Manager.ChristmasEventManager.gI(), "Update christmas boss").start();
            new Thread(nro.models.services.SeasonEventService.gI(), "Update season event").start();

            new Thread(BotManager.gI(), "Thread Bot Game").start();
            nro.models.services.DrMyuuService.gI().start(); // Dr. Myuu xuất hiện ngẫu nhiên, Baby ở Phòng thí nghiệm Myuu
            nro.models.Bot.BotWorld.gI().start(0); // bot tự duy trì cho đỡ trống server (số lượng đổi trên web panel)
            new Thread(ChonAiDay_Gem.gI(), "Thread MiniGame").start();
            new Thread(ChonAiDay_Gold.gI(), "Thread MiniGame").start();
            new Thread(ConSoMayManGold.gI(), "ConSoMayManGoldThread").start();
            new Thread(ConSoMayManGem.gI(), "ConSoMayManGemThread").start();

            startTopUpdater();
        } catch (Exception e) {
            Logger.logException(this.getClass(), e);
        }
    }

    private void startTopUpdater() {
        topUpdater = Executors.newSingleThreadScheduledExecutor();
        topUpdater.scheduleAtFixedRate(() -> {
            if (shouldUpdateTop()) {
                updateTop();
                Manager.resetTopFlags();
            }
        }, 0, 3000, TimeUnit.MILLISECONDS);
    }

    private boolean shouldUpdateTop() {
        return Manager.isTopMaydamChanged
                || Manager.isTopSukienChanged
                || Manager.isTopSukien1Changed
                || Manager.isTopSukien2Changed
                || Manager.isTopWhisChanged;
    }

    private void stopTopUpdater() {
        if (topUpdater != null && !topUpdater.isShutdown()) {
            topUpdater.shutdown();
            System.out.println("Top updater stopped.");
        }
    }

    private void updateTop() {
        try (Connection con = LocalManager.gI().getConnection()) {
            if (Manager.isTopMaydamChanged) {
                Manager.Topmaydam = Manager.realTop(Manager.queryTopmaydam, con);
            }
            if (Manager.isTopSukienChanged) {
                Manager.Topsukien = Manager.realTop(Manager.queryTopsukien, con);
            }
            if (Manager.isTopSukien1Changed) {
                Manager.Topsukien1 = Manager.realTop(Manager.queryTopsukien1, con);
            }
            if (Manager.isTopSukien2Changed) {
                Manager.Topsukien1 = Manager.realTop(Manager.queryTopsukien1, con);
            }
            if (Manager.isTopWhisChanged) {
                Manager.Topwhis = Manager.realTop(Manager.queryTopwhis, con);
            }

            Manager.resetTopFlags();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void activeServerSocket() {
        java.util.Properties cfg = new java.util.Properties();
        try (java.io.InputStreamReader in = new java.io.InputStreamReader(new java.io.FileInputStream("Config.properties"), java.nio.charset.StandardCharsets.UTF_8)) {
            cfg.load(in);
        } catch (Exception ignored) {
        }
        boolean netty = !"false".equalsIgnoreCase(cfg.getProperty("server.netty", "true").trim());
        nro.models.network.netty.AntiDdos.loadConfig(cfg);
        try {
            ISessionAcceptHandler handler = new ISessionAcceptHandler() {
                @Override
                public void sessionInit(ISession is) {
                    if (!canConnectWithIp(is.getIP())) {
                        is.disconnect();
                        return;
                    }
                    is.setMessageHandler(Controller.gI())
                            .setSendCollect(new MessageSendCollect())
                            .setKeyHandler(new MyKeyHandler())
                            .startCollect();
                }

                @Override
                public void sessionDisconnect(ISession session) {
                    Client.gI().kickSession((MySession) session);
                    disconnect((MySession) session);
                }
            };
            if (netty) {
                // Netty + chống DDoS (server.netty=false trong Config.properties để quay về kiểu cũ)
                Network.gI().setAcceptHandler(handler);
                nro.models.network.netty.NettyNetwork.start(PORT, MySession.class);
                return;
            }
            Network.gI().init().setAcceptHandler(handler).setTypeSessioClone(MySession.class)
                    .setDoSomeThingWhenClose(() -> {
                        Logger.error("SERVER CLOSE\n");
                        System.exit(0);
                    })
                    .start(PORT);
        } catch (Exception e) {
            Logger.error("Lỗi khi khởi động máy chủ: " + e.getMessage());
        }
    }

    private boolean canConnectWithIp(String ipAddress) {
        Object o = CLIENTS.get(ipAddress);
        if (o == null) {
            CLIENTS.put(ipAddress, 1);
            return true;
        } else {
            int n = Integer.parseInt(String.valueOf(o));
            if (n < Manager.MAX_PER_IP) {
                n++;
                CLIENTS.put(ipAddress, n);
                return true;
            } else {
                return false;
            }
        }
    }

    public void disconnect(MySession session) {
        Object o = CLIENTS.get(session.getIP());
        if (o != null) {
            int n = Integer.parseInt(String.valueOf(o));
            n--;
            if (n < 0) {
                n = 0;
            }
            CLIENTS.put(session.getIP(), n);
        }
    }

    public void resetNhanQuaHangNgay() {
        String resetJson = "[1,1,\"1970-01-01T00:00:00\"]";
        // dùng pool kết nối của server (trước đây kết nối cứng tới DB "ngocrong" nên không bao giờ reset đúng DB)
        try {
            int rowsUpdated = LocalManager.executeUpdate("UPDATE player SET checkNhanQua = ? WHERE checkNhanQua != ?", resetJson, resetJson);
            Logger.success("Đã reset nhận quà hằng ngày cho " + rowsUpdated + " người chơi với dữ liệu: " + resetJson);
        } catch (Exception e) {
            Logger.logException(ServerManager.class, e, "Lỗi reset nhận quà hằng ngày");
        }
    }

    public void close() {
        isRunning = false;
        try {
            ClanService.gI().close();
        } catch (Exception e) {
            Logger.error("Lỗi save clan!\n");
        }
        try {
            ConsignShopManager.gI().save();
        } catch (Exception e) {
            Logger.error("Lỗi save shop ký gửi!\n");
        }
        Client.gI().close();
        Logger.success("SUCCESSFULLY MAINTENANCE!\n");

        if (System.getenv("NRO_RESTART_LOOP") != null) {
            // run.bat / run_vps_yeu.bat / .sh bản mới: mã thoát 3 = tự bật lại ngay trong cửa sổ đang chạy (không mở cửa sổ thứ hai)
            System.exit(3);
        }
        try {
            Runtime.getRuntime().exec("cmd /c start restart_server.bat"); // file chạy bản cũ
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.exit(0);
    }

    private static void activeCommandLine() {
        Scanner sc = new Scanner(System.in);
        while (sc.hasNextLine()) { // không có console (chạy nền) thì thoát thay vì ném NoSuchElementException
            String line = sc.nextLine().trim();
            switch (line) {
                case "bt":
                    Maintenance.gI().startSeconds(5);
                    break;
                case "bat":
                    AutoMaintenance.AutoMaintenance = true;
                    System.out.println("Đã bật chế độ bảo trì tự động.");
                    break;
                case "tat":
                    AutoMaintenance.AutoMaintenance = false;
                    System.out.println("Đã tắt chế độ bảo trì tự động.");
                    break;
                case "run":
                try {
                    ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "run.bat");
                    pb.inheritIO();
                    pb.start();
                    System.out.println("Đã chạy run.bat");
                } catch (IOException e) {
                    System.out.println("Lỗi khi chạy run.bat: " + e.getMessage());
                }
                break;
                default:
                    System.out.println("Lệnh không hợp lệ.");
                    break;
            }
        }
    }

}
