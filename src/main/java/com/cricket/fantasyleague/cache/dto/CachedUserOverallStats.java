package com.cricket.fantasyleague.cache.dto;

import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.entity.table.UserOverallStats;

/**
 * Lightweight projection of {@link UserOverallStats} for cache storage.
 * Stores only the user ID instead of the full User entity.
 */
public record CachedUserOverallStats(
        Long id,
        Long userId,
        Double totalpoints,
        Double prevpoints,
        Integer boosterleft,
        Integer transferleft,
        String usedBoosters
) {

    public static CachedUserOverallStats from(UserOverallStats entity) {
        return new CachedUserOverallStats(
                entity.getId(),
                entity.getUserid() != null ? entity.getUserid().getId() : null,
                entity.getTotalpoints(),
                entity.getPrevpoints(),
                entity.getBoosterleft(),
                entity.getTransferleft(),
                entity.getUsedBoosters()
        );
    }

    /**
     * Reconstructs a detached UserOverallStats with a stub User
     * carrying only its ID — sufficient for the hot-loop calculation.
     */
    public UserOverallStats toEntity() {
        UserOverallStats o = new UserOverallStats();
        o.setId(id);

        if (userId != null) {
            User u = new User();
            u.setId(userId);
            o.setUserid(u);
        }

        o.setTotalpoints(totalpoints);
        o.setPrevpoints(prevpoints);
        o.setBoosterleft(boosterleft);
        o.setTransferleft(transferleft);
        o.setUsedBoosters(usedBoosters);
        return o;
    }
}
