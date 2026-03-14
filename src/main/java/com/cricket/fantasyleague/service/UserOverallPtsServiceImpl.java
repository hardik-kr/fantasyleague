package com.cricket.fantasyleague.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.UserMatchStats;
import com.cricket.fantasyleague.entity.table.UserOverallStats;
import com.cricket.fantasyleague.exception.CommonException;
import com.cricket.fantasyleague.repository.UserMatchStatsRespository;
import com.cricket.fantasyleague.repository.UserOverallStatsRepository;
import com.cricket.fantasyleague.util.AppConstants;

@Service
public class UserOverallPtsServiceImpl implements UserOverallPtsService {

    private static final Logger logger = LoggerFactory.getLogger(UserOverallPtsServiceImpl.class);

    private final UserOverallStatsRepository userOverallStatsRepository;
    private final UserMatchStatsRespository userMatchStatsRepository;

    public UserOverallPtsServiceImpl(UserOverallStatsRepository userOverallStatsRepository,
                                     UserMatchStatsRespository userMatchStatsRepository) {
        this.userOverallStatsRepository = userOverallStatsRepository;
        this.userMatchStatsRepository = userMatchStatsRepository;
    }

    @Override
    public void calcUserOverallPointsData(Match match) {
        List<UserMatchStats> matchStats = userMatchStatsRepository.findByMatchid(match);
        if (matchStats.isEmpty()) return;

        Map<Integer, UserOverallStats> overallByUserId = loadOverallByUserId();

        for (UserMatchStats stat : matchStats) {
            if (stat.getUserid() == null
                    || "admin@gmail.com".equals(stat.getUserid().getEmail())) {
                continue;
            }

            UserOverallStats overall = overallByUserId.get(stat.getUserid().getId());
            if (overall == null) continue;

            double matchPts = stat.getMatchpoints() == null ? 0.0 : stat.getMatchpoints();
            double prevPts = overall.getPrevpoints() == null ? 0.0 : overall.getPrevpoints();
            overall.setTotalpoints(prevPts + matchPts);
        }

        saveAll(overallByUserId.values().stream().toList());
        logger.info("User overall points updated for match {}", match.getId());
    }

    private Map<Integer, UserOverallStats> loadOverallByUserId() {
        List<UserOverallStats> all = userOverallStatsRepository.findAll();
        Map<Integer, UserOverallStats> map = new HashMap<>(all.size());
        for (UserOverallStats o : all) {
            if (o.getUserid() != null) {
                map.put(o.getUserid().getId(), o);
            }
        }
        return map;
    }

    private void saveAll(List<UserOverallStats> list) {
        try {
            userOverallStatsRepository.saveAll(list);
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new CommonException(String.format(
                    AppConstants.error.DATABASE_ERROR,
                    AppConstants.entity.USEROVERALLPOINTS,
                    cause.getMessage()));
        }
    }
}
