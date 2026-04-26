package com.cricket.fantasyleague.service.daily;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cricket.fantasyleague.dao.CricketMasterDataDao;
import com.cricket.fantasyleague.dao.model.PlayerTeamData;
import com.cricket.fantasyleague.entity.enums.PlayerType;
import com.cricket.fantasyleague.entity.table.FantasyPlayerConfig;
import com.cricket.fantasyleague.entity.table.Player;
import com.cricket.fantasyleague.exception.InvalidTeamException;
import com.cricket.fantasyleague.payload.daily.DailyTeamRequest;

/**
 * Coverage for every Daily Challenge composition rule.
 *
 * Each test crafts a happy-path 11-player squad satisfying all constraints, then
 * mutates exactly one variable to trip the rule under test — keeping each
 * assertion narrowly focused on its target invariant.
 */
@ExtendWith(MockitoExtension.class)
class DailyTeamValidatorTest {

    private static final int TEAM_A = 100;
    private static final int TEAM_B = 200;

    @Mock
    private CricketMasterDataDao cricketDao;

    private DailyTeamValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DailyTeamValidator(cricketDao);
    }

    @Test
    void validate_validTeam_passes() {
        Fixture f = baseFixture();
        when(cricketDao.findTeamsByPlayerId(anyInt())).thenAnswer(inv -> {
            int pid = inv.getArgument(0);
            return List.of(new PlayerTeamData(pid, f.teamByPlayer.get(pid), true));
        });
        assertDoesNotThrow(() -> validator.validate(f.request, f.players, f.pool, f.configMap));
    }

    @Test
    void validate_wrongSize_throws() {
        Fixture f = baseFixture();
        f.request.setPlaying11(f.request.getPlaying11().subList(0, 10));
        InvalidTeamException ex = assertThrows(InvalidTeamException.class,
                () -> validator.validate(f.request, f.players.subList(0, 10), f.pool, f.configMap));
        assert ex.getMessage().contains("exactly 11");
    }

    @Test
    void validate_duplicatePlayer_throws() {
        Fixture f = baseFixture();
        List<Integer> ids = new ArrayList<>(f.request.getPlaying11());
        ids.set(10, ids.get(0));
        f.request.setPlaying11(ids);
        InvalidTeamException ex = assertThrows(InvalidTeamException.class,
                () -> validator.validate(f.request, f.players, f.pool, f.configMap));
        assert ex.getMessage().contains("duplicate");
    }

    @Test
    void validate_playerNotInPool_throws() {
        Fixture f = baseFixture();
        List<Integer> ids = new ArrayList<>(f.request.getPlaying11());
        ids.set(0, 9999); // player id 9999 is not in pool
        f.request.setPlaying11(ids);
        InvalidTeamException ex = assertThrows(InvalidTeamException.class,
                () -> validator.validate(f.request, f.players, f.pool, f.configMap));
        assert ex.getMessage().contains("not part of this match");
    }

    @Test
    void validate_captainNotInXi_throws() {
        Fixture f = baseFixture();
        f.request.setCaptainId(9999);
        InvalidTeamException ex = assertThrows(InvalidTeamException.class,
                () -> validator.validate(f.request, f.players, f.pool, f.configMap));
        assert ex.getMessage().contains("Captain");
    }

    @Test
    void validate_viceCaptainNotInXi_throws() {
        Fixture f = baseFixture();
        f.request.setViceCaptainId(9999);
        InvalidTeamException ex = assertThrows(InvalidTeamException.class,
                () -> validator.validate(f.request, f.players, f.pool, f.configMap));
        assert ex.getMessage().contains("Vice-captain");
    }

    @Test
    void validate_captainEqualsViceCaptain_throws() {
        Fixture f = baseFixture();
        f.request.setViceCaptainId(f.request.getCaptainId());
        InvalidTeamException ex = assertThrows(InvalidTeamException.class,
                () -> validator.validate(f.request, f.players, f.pool, f.configMap));
        assert ex.getMessage().contains("different");
    }

    @Test
    void validate_tooManyBatters_throws() {
        Fixture f = roleFixture(7, 3, 1, 0); // 7 batters > 6
        when(cricketDao.findTeamsByPlayerId(anyInt())).thenAnswer(inv -> {
            int pid = inv.getArgument(0);
            return List.of(new PlayerTeamData(pid, f.teamByPlayer.get(pid), true));
        });
        InvalidTeamException ex = assertThrows(InvalidTeamException.class,
                () -> validator.validate(f.request, f.players, f.pool, f.configMap));
        assert ex.getMessage().contains("Batters");
    }

    @Test
    void validate_tooFewKeepers_throws() {
        Fixture f = roleFixture(4, 4, 0, 3); // 0 keepers < 1
        when(cricketDao.findTeamsByPlayerId(anyInt())).thenAnswer(inv -> {
            int pid = inv.getArgument(0);
            return List.of(new PlayerTeamData(pid, f.teamByPlayer.get(pid), true));
        });
        InvalidTeamException ex = assertThrows(InvalidTeamException.class,
                () -> validator.validate(f.request, f.players, f.pool, f.configMap));
        assert ex.getMessage().contains("Keepers");
    }

    @Test
    void validate_creditOverCap_throws() {
        Fixture f = baseFixture();
        for (FantasyPlayerConfig cfg : f.configMap.values()) {
            cfg.setCredit(15.0); // 11 * 15 = 165 > 100
        }
        when(cricketDao.findTeamsByPlayerId(anyInt())).thenAnswer(inv -> {
            int pid = inv.getArgument(0);
            return List.of(new PlayerTeamData(pid, f.teamByPlayer.get(pid), true));
        });
        InvalidTeamException ex = assertThrows(InvalidTeamException.class,
                () -> validator.validate(f.request, f.players, f.pool, f.configMap));
        assert ex.getMessage().contains("credit");
    }

    @Test
    void validate_overseasOverCap_throws() {
        Fixture f = baseFixture();
        // mark first 5 as overseas (cap is 4)
        int marked = 0;
        for (Player p : f.players) {
            FantasyPlayerConfig cfg = f.configMap.get(p.getId());
            if (cfg != null && marked++ < 5) cfg.setOverseas(true);
        }
        when(cricketDao.findTeamsByPlayerId(anyInt())).thenAnswer(inv -> {
            int pid = inv.getArgument(0);
            return List.of(new PlayerTeamData(pid, f.teamByPlayer.get(pid), true));
        });
        InvalidTeamException ex = assertThrows(InvalidTeamException.class,
                () -> validator.validate(f.request, f.players, f.pool, f.configMap));
        assert ex.getMessage().contains("overseas");
    }

    @Test
    void validate_tooManyFromOneTeam_throws() {
        Fixture f = baseFixture();
        // override mock so all 11 players belong to TEAM_A → exceeds max 7
        when(cricketDao.findTeamsByPlayerId(anyInt())).thenAnswer(inv -> {
            int pid = inv.getArgument(0);
            return List.of(new PlayerTeamData(pid, TEAM_A, true));
        });
        InvalidTeamException ex = assertThrows(InvalidTeamException.class,
                () -> validator.validate(f.request, f.players, f.pool, f.configMap));
        assert ex.getMessage().contains("Max 7");
    }

    @Test
    void validate_emptyPool_throws() {
        Fixture f = baseFixture();
        InvalidTeamException ex = assertThrows(InvalidTeamException.class,
                () -> validator.validate(f.request, f.players, Set.of(), f.configMap));
        assert ex.getMessage().contains("pool is unavailable");
    }

    @Test
    void validate_invalidPlayerLookup_throws() {
        Fixture f = baseFixture();
        // simulate persistence layer dropping a player (returned only 10 of 11)
        InvalidTeamException ex = assertThrows(InvalidTeamException.class,
                () -> validator.validate(f.request, f.players.subList(0, 10), f.pool, f.configMap));
        assert ex.getMessage().contains("invalid");
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private static class Fixture {
        DailyTeamRequest request;
        List<Player> players;
        Set<Integer> pool;
        Map<Integer, FantasyPlayerConfig> configMap;
        Map<Integer, Integer> teamByPlayer;
    }

    /** 11-player team: 4 BAT, 3 BOWL, 2 ALR, 2 KEEP — split 6 from TEAM_A, 5 from TEAM_B. */
    private Fixture baseFixture() {
        return roleFixture(4, 3, 2, 2);
    }

    /**
     * Builds a fixture with the given role counts (must sum to 11). 6 players go
     * to TEAM_A and 5 to TEAM_B by default to stay under the per-team cap of 7.
     */
    private Fixture roleFixture(int batters, int bowlers, int keepers, int allrounders) {
        Fixture f = new Fixture();
        f.players = new ArrayList<>();
        f.configMap = new HashMap<>();
        f.teamByPlayer = new HashMap<>();
        f.pool = new HashSet<>();

        int id = 1;
        addPlayers(f, id, batters, PlayerType.BATTER); id += batters;
        addPlayers(f, id, bowlers, PlayerType.BOWLER); id += bowlers;
        addPlayers(f, id, keepers, PlayerType.KEEPER); id += keepers;
        addPlayers(f, id, allrounders, PlayerType.ALLROUNDER);

        // distribute first 6 to TEAM_A, rest to TEAM_B
        for (int i = 0; i < f.players.size(); i++) {
            f.teamByPlayer.put(f.players.get(i).getId(), i < 6 ? TEAM_A : TEAM_B);
        }

        List<Integer> ids = f.players.stream().map(Player::getId).toList();
        f.pool.addAll(ids);

        f.request = new DailyTeamRequest();
        f.request.setPlaying11(new ArrayList<>(ids));
        f.request.setCaptainId(ids.get(0));
        f.request.setViceCaptainId(ids.get(1));
        return f;
    }

    private void addPlayers(Fixture f, int startId, int count, PlayerType role) {
        for (int i = 0; i < count; i++) {
            int pid = startId + i;
            Player p = new Player(pid, "P" + pid, role);
            f.players.add(p);
            FantasyPlayerConfig cfg = new FantasyPlayerConfig();
            cfg.setPlayerId(pid);
            cfg.setLeagueId(1);
            cfg.setCredit(8.0);
            cfg.setType(role);
            cfg.setOverseas(false);
            cfg.setUncapped(false);
            cfg.setTotalPoints(0.0);
            cfg.setIsActive(true);
            f.configMap.put(pid, cfg);
        }
    }
}
