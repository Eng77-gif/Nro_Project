package nro.models.utils;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Việt
 * 
 */

public class FileIO {

    /**
     * Bộ nhớ đệm file dữ liệu gửi cho client (icon, quái, hiệu ứng, map, ảnh theo tên): lúc vào game client xin hàng trăm
     * file, trước đây lần nào cũng đọc đĩa. Giữ tối đa CACHE_MAX byte (1/10 RAM Java, không quá 48 MB - đủ cho file hay
     * dùng, máy 2 GB không bị chiếm RAM), bỏ file dùng lâu nhất; file sửa trên đĩa (đổi thời gian sửa / kích thước) thì đọc lại.
     */
    private static final long CACHE_MAX = Math.min(48L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 10);
    private static long cacheSize;
    private static final java.util.LinkedHashMap<String, Object[]> CACHE = new java.util.LinkedHashMap<>(4096, 0.75f, true);

    public static byte[] readFile(String url) {
        File f = new File(url);
        long mod = f.lastModified(), len = f.length();
        if (mod == 0L) {
            return null; // không có file
        }
        synchronized (CACHE) {
            Object[] c = CACHE.get(url);
            if (c != null && (long) c[1] == mod && ((byte[]) c[0]).length == len) {
                return (byte[]) c[0];
            }
        }
        try {
            byte[] ab = java.nio.file.Files.readAllBytes(f.toPath()); // đọc đủ cả file (read() một lần có thể thiếu)
            if (ab.length <= 512 * 1024) { // file lớn (ảnh nhiều khung) không giữ
                synchronized (CACHE) {
                    Object[] old = CACHE.put(url, new Object[]{ab, mod});
                    cacheSize += ab.length - (old == null ? 0 : ((byte[]) old[0]).length);
                    java.util.Iterator<Object[]> it = CACHE.values().iterator();
                    while (cacheSize > CACHE_MAX && it.hasNext()) {
                        cacheSize -= ((byte[]) it.next()[0]).length;
                        it.remove();
                    }
                }
            }
            return ab;
        } catch (IOException e) {
        }
        return null;
    }

    public static ByteArrayOutputStream loadFile(String url) {
        try {
            FileInputStream openFileInput = new FileInputStream(url);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] bArr = new byte[1024];
            while (true) {
                int read = openFileInput.read(bArr);
                if (read == -1) {
                    break;
                }
                byteArrayOutputStream.write(bArr, 0, read);
            }
            byteArrayOutputStream.flush();
            byteArrayOutputStream.close();
            openFileInput.close();
            return byteArrayOutputStream;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void writeFile(String url, byte[] ab) {
        try {
            File f = new File(url);
            if (f.exists()) {
                f.delete();
            }
            f.createNewFile();
            FileOutputStream fos = new FileOutputStream(url);
            fos.write(ab);
            fos.flush();
            fos.close();
        } catch (IOException e) {
        }
    }

    public static void writeFile(String url, String content) {
        try {
            File file = new File(url);

            File parentDir = file.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }

            if (file.exists()) {
                file.delete();
            }
            file.createNewFile();

            FileOutputStream fos = new FileOutputStream(file);
            try (OutputStreamWriter writer = new OutputStreamWriter(fos)) {
                writer.write(content);
                writer.flush();
            }
        } catch (IOException e) {
        }
    }

    public static List<File> getFiles(String folderPath) {
        List<File> fileList = new ArrayList<>();
        File folder = new File(folderPath);

        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles();

            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        fileList.add(file);
                    }
                }
            }
        }

        return fileList;
    }
}
