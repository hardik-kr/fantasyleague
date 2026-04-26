package com.cricket.fantasyleague.entity.table.daily;

import java.time.LocalDateTime;
import java.util.List;

import com.cricket.fantasyleague.entity.table.Match;
import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.util.SnowflakeIdGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Locked Daily Challenge team — one row per (user, match).
 *
 * Created when the match locks (drafted team is promoted via
 * {@code DailyMatchLockService#lockTeamsForMatch}). Points are
 * recomputed every live tick by {@code DailyMatchPointsService}.
 *
 * Strict isolation: independent of {@code UserMatchStats} which serves
 * the season-long mode.
 */
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(
        name = "daily_user_match_team",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_daily_user_match",
                columnNames = {"user_id", "match_id"}),
        indexes = {
                @Index(name = "idx_daily_match_points", columnList = "match_id, match_points DESC"),
                @Index(name = "idx_daily_user", columnList = "user_id")
        })
public class DailyUserMatchTeam {

    @Id
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "match_id", referencedColumnName = "id",
                foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Match match;

    @Column(name = "captain_id")
    private Integer captainId;

    @Column(name = "vice_captain_id")
    private Integer viceCaptainId;

    @Column(name = "match_points", nullable = false)
    private Double matchPoints = 0.0;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "daily_user_playing11",
            joinColumns = @JoinColumn(name = "team_id",
                    foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)))
    @Column(name = "player_id", nullable = false)
    private List<Integer> playing11;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public DailyUserMatchTeam(User user, Match match, Integer captainId, Integer viceCaptainId,
                              List<Integer> playing11) {
        this.id = SnowflakeIdGenerator.generate();
        this.user = user;
        this.match = match;
        this.captainId = captainId;
        this.viceCaptainId = viceCaptainId;
        this.playing11 = playing11;
        this.matchPoints = 0.0;
    }

    @PrePersist
    private void onCreate() {
        if (this.id == null) {
            this.id = SnowflakeIdGenerator.generate();
        }
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.matchPoints == null) {
            this.matchPoints = 0.0;
        }
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
