package com.cricket.fantasyleague.entity.table;

import java.util.Random;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
    private Integer id ;

    /** FK to cricket match id (no JPA join — table may only exist in cricketapi DB). */
    @Column(name = "match_id")
    private Integer matchId ;

    /** FK to cricket player id (no JPA join). */
    @Column(name = "player_id")
    private Integer playerId ;

    private Double playerpoints ;

    public PlayerPoints(Match match, Player player, Double playerpoints) 
    {
        this.id = generateId() ;
        this.matchId = match != null ? match.getId() : null;
        this.playerId = player != null ? player.getId() : null;
        this.playerpoints = playerpoints;
    }

    private Integer generateId() 
    {
        Random random = new Random();
        StringBuilder id = new StringBuilder();

        id.append(random.nextInt(9)+1) ;
        
        for (int i = 0; i < 5; i++) {
            int digit = random.nextInt(9);
            id.append(digit);
        }

        return Integer.parseInt(id.toString()) ;
    } 
}
