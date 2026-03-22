package com.cricket.fantasyleague.service.playerpoints;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.cache.LiveMatchCache;
import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.enums.PlayerType;
import com.cricket.fantasyleague.entity.table.Player;
import com.cricket.fantasyleague.entity.table.PlayerPoints;
import com.cricket.fantasyleague.exception.ResourceNotFoundException;
import com.cricket.fantasyleague.payload.fullscorecarddto.BatterDto;
import com.cricket.fantasyleague.payload.fullscorecarddto.BowlerDto;
import com.cricket.fantasyleague.payload.fullscorecarddto.FielderDto;
import com.cricket.fantasyleague.payload.fullscorecarddto.FullScorecardDto;
import com.cricket.fantasyleague.payload.fullscorecarddto.InningDto;
import com.cricket.fantasyleague.payload.fullscorecarddto.MatchMetaDataDto;
import com.cricket.fantasyleague.payload.fullscorecarddto.PlayerDto;
import com.cricket.fantasyleague.service.match.MatchService;
import com.cricket.fantasyleague.util.FantasyPointSystem;

@Service
public class LiveMatchPlayerPointsServiceImpl implements LiveMatchPlayerPointsService {

    private static final Logger logger = LoggerFactory.getLogger(LiveMatchPlayerPointsServiceImpl.class);

    private final LiveMatchCache liveMatchCache;
    private final LiveMatchPlayerPointsPersistServiceImpl persistService;
    private final MatchService matchService;

    public LiveMatchPlayerPointsServiceImpl(LiveMatchCache liveMatchCache,
                                   LiveMatchPlayerPointsPersistServiceImpl persistService,
                                   MatchService matchService) {
        this.liveMatchCache = liveMatchCache;
        this.persistService = persistService;
        this.matchService = matchService;
    }

    @Override
    public void testPoints(Integer id) {
        Match match = matchService.findMatchById(id);
        if (match == null) {
            throw new ResourceNotFoundException("matches", "matchid", id.toString());
        }
        calculatePlayerPoints(match);
    }

    @Override
    public Map<Integer, Double> calculatePlayerPoints(Match match) {
        FullScorecardDto scorecard = liveMatchCache.getScorecard(match.getId());

        List<PlayerPoints> records = getOrInitPlayerPoints(match, scorecard.getMatchinfo());
        Map<Integer, PlayerPoints> byPlayerId = buildPlayerIdIndex(records);

        discoverAndRegisterNewPlayers(scorecard, match, records, byPlayerId);

        Map<Integer, PlayerType> playerRoleMap = buildPlayerRoleMap(records);

        resetToBase(records);
        accumulateInningsPoints(scorecard.getInningsA(), byPlayerId, playerRoleMap);
        accumulateInningsPoints(scorecard.getInningsB(), byPlayerId, playerRoleMap);

        liveMatchCache.markPlayerPointsDirty(match.getId());

        Map<Integer, Double> pointsMap = new HashMap<>(records.size());
        for (PlayerPoints pp : records) {
            if (pp.getPlayerId() != null) {
                pointsMap.put(pp.getPlayerId(), pp.getPlayerpoints());
            }
        }

        logger.info("Player points calculated for match {}: {} players", match.getId(), records.size());
        return pointsMap;
    }

    private void resetToBase(List<PlayerPoints> records) {
        for (PlayerPoints pp : records) {
            pp.setPlayerpoints(FantasyPointSystem.others.IN_PLAYING11);
        }
    }

    // ── Initialization ──

    private List<PlayerPoints> getOrInitPlayerPoints(Match match, MatchMetaDataDto metadata) {
        List<PlayerPoints> cached = liveMatchCache.getPlayerPointsRecords(match.getId());
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        List<PlayerPoints> fromDb = persistService.findPlayerPointsByMatch(match);
        if (!fromDb.isEmpty()) {
            liveMatchCache.putPlayerPointsRecords(match.getId(), fromDb);
            return fromDb;
        }

        List<Player> players = resolvePlayingXI(metadata);
        List<PlayerPoints> newRecords = new ArrayList<>(players.size());
        for (Player player : players) {
            newRecords.add(new PlayerPoints(match, player, FantasyPointSystem.others.IN_PLAYING11));
        }

        persistService.saveAllPlayerPoints(newRecords);
        liveMatchCache.putPlayerPointsRecords(match.getId(), newRecords);
        return newRecords;
    }

