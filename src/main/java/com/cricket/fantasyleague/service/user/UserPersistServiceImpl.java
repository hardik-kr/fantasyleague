package com.cricket.fantasyleague.service.user;

import java.util.List;

import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.entity.enums.UserRole;
import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.UserOverallStats;
import com.cricket.fantasyleague.exception.CommonException;
import com.cricket.fantasyleague.repository.UserOverallStatsRepository;
import com.cricket.fantasyleague.repository.UserRepository;
import com.cricket.fantasyleague.util.AppConstants;

@Service
public class UserPersistServiceImpl {

    private final UserRepository userRepository;
    private final UserOverallStatsRepository userOverallStatsRepository;

    public UserPersistServiceImpl(UserRepository userRepository,
                                   UserOverallStatsRepository userOverallStatsRepository) {
        this.userRepository = userRepository;
        this.userOverallStatsRepository = userOverallStatsRepository;
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public User findByRole(UserRole role) {
        return userRepository.findByRole(role);
    }

    public User saveUser(User user) {
        return userRepository.save(user);
    }

    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    public void saveOverallStats(UserOverallStats stats) {
        try {
            userOverallStatsRepository.save(stats);
        } catch (Exception e) {
            Throwable cause = extractCause(e);
            throw new CommonException(String.format(
                    AppConstants.error.DATABASE_ERROR,
                    AppConstants.entity.USEROVERALLPOINTS,
                    cause.getMessage()));
        }
    }

    private Throwable extractCause(Exception e) {
        Throwable cause = e.getCause();
        if (cause != null && cause.getCause() != null) return cause.getCause();
        return cause != null ? cause : e;
    }
}
