package com.cricket.fantasyleague.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
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

import com.cricket.fantasyleague.dao.model.MatchData;
import com.cricket.fantasyleague.dao.model.PlayerData;
import com.cricket.fantasyleague.dao.model.TeamData;

/**
 * Read-only DAO for cricket master data (matches, teams, players).
 * All reads go through the cricketApiJdbcTemplate (cricketapi DB).
 */
@Repository
public class CricketMasterDataDao {

    private final NamedParameterJdbcTemplate jdbc;

    public CricketMasterDataDao(
            @Qualifier("cricketApiJdbcTemplate") NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Match queries ──

    public Optional<MatchData> findMatchById(Integer id) {
        String sql = """
            SELECT m.id, m.date, m.time, m.venue, m.matchnum, m.result, m.toss, m.teamA_id, m.teamB_id
            FROM matches m WHERE m.id = :id
            """;
        return queryForOptional(sql, new MapSqlParameterSource("id", id), MATCH_MAPPER);
    }

    public List<MatchData> findMatchesByDate(LocalDate date) {
        String sql = """
            SELECT m.id, m.date, m.time, m.venue, m.matchnum, m.result, m.toss, m.teamA_id, m.teamB_id
            FROM matches m WHERE m.date = :date ORDER BY m.time ASC
            """;
        return jdbc.query(sql, new MapSqlParameterSource("date", date), MATCH_MAPPER);
    }

    public Optional<MatchData> findUpcomingMatch(LocalDate currDate, LocalTime currTime) {
        String sql = """
            SELECT m.id, m.date, m.time, m.venue, m.matchnum, m.result, m.toss, m.teamA_id, m.teamB_id
            FROM matches m
            WHERE (m.date > :currDate OR (m.date = :currDate AND m.time > :currTime))
            ORDER BY m.date ASC, m.time ASC
            LIMIT 1
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("currDate", currDate)
                .addValue("currTime", currTime);
        return queryForOptional(sql, params, MATCH_MAPPER);
    }

    public Optional<MatchData> findByMatchnum(Integer matchnum) {
        String sql = """
            SELECT m.id, m.date, m.time, m.venue, m.matchnum, m.result, m.toss, m.teamA_id, m.teamB_id
            FROM matches m WHERE m.matchnum = :matchnum
            """;
        return queryForOptional(sql, new MapSqlParameterSource("matchnum", matchnum), MATCH_MAPPER);
    }

    public Optional<MatchData> findLockedMatch(LocalDate currDate, LocalTime currTime) {
        String sql = """
            SELECT m.id, m.date, m.time, m.venue, m.matchnum, m.result, m.toss, m.teamA_id, m.teamB_id
            FROM matches m
            WHERE (m.date = :currDate AND m.time <= :currTime)
            ORDER BY m.time DESC
            LIMIT 1
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("currDate", currDate)
                .addValue("currTime", currTime);
        return queryForOptional(sql, params, MATCH_MAPPER);
    }

    public List<MatchData> findAllMatches() {
        String sql = """
            SELECT m.id, m.date, m.time, m.venue, m.matchnum, m.result, m.toss, m.teamA_id, m.teamB_id
            FROM matches m ORDER BY m.date, m.time, m.id
            """;
        return jdbc.query(sql, new MapSqlParameterSource(), MATCH_MAPPER);
    }

    // ── Player queries ──

    public List<PlayerData> findPlayersByTeamName(String teamName) {
        String sql = """
            SELECT p.id, p.name, p.team_id, t.country AS team_name, p.role
            FROM players p JOIN teams t ON p.team_id = t.id
            WHERE t.country = :teamName
            ORDER BY p.id
            """;
        return jdbc.query(sql, new MapSqlParameterSource("teamName", teamName), PLAYER_MAPPER);
    }

    public Optional<PlayerData> findPlayerByNameAndTeam(String playerName, String teamName) {
        String sql = """
            SELECT p.id, p.name, p.team_id, t.country AS team_name, p.role
            FROM players p JOIN teams t ON p.team_id = t.id
            WHERE p.name LIKE :playerName AND t.country = :teamName
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("playerName", "%" + playerName + "%")
                .addValue("teamName", teamName);
        return queryForOptional(sql, params, PLAYER_MAPPER);
    }

    public Optional<PlayerData> findPlayerById(Integer id) {
        String sql = """
            SELECT p.id, p.name, p.team_id, t.country AS team_name, p.role
            FROM players p JOIN teams t ON p.team_id = t.id
            WHERE p.id = :id
            """;
        return queryForOptional(sql, new MapSqlParameterSource("id", id), PLAYER_MAPPER);
    }

    public List<PlayerData> findPlayersByIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        String sql = """
            SELECT p.id, p.name, p.team_id, t.country AS team_name, p.role
            FROM players p JOIN teams t ON p.team_id = t.id
            WHERE p.id IN (:ids)
            ORDER BY p.id
            """;
        return jdbc.query(sql, new MapSqlParameterSource("ids", ids), PLAYER_MAPPER);
    }

    public List<PlayerData> findAllPlayers() {
        String sql = """
            SELECT p.id, p.name, p.team_id, t.country AS team_name, p.role
            FROM players p JOIN teams t ON p.team_id = t.id
            ORDER BY p.id
            """;
        return jdbc.query(sql, new MapSqlParameterSource(), PLAYER_MAPPER);
    }

    // ── Team queries ──

    public List<TeamData> findAllTeams() {
        String sql = "SELECT t.id, t.country, t.name FROM teams t ORDER BY t.id";
        return jdbc.query(sql, new MapSqlParameterSource(), TEAM_MAPPER);
    }

    public Optional<TeamData> findTeamById(Integer id) {
        String sql = "SELECT t.id, t.country, t.name FROM teams t WHERE t.id = :id";
        return queryForOptional(sql, new MapSqlParameterSource("id", id), TEAM_MAPPER);
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
        rs.getTime("time").toLocalTime(),
        rs.getString("venue"),
        rs.getObject("matchnum", Integer.class),
        rs.getString("result"),
        rs.getString("toss"),
        rs.getObject("teamA_id", Integer.class),
        rs.getObject("teamB_id", Integer.class)
    );

    private static final RowMapper<PlayerData> PLAYER_MAPPER = (ResultSet rs, int rowNum) -> new PlayerData(
        rs.getInt("id"),
        rs.getString("name"),
        rs.getInt("team_id"),
        rs.getString("team_name"),
        rs.getString("role")
    );

    private static final RowMapper<TeamData> TEAM_MAPPER = (ResultSet rs, int rowNum) -> new TeamData(
        rs.getInt("id"),
        rs.getString("country"),
        rs.getString("name")
    );
}