    private List<Player> resolvePlayingXI(MatchMetaDataDto metadata) {
        if (metadata == null || metadata.getPlayingSquad() == null) {
            return Collections.emptyList();
        }

        Map<Integer, Player> teamAIndex = indexTeamPlayersById(metadata.getTeamA());
        Map<Integer, Player> teamBIndex = indexTeamPlayersById(metadata.getTeamB());

        List<Player> result = new ArrayList<>();
        resolveSquadPlayers(metadata.getPlayingSquad().getPlayerListA(), metadata.getTeamA(), teamAIndex, result);
        resolveSquadPlayers(metadata.getPlayingSquad().getPlayerListB(), metadata.getTeamB(), teamBIndex, result);
        return result;
    }

    private Map<Integer, Player> indexTeamPlayersById(String teamName) {
        List<Player> teamPlayers = liveMatchCache.getTeamPlayers(teamName);
        Map<Integer, Player> index = new HashMap<>(teamPlayers.size());
        for (Player p : teamPlayers) {
            if (p.getId() != null) {
                index.put(p.getId(), p);
            }
        }
        return index;
    }

    private void resolveSquadPlayers(List<PlayerDto> squad, String teamName,
                                     Map<Integer, Player> teamIndex, List<Player> target) {
        if (squad == null) return;

        // Separate players already in cache from those that need a DB lookup
        List<PlayerDto> missingIdDtos = new ArrayList<>();
        List<PlayerDto> missingNameDtos = new ArrayList<>();

        for (PlayerDto dto : squad) {
            if (dto.getId() != null && teamIndex.containsKey(dto.getId())) {
                target.add(teamIndex.get(dto.getId()));
            } else if (dto.getId() != null) {
                missingIdDtos.add(dto);
            } else {
                missingNameDtos.add(dto);
            }
        }

        // Batch fetch all players missing from cache in a single DB call
        if (!missingIdDtos.isEmpty()) {
            List<Integer> ids = missingIdDtos.stream().map(PlayerDto::getId).toList();
            Map<Integer, Player> fetched = persistService.findPlayersByIds(ids);
            for (PlayerDto dto : missingIdDtos) {
                Player player = fetched.get(dto.getId());
                if (player == null) {
                    // Fallback: try name-based lookup for this player
                    player = dto.getName() != null
                            ? persistService.findPlayerByNameAndTeam(dto.getName(), teamName).orElse(null)
                            : null;
                }
                if (player == null) {
                    throw new ResourceNotFoundException("Player", "id", dto.getId().toString());
                }
                target.add(player);
            }
        }

        // Name-only dtos (no id supplied) — one lookup each (rare edge case)
        for (PlayerDto dto : missingNameDtos) {
            Player player = dto.getName() != null
                    ? persistService.findPlayerByNameAndTeam(dto.getName(), teamName).orElse(null)
                    : null;
            if (player == null) {
                String ref = dto.getName() != null ? dto.getName() : "unknown";
                throw new ResourceNotFoundException("Player", "name", ref);
            }
            target.add(player);
        }
    }

    // ── Player Role Resolution ──

    private Map<Integer, PlayerType> buildPlayerRoleMap(List<PlayerPoints> records) {
        List<Integer> playerIds = new ArrayList<>();
        for (PlayerPoints pp : records) {
            if (pp.getPlayerId() != null) playerIds.add(pp.getPlayerId());
        }
        if (playerIds.isEmpty()) return Collections.emptyMap();

        Map<Integer, Player> players = persistService.findPlayersByIds(playerIds);
        Map<Integer, PlayerType> roleMap = new HashMap<>(players.size());
        for (Map.Entry<Integer, Player> entry : players.entrySet()) {
            roleMap.put(entry.getKey(), entry.getValue().getRole());
        }
        return roleMap;
    }

    private static boolean isBowler(PlayerType role) {
        return role == PlayerType.BOWLER;
    }

    // ── Points Calculation ──

    private void accumulateInningsPoints(InningDto innings, Map<Integer, PlayerPoints> byPlayerId,
                                         Map<Integer, PlayerType> roleMap) {
        if (innings == null) return;
        accumulateBatterPoints(innings.getBatter(), byPlayerId, roleMap);
        accumulateBowlerPoints(innings.getBowler(), byPlayerId);
        accumulateFielderPoints(innings.getFielder(), byPlayerId);
    }

