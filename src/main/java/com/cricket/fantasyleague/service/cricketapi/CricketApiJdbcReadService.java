package com.cricket.fantasyleague.service.cricketapi;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.cricket.fantasyleague.service.cricketapi.model.CricketApiMatchRow;
import com.cricket.fantasyleague.service.cricketapi.model.CricketApiPlayerRow;
import com.cricket.fantasyleague.service.cricketapi.model.CricketApiTeamRow;

@Service
public class CricketApiJdbcReadService implements CricketApiReadService {

    private final NamedParameterJdbcTemplate cricketApiJdbcTemplate;

    public CricketApiJdbcReadService(
            @Qualifier("cricketApiJdbcTemplate") NamedParameterJdbcTemplate cricketApiJdbcTemplate) {
        this.cricketApiJdbcTemplate = cricketApiJdbcTemplate;
    }

    @Override
    public List<CricketApiTeamRow> fetchTeams() {
        String sql = """
            SELECT t.id, t.country, t.name
            FROM teams t
            ORDER BY t.id
            """;

        return cricketApiJdbcTemplate.query(sql, new MapSqlParameterSource(), new TeamRowMapper());
    }

    @Override
    public List<CricketApiPlayerRow> fetchPlayers() {
        String sql = """
            SELECT p.id, p.name, p.team_id, p.role
            FROM players p
            ORDER BY p.id
            """;

        return cricketApiJdbcTemplate.query(sql, new MapSqlParameterSource(), new PlayerRowMapper());
    }

    @Override
    public List<CricketApiMatchRow> fetchMatches() {
        String sql = """
            SELECT m.id, m.date, m.time, m.venue, m.result, m.toss, m.teamA_id, m.teamB_id
            FROM matches m
            ORDER BY m.date, m.time, m.id
            """;

        return cricketApiJdbcTemplate.query(sql, new MapSqlParameterSource(), new MatchRowMapper());
    }

    private static class TeamRowMapper implements RowMapper<CricketApiTeamRow> {
        @Override
        public CricketApiTeamRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new CricketApiTeamRow(
                rs.getInt("id"),
                rs.getString("country"),
                rs.getString("name")
            );
        }
    }

    private static class PlayerRowMapper implements RowMapper<CricketApiPlayerRow> {
        @Override
        public CricketApiPlayerRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new CricketApiPlayerRow(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getInt("team_id"),
                rs.getString("role")
            );
        }
    }

    private static class MatchRowMapper implements RowMapper<CricketApiMatchRow> {
        @Override
        public CricketApiMatchRow mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new CricketApiMatchRow(
                rs.getInt("id"),
                rs.getDate("date").toLocalDate(),
                rs.getTime("time").toLocalTime(),
                rs.getString("venue"),
                rs.getString("result"),
                rs.getString("toss"),
                rs.getInt("teamA_id"),
                rs.getInt("teamB_id")
            );
        }
    }
}
