package com.cricket.fantasyleague.entity.table;

import java.util.Random;

import com.cricket.fantasyleague.entity.enums.PlayerType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
    private Integer id ;

    @Column(name = "player_id", nullable = false)
    private Integer playerId ;

    @Column(name = "league_id", nullable = false)
    private Integer leagueId ;

    private Double credit ;

    private PlayerType type ;

    private Boolean overseas ;

    private Boolean uncapped ;

    @Column(name = "is_active")
    private Boolean isActive = true ;

    public FantasyPlayerConfig(Integer playerId, Integer leagueId, Double credit,
                               PlayerType type, Boolean overseas, Boolean uncapped) {
        this.id = generateId();
        this.playerId = playerId;
        this.leagueId = leagueId;
        this.credit = credit;
        this.type = type;
        this.overseas = overseas;
        this.uncapped = uncapped;
        this.isActive = true;
    }

    private Integer generateId() 
    {
        Random random = new Random();
        StringBuilder id = new StringBuilder();

        id.append(random.nextInt(9)+1) ;
        
        for (int i = 0; i < 5; i++) {
            int digit = random.nextInt(9); // Generates a random digit between 0 and 9
            id.append(digit);
        }

        return Integer.parseInt(id.toString()) ;
    }
}
