package com.cricket.fantasyleague.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.cache.LiveMatchCache;
import com.cricket.fantasyleague.entity.table.Match;
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
import com.cricket.fantasyleague.util.FantasyPointSystem;

@Service
public class PlayerPointsServiceImpl implements PlayerPointsService {

    private static final Logger logger = LoggerFactory.getLogger(PlayerPointsServiceImpl.class);

    private final LiveMatchCache liveMatchCache;
    private final PlayerPointsPersistServiceImpl persistService;
    private final MatchService matchService;

    public PlayerPointsServiceImpl(LiveMatchCache liveMatchCache,
                                   PlayerPointsPersistServiceImpl persistService,
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
        Map<String, PlayerPoints> byName = buildNameIndex(records);

        for (PlayerPoints pp : records) {
            pp.setPlayerpoints(FantasyPointSystem.others.IN_PLAYING11);
        }

        accumulateInningsPoints(scorecard.getInningsA(), byName);
        accumulateInningsPoints(scorecard.getInningsB(), byName);

        persistService.saveAllPlayerPoints(records);

        Map<Integer, Double> pointsMap = new HashMap<>(records.size());
        for (PlayerPoints pp : records) {
            pointsMap.put(pp.getPlayerid().getId(), pp.getPlayerpoints());
        }

        logger.info("Player points calculated for match {}: {} players", match.getId(), records.size());
        return pointsMap;
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

        Map<String, Player> teamAIndex = indexTeamPlayers(metadata.getTeamA());
        Map<String, Player> teamBIndex = indexTeamPlayers(metadata.getTeamB());

        List<Player> result = new ArrayList<>();
        resolveSquadPlayers(metadata.getPlayingSquad().getPlayerListA(), metadata.getTeamA(), teamAIndex, result);
        resolveSquadPlayers(metadata.getPlayingSquad().getPlayerListB(), metadata.getTeamB(), teamBIndex, result);
        return result;
    }

    private Map<String, Player> indexTeamPlayers(String teamName) {
        List<Player> teamPlayers = liveMatchCache.getTeamPlayers(teamName);
        Map<String, Player> index = new HashMap<>(teamPlayers.size());
        for (Player p : teamPlayers) {
            index.put(normalize(p.getName()), p);
        }
        return index;
    }

    private void resolveSquadPlayers(List<PlayerDto> squad, String teamName,
                                     Map<String, Player> teamIndex, List<Player> target) {
        if (squad == null) return;
        for (PlayerDto dto : squad) {
            String key = normalize(dto.getName());
            Player player = teamIndex.get(key);
            if (player == null) {
                player = persistService.findPlayerByNameAndTeam(dto.getName(), teamName)
                        .orElseThrow(() -> new ResourceNotFoundException("Player", "name", dto.getName()));
            }
            target.add(player);
        }
    }

    // ── Points Calculation ──

    private void accumulateInningsPoints(InningDto innings, Map<String, PlayerPoints> byName) {
        if (innings == null) return;
        accumulateBatterPoints(innings.getBatter(), byName);
        accumulateBowlerPoints(innings.getBowler(), byName);
        accumulateFielderPoints(innings.getFielder(), byName);
    }

    private void accumulateBatterPoints(List<BatterDto> batters, Map<String, PlayerPoints> byName) {
        if (batters == null) return;
        for (BatterDto bat : batters) {
            PlayerPoints pp = byName.get(normalize(bat.getName()));
            if (pp == null) continue;
            pp.setPlayerpoints(pp.getPlayerpoints() + calcBatterPoints(bat));
        }
    }

    private void accumulateBowlerPoints(List<BowlerDto> bowlers, Map<String, PlayerPoints> byName) {
        if (bowlers == null) return;
        for (BowlerDto bowl : bowlers) {
            PlayerPoints pp = byName.get(normalize(bowl.getName()));
            if (pp == null) continue;
            pp.setPlayerpoints(pp.getPlayerpoints() + calcBowlerPoints(bowl));
        }
    }

