package com.cricket.fantasyleague.service.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.UserOverallStats;
import com.cricket.fantasyleague.payload.response.LeaderboardEntry;
import com.cricket.fantasyleague.payload.response.LeaderboardPageResponse;
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

    @Override
    public LeaderboardPageResponse getRankedLeaderboard(int page, int size, User currentUser) {
        Page<UserOverallStats> statsPage = userOverallStatsRepository
                .findAllRanked(PageRequest.of(page, size));

        int startRank = page * size + 1;
        List<LeaderboardEntry> entries = new ArrayList<>(statsPage.getNumberOfElements());
        for (int i = 0; i < statsPage.getContent().size(); i++) {
            UserOverallStats uos = statsPage.getContent().get(i);
            User u = uos.getUserid();
            entries.add(new LeaderboardEntry(
                    startRank + i,
                    u != null ? u.getId() : null,
                    u != null ? u.getUsername() : null,
                    u != null ? u.getFirstname() : null,
                    uos.getTotalpoints()
            ));
        }

        LeaderboardEntry currentUserEntry = null;
        if (currentUser != null) {
            UserOverallStats myStats = userOverallStatsRepository.findByUserid(currentUser);
            if (myStats != null) {
                double pts = myStats.getTotalpoints() != null ? myStats.getTotalpoints() : 0.0;
                long above = userOverallStatsRepository.countUsersAbove(pts);
                currentUserEntry = new LeaderboardEntry(
                        (int) above + 1,
                        currentUser.getId(),
                        currentUser.getUsername(),
                        currentUser.getFirstname(),
                        myStats.getTotalpoints()
                );
            }
        }

        return new LeaderboardPageResponse(
                entries,
                page,
                size,
                statsPage.getTotalElements(),
                statsPage.getTotalPages(),
                currentUserEntry
        );
    }
}
