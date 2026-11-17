package nro.models.utils;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

/**
 *
 * @author Việt
 * 
 */

public class SystemMetrics {

    private static String formatMemory(double used, double total) {
        return String.format("(%.1f/%.1f) GB", used, total);
    }

    private static String formatPercentage(double percentage) {
        return String.format("%.0f%%", percentage);
    }

    // Linux / Android: "free" không tính bộ nhớ đệm (cache) mà hệ điều hành trả lại được ngay -> máy trông như đầy RAM.
    // Dùng MemAvailable trong /proc/meminfo nếu có.
    private static long freeBytes(OperatingSystemMXBean osBean) {
        try {
            for (String l : java.nio.file.Files.readAllLines(java.nio.file.Paths.get("/proc/meminfo"))) {
                if (l.startsWith("MemAvailable:")) {
                    return Long.parseLong(l.replaceAll("[^0-9]", "")) * 1024;
                }
            }
        } catch (Exception ignored) {
        }
        return osBean.getFreeMemorySize();
    }

    private static double getUsedMemoryGB() {
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        long totalMemory = osBean.getTotalMemorySize();
        long freeMemory = freeBytes(osBean);
        long usedMemory = totalMemory - freeMemory;
        return (double) usedMemory / (1024 * 1024 * 1024);
    }

    private static double getTotalMemoryGB() {
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        long totalMemory = osBean.getTotalMemorySize();
        return (double) totalMemory / (1024 * 1024 * 1024);
    }

    private static double getRAMUsagePercentage() {
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        long totalMemory = osBean.getTotalMemorySize();
        long freeMemory = freeBytes(osBean);
        long usedMemory = totalMemory - freeMemory;
        return ((double) usedMemory / totalMemory) * 100;
    }

    private static double getCPUUsagePercentage() {
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        return osBean.getCpuLoad() * 100;  // < 0 khi không đọc được (Android chặn /proc/stat)
    }

    private static double getHeapUsedMemoryGB() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        long usedMemory = heapUsage.getUsed();
        return (double) usedMemory / (1024 * 1024 * 1024);
    }

    private static double getHeapMaxMemoryGB() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        long maxMemory = heapUsage.getMax();
        return (double) maxMemory / (1024 * 1024 * 1024);
    }

    private static double getHeapUsagePercentage() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        long usedMemory = heapUsage.getUsed();
        long maxMemory = heapUsage.getMax();
        return ((double) usedMemory / maxMemory) * 100;
    }

    private static long lastCpu, lastWall;

    // % CPU của server trên tổng số nhân, tính từ lần xem trước (lần đầu: trung bình từ lúc bật).
    // Không dùng getProcessCpuLoad vì cần /proc/stat, Android chặn -> luôn -1.
    private static synchronized String cpuServer() {
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        long cpu = osBean.getProcessCpuTime(), wall = System.nanoTime();
        if (cpu < 0) {
            return "?";
        }
        long dCpu, dWall;
        if (lastWall == 0) {
            dCpu = cpu;
            dWall = ManagementFactory.getRuntimeMXBean().getUptime() * 1_000_000L;
        } else {
            dCpu = cpu - lastCpu;
            dWall = wall - lastWall;
        }
        lastCpu = cpu;
        lastWall = wall;
        if (dWall <= 0) {
            return "?";
        }
        return formatPercentage(100.0 * dCpu / dWall / Runtime.getRuntime().availableProcessors());
    }

    public static String ToString() {
        double usedMemory = getUsedMemoryGB();
        double totalMemory = getTotalMemoryGB();
        double ramUsagePercentage = getRAMUsagePercentage();
        double cpuUsagePercentage = getCPUUsagePercentage();
        double heapUsedMemory = getHeapUsedMemoryGB();
        double heapMaxMemory = getHeapMaxMemoryGB();
        double heapUsagePercentage = getHeapUsagePercentage();

        // "Máy" là RAM cả máy (gồm Windows / MySQL...), "Server" là phần Java của game thật sự dùng
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        long mb = 1024 * 1024;
        String string = ("Máy: " + formatMemory(usedMemory, totalMemory) + " (" + formatPercentage(ramUsagePercentage) + ")\n"
                + "Server: " + (heap.getUsed() / mb) + " MB dữ liệu, giữ " + ((heap.getCommitted() + nonHeap.getCommitted()) / mb)
                + " MB, tối đa " + (heap.getMax() / mb) + " MB\n"
                + "CPU: " + (cpuUsagePercentage >= 0 ? formatPercentage(cpuUsagePercentage) + " cả máy, " : "")
                + "server " + cpuServer() + "\n"
                + nro.models.network.netty.AntiDdos.stats() + "\n");
        return string;
    }
}
