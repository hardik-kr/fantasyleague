package com.cricket.fantasyleague.service.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.UserOverallStats;
import com.cricket.fantasyleague.payload.response.LeaderboardEntry;
import com.cricket.fantasyleague.repository.UserOverallStatsRepository;

@Service
public class LeaderboardServiceImpl implements LeaderboardService {

    private final UserOverallStatsRepository userOverallStatsRepository;

    public LeaderboardServiceImpl(UserOverallStatsRepository userOverallStatsRepository) {
        this.userOverallStatsRepository = userOverallStatsRepository;
    }

    @Override
    public List<LeaderboardEntry> getRankedLeaderboard() {
        List<UserOverallStats> allStats = userOverallStatsRepository.findAll();
        allStats.sort(Comparator.comparingDouble(
                (UserOverallStats s) -> s.getTotalpoints() != null ? s.getTotalpoints() : 0.0
        ).reversed());

        List<LeaderboardEntry> result = new ArrayList<>(allStats.size());
        int rank = 1;
        for (UserOverallStats uos : allStats) {
            User u = uos.getUserid();
            result.add(new LeaderboardEntry(
                    rank++,
                    u != null ? u.getId() : null,
                    u != null ? u.getUsername() : null,
                    u != null ? u.getFirstname() : null,
                    uos.getTotalpoints()
            ));
        }
        return result;
    }
}
