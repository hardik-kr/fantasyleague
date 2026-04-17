package com.cricket.fantasyleague.payload.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SystemInfoResponse(
        String service,
        String javaVersion,
        String jvm,
        String uptime,
        long uptimeMs,
        long pid,
        int processors,
        String os,
        String cpuTime,
        MemoryInfo memory,
        ThreadInfo threads,
        List<GcInfo> gc,
        List<BufferPoolInfo> bufferPools
) {

    public record MemoryInfo(
            long heapUsed,
            long heapCommitted,
            long heapMax,
            long heapPercent,
            long nonHeapUsed,
            long nonHeapCommitted,
            long rss
    ) {}

    public record ThreadInfo(
            int live,
            int peak,
            int daemon
    ) {}

    public record GcInfo(
            String name,
            long collections,
            long timeMs
    ) {}

    public record BufferPoolInfo(
            String name,
            long count,
            long memoryUsed,
            long totalCapacity
    ) {}
}