    private void accumulateBatterPoints(List<BatterDto> batters, Map<Integer, PlayerPoints> byPlayerId,
                                        Map<Integer, PlayerType> roleMap) {
        if (batters == null) return;
        for (BatterDto bat : batters) {
            if (bat.getPlayerId() == null) continue;
            PlayerPoints pp = byPlayerId.get(bat.getPlayerId());
            if (pp == null) continue;
            boolean bowler = isBowler(roleMap.get(bat.getPlayerId()));
            pp.setPlayerpoints(pp.getPlayerpoints() + calcBatterPoints(bat, bowler));
        }
    }

    private void accumulateBowlerPoints(List<BowlerDto> bowlers, Map<Integer, PlayerPoints> byPlayerId) {
        if (bowlers == null) return;
        for (BowlerDto bowl : bowlers) {
            if (bowl.getPlayerId() == null) continue;
            PlayerPoints pp = byPlayerId.get(bowl.getPlayerId());
            if (pp == null) continue;
            pp.setPlayerpoints(pp.getPlayerpoints() + calcBowlerPoints(bowl));
        }
    }

    private void accumulateFielderPoints(List<FielderDto> fielders, Map<Integer, PlayerPoints> byPlayerId) {
        if (fielders == null) return;
        for (FielderDto field : fielders) {
            if (field.getPlayerId() == null) continue;
            PlayerPoints pp = byPlayerId.get(field.getPlayerId());
            if (pp == null) continue;
            pp.setPlayerpoints(pp.getPlayerpoints() + calcFielderPoints(field));
        }
    }

    // ── Batting ──

    private double calcBatterPoints(BatterDto bat, boolean bowler) {
        double pts = 0.0;
        int runs = bat.getRuns() != null ? bat.getRuns() : 0;
        int balls = bat.getBalls() != null ? bat.getBalls() : 0;
        int fours = bat.getFours() != null ? bat.getFours() : 0;
        int sixes = bat.getSixes() != null ? bat.getSixes() : 0;

        pts += runs * FantasyPointSystem.Batting.RUN;
        pts += fours * FantasyPointSystem.Batting.BOUNDARY;
        pts += sixes * FantasyPointSystem.Batting.SIX;

        boolean isDismissed = bat.getDismissal() != null
                && !bat.getDismissal().isBlank()
                && !"not out".equalsIgnoreCase(bat.getDismissal().trim())
                && !"batting".equalsIgnoreCase(bat.getDismissal().trim());

        if (!bowler && isDismissed && runs == 0) {
            pts += FantasyPointSystem.Batting.DISMISSAL_DUCK;
        }

        if (runs >= 100) {
            pts += FantasyPointSystem.Batting.CENTURY;
        } else if (runs >= 50) {
            pts += FantasyPointSystem.Batting.HALF_CENTURY;
        } else if (runs >= 30) {
            pts += FantasyPointSystem.Batting.THIRTY_BONUS;
        }

        if (!bowler && (balls >= 10 || runs >= 20)) {
            double sr = balls > 0 ? (runs * 100.0) / balls : 0.0;
            pts += calcStrikeRatePoints(sr);
        }

        return pts;
    }

    private static int calcStrikeRatePoints(double sr) {
        if (sr < 50)  return FantasyPointSystem.strikerate.BELOW_50;
        if (sr < 60)  return FantasyPointSystem.strikerate.BETWEEN_50_5999;
        if (sr < 70)  return FantasyPointSystem.strikerate.BETWEEN_60_6999;
        if (sr < 130) return FantasyPointSystem.strikerate.BETWEEN_70_12999;
        if (sr < 150) return FantasyPointSystem.strikerate.BETWEEN_130_14999;
        if (sr < 170) return FantasyPointSystem.strikerate.BETWEEN_150_16999;
        return FantasyPointSystem.strikerate.ABOVE_170;
    }

    // ── Bowling ──

    private double calcBowlerPoints(BowlerDto bowl) {
        double pts = 0.0;
        int wickets = bowl.getWickets() != null ? bowl.getWickets() : 0;
        int ballsBowled = bowl.getBallbowl() != null ? bowl.getBallbowl() : 0;
        int runsConceded = bowl.getRuns() != null ? bowl.getRuns() : 0;
        int maidens = bowl.getMaidens() != null ? bowl.getMaidens() : 0;

        pts += wickets * FantasyPointSystem.Bowling.WICKET;

        if (wickets >= 5) {
            pts += FantasyPointSystem.Bowling.FIVE_WICKET_HALL;
        } else if (wickets >= 4) {
            pts += FantasyPointSystem.Bowling.FOUR_WICKET_HALL;
        } else if (wickets >= 3) {
            pts += FantasyPointSystem.Bowling.THREE_WICKET_HALL;
        }

        if (ballsBowled >= 12) {
            double economy = (runsConceded * 6.0) / ballsBowled;
            pts += calcEconomyRatePoints(economy);
        }

        pts += maidens * FantasyPointSystem.Bowling.MAIDEN;
        return pts;
    }

