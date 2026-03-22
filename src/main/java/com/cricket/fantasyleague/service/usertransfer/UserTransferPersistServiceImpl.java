package com.cricket.fantasyleague.service.usertransfer;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cricket.fantasyleague.dao.CricketEntityMapper;
import com.cricket.fantasyleague.dao.CricketMasterDataDao;
import com.cricket.fantasyleague.dao.model.PlayerData;
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

@Service
public class UserTransferPersistServiceImpl {

    private static final Logger logger = LoggerFactory.getLogger(UserTransferPersistServiceImpl.class);

    private final UserRepository userRepository;
    private final CricketMasterDataDao cricketDao;
    private final CricketEntityMapper cricketEntities;
    private final UserMatchStatsRespository userMatchStatsRepository;
    private final UserMatchStatsDraftRespository userMatchStatsDraftRepository;
    private final UserOverallStatsRepository userOverallStatsRepository;

    public UserTransferPersistServiceImpl(UserRepository userRepository,
                                           CricketMasterDataDao cricketDao,
                                           CricketEntityMapper cricketEntities,
                                           UserMatchStatsRespository userMatchStatsRepository,
                                           UserMatchStatsDraftRespository userMatchStatsDraftRepository,
                                           UserOverallStatsRepository userOverallStatsRepository) {
        this.userRepository = userRepository;
        this.cricketDao = cricketDao;
        this.cricketEntities = cricketEntities;
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
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        var byId = cricketDao.findPlayersByIds(ids).stream()
                .collect(Collectors.toMap(PlayerData::id, Function.identity()));
        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(cricketEntities::toPlayer)
                .toList();
    }

    public Player getPlayerReference(Integer id) {
        return cricketDao.findPlayerById(id)
                .map(cricketEntities::toPlayer)
                .orElseThrow(() -> new CommonException("Player not found: " + id));
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

    public List<UserMatchStatsDraft> findAllDraftsByMatch(Match match) {
        return userMatchStatsDraftRepository.findAllByMatchid(match);
    }

    @Transactional
    public void saveDraft(UserMatchStatsDraft draft) {
        try {
            userMatchStatsDraftRepository.save(draft);
        } catch (Exception e) {
            logger.error("Failed to save draft: matchId={}, userId={}", 
                    draft.getMatchid() != null ? draft.getMatchid().getId() : null,
                    draft.getUserid() != null ? draft.getUserid().getId() : null, e);
            Throwable cause = extractCause(e);
            String msg = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
            throw new CommonException(String.format(
                    AppConstants.error.DATABASE_ERROR,
                    AppConstants.entity.USERMATCHSTATSDRAFT,
                    msg));
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

    public void saveAllMatchStats(List<UserMatchStats> statsList) {
        try {
            userMatchStatsRepository.saveAll(statsList);
        } catch (Exception e) {
            Throwable cause = extractCause(e);
            throw new CommonException(String.format(
                    AppConstants.error.DATABASE_ERROR,
                    AppConstants.entity.USERMATCHSTATSDRAFT,
                    cause.getMessage()));
        }
    }

    public void saveAllOverallStats(List<UserOverallStats> statsList) {
        try {
            userOverallStatsRepository.saveAll(statsList);
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
