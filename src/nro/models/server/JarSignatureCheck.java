package nro.models.server;

import java.io.File;
import java.io.InputStream;
import java.security.CodeSigner;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import nro.models.utils.Logger;

/**
 * Kiểm tra chữ ký số "Việt" của file jar khi server khởi động.
 * Jar bị sửa, thêm file lạ, chưa ký hoặc ký bằng chứng chỉ khác thì không cho chạy.
 * Chạy từ thư mục class (đang lập trình trong NetBeans) thì chỉ cảnh báo.
 *
 * @author Việt
 */
public final class JarSignatureCheck {

    /** Vân tay SHA-256 chứng chỉ "CN=Việt" (tools/codesign/viet-codesign.cer). */
    private static final String VIET_SHA256 = "5B7B7149C3A5F20A9D95BC90FEBE0912403612398F5407879B393B223A3FC608";

    private JarSignatureCheck() {
    }

    /** true nếu được phép chạy. */
    public static boolean verify() {
        File f;
        try {
            f = new File(ServerManager.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            Logger.error("Không xác định được file jar đang chạy: " + e.getMessage() + "\n");
            return false;
        }
        if (f.isDirectory()) {
            Logger.warning("Đang chạy từ thư mục class (không phải jar đã ký): bỏ qua kiểm tra chữ ký Việt\n");
            return true;
        }
        int signed = 0;
        try (JarFile jar = new JarFile(f, true)) {
            byte[] buf = new byte[16384];
            Enumeration<JarEntry> en = jar.entries();
            while (en.hasMoreElements()) {
                JarEntry e = en.nextElement();
                String name = e.getName();
                if (e.isDirectory() || isSignatureFile(name)) {
                    continue;
                }
                try (InputStream in = jar.getInputStream(e)) {
                    while (in.read(buf) != -1) {
                        // đọc hết để Java kiểm tra mã băm; sai thì ném SecurityException
                    }
                }
                CodeSigner[] signers = e.getCodeSigners();
                if (signers == null || !signedByViet(signers)) {
                    return fail("file " + name + " chưa được ký bởi Việt (jar đã bị thêm / sửa file)");
                }
                signed++;
            }
        } catch (SecurityException e) {
            return fail("nội dung jar không khớp chữ ký (jar đã bị sửa): " + e.getMessage());
        } catch (Exception e) {
            return fail("không đọc được jar: " + e.getMessage());
        }
        if (signed == 0) {
            return fail("jar chưa được ký");
        }
        Logger.success("Chữ ký số Việt hợp lệ (" + signed + " file)\n");
        return true;
    }

    private static boolean isSignatureFile(String name) {
        String n = name.toUpperCase();
        return n.equals("META-INF/MANIFEST.MF") || (n.startsWith("META-INF/") && n.indexOf('/', 9) < 0
                && (n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA") || n.endsWith(".EC")));
    }

    private static boolean signedByViet(CodeSigner[] signers) {
        for (CodeSigner s : signers) {
            for (Certificate c : s.getSignerCertPath().getCertificates()) {
                try {
                    byte[] d = MessageDigest.getInstance("SHA-256").digest(c.getEncoded());
                    StringBuilder hex = new StringBuilder();
                    for (byte b : d) {
                        hex.append(String.format("%02X", b));
                    }
                    if (hex.toString().equals(VIET_SHA256)) {
                        return true;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return false;
    }

    private static boolean fail(String why) {
        Logger.error("KIỂM TRA CHỮ KÝ VIỆT THẤT BẠI: " + why + "\nServer không khởi động. Hãy dùng bản 20.jar gốc đã ký.\n");
        return false;
    }
}
