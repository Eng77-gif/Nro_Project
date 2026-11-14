package nro.models.utils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Bộ tải repo giữ nguyên Config.properties trên máy (cấu hình riêng), nên mục cấu hình mới không tự có. Mỗi lần khởi động:
 * mục nào có trong Config.default.properties (đi kèm bản cập nhật) mà Config.properties chưa có thì thêm vào cuối, kèm dòng ghi chú
 * ngay phía trên nó; không sửa mục đã có.
 */
public final class ConfigMerge {

    private ConfigMerge() {
    }

    public static void run() {
        run(new File("Config.default.properties"), new File("Config.properties"));
    }

    /** Trả về số mục đã thêm. */
    public static int run(File def, File cfg) {
        if (!def.isFile()) {
            return 0;
        }
        try {
            List<String> defLines = Files.readAllLines(def.toPath(), StandardCharsets.UTF_8);
            Properties have = new Properties();
            if (cfg.isFile()) {
                try (java.io.Reader r = Files.newBufferedReader(cfg.toPath(), StandardCharsets.UTF_8)) {
                    have.load(r);
                }
            }
            List<String> add = new ArrayList<>(), notes = new ArrayList<>();
            int n = 0;
            for (String l : defLines) {
                String t = l.trim();
                if (t.isEmpty()) {
                    notes.clear();
                } else if (t.startsWith("#") || t.startsWith("!")) {
                    notes.add(l);
                } else {
                    int eq = t.indexOf('=');
                    String k = (eq > 0 ? t.substring(0, eq) : t).trim();
                    if (!have.containsKey(k)) {
                        add.addAll(notes);
                        add.add(t);
                        n++;
                    }
                    notes.clear();
                }
            }
            if (n == 0) {
                return 0;
            }
            String old = cfg.isFile() ? new String(Files.readAllBytes(cfg.toPath()), StandardCharsets.UTF_8) : "";
            StringBuilder sb = new StringBuilder(old);
            if (!old.isEmpty() && !old.endsWith("\n")) {
                sb.append('\n');
            }
            sb.append("\n#--- Them tu dong tu Config.default.properties (muc moi cua ban cap nhat) ---\n");
            for (String l : add) {
                sb.append(l).append('\n');
            }
            Files.write(cfg.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
            Logger.success("Config.properties: thêm " + n + " mục cấu hình mới\n");
            return n;
        } catch (Exception e) {
            Logger.error("Không bổ sung được Config.properties: " + e.getMessage() + "\n");
            return 0;
        }
    }
}
