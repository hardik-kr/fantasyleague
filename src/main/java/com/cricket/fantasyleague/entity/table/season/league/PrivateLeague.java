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
 * A user-created closed-circle group for the season-long mode.
 *
 * <p>Identified externally by a 10-character uppercase alphanumeric
 * {@code code} that is used both as the entry in the join API and as the
 * slug of the shareable URL ({@code /season/league/join/{code}}).
 *
 * <p>The {@code memberCount} column is intentionally denormalised so the
 * join cap can be enforced atomically with a single
 * {@code UPDATE ... WHERE member_count < max_members} — see
 * {@code PrivateLeagueRepository#incrementMemberCountIfNotFull}.
 *
 * <p>The underlying table is named {@code private_league} (rather than
 * {@code league}) because {@code league} is a reserved word in some SQL
 * dialects and to clearly distinguish it from the public season-long
 * leaderboard.
 */
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(
        name = "private_league",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_pleague_code",
                columnNames = {"code"}),
        indexes = {
                @Index(name = "idx_pleague_creator", columnList = "created_by_id")
        })
public class PrivateLeague {

    @Id
    private Long id;

    @Column(name = "name", length = 80, nullable = false)
    private String name;

    @Column(name = "code", length = 10, nullable = false, updatable = false)
    private String code;

    @Column(name = "max_members", nullable = false)
    private Integer maxMembers;

    @Column(name = "member_count", nullable = false)
    private Integer memberCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", referencedColumnName = "id",
                foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT),
                nullable = false, updatable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public PrivateLeague(String name, String code, Integer maxMembers, User createdBy) {
        this.id = SnowflakeIdGenerator.generate();
        this.name = name;
        this.code = code;
        this.maxMembers = maxMembers;
        this.createdBy = createdBy;
        this.memberCount = 1;
    }

    @PrePersist
    private void onCreate() {
        if (this.id == null) {
            this.id = SnowflakeIdGenerator.generate();
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.memberCount == null) {
            this.memberCount = 0;
        }
    }
}
