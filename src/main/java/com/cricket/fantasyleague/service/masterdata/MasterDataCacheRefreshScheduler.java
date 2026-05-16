package com.cricket.fantasyleague.service.masterdata;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * During a live season-long match, periodically refreshes the master match row and only the
 * in-scope player rows in Redis (see {@link LiveMasterCacheScope}), with no {@code findActiveLiveMatch}
 * or full-catalog reload on each tick.
 * Uses the shared {@link TaskScheduler} bean (see {@code AsyncConfig}); each tick calls
 * {@link MasterDataReadService#refreshIfLiveMatchActive()}. When no live scope is published, that is a no-op.
 */
@Component
@ConditionalOnProperty(name = "fantasy.master-cache.enabled", havingValue = "true", matchIfMissing = true)
public class MasterDataCacheRefreshScheduler {

    private static final Logger logger = LoggerFactory.getLogger(MasterDataCacheRefreshScheduler.class);

    private final MasterDataReadService masterDataReadService;
    private final TaskScheduler taskScheduler;
    private final long liveRefreshMs;

    private volatile ScheduledFuture<?> refreshTask;

    public MasterDataCacheRefreshScheduler(MasterDataReadService masterDataReadService,
                                           TaskScheduler taskScheduler,
                                           @Value("${fantasy.master-cache.live-refresh-ms:300000}") long liveRefreshMs) {
        this.masterDataReadService = masterDataReadService;
        this.taskScheduler = taskScheduler;
        this.liveRefreshMs = liveRefreshMs;
    }

    @PostConstruct
    void start() {
        Duration delay = Duration.ofMillis(liveRefreshMs);
        Runnable tick = () -> {
            try {
                masterDataReadService.refreshIfLiveMatchActive();
            } catch (Exception ex) {
                logger.warn("Master data cache live refresh failed: {}", ex.getMessage());
            }
        };
        refreshTask = taskScheduler.scheduleWithFixedDelay(tick, Instant.now(), delay);
    }

    @PreDestroy
    void stop() {
        ScheduledFuture<?> f = refreshTask;
        if (f != null) {
            f.cancel(false);
        }
    }
}
