package com.cricket.fantasyleague.service.usermatchstats;

import java.util.List;

import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.UserMatchStats;
import com.cricket.fantasyleague.exception.CommonException;
import com.cricket.fantasyleague.repository.UserMatchStatsRespository;
import com.cricket.fantasyleague.util.AppConstants;

@Service
public class UserMatchStatsPersistServiceImpl {

    private final UserMatchStatsRespository userMatchStatsRepository;

    public UserMatchStatsPersistServiceImpl(UserMatchStatsRespository userMatchStatsRepository) {
        this.userMatchStatsRepository = userMatchStatsRepository;
    }

    public List<UserMatchStats> findByMatch(Match match) {
        return userMatchStatsRepository.findByMatchid(match);
    }

    public void saveAll(List<UserMatchStats> statsList) {
        try {
            userMatchStatsRepository.saveAll(statsList);
        } catch (Exception e) {
            Throwable cause = extractCause(e);
            throw new CommonException(String.format(
                    AppConstants.error.DATABASE_ERROR,
                    AppConstants.entity.USERPOINTS,
                    cause.getMessage()));
        }
    }

    private Throwable extractCause(Exception e) {
        Throwable cause = e.getCause();
        if (cause != null && cause.getCause() != null) return cause.getCause();
        return cause != null ? cause : e;
    }
}
