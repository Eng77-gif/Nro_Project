package nro.models.data;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import nro.models.utils.Logger;

/**
 * Sao lưu database lên GitHub, mã hoá (repo công khai: không lộ mật khẩu tài khoản / dữ liệu nạp).
 * Bật bằng cách tạo file backup.key (1 dòng = mật khẩu mã hoá, không đưa lên git) cạnh 20.jar.
 * Tự chạy sau bảo trì (ServerManager.close). Dòng lệnh:
 *   java -cp 20.jar nro.models.data.SqlBackup export [push]   -> sql/team2026.sql.enc (+ git commit/push)
 *   java -cp 20.jar nro.models.data.SqlBackup import          -> giải mã và nạp đè vào database
 * Định dạng: "NROB1" + salt(16) + iv(12) + AES-256-GCM(gzip(dump)); khoá PBKDF2-SHA256 200k vòng.
 */
public final class SqlBackup {

    public static final String FILE = "sql/team2026.sql.enc";
    private static final String KEY_FILE = "backup.key";
    private static final byte[] MAGIC = "NROB1".getBytes(StandardCharsets.US_ASCII);

    private SqlBackup() {
    }

    private static final String SETTINGS = "backup.properties";  // token GitHub, repo, nhánh (không đưa lên git)
    private static final String PENDING = "sql/import.pending";     // panel đã tải bản mới: nạp ở lần bật server sau
    private static volatile boolean running;
    private static volatile String lastResult = "";
    private static volatile long lastTime;
    private static volatile String pushError = "";  // lý do đẩy lỗi gần nhất, hiện trên panel

    public static boolean enabled() {
        return new File(KEY_FILE).isFile();
    }

    private static Properties settings() {
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(SETTINGS)) {
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
        return p;
    }

    private static String repo(Properties st) {
        return st.getProperty("repo", "Eng77-gif/Nro_Project").trim();
    }

    private static String branch(Properties st) {
        return st.getProperty("branch", "main").trim();
    }

    private static void result(String r) {
        lastResult = r;
        lastTime = System.currentTimeMillis();
        Logger.log(r.startsWith("Lỗi") ? Logger.RED : Logger.GREEN, r + "\n");
    }

    /** Trạng thái cho panel (không trả mật khẩu / token). */
    public static Map<String, Object> status() {
        Properties st = settings();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("password", enabled());
        m.put("token", !st.getProperty("token", "").isBlank());
        m.put("repo", repo(st));
        m.put("branch", branch(st));
        m.put("auto", !"false".equals(st.getProperty("auto")));
        m.put("git", new File(".git").exists());
        m.put("running", running);
        m.put("lastResult", lastResult);
        m.put("lastTime", lastTime);
        File f = new File(FILE);
        m.put("fileKb", f.isFile() ? f.length() / 1024 : -1);
        m.put("fileTime", f.isFile() ? f.lastModified() : 0);
        m.put("pending", new File(PENDING).isFile());
        return m;
    }

    /** Panel lưu cài đặt; ô để trống = giữ giá trị cũ. */
    public static void saveSettings(String password, String token, String repo, String branch, boolean auto) throws IOException {
        if (password != null && !password.isBlank()) {
            if (password.trim().length() < 6) {
                throw new IOException("Mật khẩu mã hoá phải từ 6 ký tự");
            }
            Files.write(Paths.get(KEY_FILE), (password.trim() + "\n").getBytes(StandardCharsets.UTF_8));
        }
        Properties st = settings();
        if (token != null && !token.isBlank()) {
            st.setProperty("token", token.trim());
        }
        if (repo != null && repo.trim().matches("[\\w.-]+/[\\w.-]+")) {
            st.setProperty("repo", repo.trim());
        }
        if (branch != null && branch.trim().matches("[\\w./-]+")) {
            st.setProperty("branch", branch.trim());
        }
        st.setProperty("auto", String.valueOf(auto));
        try (Writer w = new OutputStreamWriter(new FileOutputStream(SETTINGS), StandardCharsets.UTF_8)) {
            st.store(w, "Sao luu GitHub (khong dua file nay len git)");
        }
    }

    /** Panel bấm "Đẩy ngay": chạy nền, xem kết quả qua status(). */
    public static boolean pushAsync() {
        if (running || !enabled()) {
            return false;
        }
        running = true;
        new Thread(() -> {
            try {
                export();
                result(push() ? "Đã đẩy dữ liệu lên GitHub" : "Lỗi: " + pushError);
            } catch (Exception e) {
                result("Lỗi: " + e.getMessage());
            } finally {
                running = false;
            }
        }, "SqlBackup push").start();
        return true;
    }

