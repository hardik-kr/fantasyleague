package com.cricket.fantasyleague.dao;

import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.cricket.fantasyleague.entity.enums.PlayerType;
import com.cricket.fantasyleague.dao.model.LeagueData;
import com.cricket.fantasyleague.dao.model.MatchData;
import com.cricket.fantasyleague.dao.model.PlayerData;
import com.cricket.fantasyleague.dao.model.PlayerTeamData;
import com.cricket.fantasyleague.dao.model.TeamData;

@Repository
public class CricketMasterDataDao {

    private final NamedParameterJdbcTemplate jdbc;

    public CricketMasterDataDao(
            @Qualifier("cricketApiJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Match queries ──

    private static final String MATCH_COLS =
            "m.id, m.date, m.is_match_complete, m.matchtype, m.result, m.time, m.timezone, m.toss, m.venue, m.league_id, m.mom_player_id, m.teama_id, m.teamb_id";

    public Optional<MatchData> findMatchById(Integer id) {
        String sql = "SELECT " + MATCH_COLS + " FROM matches m WHERE m.id = :id";
        return queryForOptional(sql, new MapSqlParameterSource("id", id), MATCH_MAPPER);
    }

    public List<MatchData> findMatchesByDate(LocalDate date) {
        String sql = "SELECT " + MATCH_COLS + " FROM matches m WHERE m.date = :date ORDER BY m.time ASC";
        return jdbc.query(sql, new MapSqlParameterSource("date", date), MATCH_MAPPER);
    }

    public Optional<MatchData> findUpcomingMatch(LocalDate currDate, LocalTime currTime) {
        String sql = "SELECT " + MATCH_COLS + " FROM matches m" +
                " WHERE (m.date > :currDate OR (m.date = :currDate AND m.time > :currTime))" +
                " ORDER BY m.date ASC, m.time ASC LIMIT 1";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("currDate", currDate)
                .addValue("currTime", currTime);
        return queryForOptional(sql, params, MATCH_MAPPER);
    }

    public Optional<MatchData> findNextUpcomingMatch() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        String sql = "SELECT " + MATCH_COLS + " FROM matches m" +
                " WHERE (m.date > :today OR (m.date = :today AND m.time > :now))" +
                " AND (m.is_match_complete IS NULL OR m.is_match_complete = false)" +
                " ORDER BY m.date ASC, m.time ASC LIMIT 1";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("today", today)
                .addValue("now", now);
        return queryForOptional(sql, params, MATCH_MAPPER);
    }

    public Optional<MatchData> findPreviousMatch(LocalDate currDate, LocalTime currTime) {
        String sql = "SELECT " + MATCH_COLS + " FROM matches m" +
                " WHERE (m.date < :currDate OR (m.date = :currDate AND m.time < :currTime))" +
                " ORDER BY m.date DESC, m.time DESC LIMIT 1";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("currDate", currDate)
                .addValue("currTime", currTime);
        return queryForOptional(sql, params, MATCH_MAPPER);
    }

    public Optional<MatchData> findLockedMatch(LocalDate currDate, LocalTime currTime) {
        String sql = "SELECT " + MATCH_COLS + " FROM matches m" +
                " WHERE (m.date = :currDate AND m.time <= :currTime)" +
                " ORDER BY m.time DESC LIMIT 1";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("currDate", currDate)
                .addValue("currTime", currTime);
        return queryForOptional(sql, params, MATCH_MAPPER);
    }

    public List<MatchData> findAllMatches() {
        String sql = "SELECT " + MATCH_COLS + " FROM matches m ORDER BY m.date, m.time, m.id";
        return jdbc.query(sql, new MapSqlParameterSource(), MATCH_MAPPER);
    }

    // ── Player queries (via player_team join) ──

    private static final String PLAYER_COLS =
            "p.id, p.name, p.role";

    public List<PlayerData> findPlayersByTeamName(String teamName) {
        String sql = "SELECT DISTINCT " + PLAYER_COLS +
                " FROM players p" +
                " JOIN player_team pt ON p.id = pt.player_id" +
                " JOIN teams t ON pt.team_id = t.id" +
                " WHERE t.name = :teamName AND pt.is_active = true" +
                " ORDER BY p.id";
        return jdbc.query(sql, new MapSqlParameterSource("teamName", teamName), PLAYER_MAPPER);
    }

    public Optional<PlayerData> findPlayerByNameAndTeam(String playerName, String teamName) {
        String sql = "SELECT DISTINCT " + PLAYER_COLS +
                " FROM players p" +
                " JOIN player_team pt ON p.id = pt.player_id" +
                " JOIN teams t ON pt.team_id = t.id" +
                " WHERE p.name LIKE :playerName AND t.name = :teamName";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("playerName", "%" + playerName + "%")
                .addValue("teamName", teamName);
        return queryForOptional(sql, params, PLAYER_MAPPER);
    }

    public Optional<PlayerData> findPlayerById(Integer id) {
        String sql = "SELECT " + PLAYER_COLS + " FROM players p WHERE p.id = :id";
        return queryForOptional(sql, new MapSqlParameterSource("id", id), PLAYER_MAPPER);
    }

    public List<PlayerData> findPlayersByIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        String sql = "SELECT " + PLAYER_COLS + " FROM players p WHERE p.id IN (:ids) ORDER BY p.id";
        return jdbc.query(sql, new MapSqlParameterSource("ids", ids), PLAYER_MAPPER);
    }

