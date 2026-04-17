package com.cricket.fantasyleague.service;

import com.cricket.fantasyleague.payload.dto.SystemInfoResponse;
import com.cricket.fantasyleague.payload.dto.SystemInfoResponse.BufferPoolInfo;
import com.cricket.fantasyleague.payload.dto.SystemInfoResponse.GcInfo;
import com.cricket.fantasyleague.payload.dto.SystemInfoResponse.MemoryInfo;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.management.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Service
public class HealthService {

    private static final String SERVICE_NAME = "Fantasy League";

    public SystemInfoResponse getSystemInfo() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        MemoryUsage heap = mem.getHeapMemoryUsage();
        MemoryUsage nonHeap = mem.getNonHeapMemoryUsage();

        long cpuTimeNs = ProcessHandle.current().info().totalCpuDuration()
                .map(Duration::toNanos).orElse(-1L);

        MemoryInfo memory = new MemoryInfo(
                heap.getUsed(),
                heap.getCommitted(),
                heap.getMax(),
                heap.getMax() > 0 ? Math.round((double) heap.getUsed() / heap.getMax() * 100) : 0,
                nonHeap.getUsed(),
                nonHeap.getCommitted(),
                getProcessRssBytes()
        );

        var threadInfo = new SystemInfoResponse.ThreadInfo(
                threads.getThreadCount(),
                threads.getPeakThreadCount(),
                threads.getDaemonThreadCount()
        );

        List<GcInfo> gcList = ManagementFactory.getGarbageCollectorMXBeans().stream()
                .map(gc -> new GcInfo(gc.getName(), gc.getCollectionCount(), gc.getCollectionTime()))
                .toList();

        List<BufferPoolInfo> bufferPools = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class).stream()
                .map(bp -> new BufferPoolInfo(bp.getName(), bp.getCount(), bp.getMemoryUsed(), bp.getTotalCapacity()))
                .toList();

        return new SystemInfoResponse(
                SERVICE_NAME,
                System.getProperty("java.version"),
                runtime.getVmName() + " " + runtime.getVmVersion(),
                formatDuration(Duration.ofMillis(runtime.getUptime())),
                runtime.getUptime(),
                ProcessHandle.current().pid(),
                Runtime.getRuntime().availableProcessors(),
                os.getName() + " " + os.getVersion() + " (" + os.getArch() + ")",
                cpuTimeNs > 0 ? formatDuration(Duration.ofNanos(cpuTimeNs)) : "N/A",
                memory,
                threadInfo,
                gcList,
                bufferPools
        );
    }

    private long getProcessRssBytes() {
        try {
            String status = Files.readString(Path.of("/proc/self/status"));
            for (String line : status.split("\n")) {
                if (line.startsWith("VmRSS:")) {
                    String kb = line.replaceAll("[^0-9]", "");
                    return Long.parseLong(kb) * 1024;
                }
            }
        } catch (IOException | NumberFormatException ignored) {
        }
        return -1;
    }

    private String formatDuration(Duration d) {
        long days = d.toDays();
        long hours = d.toHoursPart();
        long mins = d.toMinutesPart();
        long secs = d.toSecondsPart();
        if (days > 0) return days + "d " + hours + "h " + mins + "m";
        if (hours > 0) return hours + "h " + mins + "m " + secs + "s";
        return mins + "m " + secs + "s";
    }
}
