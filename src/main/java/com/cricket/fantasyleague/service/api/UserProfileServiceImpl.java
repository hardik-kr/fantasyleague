package com.cricket.fantasyleague.service.api;

import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.UserOverallStats;
import com.cricket.fantasyleague.payload.response.UserProfileResponse;
import com.cricket.fantasyleague.repository.UserOverallStatsRepository;

@Service
public class UserProfileServiceImpl implements UserProfileService {

    private final UserOverallStatsRepository userOverallStatsRepository;

    public UserProfileServiceImpl(UserOverallStatsRepository userOverallStatsRepository) {
        this.userOverallStatsRepository = userOverallStatsRepository;
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
                overall != null ? overall.getTotalpoints() : null,
                overall != null ? overall.getBoosterleft() : null,
                overall != null ? overall.getTransferleft() : null
        );
    }
}
