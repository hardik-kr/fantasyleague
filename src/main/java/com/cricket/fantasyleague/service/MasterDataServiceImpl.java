package com.cricket.fantasyleague.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cricket.fantasyleague.entity.enums.PlayerType;
import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.Player;
import com.cricket.fantasyleague.entity.table.Team;
import com.cricket.fantasyleague.exception.CommonException;
import com.cricket.fantasyleague.repository.MatchRepository;
import com.cricket.fantasyleague.repository.PlayerRepository;
import com.cricket.fantasyleague.repository.TeamRepository;
import com.cricket.fantasyleague.service.cricketapi.CricketApiReadService;
import com.cricket.fantasyleague.service.cricketapi.model.CricketApiMatchRow;
import com.cricket.fantasyleague.service.cricketapi.model.CricketApiPlayerRow;
import com.cricket.fantasyleague.service.cricketapi.model.CricketApiTeamRow;
import com.cricket.fantasyleague.util.AppConstants;

@Service
public class MasterDataServiceImpl implements MasterDataService {

    private final PlayerRepository playersRepository;
    private final TeamRepository teamsRepository;
    private final MatchRepository matchRepository;
    private final CricketApiReadService cricketApiReadService;

    public MasterDataServiceImpl(PlayerRepository playersRepository,
                                 TeamRepository teamsRepository,
                                 MatchRepository matchRepository,
                                 CricketApiReadService cricketApiReadService) {
        this.playersRepository = playersRepository;
        this.teamsRepository = teamsRepository;
        this.matchRepository = matchRepository;
        this.cricketApiReadService = cricketApiReadService;
    }

    @Override
    @Transactional
    public void fetchTourPlayers(Integer id) {
        syncMasterDataFromCricketApi();
    }

    @Override
    @Transactional
    public void fetchLeaguePlayers(String leagueName) {
        syncMasterDataFromCricketApi();
    }

    private void syncMasterDataFromCricketApi() {
        List<CricketApiTeamRow> sourceTeams = cricketApiReadService.fetchTeams();
        List<CricketApiPlayerRow> sourcePlayers = cricketApiReadService.fetchPlayers();
        List<CricketApiMatchRow> sourceMatches = cricketApiReadService.fetchMatches();

        Map<Integer, Team> teamBySourceId = syncTeams(sourceTeams);
        syncPlayers(sourcePlayers, teamBySourceId);
        syncMatches(sourceMatches, teamBySourceId);
    }

    private Map<Integer, Team> syncTeams(List<CricketApiTeamRow> sourceTeams) {
        List<Team> existingTeams = teamsRepository.findAll();
        Map<Integer, Team> existingById = new HashMap<>();

        for (Team existing : existingTeams) {
            existingById.put(existing.getId(), existing);
        }

        List<Team> upsert = new ArrayList<>();
        for (CricketApiTeamRow source : sourceTeams) {
            Team team = existingById.get(source.id());
            if (team == null) {
                team = new Team();
                team.setId(source.id());
            }

            team.setName(normalizeText(source.country()));
            team.setInital(normalizeShortName(source.shortName()));
            upsert.add(team);
        }

        List<Team> saved;
        try {
            saved = teamsRepository.saveAll(upsert);
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new CommonException(String.format(
                AppConstants.error.DATABASE_ERROR,
                AppConstants.entity.TEAMS,
                cause.getMessage()
            ));
        }

        Map<Integer, Team> byId = new HashMap<>();
        for (Team team : saved) {
            byId.put(team.getId(), team);
        }
        return byId;
    }

    private void syncPlayers(List<CricketApiPlayerRow> sourcePlayers, Map<Integer, Team> teamBySourceId) {
        List<Player> existingPlayers = playersRepository.findAll();
        Map<String, Player> existingByKey = new HashMap<>();

        for (Player existing : existingPlayers) {
            existingByKey.put(playerKey(existing.getName(), existing.getTeamid().getId()), existing);
        }

        List<Player> upsert = new ArrayList<>();

        for (CricketApiPlayerRow source : sourcePlayers) {
            Team team = teamBySourceId.get(source.teamId());
            if (team == null) {
                continue;
            }

            String normalizedName = normalizeText(source.name());
            String key = playerKey(normalizedName, team.getId());

            Player player = existingByKey.get(key);
            if (player == null) {
                player = new Player();
                player.setName(normalizedName);
                player.setTeamid(team);
                player.setCredit(8.0);
                player.setOverseas(false);
                player.setUncapped(false);
            }

            player.setType(mapRoleToPlayerType(source.role()));
            upsert.add(player);
        }

        try {
            playersRepository.saveAll(upsert);
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new CommonException(String.format(
                AppConstants.error.DATABASE_ERROR,
                AppConstants.entity.PLAYER,
                cause.getMessage()
            ));
        }
    }

    private void syncMatches(List<CricketApiMatchRow> sourceMatches, Map<Integer, Team> teamBySourceId) {
        List<Match> existingMatches = matchRepository.findAll();
        Map<Integer, Match> existingById = new HashMap<>();

        for (Match existing : existingMatches) {
            existingById.put(existing.getId(), existing);
        }

        sourceMatches.sort(Comparator
            .comparing(CricketApiMatchRow::date)
            .thenComparing(CricketApiMatchRow::time)
            .thenComparing(CricketApiMatchRow::id));

        List<Match> upsert = new ArrayList<>();
        int matchNum = 1;

        for (CricketApiMatchRow source : sourceMatches) {
            Team teamA = teamBySourceId.get(source.teamAId());
            Team teamB = teamBySourceId.get(source.teamBId());
            if (teamA == null || teamB == null) {
                continue;
            }

            Match match = existingById.get(source.id());
            if (match == null) {
                match = new Match();
                match.setId(source.id());
            }

            match.setDate(source.date());
            match.setTime(source.time());
            match.setVenue(source.venue());
            match.setResult(source.result());
            match.setToss(source.toss());
            match.setTeamA(teamA);
            match.setTeamB(teamB);
            match.setMatchnum(matchNum++);

            upsert.add(match);
        }

        try {
            matchRepository.saveAll(upsert);
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new CommonException(String.format(
                AppConstants.error.DATABASE_ERROR,
                AppConstants.entity.MATCH,
                cause.getMessage()
            ));
        }
    }

    private PlayerType mapRoleToPlayerType(String role) {
        if (role == null) {
            return PlayerType.BATTER;
        }

        String normalized = role.toUpperCase(Locale.ROOT);
        if (normalized.contains("WK") || normalized.contains("KEEP")) {
            return PlayerType.KEEPER;
        }
        if (normalized.contains("BOWL")) {
            return PlayerType.BOWLER;
        }
        if (normalized.contains("ALL")) {
            return PlayerType.ALLROUNDER;
        }
        return PlayerType.BATTER;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeShortName(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String playerKey(String playerName, Integer teamId) {
        return playerName + "#" + teamId;
    }
}
