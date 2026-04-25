package com.cricket.fantasyleague.service.useroverallpts;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.cache.LiveMatchUserCache;
import com.cricket.fantasyleague.cache.dto.CachedUserOverallStats;
import com.cricket.fantasyleague.entity.table.Match;

@Service
public class UserOverallPtsServiceImpl implements UserOverallPtsService {

    private static final Logger logger = LoggerFactory.getLogger(UserOverallPtsServiceImpl.class);
    private static final int CHUNK_SIZE = 1000;

    private final LiveMatchUserCache userCache;

    public UserOverallPtsServiceImpl(LiveMatchUserCache userCache) {
        this.userCache = userCache;
    }

    @Override
    public void calcUserOverallPointsData(Match match) {
        // Single aggregated userId -> SUM(live matchpoints) snapshot. This is the
        // only bounded per-tick allocation (~5 MB at 100K users); short-lived and
        // discarded at the end of the method.
        Map<Long, Double> liveSumByUser = userCache.snapshotLiveMatchpointsByUser();

        AtomicInteger updated = new AtomicInteger();

        userCache.forEachOverallStatsChunk(CHUNK_SIZE, chunk -> {
            Map<Long, CachedUserOverallStats> updates = new HashMap<>(chunk.size());
            for (Map.Entry<Long, CachedUserOverallStats> e : chunk.entrySet()) {
                Long userId = e.getKey();
                CachedUserOverallStats dto = e.getValue();
                if (dto == null) continue;
                double total = userCache.getCommittedTotal(userId)
                        + liveSumByUser.getOrDefault(userId, 0.0);
                updates.put(userId, dto.withTotalpoints(total));
            }
            userCache.saveOverallStatsChunk(updates);
            updated.addAndGet(updates.size());
        });

        userCache.markOverallDirty();
        logger.info("User overall points streamed for match {}: {} users updated (chunk={})",
                match.getId(), updated.get(), CHUNK_SIZE);
    }
}
