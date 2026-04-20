package com.cricket.fantasyleague.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Twitter-style Snowflake ID generator producing globally unique, monotonically
 * increasing 64-bit IDs without any database coordination.
 *
 * Bit layout (63 usable bits, sign bit always 0):
 *   [41 bits: ms since EPOCH] [10 bits: worker] [12 bits: sequence]
 *
 * Capacity: 4 096 IDs per millisecond per worker (~69 years of timestamps).
 */
@Component
public class SnowflakeIdGenerator {

    private static final long EPOCH = 1735689600000L; // 2025-01-01T00:00:00Z

    private static final int WORKER_BITS = 10;
    private static final int SEQUENCE_BITS = 12;
    private static final long MAX_WORKER_ID = (1L << WORKER_BITS) - 1;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    private static final int TIMESTAMP_SHIFT = WORKER_BITS + SEQUENCE_BITS;
    private static final int WORKER_SHIFT = SEQUENCE_BITS;

    private static volatile SnowflakeIdGenerator INSTANCE;

    private final long workerId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(@Value("${fantasy.snowflake.worker-id:1}") long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("Worker ID must be between 0 and " + MAX_WORKER_ID);
        }
        this.workerId = workerId;
        INSTANCE = this;
    }

    public synchronized long nextId() {
        long now = System.currentTimeMillis();
        if (now < lastTimestamp) {
            throw new IllegalStateException(
                    "Clock moved backwards by " + (lastTimestamp - now) + " ms");
        }
        if (now == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                while (now <= lastTimestamp) {
                    now = System.currentTimeMillis();
                }
            }
        } else {
            sequence = 0;
        }
        lastTimestamp = now;
        return ((now - EPOCH) << TIMESTAMP_SHIFT)
                | (workerId << WORKER_SHIFT)
                | sequence;
    }

    /** Static accessor for use in @PrePersist (entities cannot inject Spring beans). */
    public static long generate() {
        return INSTANCE.nextId();
    }
}
