package com.cricket.fantasyleague.service;

import com.cricket.fantasyleague.payload.dto.SystemInfoResponse;
import com.cricket.fantasyleague.payload.dto.SystemInfoResponse.GcInfo;
import com.cricket.fantasyleague.payload.dto.SystemInfoResponse.MemoryInfo;
import com.cricket.fantasyleague.payload.dto.SystemInfoResponse.ThreadInfo;

import org.springframework.stereotype.Service;

import java.lang.management.*;
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

        long cpuTimeNs = ProcessHandle.current().info().totalCpuDuration()
                .map(Duration::toNanos).orElse(-1L);

        MemoryInfo memory = new MemoryInfo(
                heap.getUsed(),
                heap.getMax(),
                heap.getMax() > 0 ? Math.round((double) heap.getUsed() / heap.getMax() * 100) : 0,
                mem.getNonHeapMemoryUsage().getUsed()
        );

        ThreadInfo threadInfo = new ThreadInfo(
                threads.getThreadCount(),
                threads.getPeakThreadCount(),
                threads.getDaemonThreadCount()
        );

        List<GcInfo> gcList = ManagementFactory.getGarbageCollectorMXBeans().stream()
                .map(gc -> new GcInfo(gc.getName(), gc.getCollectionCount(), gc.getCollectionTime()))
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
                gcList
        );
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
