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
 * Editable Daily Challenge draft — created/updated by users until match lock.
 *
 * On lock the {@code DailyMatchLockService} promotes drafts into
 * {@link DailyUserMatchTeam} and deletes the source draft rows.
 *
 * Strict isolation from season-long {@code UserMatchStatsDraft}.
 */
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(
        name = "daily_user_match_team_draft",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_daily_user_match_draft",
                columnNames = {"user_id", "match_id"}),
        indexes = @Index(name = "idx_daily_draft_match", columnList = "match_id"))
public class DailyUserMatchTeamDraft {

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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "daily_user_match_team_draft_playing11",
            joinColumns = @JoinColumn(name = "draft_id",
                    foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)))
    @Column(name = "player_id", nullable = false)
    private List<Integer> playing11;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public DailyUserMatchTeamDraft(User user, Match match, Integer captainId, Integer viceCaptainId,
                                   List<Integer> playing11) {
        this.id = SnowflakeIdGenerator.generate();
        this.user = user;
        this.match = match;
        this.captainId = captainId;
        this.viceCaptainId = viceCaptainId;
        this.playing11 = playing11;
    }

    @PrePersist
    private void onCreate() {
        if (this.id == null) {
            this.id = SnowflakeIdGenerator.generate();
        }
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
