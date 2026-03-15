package com.cricket.fantasyleague.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.Player;
import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.UserMatchStats;
import com.cricket.fantasyleague.entity.table.UserMatchStatsDraft;
import com.cricket.fantasyleague.entity.table.UserOverallStats;
import com.cricket.fantasyleague.exception.CommonException;
import com.cricket.fantasyleague.repository.UserMatchStatsDraftRespository;
import com.cricket.fantasyleague.repository.UserMatchStatsRespository;
import com.cricket.fantasyleague.repository.UserOverallStatsRepository;
import com.cricket.fantasyleague.repository.UserRepository;
import com.cricket.fantasyleague.util.AppConstants;

import jakarta.persistence.EntityManager;

@Service
public class UserTransferPersistServiceImpl {

    private final UserRepository userRepository;
    private final EntityManager em;
    private final UserMatchStatsRespository userMatchStatsRepository;
    private final UserMatchStatsDraftRespository userMatchStatsDraftRepository;
    private final UserOverallStatsRepository userOverallStatsRepository;

    public UserTransferPersistServiceImpl(UserRepository userRepository,
                                           EntityManager em,
                                           UserMatchStatsRespository userMatchStatsRepository,
                                           UserMatchStatsDraftRespository userMatchStatsDraftRepository,
                                           UserOverallStatsRepository userOverallStatsRepository) {
        this.userRepository = userRepository;
        this.em = em;
        this.userMatchStatsRepository = userMatchStatsRepository;
        this.userMatchStatsDraftRepository = userMatchStatsDraftRepository;
        this.userOverallStatsRepository = userOverallStatsRepository;
    }

    public User findUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    public List<Player> findPlayersById(List<Integer> ids) {
        return ids.stream()
                .map(id -> em.find(Player.class, id))
                .filter(p -> p != null)
                .toList();
    }

    public Player getPlayerReference(Integer id) {
        return em.getReference(Player.class, id);
    }

    public UserMatchStats findMatchStatsByMatchAndUser(Match match, User user) {
        return userMatchStatsRepository.findByMatchidAndUserid(match, user);
    }

    public UserOverallStats findOverallStatsByUser(User user) {
        return userOverallStatsRepository.findByUserid(user);
    }

    public UserMatchStatsDraft findDraftByMatchAndUser(Match match, User user) {
        return userMatchStatsDraftRepository.findByMatchidAndUserid(match, user);
    }

    public void saveDraft(UserMatchStatsDraft draft) {
        try {
            userMatchStatsDraftRepository.save(draft);
        } catch (Exception e) {
            Throwable cause = extractCause(e);
            throw new CommonException(String.format(
                    AppConstants.error.DATABASE_ERROR,
                    AppConstants.entity.USERMATCHSTATSDRAFT,
                    cause.getMessage()));
        }
    }

    public void saveMatchStats(UserMatchStats stats) {
        try {
            userMatchStatsRepository.save(stats);
        } catch (Exception e) {
            Throwable cause = extractCause(e);
            throw new CommonException(String.format(
                    AppConstants.error.DATABASE_ERROR,
                    AppConstants.entity.USERMATCHSTATSDRAFT,
                    cause.getMessage()));
        }
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