    public List<PlayerData> findAllPlayers() {
        String sql = "SELECT " + PLAYER_COLS + " FROM players p ORDER BY p.id";
        return jdbc.query(sql, new MapSqlParameterSource(), PLAYER_MAPPER);
    }

    public List<PlayerData> findPlayersByLeagueId(Integer leagueId) {
        String sql = "SELECT DISTINCT " + PLAYER_COLS +
                " FROM players p" +
                " JOIN player_team pt ON p.id = pt.player_id" +
                " JOIN teams t ON pt.team_id = t.id" +
                " WHERE t.league_id = :leagueId AND pt.is_active = true" +
                " ORDER BY p.id";
        return jdbc.query(sql, new MapSqlParameterSource("leagueId", leagueId), PLAYER_MAPPER);
    }

    // ── Team queries ──

    public List<TeamData> findAllTeams() {
        String sql = "SELECT t.id, t.name, t.short_name, t.league_id FROM teams t ORDER BY t.id";
        return jdbc.query(sql, new MapSqlParameterSource(), TEAM_MAPPER);
    }

    public Optional<TeamData> findTeamById(Integer id) {
        String sql = "SELECT t.id, t.name, t.short_name, t.league_id FROM teams t WHERE t.id = :id";
        return queryForOptional(sql, new MapSqlParameterSource("id", id), TEAM_MAPPER);
    }

    public List<TeamData> findTeamsByLeagueId(Integer leagueId) {
        String sql = "SELECT t.id, t.name, t.short_name, t.league_id FROM teams t WHERE t.league_id = :leagueId ORDER BY t.id";
        return jdbc.query(sql, new MapSqlParameterSource("leagueId", leagueId), TEAM_MAPPER);
    }

    // ── League queries ──

    public List<LeagueData> findAllLeagues() {
        String sql = "SELECT l.id, l.name, l.short_name, l.format, l.country FROM leagues l ORDER BY l.id";
        return jdbc.query(sql, new MapSqlParameterSource(), LEAGUE_MAPPER);
    }

    public Optional<LeagueData> findLeagueById(Integer id) {
        String sql = "SELECT l.id, l.name, l.short_name, l.format, l.country FROM leagues l WHERE l.id = :id";
        return queryForOptional(sql, new MapSqlParameterSource("id", id), LEAGUE_MAPPER);
    }

    // ── Player-Team queries ──

    public List<PlayerTeamData> findTeamsByPlayerId(Integer playerId) {
        String sql = "SELECT pt.player_id, pt.team_id, pt.is_active FROM player_team pt WHERE pt.player_id = :playerId";
        return jdbc.query(sql, new MapSqlParameterSource("playerId", playerId), PLAYER_TEAM_MAPPER);
    }

    public List<PlayerTeamData> findPlayersByTeamId(Integer teamId) {
        String sql = "SELECT pt.player_id, pt.team_id, pt.is_active FROM player_team pt WHERE pt.team_id = :teamId AND pt.is_active = true";
        return jdbc.query(sql, new MapSqlParameterSource("teamId", teamId), PLAYER_TEAM_MAPPER);
    }

    // ── Internal ──

    private <T> Optional<T> queryForOptional(String sql, MapSqlParameterSource params, RowMapper<T> mapper) {
        try {
            T row = jdbc.queryForObject(sql, params, mapper);
            return Optional.ofNullable(row);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    // ── Row mappers ──

    private static final RowMapper<MatchData> MATCH_MAPPER = (ResultSet rs, int rowNum) -> new MatchData(
        rs.getInt("id"),
        rs.getDate("date").toLocalDate(),
        rs.getObject("is_match_complete", Boolean.class),
        rs.getString("matchtype"),
        rs.getString("result"),
        rs.getTime("time").toLocalTime(),
        rs.getString("timezone"),
        rs.getString("venue"),
        rs.getString("toss"),
        rs.getObject("league_id", Integer.class),
        rs.getObject("mom_player_id", Integer.class),
        rs.getObject("teama_id", Integer.class),
        rs.getObject("teamb_id", Integer.class)
    );

    private static final RowMapper<PlayerData> PLAYER_MAPPER = (ResultSet rs, int rowNum) -> {
        int roleOrdinal = rs.getInt("role");
        PlayerType role = rs.wasNull() ? PlayerType.BATTER : PlayerType.values()[roleOrdinal];
        return new PlayerData(rs.getInt("id"), rs.getString("name"), role);
    };

    private static final RowMapper<TeamData> TEAM_MAPPER = (ResultSet rs, int rowNum) -> new TeamData(
        rs.getInt("id"),
        rs.getString("name"),
        rs.getString("short_name"),
        rs.getObject("league_id", Integer.class)
    );

    private static final RowMapper<LeagueData> LEAGUE_MAPPER = (ResultSet rs, int rowNum) -> new LeagueData(
        rs.getInt("id"),
        rs.getString("name"),
        rs.getString("short_name"),
        rs.getString("format"),
        rs.getString("country")
    );

    private static final RowMapper<PlayerTeamData> PLAYER_TEAM_MAPPER = (ResultSet rs, int rowNum) -> new PlayerTeamData(
        rs.getInt("player_id"),
        rs.getInt("team_id"),
        rs.getBoolean("is_active")
    );
}
