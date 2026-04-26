package com.cricket.fantasyleague.service.daily;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.cricket.fantasyleague.dao.CricketMasterDataDao;
import com.cricket.fantasyleague.dao.model.PlayerTeamData;
import com.cricket.fantasyleague.entity.enums.PlayerType;
import com.cricket.fantasyleague.entity.table.FantasyPlayerConfig;
import com.cricket.fantasyleague.entity.table.Player;
import com.cricket.fantasyleague.exception.InvalidTeamException;
import com.cricket.fantasyleague.payload.daily.DailyTeamRequest;

/**
 * Pure validation logic for a Daily Challenge team submission.
 *
 * <p>Composition rules (mirroring Season Long, with no boosters):
 * <ol>
 *   <li>Exactly 11 players, no duplicates.</li>
 *   <li>Captain in playing XI; Vice-captain in playing XI; captain != vice-captain.</li>
 *   <li>Every selected player must belong to the match pool (i.e. one of the two
 *       sides taking part in this specific match).</li>
 *   <li>Role counts: 3-6 batters, 3-6 bowlers, 1-4 keepers, 1-4 allrounders.</li>
 *   <li>Total credit &le; 100.</li>
 *   <li>Max 4 overseas players.</li>
 *   <li>Max 7 players from any single real-world side.</li>
 * </ol>
 *
 * Stateless / SRP: only validates. No DB writes. Throws
 * {@link InvalidTeamException} on any violation; the caller (controller layer)
 * surfaces this to the API consumer as a 400.
 */
@Component
public class DailyTeamValidator {

    private static final int PLAYING_XI_SIZE = 11;
    private static final int MIN_BATTERS = 3, MAX_BATTERS = 6;
    private static final int MIN_BOWLERS = 3, MAX_BOWLERS = 6;
    private static final int MIN_KEEPERS = 1, MAX_KEEPERS = 4;
    private static final int MIN_ALLROUNDERS = 1, MAX_ALLROUNDERS = 4;
    private static final int MAX_OVERSEAS = 4;
    private static final int MAX_PER_TEAM = 7;
    private static final double MAX_CREDIT = 100.0;

    private final CricketMasterDataDao cricketDao;

    public DailyTeamValidator(CricketMasterDataDao cricketDao) {
        this.cricketDao = cricketDao;
    }

    /**
     * Validate the submission against the supplied match player pool and config.
     *
     * @param request          incoming team selection
     * @param players          looked-up Player entities (size must match request playing11)
     * @param matchPlayerPool  player IDs allowed for this match (squads of both sides)
     * @param configByPlayerId fantasy player config for the league (credit/type/overseas)
     */
    public void validate(DailyTeamRequest request,
                         List<Player> players,
                         Set<Integer> matchPlayerPool,
                         Map<Integer, FantasyPlayerConfig> configByPlayerId) {
        validateBasicShape(request);
        validatePoolMembership(request.getPlaying11(), matchPlayerPool);
        validateCaptaincy(request);

        if (players.size() != PLAYING_XI_SIZE) {
            throw new InvalidTeamException("One or more player IDs are invalid");
        }

        validateRolesAndCredits(players, configByPlayerId);
        validateTeamCap(players);
    }

    private void validateBasicShape(DailyTeamRequest request) {
        List<Integer> ids = request.getPlaying11();
        if (ids == null || ids.size() != PLAYING_XI_SIZE) {
            throw new InvalidTeamException("Playing XI must contain exactly 11 players");
        }
        if (new HashSet<>(ids).size() != PLAYING_XI_SIZE) {
            throw new InvalidTeamException("Playing XI contains duplicate players");
        }
    }

    private void validatePoolMembership(List<Integer> ids, Set<Integer> pool) {
        if (pool == null || pool.isEmpty()) {
            throw new InvalidTeamException("Match player pool is unavailable");
        }
        for (Integer id : ids) {
            if (!pool.contains(id)) {
                throw new InvalidTeamException(
                        "Player " + id + " is not part of this match's squads — daily teams must use only players from the two sides playing");
            }
        }
    }

