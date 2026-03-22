package com.cricket.fantasyleague.service.useroverallpts;

import java.util.List;

import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.UserMatchStats;
import com.cricket.fantasyleague.entity.table.UserOverallStats;
import com.cricket.fantasyleague.exception.CommonException;
import com.cricket.fantasyleague.repository.UserMatchStatsRespository;
import com.cricket.fantasyleague.repository.UserOverallStatsRepository;
import com.cricket.fantasyleague.util.AppConstants;

@Service
public class UserOverallPtsPersistServiceImpl {

    private final UserOverallStatsRepository userOverallStatsRepository;
    private final UserMatchStatsRespository userMatchStatsRepository;

    public UserOverallPtsPersistServiceImpl(UserOverallStatsRepository userOverallStatsRepository,
                                             UserMatchStatsRespository userMatchStatsRepository) {
        this.userOverallStatsRepository = userOverallStatsRepository;
        this.userMatchStatsRepository = userMatchStatsRepository;
    }

    public List<UserMatchStats> findMatchStatsByMatch(Match match) {
        return userMatchStatsRepository.findByMatchid(match);
    }

    public List<UserOverallStats> findAllOverallStats() {
        return userOverallStatsRepository.findAll();
    }

    public void saveAllOverallStats(List<UserOverallStats> statsList) {
        try {
            userOverallStatsRepository.saveAll(statsList);
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new CommonException(String.format(
                    AppConstants.error.DATABASE_ERROR,
                    AppConstants.entity.USEROVERALLPOINTS,
                    cause.getMessage()));
        }
    }
}
