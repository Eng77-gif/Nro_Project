package nro.models.Bot;

import java.util.ArrayList;
import java.util.List;
import nro.models.server.ServerManager;

public class BotManager implements Runnable {

    public static BotManager i;

    public List<Bot> bot = new ArrayList<>();

    public static BotManager gI() {
        if (i == null) {
            i = new BotManager();
        }
        return i;
    }

    @Override
    public void run() {
        while (ServerManager.isRunning) {
            try {
                long st = System.currentTimeMillis();

                for (Bot bot : new ArrayList<>(this.bot)) {
                    if (bot != null) {
                        try {
                            bot.update();
                        } catch (Throwable t) {
                            BotWorld.gI().logError(bot, t); // lỗi của 1 bot không làm dừng các bot khác
                        }
                    }
                }

                try {
                    BotWorld.gI().socialTick();
                } catch (Throwable t) {
                    nro.models.utils.Logger.error("Bot: lỗi bang hội / buôn bán " + t + "\n");
                }
                long timeLeft = 150 - (System.currentTimeMillis() - st);
                if (timeLeft > 0) {
                    Thread.sleep(timeLeft);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