    private void validateCaptaincy(DailyTeamRequest request) {
        Set<Integer> idSet = new HashSet<>(request.getPlaying11());
        if (request.getCaptainId() == null || !idSet.contains(request.getCaptainId())) {
            throw new InvalidTeamException("Captain must be a player in the playing XI");
        }
        if (request.getViceCaptainId() == null || !idSet.contains(request.getViceCaptainId())) {
            throw new InvalidTeamException("Vice-captain must be a player in the playing XI");
        }
        if (request.getCaptainId().equals(request.getViceCaptainId())) {
            throw new InvalidTeamException("Captain and vice-captain must be different players");
        }
    }

    private void validateRolesAndCredits(List<Player> players,
                                         Map<Integer, FantasyPlayerConfig> configMap) {
        int batters = 0, bowlers = 0, keepers = 0, allrounders = 0;
        int overseasCount = 0;
        double totalCredit = 0.0;

        for (Player p : players) {
            PlayerType role = p.getRole();
            if (role != null) {
                switch (role) {
                    case BATTER -> batters++;
                    case BOWLER -> bowlers++;
                    case KEEPER -> keepers++;
                    case ALLROUNDER -> allrounders++;
                }
            }

            FantasyPlayerConfig cfg = configMap.get(p.getId());
            if (cfg != null) {
                if (Boolean.TRUE.equals(cfg.getOverseas())) {
                    overseasCount++;
                }
                if (cfg.getCredit() != null) {
                    totalCredit += cfg.getCredit();
                }
            }
        }

        if (batters < MIN_BATTERS || batters > MAX_BATTERS) {
            throw new InvalidTeamException("Batters must be between " + MIN_BATTERS + " and " + MAX_BATTERS + ", found: " + batters);
        }
        if (bowlers < MIN_BOWLERS || bowlers > MAX_BOWLERS) {
            throw new InvalidTeamException("Bowlers must be between " + MIN_BOWLERS + " and " + MAX_BOWLERS + ", found: " + bowlers);
        }
        if (keepers < MIN_KEEPERS || keepers > MAX_KEEPERS) {
            throw new InvalidTeamException("Keepers must be between " + MIN_KEEPERS + " and " + MAX_KEEPERS + ", found: " + keepers);
        }
        if (allrounders < MIN_ALLROUNDERS || allrounders > MAX_ALLROUNDERS) {
            throw new InvalidTeamException("Allrounders must be between " + MIN_ALLROUNDERS + " and " + MAX_ALLROUNDERS + ", found: " + allrounders);
        }
        if (overseasCount > MAX_OVERSEAS) {
            throw new InvalidTeamException("Max " + MAX_OVERSEAS + " overseas players allowed, found: " + overseasCount);
        }
        if (totalCredit > MAX_CREDIT) {
            throw new InvalidTeamException(
                    String.format("Total credit must not exceed %.0f, current total: %.1f", MAX_CREDIT, totalCredit));
        }
    }

    private void validateTeamCap(List<Player> players) {
        Map<Integer, Integer> teamCount = new HashMap<>();
        for (Player p : players) {
            List<PlayerTeamData> playerTeams = cricketDao.findTeamsByPlayerId(p.getId());
            for (PlayerTeamData ptd : playerTeams) {
                if (Boolean.TRUE.equals(ptd.isActive())) {
                    teamCount.merge(ptd.teamId(), 1, Integer::sum);
                }
            }
        }
        for (Map.Entry<Integer, Integer> entry : teamCount.entrySet()) {
            if (entry.getValue() > MAX_PER_TEAM) {
                throw new InvalidTeamException(
                        "Max " + MAX_PER_TEAM + " players from one team allowed, teamId=" + entry.getKey()
                                + " has " + entry.getValue());
            }
        }
    }
}