    private void accumulateFielderPoints(List<FielderDto> fielders, Map<String, PlayerPoints> byName) {
        if (fielders == null) return;
        for (FielderDto field : fielders) {
            PlayerPoints pp = byName.get(normalize(field.getName()));
            if (pp == null) continue;
            pp.setPlayerpoints(pp.getPlayerpoints() + calcFielderPoints(field));
        }
    }

    private double calcBatterPoints(BatterDto bat) {
        double pts = 0.0;
        pts += bat.getRuns() * FantasyPointSystem.myBatting.RUN;
        pts += bat.getFours() * FantasyPointSystem.myBatting.BOUNDARY;
        pts += bat.getSixes() * FantasyPointSystem.myBatting.SIX;

        boolean isDismissed = !"batting".equalsIgnoreCase(bat.getDismissal())
                && !"not out".equalsIgnoreCase(bat.getDismissal());
        if (isDismissed && bat.getRuns() == 0) {
            pts += FantasyPointSystem.myBatting.DISMISSAL_DUCK;
        }

        if (bat.getRuns() != 0 && bat.getBalls() >= 10) {
            int srDelta = bat.getRuns() - bat.getBalls();
            if (srDelta > 0) {
                pts += Math.min(srDelta, FantasyPointSystem.others.STRIKERATE_MAX);
            } else if (srDelta < 0) {
                pts += Math.max(srDelta, FantasyPointSystem.others.STRIKERATE_MIN);
            }
        }

        if (bat.getRuns() >= 100) {
            pts += FantasyPointSystem.myBatting.CENTURY;
        } else if (bat.getRuns() >= 50) {
            pts += FantasyPointSystem.myBatting.HALF_CENTURY;
        } else if (bat.getRuns() >= 30) {
            pts += FantasyPointSystem.myBatting.THIRTY;
        }

        return pts;
    }

    private double calcBowlerPoints(BowlerDto bowl) {
        double pts = 0.0;
        pts += bowl.getWickets() * FantasyPointSystem.Bowling.WICKET;

        if (bowl.getWickets() >= 3) {
            pts += (bowl.getWickets() - 2) * FantasyPointSystem.myBowling.WICKET_BONUS;
        }

        if (bowl.getBallbowl() >= 12) {
            double economy = bowl.getBallbowl() * 1.5 - bowl.getRuns();
            if (economy >= 0) {
                pts += Math.min(economy, FantasyPointSystem.others.ECONOMY_MAX);
            } else {
                pts += Math.max(economy, FantasyPointSystem.others.ECONOMY_MIN);
            }
        }

        pts += bowl.getMaidens() * FantasyPointSystem.myBowling.MAIDEN;
        return pts;
    }

    private double calcFielderPoints(FielderDto field) {
        double pts = 0.0;
        pts += field.getCatches() * FantasyPointSystem.myFielding.CATCH;

        if (field.getCatches() >= 2) {
            pts += (field.getCatches() - 1) * FantasyPointSystem.myFielding.CATCH_BONUS;
        }

        pts += field.getLbwbowled() * FantasyPointSystem.myBowling.LBW_BOWLED;
        pts += field.getRunouts_direct() * FantasyPointSystem.myFielding.RUN_OUT_DIRECT;
        pts += field.getRunouts() * FantasyPointSystem.myFielding.RUN_OUT;
        pts += field.getStumpings() * FantasyPointSystem.myFielding.STUMPING;

        if (field.getStumpings() >= 2) {
            pts += (field.getStumpings() - 1) * FantasyPointSystem.myFielding.STUMPING_BONUS;
        }

        return pts;
    }

    // ── Helpers ──

    private Map<String, PlayerPoints> buildNameIndex(List<PlayerPoints> records) {
        Map<String, PlayerPoints> index = new HashMap<>(records.size());
        for (PlayerPoints pp : records) {
            index.put(normalize(pp.getPlayerid().getName()), pp);
        }
        return index;
    }

    private String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }
}