    private static int calcEconomyRatePoints(double economy) {
        if (economy < 5)  return FantasyPointSystem.economy.BELOW_5;
        if (economy < 6)  return FantasyPointSystem.economy.BETWEEN_5_599;
        if (economy < 7)  return FantasyPointSystem.economy.BETWEEN_6_699;
        if (economy < 10) return FantasyPointSystem.economy.BETWEEN_7_999;
        if (economy < 11) return FantasyPointSystem.economy.BETWEEN_10_1099;
        if (economy < 12) return FantasyPointSystem.economy.BETWEEN_11_1199;
        return FantasyPointSystem.economy.ABOVE_12;
    }

    // ── Fielding ──

    private double calcFielderPoints(FielderDto field) {
        double pts = 0.0;
        int catches = field.getCatches() != null ? field.getCatches() : 0;
        int stumpings = field.getStumpings() != null ? field.getStumpings() : 0;
        int runoutsDirect = field.getRunouts_direct() != null ? field.getRunouts_direct() : 0;
        int runouts = field.getRunouts() != null ? field.getRunouts() : 0;
        int lbwBowled = field.getLbwbowled() != null ? field.getLbwbowled() : 0;

        pts += catches * FantasyPointSystem.Fielding.CATCH;
        if (catches >= 3) {
            pts += FantasyPointSystem.Fielding.THREE_CATCH_BONUS;
        }

        pts += lbwBowled * FantasyPointSystem.Bowling.LBW_BOWLED;
        pts += runoutsDirect * FantasyPointSystem.Fielding.RUN_OUT_DIRECT;
        pts += runouts * FantasyPointSystem.Fielding.RUN_OUT;
        pts += stumpings * FantasyPointSystem.Fielding.STUMPING;

        return pts;
    }

    // ── Impact Player Discovery ──

    /**
     * Scans the live scorecard for any playerIds not yet tracked (e.g. IPL impact player,
     * concussion substitute). Resolves them in a single batch DB call and registers a new
     * PlayerPoints record so their stats are included in the current tick's calculation.
     */
    private void discoverAndRegisterNewPlayers(FullScorecardDto scorecard, Match match,
                                               List<PlayerPoints> records,
                                               Map<Integer, PlayerPoints> byPlayerId) {
        List<Integer> missingIds = collectScorecardPlayerIds(scorecard).stream()
                .filter(id -> !byPlayerId.containsKey(id))
                .collect(Collectors.toList());

        if (missingIds.isEmpty()) return;

        Map<Integer, Player> resolved = persistService.findPlayersByIds(missingIds);
        for (Player player : resolved.values()) {
            PlayerPoints pp = new PlayerPoints(match, player, FantasyPointSystem.others.IN_PLAYING11);
            records.add(pp);
            byPlayerId.put(player.getId(), pp);
        }
        logger.info("Discovered {} new player(s) for match {} (impact/sub rule)", resolved.size(), match.getId());
    }

    private Set<Integer> collectScorecardPlayerIds(FullScorecardDto scorecard) {
        Set<Integer> ids = new HashSet<>();
        collectFromInnings(scorecard.getInningsA(), ids);
        collectFromInnings(scorecard.getInningsB(), ids);
        return ids;
    }

    private void collectFromInnings(InningDto innings, Set<Integer> ids) {
        if (innings == null) return;
        if (innings.getBatter() != null) {
            for (BatterDto b : innings.getBatter()) {
                if (b.getPlayerId() != null) ids.add(b.getPlayerId());
            }
        }
        if (innings.getBowler() != null) {
            for (BowlerDto b : innings.getBowler()) {
                if (b.getPlayerId() != null) ids.add(b.getPlayerId());
            }
        }
        if (innings.getFielder() != null) {
            for (FielderDto f : innings.getFielder()) {
                if (f.getPlayerId() != null) ids.add(f.getPlayerId());
            }
        }
    }

    // ── Helpers ──

    private Map<Integer, PlayerPoints> buildPlayerIdIndex(List<PlayerPoints> records) {
        Map<Integer, PlayerPoints> index = new HashMap<>(records.size());
        for (PlayerPoints pp : records) {
            if (pp.getPlayerId() != null) {
                index.put(pp.getPlayerId(), pp);
            }
        }
        return index;
    }
}
