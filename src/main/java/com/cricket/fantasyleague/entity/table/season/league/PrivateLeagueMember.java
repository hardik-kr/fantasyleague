package com.cricket.fantasyleague.entity.table.season.league;

import java.time.LocalDateTime;

import com.cricket.fantasyleague.entity.table.User;
import com.cricket.fantasyleague.util.SnowflakeIdGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Membership record linking a {@link User} to a {@link PrivateLeague}.
 *
 * <p>The {@code (private_league_id, user_id)} unique constraint is the
 * source of truth for "is this user a member?" — even if the denormalised
 * {@code member_count} on {@link PrivateLeague} were to drift, this row's
 * presence/absence is authoritative.
 */
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(
        name = "private_league_member",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_pleague_member",
                columnNames = {"private_league_id", "user_id"}),
        indexes = {
                @Index(name = "idx_pleague_member_user", columnList = "user_id"),
                @Index(name = "idx_pleague_member_league", columnList = "private_league_id")
        })
public class PrivateLeagueMember {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "private_league_id", referencedColumnName = "id",
                foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT),
                nullable = false, updatable = false)
    private PrivateLeague privateLeague;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id",
                foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT),
                nullable = false, updatable = false)
    private User user;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    public PrivateLeagueMember(PrivateLeague privateLeague, User user) {
        this.id = SnowflakeIdGenerator.generate();
        this.privateLeague = privateLeague;
        this.user = user;
    }

    @PrePersist
    private void onCreate() {
        if (this.id == null) {
            this.id = SnowflakeIdGenerator.generate();
        }
        if (this.joinedAt == null) {
            this.joinedAt = LocalDateTime.now();
        }
    }
}
