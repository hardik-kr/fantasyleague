package com.cricket.fantasyleague.cache.dto;

import java.util.Collections;
import java.util.List;

import com.cricket.fantasyleague.entity.enums.Booster;
import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.Player;
import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.UserMatchStats;

/**
 * Lightweight projection of {@link UserMatchStats} for cache storage.
 * Stores only IDs for relationships to avoid serializing full JPA entity graphs.
 */
public record CachedUserMatchStats(
        Long id,
        Long userId,
        String userEmail,
        Integer matchId,
        Integer boosterOrdinal,
        Integer transferused,
        Double matchpoints,
        Integer captainId,
        Integer vicecaptainId,
        Integer tripleBoosterId,
        List<Integer> playing11Ids
) {

    public static CachedUserMatchStats from(UserMatchStats entity) {
        return new CachedUserMatchStats(
                entity.getId(),
                entity.getUserid() != null ? entity.getUserid().getId() : null,
                entity.getUserid() != null ? entity.getUserid().getEmail() : null,
                entity.getMatchid() != null ? entity.getMatchid().getId() : null,
                entity.getBoosterused() != null ? entity.getBoosterused().ordinal() : null,
                entity.getTransferused(),
                entity.getMatchpoints(),
                entity.getCaptainid() != null ? entity.getCaptainid().getId() : null,
                entity.getVicecaptainid() != null ? entity.getVicecaptainid().getId() : null,
                entity.getTripleboosterplayerid() != null ? entity.getTripleboosterplayerid().getId() : null,
                entity.getPlaying11() != null
                        ? entity.getPlaying11().stream().map(Player::getId).toList()
                        : Collections.emptyList()
        );
    }

    /**
     * Returns a copy of this record with a new {@code matchpoints} value.
     * Used in the streaming hot-loop to avoid mutating shared DTOs and to
     * keep allocation per user bounded to a single record object.
     */
    public CachedUserMatchStats withMatchpoints(Double mp) {
        return new CachedUserMatchStats(
                id, userId, userEmail, matchId, boosterOrdinal, transferused,
                mp,
                captainId, vicecaptainId, tripleBoosterId, playing11Ids);
    }

    /**
     * Reconstructs a detached UserMatchStats with stub relationship objects
     * that carry only their IDs — sufficient for the hot-loop calculation
     * (captain/vice-captain comparison, playing11 iteration, etc.).
     */
    public UserMatchStats toEntity() {
        UserMatchStats s = new UserMatchStats();
        s.setId(id);

        if (userId != null) {
            User u = new User();
            u.setId(userId);
            u.setEmail(userEmail);
            s.setUserid(u);
        }
        if (matchId != null) {
            Match m = new Match();
            m.setId(matchId);
            s.setMatchid(m);
        }

        s.setBoosterused(boosterOrdinal != null ? Booster.values()[boosterOrdinal] : null);
        s.setTransferused(transferused);
        s.setMatchpoints(matchpoints);

        if (captainId != null) {
            Player p = new Player();
            p.setId(captainId);
            s.setCaptainid(p);
        }
        if (vicecaptainId != null) {
            Player p = new Player();
            p.setId(vicecaptainId);
            s.setVicecaptainid(p);
        }
        if (tripleBoosterId != null) {
            Player p = new Player();
            p.setId(tripleBoosterId);
            s.setTripleboosterplayerid(p);
        }

        if (playing11Ids != null) {
            s.setPlaying11(playing11Ids.stream().map(pid -> {
                Player p = new Player();
                p.setId(pid);
                return p;
            }).toList());
        }

        return s;
    }
}
