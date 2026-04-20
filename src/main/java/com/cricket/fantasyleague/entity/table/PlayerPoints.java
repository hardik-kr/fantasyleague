package com.cricket.fantasyleague.entity.table;

import com.cricket.fantasyleague.util.SnowflakeIdGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@Table(name = "player_points")
public class PlayerPoints 
{
    @Id
    private Long id ;

    /** FK to cricket match id (no JPA join — table may only exist in cricketapi DB). */
    @Column(name = "match_id")
    private Integer matchId ;

    /** FK to cricket player id (no JPA join). */
    @Column(name = "player_id")
    private Integer playerId ;

    private Double playerpoints ;

    public PlayerPoints(Match match, Player player, Double playerpoints) 
    {
        this.id = SnowflakeIdGenerator.generate();
        this.matchId = match != null ? match.getId() : null;
        this.playerId = player != null ? player.getId() : null;
        this.playerpoints = playerpoints;
    }

    @PrePersist
    private void ensureId() {
        if (this.id == null) {
            this.id = SnowflakeIdGenerator.generate();
        }
    }
}
