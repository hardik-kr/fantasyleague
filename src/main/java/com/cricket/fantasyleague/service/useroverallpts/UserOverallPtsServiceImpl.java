package com.cricket.fantasyleague.service.useroverallpts;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.cache.LiveMatchUserCache;
import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.UserOverallStats;

@Service
public class UserOverallPtsServiceImpl implements UserOverallPtsService {

    private static final Logger logger = LoggerFactory.getLogger(UserOverallPtsServiceImpl.class);

    private final LiveMatchUserCache userCache;

    public UserOverallPtsServiceImpl(LiveMatchUserCache userCache) {
        this.userCache = userCache;
    }

    @Override
    public void calcUserOverallPointsData(Match match, Map<Integer, Double> matchPointsByUserId) {
        if (matchPointsByUserId.isEmpty()) return;

        Map<Integer, UserOverallStats> overallByUserId = userCache.getOverallStatsByUserId();

        int updated = 0;
        for (Map.Entry<Integer, Double> entry : matchPointsByUserId.entrySet()) {
            UserOverallStats overall = overallByUserId.get(entry.getKey());
            if (overall == null) continue;

            double prevPts = overall.getPrevpoints() == null ? 0.0 : overall.getPrevpoints();
            overall.setTotalpoints(prevPts + entry.getValue());
            updated++;
        }

        userCache.markOverallDirty();
        logger.info("User overall points calculated for match {}: {} users updated", match.getId(), updated);
    }
}
