package com.cricket.fantasyleague.service.api;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.cricket.fantasyleague.config.AppConfig;
import com.cricket.fantasyleague.dao.CricketMasterDataDao;
import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.season.UserOverallStats;
import com.cricket.fantasyleague.exception.InvalidTeamException;
import com.cricket.fantasyleague.payload.dto.SeasonOnboardingRequest;
import com.cricket.fantasyleague.payload.response.UserProfileResponse;
import com.cricket.fantasyleague.repository.UserRepository;
import com.cricket.fantasyleague.repository.season.UserOverallStatsRepository;

@Service
public class UserProfileServiceImpl implements UserProfileService {

    private final UserOverallStatsRepository userOverallStatsRepository;
    private final UserRepository userRepository;
    private final CricketMasterDataDao cricketMasterDataDao;
    private final AppConfig appConfig;

    public UserProfileServiceImpl(
            UserOverallStatsRepository userOverallStatsRepository,
            UserRepository userRepository,
            CricketMasterDataDao cricketMasterDataDao,
            AppConfig appConfig) {
        this.userOverallStatsRepository = userOverallStatsRepository;
        this.userRepository = userRepository;
        this.cricketMasterDataDao = cricketMasterDataDao;
        this.appConfig = appConfig;
    }

    @Override
    public UserProfileResponse getProfile(User user) {
        UserOverallStats overall = userOverallStatsRepository.findByUserid(user);
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getFirstname(),
                user.getLastname(),
                user.getEmail(),
                user.getFavteam(),
                Boolean.TRUE.equals(user.getSeasonOnboardingSeen()),
                overall != null ? overall.getTotalpoints() : null,
                overall != null ? overall.getBoosterleft() : null,
                overall != null ? overall.getTransferleft() : null
        );
    }

    @Override
    public UserProfileResponse completeSeasonOnboarding(User user, SeasonOnboardingRequest request) {
        String favteam = request.favteam() == null ? "" : request.favteam().trim();
        if (!StringUtils.hasText(favteam)) {
            throw new InvalidTeamException("Favorite team is required");
        }
        boolean validCurrentSeasonTeam = cricketMasterDataDao.findTeamsByLeagueId(appConfig.getActiveLeagueId())
                .stream()
                .anyMatch(team -> favteam.equalsIgnoreCase(team.shortName()));
        if (!validCurrentSeasonTeam) {
            throw new InvalidTeamException("Favorite team is not valid for the current tournament");
        }

        user.setFavteam(favteam.toUpperCase());
        user.setSeasonOnboardingSeen(true);
        return getProfile(userRepository.save(user));
    }
}