    /** Panel bấm "Tải về": tải file mã hoá, thử giải mã, đánh dấu nạp ở lần bật server tiếp theo. */
    public static void download() throws Exception {
        Properties st = settings();
        java.net.http.HttpRequest.Builder rq = java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                "https://api.github.com/repos/" + repo(st) + "/contents/" + FILE + "?ref=" + branch(st)))
                .header("Accept", "application/vnd.github.raw").header("User-Agent", "nro-server");
        String token = st.getProperty("token", "").trim();
        if (!token.isEmpty()) {
            rq.header("Authorization", "Bearer " + token);
        }
        java.net.http.HttpResponse<byte[]> r = java.net.http.HttpClient.newBuilder().followRedirects(java.net.http.HttpClient.Redirect.NORMAL).build()
                .send(rq.build(), java.net.http.HttpResponse.BodyHandlers.ofByteArray());
        if (r.statusCode() != 200) {
            throw new IOException("GitHub trả " + r.statusCode() + (r.statusCode() == 404 ? " (chưa có " + FILE + " trên GitHub)" : ""));
        }
        Path tmp = Paths.get(FILE + ".dl");
        Files.createDirectories(tmp.getParent());
        Files.write(tmp, r.body());
        try (InputStream in = Files.newInputStream(tmp)) {
            decrypt(in);  // sai mật khẩu thì báo ngay, không ghi đè file cũ
        } catch (Exception e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
        Files.move(tmp, Paths.get(FILE), StandardCopyOption.REPLACE_EXISTING);
        Files.write(Paths.get(PENDING), new byte[0]);
    }

    /** Gọi khi bật server, trước khi nạp dữ liệu. */
    public static void importPendingAtBoot() {
        if (!new File(PENDING).isFile()) {
            return;
        }
        try {
            new File(PENDING).delete();  // lỗi cũng không thử lại mãi
            Logger.log(Logger.YELLOW, "Nạp dữ liệu tải từ GitHub ...\n");
            importBackup();
        } catch (Exception e) {
            Logger.log(Logger.RED, "Nạp dữ liệu GitHub lỗi: " + e.getMessage() + "\n");
        }
    }

    /** Gọi sau bảo trì: không bao giờ ném lỗi ra ngoài, chỉ ghi log. */
    public static void afterMaintenance() {
        if (!enabled() || "false".equals(settings().getProperty("auto"))) {
            return;
        }
        try {
            Logger.log(Logger.YELLOW, "Sao lưu database lên GitHub (mã hoá) ...\n");
            export();
            result(push() ? "Đã đẩy dữ liệu lên GitHub (sau bảo trì)" : "Lỗi: " + pushError + " (file vẫn lưu ở " + FILE + ")");
        } catch (Exception e) {
            result("Lỗi: sao lưu GitHub " + e.getMessage());
        }
    }

    private static Properties config() throws IOException {
        Properties p = new Properties();
        try (InputStream in = new FileInputStream("Config.properties")) {
            p.load(in);
        }
        return p;
    }

    private static char[] password() throws IOException {
        String pw = new String(Files.readAllBytes(Paths.get(KEY_FILE)), StandardCharsets.UTF_8).trim();
        if (pw.length() < 6) {
            throw new IOException(KEY_FILE + " phải có mật khẩu từ 6 ký tự");
        }
        return pw.toCharArray();
    }

    private static Cipher cipher(int mode, char[] pw, byte[] salt, byte[] iv) throws Exception {
        byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(new PBEKeySpec(pw, salt, 200_000, 256)).getEncoded();
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        return c;
    }

    /** Thử lần lượt các tên lệnh (PATH, rồi thư mục bin của MySQL / MariaDB). */
    private static Process start(List<String> names, List<String> args, Properties cfg) throws IOException {
        List<String> tried = new ArrayList<>(names);
        String base = mysqlBaseDir(cfg);
        if (base != null) {
            for (String n : names) {
                tried.add(Paths.get(base, "bin", n).toString());
                tried.add(Paths.get(base, "bin", n + ".exe").toString());
            }
        }
        IOException last = null;
        for (String exe : tried) {
            List<String> cmd = new ArrayList<>();
            cmd.add(exe);
            cmd.addAll(args);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().put("MYSQL_PWD", cfg.getProperty("database.pass", ""));
            try {
                return pb.start();
            } catch (IOException e) {
                last = e;
            }
        }
        throw new IOException("không tìm thấy " + names + (last != null ? " (" + last.getMessage() + ")" : ""));
    }

    private static String mysqlBaseDir(Properties cfg) {
        try {
            Class.forName(cfg.getProperty("database.driver", "com.mysql.jdbc.Driver"));
        } catch (ClassNotFoundException ignored) {
        }
        try (java.sql.Connection c = java.sql.DriverManager.getConnection(
                "jdbc:mysql://" + cfg.getProperty("database.host", "localhost") + ":" + cfg.getProperty("database.port", "3306")
                + "/?useSSL=false", cfg.getProperty("database.user", "root"), cfg.getProperty("database.pass", ""));
                java.sql.ResultSet rs = c.createStatement().executeQuery("select @@basedir")) {
            return rs.next() ? rs.getString(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> connArgs(Properties cfg) {
        return new ArrayList<>(List.of("-h", cfg.getProperty("database.host", "localhost"), "-P", cfg.getProperty("database.port", "3306"),
                "-u", cfg.getProperty("database.user", "root"), "--default-character-set=utf8mb4"));
    }

    private static String drain(InputStream in) throws IOException {
        String s = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        return s.length() > 400 ? s.substring(s.length() - 400) : s;
    }

    public static void export() throws Exception {
        Properties cfg = config();
        List<String> args = connArgs(cfg);
        args.addAll(List.of("--single-transaction", "--no-tablespaces", "--skip-dump-date", cfg.getProperty("database.name", "team2026")));
        Process p = start(List.of("mariadb-dump", "mysqldump"), args, cfg);
        byte[] salt = new byte[16], iv = new byte[12];
        SecureRandom r = new SecureRandom();
        r.nextBytes(salt);
        r.nextBytes(iv);
        Path tmp = Paths.get(FILE + ".tmp");
        Files.createDirectories(tmp.getParent());
        StringBuilder err = new StringBuilder();
        Thread t = new Thread(() -> {
            try {
                err.append(drain(p.getErrorStream()));
            } catch (IOException ignored) {
            }
        });
        t.start();
        try (OutputStream file = Files.newOutputStream(tmp)) {
            file.write(MAGIC);
            file.write(salt);
            file.write(iv);
            try (OutputStream out = new GZIPOutputStream(new CipherOutputStream(file, cipher(Cipher.ENCRYPT_MODE, password(), salt, iv)), 1 << 16)) {
                p.getInputStream().transferTo(out);
            }
        }
        if (!p.waitFor(30, TimeUnit.MINUTES) || p.exitValue() != 0) {
            t.join(2000);
            Files.deleteIfExists(tmp);
            throw new IOException("xuất database lỗi: " + err);
        }
        Files.move(tmp, Paths.get(FILE), StandardCopyOption.REPLACE_EXISTING);
        Logger.log(Logger.GREEN, "Đã xuất database mã hoá: " + FILE + " (" + Files.size(Paths.get(FILE)) / 1024 + " KB)\n");
    }

    private static boolean git(String... a) throws Exception {
        List<String> cmd = new ArrayList<>(List.of("git", "-c", "user.name=nro-server", "-c", "user.email=nro-server@localhost"));
        cmd.addAll(Arrays.asList(a));
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        pb.environment().put("GIT_TERMINAL_PROMPT", "0");  // chưa lưu đăng nhập GitHub thì báo lỗi, không treo chờ nhập
        Process p = pb.start();
        String out = drain(p.getInputStream());
        boolean ok = p.waitFor(5, TimeUnit.MINUTES) && p.exitValue() == 0;
        if (!ok) {
            pushError = "git " + a[0] + ": " + out;
            Logger.log(Logger.RED, "git " + String.join(" ", a) + ": " + out + "\n");
        }
        return ok;
    }

    /** Commit đúng 1 file rồi đẩy; bị từ chối (máy khác đẩy trước) thì kéo về giữ bản của máy này rồi đẩy lại. */
    public static boolean push() throws Exception {
        pushError = "xem log";
        if (!settings().getProperty("token", "").isBlank()) {
            return pushApi();
        }
        if (!new File(".git").exists()) {
            pushError = "chưa có token GitHub (panel → Dữ liệu GitHub) và thư mục server không phải bản git clone";
            return false;
        }
        git("add", "-f", FILE);
        String msg = "Dữ liệu server " + new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date());
        git("commit", "-m", msg, "--", FILE);  // không có thay đổi thì lỗi, vẫn thử đẩy commit cũ chưa đẩy
        return git("push") || (git("pull", "--no-edit", "--no-rebase", "-X", "ours") && git("push"));
    }

    /** Đẩy qua GitHub API bằng token: không cần cài git, thư mục server không cần là bản clone. */
    private static boolean pushApi() throws Exception {
        Properties st = settings();
        String url = "https://api.github.com/repos/" + repo(st) + "/contents/" + FILE;
        java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
        java.util.function.Function<String, java.net.http.HttpRequest.Builder> req = u -> java.net.http.HttpRequest.newBuilder(java.net.URI.create(u))
                .header("Authorization", "Bearer " + st.getProperty("token").trim())
                .header("Accept", "application/vnd.github+json").header("User-Agent", "nro-server");
        java.net.http.HttpResponse<String> cur = http.send(req.apply(url + "?ref=" + branch(st)).GET().build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        String sha = null;
        if (cur.statusCode() == 200) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"sha\"\\s*:\\s*\"([0-9a-f]{40})\"").matcher(cur.body());
            sha = m.find() ? m.group(1) : null;
        } else if (cur.statusCode() != 404) {
            pushError = "GitHub " + cur.statusCode() + ": " + apiMessage(cur.body()) + tokenHint(cur.statusCode());
            return false;
        }
        String body = "{\"message\":\"Dữ liệu server " + new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date())
                + "\",\"branch\":\"" + branch(st) + "\",\"committer\":{\"name\":\"nro-server\",\"email\":\"nro-server@localhost\"}"
                + (sha != null ? ",\"sha\":\"" + sha + "\"" : "")
                + ",\"content\":\"" + Base64.getEncoder().encodeToString(Files.readAllBytes(Paths.get(FILE))) + "\"}";
        java.net.http.HttpResponse<String> r = http.send(req.apply(url).PUT(java.net.http.HttpRequest.BodyPublishers.ofString(body)).build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() == 200 || r.statusCode() == 201) {
            return true;
        }
        pushError = "GitHub " + r.statusCode() + ": " + apiMessage(r.body()) + tokenHint(r.statusCode());
        return false;
    }

    private static String tokenHint(int code) {
        return code == 401 || code == 403 || code == 404 ? " (token sai / hết hạn / thiếu quyền Contents: Read and write)" : "";
    }

    private static String apiMessage(String json) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"message\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : json.length() > 200 ? json.substring(0, 200) : json;
    }

    /** Giải mã + kiểm tra toàn bộ file, trả dữ liệu gzip. */
    private static byte[] decrypt(InputStream raw) throws Exception {
        InputStream file = new BufferedInputStream(raw);
        byte[] head = new byte[MAGIC.length + 28];
        if (file.readNBytes(head, 0, head.length) != head.length || !Arrays.equals(Arrays.copyOf(head, MAGIC.length), MAGIC)) {
            throw new IOException(FILE + " không đúng định dạng");
        }
        byte[] salt = Arrays.copyOfRange(head, MAGIC.length, MAGIC.length + 16);
        byte[] iv = Arrays.copyOfRange(head, MAGIC.length + 16, head.length);
        // GCM chỉ xác thực ở cuối: giải mã hết vào bộ nhớ trước, sai mật khẩu thì báo lỗi, không nạp nửa chừng
        // shortcut: dữ liệu giải mã nằm trong RAM, database vài trăm MB thì cần đổi sang file tạm
        try (InputStream in = new CipherInputStream(file, cipher(Cipher.DECRYPT_MODE, password(), salt, iv))) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IOException("sai mật khẩu mã hoá hoặc file hỏng");
        }
    }

    public static void importBackup() throws Exception {
        Properties cfg = config();
        byte[] gz;
        try (InputStream file = Files.newInputStream(Paths.get(FILE))) {
            gz = decrypt(file);
        }
        String db = cfg.getProperty("database.name", "team2026");
        List<String> args = connArgs(cfg);
        args.add(db);
        Process p = start(List.of("mariadb", "mysql"), args, cfg);
        try (OutputStream out = p.getOutputStream(); InputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
            in.transferTo(out);
        }
        String err = drain(p.getErrorStream());
        if (!p.waitFor(30, TimeUnit.MINUTES) || p.exitValue() != 0) {
            throw new IOException("nạp database lỗi: " + err);
        }
        Logger.log(Logger.GREEN, "Đã nạp " + FILE + " vào database " + db + "\n");
    }

    public static void main(String[] a) {
        try {
            String cmd = a.length > 0 ? a[0] : "";
            if (cmd.equals("export")) {
                export();
                if (a.length > 1 && a[1].equals("push") && !push()) {
                    System.exit(2);
                }
            } else if (cmd.equals("import")) {
                importBackup();
            } else {
                System.out.println("Dùng: export [push] | import   (cần file " + KEY_FILE + ")");
                System.exit(1);
            }
        } catch (Exception e) {
            System.out.println("Lỗi: " + e.getMessage());
            System.exit(1);
        }
    }
}
