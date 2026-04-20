package com.cricket.fantasyleague.entity.table;

import com.cricket.fantasyleague.entity.enums.PlayerType;
import com.cricket.fantasyleague.util.SnowflakeIdGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "fantasy_player_config")
public class FantasyPlayerConfig 
{
    @Id
    private Long id ;

    @Column(name = "player_id", nullable = false)
    private Integer playerId ;

    @Column(name = "league_id", nullable = false)
    private Integer leagueId ;

    private Double credit ;

    private PlayerType type ;

    private Boolean overseas ;

    private Boolean uncapped ;

    @Column(name = "total_points")
    private Double totalPoints = 0.0;

    @Column(name = "is_active")
    private Boolean isActive = true ;

    public FantasyPlayerConfig(Integer playerId, Integer leagueId, Double credit,
                               PlayerType type, Boolean overseas, Boolean uncapped) {
        this.id = SnowflakeIdGenerator.generate();
        this.playerId = playerId;
        this.leagueId = leagueId;
        this.credit = credit;
        this.type = type;
        this.overseas = overseas;
        this.uncapped = uncapped;
        this.isActive = true;
    }

    @PrePersist
    private void ensureId() {
        if (this.id == null) {
            this.id = SnowflakeIdGenerator.generate();
        }
    }
}
